# B22 CRM 呼叫交互内核与私有外部适配器开源边界规划

> 基线：`dc50380 feat(B19b): 实现营销表单成功提交归因台账与统计`
>
> 对账日期：2026-07-19
>
> 状态：B22a 已按第 12 节实现 connector 安全、人工控制平面、独立认证主体、tenant 解析、限流和 registration 锁，并由 V58 与专用验收记录固化；它不包含呼叫事实 runtime。B22-R、B22-D、B22-S、B22-M、B22-A 及 B22-CW/CP/PC/PD/PM 已闭合后续可靠性、敏感数据、状态/拓扑、媒体、readiness 和组合 fixture 契约。B22b 已实际启动隔离 Asterisk 20.15.2 ARI/Stasis 与 SIPp 软件端点并验证 transport，但它准确报告 `PBX_REAL=BLOCKED_RUNTIME_CONFORMANCE`、`LINE_REAL=BLOCKED_EXTERNAL`；真实 adapter/runtime conformance 仍未闭合，除 B22a 外不拆后续实现子批次，也不预占其 API、错误码或 Flyway 版本

> 实现推进：B22c 已以 V82 建立 core-side interaction/request binding/start outbox/fact inbox/observation-gap 的最小 durable schema，所有关系均受 tenant/connector 复合约束，且没有号码、原始 provider 输入或媒体字段。私有 inbox 仅接收两枚 32-byte opaque digest，先锁定已验证 connector 再由 MySQL 唯一键仲裁 apply/duplicate/safe-conflict，并用 owner-only manifest 固化 MySQL 并发/隔离证据；该 core lane 仅为 `DURABLE_SIMULATED`，不启用 provider/runtime，也不改变 B22b 的 PBX/line evidence。

## 1. 范围结论

B22 不在当前 MIT 仓库内建设一套完整 PBX/ACD 呼叫中心，也不直接绑定某一家云通信厂商。开源部分定位为“CRM 呼叫交互内核”：保存 provider-neutral 的呼叫意图、规范生命周期、当前坐席和 CRM 业务关系，执行 tenant、成员、能力、数据范围与敏感字段校验，并向部署提供稳定的呼叫控制与事件归一化契约。

真实云通信、SIP/PBX 或 CTI 系统的接入实现保留为私有外部适配器。私有模块只能依赖开源 application 端口和输入契约，不得访问业务 repository、MyBatis mapper、PO 或数据库表，也不得要求 domain/application 引入厂商 SDK、状态枚举、签名算法或账号模型。

开源仓库保留一个确定性的 dev/test 模拟适配器和一个显式禁用适配器，用于验证端口、生命周期、幂等和失败语义。它们不会假装真实拨号或录音已经成功；生产未安装真实适配器时，呼叫能力必须显式不可用并在启动或调用边界失败关闭。

这意味着 B22 开源的是可审计、可替换的 CRM 集成内核，不以“无需任何电话基础设施即可使用完整呼叫中心”为产品承诺。

## 2. 开源与私有矩阵

| 能力 | 决策 | 所属边界 | 说明 |
| --- | --- | --- | --- |
| 呼叫意图、交互根和规范生命周期 | 开源 | domain/application/persistence | 不保存厂商原始状态或任意 payload |
| 当前成员发起呼叫与查询历史的用例 | 开源 | api/application/rest/facade | 显式 caller、tenant、能力和 CRM 记录权限 |
| CUSTOMER、CONTACT、LEAD 业务锚点 | 开源 | application/domain | 呼叫权限不扩大业务记录或电话号码字段可见性 |
| 规范呼叫控制输出端口 | 开源 | `application/port/out` | 只表达发起、终止、能力探测等稳定意图 |
| 已验证事件的规范输入契约 | 开源 | api/application | 只接受已归一化且可去重的事实，不接受原始 Webhook |
| inbox/outbox、幂等、重放与乱序仲裁 | 开源 | application/persistence | 外部副作用不能依赖普通 post-commit 监听器的尽力而为语义 |
| dev/test 模拟适配器与适配器契约测试套件 | 开源 | `infra/external`/tests | 证明核心不依赖某一家厂商即可测试 |
| 云厂商 SDK、HTTP API、SIP、ARI、AMI、ESL 或专有 CTI 协议 | 私有 | 独立外部适配器模块 | 不进入当前仓库依赖树 |
| 厂商 Webhook Controller、原始请求解析、验签和重放防护 | 私有 | 独立外部适配器模块 | 每个厂商的 URL、body、header 和签名规则分别实现 |
| tenant 到厂商账号/子账号、应用、线路或号码的映射 | 私有 | 外部适配器配置 | 核心只持有不透明 connector 引用，不持有厂商账号模型 |
| API key、secret、证书、SIP 密码和设备 token | 私有 | 部署 secret 管理 | 不进入普通请求、仓库样例、数据库明文或日志 |
| 号码购买、实名/资质、线路、SIP trunk 和计费 | 私有 | 厂商/运营部署 | 不抽象成 CRM 聚合 |
| 坐席分机、软电话/WebRTC 注册与设备在线状态 | 私有 | CTI/前端适配器 | 不把设备会话或媒体凭证放入 CRM 身份模型 |
| 人工录音媒体读取 | 开源控制、私有取流 | REST/application + 媒体适配器 | 核心逐请求授权并代理 WAV/MP3 字节流；provider URL、路径、凭据、转码和删除仍留在私有 adapter |
| ACD 队列、IVR、路由策略、预测式外呼、监听/强插 | 不纳入 B22 | 专业呼叫中心域 | 不用简单 CRUD 近似实时通信系统 |
| 外呼活动、名单清洗、并发节流和营销合规 | 不纳入 B22 | 后续独立批次 | 需要独立的活动、配额、同意/拒呼和审计边界 |
| Asterisk 或 FreeSWITCH 开源参考适配器 | 后置复审 | 独立可选模块 | 不作为 B22 开源内核的完成条件 |

## 3. 为什么不把真实适配器放进核心仓库

SIP 只定义会话建立、修改和终止等信令，并不统一 CRM 所需的坐席身份、队列、通话腿、录音就绪、厂商错误、请求签名和 tenant 账号映射。Asterisk ARI 暴露 channel、bridge、endpoint 和 media 等原语，通过 REST 与 WebSocket 控制；FreeSWITCH ESL 暴露原始命令和事件流；云厂商通常使用另一套异步 HTTP callback。

这些差异不是几个枚举别名可以消除：

- 一次 CRM “通话”在 PBX 中可能包含多条 channel/call leg，不能把外部 channel ID 当成本地聚合 ID；
- 进度事件可能重复、延迟或乱序到达，呼叫完成与录音可用也可能是不同事件；
- Webhook 验证可能依赖请求使用的完整 URL、原始 body、content type、header 和账号 secret，不能先映射成通用 DTO 再验签；
- 云账号、PBX 节点、线路、分机和号码属于部署拓扑，不属于 CRM tenant 业务模型；
- 录音和实时媒体具有独立的授权、保留、下载和合规风险，不能因为呼叫记录可读就直接暴露厂商 URL。

因此开源核心只接受“已经由受信任适配器验证并归一化的事实”，而私有适配器负责证明来源、解析厂商语义并映射为稳定契约。核心仍必须重新验证 connector、tenant、事件幂等和允许的状态推进，不能无条件信任一个普通请求体中的 tenant 或本地 call ID。

## 4. 开源核心的最小产品边界

### 4.1 首期纳入

- 当前活动成员从一个当前可见且电话号码字段可读、未脱敏的 CUSTOMER、CONTACT 或 LEAD 发起单次外呼；
- tenant-scoped 的 CRM 呼叫交互记录，区分呼入/呼出并保存规范里程碑、当前坐席、终止分类、审计时间和乐观锁版本；
- 厂商命令与事件的本地幂等关联，不把厂商 call ID、event ID 或错误文本作为公共业务标识；
- 当前坐席和被授权管理员的呼叫列表与详情；返回 CRM 关系前再次应用所属对象的当前能力、数据范围与字段策略；
- 对未匹配呼入只保存最小、默认脱敏的对端展示信息，后续人工关联仍要求操作者当前可见目标记录；
- 录音只公开 `UNAVAILABLE`、`PROCESSING`、`AVAILABLE`、`REMOVED` 一类 provider-neutral 可用性事实；B22-M 固定逐请求重新授权的同源服务端代理流，不签发浏览器媒体 token，不重定向到 provider；
- 呼叫终结后可为已关联业务记录追加一次服务端定义的 `SYSTEM` Activity 摘要，但 Activity 不是呼叫状态、事件 inbox 或录音元数据的所有者。

现有跟进方式中的 `CALL` 只是用户活动分类，不能复用为异步呼叫聚合。反向也不要求每个普通 `CALL` 跟进都触发真实拨号。

### 4.2 首期不纳入

- 浏览器软电话、WebRTC 媒体、SIP 注册、分机配置和设备切换；
- 呼叫等待、保持、转接、咨询、会议、静音、DTMF、监听、耳语或强插；
- ACD/技能队列、IVR、工作时间、溢出路由、黑白名单和语音导航；
- 批量/预测式外呼、机器人、自动语音、号码池、外显号码策略和外呼活动；
- 实时坐席大盘、队列 SLA、质检、评分、敏感词、情绪分析、摘要和知识推荐；
- 录音文件落入核心数据库/对象存储、转写索引、全文检索或分享链接；除 B22-D 到期停访与 adapter 确认删除外，不建设通用媒体资产/保留产品；
- 运营商/云厂商费用、余额、发票、资质、号码购买和跨区域合规配置；
- 用一个通用 provider 配置 API 接收任意 base URL、header、脚本、模板或状态映射表达式。

## 5. 稳定扩展契约

开源端口表达 CRM 意图和规范事实，而不是最低公共分母的厂商 DTO。最终命名在实现批次确定，但边界必须覆盖：

- `connectorRef`：由核心生成、全局唯一且不透明的部署连接引用；一个引用终身只绑定一个 tenant，不能由普通呼叫请求、规范事实或厂商字段切换 tenant 或账号；
- `interactionRef`：由核心生成的呼叫交互标识；已绑定事实回传它，呼入首个发现事实则不得伪造一个本地标识，由核心幂等创建后返回；
- `requestId` 与 `commandId`：前者约束人工发起请求的重试，后者约束本地 outbox 到 adapter 的一次呼叫意图；二者都不能凭自身承诺外部平台 exactly-once 拨号；
- dispatch 结果：至少区分 `CONFIRMED`、`REJECTED` 和 `AMBIGUOUS`；超时或崩溃窗口不得被解释为“未拨号”并盲目重试；
- `deliveryRef` 与 `sourceEventRef`：前者由 adapter 保证同一事实重投稳定并用于 inbox 唯一性，后者只在 provider 确实提供原生 event ID 时可选保存；
- `externalInteractionRef`、首期最多两个规范参与 `externalLegRef` 与可选 `externalRecordingRef`：分别关联 adapter 外部会话、一个 `AGENT`/一个 `REMOTE_PARTY` 参与腿和录音对象，都不直接对普通客户端公开；provider/PBX 的额外 native legs 留在私有 session registry；
- 规范方向、里程碑、可选 provider `occurredAt`、终止分类、录音可用性和 observation gap；核心自行生成 tenant、数据库接收时间与规范事实 digest，不信任 adapter 提交这些派生值；
- 专用敏感地址值：普通 CRM 请求只能选择当前可见、未脱敏的服务端字段，应用层将授权时号码快照通过受控内部端口交给 adapter；完整值不进入普通 API 输出、日志或通用事实字段；
- 显式的适配器能力集合，例如是否支持主动挂断、录音状态观察、受控 WAV/MP3 流、确认删除和可选单 byte-range；核心不得假定所有实现都支持同一实时控制能力。

不允许端口使用 `Map<String, Object>`、原始 JSON/XML、厂商异常、SDK 类型、HTTP request、SIP header 或可执行表达式。新增公共规范字段必须先证明至少两个不同适配器可以以一致语义实现，或明确只属于可选 capability。

## 6. 安全、租户与可靠性边界

- 人工发起、查询、关联和媒体访问用例继续显式接收 `AuthenticatedCaller`，通过 `CallerGuard` 和活动成员关系派生 tenant；普通请求不能声明坐席、connector、tenant 或管理员状态。
- 外部事件不能伪装成某个离职成员或 tenant owner。实现前必须确定一个开源、可审计的 integration principal/connector authentication 方案，并与“每个用例显式验证 caller”的仓库不变量对齐；不得让私有 Controller 直接写 repository。
- Webhook 中的 tenant、actor、authority、本地记录 ID 和外部状态都不是可信事实。适配器先验签并定位受治理 connector，核心再从启用的 opaque connector 注册派生 tenant，并按 connector 与已绑定 interaction 或呼入发现唯一键失败关闭。
- inbox 以 tenant、connector 和 `deliveryRef` 去重；核心对不含直接敏感值的规范事实计算 digest，相同 delivery 内容不同时失败关闭。乱序事件只能补充合法里程碑，不能把终态倒退为振铃或覆盖更新的事实。
- 外呼命令需要持久化 outbox、幂等 dispatch 和可恢复的不确定状态；普通 `@Async @EventListener` 或 post-commit 回调不能承诺拨号一定发生。
- MySQL 是 CRM 侧呼叫意图、权限关系、inbox/outbox 和规范投影的正确性来源；电话平台仍是实际信令、媒体和线路状态的权威来源。云 callback 可能因未订阅、有限重试或排队期取消而缺失，PBX 流则可在断线期间缺失事件；两类适配器都必须显式表达 observation gap 与对账能力，不得把本地事务或 inbox 误当成端到端可靠交付。
- 电话号码、录音引用、转写、原始厂商请求和错误可能包含敏感内容。第 14 节已经固定授权时拨号快照、呼入对端指纹、录音引用、私有 adapter journal/command ledger 的敏感 allowlist、加密/HMAC、tenant/connector 分区、保留与销毁；第 13 节 B22-R 已固定 adapter journal/session/ledger 的职责、容量、可靠性保留和 tombstone。公共日志只记录本地交互 ID、connector 和安全分类，不记录完整或脱敏号码、HMAC、凭证、原始 callback、录音 URL 或任意异常消息。
- B22-M 的人工媒体读取每个完整/range 请求都重新验证活动成员、呼叫/录音能力、参与或管理范围、关联 CRM 当前可见性、policy/deadline 和 connector capability。实际 provider I/O 在授权事务外经可取消的 typed stream 端口执行；核心不缓存媒体、不返回 3xx/URL/path/credential，也不在首字节后透明重试。
- 呼叫记录可见性与 CRM 业务记录可见性相互独立且只取交集；任何一方都不能向另一方授予能力或数据范围。查询必须在 SQL 分页和 `total` 前应用 tenant、参与关系和状态条件，关联输出再按所属业务域失败关闭。

