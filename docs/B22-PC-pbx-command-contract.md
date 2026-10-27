# B22-PC PBX command 组合契约 fixture

> 决策日期：2026-07-19
>
> 状态：PBX command 跨核心 outbox、adapter ledger、provider start 与 reconciliation 的实现前组合契约已闭合；本文件冻结确定性 fixture、crash matrix、oracle 和失败关闭条件，不实现 adapter、核心 runtime、API、表、配置、Docker PBX 或真实电话调用
>
> 输入：[B22 总规划](../batch-B22.md)、[B22-R 可靠性设计](B22-R-reliability.md)、[B22-A readiness 与隔离设计](B22-A-adapter-readiness.md)、[B22-S 状态与拓扑设计](spikes/B22-state-and-topology.md)、[B22-CP PBX 核心组合契约](B22-CP-pbx-composed-contract.md)和 [PBX 事件流 spike](spikes/B22-pbx-event-stream.md)

## 1. 决策摘要

B22-PC 用一组双 connector、可崩溃、可重启的 durable trace 证明 PBX 起呼命令的局部可靠性能够安全组合，而不把任何局部幂等误报为端到端 exactly-once 拨号：

- 人工请求绑定、interaction 和唯一 start outbox 要么一起提交，要么一起回滚；核心 transport 不确定时只重投同一 `commandId` 和 digest；
- adapter 在返回 durable receipt 前先保存 `(connectorRef, commandId)` ledger。同 ID/同 digest 复用首次记录，同 ID/不同 digest 在 provider I/O 前永久失败关闭；
- `RECEIVED -> DISPATCHING` 的 durable commit 是 provider 调用可能发生的保守分界。越过分界后，进程崩溃、超时、断连或响应丢失都不能把命令退回 `RECEIVED` 或生成第二个 provider start；
- `AT_MOST_ONCE_START` 对每个命令最多进入一次可能触达 provider 的调用；`IDEMPOTENT_START(window)` 只允许在声明窗口内以完全相同的 provider key 和 digest 重投，并由 provider fixture 证明只创建一个逻辑对象；
- provider event 早于 command response 时，可以通过已验证 stream、不可改绑 ownership/session correlation 和 durable journal 确认同一命令；晚到 response 只补证据，不再次 start；
- `AMBIGUOUS` 在核心 settle 原 start outbox 并原子取得 reconciliation case。非权威 `NOT_FOUND`、current snapshot、健康恢复和人工刷新都不能把它变成 `REJECTED` 或重新拨号；
- 只有 case 创建时已声明的 `AUTHORITATIVE_COMMAND_ABSENCE`，在最终一致窗口结束后覆盖原 provider authority、correlation namespace 和完整查询范围，才可证明 provider 未接受；
- command、ledger、provider key、query ledger、result delivery、capacity 和观测入口都按 connector 分区。同名 command 或伪造 body 不能在两个 connector 之间查找、settle 或调用 provider。

本 fixture 冻结未来开源 durable 模拟 adapter 与私有 PBX adapter 共同执行的 conformance contract。纯内存 mock、只统计 Java 方法调用、把固定 channel UUID 当成永久幂等保证、让 adapter 自己提供 expected state，或只检查最终 dispatch 状态，都不满足 B22-PC。

B22-PC 只闭合 PBX start command 的 crash/`AMBIGUOUS` 组合面。B22-D 敏感值加密/销毁组合后来由 [B22-PD](B22-PD-pbx-sensitive-data-contract.md) 独立闭合，B22-M stored-recording 传输/取消组合后来由 [B22-PM](B22-PM-pbx-media-contract.md) 独立闭合；真实 Asterisk/FreeSWITCH/SIP trunk canary 仍是独立组合门槛，因此 B22 仍不拆实现子批次。

## 2. Fixture 边界与独立 oracle

### 2.1 固定双 connector 部署

测试部署使用同一个 adapter 进程、两个 tenant 和两个逻辑 connector。相同 token 在不同 connector 分区内合法，专门用于暴露错误的全局查找：

