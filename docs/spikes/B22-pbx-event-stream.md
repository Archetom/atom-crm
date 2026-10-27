# B22 PBX 事件流适配器只读 spike

> 调研日期：2026-07-19
>
> 代表模型：Asterisk ARI REST + WebSocket；FreeSWITCH ESL 用于差异校验
>
> 状态：只读调研完成并经复审修正；未选择 PBX、SIP trunk、SDK 或参考适配器，未实现代码

## 1. 结论

PBX 事件流可以映射到 B22 的规范呼叫事实，但它比云 Webhook 更清楚地证明：CRM interaction 不能等同于 channel，核心 inbox 也不能修复 PBX 到 adapter 之间已经丢失的事件。

Asterisk ARI 暴露 channel、bridge、endpoint 和 media 原语，以 REST 发命令、以 WebSocket 发异步事件。ARI Event 基类没有协议级 event ID 或 sequence；断线后没有 replay/cursor，官方配置文档还明确警告可能错过事件。FreeSWITCH ESL 有 `(Core-UUID, Event-Sequence)`，但序号属于整个 core，不是某一订阅的无缺口 cursor，重启后也换 core 身份。

因此当前 `externalEventRef` 假设必须拆分，正式内核还需要显式的 gap/uncertain 状态和 adapter 对账能力；单靠核心 MySQL inbox 不能把易失 WebSocket 提升为 at-least-once 事件源。PBX 侧还必须把订阅隔离、跨重连/进程重启的会话关联，以及 journal/ledger 中的号码与拨号原语治理成明确边界。

## 2. 连接、认证和订阅拓扑

### 2.1 Asterisk ARI

- ARI 通过 REST 控制资源，通过 WebSocket 接收事件；只应使用 TLS、受限网络和最小权限 ARI 用户。
- `GET /events` 必须指定 `app`。每个 tenant 逻辑 connector 使用独立 Stasis app 或等价的强隔离路由，并保持 `subscribeAll=false` 以保留 application-specific 事件边界；确需观察额外 event source 时，只能通过 Applications API 显式订阅首期所需的最小资源。不能为了省事接收整个 Asterisk 的事件。
- channel 必须进入指定 Stasis application 后才由对应 ARI 应用安全控制。没有 WebSocket 时，新进入的 channel 会退出 Stasis；连接中断后 Asterisk 可能继续接受 channel，但事件会被错过。
- 适配器必须显式绑定 connector、PBX 节点和 Stasis app。app 名、ARI 用户、密码、base URL、dialplan、endpoint 和线路全部属于私有部署配置。
- 一个 Stasis application 同时只能由一个 WebSocket 服务；新连接可能替代旧连接并产生 `ApplicationReplaced`。多副本消费者不能靠普通抢占同一 app 名获得可靠负载均衡，连接所有权、租约、fencing、重连退避和 split-brain 防护是适配器责任。
- REST 与 WebSocket 使用专用受限 ARI 用户；生产 URI、反向代理 access log、异常和诊断日志都不得包含 Basic Auth 凭据。`read_only=yes` 的用户不能承担 originate/hangup，capability probe 必须区分只读观察与受控写权限，不能用一次连接成功推断所有命令可用。

### 2.2 FreeSWITCH ESL 差异

ESL 是带认证的 TCP 命令/事件接口，支持 inbound 和 outbound 两种模式。inbound 模式由适配器连接 PBX，认证后订阅事件并发出 `api`/`bgapi` 命令；`bgapi` 先返回 `Job-UUID`，随后发送 `BACKGROUND_JOB`。

ESL 的事件是 header/body 字典，必须在私有适配器内解析和白名单化。默认密码、监听所有地址和无限订阅都不适合生产；ACL、网络隔离、TLS/隧道、最小事件集合与凭据轮换留在部署边界。

## 3. interaction、channel、bridge 和 call leg

一次 CRM 通话不能绑定一个 PBX channel：

- ARI originate 可以创建指定 `channelId` 的 channel，Local channel 还可以同时产生另一条 channel；坐席腿、客户腿、转接腿和 bridge 都是独立资源；
- FreeSWITCH 的 `Unique-ID` 标识单条 channel，originate 的 A-leg/B-leg 和 bridge 仍是多腿关系；
- `ChannelStateChange=Up` 或 `CHANNEL_ANSWER` 只说明某条 leg 接听，不能单独证明坐席和客户已经建立媒体路径；
- channel 销毁也不总等于 interaction 结束，其他相关 leg 可能仍在 bridge 中。