## 7. 模块和发布边界

当前 MIT 仓库：

- domain/application/api 中的 provider-neutral 模型、用例、端口和错误语义；
- infra/persistence 中的 CRM 交互、inbox/outbox 与不透明引用持久化；
- infra/rest/facade 中面向 CRM 用户的调用、查询和 B22-M 同源媒体代理 API，不包含厂商 callback 路由；
- infra/external 中的禁用实现、dev/test 模拟实现和适配器契约测试工具；
- start 中只组装开源默认实现，不依赖私有 artifact 才能完成普通构建和测试。

私有适配器仓库：

- 每个厂商或 PBX 一个独立 artifact，依赖开源 application/api 契约；
- 原始 webhook/事件 socket、签名、SDK、重连、限速、厂商重试和状态映射；
- 账号、secret、证书、线路、号码、分机、设备与媒体配置；
- 部署时显式选择唯一或受治理的 adapter bean，不通过 classpath 偶然顺序决定；
- 运行与兼容测试必须复用开源契约测试套件，防止私有实现绕过 tenant、幂等或失败语义。

私有模块不得通过 Spring 扫描取得 persistence repository，也不得在外部 callback Controller 中自行创建/更新 Call 聚合。所有 CRM 状态变化必须经开源 application 用例完成。

## 8. 暂不选择开源 Asterisk/FreeSWITCH 适配器

Asterisk ARI/AMI 和 FreeSWITCH ESL 都能支撑自托管接入，但首轮不选择其中一个作为“标准实现”：

- 二者暴露的原语、事件模型、部署和安全配置不同，过早选定会让开源端口反向贴合首个实现；
- 自托管 PBX 仍需要 SIP trunk、号码、NAT、TLS、编解码、媒体和运维能力，不能作为当前 Java 仓库的零依赖默认功能；
- Asterisk 采用 GPLv2/商业双许可，FreeSWITCH 及其模块也需要逐项核对许可；任何参考适配器的分发方式都应在单独批次完成依赖和法律复审；
- 先用私有云厂商/PBX 适配器和开源模拟器共同验证契约，至少覆盖两种不同事件模型，再决定是否将一个网络边界参考适配器单独开源。

后续若开源参考适配器，应作为可选 module 发布，不把 PBX 二进制、配置、音频或运营商能力打包进 `atom-crm`，也不改变私有适配器可以并存的端口。

## 9. 外部事实依据