| Fixture identity | Connector A | Connector B |
| --- | --- | --- |
| tenant | `tenant-a` | `tenant-b` |
| connector | `connector-a` | `connector-b` |
| command | `command-23` | 同名 `command-23` |
| interaction | `interaction-a` | `interaction-b` |
| provider key namespace | A 的私有 namespace | B 的独立私有 namespace |
| governed observation stream | `stream-a` | `stream-b` |
| initial binding revision | `revision-a-7` | `revision-b-9` |

每轮只把 A 作为被注入崩溃的主体，同时在每个 checkpoint 对 B 的 ledger、provider effect、session、result delivery、query、capacity 和 readiness 做负向断言。然后交换 A/B 重跑，避免 fixture 只证明一个硬编码主 connector。

外呼目标使用合成的 sealed canary。driver 只能在唯一的 provider invocation 边界消费它；序列化、普通 `toString`、异常拼接、snapshot、trace、metric label 或报告读取都使场景失败。这个 canary 只验证 B22-PC 没有新建明文旁路，不替代 B22-D 的真实 AEAD、key rotation、保留和确认销毁矩阵。

### 2.2 三层可观察状态

独立 oracle 以只读方式观察提交后的有界投影：

1. 核心 request binding、interaction、start outbox、worker lease/fence、dispatch projection、fact/result inbox 和 reconciliation case；
2. adapter command ledger、result delivery、ingress journal、session/native ownership、query ledger、binding revision、readiness gate 和 connector 分区容量；
3. durable provider simulator 的 invocation attempt、idempotency key/digest、明确接受/拒绝、逻辑对象和可查询历史 coverage。

provider simulator 的 effect store 不与 adapter ledger 共库，也不接受 adapter 回写 expected outcome。oracle 必须能区分“adapter 调用了 start”“provider 创建了逻辑对象”和“核心已经确认 dispatch”三个事实。真实 adapter conformance 则由隔离测试 PBX 的控制面/历史投影提供同等独立证据。

测试报告不得读取 sealed payload、native dial string、channel variable、endpoint、provider object ID 或原始错误。稳定 opaque identity 可以比较相等性和计数，但报告只输出安全 digest。

### 2.3 Driver 动作词汇

共享 harness 至少提供以下语义动作：

- `commitStartIntent(connector, request, command)`：在核心事务中提交 request binding、interaction 和一个 start outbox；
- `claimOutbox(command, worker)`：提交有界 lease 和递增 fence，不在持锁事务内调用 adapter；
- `deliverCommand(connector, command, digest)`：通过 connector-authenticated transport 发送 typed command；
- `commitDispatching(connector, command)`：在本地 readiness 复核成功后 durable 提交 provider 可能调用位；
- `invokeProvider(connector, providerKey, digest)`：消费 sealed target 并进入受 fixture 监控的唯一 start 边界；
- `emitProviderEvent(stream, nativeObject)`：让已治理 stream 和 ownership/session registry 处理 response 之前到达的对象事实；
- `commitCommandOutcome(command, outcome)`：原子提交 ledger 终态与稳定 core-result delivery；
- `deliverOutcome(deliveryRef)`：经核心 connector ingress/inbox settle outbox 与 dispatch；
- `reconcile(reconciliationId, capabilitySnapshot)`：执行只读、有界、可重投 provider 查询；
- `crashCore()`、`restartCore()`、`crashAdapter()`、`restartAdapter()`：真正丢弃进程内对象，只从 durable store 恢复；
- `advanceClock(duration)`：推进 lease、idempotency 和最终一致窗口，不使用 `sleep`。

fixture 对 reconciliation 期间的任何 start、originate、create channel、hangup、bridge、media 或 recording 控制调用立即失败。provider effect 不能由测试结束时补写或从 adapter ledger 推导。

## 3. 初始不变量和 capability profiles

产生第一条命令前必须证明：

