# B22-CP PBX 组合契约 fixture

> 决策日期：2026-07-19
>
> 状态：PBX 组合契约 fixture 设计闭合；冻结可由私有 PBX adapter 与后续开源 durable 模拟 adapter 共同执行的 trace、oracle 和失败关闭断言，不表示 Asterisk、FreeSWITCH 或 B22 runtime 已实现
>
> 输入：[B22 总规划](../batch-B22.md)、[B22-R 可靠性设计](B22-R-reliability.md)、[B22-S 状态与拓扑设计](spikes/B22-state-and-topology.md)、[B22-A readiness 与隔离设计](B22-A-adapter-readiness.md)和 [PBX 事件流 spike](spikes/B22-pbx-event-stream.md)

## 1. 决策摘要

B22-CP 用一套确定性的双 connector PBX trace 组合证明以下不变量，而不是再为每份设计各写一组互不相连的 happy-path 单测：

- dirty reconnect、新 owner fencing 和旧 owner 写入拒绝形成一个不可拆分的控制序列；
- durable session registry 在重连、adapter 进程重启和 native 多腿重新发现后仍返回相同 `externalInteractionRef` 与两条规范 leg ref；
- early media、坐席单腿接听、对端单腿接听、两腿分别 media-ready、bridge 创建或 holding membership 都不能产生 `CONNECTED`；
- 只有精确的 `AGENT` 与 `REMOTE_PARTY` 两腿同时 media-ready，并有符合固定 topology 的双向两方媒体关系证明时，才提交一次 `PARTICIPANTS_CONNECTED`；
- connector 在 frame body 解析前由 fenced stream 选定，错误 app/stream/native ownership 的输入不能通过 body、variable、extension 或同名 session 切换 connector/tenant；
- 非 clean close 在新 owner ready 前先持久化 observation gap；重连、健康快照和 active-resource snapshot 都不能冒充 replay 或证明 gap 完整恢复。

fixture 是未来实现的共享 conformance contract，不是 provider emulator 的真实性声明。通过内存 mock、只检查最终 `CONNECTED`、跳过 durable store 或让 adapter 自己宣布测试通过，都不满足 B22-CP。

本检查点只闭合 PBX reconnect/fencing、session correlation、接通证明、订阅隔离与 gap 的组合面。[B22-CW](B22-CW-cloud-callback-fixture.md) 已独立闭合云 callback 组合；[B22-PC](B22-PC-pbx-command-contract.md) 后来闭合 PBX command crash/`AMBIGUOUS` 全矩阵，[B22-PD](B22-PD-pbx-sensitive-data-contract.md) 再闭合 B22-D 敏感值/销毁组合，[B22-PM](B22-PM-pbx-media-contract.md) 又闭合 PBX stored-recording 流/取消/上限/确认删除组合。真实 PBX canary 仍是后续独立门槛，因此 B22 仍不拆实现子批次。

## 2. Fixture 边界与角色

### 2.1 固定拓扑

测试部署包含两个 tenant、两个逻辑 connector、同一个受信 adapter 进程和一个可共享的 PBX 测试节点：

| Fixture identity | Connector A | Connector B |
| --- | --- | --- |
| tenant | `tenant-a` | `tenant-b` |
| connector | `connector-a` | `connector-b` |
| governed stream | `stream-a` / `app-a` | `stream-b` / `app-b` |
| initial owner | `owner-a-1`, fence `17` | `owner-b-1`, fence `29` |
| session key | `session-7` | 同名 `session-7` 也合法，但属于独立分区 |
| normalized interaction | `external-interaction-a` | 不因 A 的 trace 被创建或修改 |

Asterisk profile 使用独立 Stasis app、`subscribeAll=false` 和首期最小 resource subscription。FreeSWITCH profile 使用两条固定逻辑 stream/filter 与全局 native ownership 仲裁；若只能提供 adapter-enforced trust zone，测试报告必须如实记录该等级，不能标成 provider resource-scoped isolation。

所有标识都是无敏感值的 fixture token。真实号码、Caller ID、extension、dial string、SIP header、channel variables、credential、native error、媒体路径和原始 frame 不得进入 oracle snapshot、核心 fact、日志或测试失败输出。

### 2.2 可观察 store

driver 必须让独立 oracle 以只读方式观察下列逻辑状态；具体表、文件、RocksDB/SQL 产品或 Java 类型不属于本设计：