- [RFC 3261](https://datatracker.ietf.org/doc/rfc3261/) 将 SIP 定义为建立、修改和终止会话的应用层信令协议，并未定义 CRM 呼叫记录、坐席权限或录音治理。
- [Asterisk ARI 官方说明](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/) 表明 ARI 直接暴露 channel、bridge、endpoint、media 等通信原语，通过 REST 控制并经 WebSocket 发送异步事件；官方也建议在业务应用前建立抽象层。
- [FreeSWITCH Event Socket 官方说明](https://developer.signalwire.com/freeswitch/integration/event-socket/) 显示 ESL 同时提供命令执行、实时事件订阅、originate、bridge、DTMF 和媒体控制，属于 PBX 级适配面而非 CRM 领域 API。
- [Twilio Call Resource](https://www.twilio.com/docs/voice/api/call-resource) 明确进度 callback 是独立异步请求且不保证按发送顺序到达，录音就绪还需要独立 Recording callback；核心必须自行处理幂等、乱序与分离生命周期。
- [Twilio Webhook Security](https://www.twilio.com/docs/usage/webhooks/webhooks-security) 要求按完整 URL、编码形式、form 参数或原始 JSON body 验证签名，说明验签必须留在原始传输适配器边界。
- [Asterisk 许可说明](https://www.asterisk.org/products/software/) 说明其开源版本采用 GPLv2 并另有商业许可；参考适配器是否开源应独立完成分发和依赖复审。

## 10. 本轮不做的决定

- B22-S 已固定 `CallInteraction`/两条规范 `CallLeg`、正交状态维度、两个首期 topology 和状态侧 typed capability，B22-M 已固定单录音、逐请求代理流、WAV/MP3、单 range、审计/取消和媒体 capability 的规划语义；最终 Java 包名、端口名、URI、表、错误码、公共 Response 和 Flyway 版本仍不预占；
- 不选择首个云厂商、PBX、SIP trunk、SDK 或 Java 客户端；
- 不确定私有 artifact 的发布仓库、商业授权或计费方式；
- 除已经明确授权并实现的 B22a 安全与控制平面外，不实现呼叫 runtime、Docker 电话环境或前端软电话；
- 不预占 B22b 及后续子批次；状态/腿/拓扑、可靠 outbox/inbox、号码/录音敏感数据策略、媒体传输、真实 adapter readiness/隔离、两个适配器映射草案、B22-CW 云组合、B22-CP PBX 核心组合、B22-PC PBX command 组合、B22-PD PBX 敏感数据组合和 B22-PM PBX stored-recording/media 组合虽已通过复审，仍须补齐真实 adapter conformance 才能拆后续实现任务。

B22 的本次复审按上一版要求输入两份只读适配器 spike：一个云 Webhook 模型和一个 PBX 事件流模型。第 11 节记录对账结果；它们只验证开源契约是否足够稳定，不提交厂商代码。

## 11. 适配器只读 spike 对账

本轮已完成两份独立调研：

- [云平台 Webhook spike](docs/spikes/B22-cloud-webhook.md)：以 Twilio Call、Status Callback、Recording Callback 和 Webhook Security 为代表；
- [PBX 事件流 spike](docs/spikes/B22-pbx-event-stream.md)：以 Asterisk ARI 为主模型，并用 FreeSWITCH ESL 校验事件身份、call leg 和断线差异。

两类适配面共同验证了以下边界：

| 稳定概念 | 云 Webhook | PBX 事件流 | B22 结论 |
| --- | --- | --- | --- |
| 起呼命令 | 异步 HTTP，成功后取得 `CallSid` | REST/命令 socket，可指定 channel UUID 或取得 Job UUID | 本地 outbox 与外部 dispatch 结果分离，必须保留 `AMBIGUOUS` |
| 呼叫对象 | Call resource，可出现 parent/child legs | channel、bridge、endpoint，多条 call legs | interaction 不能等同于单一 `externalCallRef`；B22-S 核心只保存 adapter 会话引用与两个规范参与腿，额外 native 图留在 adapter |
| 事件身份 | 有 `CallSid`、callback source、sequence 和 HTTP retry 元数据 | ARI 无 event ID/sequence；ESL 序号属于整个 core | `deliveryRef` 必填且由 adapter 保证重投稳定，provider `sourceEventRef` 只能可选 |
| 顺序与重放 | callback 可乱序；未订阅、有限重试或排队期取消都可能留下观测缺口 | 长连接可断；ARI 无 replay，活动 channel 查询补不回已结束事件 | 核心状态机单调推进，两类适配器都显式支持 observation gap/对账 |
| 接通语义 | answered 仍可能只描述一条 leg | channel answer/up 不等于双方已 bridge | `CONNECTED` 必须由 adapter 按首期媒体拓扑确认 |
| 录音 | Call 完成回调中的 URL 可能尚不可用；recording status `completed` 才证明就绪 | live/stored recording 与文件读取属于 PBX 原语 | B22-M 统一为单录音四态和核心逐请求代理流；URL、路径、凭据和 native 对象继续私有 |
| 呼入首事件 | 首个 Webhook 尚无本地 interaction | `StasisStart`/ESL 首事件尚无本地 interaction | 需要 connector-scoped 幂等 inbound discovery，不能强制预带 `interactionRef` |
| readiness 与隔离 | 受治理 callback route、完整验签、account/resource ownership、显式 callback 与最小权限 | 独立 Stasis app/固定 ESL stream、单 owner、最小订阅、native ownership、权限与版本 | B22-A 使用按操作短期 snapshot；route/stream 在解析 body 前选 connector，漂移先撤销 start 再打开 gap |

### 11.1 复审结论回写

第 5、6 节已经按两份 spike 回写，是本批次唯一的规范契约；本节只保留复审证据，不再作为覆盖正文的修正层：

- 事件身份拆为 adapter 保证重投稳定的必填 `deliveryRef` 和可选 provider `sourceEventRef`。核心生成数据库接收时间与不含直接敏感值的规范 digest；同 delivery 不同内容失败关闭。
- 命令 dispatch 与 interaction 生命周期分开。transactional outbox 不自动提供 exactly-once 拨号；适配器明确 `CONFIRMED`、`REJECTED`、`AMBIGUOUS` 及 start guarantee capability。
- adapter-scoped `externalInteractionRef` 关联一次外部会话；B22-S 首期只允许一个 `AGENT` 和一个 `REMOTE_PARTY` 规范 `externalLegRef`，并保留可选 `externalRecordingRef`。额外 native legs/bridge 由 adapter session registry 折叠；外部会话引用必须经该 durable registry 保证重连和进程重启后稳定，不能只依赖内存或单帧 journal。
- `RINGING`、`CONNECTED` 和 `TERMINATED` 是 interaction 级规范里程碑。`CONNECTED` 必须确认已识别的坐席腿与对端腿都已接听，并处于预期 bridge/媒体关系；早期媒体、单腿 answered/up 或任意 bridge 事件均不足以证明接通。
- 呼入发现事实不预带本地 `interactionRef`。核心从已认证 connector 派生 tenant，按外部会话引用幂等创建 interaction；完整呼入号码只通过专用敏感地址输入进入受控匹配路径。
- 完整拨号目标使用授权时的服务端快照，经专用内部值对象交给 adapter；呼入号码以加密值、tenant-scoped HMAC 指纹和脱敏展示中的最小必要组合治理。它们不进入普通 API、日志或通用生命周期事实。
- 云 callback 和 PBX 事件流都会形成 provider→adapter 观测缺口。HTTP 2xx、WebSocket checkpoint 和 socket flow control 留在私有传输适配器；公共 ingress 只返回 accepted/duplicate/rejected，并用 observation gap/对账 capability 显式表达不完整性。
- B22-M 把录音媒体固定为最多一个受治理对象。人工端只用现有认证对核心发起同源 GET；完整/单 range 每次重新授权并由核心代理规范 WAV/MP3 bytes，provider URL/path/header/credential、live recording 和多 track 都不能穿透端口。

### 11.2 实现门槛闭合状态

1. **Integration principal（已闭合）**：第 12 节已经固定独立 `AuthenticatedConnector`、独立进程参考 ingress、connector 专用核心凭据、全局 opaque connector 注册和精确 pre-tenant 解析；它不复用 `AuthenticatedCaller`，不携带 tenant、actor 或 authority。连接世代也不属于身份。
2. **双重幂等（B22-R 已闭合）**：[B22-R](docs/B22-R-reliability.md) 已固定 caller-scoped `requestId`、本地 interaction/request binding/outbox 单事务、核心至少一次投递同一 `commandId`、adapter durable command ledger、`IDEMPOTENT_START(window)`/`AT_MOST_ONCE_START` 和 `AMBIGUOUS` 禁止盲重试。
3. **事件可靠性（B22-R 已闭合）**：[B22-R](docs/B22-R-reliability.md) 已固定 adapter durable ingress journal、核心短事务 inbox、稳定 delivery/digest、gap/observation assessment、durable reconciliation case、权威不存在门槛，以及云/PBX 不同缺口的 recovered/partial/unrecoverable 语义。
4. **状态和腿模型（B22-S 已闭合）**：[B22-S](docs/spikes/B22-state-and-topology.md) 已固定 interaction 根、最多一个 `AGENT` 与一个 `REMOTE_PARTY` 规范腿、正交 lifecycle/dispatch/observation/recording、单调里程碑、终止分类补充规则和 `OBSERVATION_INCOMPLETE`；早期媒体、单腿 media-ready 或没有两方媒体关系证明都不能产生 `CONNECTED`。
5. **号码边界（B22-D 已闭合）**：第 14 节已经固定服务端字段选择、保守 E.164 规范化、授权时不可变外呼快照、呼入加密/HMAC/固定脱敏值、tenant 分区、轮换、访问、保留和销毁；普通请求仍不能提交任意号码。
6. **坐席与设备（B22-S 状态侧已闭合）**：[B22-S](docs/spikes/B22-state-and-topology.md) 已分离 CRM member、connector agent binding 和私有 endpoint/device；核心只接收不可改绑的 opaque external agent 引用和最小 configuration/presence capability，设备选择、地址、注册与 token 不进入核心，外部事件仍不能直接声明 CRM actor。
7. **呼入和媒体（B22-M 已闭合）**：B22-R 已固定呼入发现幂等，第 14 节已经固定未匹配呼入的敏感值、加密录音引用、独立媒体授权和删除截止边界；[B22-M](docs/spikes/B22-media-transfer.md) 进一步固定单录音、四态映射、逐请求同源代理流、WAV/MP3、可选单 byte-range、typed adapter stream、审计、取消和资源上限，核心禁止保存或返回厂商媒体 URL/path/credential。
8. **契约测试（云、PBX 核心、PBX command、PBX 敏感数据与媒体组合已闭合）**：[B22-CW](docs/B22-CW-cloud-callback-fixture.md) 已固定云 callback 的重复、乱序、callback-before-response、两腿状态、录音晚到、媒体取消和 readiness 漂移检查点；[B22-CP](docs/B22-CP-pbx-composed-contract.md) 已用同一双 connector trace 冻结 PBX dirty reconnect/fencing、durable session registry、early media、单腿 answered、无关系的两腿 ready、正确双向媒体证明、订阅/ownership 隔离和 observation gap；[B22-PC](docs/B22-PC-pbx-command-contract.md) 进一步冻结 core outbox/adapter ledger 重投、`AT_MOST_ONCE_START` 与 `IDEMPOTENT_START(window)` crash matrix、event-before-response、`AMBIGUOUS` 只读对账、权威不存在和跨 connector command 隔离；[B22-PD](docs/B22-PD-pbx-sensitive-data-contract.md) 再冻结号码快照/呼入 HMAC、key 轮换、retention、quarantine allowlist、PBX CDR/CEL/log surface、restore-before-service 和分层确认销毁；[B22-PM](docs/B22-PM-pbx-media-contract.md) 又冻结 PBX 录音晚到、stored-object 证明、完整/原生单 range、取消、hard limit、durable removal 和权威删除确认。真实 PBX canary 组合仍待闭合。
9. **呼叫拓扑（B22-S 已闭合）**：首期外呼固定 `OUTBOUND_AGENT_FIRST`，坐席腿 `MEDIA_READY` 前禁止创建或呼叫对端腿；呼入固定 `INBOUND_AGENT_BRIDGE`，私有 IVR/队列/holding 不算坐席参与或接通。两种方向都只在正确两腿的双向媒体关系成立后进入 `CONNECTED`。
10. **PBX 会话与敏感 journal（已闭合）**：[B22-R](docs/B22-R-reliability.md) 已固定 journal、command ledger、correlation/session registry、connection fence 的职责分离、容量、tenant/connector 分区和 tombstone 保留；第 14 节同时固定其中直接敏感数据的 allowlist、加密、到期与销毁上限，禁止以明文或无界原始请求实现。
11. **真实 adapter readiness 与隔离（B22-A 已闭合）**：[B22-A](docs/B22-A-adapter-readiness.md) 已固定按操作的短期 readiness snapshot、binding revision/sequence、provider route 在解析前选定 connector、native ownership registry、三档隔离证明、配置漂移 gap、云 callback 与 PBX stream profile，以及认证、app/连接所有权、发起/挂断权限、活动资源快照、event/replay/对账、B22-M 媒体读删/格式/range/资源上限和 API 版本的证据门。Asterisk 每 connector 独立 Stasis app 且 `subscribeAll=false`；FreeSWITCH 宽权限 ESL 只能声明 dedicated boundary 或 adapter-enforced trust-zone 隔离，不能伪装成 provider 细粒度授权。

两份映射草案和统一契约已经完成复审。第 12 节身份/tenant 来源已独立拆成 B22a 并实现；其余第 1 至 7、9 至 11 项已完成前置设计闭合，第 8 项的云 callback、PBX 核心、PBX command、PBX 敏感数据与 PBX stored-recording media 组合也分别由 B22-CW、B22-CP、B22-PC、B22-PD、B22-PM 闭合。真实 provider 跨设计组合仍未闭合，因此除 B22a 外的呼叫 runtime 继续停留在规划/调研阶段，不预占 B22b 及后续实现。

## 12. Integration principal 与 connector tenant 解析设计闭合

> 决策日期：2026-07-19
>
> 实现状态：本节的认证主体、connector 注册、tenant 解析、失败关闭与 registration 锁已拆为 B22a 实现；具体路由、类型、V58 和验证证据见 [B22a 验收记录](docs/acceptance/B22a.md)。呼叫事实、inbox/outbox、interaction 与 adapter runtime 仍不属于 B22a。

### 12.1 决策摘要

B22 使用两种不可互换的受信主体：

- 人工发起、查询、关联、管理和媒体访问继续显式接收 `AuthenticatedCaller`，通过既有 caller/member/authority 路径派生 tenant；
- adapter→core 的规范化 ingress 显式接收独立的 `AuthenticatedConnector`。它唯一有身份语义的值是已经过核心入口认证的 `connectorRef`，另携带同一认证服务实例签发、不可由普通 bean 伪造的进程内 evidence；它不包含 `tenantId`、actor、member、role、authority、provider account 或连接世代；
- 不引入带可空 actor/connector 字段的通用 `Principal`，不把 connector 模拟成 tenant owner、系统用户或外部坐席，也不让 `CallerGuard` 接受 integration principal；
- 每个入口只接受一种主体。人工主体不能调用 connector ingress，connector 主体不能调用人工 facade，类型和路由两层都失败关闭。

B22a 使用 `ConnectorPrincipal` 表达 Spring Security 已认证身份，API/application 边界使用带同一认证服务 evidence 的 `AuthenticatedConnector` 作为显式用例参数，并由专用 mapper 原样传递；`ConnectorGuard` 在 identity lookup 前验证 evidence。两者没有合并回 `ActorPrincipal` 或 `AuthenticatedCaller`。

### 12.2 参考认证拓扑

首期以**独立进程的规范化 ingress**作为安全参考拓扑：私有云 Webhook/PBX adapter 先在自己的原始传输边界验证厂商签名、连接或事件来源，再通过受限网络上的 TLS 连接调用核心规范化 ingress。核心不暴露厂商 callback 路由，也不接收原始 Webhook、socket frame 或 PBX 凭据。

每个逻辑 connector 使用独立的核心 bearer credential，B22a 已实现格式 `connectorRef.keyId.secret`：

- `connectorRef` 是核心生成的高熵、全局唯一 opaque locator，不编码 tenant，也不是秘密；
- `keyId` 在 connector 内大小写敏感且唯一，用于有界轮换查找；
- `secret` 由核心生成并具有至少 256 bit 随机熵，只在创建或轮换时返回一次，核心只保存单向 verifier，完整 credential 不进入日志、普通响应或事实 envelope；
- 一个 connector 最多同时保留两个活动 key 以完成无停机轮换；停用或退役 connector 会使全部 key 立即失效；
- credential 只出现在认证头，规范事实 body 不携带 connector、tenant、actor 或 authority。若路由为了定位凭据包含 connectorRef，它也只能是认证 locator，且必须与 credential 中的值完全一致。

即使 ingress 只暴露在受限网络，认证查询前仍需要网关/来源级有界限流，验证成功后再进入按 connector 分区的配额；同一 connector 的轮换 key 共享该配额，撤销单 key 不重置已消费窗口。伪造 locator 不能创建无界 tenant/connector 分区或驱逐仍有效的已验证分区。未知 connector、未知 key 和 secret 错误使用相同外部响应与近似成本，credential 或 identity 认证存储异常都返回可重试不可用；credential lookup 异常不计作无效凭据并允许相同投递安全重试。

mTLS 可以作为 workload 级纵深保护，但共享 adapter 证书不能单独选择 tenant。若部署只使用 mTLS，则证书身份必须一对一绑定逻辑 connector，并进入完全相同的注册解析流程。首期不提供“任意同进程 bean 直接构造已认证 connector”的绕过；以后若支持同进程私有 artifact，也必须经过同一 connector authenticator 和应用 guard，同进程本身只能被描述为受信部署选择，不能被包装成密码学隔离。

provider→adapter 与 adapter→core 是两个独立认证跳：Twilio 签名、ARI/ESL 登录或私有网络只证明前一跳，不能替代核心 connector credential；核心 credential 也不能证明某个原始 provider callback 已正确验签。一个私有 adapter 进程可以服务多个 tenant，但必须为每个逻辑 connector 保存独立 credential、日志分区和会话状态，不能用共享工作负载身份加事实 body 中的 tenant 来复用。

### 12.3 Opaque connector 注册

核心拥有最小、可审计的 `ConnectorRegistration` 概念，至少固定以下不变量：

- 一个 tenant 可以拥有多个 connector；一个 `connectorRef` 在整个系统中最多对应一个 registration；
- `connectorRef → tenantId` 是一对一、终身不可改绑的关系。迁移到另一个 tenant 必须创建新 connector；停用、退役或租户清理后旧引用保留 tombstone，永不复用；
- registration 只保存核心身份、tenant 绑定、启停/退役状态、credential verifier、版本和审计时间。厂商账号、Account SID、PBX node、Stasis app、线路、号码、secret、证书、endpoint 和 provider 类型配置继续留在私有 adapter；
- connector 管理属于人工 control plane，用独立管理能力和 `AuthenticatedCaller` 创建、轮换、停用或退役。管理请求同样不能提交目标 tenant；tenant 只来自 caller；
- 活动状态只授权进入 connector ingress，不产生 CRM authority、数据范围或成员身份。已存交互的人工可见性继续由呼叫权限和关联业务对象当前可见性的交集决定。

credential key 是 registration 的有界认证子记录，不是业务 authority。轮换不改变 connectorRef 或 tenant；退役不可逆。需要临时停流时使用可恢复停用，安全事件或永久下线使用不可恢复退役。两种状态都拒绝新 ingress 和新 dispatch；已提交但尚未认证到核心的 adapter journal 项保留原 `deliveryRef`，恢复或换新 connector 后不得偷偷改写成另一个 tenant 的投递。

### 12.4 Tenant 解析与仓储例外

`AuthenticatedConnector` 不缓存 tenant。每个 integration 用例在读取 inbox、interaction 或任何业务 repository 之前，通过专用 `ConnectorGuard` 对 `connectorRef` 做一次当前状态解析，得到只在该次用例内使用的 `(TenantId, ConnectorId/ConnectorRef)` context。停用、退役、未知或发生歧义时都在业务访问前失败关闭。

这要求对“所有仓储调用必须预带 tenant”增加两个职责分离、但共享相同限制的极窄 security lookup：

- transport authenticator 使用 `ConnectorCredentialLookup` 按 `(connectorRef, keyId)` 做单行精确查询，只取得活动状态和校验 secret 所需的 verifier；verifier 不进入 principal 或 application ingress input；
- application guard 使用 `ConnectorIdentityLookup` 按完整、规范化的 `connectorRef` 做单行精确查询，只取得内部 connector identity、tenant 绑定、当前状态和版本；
- 两个 lookup 都不提供列表、前缀、模糊、provider account、跨 tenant 扫描或通用 aggregate 读取，也不能互相扩大返回投影；
- connector 的人工管理 repository 以及解析后的 interaction、inbox、outbox、agent mapping、cache 和审计操作仍全部显式 tenant scoped；
- 数据库对 `connector_ref` 建立全局唯一约束，并用 tenant/connector 复合键或等价约束重复保护 inbox、interaction 和外部引用，避免应用错误把已解析 connector 带入另一个 tenant；
- lookup 基础设施异常是可重试的服务不可用，不得降级成未知 credential、默认 tenant 或空权限主体。

这些例外只负责从 opaque、精确的 locator 验证 credential 并建立 tenant 上下文，不推广为任意公共 ID 的跨 tenant 查询模式。现有 lead-intake 的 tenant locator + `ActorPrincipal` 复用是其既有专用契约，不作为 B22 connector identity 的先例。

早期 guard 解析不足以单独承诺停用竞态安全。integration command 在自己的写事务内、首个 inbox/interaction 写入之前，还必须按已解析的 tenant/connector 锁定 registration 并再次要求其活动；停用和退役命令锁定同一行。这样两者形成明确全序：先取得锁并完成的 ingress 可以提交，先完成的停用/退役会使后续 ingress 拒绝。单个 key 的轮换或撤销阻止后续认证，但不追溯取消已经完成 transport 认证的请求；需要强制切断所有在途请求时必须停用或退役 connector。

### 12.5 规范 ingress 流程

统一流程固定如下：

1. 私有 adapter 从受治理路由或部署配置选择一个逻辑 connector，先按厂商原始 URL/body/header、PBX 连接或 journal 事实验证来源；不得从 Account SID、channel variable、caller ID、appArgs 或规范事实 body 选择 tenant。
2. adapter 使用该逻辑 connector 的核心 credential 调用规范 ingress。核心认证适配器按 `(connectorRef, keyId)` 精确定位活动 credential，以恒定时间校验 verifier，构造只含 connectorRef 的 `ConnectorPrincipal`。
3. connector mapper 只接受这个基础设施 principal，并原样传递认证服务签发的 `AuthenticatedConnector`；直接构造、普通 JSON binder、`ActorPrincipal`、trusted headers 和人工 bearer token 都不能产生可通过 application guard 的 evidence。
4. application 的 `ConnectorGuard` 重新解析当前活动 registration，派生 tenant 和内部 connector identity，并在业务 repository 访问前失败关闭明显无效的 connector。
5. command 写事务锁定同一 tenant/connector registration 并再次检查活动状态；锁 API 在没有活动事务时立即失败，避免自动提交释放 `FOR UPDATE` 后继续写入。取得锁后再写入 inbox 并按 `(tenant, connector, deliveryRef)` 去重；事实中的 interaction、external interaction、leg、recording 和 command 关联必须再次属于同一 tenant/connector。
6. 呼入发现按 `(tenant, connector, externalInteractionRef)` 幂等生成本地 interaction；已绑定事实不能借本地 `interactionRef` 跳到其他 connector。相同 delivery 的 digest 不同则安全拒绝。

人工外呼走相反方向但不改变该身份模型：人工用例先从 `AuthenticatedCaller` 派生 tenant，并由服务端 tenant-scoped 配置选择 connector；普通请求不能提交 connectorRef。outbox 固定 tenant/connector 后才交给私有 adapter。后续 dispatch 结果和生命周期事实重新从 connector principal 进入，不能继承发起人的人工权限。

PBX 的 `connectionGeneration`、WebSocket 世代、journal checkpoint 和 observation gap 是有界的可靠性事实，不属于 `AuthenticatedConnector`，不能选择 tenant、替代 `deliveryRef` 或恢复成员身份。外部 agent/extension 也只作为 connector-scoped 引用进入受治理目录；核心在解析 tenant 后映射并复核当前活动成员，无法解析时保持未分配。

### 12.6 失败与生命周期语义

| 场景 | 规划结果 |
| --- | --- |
| credential 缺失、格式错误、未知 connector/key、secret 不匹配 | 返回相同的安全认证失败，不泄露 connector 是否存在 |
| credential 已验证，但 registration 在早期 guard 前被停用/退役 | 在任何业务仓储访问前拒绝，不接受事实 |
| registration 在早期 guard 后与 ingress 写事务并发停用/退役 | 两条命令锁定同一 registration 形成全序；停用先完成则 ingress 拒绝，ingress 先完成则该次事实可以提交 |
| connector lookup 或 verifier 存储不可用 | 返回可重试的服务不可用；adapter 以相同 `deliveryRef` 重投 |
| body 声明 tenant、connector、actor、authority 或与认证 locator 冲突 | 拒绝整个输入，不采用或覆盖认证上下文 |
| 已认证 connector 引用其他 tenant/connector 的 interaction、command 或外部会话 | 使用不泄露目标存在性的 scope-mismatch/not-found 语义拒绝 |
| connector 停用后仍有 provider 事件或本地 journal backlog | 保留私有 backlog，不改写身份；只有原 connector 恢复后才能按原 delivery 重投，退役则需要显式运维处置 |
| credential 轮换 | connectorRef、tenant 和既有幂等键保持不变；旧 key 在重叠窗口结束后立即失效 |

认证成功不等于事实被接受。规范校验、状态单调性、digest、inbox 唯一性和 observation gap 仍由 B22 后续前置分别决定；HTTP/transport acknowledgement 也不能被解释为 provider 终态完整。

### 12.7 B22a 验收与后续契约测试门槛

B22a 已落地主体不可互换、服务端生成及一次性 credential、hash-only verifier、两个精确 pre-tenant 投影、全局 ref 唯一与 tenant/ref 复合外键、两 key 轮换、撤销/停用/退役、认证容量隔离和 registration 锁并发顺序。完整证据见 [B22a 验收记录](docs/acceptance/B22a.md)。后续事实 ingress 拆分前仍须在其具体事实契约中继续满足：

- `ActorPrincipal`/`AuthenticatedCaller` 不能进入 connector ingress，`ConnectorPrincipal`/`AuthenticatedConnector` 不能进入人工 facade；
- tenant、connector、actor、authority 和外部 agent 出现在普通事实 body 时严格拒绝；未知 JSON 属性也失败关闭；
- 两个 tenant、两个 connector 和同一多租户 adapter 进程的 fixture 证明每个 credential 只解析到唯一 connector/tenant，不能交叉重放；
- connectorRef 全局唯一、tenant 绑定不可更新、退役引用不可复用，credential 轮换不改变 tenant 或幂等作用域；
- 停用/退役发生在 transport 认证与 application guard 之间时仍在 business repository 访问前拒绝；真实 MySQL 并发测试还要证明 guard 之后的 ingress 与停用命令锁定同一 registration 并形成上述全序；
- 两个 pre-tenant security lookup 都只有精确单行查询、投影职责不混合，并对未知、重复脏数据和基础设施异常分别失败关闭；解析之后每个 repository/cache 调用都显式携带 tenant；
- interaction、command、inbox、`externalInteractionRef` 和 agent directory 的复合 tenant/connector 约束能阻止错误应用参数形成跨租户关联；
- PBX 连接世代变化不改变 principal、tenant 或 `externalInteractionRef`，仅产生 gap/fencing 输入；
- credential、原始认证头、secret verifier、完整 callback 和 provider 错误不出现在日志、稳定错误或普通响应中。
- 认证洪泛 fixture 证明伪造 locator 只占用独立有界的 pending/source 容量，不能制造 tenant 分区、淘汰活动 connector 配额或把认证基础设施故障记成无效 secret。

后续事实 ingress 清单必须同时覆盖 HTTP 独立进程入口和 dev/test 模拟 adapter 的同语义调用路径。B22a 只实现身份与 tenant 来源及可复用 registration 锁；双重幂等、outbox/inbox、journal、gap 和对账由第 13 节 B22-R 闭合，号码、录音引用和敏感 journal 由第 14 节 B22-D 闭合，状态、多腿、坐席设备与首期拓扑由第 15 节 B22-S 闭合，媒体传输由第 16 节 B22-M 闭合，真实能力探测与隔离由第 17 节 B22-A 闭合；云 callback 组合由第 18 节 B22-CW 闭合，第 19 节 B22-CP 闭合 PBX 核心组合面，第 20 节 B22-PC 闭合 PBX command 组合面，第 21 节 B22-PD 闭合 PBX 敏感数据组合面，第 22 节 B22-PM 闭合 PBX stored-recording/media 组合面，真实 provider fixture 仍待闭合。

## 13. B22-R 可靠性前置设计闭合

> 决策日期：2026-07-19
>
> 完整设计：[B22-R 呼叫可靠性、幂等与对账设计](docs/B22-R-reliability.md)

B22-R 冻结以下可靠性边界：

- 人工 `requestId` 只约束本地呼叫意图创建；request binding、interaction 和 start outbox 在一个核心事务中提交；
- 核心至少一次重投同一 `commandId`，adapter 在触达 provider 前先写 durable command ledger。核心传输不确定只会重投同一命令，provider 起呼不确定才进入 `AMBIGUOUS`；
- adapter 明确声明 `IDEMPOTENT_START(window)` 或 `AT_MOST_ONCE_START`。`AMBIGUOUS` 只能由 callback、provider 查询、replay/CDR/CEL 或权威不存在证据收敛，永不自动补偿拨号；
- provider 事实先写 adapter durable ingress journal，再用稳定 `deliveryRef` 投递核心。核心 inbox 与规范投影在一个短事务内提交，相同 delivery/digest 返回首次结果，不同 digest 失败关闭；
- PBX/Webhook gap、observation assessment 和 reconciliation case 都是 durable 事实。连接恢复或非权威 `NOT_FOUND` 不证明事件完整，也不证明呼叫未发生；
- journal、command ledger、session registry 和 connection fence 职责分离；未解决记录不得被 TTL/LRU 淘汰，最小 tombstone 至少保留 30 天且不短于相邻重投、callback 和对账窗口。

B22-R 同时给出 core MySQL、adapter crash point、云/PBX gap 和对账的契约测试矩阵。它没有闭合 interaction/leg 状态机、首期呼叫拓扑、密钥托管与号码保留、坐席设备和媒体授权，因此仍不触发正式实现拆批。

## 14. B22-D 敏感数据、录音访问与保留设计闭合

> 决策日期：2026-07-19
>
> 范围：`B22-D` 是实现前设计检查点，不是已经拆出的实现子批次。本节只固定号码快照、呼入指纹、录音引用、密钥、访问、审计、保留和销毁不变量；不预占 HTTP 路径、Java 类名、能力名、错误码、表名或 Flyway 版本。双重幂等和 session registry 可靠性由 B22-R 独立固定；呼叫状态、多腿拓扑和媒体传输仍不属于本节。

### 14.1 决策摘要

- 人工外呼请求只提交当前 CRM 业务记录和字段选择，不提交电话号码、connector、tenant 或外显号码。应用在写事务内重新验证当前成员、呼叫能力、业务记录数据范围及字段 `READ` 且 `mask=NONE`，从权威记录生成一次不可变号码快照。
- 可拨地址使用版本化的保守规范化规则。首期只接受完整 E.164；不推断国家/地区、不把 `00` 改成 `+`，不解释分机、pause/wait、SIP URI 或任意 dial string。不能规范化的 `telephone` 或自定义 `PHONE` 值只是不可拨，不能交给 adapter 自行猜测。
- 完整外呼快照和 `PRESENT` 呼入号码都使用 tenant-scoped、purpose-separated 的 AEAD 加密。呼入号码额外保存 tenant-scoped HMAC-SHA-256 等值指纹；禁止无密钥 SHA、截断指纹、全局 salt 或跨 tenant 比较。
- 普通人工 API 永不返回、搜索或导出历史完整号码。呼叫记录最多返回固定形态的末四位脱敏值；需要完整当前号码时，用户只能重新通过 CUSTOMER、CONTACT 或 LEAD 的既有 API 和当前字段策略读取当前业务值。
- `externalRecordingRef` 是 connector-scoped 敏感标识：加密保存，以独立 HMAC 指纹做内部精确关联，不直接公开。它不得是 URL、文件路径、认证 header、signed query 或媒体 credential；私有 adapter 必须把厂商对象映射成稳定的不透明引用。
- 录音内容不进入核心数据库、缓存、日志、搜索索引或 Activity。人工媒体访问必须经过独立权限、当前参与关系、关联 CRM 记录当前可见性、tenant 录音策略、未到期状态和 adapter capability 的交集，并由服务端受控流转；核心不向浏览器重定向或返回厂商 URL。
- 生产环境启用 B22 前必须显式配置受治理的密钥提供器、部署保留上限和 tenant 策略，缺失、不可用或越界时呼叫失败关闭；无法证明录音删除能力只允许关闭录音，不阻止明确禁用录音的普通呼叫。dev/test 的确定性实现不能进入 `prod`。

HMAC 是可链接的假名化标识，不是匿名化。电话号码空间很小，只有 secret key、tenant 隔离、最小访问、短保留和删除共同成立时，数据库泄露才不能直接离线枚举全部号码。

### 14.2 数据分类与最小持久化

| 数据 | 核心最小持久化 | 允许用途 | 普通输出 | 到期动作 |
| --- | --- | --- | --- | --- |
| 外呼对端快照 | 来源业务类型/ID/字段 code/记录版本、规范化版本、AEAD envelope、固定末四位展示、绝对到期时间 | 已授权 command 的首次 dispatch、同 command 的安全恢复/对账 | 仅固定脱敏值；完整值永不由呼叫 API 返回 | 清空 ciphertext、nonce、key version 和所有可恢复材料，标记 `PURGED` |
| `PRESENT` 呼入对端 | 规范化版本、AEAD envelope、tenant HMAC 指纹及 key version、固定末四位展示、绝对到期时间 | 人工授权后的受控候选匹配、故障处理 | 仅固定脱敏值 | 同时清空 ciphertext、HMAC 和 key metadata |
| withheld/unavailable/invalid 呼入 | 有界分类、数据库接收时间 | 生命周期与安全诊断 | 固定分类，不返回原输入 | 随 interaction 元数据到期 |
| 录音引用 | connector、AEAD envelope、独立 HMAC 指纹/key version、可用性、删除截止和确认时间 | adapter 内部关联、受控媒体打开与删除 | 只返回 provider-neutral 可用性 | 到期先停访问并 durable 请求 adapter 删除；确认后清空引用 |
| 录音媒体/转写 | 核心不保存 | 私有媒体 adapter 的受控流 | 不返回 URL、路径、credential 或原始内容 | 由 adapter 在核心截止前删除并回报；核心不能把请求发送当作已删除 |
| 规范 inbox/outbox | 本地 ID、connector、delivery/command 引用、无直接敏感值的规范 digest | 幂等、重放、状态处理 | 不直接公开 | 按独立可靠性窗口清理，不因需要幂等而延长号码/录音引用 |
| 安全访问审计 | tenant、interaction/recording 本地 ID、member、动作、结果分类、策略版本和服务端时间 | 追责、异常检测 | 仅独立治理查询 | 按审计期限删除；不保存号码、HMAC、引用、URL、请求体或任意异常消息 |

脱敏展示是个人数据的受限派生值，不是公开值。统一使用固定长度的 `[PHONE **** 1234]` 形态，只保留四位尾号，不暴露国家代码或原始长度；非 `PRESENT` 地址使用固定分类展示。任何列表、导出、报表、Activity 和公共日志都不得自行生成另一种更宽松遮罩。

### 14.3 外呼号码快照

人工起呼的公开输入只允许业务对象类型、记录 ID、字段 code 和用户请求幂等键。字段 code 必须属于 CUSTOMER、CONTACT 或 LEAD 的内置 `phone_number`、内置 `telephone`，或当前活动的自定义 `PHONE` 字段；`telephone` 虽是 `TEXT`，也只有用户显式选择且整个值通过下述 E.164 规则时才能拨打，不能自动成为默认号码。

应用在创建 interaction/outbox 的同一写事务内完成：

1. 从 `AuthenticatedCaller` 派生 tenant，重新确认活动成员和呼叫入口能力；
2. 用所属业务域的当前写入/呼叫所需数据范围读取权威记录，拒绝公海、删除、转换后不可用或其他后续状态规则排除的记录；
3. 解析同一数据库快照中的当前字段定义与 `EffectiveFieldAccess`，要求字段存在、活动、可读且 `mask=NONE`；普通请求中的任何 `number`、`dialString`、`connectorRef` 或外显号码属性都按未知/禁止输入拒绝，而不是覆盖服务端值；
4. 对权威字段值执行 `E164_V1`：先 NFKC 和两端 Unicode 空白裁剪，只移除 ASCII 空格、`-`、`.`、`(`、`)` 这些展示分隔符，然后要求 `^\+[1-9]\d{7,14}$`；拒绝 `00` 前缀、国家代码推断、分机、字母、`;ext=`、`,`、`;`、SIP URI、换行和控制字符；
5. 生成固定脱敏展示，使用 tenant/purpose key 加密规范值，并把来源业务类型/ID、field code、业务记录版本、规范化版本、策略版本和绝对到期时间与 interaction/outbox 原子保存。

保存的是“授权时号码”，不是对 CRM 字段的延迟引用。字段随后修改、隐藏、删除或记录转移都不能改写已经创建的 command；请求幂等重试返回原 interaction，不重新读取一个新号码。普通 outbox payload 和 domain event 只携带本地 interaction/command ID。dispatch worker 在验证原 tenant/connector/command 仍可发送后，按专用内部能力即时解密为不可序列化、`toString` 固定脱敏的 dial-target 值，交给唯一 adapter 并尽快释放；不得把明文写入 command ledger、trace、metric label、exception、线程名或缓存。

### 14.4 呼入号码、HMAC 与受控匹配

规范 ingress 的专用敏感地址输入只允许四种互斥状态：`PRESENT`、`WITHHELD`、`UNAVAILABLE`、`INVALID`。只有 `PRESENT` 携带地址，且必须通过与外呼相同的 `E164_V1`；另外三种状态不得携带占位字符串或原始值。adapter 应尽早分类，但核心仍独立验证，失败的原输入不落库、不进入稳定错误或日志。

`PRESENT` 值加密后，以完整 32-byte HMAC-SHA-256 保存等值指纹。MAC 输入是长度前缀的规范二进制 frame，至少包含固定协议标识、`B22_PHONE_MATCH` purpose、格式版本、tenant identity 和 E.164 UTF-8 bytes；MAC key 是该 tenant 当前 phone-match key。connector 不进入 phone-match purpose，因此同 tenant 的多个 connector 可以匹配同一号码；tenant identity 和 tenant 独立 key 共同阻止跨租户相等性。数据库唯一键或查询必须同时带 tenant 与 key version，指纹不能出现在 API、日志、缓存键、通用事实 digest 或 analytics。

号码指纹只支持**精确等值候选**，不支持前缀、末四位、模糊、跨 tenant、批量导出或“相似号码”查询。若后续实现 CRM phone-match 投影，它必须：

- 在 owning aggregate 保存号码的事务中或通过可验证的 durable projection 同步 tenant、业务类型、记录、field code、key version 和 HMAC；不复制明文、mask 或任意业务展示字段；
- 对索引陈旧采取失败关闭：候选使用前重新加载当前业务根和字段值、重新规范化/计算 HMAC，并排除删除、转换、入池、失活字段或值已改变的记录；
- 只在显式 `AuthenticatedCaller` 的人工查询/关联用例内返回有界候选，再应用 CUSTOMER/CONTACT/LEAD 当前读取能力、数据范围及字段 `READ + mask=NONE`；connector ingress 不自动取得候选 ID，也不能合成 caller；
- 零个或多个当前可见候选都保持未匹配，且不返回候选总数。即使恰好一个候选，也只提供不含号码的安全业务摘要供人工确认；真正关联仍锁定 interaction 并重新验证操作者和目标记录，避免号码复用或共享号码造成错误自动关联。

因此呼入发现可以先保存最小指纹和脱敏值，却不会借“号码相同”扩大 CRM 可见性或把 connector 变成业务用户。

### 14.5 加密、密钥隔离与轮换

开源 application 只依赖窄的敏感值保护能力，不取得通用 KMS client 或任意 key bytes。`infra/security` 的生产实现连接部署选择的 KMS/HSM 或 secret-backed envelope provider；MySQL 最多保存不可直接使用的 wrapped tenant key material、非秘密 key reference/version 和 AEAD envelope，不保存明文 key。每个 tenant 至少具有独立的版本化根数据密钥，使用 HKDF-SHA-256 的固定 context 分离 `ADDRESS_ENCRYPTION`、`PHONE_MATCH_HMAC`、`RECORDING_REF_ENCRYPTION` 和 `RECORDING_REF_HMAC`；实现也可用 KMS 中四把独立 key，但不能复用 credential/password key。

- 加密固定为 AES-256-GCM。每个值使用由 tenant root、purpose 和不可变本地 ID 派生的独立 encryption subkey，或使用等价的 per-value envelope DEK；每次使用随机且同 subkey 下不重复的 96-bit nonce 和 128-bit tag。保护实现必须跟踪/限制 key invocation 并在无法保证 nonce 不重复时拒绝而不是继续写入。AAD 至少绑定 envelope/schema version、tenant、connector（适用时）、interaction、本地 recording ID、purpose、规范化版本和 key version；复制 ciphertext 到另一个 tenant、interaction 或 purpose 必须鉴权失败。
- 指纹固定为 HMAC-SHA-256 完整 32 bytes，并使用与加密不同的 purpose key。记录保存 MAC key version，以支持轮换期间同时查询活动和唯一 retiring 版本。
- 新写入只使用 active key。加密 key 轮换由有界任务重包/重加密尚未到期值；HMAC 轮换先 dual-read、新 key 单写，再有界重算存量 token，完成计数与抽样验证后才退役旧 key。已经到期的数据直接清除，不为轮换重新解密。
- key provider 不可用、key version 未知、GCM tag 校验失败或 tenant/purpose/AAD 不匹配时失败关闭，不返回空值、不使用默认 key、不回退为明文，也不把底层异常消息公开。tag 失败产生不含敏感值的安全告警并阻止 dispatch/媒体访问。
- JVM 中的明文生命周期限制在规范化、加密或一次 adapter 调用内；专用值禁止默认 JSON 序列化并覆盖安全展示。Java 无法承诺回收前彻底擦除所有副本，因此不能把 `char[]` 清零描述成完整销毁；主要控制仍是最小复制、无日志/缓存、短生命周期和持久化加密。

tenant 清理时先停用 connector 和新命令，再清理在线 ciphertext/HMAC/wrapped material，最后销毁 tenant key version。普通单条 retention 删除通过清除在线 envelope 和让备份到期完成，不宣称可以用共享 tenant key 对单行即时 crypto-shred。部署必须公开备份保留期、限制备份/key 的联合访问，并在备份恢复到可服务环境前先运行 retention sweep；否则“主库已删”不能被描述成所有副本即时删除。

### 14.6 录音引用与媒体访问

adapter 提交的 `externalRecordingRef` 是 1–512 UTF-8 bytes 的稳定 opaque value，按大小写敏感的原始 UTF-8 bytes 处理且不做 Unicode/路径规范化，并拒绝控制字符、空白-only、绝对 URL、URI user-info、文件路径和带认证 query 的值。若厂商只提供 URL 或路径，私有 adapter 在自己的受治理 registry 中建立随机稳定引用，核心只接收该引用。核心按 `(tenant, connector, recording-ref purpose)` 加密，并以独立 HMAC 指纹和 key version 完成精确关联；同 delivery 不同引用或同引用跨 connector 复用都失败关闭。

公开状态仍只有 `UNAVAILABLE`、`PROCESSING`、`AVAILABLE`、`REMOVED` 这类 provider-neutral 事实。删除派发、重试、失败与确认使用内部 removal 状态，不能为了暴露运维细节扩张普通状态；到达删除截止立即停止新访问，即使外部删除仍在重试。只有 adapter 明确确认对象已不存在后才能公开 `REMOVED`，然后在有界晚到事件宽限期结束时清除 encrypted ref 和 HMAC；发送删除命令、HTTP 2xx 或本地超时都不足以声称已删除。

人工访问每次都必须满足以下全部条件：

1. `AuthenticatedCaller` 当前仍是活动 tenant 成员，并具有呼叫读取和独立的录音访问能力；
2. 当前 caller 是 interaction 的当前坐席/受治理参与者，或具有明确的 tenant 呼叫管理范围；
3. interaction 已关联 CRM 记录时，所属 CUSTOMER/CONTACT/LEAD 当前读取能力和数据范围仍允许该记录；呼叫参与关系不扩大 CRM 权限，CRM 权限也不扩大呼叫参与关系；
4. tenant 策略允许录音访问且保存了本次 interaction 的策略版本，录音为 `AVAILABLE`，未到访问/删除截止，未处于 removal 流程；
5. connector 当前可用，并声明受控读取和删除 capability。不能删除或无法报告删除结果的 adapter 不得为新 interaction 启用录音访问。

connector 停用或退役会立即阻止人工播放，但不能取消已经到期的删除义务；control plane 必须保留仅能执行删除/确认的最小 decommission 路径，或在退役完成前证明所有媒体已删除。无法继续删除的退役不能被记录成合规清理成功。

应用授权在短数据库事务中完成，实际长媒体 I/O 在事务外经专用 adapter 能力执行。B22-M 已从本节保留的两个候选中选择同请求、逐次重新授权的同源服务端代理流：不签发浏览器媒体 grant，不返回 provider redirect、URL/query token 或 `externalRecordingRef`，设置 `Cache-Control: no-store` 并记录最小 access attempt/outcome。内部授权结果只在当前服务端调用栈即时消费，最长不超过 60 秒启动上限，不能跨请求成为 bearer。完整 WAV/MP3、可选 single byte-range、取消和资源上限见[第 16 节](#16-b22-m-录音媒体传输契约设计闭合)。普通详情、列表和导出只看到可用性。

录音的告知、同意、法定事由、跨境、双向录音或暂停规则依部署司法辖区和通信拓扑而异，不由一个布尔字段假装解决。生产启用录音必须由部署方提供版本化、已审查的 tenant 录音政策，并保证 provider/PBX 侧实际行为与该政策一致；B22 核心保存政策版本和技术截止，不宣称单凭本节即实现法律合规。

### 14.7 保留政策与销毁流程

核心使用版本化 tenant call-data retention policy 概念，部署另设不可由 tenant 超越的全局上限。生产环境两者都必须显式存在，policy 保存或 connector readiness 对任何缺失、负 duration、溢出或超过部署上限的值失败关闭，不能静默 clamp 后向管理员展示另一个期限。策略至少分别约束：

- 完整号码 ciphertext/HMAC 在终态后的保留时长，以及没有可靠终态时从 capture 起计算的绝对最长时长；
- 录音媒体的删除期限、删除确认后的引用宽限期；
- interaction 的非敏感/低敏感元数据期限；
- 媒体访问审计期限；
- 私有 adapter journal/command ledger 中任何直接敏感值的期限。

dev/test 模拟器使用确定性基线：号码 30 天、无终态号码绝对上限 7 天、录音 30 天、确认后引用宽限 7 天、interaction 元数据 365 天、访问审计 365 天、adapter 敏感 journal 7 天。这些只是测试 fixture，不是生产合规默认值。生产按处理目的必要的最短时间配置；法律或合同需要更长时由部署方审查并显式配置，不由代码猜测。

创建/发现 interaction 时保存 policy version 和当时可计算的绝对截止；终态或录音可用事实到达时只补充剩余绝对截止。号码截止取 `terminal time + address retention` 与 `captured time + unterminated absolute cap` 的较早者；录音和 interaction 元数据也必须同时具有业务 anchor 后期限与 deployment absolute cap，任何 observation gap 都不能造成无限保留。策略缩短会通过有界重算把既有未到期 deadline 提前；策略延长只影响新 interaction，不能静默延长已经告知/快照的数据寿命。首期不提供 per-call legal hold；需要保全的部署必须先设计独立、双人审批、可审计且有到期日的 hold 能力，不能用修改数据库 deadline 绕过策略。

retention worker 使用 tenant 游标、固定小页、按本地 ID 排序锁定并可重复执行：

1. 到期号码先原子清空 ciphertext、nonce/tag、HMAC、key version 和所有可恢复材料，保留固定 `PURGED` 分类；末四位展示只保留到 interaction 元数据期限。
2. 到期录音立即拒绝新的 B22-M access attempt/stream，并在同一事务写 durable deletion outbox；adapter 以同 command 重试。确认删除后记录安全确认时间，宽限期后清空录音引用及指纹。删除失败保持不可访问、持续有界退避并告警，不伪造 `REMOVED`。
3. interaction 元数据到期后先去除 member、CRM relation、mask 和精确业务时间等可识别关系，只留下满足 inbox/outbox 幂等窗口和删除证明所需的最小 tombstone；窗口结束后由专用 retention store 物理删除。该物理删除是安全生命周期，不扩张普通 aggregate repository 的 delete 契约。
4. access audit、inbox/outbox tombstone 和 adapter journal 各按自己的 purpose 到期，不能因为另一个类别保留更久而复制号码或录音引用。

私有 adapter 的 journal、command ledger 和 session registry 必须按 connector/tenant 分区并声明容量。默认只写规范 allowlist；Caller ID、dial string、channel variables、SIP header、原始 callback/frame、provider error 和录音路径不落盘。确需原始材料完成短期传输重放时，必须进入独立加密 quarantine，期限不晚于策略中的 adapter sensitive journal deadline，访问单独审计，过期销毁；核心 inbox 不能作为允许私有 raw journal 永久保留的理由。

### 14.8 访问矩阵与日志边界

| 主体 | 完整外呼快照 | 完整呼入号码 | HMAC/crypto metadata | 末四位展示 | 录音引用 | 录音内容 |
| --- | --- | --- | --- | --- | --- | --- |
| 普通呼叫读者 | 不可读 | 不可读 | 不可读 | 仅在 interaction 可见时可读 | 不可读 | 不可读 |
| 当前坐席/呼叫管理员 | 不由呼叫 API 返回 | 不由呼叫 API 返回 | 不可读 | interaction 与关联 CRM 可见性交集内可读 | 不可读 | 另有录音能力且全部实时条件满足时受控读取 |
| connector ingress | 不可任意读取 | 只在该次已认证敏感事实写入中提交 | 不可读 | 不需要 | 只在该次事实中提交 opaque ref | 不可读取核心授权 |
| dispatch/media worker | 仅原 command、原 tenant/connector 的即时解密 | 不需要解密做 HMAC 匹配 | 仅专用保护端口内部 | 不需要 | 仅原 recording 操作即时解密 | 经已授权本地操作调用 adapter |
| 数据库/日志/监控操作者 | 无 key 时不可恢复 | 无 key 时不可恢复 | 数据库管理员可见的 HMAC 仍按敏感值治理 | 仅受控数据库访问 | 无 key 时不可恢复 | 核心不保存 |

日志、trace、metric、审计和稳定错误只允许本地 interaction/command/recording ID、connectorRef 的安全展示、结果分类、策略/key version 和服务端时间。禁止完整/脱敏号码作为 metric label，禁止 HMAC/ciphertext/nonce/tag、credential、raw callback、provider URL/path、任意异常 message 和请求对象。结构化日志字段也适用同一规则；不能依赖后置正则清洗作为主要防线。

### 14.9 失败语义

| 场景 | 规划结果 |
| --- | --- |
| 人工请求提交号码或选择隐藏/脱敏/不可见字段 | 稳定拒绝，不比较请求值，不创建 interaction/outbox |
| 权威值不能通过 `E164_V1` | 稳定“不可拨”拒绝；不交给 adapter 猜测，不记录原值 |
| 呼入 `PRESENT` 不能规范化 | 只接受有界 `INVALID` 分类或拒绝原 delivery；原始值不落库 |
| key provider 暂时不可用 | 外呼不创建可派发 command；ingress 用相同 `deliveryRef` 安全重试；不降级明文 |
| ciphertext tag/AAD 失败或 key version 丢失 | 阻止 dispatch/媒体访问并产生安全告警；不显示空值或底层异常 |
| 同 tenant 同号码 | 当前 key version 的 HMAC 相同，只能进入 tenant 内精确候选路径 |
| 不同 tenant 同号码 | HMAC 不同，任何跨 tenant 查询都无效 |
| 录音到期但 adapter 删除失败 | 从截止时起停止访问，保持删除待处置并告警；不能返回 `REMOVED` |
| adapter 只提供媒体 URL 或无法声明删除能力 | 拒绝启用新录音访问，不把 URL 保存成 opaque ref |
| policy/key 配置在生产缺失或 dev provider 被装入 prod | 启动/readiness 或首次启用失败关闭，呼叫能力明确不可用 |

### 14.10 实现前契约测试门槛

- 公共请求严格拒绝号码、dial string、connector、tenant、外显号码和未知属性；同一请求幂等重试在源字段改变后仍引用原加密快照。
- 事务内撤销成员、业务数据范围或字段 `READ/mask=NONE` 与起呼并发时形成明确顺序，已完成撤销后不能创建新 snapshot/outbox。
- `E164_V1` 用固定向量覆盖 NFKC、展示分隔符、最短/最长 E.164、`00`、本地号、分机、pause、SIP URI、控制字符和超长输入；错误和对象 `toString` 不含原值。
- 同 tenant/同 key/同号码 HMAC 相同，不同 tenant、purpose 或号码不同；数据库只保存完整 32-byte token 和 key version。测试证明无 key SHA、末四位或 mask 不能进入匹配查询。
- AES-GCM 使用固定官方/库向量并验证随机 nonce 不复用；篡改 ciphertext/tag/AAD、交换 tenant/interaction/purpose 或未知 key version 全部失败关闭。
- key 轮换覆盖 active/retiring dual-read、新 key 单写、有界 re-HMAC/re-encrypt、到期优先删除、完成前禁止销毁旧 key 和 tenant 清理顺序。
- 人工候选匹配在 SQL/分页前 tenant scoped，陈旧 token 会重新加载当前值，隐藏、脱敏、越权、入池、删除和多候选都不能自动关联或泄露 candidate count；connector ingress 永不取得候选 ID。
- 所有呼叫 DTO/VO/Response、Activity、inbox/outbox digest、日志 capture、trace 与 metric fixture 都不含完整号码、HMAC、ciphertext、录音 ref、provider URL 或异常原文；脱敏展示固定为末四位形态。
- 录音访问矩阵覆盖活动成员、参与/管理范围、关联 CRM 当前可见性、独立能力、策略版本、截止、状态和 adapter capability 任一缺失即拒绝；B22-M 完整或 range 请求逐次重新授权，内部 open 结果最长 60 秒且只在同一调用栈消费，不存在浏览器 grant 或 URL token。
- 录音到期立即停止访问，删除命令可重复，只有 adapter 确认才进入 `REMOVED`；确认宽限后引用/HMAC 被清除，晚到事实、删除失败和 connector 停用均不会恢复访问。
- policy 缩短只会提前既有 deadline，延长不追溯；无终态 interaction 仍在绝对上限清除号码。tenant cursor/batch worker 在崩溃重启和并发运行下幂等且有界。
- 备份恢复 fixture 在对外服务前运行 sweep；私有云/PBX adapter 契约 fixture 证明 journal/ledger allowlist、加密分区、容量、deadline 和删除确认，不保留无界 raw frame/callback 或 dial string。

### 14.11 设计依据与非合规声明

- [NIST SP 800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final) 定义 GCM 认证加密；B22-D 采用 AEAD、唯一 nonce、完整 tag 和绑定 tenant/purpose 的 AAD，而不是仅加密不鉴权。
- [FIPS 198-1 HMAC](https://csrc.nist.gov/pubs/fips/198-1/final) 当前仍是 final，NIST 已提出在 [SP 800-224](https://csrc.nist.gov/pubs/sp/800/224/ipd) 最终发布时迁移；本设计固定 HMAC-SHA-256 语义但要求实现时复核届时最终文档，不把普通 SHA-256 当作号码指纹。
- [RFC 5869](https://www.rfc-editor.org/info/rfc5869/) 定义 HKDF 的 extract-and-expand 与 context separation；B22-D 用固定 purpose context 分离号码加密、号码匹配、录音引用加密和录音引用匹配 key。
- [NIST SP 800-57 Part 1 Rev. 5](https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final) 要求对 key 生命周期、cryptoperiod、保护、库存、轮换和销毁作整体治理；本节因此不把算法选择等同于完成密钥管理。
- [《中华人民共和国个人信息保护法》](https://www.npc.gov.cn/npc/c2/c30834/202108/t20210820_313088.html) 第六、十九、四十七条要求最小必要、最短必要保存和在相应情形删除；[《网络数据安全管理条例》](https://app.www.gov.cn/govdata/gov/202409/30/520076/article.html) 还要求说明保存期限及到期处理。因此生产期限必须由部署和 tenant 明示，dev/test fixture 不能冒充法律默认值。

本节是技术安全边界，不是针对任一司法辖区的法律意见，也不证明录音告知、同意、劳动监察、电信、跨境或证据保全要求已经满足。部署方仍需完成适用的数据保护影响评估、告知与适配器/运营商处理协议。

## 15. B22-S 呼叫状态、两腿拓扑与坐席设备能力设计闭合

> 决策日期：2026-07-19
>
> 完整设计与契约测试清单：[B22-S 呼叫状态、两腿拓扑与坐席设备能力设计](docs/spikes/B22-state-and-topology.md)
>
> 范围：只固定 interaction/leg、首期拓扑、接通与终止状态、observation 以及坐席/设备 capability 语义；不预占 API、表、错误码、迁移或真实 adapter。

### 15.1 Interaction 与 leg

`CallInteraction` 是 CRM 的 tenant/connector-scoped 交互根，不等于 provider Call、PBX channel 或 bridge。首期核心只保存一个 `AGENT` 和一个 `REMOTE_PARTY` 规范参与腿；native control leg、Local channel、holding bridge、IVR 和其他内部节点留在私有 adapter 的 durable session registry。第三条规范腿、角色替换、transfer 和 conference 不属于首期。

leg 只保存稳定 opaque external ref、不可变角色和 `DISCOVERED`、`ALERTING`、`MEDIA_READY`、`TERMINATED` 里程碑。`MEDIA_READY` 表示该腿与平台之间已有双向媒体路径，不表示两位参与者已互通。interaction lifecycle 从不可清除的里程碑派生为 `PREPARING`、`RINGING`、`CONNECTED`、`TERMINATED`；dispatch、observation 与 recording 分别独立，不制造组合状态枚举。

### 15.2 首期 topology

外呼固定 `OUTBOUND_AGENT_FIRST`：先呼叫私有坐席 endpoint，只有坐席腿 `MEDIA_READY` 后才允许创建并呼叫客户腿。坐席未配置、拒接、超时或离线时客户不会被联系。呼入固定 `INBOUND_AGENT_BRIDGE`：adapter 先发现对端，私有完成 IVR/排队/设备选择，再把最终坐席和对端归一为两条腿；等待或 IVR 媒体不算 CRM 坐席参与。

外呼的 interaction `RINGING` 专指客户腿 alerting；坐席设备振铃仍属于 `PREPARING`。呼入的 `RINGING` 专指最终坐席腿 alerting。该方向敏感定义避免同一个 public 状态在外呼时错误暗示客户已被联系。

### 15.3 `CONNECTED` 与终止

只有 `PARTICIPANTS_CONNECTED(agentLegRef, remotePartyLegRef)` 可以建立 `connectedAt`。核心要求两个不同角色的规范腿都已 `MEDIA_READY`，adapter 还必须证明它们处于首期预期的双向两方媒体关系。early media、单腿 answered/up、两腿各自 ready 但无关系、只有 bridge 创建、holding bridge、IVR、voicemail 或 provider 单 Call `completed` 均不足以接通。

终止 reason 固定使用 `NORMAL`、`BUSY`、`NO_ANSWER`、`REJECTED`、`CANCELED`、`NETWORK_FAILURE`、`PROVIDER_FAILURE`、`UNKNOWN` 安全分类，可选失败方只允许 `AGENT`、`REMOTE_PARTY`、`SYSTEM`、`UNKNOWN`。是否曾接通只从 `connectedAt` 派生；具体分类只能从 unknown 单调补充，冲突不覆盖并进入 reconciliation。挂断命令响应本身不是终态。

乱序事实先经 inbox 去重，再在 interaction 锁内仲裁。缺前置条件的合法事实可以有界 deferred；终态后的历史接通只有在时间/顺序证明其发生于终止前且终止分类兼容时才能补充。已知 gap、事实冲突或 ambiguous dispatch 使用独立 `RECONCILIATION_REQUIRED`，无法恢复时为 `OBSERVATION_INCOMPLETE`；它们可以与任一 lifecycle 并存。

### 15.4 坐席与设备 capability

CRM member、connector-scoped external agent 和 endpoint/device 三层分离。核心 agent binding 采用不可改绑的 opaque external ref：分机或账号复用时由私有 adapter 生成新 binding generation，晚到事件不能把旧通话归给新成员。呼入 binding 未知、退役或成员失活时 interaction 保持未分配，不能采用事实 body 中的 actor/member。

状态侧 typed connector capability 固定覆盖 `AGENT_FIRST_OUTBOUND`、`INBOUND_AGENT_BRIDGE_OBSERVATION`、`TWO_PARTY_MEDIA_PROOF`、`AGENT_ENDPOINT_CONFIGURATION_PROBE`、可选 `AGENT_PRESENCE_PROBE` 和可选 `TERMINATE_INTERACTION`。成员 projection 只返回 endpoint configuration、短时 `AVAILABLE/BUSY/UNAVAILABLE/UNKNOWN` presence、安全原因和观测有效期；设备 ID、分机、坐席号码、SIP URI、registration、IP、token、codec 和 provider 错误都不进入核心。

`CONFIGURED + UNKNOWN` 可以安全尝试坐席腿，因为 agent-first 保证坐席媒体就绪前不联系客户；`AVAILABLE` 也只是短时提示，不是 authority 或成功保证。核心不创建实时坐席大盘，不让 caller 选择设备，也不把陈旧 interaction 当作设备忙的绝对事实。

本节闭合状态与 topology 前置，并与第 13 节 B22-R、第 14 节 B22-D 共同构成实现前约束；媒体传输由第 16 节 B22-M 闭合，真实 connector readiness 与隔离由第 17 节 B22-A 闭合，云 callback 组合由第 18 节 B22-CW 闭合，第 19 节 B22-CP 已闭合本节与 PBX fencing/session/gap/isolation 的核心组合，第 20 节 B22-PC 又闭合 PBX command 组合，第 21 节 B22-PD 再闭合 PBX 敏感数据组合，第 22 节 B22-PM 闭合 PBX stored-recording/media 组合；真实 provider 组合仍待闭合。

## 16. B22-M 录音媒体传输契约设计闭合

> 决策日期：2026-07-19
>
> 完整设计与契约测试清单：[B22-M 录音媒体传输契约设计](docs/spikes/B22-media-transfer.md)
>
> 范围：只固定单录音对象、可用性映射、人工授权、HTTP 代理流、adapter byte-stream、资源上限与云/PBX fixture；不预占最终 URI、Java 名称、表、迁移或真实 adapter。

### 16.1 传输与授权

首期每个 interaction 最多公开一个录音对象。人工读取固定为每个请求重新授权的同源核心 `GET`：target 只含本地 identity，完整或每个 single-byte-range 请求都重新验证活动成员、呼叫读取、独立录音读取、参与/管理范围、关联 CRM record 当前可见性、tenant policy/deadline 和 connector capability。首期不签发浏览器媒体 grant，不接受 query token，不返回 3xx、provider URL/path/credential 或 `externalRecordingRef`。

授权和最小 access attempt 在短数据库事务内提交，adapter/provider I/O 在事务外。核心只接受可取消、可关闭、可背压的 typed byte-stream handle；完整录音不能进入 JVM buffer、MySQL、Redis、临时文件、日志或 trace。client abort、截止、最大字节/时长和 idle timeout 都必须取消并关闭上游，首字节后不透明重试。

### 16.2 状态、格式与 HTTP

录音继续独立投影 `UNAVAILABLE`、`PROCESSING`、`AVAILABLE`、`REMOVED`。Call/PBX 终态不产生 `AVAILABLE`；只有同一 opaque object 的权威完成与 stored-media 证据可以建立可用，普通读取 404 不等于确认删除，`REMOVED` 后晚到 callback 永不恢复访问。

首期规范格式只有 `WAV -> audio/wav` 和 `MP3 -> audio/mpeg`，同一对象格式不可变。成功 full stream 返回 `200`；只有 adapter 声明 immutable representation、稳定总长度与原生 range 时才接受一个 `bytes` range 并返回 `206`，否则 range 返回 `416`，核心不得读取并丢弃前缀模拟 seek。响应固定 `no-store`、同源、`nosniff` 和安全 filename，不转发 provider header/validator。

### 16.3 Capability 与剩余阻断

媒体侧 typed capability 固定覆盖 recording status observation、controlled media read、delete confirmation 和可选 byte range；生产 readiness 还必须证明格式、stored-object 时点、同一 connector/account/storage scope、decommission 删除路径、egress/path 隔离、超时、最大对象/流和活动流上限。B22-M 闭合媒体传输设计，真实云/PBX readiness/隔离由第 17 节 B22-A 闭合；云 callback 组合由第 18 节 B22-CW 闭合，第 19 节 B22-CP 与第 20 节 B22-PC 不含 recording 的 PBX 核心/command 组合，PBX 敏感数据/确认销毁和 stored-recording/media 组合后来分别由第 21 节 B22-PD 与第 22 节 B22-PM 闭合。真实 canary 仍阻止实现拆批。

## 17. B22-A 真实适配器 readiness 与隔离契约设计闭合

> 决策日期：2026-07-19
>
> 完整设计与契约测试清单：[B22-A 真实适配器 readiness 与隔离契约设计](docs/B22-A-adapter-readiness.md)
>
> 范围：只固定真实 adapter 的 provider-neutral readiness 快照、操作门、connector 隔离、配置漂移和云/PBX 证据；不实现 adapter、API、表、配置或真实电话调用。

### 17.1 三层状态与按操作 readiness

connector registration 的 `ACTIVE/DISABLED/RETIRED`、adapter readiness 与 B22-S member endpoint capability 三层正交。`ACTIVE` 不等于能起呼，readiness 不授予 CRM authority，`AVAILABLE` presence 也不是 dispatch 成功保证。

核心规划投影 `ConnectorReadinessSnapshot` 绑定 adapter contract/build、私有 binding revision、单调 probe sequence、核心接收时间和最长 5 分钟有效期，并分别表达 `START_OUTBOUND`、`OBSERVE_INBOUND`、`TERMINATE`、`RECONCILE`、`RECORDING_READ` 与 `RECORDING_DELETE` 的 `READY/NOT_READY/UNSUPPORTED`。缺失、过期、乱序、未知或版本不兼容统一失败关闭，不使用一个含义模糊的 `DEGRADED` 布尔值。

新外呼在创建 interaction/outbox 前、core worker claim 时以及 adapter ledger 越过 `DISPATCHING` 前分别检查。B22-R durability/capacity/start guarantee、B22-S agent-first/two-party proof/endpoint configuration、B22-D key/retention/recording policy、B22-M media transport profile，以及 provider 认证、控制权限、observation path、reconciliation 和版本证据缺一不可。start 不 ready 不停止 backlog 重投、已有 reconciliation case、到期录音删除或安全停用。

### 17.2 Connector 隔离

每个 provider 输入在解析内容前已由受治理 callback route 或 fenced stream owner 选定唯一逻辑 connector。Account SID、Stasis app、ESL header、channel variable、Caller ID、extension 和事实 body 只能佐证绑定，不能选择 connector 或 tenant。私有 adapter 维护 `(provider authority fingerprint, native resource)` 到唯一 connector/session 的 durable ownership registry；歧义输入 quarantine，不猜测投递。

一个 adapter 进程可服务多个 tenant，但 credential、provider binding、route/stream、ledger、journal、session registry、query ledger、capacity、quarantine、retention 和 observability 全部 connector 分区。隔离证据明确区分 dedicated provider boundary、provider resource-scoped 和 adapter-enforced trust zone；后者必须通过同进程双 tenant 负向 fixture，不能宣传成 provider 强制授权。

### 17.3 漂移、云与 PBX profile

provider account/node、route/app/filter、callback 集合、owner、权限、API breaking version、adapter build、durable schema/capacity/retention 或 media policy 漂移会先提高 binding revision、撤销受影响 readiness 并停止新 start。observation 相关漂移从上一次被证明正常的检查时间起保守打开 gap；新快照不能抹去旧 gap 或让 `AMBIGUOUS` command 自动重拨。

云 adapter 对 connector 专属完整 callback URL/raw body 验签，核对 account/resource ownership，显式配置完整 call 与独立 recording callback，并在 journal commit 后才成功应答。共享 provider account 只有在 route、command callback、native ownership 和 store partition 全部唯一时可用，否则要求独立 account/subaccount 或等价边界。

Asterisk 每个 connector 使用独立 Stasis app 或等价强隔离，保持 `subscribeAll=false`、最小订阅和单一 WebSocket owner；`ApplicationReplaced`/非 clean close 立即开 gap，`read_only=yes` 不能声明 originate/hangup/delete ready，active channel/bridge snapshot 不等于 replay。FreeSWITCH ESL 认证后具有宽命令面，因此要求受限地址/ACL/credential、固定最小订阅/filter、全局 ownership 仲裁和明确 trust-zone 声明；需要 PBX 强制隔离时使用独立 node 或策略执行代理。

### 17.4 证据与剩余门槛

readiness 周期探测只允许无外部人类副作用的 read-only 操作，不能为了 health 拨号、挂断、读取真实媒体或删除 recording。写 capability 由最小权限配置读回、与 adapter build 绑定的隔离测试环境 canary 和实际安全拒绝共同证明。普通输出只返回 provider-neutral 可用性、检查/过期时间和安全分类，不泄露账号、app、node、route、endpoint、native ID、credential、录音引用或异常原文。

B22-A 闭合真实 readiness/隔离的设计语义，不表示任一真实 adapter 已 ready。云 callback 的 B22-R/S/D/M/A 组合由第 18 节 B22-CW 闭合，PBX stream/subscription 隔离与 fencing/session/topology/gap 的核心组合由第 19 节 B22-CP 闭合，PBX command crash/`AMBIGUOUS` 组合由第 20 节 B22-PC 闭合，PBX 号码/key/retention/quarantine/销毁组合由第 21 节 B22-PD 闭合，PBX stored-recording/media 组合由第 22 节 B22-PM 闭合；真实 adapter 组合仍未闭合，因此除已交付的 B22a 外，呼叫 runtime 继续停留在规划阶段。

## 18. B22-CW 云回调组合契约 fixture 闭合

> 决策日期：2026-07-19
>
> 完整场景、检查点与通过条件：[B22-CW 云回调组合契约 fixture](docs/B22-CW-cloud-callback-fixture.md)
>
> 范围：只冻结云 callback 的跨 B22-R/D/S/M/A 确定性组合脚本，不实现 adapter、核心运行时、API、表、配置或真实电话调用。

### 18.1 双层 fixture 与统一 oracle

native 层先由受治理 route 选择 connector，按完整 URL/raw body 规则验签，核对 account/resource ownership，归一化固定 typed fact 并在 journal commit 后才应答 provider。核心层只接收 provider-neutral delivery、dispatch、leg、recording、readiness、gap 与 typed media handle；raw callback、号码、provider ID/URL/header/credential 和任意属性 map 均不能越过 adapter。

每步统一检查 command ledger/outbox、journal/inbox、session/ownership、interaction/两腿、recording、media handle、readiness/gap、一次性 effect 和敏感值泄漏。场景使用虚拟时钟和可重启 durable store，不靠 `sleep` 或纯调用次数 mock 近似事务、唯一键和 lease。

### 18.2 七组组合场景

- `CW-01` 区分同 delivery duplicate、不同 delivery semantic no-op 和同 delivery/digest conflict；
- `CW-02` 允许有同腿顺序证据的终态前里程碑晚到补充，但拒绝 `NO_ANSWER` 与 connected 或两个具体终止分类互相覆盖；
- `CW-03` 让 callback-before-response 在 session ownership 证明下确认 ledger、settle 核心 outbox，重启和晚到响应不产生第二次 start；
- `CW-04` 固定 agent-first 两腿脚本，只有两个正确角色腿 media-ready 加显式两方关系 proof 才产生 `CONNECTED`；
- `CW-05` 让 recording completion 在 Call terminal 后独立到达，普通 404、晚到 callback 或删除请求本身都不伪造 `REMOVED`；
- `CW-06` 让 client disconnect、deadline 与 removal 传播可重复 cancel/close，禁止首字节后透明 reopen/拼接；
- `CW-07` 让 callback/readiness 漂移先停止三层 start admission，再从最后证明正常时点开 gap；旧/new snapshot 均不能抹去 gap 或自动重拨旧 command。

### 18.3 执行与剩余门槛

同一场景 ID 以后必须运行在纯规范模型、真实 MySQL 加 durable 模拟 adapter，以及隔离测试账号中的真实云 adapter conformance 三层。生产证据绑定 adapter contract/build、provider profile、fixture 版本和隔离等级；周期 probe 本身仍不得拨号、挂断、读取真实媒体或删除 recording。

B22-CW 闭合云 callback 组合契约，但不表示真实云 adapter 已实现或 ready。它不覆盖 PBX stream disconnect、owner fencing、native bridge 和 stored-file 路径；其中 PBX 核心 stream/session/topology/gap 组合后来由 B22-CP 闭合，PBX command 组合后来由 B22-PC 闭合，PBX 敏感数据组合后来由 B22-PD 闭合，stored-file media 组合后来由 B22-PM 闭合。真实 provider conformance 仍是拆分 B22a 以外实现子批次前的跨设计阻断项。

## 19. B22-CP PBX 组合契约 fixture 闭合

> 决策日期：2026-07-19
>
> 完整 trace、oracle、crash matrix 与 conformance 输出：[B22-CP PBX 组合契约 fixture](docs/B22-CP-pbx-composed-contract.md)
>
> 范围：只冻结 PBX reconnect/fencing、durable session registry、early media/单腿接听、双向两方媒体证明、订阅/ownership 隔离和 observation gap 的同一组合 fixture；不实现 adapter、核心 runtime、Docker/真实电话环境或录音媒体流。

B22-CP 固定一个双 tenant、双逻辑 connector、共享 adapter 进程的确定性 trace。A 的旧 stream owner dirty close 后，新 owner 必须先以递增 fence 在 durable control transaction 中打开 gap，才能把新 generation 标为 ready；旧 fence 对 journal、checkpoint、session 和 native ownership 的所有写入都失败。adapter 进程重启后，session 与两条规范 leg 继续解析为相同 opaque external refs，Local/control/holding 等额外 native legs 不能进入核心。

状态 oracle 明确检查 early media、坐席单腿 `Up`、对端单腿 `Up`、两腿分别 `MEDIA_READY`、bridge creation、holding 或错误成员关系都不会产生 `CONNECTED`。只有 exact `AGENT`/`REMOTE_PARTY` 两腿已有 media-ready 且权威证明符合首期 topology 的双向两方媒体关系时，adapter 才能提交一次 `PARTICIPANTS_CONNECTED`；重投、等价 delivery、崩溃恢复和乱序不会改写 `connectedAt` 或重复业务副作用。

隔离分支要求 governed stream 在解析 frame body 前选定 connector，A stream 上的 B ownership、未知 ownership 和 body 伪造 tenant/app/extension/session 都只能失败关闭或进入 A 的有界安全 quarantine，不能查询 B session、投递跨 tenant fact 或改写任一 B 分区。无 replay PBX 的 reconnect、healthy snapshot 和 active-resource query 最多把 gap 关闭为 `CLOSED_PARTIAL`，受影响 interaction 保持 `OBSERVATION_INCOMPLETE`；非权威 `NOT_FOUND` 不能证明完整或未发生。

B22-CP/1 还要求在 session/journal/gap/connected/reconciliation 的关键 commit 前后参数化崩溃，并输出无敏感值、绑定 adapter build/profile/binding revision 的机器可读 conformance 报告。这个报告只证明 PBX 核心组合语义，不替代真实 Asterisk/FreeSWITCH canary，也不覆盖后来分别由 B22-PC、B22-PD 和 B22-PM 闭合的完整 command crash/`AMBIGUOUS`、敏感数据/销毁与 stored-recording/media 组合。

## 20. B22-PC PBX command 组合契约 fixture 闭合

> 决策日期：2026-07-19
>
> 完整场景、crash matrix、oracle 与通过条件：[B22-PC PBX command 组合契约 fixture](docs/B22-PC-pbx-command-contract.md)
>
> 范围：只冻结 committed core outbox 到 adapter ledger、PBX start、event-before-response 和 `AMBIGUOUS` reconciliation 的确定性组合脚本；不实现 adapter、核心 runtime、Docker/真实电话环境、敏感值销毁或录音媒体流。

B22-PC 固定同一 adapter 进程中的双 tenant/双 connector fixture，以独立 core、adapter 和 durable provider oracle 区分 command 投递、provider invocation 与逻辑 PBX 对象。核心 transport 丢响应只能重投相同 `commandId`/digest；adapter 在 durable `RECEIVED` 前不确认接收，同 ID/不同 digest 在 provider I/O 前永久失败。`DISPATCHING` 是保守不可回退分界，之后的崩溃、超时或断连不能把命令放回队列或产生 replacement command。

`AT_MOST_ONCE_START` profile 对每个 command 最多进入一次 provider start；即使在 `DISPATCHING` commit 后、实际调用前崩溃，也允许保守 false `AMBIGUOUS`，不允许第二次拨号。`IDEMPOTENT_START(window)` profile 只在原窗口内复用完全相同的 provider key/digest，窗口不能因重启或 readiness 刷新而重置，provider logical object 始终最多一个。event-before-response 通过受治理 stream、不可改绑 session/ownership 和 journal 确认同一 ledger；只确认 dispatch，不补造 lifecycle。

`AMBIGUOUS` result 在核心事务内同时 settle start outbox 并创建唯一 reconciliation case。active snapshot、普通 `NOT_FOUND`、查询不完整、健康恢复和人工刷新都不证明未拨出；只有 case 创建时声明的 `AUTHORITATIVE_COMMAND_ABSENCE` 在最终一致窗口后覆盖原 provider authority/correlation namespace 和完整查询范围才可收敛 `REJECTED`。reconciliation 全程只读且使用 durable query ledger，任何 start/control 调用使 fixture 失败。

B22-PC/1 对 core outbox、adapter ledger、provider invocation/object、session/journal、result delivery/inbox 和 query completion 的相邻 commit 点参数化崩溃，并在每个 cut 重复 stale lease、readiness drift 与 cross-connector 负向断言。它闭合 PBX command crash/`AMBIGUOUS` 组合设计，但不表示真实 PBX 已通过；B22-D 敏感数据/销毁组合后来由 B22-PD 闭合，B22-M stored-recording/流取消组合后来由 B22-PM 闭合，真实 provider canary 仍待执行，因此除 B22a 外的 PBX runtime 继续停留在规划阶段。

## 21. B22-PD PBX 敏感数据与确认销毁组合契约 fixture 闭合

> 决策日期：2026-07-19
>
> 完整场景、data-surface manifest、crash matrix、oracle 与通过条件：[B22-PD PBX 敏感数据与确认销毁组合契约 fixture](docs/B22-PD-pbx-sensitive-data-contract.md)
>
> 范围：只冻结 PBX 外呼/呼入号码、core/adapter key、retention、quarantine allowlist、应用/PBX 日志泄漏哨兵和分层确认销毁的组合脚本；不实现 adapter、核心 runtime、KMS、备份系统、Docker/真实电话环境或 stored-recording media。

B22-PD 复用 B22-CP/PC 的双 tenant、双 connector trace，并增加相互独立的 core store、adapter store、PBX data-surface、key/backup 和 sink oracle。外呼权威 E.164 快照只在 tenant/purpose/AAD 绑定的 envelope 中持久化，普通 outbox/ledger/session/journal 不含号码；呼入 frame 由 governed stream 先选 connector，再从固定字段源抽取专用地址，其他 Caller ID、destination、extension、`variable_*`、SIP header、command body 与 native error 在普通 journal 前丢弃。相同号码只在同 tenant/同 HMAC key version 中可等值，跨 tenant/purpose/key token 必须不同。

普通 quarantine 固定为安全 allowlist。确需协议重放的 raw 材料只能进入独立 `GOVERNED_ENCRYPTED` payload store，具有 connector/tenant/purpose AAD、row/byte hard limit、独立访问审计和不晚于 adapter sensitive-journal policy 的绝对 deadline；达到 A 分区 high-water 不能淘汰 A 的未解决 metadata 或 B 的数据，并须用 control reserve 先打开 gap。active/retiring key、新 key 单写、HMAC dual-read、有界 re-encrypt/re-HMAC、到期优先 purge、完整 inventory 和 KMS 最终销毁确认共同组成 key rotation oracle，PBX credential 与 data key 始终正交。

retention 使用虚拟时钟验证 policy 缩短只提前、延长不追溯，号码 deadline 取业务 anchor 与 capture absolute cap 的较早值；gap、`AMBIGUOUS`、pending journal、connector 停用和 KMS outage 都不能延寿。恢复旧 backup 时服务门默认关闭，必须先 sweep core、adapter 和 PBX 受控面，再重新验证未到期 envelope/readiness。可靠性 tombstone 可以继续保留 identity/digest/outcome，但不能为对账复制或重建已到期号码。

泄漏 oracle 在格式化前检查 DTO/PO/log argument/trace/metric/audit/exception 字段目录，并在最终 sink 扫描 raw、digits-only、末四位/mask 及常见编码 canary。PBX profile 还必须枚举 Asterisk CDR/CEL/queue/logger/syslog 和 FreeSWITCH `Caller-*`/`Other-Leg-*`/`variable_*`、`BACKGROUND_JOB`、CDR/event handler、console/logfile/syslog、rotated log 与 failure spool；未登记副本、debug dump 或只 rotate 不 delete 都使 conformance 失败。

确认销毁分别记录 core online purge、adapter online purge、PBX controlled-surface purge、KMS final destruction 和 backup expiry/crypto-erasure 证据。删除命令、HTTP 2xx、SQL tombstone、普通 `NOT_FOUND`、KMS scheduled deletion 或文件 rotation 不能合并成一个 `deleted=true`。B22-PD 明确不宣称 Java/PBX 内存、SSD 底层块、终端或运营商副本已物理擦除；这些仍由真实部署与 B22-CANARY 证明。

B22-PD/1 闭合 PBX 号码/key/retention/quarantine/泄漏/销毁的组合设计，但不表示真实 PBX/KMS/备份已通过。B22-PM 后来闭合 stored-recording media 设计 oracle；真实 provider canary 仍待执行，因此除 B22a 外的敏感数据与媒体 runtime 继续停留在规划阶段。

## 22. B22-PM PBX 录音媒体组合契约 fixture 闭合

> 决策日期：2026-07-19
>
> 完整场景、profiles、crash matrix、oracle 与通过条件：[B22-PM PBX 录音媒体组合契约 fixture](docs/B22-PM-pbx-media-contract.md)
>
> 范围：只冻结 PBX 录音晚到、stored-object、完整/单 range、取消、hard limit、durable removal 与权威删除确认的确定性组合脚本；不实现 adapter、核心 runtime、HTTP API、Docker/真实电话环境或录音。

### 22.1 Late recording 与 stored-object

`PM-01` 把 live recording、interaction terminal、recording finished/stop 和 stored object 可读固定为四个不同检查点。finished/`RECORD_STOP` 只使私有 registry 进入 finalizing；只有同一 connector/storage scope 的只读 probe 证明 writer 已结束、exact object 可读、WAV/MP3 magic 正确、长度稳定且未超限、表示 immutable 后，adapter 才能 journal 一个 `AVAILABLE` fact。重启复用同一 opaque ref；path/name/symlink/URL、同名跨 connector 对象和普通 read `404` 都不能建立或删除核心 recording。

### 22.2 Full、single range、取消与上限

`PM-02`/`PM-03` 对每个 full 或 single-range GET 重做 B22-D/M 人工授权、分层 admission 与独立 access audit。full 返回规范 `200`；只有 `STORED_IMMUTABLE_NATIVE_RANGE` 能返回 `206`，并用 upstream offset/byte-count spy 证明未读取丢弃前缀。unsupported、multi、溢出、超限和不可满足 range 在 adapter open 前返回 `416`，不回退 full。

`PM-04`/`PM-05` 参数化 client disconnect、open/idle/stream deadline、录音截止、最大字节、removal 和进程崩溃。每个竞态都禁止 transparent reopen，底层 handle 只释放一次，buffer 保持固定上限；来源/member/tenant/connector/process hard limit 不排队保存 open command、不跨 tenant 淘汰，媒体饱和时 deletion control reserve 仍可用。

### 22.3 Durable removal 与确认不存在

`PM-06` 让 retention 截止在同一 recording lock 下先停新访问并写唯一 deletion outbox；adapter durable delete ledger、外部 delete effect 和 authoritative absence confirmation 是三个独立阶段。delete 2xx/void、媒体 `404`、PBX 断线或暂时不可读都不能产生 `REMOVED`。只有绑定原 registry/delete identity、相同 build/backend/storage scope 且排除 live/stored/替代 format 的确认 delivery 被核心应用后才进入 `REMOVED`，宽限期后再清除 encrypted ref/HMAC。read/start 漂移不取消删除义务，退役必须保留最小 delete-confirm lane。

### 22.4 执行与剩余门槛

同一 `B22-PM/1` 六组场景必须运行在规范模型、真实 MySQL/HTTP 加 durable media simulator，以及每个真实 PBX adapter build/backend/profile 三层。真实 backend 不能证明原生 range 时只可声明 `STORED_FULL_ONLY`，但仍要通过 range 负向断言。报告绑定 build、binding revision、storage profile、hard limits、隔离与 deletion procedure，且禁止出现 recording name/path/ref、URL、credential、媒体 bytes 或 provider 异常。

B22-PM 闭合 PBX stored-recording/media 组合 oracle，不表示 runtime 或真实 adapter 已通过。B22-PD 已独立闭合号码/密钥/quarantine/确认销毁设计；真实 canary、最终类型与 fixture harness 实现仍是后置门槛。

## 23. 当前任务板与并行关系

> 更新日期：2026-07-19
>
> B22a 已作为独立身份/控制平面子批次交付。本节其余行只跟踪呼叫 runtime 实现前检查点，不预占 B22b 及后续 API、Java 类型、错误码、表或 Flyway 版本。

| 任务 | 状态 | 交付物 | 依赖 | 并行关系 |
| --- | --- | --- | --- | --- |
| B22a | 已实现 | Connector 注册、一次性凭据、人工控制平面、独立 principal、tenant 解析、认证限流、registration 锁与 V58 | 第 12 节 | 后续 runtime 的身份基础 |
| B22-PC | 已完成 | PBX command crash、两类 start guarantee、`AMBIGUOUS`、权威不存在与跨 connector 隔离组合契约 | B22-R/A/S/CP | — |
| B22-PD | 已完成 | PBX 号码、密钥、retention、quarantine allowlist、日志泄漏哨兵与确认销毁组合契约 `B22-PD/1` | B22-D、B22-CP、B22-PC | — |
| B22-PM | 已完成 | [PBX 录音晚到、stored-object 证明、完整/单 range、取消、大小/时间上限与删除确认组合契约](docs/B22-PM-pbx-media-contract.md) `B22-PM/1` | B22-M、B22-D、B22-A、B22-CP | — |
| B22-CANARY | 后置 | 真实 PBX/线路版本、隔离、readiness、command、敏感数据与媒体的私有 conformance/canary 证据 | B22-PC、B22-PD、B22-PM；可用的真实私有适配器环境 | 设计 oracle 均已冻结；必须等待真实环境且不得以设计结果宣告通过 |
| B22-SPLIT | 后置 | 汇总全部门槛并拆分正式实现子批次 | B22-CANARY 及既有 B22-CW/CP/PC/PD/PM | 依赖完成后执行 |

下一项 B22 优先任务是 B22-CANARY；它只能在可用的真实私有适配器环境中执行，可与 B23 汇总任务并行。B22a、B22-PD/1 与 B22-PM/1 都不能替代真实 adapter conformance，也不能为了复用 fixture 或接入新事实而放宽 B22-R/D/S/M/A 已冻结的不变量。