- A/B registration 都是活动且不可改绑的独立 connector，普通 command/result/fact body 不含 tenant、actor、authority、connector selector 或 provider account；
- 核心 `START_OUTBOUND` 快照、worker claim 和 adapter 本地 pre-dispatch gate 都绑定当前 build/contract/binding revision，并且 A 的 readiness 不依赖 B；
- command ledger、result delivery、journal/session、query ledger 和 provider effect store 都能 durable commit；未解决记录不受 TTL/LRU 淘汰，control-plane reserve 可用；
- required PBX observation stream 已按 B22-CP 取得有效 owner/fence，session/ownership 冲突在 typed fact 构造前失败关闭；
- 核心没有既有 request binding、interaction、outbox、inbox 或 case，provider simulator 没有逻辑对象或 invocation；
- 本轮 profile 恰好声明以下一种 start guarantee，且该声明与相同 adapter build/profile 的 conformance evidence 一致。

| Profile | 允许的 provider 重投 | 必须证明 |
| --- | --- | --- |
| `AT_MOST_ONCE_START` | 一次可能触达 provider 的调用；调用进入后永不再次 start | 每个 command 的 invocation count `<= 1`，未知结果进入 `AMBIGUOUS` |
| `IDEMPOTENT_START(window)` | 仅在窗口内使用完全相同的 provider key 和 digest；窗口外为零次 | attempt 可以重投，但 provider logical object count `<= 1`，key/digest/namespace 不漂移 |

可按 correlation 查询活动对象、查询 CDR/CEL/历史和 `AUTHORITATIVE_COMMAND_ABSENCE` 是相互独立的 reconciliation capability，不从指定 channel UUID、Job UUID、普通 `NOT_FOUND` 或 start guarantee 自动推出。

## 4. `PC-01` 核心重投与 durable receipt

`PC-01` 先隔离 core→adapter 不确定，证明它不会被误判为 provider 不确定：

| Step | 动作 | 必须提交和断言 |
| --- | --- | --- |
| 1 | A 的首次人工请求 | request binding、`interaction-a` 和唯一 `command-23` outbox 同事务提交；任一写入 cut 使三者全回滚 |
| 2 | worker `w1` claim 后投递；driver 暂停 adapter dispatch worker | adapter 先提交 A 分区 `RECEIVED` ledger 和首次 digest，再返回 durable receipt；provider invocation count 仍为 `0` |
| 3 | durable receipt/transport response 丢失，`w1` lease 到期 | 核心 outbox 回到可领取，但 `commandId`、digest、interaction、sealed payload identity 和 connector 都不变；不创建 replacement command |
| 4 | worker `w2` 重投相同 command/digest | adapter 返回首次 durable receipt；ledger row count 仍为 `1`，provider invocation count 仍为 `0` |
| 5 | 旧 `w1` 尝试用 stale fence 完成本地 outbox | 不能覆盖 `w2` lease 或已提交结果；经认证 connector 后续送达的 provider 事实仍走 inbox 仲裁，不能被 worker fence 丢弃 |
| 6 | 发送同 connector、同 command、不同 digest | 永久 command conflict；保留首次 ledger/payload，provider invocation count 不变，错误不包含差异内容 |

B 分区先独立接受自己的同名 `command-23` 和首次 digest；A 的 command body、provider key、ledger receipt 和结果均不能查询或修改它。fixture 再把 A command 放到 B connector credential 下重投：受认证 transport 已选定 B，adapter 只能与 `(connector-b, command-23)` 的首次 digest 比较并稳定 conflict，不能借 body 中的 interaction 或 provider correlation 命中 A。

## 5. `PC-02` `AT_MOST_ONCE_START` crash matrix

同一 A 命令分别从空 fixture 执行以下 cut；每个 cut 后重启 adapter，并让核心 transport 任意重投同一 command：

| Cut point | 恢复语义 | Provider oracle |
| --- | --- | --- |
| `RECEIVED` commit 前 | adapter 没有 durable receipt，核心可重投并首次建立 ledger | invocation=`0` |
| `RECEIVED` commit 后、receipt 前 | 重投复用 ledger；恢复后可重新执行 readiness gate | invocation=`0` |
| readiness 通过后、`DISPATCHING` commit 前 | 旧检查不算调用位；恢复时重新检查当前 readiness，再原子提交 `DISPATCHING` | invocation=`0` |
| `DISPATCHING` commit 后、provider I/O 前 | 保守收敛 `AMBIGUOUS`，不能退回 `RECEIVED`；允许出现“实际未调用但结果未知” | invocation=`0` |
| provider I/O 进入后、明确 response 前 | 收敛 `AMBIGUOUS`；核心/adapter 任意重启都不能再次 start | invocation=`1`，logical object=`0` 或 `1` |
| provider 明确接受后、ledger 终态前 | 不再次 start；等待已验证 event 或只读 reconciliation 恢复 `CONFIRMED` | invocation=`1`，logical object=`1` |
| provider 明确拒绝后、ledger 终态前 | 若拒绝未 durable，仍保守 `AMBIGUOUS`；只有稳定拒绝证据或权威不存在可转 `REJECTED` | invocation=`1`，logical object=`0` |
| ledger outcome/result delivery commit 后、core ack 前 | 以相同 delivery/digest 重投；核心 dispatch 与 outbox 只 settle 一次 | invocation 不变 |