1. stream owner lease、connection generation、fencing token、last proven-ready point 和 checkpoint；
2. connector-partitioned journal，包括 delivery identity、digest、状态和 fence；
3. durable session registry，包括 provider session/leg correlation、稳定外部 ref、核心 interaction binding 和 tombstone；
4. native resource ownership registry 与隔离 quarantine；
5. 核心 fact inbox 的 accepted/duplicate/no-change/rejected 结果；
6. interaction 的两条规范 leg、lifecycle、`connectedAt`、`terminatedAt` 和 observation assessment；
7. gap、reconciliation case、创建时 capability evidence、coverage 和完成状态；
8. connector 分区容量、readiness 与安全计数器。

oracle 直接读取提交后的 store/projection 或它们的只读测试投影，不能复用被测 adapter 的“expected state”方法。每个动作后都检查 A、B 两个分区；只比较最后状态会漏掉旧 fence 曾成功写入、短暂跨 tenant 投递或 gap 在新 frame 后才补写的问题。

### 2.3 Driver 动作词汇

共享 harness 至少提供以下语义动作；真实 adapter 可将它们绑定到 ARI/ESL 测试环境，durable 模拟 adapter 使用相同动作与 oracle：

- `claimStream(connector, owner)`：取得唯一 stream lease 并返回新 fence；
- `openAuthenticatedStream(connector, generation)`：建立已经由部署 binding 选定 connector 的来源连接；
- `acceptNativeFrame(owner, fence, frame)`：验证 ownership、白名单化并在 journal commit 后返回；
- `crashOrDirtyClose(owner)`：不写可证明的 clean-close marker；
- `restartAdapter()`：清空进程内状态但保留所有 durable store；
- `discoverActiveResources(owner, fence)`：只读活动 channel/bridge snapshot，不提供 replay；
- `reconcile(gapRef, capabilitySnapshot)`：执行 B22-R 的只读、有界、可重投 query；
- `attemptStaleWrite(storeKind, oldFence)`：分别尝试 journal、checkpoint、session registry 和 ownership 写入；
- `deliverToCore(deliveryRef)`：经规范 ingress/inbox 应用已经 journaled 的 typed fact。

测试时钟、lease deadline、provider observed time 和 core received time 固定可推进。崩溃动作必须真正丢弃内存对象并从 durable store 重建，不能只调用同一个对象的 `reconnect()`。

## 3. 初始不变量

执行主 trace 前必须证明：

- `connector-a` 与 `connector-b` 都是独立活动注册，精确映射到各自 tenant；普通 fact body 无 tenant/actor/authority 字段；
- 两个 connector 的 credential、binding、stream、ledger、journal、session、ownership、query、capacity、quarantine、retention 和 observability 分区独立；
- A 的 `OBSERVE_INBOUND`/connected-observation evidence 绑定当前 adapter build、binding revision 和 fence；B 的 readiness 不依赖 A 的 owner；
- A/B 的 governed subscription 均不是 `ALL`/`subscribeAll=true`，并且只包含首期所需事件和显式拥有的资源；
- session registry、journal、stream fence 和 ownership registry 均可 durable commit；hard capacity 以下仍保留 gap control capacity；
- 核心中尚无 A/B interaction、inbox delivery 或 gap。

任一条件不满足时 fixture 在产生 provider frame 前失败关闭；不能通过跳过负向分支把 connector 宣告 conformance。

## 4. `CP-PBX-01` 主 trace

### 4.1 Owner、session 与 agent-first

| Step | Driver/provider 动作 | Adapter 必须提交 | 核心 oracle |
| --- | --- | --- | --- |
| 1 | A 的 `owner-a-1` 取得 stream | fence=`17`、generation=`g1`、owner 唯一 | B 的 fence/readiness/store 完全不变 |
| 2 | A 发现 `session-7` 和一个 native agent channel；同时存在 Local/control channel | registry 原子建立 `session-7 -> external-interaction-a` 与 agent native leg -> `external-agent-leg-a`；额外 native leg 只保留私有最小 correlation | 幂等发现一个 interaction 和一个 `AGENT` leg；没有第三条规范 leg |
| 3 | agent channel `Up`，并证明 endpoint 与平台双向媒体可用 | journal `LEG_MEDIA_READY(external-agent-leg-a)`；其 delivery/fact digest 稳定 | lifecycle 仍为 `PREPARING`，`connectedAt` 为空；此后才允许创建/呼叫 remote leg |
| 4 | remote leg 在 step 3 durable 后被创建并 alerting | registry 将 remote native leg 绑定同一 session 和稳定 `external-remote-leg-a`；journal discovered/alerting facts | 恰好一个 `REMOTE_PARTY` leg，lifecycle=`RINGING`；agent-first 顺序可由 committed evidence 证明 |