建议由 adapter 为每次通话生成一个不透明 `externalInteractionRef`。B22-S 首期核心只关联一个 `AGENT` 和一个 `REMOTE_PARTY` 规范 `externalLegRef`；其余 native channel/bridge 由 adapter 折叠。该会话引用必须保存在 durable correlation/session registry 中，以受控 originate correlation、呼入首腿和后续 leg/bridge 关系幂等解析；WebSocket 重连、连接世代变化或 adapter 进程重启后仍返回同一值。只把引用放在内存、`appArgs` 或单帧 ingress journal 中都不足以保证会话稳定。PBX 的 bridge ID、endpoint、channel technology 和完整腿图保留在私有侧；核心只保存完成幂等和生命周期仲裁所需的最小引用。

首期 `CONNECTED` 的统一语义应是“adapter 已识别坐席腿与对端腿，二者都已处于 answered/`Up`，并确认两腿之间存在首期拓扑预期的 bridge/communication 关系”，或 provider 提供语义等价的最终 connected 事实。早期媒体、progress、单腿 answered/`Up`、单独 bridge 创建或未确认成员关系都不足以推进。无法证明该条件时 adapter 宁可不发 `CONNECTED`，不能夸大状态。

## 4. 命令和时序

### 4.1 起呼

Asterisk ARI 支持 `POST /channels/{channelId}` 由调用方指定 channel ID，并通过 `appArgs` 或受控 channel variables 携带本地相关性；当同 ID 的活动 channel 已存在时返回 409。FreeSWITCH 可以用 `bgapi originate` 的 `Job-UUID` 跟踪命令完成，并通过受控 `origination_uuid` 固定 channel UUID。

这些能力只在活动 channel/session 范围内帮助抑制并发重复，不是历史命令幂等保证。若适配器在 PBX 已接收命令后、核心记录确认前崩溃：

- 查询到同 ID 的活动 channel 可以收敛为已接受；
- 查询不到只能说明当前不活动，无法区分从未创建、已失败或已快速结束；
- channel 终止后再次 originate 仍可能产生新呼叫，不能把复用 ID 当成永久 dedupe tombstone。

因此 PBX adapter 也需要持久化 command ledger，并向核心返回 `CONFIRMED`、`REJECTED` 或 `AMBIGUOUS`。若没有 CDR/CEL 或其他可查询终态事实，`AMBIGUOUS` 不能自动重试。ledger 只保存恢复命令所需的最小 correlation、状态和安全错误分类；完整号码、dial string、channel variables 或凭据若确需暂存，必须独立加密、tenant 分区并按短保留期销毁。

### 4.2 挂断

ARI `DELETE /channels/{channelId}` 和 FreeSWITCH `uuid_kill` 一类命令都作用于具体 leg。核心的“结束 interaction”意图应由 adapter 根据私有腿拓扑转换为必要的 PBX 命令；核心不接受客户端传入 channel ID，也不在公共端口暴露 Q.850/SIP cause、PBX reason code 或 dial string。

挂断响应只说明命令处理结果，最终 interaction 终止仍由后续规范事实或对账确认。目标已经消失可以按幂等 no-op 收敛，但不能反向证明此前所有 leg 的终态事实完整。

## 5. 事件映射草案

| PBX 事实 | 核心处理 | 限制 |
| --- | --- | --- |
| ARI `StasisStart` / channel create；ESL `CHANNEL_CREATE` | leg discovered/created | 不是振铃或接通 |
| ARI leg `Ringing`；ESL `CHANNEL_PROGRESS` | leg `ALERTING` 候选 | B22-S 外呼只允许对端腿推进 interaction `RINGING`，呼入只允许最终坐席腿推进；必须先按固定 topology 识别角色 |
| ARI leg `Up`；ESL `CHANNEL_ANSWER` | `LEG_MEDIA_READY` 候选 | 只是该 leg 与平台媒体就绪，不能直接映射 interaction `CONNECTED` |
| ARI bridge membership；ESL bridge facts | `PARTICIPANTS_CONNECTED` 候选 | 已识别的坐席腿与对端腿都 media-ready，且 adapter 确认这两条正确角色腿的首期双向媒体关系；早期媒体或任意 bridge 不足以推进 |
| ARI `ChannelDestroyed`；ESL `CHANNEL_HANGUP_COMPLETE` | leg terminated | interaction 仅在相关腿终止策略成立后结束 |
| PBX recording finished/failure | 录音可用性候选 | 文件、路径、下载、保留和媒体认证留在私有侧 |
| WebSocket disconnect / core restart | observation gap | 不能伪造正常终态；进入 uncertain/reconciliation required |