`DISPATCHING` 之后即使 adapter 能从命令模板预先指定 native channel UUID，也不能据此再次 originate。一个 UUID 暂时未出现在 active snapshot 中不是权威不存在；provider 可能尚未可见、已经快速终止，或 adapter 在调用前崩溃。fixture 接受保守的 false ambiguity，不接受 duplicate dialing。

正常返回也要分别运行：明确接受提交 `CONFIRMED`，明确拒绝提交 `REJECTED`，超时/断连提交 `AMBIGUOUS`。三种 ledger 终态都必须与稳定 result delivery 在同一 adapter 事务中提交；不能先改 ledger，再依赖一次内存 response 通知核心。

## 6. `PC-03` `IDEMPOTENT_START(window)` crash matrix

本 profile 复用 `PC-02` 全部 cut，但只在 fixture 声明的有效窗口内允许恢复过程再次调用 provider：

1. provider key 由 adapter 在 connector 私有 namespace 内稳定绑定首次 command/digest；进程重启、core worker/lease、stream generation、binding probe sequence 和 attempt number 都不参与 key；
2. 每次重投的 typed operation、provider key、规范 digest 和 sealed target identity 完全相同。fixture 在 adapter 尝试换 key、改 payload、增加任意 variable/header 或跨 connector 复用 namespace 时，在 provider effect 前失败；
3. provider simulator 对相同 key/digest 返回同一逻辑对象或同一稳定拒绝，不创建第二个 channel/session。attempt count 可以大于 `1`，logical object count 必须小于等于 `1`；
4. 重投前同时检查当前时间仍在首次声明窗口内、当前 build/profile 仍证明同一 idempotency namespace，以及 required observation path 仍 ready。任一条件不满足时不调用 provider 并收敛 `AMBIGUOUS`；
5. 窗口不能因 adapter/core 重启、readiness 刷新、binding revision 增加或 retry 延后而重新起算。窗口到期后的第一次恢复也不得“再试一次确认”；
6. adapter 收到 provider 对象或稳定 response 后按首次 identity 提交 outcome/result delivery；晚到的重复响应是 no-op，相反响应进入 reliability conflict。

`IDEMPOTENT_START(window)` 只证明同一 provider namespace 的有界去重，不证明 callback 完整、事件有序、session correlation 唯一或历史查询权威。这些仍分别由 B22-CP 和本文件的 event/reconciliation 场景证明。

## 7. `PC-04` event-before-command-response

`PC-04` 固定 PBX event 在 originate/`bgapi` response 前到达的组合顺序：

1. A ledger 已 durable `DISPATCHING`，provider 接受并创建一个对象，但 command response 被延迟；
2. `stream-a` 先选定 A，校验 native ownership，并以 command correlation 在 A session registry 中原子绑定同一 `command-23`、`external-interaction-a` 和 native object；body 中任何 connector/tenant/command 覆盖值都被拒绝；
3. 最小 object-discovered fact 先写 durable journal。adapter 重放该事实时，以已提交 correlation 把 A ledger 收敛 `CONFIRMED`，并原子创建稳定 command-result delivery；
4. 核心 connector ingress/inbox 中 provider object fact 或 command result 先到达的一方，都在同一事务中把 dispatch 投影收敛 `CONFIRMED` 并 settle 原 outbox。另一方只能 `NO_CHANGE`/`DUPLICATE`；
5. adapter/core 在 journal、session binding、ledger outcome、result delivery、core inbox commit 的任一相邻点崩溃，恢复后仍使用同一 command、external interaction、delivery 和 provider object；
6. 晚到的 provider accept response 是 no-op；晚到明确 reject 形成 reliability conflict，不能把已经观察到的对象改成未拨出，也不能创建 replacement start。