若 remote provider invocation 或 native remote leg 的 durable creation evidence 早于 agent `MEDIA_READY` commit，fixture 必须以 topology violation 失败；不能仅靠核心稍后收到的事件顺序掩盖实际先拨客户。

### 4.2 Early media、单腿接听与错误 bridge

| Step | Driver/provider 动作 | 允许的归一化 | 禁止的投影 |
| --- | --- | --- | --- |
| 5 | remote `183`/progress-media/ringback，存在单向 early-media path | 可保留私有诊断分类；若已有 alerting，可重复/no-change | 不产生 remote `MEDIA_READY`，不产生 `PARTICIPANTS_CONNECTED`，仍为 `RINGING` |
| 6 | 只有 agent leg 已 `Up`/answered | 已有 agent `MEDIA_READY` 重复事实只能 duplicate/no-change | 不接通；单腿接听不是 interaction connected |
| 7 | remote leg `Up`/answered，证明 remote endpoint 与平台双向媒体可用，但尚未桥接 agent | `LEG_MEDIA_READY(external-remote-leg-a)` | 两腿分别 ready 仍不接通，`connectedAt` 为空 |
| 8 | 只创建 mixing bridge、创建 holding bridge、只加入一条目标腿，或加入 unrelated/control leg | 无生命周期 fact；必要时安全 quarantine/reconciliation conflict | 任一种 bridge existence/membership 都不能产生 `CONNECTED` |

此分支必须检查 journal 和 core inbox，而不只检查 public projection：adapter 若曾错误提交 `PARTICIPANTS_CONNECTED` 后又被核心拒绝，同样不通过 conformance，因为它没有证明 B22-S 的映射能力。

### 4.3 Dirty reconnect、fencing 与 durable correlation

| Step | Driver/provider 动作 | 必须发生的顺序与断言 |
| --- | --- | --- |
| 9 | `owner-a-1` 在 step 8 之后 dirty close；没有 clean marker | A 的 observation path 立即失去正向 readiness；不能把 TCP 重连计划当成 gap close |
| 10 | `restartAdapter()`；`owner-a-2` claim A stream | 新 fence=`18`、generation=`g2`。在一个 fenced control transaction 中先提交确定性的 A gap open，再允许 g2 ready/消费首个 frame；gap 起点不晚于 g1 的 last proven-ready point |
| 11 | 旧 `owner-a-1`/fence 17 分别尝试 append journal、推进 checkpoint、改变 session mapping、写 native ownership | 四类写入都因 stale fence 失败且无部分提交；不能只 fence checkpoint。B 的 owner/fence 不变 |
| 12 | 新 owner 从 durable store 重建，active snapshot 再次看到 `session-7`、两条参与 channel 与额外 native legs | session 和两条 leg 仍精确映射到 `external-interaction-a`、`external-agent-leg-a`、`external-remote-leg-a`；核心不创建第二 interaction/leg；generation/fence 不参与任何外部 ref |

session registry 重建必须先于对新 frame 生成 correlation。若 registry 暂时不可用或出现一个 native leg 对多个 session、一个 session 对多个 external interaction、已绑定 interaction 改 tenant/connector 等冲突，新 owner停止该分区 ingress 并 quarantine/open incident，不能临时生成 ref 后再合并。

### 4.4 订阅隔离负向分支

在 A 的 gap 已 durable open、新 owner 消费正常 A frame 前，执行以下攻击/误路由：

1. 把 ownership 属于 `connector-b`/`app-b` 的 native frame 交给 `stream-a`；body 同时伪造 `tenant-a`、`app-a`、A 的 extension 或 `session-7`。
2. 把没有 ownership、但 body 声称属于 A 的 native frame 交给 `stream-a`。
3. 尝试把 A 的既有 native leg 从 ownership registry 改绑 B，或用 B 的 session registry lookup 补全 A frame。

三种输入都必须在 typed core fact 构造前拒绝或进入 A 的有界隔离 quarantine：