适配器可以把 native cause 映射为 `BUSY`、`NO_ANSWER`、`REJECTED`、`NETWORK_FAILURE` 或 `UNKNOWN` 等安全分类，但原始 cause、SIP header 和诊断文本不进入核心。晚到事实只能从较弱分类补充到不冲突的较强分类，不能改写已确定的相反终态。

## 6. 事件身份、去重和断线

建议把 B22 当前的 `externalEventRef` 拆为两个来源身份字段，由 adapter 提交：

| 字段 | PBX 语义 |
| --- | --- |
| `deliveryRef` | 必填；由 adapter 的 durable ingress journal 分配，在 adapter→core 重投时保持不变 |
| `sourceEventRef` | 可选；ARI 通常为空；FreeSWITCH 可使用 `(Core-UUID, Event-Sequence)` 作为来源参考 |

核心另行对不含直接敏感值的有界规范事实计算 `normalizedDigest`，或使用核心密钥 HMAC 检测同 delivery 不同内容；它不是 adapter 输入。

FreeSWITCH `Event-Sequence` 是整个 core 的递增序号。订阅过滤会自然产生序号间隔，因此单凭 gap 不能证明丢事件；`Core-UUID` 变化也只说明 core 世代变化，不提供旧事件 replay。

ARI `GET /events` 没有 `since`、cursor 或 replay 参数。连接断开后重新订阅只接收新事件。`GET /channels` 只能列出当前活动 channel，无法恢复断线期间已经结束的 interaction 终态。因此：

- 核心 inbox 只能保证 adapter 已经持久化后的 adapter→core 幂等重投；
- 直接把 WebSocket frame 同步转发到核心、但不先写 adapter journal，会在 adapter 崩溃时永久丢失；
- adapter journal 也无法恢复 PBX 在到达 adapter 前丢失的 frame；需要可选 PBX CDR/CEL sink 或厂商查询面补齐终态；
- 无法对账时核心必须显示 `OBSERVATION_INCOMPLETE`/`RECONCILIATION_REQUIRED` 一类安全事实，而不是假装通话已正常结束。

WebSocket 没有逐事件 HTTP ack。核心 ingest 的 accepted/duplicate/rejected 结果只控制 adapter 自己的 journal checkpoint，不能改变 PBX 原始事件的投递保证。

durable ingress journal 只解决单帧 adapter→core 重投，durable correlation/session registry 才负责把不同帧、legs 和连接世代稳定归并到同一 `externalInteractionRef`，两者不能混为一张无界原始事件表。B22-R 已固定 journal、session registry、command ledger 和 connection fence 的职责、容量、checkpoint、租约、重投与可靠性保留；B22-D 则固定 tenant/connector 加密分区、敏感 allowlist、policy deadline 与销毁。Caller ID、dial string、variables、SIP headers 和录音路径默认不写入，确需恢复时也只能进入有截止的独立加密 quarantine。

## 7. 已验证事实输入、核心 envelope 和私有原语边界

connector principal 是已经过私有连接认证的调用上下文，不是 adapter 可以在普通事实字段里声明的值。PBX 与云 Webhook 可以共享以下 adapter 输入：`deliveryRef`、可选 `sourceEventRef`、呼入发现时可空的本地 `interactionRef`、可选 `commandId`、adapter-scoped `externalInteractionRef`、leg 事实必填且首期最多两个规范 `externalLegRef`、可选 `externalRecordingRef`、[B22-S](B22-state-and-topology.md) typed lifecycle fact、方向、可选 `occurredAt`、安全终止分类和 observation gap/连接世代提示。呼入 Caller ID 只允许经专用、长度受限的敏感 `partyAddress` 进入受控匹配路径，不属于通用事实 map。

核心验证 connector principal 后自行从 opaque connector 注册派生 tenant，生成数据库 `receivedAt` 与规范 digest，并在呼入发现时幂等生成 `interactionRef`。这些值不能由 adapter 提交或覆盖。直接敏感值按 B22-D 使用 tenant/purpose-separated AEAD、HMAC、固定末四位展示和绝对截止；connector ingress 不取得 CRM 候选，后续人工建议/关联重新应用当前 caller 权限。

必须留在 PBX 私有适配器的内容包括：