object discovery 只确认 dispatch。它不自动产生 `RINGING`、`CONNECTED`、`TERMINATED` 或第三条规范腿；两腿与媒体拓扑继续完整执行 B22-CP oracle。若 session/ownership 无法唯一关联，事实进入安全 quarantine，ledger 保守保持 `AMBIGUOUS`/`DISPATCHING` 恢复路径，不能靠同名 native ID 猜测命令。

## 8. `PC-05` `AMBIGUOUS` reconciliation

### 8.1 进入 case 的原子边界

adapter 提交 `AMBIGUOUS` ledger 时同时创建稳定 result delivery。核心处理该 delivery 的事务必须一起完成：

- 保存 inbox outcome；
- 把 dispatch 从 `PENDING` 收敛为 `AMBIGUOUS`；
- settle 原 start outbox，使任何 lease 到期都不再投递；
- 按 command/reason/evidence window 创建或取得唯一 reconciliation case；
- 保存 case 创建时的 provider authority、binding/build evidence digest、query capabilities、最终一致窗口和覆盖要求。

任一写失败则整个核心事务回滚，adapter 用同一 delivery 重投。不能先关闭 outbox 后异步遗漏 case，也不能先创建 case 而让 start worker 继续领取。

### 8.2 只读证据矩阵

| Query/evidence | Dispatch/case 结果 | 禁止推论 |
| --- | --- | --- |
| 最终一致窗口内 `NOT_YET_VISIBLE` 或空结果 | 保持 `AMBIGUOUS`，按同一 query ledger/cursor 退避 | 不能当拒绝或重新 start |
| active-resource `NOT_FOUND` / `CURRENT_STATE_ONLY` | 保持 `AMBIGUOUS`；case 可继续或最终 `UNRECOVERABLE` | 不能证明快速结束的对象从未存在 |
| 历史/CDR/CEL 覆盖不完整、查询超时、限流或 `UNSUPPORTED` | 保持 `AMBIGUOUS`；按证据关闭 `UNRECOVERABLE` | 运维关闭告警不改写 dispatch |
| 唯一且已验证的 correlated provider object/fact | 收敛 `CONFIRMED`，复用稳定 session/ownership；所有事实继续走 journal/inbox | 不补造 ringing/connected/terminated |
| 多个对象声称同一 command correlation | 至少阻止任何重拨并升级 reliability conflict；对象存在证据可确认 dispatch，但 session/lifecycle 在唯一 ownership 前失败关闭 | 不能任选一个对象或合并 native 会话 |
| `AUTHORITATIVE_COMMAND_ABSENCE` | 仅满足下节全部门槛后收敛 `REJECTED` 并 `RESOLVED` | 不能自动复活原 request/outbox |

### 8.3 权威不存在门槛

`AUTHORITATIVE_COMMAND_ABSENCE` 必须同时证明：

1. case 创建时的 capability snapshot 已明确声明该能力、最终一致窗口、历史/命令索引范围和 provider authority；后来的更强 readiness 不能追溯升级旧 case；
2. 查询在窗口结束后执行，并覆盖原命令可能到达的同一 authority、binding/correlation namespace、所有活动与终态分区；配置迁移后的新账号或新 PBX node 不能证明旧 authority 不存在；
3. adapter 使用独立 durable `(connectorRef, reconciliationId, digest)` query ledger，稳定 cursor/page/delivery identity 可跨重启恢复；
4. completion marker 只在全部 page 已被核心 `APPLIED`、`NO_CHANGE` 或 `DUPLICATE` 后提交；普通第一页为空不是 coverage completion；
5. 查询期间没有 command correlation conflict、未覆盖 observation gap、provider clock/window 越界或相反对象事实；
6. provider/query spy 证明全过程只读，start/control invocation count 为 `0`。