- 不能从 frame body 切换到 A 或 B；受治理 stream 已先选定 A，ownership 只能 corroborate 或拒绝；
- A/B journal、session、inbox、interaction、gap、capacity admission 和 readiness 不发生跨分区修改；
- B 的合法同名 `session-7` 仍可在 B 分区独立映射，不能与 A 冲突或复用 external ref；
- quarantine 只保存安全 reason、connector/stream/fence 和 opaque evidence digest，不保存 raw frame、变量、号码或异常原文；
- 单个被正确拒绝的攻击/误路由不改变 readiness 或另开 gap；若证据反而表明真实 subscription/ownership 已漂移并可能漏收合法事件，则撤销受影响 readiness，并按影响窗口打开独立 gap，不能吞掉错误后继续宣称 `READY`。

只有此负向分支和两个 connector 的逐步 store diff 都通过，shared adapter deployment 才能声明对应的 B22-A 隔离等级。

### 4.5 双向媒体证明、重复与终止

| Step | Driver/provider 动作 | Adapter 必须提交 | 核心 oracle |
| --- | --- | --- | --- |
| 13 | g2 的权威事件/查询证明 exact agent 与 remote 两腿同时属于首期允许的 two-party mixing relation，且媒体双向；无 holding/IVR/unrelated member | 一个 `PARTICIPANTS_CONNECTED(external-agent-leg-a, external-remote-leg-a)`，携带可验证的 provider 顺序/时间证据但不携带 bridge ID/map | 校验 connector/session/interaction/角色/两腿 ready 后，首次写入 `connectedAt`，lifecycle=`CONNECTED` |
| 14 | 相同 source event 重投、不同 delivery 的等价 connected fact、事件乱序再次到达 | 同 delivery duplicate；等价 fact no-change；不创建新 session/leg | `connectedAt` 永不改变，只产生一次业务副作用 |
| 15 | 任一 required leg 权威终止 | journal 规范 leg/interaction terminal fact，安全 reason 不含 native cause | lifecycle=`TERMINATED`；`terminatedAt` 单调，既有 `connectedAt` 保留 |

bridge 证明必须来自 adapter build 声明并由本 fixture 驱动的固定映射。无法证明双向关系的 PBX/profile 可以把相关 connected-observation operation 标成 `UNSUPPORTED`，但不能用两条 `Up`、bridge name、录音开始或 RTP packet count 的猜测通过 step 13。

## 5. Observation gap 与 reconciliation oracle

`CP-PBX-01` 的 gap 只因 step 9 的 dirty close 产生，且必须满足：

1. `gapRef` 对 `(connector-a, old owner/fence, evidence window, reason)` 稳定；重启或重复 open 不创建第二个逻辑 gap；
2. gap open durable commit 早于 g2 ready、首个新 frame、checkpoint advance 和正向 readiness snapshot；
3. 重连只能把 gap 从 `OPEN` 送入 `RECONCILING`，不能直接关闭；
4. Asterisk active channel/bridge snapshot 或无 replay 的 ESL 当前态只能返回 `CURRENT_STATE_ONLY`。即使它确认 step 13 的当前媒体关系，也不能证明断线窗口没有漏掉 ringing、bridge change 或短暂终止；
5. 上述 evidence 最多把 gap 关闭为 `CLOSED_PARTIAL`；受影响 interaction 的 observation assessment 为 `OBSERVATION_INCOMPLETE`。step 13/15 的明确晚到事实仍可单调推进 lifecycle，但普通事实和新 readiness 均不清除 incomplete；
6. 只有 profile 实际声明并证明覆盖完整 evidence window 的 replay/cursor 才允许 `CLOSED_RECOVERED`。fixture 默认 PBX profile 没有该能力，测试不能通过开关伪造；
7. reconciliation 使用独立 durable query ledger、稳定 ID/digest、分页/coverage completion；任何 start、originate、hangup、bridge mutation 或 media control 调用使 fixture 立即失败；
8. gap/case 完成不能补造未观测的 `RINGING`、`CONNECTED` 或 `TERMINATED`，也不能重拨已有 command。

若断线窗口中 provider 已结束 session，而 active snapshot 查询不到对象，非权威 `NOT_FOUND` 不等于完整或未发生。没有 CDR/CEL/历史能力时，case 关闭为 `UNRECOVERABLE`，interaction 保持 `OBSERVATION_INCOMPLETE`；这作为主 trace 的必跑替代结尾，而不是可选说明。

## 6. Crash points 与执行矩阵

