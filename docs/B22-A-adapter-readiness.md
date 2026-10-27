# B22-A 真实适配器 readiness 与隔离契约设计

> 决策日期：2026-07-19
>
> 状态：真实 adapter readiness 与逻辑 connector 隔离的实现前设计闭合；只冻结 provider-neutral 快照、操作门、隔离证明、漂移处理和契约测试，不实现 adapter、API、表、配置或真实电话调用
>
> 输入：[B22 总规划](../batch-B22.md)、[B22-R 可靠性设计](B22-R-reliability.md)、[B22-S 状态与拓扑设计](spikes/B22-state-and-topology.md)、[B22-D 敏感数据设计](../batch-B22.md#14-b22-d-敏感数据录音访问与保留设计闭合)、[B22-M 媒体传输设计](spikes/B22-media-transfer.md)、[云 Webhook spike](spikes/B22-cloud-webhook.md)和 [PBX 事件流 spike](spikes/B22-pbx-event-stream.md)

## 1. 决策摘要

B22-A 把“adapter 健康”拆成可审计、按操作失败关闭的 readiness，而不是一个 TCP 存活或 Spring health 布尔值：

- connector 的 `ACTIVE` 生命周期、adapter 的 readiness 和某个成员 endpoint 的短时 capability 是三个正交概念。`ACTIVE` 不表示可起呼，readiness 不授予 CRM authority，endpoint `AVAILABLE` 也不保证下一次 dispatch 成功；
- 核心只保存 provider-neutral 的 `ConnectorReadinessSnapshot` 规划投影。快照绑定当前 connector、adapter build、契约版本、私有 binding revision、检查序号和有效期，并分别给出 `START_OUTBOUND`、`OBSERVE_INBOUND`、`TERMINATE`、`RECONCILE`、`RECORDING_READ`、`RECORDING_DELETE` 的结果；
- 每个操作只允许 `READY`、`NOT_READY` 或 `UNSUPPORTED`。缺失、过期、乱序、版本不兼容或证据不足由核心派生为 `UNKNOWN` 并失败关闭；不使用含义不清的全局 `DEGRADED`；
- 新外呼必须同时满足 B22-R durable store/capacity/start guarantee、B22-S agent-first/two-party proof/endpoint probe、B22-D key/retention、B22-M media transport profile，以及本节的 provider 认证、命令权限、callback/stream 所有权、隔离和版本证据；
- readiness 的正向快照最长有效 5 分钟，adapter 或部署可以设置更短期限。它只是 admission freshness，不承诺 provider 在有效期内不会故障；adapter 在越过 B22-R `DISPATCHING` 门前仍要用本地当前状态重新检查；
- 一次安全读探测不能证明起呼、挂断或录音删除的写语义。写 capability 必须同时来自受治理权限/配置读回、与该 adapter build 绑定的真实环境 conformance fixture，以及实际命令仍可能返回的安全拒绝；readiness 不允许为了“探测”而拨打真实号码、挂断活动通话、读取真实录音或删除媒体；
- 每个逻辑 connector 在解析 provider 输入前已经由受治理 callback route 或独占 stream owner 选定。provider account、Account SID、Stasis app、channel variable、Caller ID、extension 或事实 body 只能做一致性佐证，永远不能选择 connector 或 tenant；
- 一个私有 adapter 进程可以服务多个 tenant，但 credential、provider binding、callback/stream、command ledger、journal、session registry、capacity、quarantine 和日志指标都按 connector 分区。native 资源还要经过 adapter trust zone 内的不可改绑 ownership registry，不能被两个 connector 同时认领；
- 配置或订阅漂移会立即撤销受影响操作的正向 readiness。若漂移可能造成事件缺口，adapter 从上一次被证明正常的时间起保守打开 B22-R observation gap，再按当时能力对账，不能只刷新 health 后继续起呼。

本设计闭合 [B22 第 11.2 节](../batch-B22.md#112-实现门槛闭合状态)第 11 项的真实 readiness/隔离语义。它不表示某个真实 adapter 已通过验证；[B22-CW](B22-CW-cloud-callback-fixture.md) 已把云 callback readiness 漂移与 B22-R/S/D/M 组合，[B22-CP](B22-CP-pbx-composed-contract.md) 后来闭合 PBX reconnect/fencing、session、topology、subscription isolation 与 gap 的核心组合 fixture 设计，[B22-PC](B22-PC-pbx-command-contract.md) 再闭合 PBX command crash/`AMBIGUOUS` 与三层 start gate 的组合，[B22-PD](B22-PD-pbx-sensitive-data-contract.md) 又闭合 PBX 敏感数据组合，[B22-PM](B22-PM-pbx-media-contract.md) 进一步闭合 PBX stored-recording/media 组合。真实 provider conformance 仍是拆分实现子批次前的独立门槛。

## 2. 三层状态与信任边界

### 2.1 Connector 生命周期

核心 registration 的 `ACTIVE`、`DISABLED` 和 `RETIRED` 继续由人工 control plane 管理：

- `ACTIVE` 只表示该 connector 可以参加 readiness 检查、ingress 和 dispatch 仲裁；
- `DISABLED`/`RETIRED` 始终覆盖任何旧的正向快照，readiness 不能重新启用 registration；
- readiness 失败不自动退役 connector，因为 backlog 重投、gap 上报、对账、到期录音删除和安全停用可能仍需原 connector identity；
- 初次启用或停用后恢复必须有当前 binding revision 的新快照，不能沿用停用前结果。

### 2.2 Connector readiness

readiness 是对“这个 connector 的这个 adapter build 和当前私有配置，是否能够安全执行指定操作”的短期证据。它不描述某个成员有没有设备，也不表示一次具体命令已经得到 provider 接受。

正向 readiness 不能由以下任一事实单独推出：

- DNS/TCP/TLS 可连接；
- provider credential 验证成功；
- 核心 connector credential 验证成功；
- Spring bean 已装配或进程存活；
- 一次 `GET`/ping 成功；
- 最近一条 callback、WebSocket frame 或真实通话成功；
- adapter 声明了一个字符串 capability。

### 2.3 Member endpoint capability

B22-S 的 member endpoint projection 继续只描述指定活动成员的 `CONFIGURED/NOT_CONFIGURED/DISABLED` 与短时 presence。起呼需要 connector 的 `START_OUTBOUND=READY` 和该成员的 `CONFIGURED`；presence 为 `UNKNOWN` 仍可在 agent-first 保护下尝试坐席腿。两者都不授予人工呼叫 authority、电话号码字段权限或业务记录可见性。

## 3. 私有 provider binding 与隔离

### 3.1 不可混淆的 binding

每个逻辑 connector 在私有 adapter 内绑定一个受治理的 provider route，至少包含以下私有概念：

- provider authority/account/project 或 PBX node 的稳定身份；
- callback route、Stasis application、ESL stream/filter 或语义等价的事件入口；
- 允许的起呼、挂断、查询、录音和 endpoint 配置面；
- adapter durable store 分区、凭据引用、版本范围、retention/capacity 配置；
- 单调递增的 `bindingRevision`。

这些值不进入核心 registration、普通 API 或 normalized fact。核心只保存不透明 revision、adapter build/contract identity、provider-neutral capability 和安全原因。`bindingRevision` 不是 principal、tenant 或 connection generation；它只用于使旧 readiness 失效。

provider account、app、node 或 route 的安全相关变更必须增加 revision。secret/key/certificate 的同 binding 轮换也要重新探测，但不改变 `connectorRef`、tenant、command、delivery 或 external session identity。把 connector 迁移到另一个 tenant 仍必须创建新 connector；revision 不能绕过不可改绑规则。

### 3.2 输入在解析前选定 connector

provider→adapter 的路由顺序固定为：

```text
governed callback route or fenced stream owner selects one logical connector
  -> verify original provider transport against that connector binding
  -> corroborate account/app/resource ownership
  -> parse an allowlisted native fact
  -> bind or look up native session ownership
  -> normalize and journal under the already selected connector
```

下列做法全部禁止：

- 用 callback body 中的 Account SID、tenant、自定义 metadata 或号码查 connector；
- 用 ARI `appArgs`、channel variable、ESL header、extension 或 dialplan 字段查 tenant；
- 先把 raw event 放进共享队列，再由消费者按可伪造字段分 tenant；
- 对无法唯一归属的事件“选第一个匹配 connector”；
- 把一个 connector 的 journal/backlog 改写成另一个 connector 的 credential 重投。

### 3.3 Native resource ownership registry

私有 adapter trust zone 维护最小、durable 的 provider resource ownership：

```text
(providerAuthorityFingerprint, nativeResourceType, nativeResourceKey)
  -> exactly one connectorRef and stable externalInteractionRef
```

它与 B22-R session registry 可以共享事务，但必须保留以下额外隔离不变量：

- native session、leg、recording 或 command correlation 一经绑定不能改到另一 connector；
- terminal/tombstone 在 callback、reconciliation 和最大迟到窗口结束前保留，资源 ID 复用不能归到新 connector；
- 同一 provider 资源同时出现在两个 route/stream 时隔离并告警，不向任一核心 tenant 猜测投递；
- provider authority 使用私有稳定指纹，不保存到核心，也不能由 native object ID 单独替代；
- ownership lookup 不返回 CRM tenant、actor、member、authority 或业务记录。

### 3.4 隔离证明等级

readiness 审计需要记录一种 provider-neutral 隔离证明，不把不同强度都包装成“独立账号”：

| 证明 | 含义 | 允许条件 |
| --- | --- | --- |
| `DEDICATED_PROVIDER_BOUNDARY` | connector 拥有独立 provider account/subaccount、PBX node 或等价安全边界 | 仍需 route、store 和 core credential 隔离 |
| `PROVIDER_RESOURCE_SCOPED` | provider credential/应用能把控制和事件限制在 connector 专属资源 | 必须实测跨资源访问被 provider 拒绝 |
| `ADAPTER_ENFORCED_TRUST_ZONE` | provider 原语本身较宽，受信私有 adapter 用独立 route/stream、ownership registry 和分区执行逻辑隔离 | 必须通过同进程双 tenant 负向 fixture；不能宣传为 provider 强制隔离 |

三者都不允许 body 选择 tenant。若一个 provider/PBX 既不能提供独立边界，也无法让 adapter 在解析前建立唯一 route/stream 和全局 ownership 仲裁，该 connector 的 ingress 与 start 都是 `NOT_READY`。

### 3.5 Store、容量和可观测性隔离

command ledger、journal、session/ownership registry、query ledger、connection fence、quarantine 和 control-plane reserve 都以 connector 为键和容量分区。共享数据库允许使用，但一个 connector 的 flood、high-water、quarantine 或长查询不能淘汰、阻塞删除或借用另一个 connector 的未完成记录。

指标可以按 connectorRef 的安全表示聚合；禁止 provider account/app/node、callback URL、电话号码、endpoint、native resource ID、credential、录音引用和任意 provider error 成为普通日志、trace 或 metric label。

## 4. Readiness snapshot

### 4.1 最小投影

规划中的快照至少包含：

| 字段 | 语义 |
| --- | --- |
| adapter contract version | 与开源核心规范契约兼容的版本，不是 provider API version |
| adapter build identity | 不含 secret 的不可变构建/发布标识和 `PRODUCTION/TEST_ONLY` deployment class，用于关联 conformance evidence |
| binding revision | 当前 connector 私有配置的单调 revision；不选择 tenant |
| probe sequence | connector 内跨进程重启单调递增，防止旧正向结果覆盖新失败 |
| checked/received/expiry | adapter 检查时间、核心接收时间和不超过 5 分钟的正向有效期 |
| operation readiness | 每个固定操作的 `READY/NOT_READY/UNSUPPORTED` 与安全原因集合 |
| topology capabilities | B22-S 固定 capability 集合，不接受任意字符串扩展 |
| start guarantee | 恰好一个 B22-R `IDEMPOTENT_START(window)` 或 `AT_MOST_ONCE_START` |
| reconciliation snapshot | B22-R 固定的 query/replay/CDR/absence/orphan 能力和覆盖窗口 |
| isolation evidence | 上节证明等级、路由/ownership/store 检查结果和安全配置摘要 |
| policy compatibility | adapter retention/capacity、核心 key/policy 前置和可选录音策略是否匹配 |

provider API/version、account、app、node 和 permission 的原值留在私有审计。核心最多保存“版本兼容”“权限已证明”及 adapter 计算的非秘密配置摘要，不能把 readiness 变成 provider 配置仓库。

`TEST_ONLY` build 在任何 production profile 中固定为所有外部操作 `NOT_READY`，不能靠配置开关、手工改快照或同名 bean 提升为生产 adapter。生产 build 也只有在对应版本的组合 fixture evidence 存在时才能产生正向快照。

### 4.2 新旧快照仲裁

- adapter 在 durable store 中为每个 connector 维护永不回退的 `(bindingRevision, probeSequence)`；换 owner、重连或进程重启不能重置；
- 核心在 registration 锁内只接受当前认证 connector 按 `(bindingRevision, probeSequence)` 字典序更高的结果，并计算自己的 `receivedAt` 和 snapshot digest；
- 相同序号和 digest 是幂等 duplicate，相同序号不同 digest 是安全冲突；低序号、旧 revision 或迟到 response 不能恢复 readiness；
- 正向有效期以 `min(adapterExpiresAt, coreReceivedAt + 5 minutes)` 为上限。明显未来时间、负期限、溢出或时钟偏差超界直接拒绝；
- `NOT_READY`/隔离撤销可以随时提前使正向快照失效。为避免故障时无法“报告不健康”，本地 operation gate 也必须立即停止领取 start，不能等待核心 TTL 到期；
- snapshot 是持久化 control-plane 事实。核心 outbox claim 在短事务中读取它，不能在持锁事务内同步调用 adapter。

### 4.3 安全原因

原因使用固定、无 provider 文本的分类，例如：`SNAPSHOT_MISSING`、`SNAPSHOT_EXPIRED`、`BINDING_DRIFT`、`ISOLATION_UNPROVEN`、`PROVIDER_AUTH_FAILED`、`CONTROL_PERMISSION_MISSING`、`OBSERVATION_PATH_MISSING`、`STREAM_OWNER_MISSING`、`DURABLE_STORE_UNAVAILABLE`、`CAPACITY_EXCEEDED`、`CONTRACT_VERSION_UNSUPPORTED`、`RECONCILIATION_UNPROBED`、`RETENTION_INVALID` 和 `MEDIA_DELETE_UNPROVEN`。

实现批次可以固定最终枚举和错误码，但不能返回 provider response body、异常 message、账号、app、URL、endpoint 或配置 diff。

## 5. 探测维度和证据

readiness 至少逐项检查：

| 维度 | 可接受证据 | 失败影响 |
| --- | --- | --- |
| contract/build | adapter build 的版本化 conformance manifest 与核心支持范围相交，deployment class 允许当前 profile | 所有新操作失败关闭；test adapter 永不进入 production |
| provider API compatibility | 受支持的明确版本/feature profile；breaking 或未知 major 不兼容 | 受影响操作 `NOT_READY`，旧 backlog 只按兼容 parser 安全处理 |
| provider authentication | 安全 read-only identity/probe 成功，且返回的 authority 与私有 binding 一致 | provider I/O 操作 `NOT_READY` |
| connector isolation | 唯一路由/stream、ownership registry、store partition 和双 tenant 负向证据 | start/ingress `NOT_READY`，歧义输入 quarantine |
| observation path | required callback 配置/命令模板读回，或当前 fenced stream owner 与最小订阅成立 | start/inbound observation `NOT_READY`；漂移打开 gap |
| control permission | 权限清单/readback、非破坏性 API surface 检查和 build fixture | start/terminate 分别失败，不从一次 auth 成功推断 |
| topology | B22-S capability 由相同 build 的云/PBX fixture 证明 | 缺 agent-first、two-party proof 或 endpoint probe 时 start 不可用 |
| durability | ledger/journal/session/query store 可原子提交，唯一键、lease/fence 与 control reserve 可用 | start 停止；ingress 按 B22-R 失败/断流并开 gap |
| capacity/retention | 每 connector 低于 hard limit，tombstone 和敏感期限不短于声明窗口 | start 停止；不能淘汰 unresolved 记录 |
| reconciliation | 每项能力显式 `SUPPORTED` 或 `UNSUPPORTED`，覆盖窗口/一致性/分页已探测 | 未探测时 start 不可用；明确 unsupported 仍保留真实不完整语义 |
| member endpoint | 能确定唯一受治理 endpoint 配置；presence 可选 | 未配置/歧义阻止该成员 start，不泄露设备列表 |
| recording/media | B22-M 的 stored-object 时点、格式、受控 read、幂等 delete/确认不存在、egress/path、range 和资源上限 fixture | 只阻止录音启用/访问，不阻止明确无录音的普通通话 |

readiness probe 只执行 read-only、无外部人类副作用的操作。需要写权限的能力由最小权限配置证据和隔离测试环境 canary 在发布/部署验收时证明；生产周期探测不能创建真实 Call/channel、拨打测试号码、挂断现有资源或删除真实 recording。

## 6. 操作门

### 6.1 `START_OUTBOUND`

新外呼的正向门至少要求：

- registration 当前 `ACTIVE`，当前快照未过期且 binding revision/adapter contract 匹配；
- provider binding、认证、隔离和明确 API profile ready；
- durable command ledger、journal、session/ownership registry、capacity、retention 和 control reserve ready；
- required callback 或 fenced stream owner ready，且生命周期/终态观察集合完整；
- 恰好一种 start guarantee 已声明，reconciliation 能力已明确探测；
- `AGENT_FIRST_OUTBOUND`、`TWO_PARTY_MEDIA_PROOF`、`AGENT_ENDPOINT_CONFIGURATION_PROBE` ready；
- 指定成员当前 endpoint 为 `CONFIGURED`；
- B22-D 的核心 key provider、deployment ceiling、tenant policy 和本次 recording policy ready；启用录音时还要求 B22-M media profile ready。

核心创建 interaction/outbox 前检查持久化快照；worker claim 再检查；adapter 在 ledger `RECEIVED -> DISPATCHING` 前检查本地当前状态。三个检查都可能拒绝，任何一个成功都不替代另外两个。

### 6.2 `OBSERVE_INBOUND`

呼入观察需要唯一 route/stream、原始来源验证、journal/session ownership、两腿归一、agent bridge observation、two-party proof、capacity 和 core ingress ready。它不要求 outbound start 权限或 endpoint probe。无法唯一归属的 native 输入保持私有 quarantine；不能为了避免漏呼入而使用 body 猜 tenant。

### 6.3 可选控制和恢复操作

| 操作 | 独立规则 |
| --- | --- |
| `TERMINATE` | 需要 `TERMINATE_INTERACTION` 与控制权限；命令响应不产生 lifecycle 终态 |
| `RECONCILE` | 只需要声明的 read/query/replay 能力和 durable query ledger；即使 start 不 ready 也应继续处理已有 case |
| `RECORDING_READ` | 需要受控 read capability、B22-D 每次人工授权和 B22-M format/range/resource profile；禁止 provider redirect/URL |
| `RECORDING_DELETE` | 需要幂等删除与确认不存在能力；start/媒体读取失败时仍应继续到期删除，失败则重试并保持不可访问 |
| backlog drain | adapter→core 重投不需要 provider start ready；仍要求原 connector credential、scope 和 durable identity |
| disable/retire | 永远不依赖正向 readiness；必须能够停止新领取、释放 owner 并保留必要 tombstone/gap |

## 7. 配置漂移、版本升级与 gap

adapter 必须周期性检查并在受治理配置变更时主动检查。至少以下变化使相关快照立即失效：

- provider account/project/node、callback route、Stasis app、ESL filter/stream 或资源 ownership 改变；
- required callback 事件集合、recording callback、`subscribeAll`、显式资源订阅或 stream owner 改变；
- credential 权限、ARI `read_only`、provider feature/API major/breaking version 改变；
- adapter contract/build、normalizer/topology profile、start guarantee 或 reconciliation window 改变；
- durable schema、unique constraint、lease/fence、capacity、retention、key/policy 或 media delete 配置改变。

漂移处理固定为：

1. 在 adapter 本地原子增加 revision/sequence，撤销受影响 operation 的 ready 状态并停止新 start 领取；
2. 持久化安全 drift 分类和检测时间，不保存敏感 diff；
3. observation/callback/owner 漂移从上一份快照的 `checkedAt` 到检测时刻保守打开 gap；若只能确定更早的最后正常事件，则使用更早时间；
4. 已经 `DISPATCHING` 或 `AMBIGUOUS` 的命令按 B22-R 处理，不因新配置重新 start；
5. 新配置通过当前 build fixture 和 live probe 后产生更高 revision 的新快照；旧 gap 独立对账，新 readiness 不能把它抹去。

API 升级不自动采用未知字段或状态。compatible non-breaking 版本仍需 schema/fixture 允许；unknown enum、breaking revision 或 provider 改变 callback 签名时先失败关闭并 quarantine 单项，不能把原值塞入 `UNKNOWN + raw payload` 传给核心。

## 8. 云 Webhook profile

真实云 adapter 至少满足：

- 每个 connector 使用一个受治理且不可猜测为 tenant 的 callback route；签名校验覆盖厂商要求的完整 URL、query、form 参数或 raw JSON body，换 route 重放即使来自同一 provider account 也必须失败；
- API credential 返回的 account/project identity 必须与私有 binding 一致。callback body 中的 account/resource ID 只做一致性校验，不选择 connector；
- 共享 provider account 只有在 callback route、命令创建的 callback 参数、native resource ownership registry 和 store partition 都可证明唯一时才允许；否则使用独立 account/subaccount 或等价 provider boundary；
- 外呼命令显式请求首期需要的全部进度事件与独立 recording callback。以 Twilio 模型为例，只依赖默认 `completed` 不满足 readiness，因为 `initiated/ringing/answered/completed` 需要显式选择，queued 阶段取消还可能没有 callback；
- callback handler 在 durable journal commit 后才返回 provider success。外部可达性、TLS、签名 secret 和 journal capacity 都是 observation readiness 的组成部分；
- API create、fetch/history/correlation、terminate、recording read/delete 使用最小权限 credential。若 provider 提供 endpoint 级 restricted key，应限制到实际使用的 action；
- provider Call/recording URL、signed query、raw callback 和 error body 永不进入核心；adapter 用 stable opaque refs 和安全状态映射；
- “最近收到过 callback”只能补充运行证据，空闲 connector 不能因没有流量自动失败。配置 readback、部署 canary 和 runtime evidence 的来源必须分别可审计。

## 9. PBX stream profile

### 9.1 Asterisk ARI

每个逻辑 connector 至少使用独立 Stasis application 或经同等强度验证的独立路由，并满足：

- `/events` 只订阅该 app，`subscribeAll=false`；额外 resource subscription 只允许首期 topology 所需的最小集合；
- 当前 app 有且只有一个有效 WebSocket owner。`ApplicationReplaced`、未 clean close、lease/fence 切换或 core generation 改变立即撤销 observation/start readiness 并打开 gap；
- Applications API 的 app/resource snapshot 用于核对注册和当前订阅，Channels/Bridges 列表只能作为活动资源对账，不能宣称提供断线 replay 或历史完整性；
- ARI credential identity、TLS/WSS 和 API version 在支持范围内。`read_only=yes` 只允许 observation/query，不能声明 originate/hangup/recording delete ready；
- adapter 只控制由目标 Stasis app 和 ownership registry 证明属于该 connector 的 channel/bridge。独立 app 是事件/控制路由，不把普通 ARI user 自动宣传成细粒度 tenant credential；
- stored recording 的读取和删除是独立 capability。只有相同 build 的受控 read/delete fixture 证明对象映射、流转和删除确认后才可启用录音；
- ARI version 按 `MAJOR.BREAKING.NON-BREAKING` 兼容矩阵核对，不能只比较 Asterisk 产品字符串。

### 9.2 FreeSWITCH ESL

FreeSWITCH 官方 Event Socket 在认证后允许客户端执行 API 命令、originate 并订阅事件；因此单个 ESL password 本身不能证明 connector 级资源授权。首期 readiness 要求：

- 使用受限监听地址、ACL、强 credential 和 TLS/受限隧道；默认 password、全接口暴露和普通公网 TCP 失败关闭；
- 每个 connector 使用独立、fenced 的 stream owner 和固定最小事件集合/精确 filter；`event ... ALL`、日志流和运行时任意 filter 不能声明 ready；
- 同一 PBX node 多 connector 必须由 adapter trust zone 的全局 native ownership registry 仲裁，且双 tenant fixture 证明 command/event 不交叉。需要 provider/PBX 强制隔离的部署使用独立 FreeSWITCH node 或策略执行代理；
- `Core-UUID`/`Event-Sequence` 只进入 gap/reconciliation 证据，不选择 connector，也不被描述成 connector 无缺口 cursor；
- originate、hangup、recording 和任意 dial string 只由私有固定模板产生，公共端口不能提交 API command、application、header 或 variable map；
- 版本、模块、event 类型、query/CDR 能力和 recording backend 都绑定明确 adapter profile；未知模块输出或任意文本不能直接规范化。

## 10. Control plane 与普通输出

普通呼叫用户最多看到 provider-neutral 的“当前可尝试/不可用”和安全原因，不得到 adapter build、隔离等级、provider 版本或配置证据。tenant 呼叫管理员可以读取有界的操作矩阵、检查/过期时间和安全原因；更详细的 build、revision、conformance 与隔离证明只属于部署运维审计。

任何 readiness 页面、错误、日志或审计都不得泄露 provider account/app/node、callback URL、credential、endpoint、号码、native resource ID、recording ref、配置原值或异常 message。快照和审计不会提供修改 provider 配置的通用 API；真实配置继续由私有部署管理。

## 11. 契约测试门槛

真实云/PBX adapter 与开源 durable 模拟 adapter 在进入生产装配前必须复用以下 fixtures：

1. `ACTIVE` 但 snapshot 缺失/过期/不兼容时不能 start；readiness `READY` 也不能绕过 caller/member/字段/记录授权；
2. 两个 tenant、两个 connector、一个 adapter 进程的 route、credential、store、capacity、journal、session 和 native ownership 全部隔离；同 provider resource 不能绑定两者；
3. body Account SID/tenant/appArgs/variable/extension 不能选 connector；真实签名但错误 callback route、正确 PBX frame 但错误 stream 都拒绝或 quarantine；
4. 更低 sequence、旧 revision、重复不同 digest、未来时间和超过 5 分钟的正向快照不能复活 readiness；
5. callback/subscription/owner/config drift 先停止 start，再从最后证明正常的时点打开 gap；新快照不关闭旧 gap；
6. durable store 不可写、hard limit、control reserve 不可用、unique/lease/fence 失败时 start 停止，且不跨 connector 淘汰 unresolved 数据；
7. start、terminate、reconcile、recording read/delete 的操作门互相独立；start 不 ready 不阻止 backlog、已有 case 或到期删除；
8. recording 明确禁用时缺少 media capability 不阻止普通呼叫；启用时 read、delete、确认不存在、policy/retention 任一缺失都失败关闭；
9. readiness probe spy 证明周期探测从不 create call/channel、拨号、hangup、读真实媒体或 delete recording；写能力由测试环境 canary 和配置证据证明；
10. 云 fixture 覆盖完整 URL/raw body 验签、错误 route 重放、默认/漏配 callback、queued 取消、callback-before-response、独立 recording callback、最终一致查询和最小权限 credential；
11. Asterisk fixture 覆盖独立 app、`subscribeAll=false`、最小 resource subscription、`ApplicationReplaced`、`read_only`、active snapshot 无 replay、API breaking version 和 recording read/delete；
12. FreeSWITCH fixture 覆盖 ACL/credential、禁止 `ALL`、固定 filter、Core-UUID 变化、宽权限 ESL trust-zone 声明和同 node 跨 connector 负向命令/事件；
13. adapter artifact 的依赖与运行测试证明它只通过发布的 application/API 契约调用核心，不取得核心 repository、mapper、PO、datasource、provider-neutral 表或业务 secret；runtime binding 由显式配置选择，不依赖 classpath/`@Primary` 偶然顺序；`TEST_ONLY` build 在 production 永不 ready；
14. 所有 snapshot、普通输出、日志、trace、metric 和稳定错误只包含 provider-neutral 安全分类，不含账号、app、route、endpoint、号码、native ID、录音引用、credential 或异常原文。

这些 readiness/隔离 fixtures 还必须与 B22-R crash/gap、B22-S 两腿/topology、B22-D 敏感值/删除矩阵和 B22-M 媒体流/取消/上限矩阵组合运行。云 adapter 使用 [B22-CW](B22-CW-cloud-callback-fixture.md) 的 `CW-01` 至 `CW-07` 与统一 checkpoint；PBX 使用 [B22-CP](B22-CP-pbx-composed-contract.md) 的核心 trace、[B22-PC](B22-PC-pbx-command-contract.md) 的 command crash/`AMBIGUOUS` matrix、[B22-PD](B22-PD-pbx-sensitive-data-contract.md) 的 sensitive-data matrix 和 [B22-PM](B22-PM-pbx-media-contract.md) 的 stored-recording/media matrix。只通过本节的静态 readiness 测试不能让一个 adapter build 获得生产 conformance。

[B22-CP](B22-CP-pbx-composed-contract.md) 已固定上述 PBX 组合的第一套共享 trace：dirty reconnect 在新 generation ready 前持久化 gap，旧 fence 不能写 journal/checkpoint/session/ownership，进程重启后 correlation 稳定，early media/单腿 answered/无关系的两腿 ready 不接通，错误 stream/native ownership 不跨 connector，active snapshot 不冒充 replay。[B22-PC](B22-PC-pbx-command-contract.md) 又固定 `DISPATCHING` 前后的三层 gate、两种 start guarantee、event-before-response、只读 command reconciliation 和跨 connector command 隔离。[B22-PD](B22-PD-pbx-sensitive-data-contract.md) 固定 PBX 号码、key、retention、quarantine 与分层销毁，[B22-PM](B22-PM-pbx-media-contract.md) 进一步固定 live/stored 时点、full/native-range、取消/上限、delete ledger 和权威不存在确认。四者都只是实现前 oracle；真实 PBX canary 尚未完成，因此不产生生产 readiness。

## 12. 不属于 B22-A 的决定

- 最终 Java 类型、port 名、HTTP 路径、管理 UI、表名、Flyway 版本、错误码和配置键；
- 首个云厂商、Asterisk/FreeSWITCH 版本、PBX/SIP trunk、SDK 或私有 artifact 发布方式；
- B22-M 已冻结的媒体传输语义所对应的最终 URI、Java 类型、浏览器播放 UI 和转写；
- provider 账号采购、线路、号码、资质、计费、司法辖区录音合规和灾备拓扑；
- B22-CP、B22-PC、B22-PD、B22-PM 及其余跨设计组合 fixture 的具体 harness 代码、Docker/真实电话测试环境和 provider canary。

B22-A 只规定真实 adapter 在什么证据下可以声明某项操作 ready，以及多个 connector 如何失败关闭地共享一个受信 adapter 部署。它不会把部署声明升级成 provider 的密码学保证，也不会因为设计完成而声称真实电话能力可用。

## 13. 官方依据

- [Asterisk ARI Events API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Events_REST_API/)：`app` 必填，`subscribeAll` 默认为 `false`，启用后会取消 application-specific subscription 边界。
- [Asterisk ARI Event Models](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Asterisk_REST_Data_Models/)：`ApplicationReplaced` 表示新的 WebSocket 接管 application，且一个 application 同时只能由一个 WebSocket 订阅。
- [Asterisk Configuration for ARI](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/Asterisk-Configuration-for-ARI/)：ARI 由 HTTP、Stasis 和 WebSocket 共同组成；`read_only` 只允许 GET/OPTIONS；断线后可能继续接收 channel 但错过事件。
- [Asterisk ARI Applications API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Applications_REST_API/)：application 当前 channel/bridge/endpoint/device subscription 的查询与显式资源订阅边界。
- [Asterisk ARI Versioning](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/ARI-Versioning/)：ARI 使用 `MAJOR.BREAKING.NON-BREAKING` 版本语义，breaking 修改不能按普通存活探测忽略。
- [Asterisk ARI Channels API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Channels_REST_API/)与 [Bridges API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Bridges_REST_API/)：列表只覆盖当前活动资源，不提供断线期间历史 replay。
- [Asterisk ARI Recordings API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Recordings_REST_API/)：stored recording 的 metadata、binary read 和 delete 是不同 API 能力。
- [FreeSWITCH Event Socket](https://developer.signalwire.com/freeswitch/integration/event-socket/)：认证后的 ESL 可执行 API 命令、originate 与事件订阅；官方建议远程部署使用指定监听地址、ACL 和强密码，并提供固定事件/filter 语义。
- [FreeSWITCH Event Model](https://developer.signalwire.com/freeswitch/programming/event-model/)：事件是 header/body 字典，`Core-UUID` 与 `Event-Sequence` 属于 PBX 事件模型而不是 tenant identity。
- [Twilio Webhook Security](https://www.twilio.com/docs/usage/webhooks/webhooks-security)：Webhook 验签必须使用完整 URL、query/form 参数或 raw JSON body，证明 callback route 是来源校验的一部分。
- [Twilio Call Resource](https://www.twilio.com/docs/voice/api/call-resource)：进度 callback 事件需要显式选择，默认只有 completed；queued call 取消可能不发 callback，recording 另有独立状态 callback。
- [Twilio Restricted API Keys](https://www.twilio.com/docs/iam/api-keys/restricted-api-keys)：Restricted API key 可以按 API resource endpoint/action 限权，说明真实 readiness 应核对实际最小权限而非只验证账号可登录。
- [Twilio Recording Resource](https://www.twilio.com/docs/voice/api/recording)：recording metadata/status、media fetch 和 delete 是独立操作，不能由“录音存在”推断读取与删除都 ready。