满足门槛后的 `REJECTED` 只表示旧 command 被证明未接受。再次拨号仍需当前人工授权、新 `requestId`、新 interaction 和新 `commandId`；fixture 必须断言旧 request binding、command ledger 和 provider key 从未复用。若随后出现相反对象事实，保留双方证据并进入 reliability conflict，不自动 last-write-wins 或补偿拨号。

## 9. `PC-06` readiness 漂移与跨 connector 隔离

### 9.1 三层 start gate

对同一命令分别在三个 gate 后制造 snapshot expiry、stream owner 丢失、control permission 撤销、capacity hard-limit 和 binding revision drift：

- interaction/outbox 创建前失败：核心不提交任何呼叫意图；
- interaction/outbox 已提交但 worker claim 前失败：outbox 保持 `READY`，不调用 adapter；
- adapter ledger 已 `RECEIVED` 但尚未 `DISPATCHING`：命令保持 `RECEIVED`，不调用 provider；恢复时重新检查当前证据，不能沿用旧正向 snapshot；
- ledger 已 `DISPATCHING`：按本轮 start guarantee 恢复为确定 outcome 或 `AMBIGUOUS`，不能因新 revision、owner 或配置重新 start；
- observation path 漂移还按 B22-A/B22-CP 从最后证明正常点打开独立 gap。新 readiness、command query 或 command outcome 不能关闭该 gap。

fixture 不用超时把长期 `RECEIVED` 猜成 provider `REJECTED`，也不把 backlog 改绑到新 connector。停用/退役后，结果重投和既有 case 仍使用原 connector identity；无法继续的 case 按真实证据保持 unresolved/`UNRECOVERABLE`。

### 9.2 双 connector 攻击矩阵

以下动作在每个 crash cut point 后都必须重复：

1. 用 B credential 发送 A command/result/reconciliation body，并伪造 A tenant、interaction、stream、revision 或 provider key；
2. 让 A provider object/event 从 `stream-b` 到达，或尝试把 A native ownership/session 改绑 B；
3. 让 A ledger/capacity 达到 high-water，同时在 B 创建同名 command 并正常完成；
4. 用 A query ledger/cursor 查询 B provider namespace，或把 B 的空查询作为 A 的权威不存在证据；
5. 在 A/B 使用相同 command token 但不同 digest，验证唯一性始终是 connector-scoped，而不是错误的全局首次命中。

所有攻击在 provider start 或 typed core fact 构造前失败，或进入已选 connector 的有界安全 quarantine。A/B 的 ledger、provider effect、result delivery、session、query、case、gap、capacity 和 readiness 均不能交叉修改；安全输出只含 connector、fixture step、固定 reason 和 evidence digest。

## 10. 统一 crash matrix 与执行层

每组场景至少在以下持久化边界前后注入进程终止；恢复不允许保留对象引用、线程、future、SDK response 或内存 dedup cache：

| Boundary | 恢复断言 |
| --- | --- |
| core request binding/interaction/outbox commit | 三者全有或全无，只有一个 command |
| core outbox claim commit | lease/fence 可恢复，identity 不变，旧 fence 不覆盖 |
| adapter ledger `RECEIVED` commit | durable receipt 之前不可声称已接收；重投复用首次 digest |
| adapter `DISPATCHING` commit | commit 前可安全首次调用；commit 后只能按声明 guarantee 恢复 |
| provider invocation enter/object commit/response return | 独立区分 attempt、logical object 和 adapter outcome |
| session/journal fact commit | event-before-response 使用稳定 ownership/correlation，不从内存补 ref |
| ledger outcome/result delivery commit | outcome 与可重投 delivery 原子；核心 ack 丢失不丢结果 |
| core inbox/outbox/case commit | dispatch、settle 和 case 符合单事务规则，无 gap-free redelivery 窗口 |
| reconciliation page/completion commit | cursor/page/delivery 稳定，所有 page ack 后才完成 coverage |

同一 `PC-01` 至 `PC-06` ID 最终必须运行在三层：

1. 纯规范模型，用穷举/属性化调度检查允许状态迁移和 identity；
2. 真实 MySQL 核心加 durable provider simulator/adapter，使用真实事务、唯一键、lease、进程重建和虚拟时钟；
3. 隔离测试环境中的每个真实 PBX adapter build/profile，使用相同 driver 语义和独立 PBX oracle。

