# B22-S 呼叫状态、两腿拓扑与坐席设备能力设计

> 决策日期：2026-07-19
>
> 状态：状态、interaction/leg、首期拓扑、接通判定和坐席/设备能力语义已完成设计闭合；未实现代码、API、迁移或真实适配器
>
> 前置：[B22 云 Webhook spike](B22-cloud-webhook.md)、[B22 PBX 事件流 spike](B22-pbx-event-stream.md)、[B22-R 可靠性设计](../B22-R-reliability.md)、[B22 connector 身份设计](../../batch-B22.md#12-integration-principal-与-connector-tenant-解析设计闭合)和 [B22-D 敏感数据设计](../../batch-B22.md#14-b22-d-敏感数据录音访问与保留设计闭合)

## 1. 决策摘要

B22-S 固定以下 provider-neutral 语义：

- `CallInteraction` 是 CRM 的一次呼叫交互，不等于云厂商 Call、PBX channel、bridge 或设备会话；
- 首期每个 interaction 只允许两个规范参与腿：一个 `AGENT` 腿和一个 `REMOTE_PARTY` 腿。私有适配器可以在自己的 session registry 中管理更多 native channel、holding bridge、control leg 或 Local channel，但不能把它们逐个镜像进核心；
- 首期点击外呼只采用 `OUTBOUND_AGENT_FIRST`：先呼叫坐席的私有 endpoint，坐席腿媒体就绪后才允许创建并呼叫对端腿；
- 首期呼入采用 `INBOUND_AGENT_BRIDGE`：对端腿先被发现，IVR、排队和设备选择留在私有适配器；核心只观察最终被选择的坐席腿与对端腿；
- interaction 的 `CONNECTED` 只能由显式的 `PARTICIPANTS_CONNECTED` 规范事实建立。核心必须已经绑定两个不同角色的腿，两腿都达到 `MEDIA_READY`，且 adapter 证明它们处于首期预期的双向两方媒体关系；
- lifecycle、dispatch、observation 和 recording 是四个正交维度，不合并成一个同时表达“拨号中、回调丢失、录音处理中”的巨大状态枚举；
- CRM 成员、外部 agent identity 和具体设备/endpoint 是三种不同身份。核心可以治理外部 agent 到成员的不可变绑定并查询安全的成员呼叫能力，但设备地址、分机、注册、token、IP 和媒体凭证始终留在私有适配器。

这组决策闭合 [B22 第 11.2 节](../../batch-B22.md#112-实现门槛闭合状态)第 4、6、9 项的状态侧前置。双重幂等、事件 journal/对账和号码/录音敏感边界分别由 B22-R、B22-D 闭合；媒体传输后来由 [B22-M](B22-media-transfer.md) 闭合，真实厂商 readiness/隔离后来由 [B22-A](../B22-A-adapter-readiness.md) 闭合，[B22-CW](../B22-CW-cloud-callback-fixture.md) 已组合验证云 callback 的两腿/乱序接缝，[B22-CP](../B22-CP-pbx-composed-contract.md) 再闭合本状态模型与 PBX fencing/session/subscription/gap 的核心组合 fixture 设计，[B22-PC](../B22-PC-pbx-command-contract.md) 又闭合 PBX command 与 event-before-response 的组合，[B22-PD](../B22-PD-pbx-sensitive-data-contract.md) 再闭合 PBX 敏感数据组合，[B22-PM](../B22-PM-pbx-media-contract.md) 进一步闭合 PBX stored-recording/media 组合。真实 adapter 仍是独立门槛。

## 2. 核心对象边界

### 2.1 `CallInteraction` 根

规划中的 `CallInteraction` 是 tenant-scoped、connector-scoped、版本化聚合根。它至少拥有：

- 核心生成的 `interactionRef`、创建时固定的 connector，以及可在 dispatch/discovery 后建立但一经绑定就不可更换的 adapter-scoped `externalInteractionRef`；
- `INBOUND` 或 `OUTBOUND` 方向，以及与方向匹配的首期 topology；
- 可空的当前 CRM 坐席成员。人工外呼从已验证 caller 派生；呼入只能通过 connector agent directory 解析，不能直接采用事实 body 中的 actor/member；
- 一个可选的 CUSTOMER、CONTACT 或 LEAD 业务锚点。锚点可见性与 interaction 可见性继续只取交集；
- lifecycle 里程碑时间、终止安全分类、observation 状态、录音可用性和乐观锁版本；
- 最多两个规范 `CallLeg` 子实体。

interaction 不保存 provider 状态字符串、bridge ID、channel technology、SIP cause、任意 headers/variables、设备地址或原始 callback。`externalInteractionRef` 和 `externalLegRef` 只用于 connector-scoped 关联与幂等，普通 CRM API 不返回它们。

### 2.2 `CallLeg` 子实体

首期 leg vocabulary 固定为：

| 概念 | 规划语义 |
| --- | --- |
| `role` | 只允许 `AGENT` 或 `REMOTE_PARTY`，创建后不可改变 |
| `externalLegRef` | adapter 提供的稳定、不透明、connector-scoped 腿引用；同一引用不能绑定两个 interaction |
| `DISCOVERED` | adapter 已识别该规范参与腿；不表示振铃或媒体成立 |
| `ALERTING` | 该腿对应的终端正在被提示接听；早期媒体不等于 alerting 对端已接听 |
| `MEDIA_READY` | 该参与腿与电话平台之间已经建立可双向传输媒体的路径；adapter 可从 native answered、`Up` 或语义等价事实归一化 |
| `TERMINATED` | 该规范参与腿已不可继续属于本次首期两方媒体路径 |

`MEDIA_READY` 刻意不用“整通电话已接通”命名。Asterisk 的 `Up` 只说明一条 channel 与一个设备之间可双向传输媒体；Twilio 的 `in-progress` 也只描述对应 Call resource。两条腿分别媒体就绪，仍不证明它们彼此已经通信。

每个 interaction 最多一个 `AGENT` leg 和一个 `REMOTE_PARTY` leg。首期不接受第三条规范腿，也不接受一个角色的替换腿；transfer、consult、conference、retry-to-another-device 和多坐席场景必须以后通过新 topology 和显式模型演进加入。私有适配器为实现拨号产生的额外 native channel 必须折叠在自己的 durable session registry 内。若一个实现无法稳定归一为两个参与腿，它不能声明首期 topology ready。

### 2.3 关系和不可变性

- `(tenant, connector, externalInteractionRef)` 唯一且不可改绑；
- `(tenant, connector, externalLegRef)` 唯一且不可改绑，不能依赖请求中的 interactionRef 把同一外部腿切换到另一通话；
- 两条腿必须与 interaction 属于同一 tenant 和 connector；数据库复合约束需要重复保护这一点；
- 外呼的 agent member 在 interaction 创建时由 caller 当前活动成员关系固定。呼入的 agent member 在首次成功解析外部 agent binding 时固定；后续 binding 变更不重写历史 interaction；
- 呼入可以在外部 agent 尚未解析时保持 `agentMemberId=null`，也可以在这种情况下记录真实 `CONNECTED`。它只能证明存在规范 `AGENT` 腿，不能伪造一个 CRM 成员；未分配记录的人工可见性以后由管理能力决定；
- provider 时间和数据库接收时间分别保存语义，不用晚到 callback 覆盖核心审计时间。

## 3. 正交状态模型

### 3.1 Interaction lifecycle

当前 lifecycle 由不可清除的里程碑派生，而不是靠覆盖一个易倒退的 status 字段：

| 派生状态 | 条件 | 语义 |
| --- | --- | --- |
| `PREPARING` | 尚无 `ringingAt`、`connectedAt`、`terminatedAt` | 本地意图已接受或呼入已发现，但首期有意义的被叫振铃尚未确认 |
| `RINGING` | 有 `ringingAt`，无 `connectedAt`、`terminatedAt` | 外呼时对端腿正在振铃；呼入时坐席腿正在振铃 |
| `CONNECTED` | 有 `connectedAt`，无 `terminatedAt` | 两条规范参与腿已经通过第 5 节的媒体证明建立两方通信 |
| `TERMINATED` | 有 `terminatedAt` | 本次首期两方拓扑已结束；是否曾接通由 `connectedAt` 独立判断 |

外呼中的坐席腿振铃仍属于 interaction `PREPARING`，因为客户尚未被呼叫。这样 public `RINGING` 不会错误暗示“客户正在响铃”。呼入中的私有 IVR、队列或 holding bridge 也不产生 `CONNECTED`；只有最终坐席腿 alerting 才形成首期 interaction `RINGING`。

里程碑只允许单调补充：

- `RINGING` 可以缺失并直接到 `CONNECTED` 或 `TERMINATED`；
- 终态先到时仍可补录一个实际发生在终止前、且与终止分类不冲突的 `ringingAt` 或 `connectedAt`，当前派生状态保持 `TERMINATED`；
- 没有可信 provider 时间或其他有序证明时，终态后的 connected 事实不能猜测发生顺序，进入 reconciliation；
- 已确定的里程碑不能清空或换成另一个时间；重复事实只确认原值；
- adapter 断线、命令超时或 callback 暂未到达都不能自行生成 `TERMINATED`。

### 3.2 Dispatch

外呼命令的 dispatch 状态继续独立保存：`PENDING`、`CONFIRMED`、`REJECTED`、`AMBIGUOUS`。它只描述 adapter 是否确认电话平台接受了命令，不描述振铃、接通或终止。

- `CONFIRMED` 不能推进 lifecycle；
- `REJECTED` 可以在尚无任何外部呼叫可能性的确定证据下终结 interaction，但不能覆盖已经观测到的真实腿事实；
- `AMBIGUOUS` 触发 reconciliation，不能自动重试并创建第二通电话；
- callback-before-command-response 仍按 interaction/leg 事实推进，晚到的 dispatch 结果不能倒退 lifecycle。

### 3.3 Observation

观测质量使用独立状态：

| 状态 | 语义 |
| --- | --- |
| `NOMINAL` | 当前没有已知观测缺口；不承诺 provider 从未丢事件 |
| `RECONCILIATION_REQUIRED` | 已知 gap、dispatch ambiguity 或事实冲突需要查询/账本恢复 |
| `RECONCILED` | 已知缺口已由 adapter 声明的权威查询范围成功覆盖；仍保留 gap 与对账审计时间 |
| `OBSERVATION_INCOMPLETE` | adapter 没有能力恢复所需事实，或对账后仍无法确定完整 lifecycle |

普通进度事实不能自动把 `RECONCILIATION_REQUIRED` 或 `OBSERVATION_INCOMPLETE` 清回 `NOMINAL`。只有与 connector capability 一致的显式 reconciliation 结果可以进入 `RECONCILED`；新的 gap 可以再次进入 `RECONCILIATION_REQUIRED`。因此 `TERMINATED + OBSERVATION_INCOMPLETE` 是合法且重要的组合，不能伪装成正常完成。

### 3.4 Recording

`UNAVAILABLE`、`PROCESSING`、`AVAILABLE`、`REMOVED` 继续是独立 recording availability。录音 callback 可以早于或晚于呼叫终态；它不推进或倒退 lifecycle，也不把媒体 URL 带入核心。

## 4. 首期呼叫拓扑

### 4.1 外呼：`OUTBOUND_AGENT_FIRST`

首期点击外呼固定为坐席优先：

```text
CRM accepts intent                    interaction PREPARING
  -> adapter alerts private agent endpoint
     -> AGENT leg MEDIA_READY
        -> adapter creates and alerts REMOTE_PARTY leg
           -> remote alerting         interaction RINGING
           -> remote MEDIA_READY
              -> adapter proves two-party media relation
                 ->                  interaction CONNECTED
  -> either required leg ends         interaction TERMINATED
```

硬约束：

- 坐席腿达到 `MEDIA_READY` 前，adapter 不得创建、呼叫或振铃对端腿；
- 坐席未配置、拒接、超时、离线或在起呼窗口失效时，interaction 在联系客户之前终止；
- 坐席接听只说明可以继续呼叫客户，不能产生 interaction `CONNECTED`；
- 对端接听后仍需 adapter 证明两条正确角色的腿进入同一双向两方媒体关系；
- 不支持先拨客户后补坐席、客户接听后播放等待音、同时拨多台坐席设备或自动重试另一设备。

选择坐席优先的原因是当前核心不提供浏览器媒体或 CRM 设备控制。它能在不暴露设备的情况下保证真实坐席先参与，再联系客户，并使客户振铃、双方接通和设备不可用具有一致语义。代价是起呼多一步且坐席必须先接听；这是首期明确接受的产品取舍。

### 4.2 呼入：`INBOUND_AGENT_BRIDGE`

```text
adapter discovers inbound remote leg  interaction PREPARING
  -> private IVR/routing/waiting logic
  -> adapter selects and alerts agent leg
                                      interaction RINGING
  -> AGENT and REMOTE_PARTY MEDIA_READY
  -> adapter proves two-party media relation
                                      interaction CONNECTED
  -> either required leg ends         interaction TERMINATED
```

核心不拥有呼入应答脚本、IVR、队列、工作时间、分机策略或设备选择。私有适配器可以先把对端放入 holding/queue，但 holding bridge、IVR media 和排队机器人都不是 `AGENT` leg，也不能形成 `CONNECTED`。只有最终被选中的一个 agent endpoint 被归一为 `AGENT` leg。

## 5. `CONNECTED` 判定

adapter 只能在同时满足以下条件时提交 `PARTICIPANTS_CONNECTED`：

1. interaction 已绑定同一 connector 下稳定的 `externalInteractionRef`；
2. 存在且只存在一个 `AGENT` leg 和一个不同的 `REMOTE_PARTY` leg；
3. 两个 leg 都已经达到 `MEDIA_READY`，且未在连接证明发生前终止；
4. adapter 已从 native bridge membership、session/leg relationship、媒体连接事件或语义等价的权威查询证明这两条腿彼此处于双向两方媒体关系；
5. 该关系匹配 interaction 固定 topology，不是 holding bridge、early-media path、IVR、voicemail、conference 中的任意两个成员或另一次 interaction 的腿；
6. 事实携带两个规范 leg 引用；核心重新验证 tenant、connector、interaction、角色和唯一性，不接收 bridge ID 或任意 topology map；
7. 若事实在 `TERMINATED` 后晚到，adapter 还必须提供足以证明连接发生在终止前的时间/顺序依据，且既有终止分类允许“曾接通”。

以下事实单独或组合都不足以接通：

- early media、183/progress-media、ringback、播放音频或单向媒体；
- 只有一个 leg answered、`Up`、`in-progress` 或 `MEDIA_READY`；
- 两条腿都 `MEDIA_READY`，但没有两者关系证明；
- bridge 被创建但成员未知、只有一条目标腿、包含错误角色或只是 holding bridge；
- provider 对一条 Call 返回 `completed`。该状态可能只说明音频曾到达 IVR、语音信箱或其他 endpoint；
- 创建呼叫 API 成功、挂断命令成功、录音开始或出现可下载录音。

若 connector 不能稳定证明第 4 项，它可以继续接收/归档安全的呼入发现事实，但不能声明 B22 首期 outbound/inbound connected-observation capability，也不能把猜测状态映射为 `CONNECTED`。

## 6. 终止、结果与乱序

### 6.1 终止规则

首期没有替换腿。任一 required leg 在连接前不可恢复地终止，表示本次 topology 无法继续；任一 required leg 在连接后终止，表示唯一两方媒体关系结束。adapter 提交规范 leg/interaction terminal 事实，核心按同一聚合事务补充 `terminatedAt`，但用户挂断命令本身不直接产生终态。

终止信息拆为安全原因和可选失败方，避免枚举爆炸：

- reason：`NORMAL`、`BUSY`、`NO_ANSWER`、`REJECTED`、`CANCELED`、`NETWORK_FAILURE`、`PROVIDER_FAILURE`、`UNKNOWN`；
- failed party：`AGENT`、`REMOTE_PARTY`、`SYSTEM`、`UNKNOWN`，仅在 adapter 有可靠证据时填写；
- 是否曾接通由 `connectedAt` 派生，不另信任 adapter 提交的 connected boolean；
- `BUSY`、`NO_ANSWER`、`REJECTED` 只适用于未接通结果，不能与已证明的 `connectedAt` 并存；
- 不从 native SIP/Q.850 cause、HTTP 文本或哪条 channel 先销毁推断“谁主动挂断”。原始原因继续留在私有诊断边界。

补充只允许从 `UNKNOWN` 变为一个有证据的具体 reason/party，或重复同一分类；两个不同的具体分类互相冲突时不覆盖，转入 reconciliation。已接通后正常结束可以保持 `failedParty=UNKNOWN`。

### 6.2 乱序应用

规范 ingress 先完成结构、身份、scope、digest 和 inbox 去重，再应用状态投影。对结构合法但缺少前置事实的事件使用有界 deferred 语义：

- connected 先于两条 `MEDIA_READY` 到达时保存事实但不公开 `CONNECTED`；前置事实到达后在同一 interaction 锁内重新仲裁；
- remote alerting 先于 agent media-ready 投递到核心时可以等待前置事实；若最终 provider 时间证明对端实际上在坐席接听前已被呼叫，则违反 `OUTBOUND_AGENT_FIRST`，不得应用为合法状态；
- deferred 数量由固定 fact vocabulary 和两腿上限天然约束，不能演化成无界事件脚本；
- 永久缺失前置事实、第三条腿、角色变化、跨 interaction 腿复用或具体终止分类冲突都不能靠“最后写入获胜”处理，必须失败关闭并进入 reconciliation/incomplete 路径。

## 7. 坐席、外部 agent 与设备能力

### 7.1 三层身份

| 层次 | 所属边界 | 规则 |
| --- | --- | --- |
| CRM member | 开源核心 | 来自已验证 caller 或当前 tenant 活动成员关系；拥有业务 authority 和 interaction 可见性 |
| connector agent binding | 开源 control plane | connector-scoped opaque `externalAgentRef` 只绑定一个 tenant/member，用于把呼入/事件中的外部 agent 身份解析成当前成员 |
| endpoint/device | 私有适配器 | 云客户端、分机、PJSIP endpoint、PSTN 坐席号码、软电话注册、device token 和在线状态 |

`externalAgentRef` 不是 actor，也不是设备地址。绑定采用不可改绑语义：一个 external agent ref 一旦绑定某成员，只能退役，不能转绑给另一个成员；私有适配器在分机或账号被复用时生成新的 opaque binding generation。这样晚到事件不会把旧通话归给新员工。一个成员可以在不同 connector 下拥有独立 binding，但所有管理和查询都显式 tenant/connector scoped。

外呼不接收客户端提交的 external agent 或 device。核心从 caller 固定 member，tenant 配置选择 connector，私有 adapter 再从自己的目录选择 endpoint。呼入 adapter 只提交 externalAgentRef，核心在解析 connector tenant 后通过受治理目录映射并再次检查活动成员；未知、退役或失活 binding 保持 interaction 未分配。

### 7.2 Connector 静态能力

B22-S 只冻结状态相关的 typed capability，不允许任意字符串或 provider 配置 map：

| capability | 首期语义 |
| --- | --- |
| `AGENT_FIRST_OUTBOUND` | adapter 保证 agent `MEDIA_READY` 前不会呼叫 remote party |
| `INBOUND_AGENT_BRIDGE_OBSERVATION` | adapter 可以把呼入最终坐席与对端归一为两条规范腿 |
| `TWO_PARTY_MEDIA_PROOF` | adapter 能证明两条指定角色腿之间的双向两方媒体关系；推进 `CONNECTED` 必需 |
| `AGENT_ENDPOINT_CONFIGURATION_PROBE` | adapter 能判断指定当前成员是否有可用于 agent-first 的受治理 endpoint 配置 |
| `AGENT_PRESENCE_PROBE` | 可选；adapter 能给出短时、提示性的 available/busy/unavailable 观测 |
| `TERMINATE_INTERACTION` | 可选；adapter 能把结束 interaction 意图转换为私有 native leg 控制并等待终态事实 |

connector 的认证成功不等于这些能力 ready。readiness 还必须核对账号/应用权限、callback 或事件订阅、session registry、必要查询面和当前连接所有权。缺少 `AGENT_FIRST_OUTBOUND`、`TWO_PARTY_MEDIA_PROOF` 或 endpoint configuration probe 时，首期点击外呼失败关闭；缺少可选 presence probe 时仍可安全尝试 agent-first，因为 remote leg 不会在 agent 接听前被创建。

### 7.3 成员 endpoint capability

核心对指定活动 member 查询一个最小、安全的 capability projection：

| 字段 | 允许值与语义 |
| --- | --- |
| configuration | `CONFIGURED`、`NOT_CONFIGURED`、`DISABLED` |
| presence | `AVAILABLE`、`BUSY`、`UNAVAILABLE`、`UNKNOWN`；没有 `AGENT_PRESENCE_PROBE` 时固定为 `UNKNOWN` |
| checked/expiry | adapter 观测时间与严格有界的短有效期；过期结果只能用于 UI 提示，不能当命令授权 |
| safe reason | 固定分类，如 `NO_ENDPOINT`、`ENDPOINT_DISABLED`、`BUSY`、`OFFLINE`、`PROBE_UNAVAILABLE`；不返回 provider 错误文本 |

`CONFIGURED + UNKNOWN` 是合法组合：它表示 adapter 知道如何呼叫坐席，但无法权威证明设备此刻在线。首期仍可发起 agent leg；若坐席未接听，客户腿永远不会被创建。`AVAILABLE` 只是短时提示，不能保证下一毫秒 dispatch 成功；实际起呼仍必须重新检查 connector readiness，并接受确定的 agent-side terminal 结果。

任何 capability 都不授予 CRM authority，也不扩大电话号码字段、业务记录或 interaction 可见性。反向亦然：拥有呼叫 authority 不会创建 endpoint 配置。普通响应不得包含 device ID、分机、坐席号码、SIP URI、registration contact、IP、user agent、codec、token、厂商账号或诊断文本。

### 7.4 并发和设备选择

核心首期不建立全 tenant 的实时 agent presence 大盘，也不把一个陈旧的 non-terminal interaction 当作设备忙的绝对事实。设备是否还能接受新呼叫由私有 adapter 的 endpoint/session 状态最终仲裁；核心可以用请求幂等和已知活动 interaction 改善重复点击，但不能据此承诺全局单通话。

如果一个成员有多个可用设备，私有 adapter 必须按受治理配置确定性选择一个；首期普通调用者不能选设备，也不能同时振铃多个设备。若适配器无法确定唯一首期 endpoint，configuration 必须失败关闭，而不是把设备列表暴露给核心。

## 8. 规范事实最小集合

B22-S 要求 lifecycle ingress 使用固定 typed facts：

- `LEG_DISCOVERED(role, externalLegRef)`；
- `LEG_ALERTING(externalLegRef)`；
- `LEG_MEDIA_READY(externalLegRef)`；
- `PARTICIPANTS_CONNECTED(agentLegRef, remotePartyLegRef)`；
- `LEG_TERMINATED(externalLegRef, safeReason)`；核心只在有可靠的未接通失败证据时按已绑定 leg role 补充 failed party；
- `INTERACTION_TERMINATED(safeReason, optionalFailedParty)`，仅用于 provider 确实提供 session 级终态时；
- `OBSERVATION_GAP` 与 typed reconciliation 结果。

agent 解析使用独立、可选、connector-scoped `externalAgentRef` 字段，只允许出现在 `AGENT` leg 的发现/绑定语义中。事实 body 不接受 actor、member、tenant、connector、device、bridge、任意 native state、headers、variables 或 `Map<String, Object>`。核心生成 received time 和 digest，并重新查验所有本地/外部引用 scope。

最终实现可以把这些事实拆成 sealed types 或等价的严格输入对象，但不能退化成一个 `factKind + Map` 的通用事件总线。

## 9. 契约测试门槛

实现前的云 Webhook、PBX 事件流和 dev/test 模拟 adapter 必须共享以下 fixtures：

1. interaction 与一个 provider Call/channel 不相等；native 额外 channel 留在 adapter，核心始终最多两个规范腿；
2. 同 external leg 不能跨 interaction/connector 重放，角色不能改变，第三条规范腿失败关闭；
3. 外呼坐席未配置、拒接、超时、离线或 dispatch agent-side 失败时，remote leg 从未创建，interaction 未接通终止；
4. early media、单腿 media-ready、两腿 media-ready 但无关系、只有 bridge 创建、holding bridge、错误 bridge 成员和 unrelated leg 都不产生 `CONNECTED`；
5. 正确的 agent/remote 两腿 media-ready 加两方媒体证明只写一次 `connectedAt`；重复和乱序不改变原值；
6. connected 先到时有界 deferred，前置事实到达后应用；若 chronology 证明 remote 在 agent ready 前被呼叫，则 connector 违反 topology；
7. 终态先到后只允许补充发生在终止前且与分类兼容的 connected；`NO_ANSWER/BUSY/REJECTED` 与 connected 冲突进入 reconciliation；
8. 任一 required leg 在 connected 前后终止都结束首期 interaction；挂断命令响应本身不产生终态；
9. observation gap 不伪造终态，普通晚到事实不自动清除 `OBSERVATION_INCOMPLETE`；只有 capability 覆盖范围内的显式对账可进入 `RECONCILED`；
10. 呼入 external agent binding 未知、退役、跨 tenant 或成员失活时保持未分配；正确 binding 只绑定当前活动同 tenant member，后续目录变化不改写历史；
11. member capability 的 `CONFIGURED + UNKNOWN` 可以安全尝试 agent leg，但 adapter 必须证明 remote 在 agent ready 前未被联系；过期 `AVAILABLE` 不被当作成功保证；
12. 所有普通 API、日志、错误和 core facts 均不出现 device/extension/token、完整号码、bridge ID、native cause、provider 错误或原始 callback。

这些测试必须同时覆盖 callback-before-command-response、重复 delivery、不同 delivery 的等价事实、provider 时间乱序和 adapter 重启后稳定的 external interaction/leg correlation，并满足 B22-R 已冻结的 durable session registry、journal、gap 与 reconciliation 不变量。[B22-CP](../B22-CP-pbx-composed-contract.md) 已把其中 PBX early media、两种单腿 answered、错误 bridge、exact two-party proof、dirty reconnect 和稳定 correlation 放入同一逐步 oracle；云 callback 与 PBX 录音组合分别由 B22-CW 和 [B22-PM](../B22-PM-pbx-media-contract.md) 覆盖。

## 10. 本设计不重复定义的边界

- 最终 HTTP 路径、Java 包名、PO/表名、Flyway 版本、错误码和 public Response 形状；
- connector/agent binding 的管理 API 与最终呼叫 authority code；
- 用户请求幂等、outbox worker、adapter command ledger、重试租约、`AMBIGUOUS`、journal、CDR/CEL 对账、事件 replay、连接 fencing 和容量；这些由 B22-R 定义，B22-S 只组合使用；
- 电话号码 E.164 快照、呼入地址加密/HMAC/脱敏、录音引用、删除和保留；这些由 B22-D 定义，B22-S 只保存正交 recording availability；
- 录音媒体传输由 [B22-M](B22-media-transfer.md) 固定为逐请求同源代理流、WAV/MP3 和可选 single byte-range；转写仍不属于首期；
- provider-specific 认证、route/stream 隔离、按操作 readiness、配置漂移和真实 conformance 证据；这些由 [B22-A](../B22-A-adapter-readiness.md) 定义，B22-S 只消费状态/topology capability；
- transfer、hold、consult、conference、multiple devices、browser/WebRTC 或 ACD/IVR 产品能力。

已经由 B22-R/B22-D 冻结的边界必须原样组合，媒体读取还必须组合 B22-M，真实 readiness/隔离还必须组合 B22-A；[B22-CW](../B22-CW-cloud-callback-fixture.md) 固定云侧统一 checkpoint，[B22-CP](../B22-CP-pbx-composed-contract.md) 冻结 PBX 核心组合，[B22-PC](../B22-PC-pbx-command-contract.md) 冻结 PBX command 组合，[B22-PD](../B22-PD-pbx-sensitive-data-contract.md) 冻结 PBX 敏感数据/确认销毁组合，[B22-PM](../B22-PM-pbx-media-contract.md) 冻结 PBX stored-recording/media 组合。后续真实 provider fixture 不能通过放宽两腿模型、接通证明、设备隐私、媒体代理或 connector 隔离边界来规避。

## 11. 外部事实依据

- [Asterisk ARI Channel State](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/Introduction-to-ARI-and-Channels/ARI-and-Channels-Manipulating-Channel-State/)：channel `Up` 表示 Asterisk 与该设备之间可双向传输媒体，只是单腿事实。
- [Asterisk ARI Bridge Operations](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/Introduction-to-ARI-and-Bridges/ARI-and-Bridges-Bridge-Operations/)：answered channel 仍需与另一 channel 一起加入 mixing bridge 才建立两方音频通话，holding bridge 与 mixing bridge 语义不同。
- [Twilio Call Resource](https://www.twilio.com/docs/voice/api/call-resource)：每条 Call 有独立 `ringing`/`in-progress`/terminal 状态和异步 callback；`completed` 可能表示音频连接到人、IVR 或语音信箱。
- [FreeSWITCH Events Catalog](https://developer.signalwire.com/freeswitch/programming/events-catalog/)：`CHANNEL_ANSWER`、`CHANNEL_BRIDGE` 和 `CHANNEL_HANGUP_COMPLETE` 是不同事件，progress media 也与普通 ringing 分开。
- [FreeSWITCH Core Concepts](https://developer.signalwire.com/freeswitch/foundations/introduction/)：channel 是平台与单个 endpoint 的一条通信腿，典型两方呼叫由两个 channel 经 bridge 连接。