- ARI/ESL base URL、用户、密码、证书、Stasis app、事件订阅和连接世代；
- PJSIP/SIP/Local/sofia 等 channel technology、endpoint、dial string、dialplan context、extension 和 gateway；
- bridge 拓扑、channel variables、任意 header/body map、DTMF、hold、transfer、conference 和 media 控制；
- Q.850/SIP cause、native hangup reason、codec、RTP 统计、SDP、SIP header 和原始诊断；
- 录音文件路径、文件名、媒体服务器、下载凭据、临时 URL、转写和清理策略；
- 坐席分机、软电话注册、设备在线状态，以及 CRM 成员到私有 endpoint/device 的配置；connector-scoped opaque external agent 到 CRM 成员的不可改绑目录由开源 control plane 治理，但不保存分机或设备地址。

公共端口不能接收任意 variables、headers、dial string 或 executable app name。`Map<String, Object>` 会把 PBX 控制面穿透到核心，继续禁止。

## 8. integration principal 与呼入

PBX 长连接由部署适配器认证，不存在每个 frame 的最终用户签名。B22 前置设计已经固定 connector principal 只携带已认证的 `connectorRef`；核心从 opaque connector 注册解析 tenant，永远不从 channel variable、caller ID、appArgs 或 frame body 接受 tenant/actor/authority。连接世代作为独立、有界的 gap/fencing 输入，不进入 principal，不能替代稳定的 session registry 或成为业务身份。

呼入 `StasisStart` 或 ESL 首事件没有本地 `interactionRef`。适配器应先通过 durable session registry 为该外部会话取得跨重启稳定的 `externalInteractionRef`，再以 `(connector, externalInteractionRef)` 调用单独的幂等 inbound discovery 入口，由核心生成 interaction。路由、IVR、ACD 和设备接听仍由 dialplan/私有适配器拥有。

PBX 的 external agent/extension 不能直接成为 CRM actor。需要受治理的 connector agent directory，把外部引用解析成同 tenant 当前活动成员；核心再次校验成员状态。无法解析时 interaction 保持未分配，不能借 caller ID 或请求变量伪造当前坐席。

一个核心 `connectorRef` 应只绑定一个 tenant。若同一物理 PBX 服务多个 tenant，私有部署必须把它拆成独立逻辑 connector、Stasis app/订阅或等价的强隔离路由，不能让 frame 中的 tenant 变量决定归属。

## 9. 正式实现阻断项