模型通过不能替代 durable 测试，模拟器通过不能替代真实 provider conformance。真实环境可以因不支持 `AUTHORITATIVE_COMMAND_ABSENCE` 而执行对应 `UNSUPPORTED/UNRECOVERABLE` 分支，但不能伪造 capability 跳过负向断言。

## 11. Conformance 输出

机器可读报告使用 fixture version `B22-PC/1`，至少包含：

- adapter contract/build evidence digest、PBX profile、binding revision、isolation level 和精确 start guarantee/window；
- `PC-01` 至 `PC-06` 每个 profile/cut point 的 pass/fail 与固定安全分类；
- 每 connector 的 core outbox、adapter ledger/result delivery、provider attempt/logical object、inbox、session、case/query ledger 最终计数和 identity digest；
- `AT_MOST_ONCE_START` 每 command provider invocation `<= 1`；`IDEMPOTENT_START(window)` 的窗口外调用=`0`、provider logical object `<= 1`；
- replacement `commandId`、automatic redial、reconciliation control call、stale core-worker fence settle、conflicting-digest provider call 和 cross-connector mutation/delivery/query 均为 `0`；
- `AMBIGUOUS` 对应 outbox 已 settle、case 已原子创建；非权威 absence 保持不确定，权威不存在具备窗口与 coverage 证据；
- callback/event-before-response 只确认一次 dispatch，result delivery/inbox 重投不重复业务副作用；
- sealed target、dial string、native ID、provider key 原值、credential、raw frame/response、错误原文和任意敏感值扫描命中数为 `0`。

报告不能包含 provider endpoint/account/node/app、号码或脱敏号码、channel/Job UUID、query cursor、provider object ID、native cause、credential 或异常 message。build、profile、start guarantee、binding revision、provider idempotency namespace 或 query coverage 变化后必须重跑；旧报告不能自动迁移为新 revision 的证据。

## 12. 闭合与剩余门槛

B22-PC 闭合以下 PBX command 组合问题：

- 核心至少一次投递只会复用同一 command；adapter durable receipt 和 digest conflict 都发生在 provider I/O 前；
- `DISPATCHING` 是保守不可回退分界，两种 start guarantee 在全部崩溃窗口中都不会创建第二个 provider 逻辑呼叫；
- event-before-response 可通过 durable session/journal/ledger 收敛同一 command，dispatch confirmation 不伪造 lifecycle；
- `AMBIGUOUS` 原子停止 outbox 并进入只读 durable reconciliation；普通未找到、重连和 active snapshot 不触发补拨；
- 权威不存在必须绑定旧 capability/window/authority/full coverage，不能由配置迁移后的空查询伪造；
- readiness 漂移和同进程双 connector 攻击都不能改变 command identity、跨分区 settle 或借恢复路径调用 provider。

仍未由本 fixture 闭合的项目包括：

- B22-D 在 PBX command/journal/quarantine 中的加密、key rotation、policy deadline、backup restore sweep 和确认销毁组合；该项后来由 [B22-PD](B22-PD-pbx-sensitive-data-contract.md) 闭合设计 oracle，但真实环境证据仍待 B22-CANARY；
- B22-M 的 PBX stored-recording 晚到、路径隔离、stream cancellation、byte/time/concurrency 上限和确认删除组合；该项后来由 [B22-PM](B22-PM-pbx-media-contract.md) 独立闭合；
- 真实 Asterisk/FreeSWITCH/SIP trunk 版本、Docker/电话 canary、provider 权限和生产 adapter conformance；
- 最终 Java/HTTP/SQL 类型、表、迁移、错误码、配置与 fixture harness 实现。

因此 B22-PC 只把 B22 总门槛中的“PBX command crash/`AMBIGUOUS` 组合”标为设计闭合；后来 B22-PD 与 B22-PM 又分别闭合敏感数据/确认销毁和 stored-recording/media 组合，但这些设计都不表示 runtime 已实现、provider 已 exactly-once 或真实 adapter 已通过，也不触发 B22a/B22b 拆分。
