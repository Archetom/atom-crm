# B22-CW 云回调组合契约 fixture

> 决策日期：2026-07-19
>
> 状态：云 callback 侧跨 B22-R/D/S/M/A 的实现前组合契约已闭合；本文件冻结确定性 fixture、检查点与通过条件，不实现 adapter、核心运行时、API、表、配置或真实电话调用
>
> 输入：[B22 总规划](../batch-B22.md)、[B22-R 可靠性设计](B22-R-reliability.md)、[B22-D 敏感数据设计](../batch-B22.md#14-b22-d-敏感数据录音访问与保留设计闭合)、[B22-S 状态与拓扑设计](spikes/B22-state-and-topology.md)、[B22-M 媒体传输设计](spikes/B22-media-transfer.md)、[B22-A readiness/隔离设计](B22-A-adapter-readiness.md)和[云 Webhook spike](spikes/B22-cloud-webhook.md)

## 1. 决策摘要

B22-CW 固定一套由开源 durable 模拟 adapter 和每个真实云 adapter build 共同执行的组合 fixture。它不为某个厂商创建公共状态模型，而是以一条确定性的云外呼脚本同时证明以下接缝：

- 同一 callback 重投、不同 delivery 的语义重复和同 delivery 内容冲突分别收敛为 `DUPLICATE`、`NO_CHANGE` 和永久隔离，不重复推进状态或产生一次性副作用；
- callback 可以乱序到达，但只能补充有顺序证据且与终态兼容的历史里程碑；终态、具体失败分类和 `connectedAt` 不允许 last-write-wins；
- callback 早于 Create Call 响应时，adapter command ledger、session registry 和核心 outbox 可以由已验证的 provider 对象事实收敛为 `CONFIRMED`，晚到响应不再次起呼；
- 云 provider 的 parent/child Call 只在私有 adapter 中归并。核心始终只看到一个 `AGENT` 和一个 `REMOTE_PARTY` 规范腿；两腿分别 answered 仍不足以产生 `CONNECTED`；
- Call terminal 与 recording availability 正交。独立 recording callback 可以晚到，`RecordingUrl`、credential 和 provider metadata 永不进入核心；
- 人工媒体读取在短授权事务后经可取消流执行。客户端中断、截止或 removal 会关闭上游，首字节后不透明重试被禁止；
- callback/readiness 配置漂移先撤销新 start，再从最后证明正常的时点打开 observation gap。新快照不能抹去旧 gap、恢复旧 lease 或自动重拨不确定命令。

Twilio 只作为代表性的 native callback 词汇。fixture oracle 使用 B22 的 provider-neutral 事实和投影；其他云 adapter 可以使用不同原始对象、事件和签名协议，但必须产生相同规范检查点。通过本文件只表示组合契约已冻结，不表示任一真实 adapter、账号、route 或电话线路已经 ready。

## 2. Fixture 边界与双层输入

### 2.1 Native 层

私有 adapter test kit 负责构造厂商原始请求、Create Call 响应和媒体字节源。以 Twilio 模型为例，native 层可以使用 Call、parent/child leg、`SequenceNumber`、Status Callback 和 Recording Status Callback；这些名称只存在于 adapter fixture 与私有诊断中。

每个 native callback 都必须按以下顺序通过 spy：

```text
governed route selects connector
  -> verify exact external URL and original signed request
  -> corroborate account/resource ownership
  -> parse fixed allowlist
  -> resolve durable session/native ownership
  -> normalize one typed fact
  -> commit adapter journal
  -> return provider success
```

验签失败、错误 route 重放、native ownership 歧义、journal 未提交或 hard capacity 都不得进入规范层。callback body 中的 account、tenant、member、号码、parent ID 或自定义参数不能选择 connector 或核心 tenant。

### 2.2 Provider-neutral 层

核心 test kit 只接收 B22 已冻结的 typed facts、dispatch result、readiness snapshot、gap/reconciliation 结果和 typed media handle。它不接收 raw form/JSON、provider status、HTTP request、URL、header map、SDK object 或通用属性 map。

每个事实固定包含当前场景所需的最小相关性：

- connector 内稳定 `deliveryRef` 和可选 `sourceEventRef`；
- 已绑定外呼的本地 `interactionRef`/可选 `commandId`；
- adapter-scoped `externalInteractionRef`；
- leg 事实的一条 `externalLegRef`，或两方关系事实的两个规范 leg ref；
- 录音事实可选的稳定 `externalRecordingRef`，但绝不包含 URL、路径或 credential；
- typed fact、可选 provider occurred time、有限方向和安全终止分类。

核心从 `AuthenticatedConnector` 和活动 registration 派生 tenant/connector，生成数据库 `receivedAt` 和 digest，并在一个短事务中提交 inbox outcome 与投影。fixture body 不能提交 tenant、actor、authority、member、readiness、connection generation 或任意业务权限。

## 3. 确定性世界、时钟与检查点

### 3.1 固定对象

一轮 fixture 使用以下逻辑别名；它们不是最终 Java 名称或公共 ID：

| 别名 | 作用 |
| --- | --- |
| `C1` | 核心 start command；所有 transport 重投复用同一 command identity |
| `I1` | 核心 interaction；方向为 `OUTBOUND`，topology 为 `OUTBOUND_AGENT_FIRST` |
| `S1` | adapter durable external session/correlation |
| `LA` | 唯一 `AGENT` 规范腿 |
| `LR` | 唯一 `REMOTE_PARTY` 规范腿 |
| `NR-A`、`NR-R` | 私有 native agent/remote Call resource；不得进入核心输出 |
| `REC1` | interaction 的唯一公开 recording identity |
| `NR-REC` | 私有 native recording resource；核心只保存其受保护 opaque ref |
| `Q1` | readiness 漂移形成的 observation gap |

初始核心状态固定为：registration `ACTIVE`，当前 readiness revision `r40`/sequence `100` 未过期，`START_OUTBOUND`、`RECONCILE`、`RECORDING_READ` 和 `RECORDING_DELETE` 为 `READY`；成员 endpoint 为 `CONFIGURED + UNKNOWN`；interaction、request binding 与 `C1` outbox 已在同一事务提交；adapter ledger 尚未收到 `C1`。

每个场景从自己的已声明前置 checkpoint 独立运行；场景间不共享可变数据库或 adapter store，只复用上述逻辑别名和 oracle。

所有时间由虚拟时钟推进，不用 wall-clock sleep 判断顺序、TTL、deadline 或 retry。provider occurred time、adapter journal received time、核心数据库 received time 和 readiness checked/received time 是独立轴。跨 native leg 的 sequence 不可直接比较；只有 adapter 能证明的 session chronology、provider occurred time 或权威查询证据才能建立两腿先后关系。

### 3.2 每步快照

每个动作后 test kit 必须取得一个不可修改的 checkpoint，至少断言：

| 维度 | 检查内容 |
| --- | --- |
| command | core outbox state/lease、adapter ledger state、provider start invocation count、dispatch projection |
| delivery | adapter journal identity/state、core inbox outcome/digest、provider ack 是否发生在 journal commit 后 |
| session | `S1`、native resource ownership 与 `I1/LA/LR` 的稳定绑定；restart 后不改绑 |
| interaction | lifecycle、`ringingAt`/`connectedAt`/`terminatedAt`、终止分类、observation assessment、optimistic version |
| legs | 每个角色最多一腿、各腿 milestone、deferred fact 数量和两方关系 proof 状态 |
| recording | 四态、受保护 external ref identity、policy/deadline、removal state |
| media | access-attempt outcome、adapter open/read/cancel/close 次数、已发送字节和 active handle 数 |
| readiness | build/contract、revision/sequence、逐操作结果、expiry、gap/case 状态和 capability snapshot |
| effects | Activity 摘要或其他一次性 effect/outbox 的唯一行数和发布次数 |
| leakage | API、DTO/VO、持久化白名单、日志、trace、metric、audit、exception capture 与对象 `toString` 的禁止值扫描 |

禁止值扫描至少放入完整/脱敏号码、HMAC、ciphertext metadata、raw callback、signature、provider account、native Call/Recording ID、`RecordingUrl`、credential、provider header/body、异常原文和媒体 bytes。fixture 使用不可与真实资源混淆的合成值，并把每个 canary 同时放入 raw body、URL、header 与异常中，证明没有仅靠单一字段清洗漏过边界。

### 3.3 不允许测试替身替核心猜测

fixture driver 只能投递 native 输入、控制响应/崩溃点、推进虚拟时间、切换受治理配置和观察端口调用。它不能直接编辑 interaction、inbox、ledger、gap、readiness 或 recording 状态，也不能代替 adapter 生成 `PARTICIPANTS_CONNECTED`、权威 absence 或 deletion confirmation。

## 4. `CW-01` 重复与 delivery 冲突

基线事实选择 `LR` 的 `LEG_ALERTING`。第一次 callback 经 native 验签、session registry 和 journal 后以 `d-ring-1` 送入核心，预期为 `APPLIED`。随后依次执行：

1. provider 使用完全相同的原始 callback 重投；adapter 必须命中相同 journal identity 或稳定 `deliveryRef`，核心返回首次 `APPLIED` 的 duplicate 结果；
2. adapter 在 core ack 丢失后重启并重投同一 journal 项；仍使用 `d-ring-1`，不生成第二条 inbox；
3. provider 产生另一个可证明为同一规范 alerting 事实的 callback，adapter 使用不同 `d-ring-2`；核心允许新增 inbox，但 outcome 为 `NO_CHANGE`；
4. 用 `d-ring-1` 重放内容不同的 normalized fact；核心保留首次 digest/outcome，返回永久 delivery conflict，adapter quarantine 该输入并发出安全告警；
5. 把对原 route 合法签名的 callback 重放到另一个 connector route，或让相同 native resource 已属于另一 connector；adapter 在规范化/核心调用前拒绝或 quarantine。

最终断言：`ringingAt` 只写一次，interaction version 只因真实投影变化递增，一次性 effect/outbox 仍为一份，provider callback 的重投都在 journal durable 后应答；冲突输入没有覆盖任何首次事实，也没有把 raw body 或不同 digest 内容写入普通核心存储。

## 5. `CW-02` 乱序、兼容补充与冲突

### 5.1 兼容的终态先到

为避免把跨 leg sequence 当成全局时钟，本场景只比较 `LR` 同一 native Call 的受签名 occurred time/sequence。按产生顺序，remote 曾在 `t30` alerting，并在未接通时于 `t40` 以 `NO_ANSWER` 终止；delivery 刻意反序到达：

1. `LEG_TERMINATED(LR, NO_ANSWER, t40)` 先到，interaction 收敛为 `TERMINATED`，没有 `ringingAt` 或 `connectedAt`；
2. `LEG_ALERTING(LR, t30)` 后到，adapter 提供同 Call 内发生于终止前的顺序证据；核心只补 `ringingAt=t30`，当前 lifecycle 仍为 `TERMINATED`；
3. alerting 的相同 delivery 与语义重复再次到达，结果分别为 duplicate/no-change。

### 5.2 不兼容的晚到 answered/connected

随后投递声称 `LR` 在 `t35` media-ready 的事实，或者在终态后投递两方 connected proof：

- `NO_ANSWER` 与已接通事实不兼容，核心不能写 `connectedAt`、不能把 reason 改成 `NORMAL`，而是保留相反证据并进入 `RECONCILIATION_REQUIRED`；
- 若事实缺少可信 occurred time、跨 leg 顺序证据或完整两方 proof，只允许有界 deferred/对账，不能按核心 received order 猜测；
- 两个不同具体终止分类不能互相覆盖；只有 `UNKNOWN` 可以在证据充分时补充为一个具体分类；
- replay/query 只返回“当前已终止”时不能补造 `RINGING` 或 `CONNECTED`。最终一致窗口内的 `NOT_FOUND` 也不能解决 dispatch ambiguity。

最终断言：终态不倒退，兼容历史里程碑只补一次，冲突不会以最后到达者获胜，ordinary late callback 不会把 observation 清回 `NOMINAL`。

## 6. `CW-03` callback-before-response

场景从 adapter 已持久化 `C1=DISPATCHING` 开始。provider 接受 agent-side Call，但 Create Call HTTP response 被 test transport 暂停：

1. provider 先向受治理 route 发送 `NR-A` 的 initiated/answered callback；callback path 中只携带非认证的 command correlation，adapter 还必须通过当前 route、签名、account/resource ownership 和 `S1` registry 证明它属于 `C1/I1`；
2. adapter 在 journal commit 后应答 callback，并让 ledger 从 `DISPATCHING` 收敛为 `CONFIRMED`；该 ledger 结果与稳定 core-result delivery 在同一 adapter 事务建立；
3. 核心 fact inbox 在 outbox HTTP result 之前把 dispatch 投影设为 `CONFIRMED`，settle `C1` outbox，后续 lease expiry 不再投递 start；
4. 在 adapter 与核心各重启一次后恢复被暂停的 Create Call response；相同 external object/`CONFIRMED` 只产生 no-op；
5. 再注入晚到的 `REJECTED` transport result，必须形成 reliability conflict，不能把 dispatch 或已观察腿改为未拨号。

最终断言：provider start invocation count 为 `1`，`C1`、`S1`、`NR-A` 与 `I1/LA` 的绑定跨重启稳定，outbox 不再领取，callback 本身不伪造 `RINGING`/`CONNECTED`。若 correlation 或 ownership 不能唯一证明归属，事实必须 quarantine，ledger 保持真实不确定性而不是猜测确认。

## 7. `CW-04` 双腿状态与两方媒体证明

本场景使用正常 outbound agent-first 路径，并对每步断言 provider remote-leg create invocation count：

| 步骤 | 规范输入 | 预期投影 |
| --- | --- | --- |
| 1 | `LEG_DISCOVERED(AGENT, LA)` | interaction `PREPARING`；remote create count `0` |
| 2 | agent alerting/early media | 仍为 `PREPARING`；early media 不产生 `MEDIA_READY` 或 `CONNECTED` |
| 3 | `LEG_MEDIA_READY(LA)` | 仍为 `PREPARING`；从此 adapter 才允许创建 `NR-R` |
| 4 | `LEG_DISCOVERED(REMOTE_PARTY, LR)` | 两腿绑定完成；parent/child native relationship 只用于私有 correlation |
| 5 | `LEG_ALERTING(LR)` | interaction `RINGING` |
| 6 | `LEG_MEDIA_READY(LR)` | 两腿分别 ready，但仍是 `RINGING` |
| 7 | 无关系的 bridge、错误成员、holding/IVR 或第三 native leg | 不产生 `CONNECTED`；第三规范腿/角色变化永久拒绝 |
| 8 | `PARTICIPANTS_CONNECTED(LA, LR)`，带相同 session 中正确双向两方媒体 proof | 只写一次 `connectedAt`，interaction `CONNECTED` |
| 9 | 任一 required leg terminal | 只写一次 `terminatedAt`，interaction `TERMINATED`，是否曾接通由 `connectedAt` 保留 |

额外负向变体必须证明：agent 未配置、拒接、超时、离线或在 `MEDIA_READY` 前 terminal 时，`NR-R` 从未创建；只有一个 answered Call、两个 individually answered Call、provider `completed`、录音开始或 native parent/child ID 本身都不产生 `CONNECTED`；同一 external leg 跨 interaction/connector 复用和把 `LA` 改成 `REMOTE_PARTY` 都失败关闭。

若 `PARTICIPANTS_CONNECTED` 早于两条 `LEG_MEDIA_READY` 到达，它只能进入有界 deferred；前置事实齐备并验证 chronology 后才应用。若 chronology 证明 remote 在 agent ready 前已被联系，adapter build 违反 `AGENT_FIRST_OUTBOUND`，该运行进入 reconciliation，该 build 的 start conformance 失败。

## 8. `CW-05` 录音晚到与终态正交

在 `I1` 已 `TERMINATED` 后执行：

1. Call terminal callback 包含 native `RecordingUrl` canary，但没有独立 recording completion；核心 recording 仍为 `UNAVAILABLE` 或已知策略下的 `PROCESSING`，URL 被 adapter 丢弃；
2. 独立 recording `in-progress` callback 绑定 `NR-REC`/`REC1`，投影为 `PROCESSING`，不修改 lifecycle、dispatch 或 observation；
3. adapter 重启后收到独立 `completed` callback，并证明同一 stored object 可按声明的规范 WAV/MP3 profile 打开；投影为 `AVAILABLE`；
4. completed 重投、不同 delivery 的同义 completed 和更晚到达的 processing 都不重复绑定或降级；
5. 在没有更晚权威 revision/sequence 时注入 absent/failed，保持不可破坏的既有证据并进入 recording reconciliation；普通媒体读取 `404` 也不能产生 `REMOVED`；
6. 到期后先禁止新 access 并投递 durable deletion command；只有 adapter 权威确认不存在才进入 `REMOVED`；晚到 completed/processing callback 不恢复引用、访问、policy deadline 或 retention。

最终断言：一个 interaction 最多一个公开 recording；`externalRecordingRef` 只以 tenant/connector/purpose 绑定的 AEAD envelope 和独立 HMAC 关联；核心 persistence、日志、普通响应和 access audit 均没有 `RecordingUrl`、provider credential、native ID 或媒体 bytes。Call terminal、recording availability、removal 与 observation 四条状态线互不替代。

## 9. `CW-06` 媒体取消

### 9.1 客户端在首字节后中断

前置为 `REC1=AVAILABLE`、recording policy 未到期且 `RECORDING_READ=READY`。人工 caller 通过全部 B22-D/M 权限矩阵后，在短事务提交一个 access attempt；事务结束后 adapter 返回一个慢速、可背压、可取消的合成 WAV/MP3 handle。

测试在首个 chunk 写给客户端后触发 client disconnect，并断言：

- 取消信号立即传播到上游，provider read 停止，handle 的 cancel/close 重复调用安全，active handle 最终归零；
- 核心不透明续传、不再次 open provider、不拼接另一对象，也不在已提交 body 后写 JSON/provider error；
- access attempt 从 `AUTHORIZED`/`STREAMED` 补充为安全的 `CLIENT_ABORTED`；completion audit 写失败只留下可恢复的 incomplete，不为补审计再次读取媒体；
- buffering 保持固定上限，已发送字节小于对象长度，provider URL/header/credential 和 chunk 内容不进入日志、trace、metric 或异常。

### 9.2 截止与 removal 取消

分别在 adapter open 前、首字节前和流进行中让 recording hard deadline 到达或 removal 取得同一 recording 锁：

- open 前到期不调用 adapter；authorization 已提交但尚未 open 时，短启动期限/绝对截止复核失败；
- 流进行中到期或 removal 尽力取消活动 handle，结果为 `LIMIT_ABORTED` 或对应安全分类；后续 full/range 请求重新授权并失败；
- deletion command 的继续执行不依赖 `RECORDING_READ` readiness，读取 drift 或 connector 停用都不能取消删除义务；
- client cancel、deadline cancel 和 removal cancel 竞态可以多次调用幂等 close/cancel，但只能有一次资源释放和零次透明 reopen。

## 10. `CW-07` Readiness 漂移与 observation gap

初始 `r40/100` 快照最后在 `t50` 证明完整 status/recording callback 配置，expiry 尚未到达。fixture 在 `t55` 移除 required callback 集合或改变受治理 route，并把 core invalidation delivery 暂停，以覆盖三层 admission 的竞态：

1. adapter 本地 durable control transaction 增加到 binding revision `r41`，撤销 `START_OUTBOUND`/受影响 observation operation，停止领取新 start；它不等待核心旧 snapshot TTL；
2. 从 `t50` 到检测时刻 `t55` 保守创建 `Q1`，因为真实漂移可能发生在两次检查之间。gap identity、窗口和旧 capability evidence 持久化；
3. invalidation 暂停期间，即使核心凭旧 snapshot 先 claim 并把命令送到 adapter，pre-`DISPATCHING` 本地门也拒绝并保持 `RECEIVED`；随后恢复并投递 `r41/101 NOT_READY`，核心对尚未 claim 的新 outbox 拒绝领取；
4. 对已经越过 `DISPATCHING` 的 `C1`，漂移不能重新 start。若结果不确定则收敛 `AMBIGUOUS` 并创建 reconciliation case；
5. adapter 仍可用原 connector identity drain 已提交 journal/backlog、上报 gap、执行已有 reconciliation 和到期 recording delete。它不能把一条 backlog 改写到新 connector；
6. 先投递一个迟到的 `r40/101 READY`，它因旧 revision 被拒绝；再把已接受的 `r41/101 NOT_READY` 以相同序号、不同 digest 重放，形成安全冲突且不能恢复 readiness；
7. 修复配置并通过当前 build fixture/read-only live probe 后产生 `r41/102 READY`。它只允许新的、重新授权的 start admission，不能关闭 `Q1`、把 observation 改回 `NOMINAL`、恢复旧 lease 或自动重拨 `C1`；
8. `Q1` 只能依据创建时记录的 replay/history/final-consistency capability 关闭为 recovered、partial 或 unrecoverable。当前 Call 查询只证明终态时不得补造 ringing/connected。

媒体操作门使用同样的正交断言：仅 `RECORDING_READ` 漂移会阻止新的 open，但不把既有 `AVAILABLE` 改成 `REMOVED`；`RECORDING_DELETE` 仍按自己的 snapshot/decommission 路径继续。周期 readiness probe spy 在整个场景中对 create Call、hangup、真实 media read 和 delete 的调用数均为 `0`。

## 11. 组合覆盖矩阵

| Fixture | B22-R | B22-D | B22-S | B22-M | B22-A |
| --- | --- | --- | --- | --- | --- |
| `CW-01` duplicate/conflict | journal、inbox、digest、一次性 effect | raw/sensitive allowlist | 单调 milestone | recording fact 重复不降级 | route/ownership 隔离 |
| `CW-02` out-of-order | late fact、gap、reconciliation | 安全终止分类/无 raw cause | 终态、chronology、deferred | recording 维度不参与 lifecycle | capability evidence 解释对账 |
| `CW-03` callback-before-response | ledger、outbox settle、restart | command correlation 不泄密 | dispatch/lifecycle 正交 | 无关媒体不被推断 | pre-dispatch gate 与 ownership |
| `CW-04` two legs | delivery/restart correlation | 设备/号码/native ID 不出核心 | agent-first、两腿、two-party proof | recording 不证明 connected | topology build evidence |
| `CW-05` late recording | durable late delivery、对账 | ref AEAD/HMAC、到期与确认删除 | lifecycle/recording 正交 | 四态、stored object、404 边界 | read/delete 独立 operation gate |
| `CW-06` media cancel | access crash 不重做副作用 | 每次授权、deadline、删除义务 | participation/CRM 可见性交集 | stream、backpressure、cancel/close | read drift 不影响 delete |
| `CW-07` readiness drift | claim、AMBIGUOUS、gap/case | key/policy/retention gate | endpoint/readiness/lifecycle 正交 | availability/read/delete 正交 | revision/sequence、漂移、恢复 |

任何场景若只验证一个设计文档的局部状态而没有执行该行其余列的断言，不得宣称通过 B22-CW。例如只断言 `CONNECTED` 而没有 journal/inbox duplicate、native value leak scan 和 current readiness evidence，仍是失败。

## 12. 执行层次与生产 conformance

实现批次必须把相同场景 ID 和 checkpoint oracle 运行在三层：

1. **纯规范模型层**：确定性地穷举 delivery 顺序、重复、冲突和状态仲裁，快速证明 provider-neutral 不变量；
2. **开源 durable 组合层**：真实 MySQL transaction/lock/outbox/inbox，加可重启的模拟 adapter durable store、HTTP callback transport 和慢媒体流；不得用一个内存 fake 同时冒充 core 与 adapter durability；
3. **真实云 adapter conformance 层**：在隔离测试账号/项目中使用真实签名校验、callback route、Create/Fetch/Recording API、最小权限 credential 和 adapter store。周期 probe 本身仍禁止产生电话或媒体副作用；会产生外部动作的 canary 只在显式隔离验收中运行。

真实 adapter 的 conformance manifest 至少绑定 adapter contract version、不可变 build identity、deployment class、provider API profile、fixture 版本、通过的场景 ID、隔离证据和执行时间。核心的正向 readiness 只接受与当前 build/profile 相符的证据；手工声明、历史 build 结果或只通过静态 mock 的结果不足以产生 production `READY`。

故障注入至少覆盖 adapter journal commit 前后、core inbox commit 前后、Create Call response 前、ledger result commit 前后、adapter/core restart、client first-byte 前后和 readiness invalidation 传播前后。每个崩溃点恢复后仍必须满足同一 identity、同一 digest、同一 provider invocation 上限和同一泄漏扫描。

## 13. 通过条件与剩余边界

B22-CW 只有在以下条件同时成立时通过：

- `CW-01` 至 `CW-07` 的所有正向、负向、restart 和竞态变体都有确定 checkpoint；
- adapter 原始请求在 route/验签/journal 边界失败关闭，核心只接收 provider-neutral typed facts；
- provider start invocation、interaction milestone、recording binding、一次性 effect 和 media open/close 都满足各自上限；
- MySQL 与 adapter durable store 的真实事务/唯一键/lease 参与测试，不能以调用次数 mock 替代持久化仲裁；
- 全量敏感值 canary 扫描通过，任何普通输出、日志、错误或持久化白名单都没有 provider/native/号码/媒体泄漏；
- readiness drift 的本地停止、核心失效传播、gap 窗口、旧快照拒绝和新快照不抹 gap 全部成立。

本文件只闭合云 callback 路径中用户指定的组合 fixture。它不选择首个云厂商，不定义最终 Java/HTTP/SQL 名称，不实现真实 adapter，也不代替 PBX stream disconnect/fencing/bridge/stored-file 的组合 fixture。[B22-CP](B22-CP-pbx-composed-contract.md) 后来闭合其中 reconnect/fencing/session/topology/subscription/gap 的核心组合，[B22-PC](B22-PC-pbx-command-contract.md) 再闭合 PBX command crash/`AMBIGUOUS` 组合，[B22-PD](B22-PD-pbx-sensitive-data-contract.md) 又闭合 PBX 敏感数据组合，[B22-PM](B22-PM-pbx-media-contract.md) 进一步闭合 PBX stored-recording/media 组合；真实 provider conformance 仍须独立闭合。

## 14. 官方依据

- [Twilio Call Resource](https://www.twilio.com/docs/voice/api/call-resource)：Call 是单条资源；status callback 是独立 HTTP 请求，`SequenceNumber` 表示发出顺序但不保证到达顺序；默认只有 completed，queued 状态取消可能没有 callback；Retrieve Call 最终一致。
- [Twilio Webhook Security](https://www.twilio.com/docs/usage/webhooks/webhooks-security)：签名验证依赖完整外部 URL、编码后的 query/form 参数或 raw JSON body，验证必须发生在通用 DTO 解析前。
- [Twilio Record](https://www.twilio.com/docs/voice/twiml/record)：Call/Record action 中的 recording URL 可能尚不可用，独立 recording status callback 的 completed 才表示录音完成且可访问。
- [Twilio Recordings Resource](https://www.twilio.com/docs/voice/api/recording)：recording metadata、WAV/MP3 media read 和 delete 是不同 provider 能力；URL 与 credential 继续留在私有 adapter。