1. connector principal、核心 opaque connector 注册和 tenant 解析已由 [B22 第 12 节](../../batch-B22.md#12-integration-principal-与-connector-tenant-解析设计闭合)闭合；PBX 连接所有权、租约/fencing 和世代模型由 [B22-R](../B22-R-reliability.md) 闭合，独立 Stasis app/等价路由、`subscribeAll=false`、单 owner、最小订阅和 native ownership 隔离由 [B22-A](../B22-A-adapter-readiness.md) 固定。[B22-CP](../B22-CP-pbx-composed-contract.md) 已冻结这些条件的组合 fixture 与逐步 oracle；真实 adapter 仍须执行它们。
2. adapter durable ingress journal、correlation/session registry、command ledger 的职责、租约、重投、容量与保留已由 B22-R 闭合；直接敏感值的 allowlist、加密分区、policy deadline 和销毁已由 [B22-D 第 14 节](../../batch-B22.md#14-b22-d-敏感数据录音访问与保留设计闭合)闭合。实现 fixture 必须共同证明这些不变量，核心 inbox 不能替代 adapter store。
3. 无 replay PBX 的 `OBSERVATION_INCOMPLETE`、gap 和 CDR/CEL 对账语义已由 B22-R 闭合；真实 capability 探测仍属于第 7 项。
4. interaction 与两个规范参与腿，以及 ringing、connected、terminated 和 observation 的判定已由 [B22-S](B22-state-and-topology.md) 闭合；B22-CP 已把早期媒体、坐席/对端单腿 answered/Up、两腿 ready 但无关系、错误 bridge 和正确双向媒体证明固定为同一 trace，真实实现必须复用该 fixture。
5. originate 的 `CONFIRMED`/`REJECTED`/`AMBIGUOUS`、start guarantee 和 command ledger 已由 B22-R 闭合；[B22-PC](../B22-PC-pbx-command-contract.md) 已进一步冻结两种 guarantee 的 provider start crash matrix、event-before-response、权威不存在和跨 connector command oracle；不得把指定 channel UUID 描述成永久 exactly-once。
6. 呼入幂等发现已由 B22-R 闭合；external agent binding、CRM member 与私有 endpoint/device 的分层已由 [B22-S](B22-state-and-topology.md) 闭合，绑定管理 API 和真实 adapter 目录仍待实现设计。
7. 真实 adapter readiness/capability probe 已由 B22-A 固定为按操作、最长 5 分钟且绑定 build/revision 的快照，至少覆盖认证、Stasis app/ESL stream 注册与连接所有权、`read_only`/发起/挂断权限、活动 channel/bridge 快照、provider event ID/sequence、replay/cursor/终态对账能力，以及 B22-M 的 live/stored 边界、WAV/MP3、媒体读/确认删除、storage root/path 隔离、range/上限和 ARI/API 版本；周期探测不得执行破坏性控制或真实媒体读取。
8. 明确首期不支持 transfer、conference、hold、DTMF、ACD、IVR 和浏览器媒体，避免它们反向污染规范事件。
9. 点击外呼已由 [B22-S](B22-state-and-topology.md) 固定为 `OUTBOUND_AGENT_FIRST`；坐席腿 `MEDIA_READY` 前不得呼叫客户腿，PBX adapter 必须按两个规范角色腿和显式两方媒体证明映射。
10. B22-R 与 B22-D 已共同固定 journal/ledger/session registry 的职责、容量、租约、重放、敏感字段白名单、加密、tenant/connector 分区、保留和销毁，且禁止凭据、无界原始 frames、Caller ID、dial string、variables 和录音路径默认落盘；[B22-PD](../B22-PD-pbx-sensitive-data-contract.md) 已进一步冻结私有 adapter 的号码/key/retention/quarantine/log surface/确认销毁组合 oracle，真实实现仍须执行它。
11. B22-D 已固定可选 `externalRecordingRef` 的加密关联、媒体访问/删除授权、到期停访和确认删除；[B22-M](B22-media-transfer.md) 已固定 live/stored 到四态的映射、逐请求同源代理流、typed byte-stream、可选 single byte-range 和 PBX path/取消 fixture。云侧统一 checkpoint 见 [B22-CW](../B22-CW-cloud-callback-fixture.md)；PBX 侧录音状态与呼叫终态独立乱序、stored-object、完整/原生单 range、取消/上限和普通 404 不伪造 `REMOVED` 的组合后来由 [B22-PM](../B22-PM-pbx-media-contract.md) 闭合。

B22-A 已闭合第 1、7 项的 readiness/隔离设计语义，B22-CW 已闭合云 callback 组合契约，B22-CP 已闭合 PBX reconnect/session/topology/subscription/gap 的核心组合 fixture 设计，B22-PC、B22-PD 与 B22-PM 又分别闭合 command crash/`AMBIGUOUS`、敏感数据/确认销毁和 stored-recording/media 组合，但不表示 Asterisk 或 FreeSWITCH 已通过真实 fixture。真实 provider 组合仍未闭合，因此不选标准实现，也不拆 B22a/B22b。

## 10. 官方依据

- [Asterisk ARI Overview](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/)：ARI 的 REST、WebSocket 和 channel/bridge/endpoint/media 原语。
- [Asterisk ARI Channels API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Channels_REST_API/)：指定 `channelId` originate、409、`appArgs`/variables 和 hangup。
- [Asterisk ARI Events API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Events_REST_API/)：事件订阅参数及缺少 replay/cursor 的事实。
- [Asterisk ARI Applications API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Applications_REST_API/)：Stasis application 的资源订阅与取消订阅边界。
- [Asterisk ARI Channel State](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/Introduction-to-ARI-and-Channels/ARI-and-Channels-Manipulating-Channel-State/)：channel ringing、answer 和状态控制边界。
- [Asterisk ARI Bridges API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Bridges_REST_API/)：bridge 资源与 channel membership 控制面。
- [Asterisk ARI Recordings API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Recordings_REST_API/)：live/stored recording 对象、播放、删除和媒体控制边界。
- [Asterisk Configuration for ARI](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/Asterisk-Configuration-for-ARI/)：Stasis/WebSocket 前置和断线可能错过事件的官方说明。
- [Asterisk ARI Versioning](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/ARI-Versioning/)：API 版本兼容和模型演进约束。
- [Asterisk ARI Event Models](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Asterisk_REST_Data_Models/)：Event 基类与 channel/bridge 事件数据模型。
- [FreeSWITCH Event Socket](https://developer.signalwire.com/freeswitch/integration/event-socket/)：ESL 认证、命令、订阅、`bgapi`/`Job-UUID` 和事件传输。
- [FreeSWITCH Event Model](https://developer.signalwire.com/freeswitch/programming/event-model/)：`Core-UUID`、`Event-Sequence`、header/body 事件结构。
- [FreeSWITCH Events Catalog](https://developer.signalwire.com/freeswitch/programming/events-catalog/)：channel 生命周期、bridge、answer、hangup 和 recording 类事件。