同一主 trace 至少在以下 cut point 参数化执行；每次都从空 fixture store 开始，崩溃后只从 durable state 恢复：

| Cut point | 恢复断言 |
| --- | --- |
| session registry 创建前 | 重投只能建立一个 session mapping；未提交的 ref 不得泄露给核心 |
| session registry commit 后、core discovery 前 | 使用相同 `externalInteractionRef` 完成一次幂等 discovery |
| native frame 收到后、journal commit 前 | frame 不可声称 durable；dirty close 打开 gap，不能靠内存补投 |
| journal commit 后、core ack 前 | 使用相同 delivery/digest 重投，核心至多应用一次 |
| gap open 前的 owner 丢失 | 新 owner 的第一笔 fenced control transaction 先写 gap |
| gap commit 后、新 generation ready 前 | 再次 claim 复用逻辑 gap，不产生 gap-free ready 窗口 |
| connected journal commit 后、core ack 前 | 重投后 `connectedAt` 和业务副作用各一次 |
| reconciliation page commit 后、completion 前 | 从相同 cursor/delivery identity 恢复，所有 page ack 后才完成 |

每个 cut point 都重复 stale-fence 和 connector-B isolation 断言。只在无崩溃路径执行负向测试不够，因为旧进程和恢复中的 registry 最容易形成跨 owner/connector 旁路。

## 7. Conformance 输出

一次合格运行输出有界、无敏感值的机器可读报告，至少包含：

- adapter contract version、build evidence digest、PBX profile、binding revision 和隔离等级；
- fixture version `B22-CP/1`、每个 cut point/scenario 的 pass/fail 和安全失败分类；
- 每个 connector 的最终 journal/inbox/session/gap 计数以及 stable identity digest；
- stale fence write 成功数必须为 `0`，cross-connector core delivery/lookup/mutation 数必须为 `0`；
- A 的 interaction 数=`1`、规范 leg 数=`2`、`connectedAt` 写次数=`1`、业务 connected side effect 次数=`1`；
- gap open-before-ready 证据、最终 `CLOSED_PARTIAL`/`CLOSED_UNRECOVERABLE` 与 `OBSERVATION_INCOMPLETE` 断言；
- 未调用 reconciliation write/control API、未出现 raw/sensitive/provider-private 值的扫描结果。

报告不能包含完整 native resource、provider endpoint、app secret、号码、channel variable、原始 frame、bridge ID、recording ref/path、credential 或异常 message。fixture 通过只证明该 build/revision/profile 对 B22-CP/1 的这组 provider-neutral 语义合格；配置漂移、build 变化、binding revision 变化或订阅变化必须重新执行，旧报告不能自动迁移。

## 8. 闭合与剩余门槛

B22-CP 闭合 PBX 侧以下组合设计问题：

- fencing 必须同时保护 journal、checkpoint、session 与 ownership，而不只是 WebSocket owner；
- session identity 跨 generation/restart 稳定，native 多腿不会穿透为第三条规范腿；
- early media、单腿 answered 和无精确关系的两腿 ready 均不可接通；
- stream/subscription/ownership 在 body 之前确定 connector，shared process 的另一 tenant 不能被错误 frame 影响；
- dirty reconnect 必然留下 durable gap，current snapshot 与健康恢复不会伪造完整观察。

不能由本 fixture 推导的项目包括：

- command ledger 的全部 provider start crash/`AMBIGUOUS` 与 authoritative absence 组合；该项后来由 [B22-PC](B22-PC-pbx-command-contract.md) 独立闭合；
- B22-D 号码、密钥、retention、quarantine payload 和确认销毁矩阵；该项后来由 [B22-PD](B22-PD-pbx-sensitive-data-contract.md) 独立闭合；
- B22-M 录音晚到、stored object、读取/单 range、取消、大小/时间上限和删除确认组合；该项后来由 [B22-PM](B22-PM-pbx-media-contract.md) 独立闭合；
- 真实 Asterisk/FreeSWITCH/SIP trunk 版本、Docker/电话 canary 和 production adapter conformance。

因此本文件只把 B22 总门槛中的 PBX 核心组合 fixture 标为闭合；后来 B22-PC、B22-PD 与 B22-PM 又分别闭合 command、敏感数据/确认销毁和 stored-recording/media 组合，但真实 provider 组合仍未完成，不能把整个 B22 或任一真实 adapter 标为完成。
