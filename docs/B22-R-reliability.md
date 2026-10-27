# B22-R 呼叫可靠性、幂等与对账设计

> 决策日期：2026-07-19
>
> 状态：可靠性前置设计闭合；仅冻结 provider-neutral 语义、持久化职责和契约测试门槛，不预占 HTTP 路径、Java 类型、表名、错误码或 Flyway 版本。B22a 已独立实现 connector 身份与控制平面，但不代表本 B22-R 呼叫可靠性 runtime 已进入实现
>
> 输入：[B22 总规划](../batch-B22.md)、[云 Webhook spike](spikes/B22-cloud-webhook.md)、[PBX 事件流 spike](spikes/B22-pbx-event-stream.md)

## 1. 决策摘要

B22-R 不承诺端到端 exactly-once 拨号。它把可靠性拆成可以分别证明的五段，并在每一段保留真实的不确定性：

1. 人工请求在核心内通过请求幂等绑定、interaction 和 command outbox 的单事务提交，保证同一有效请求只产生一个本地呼叫意图；
2. 核心以至少一次方式投递同一个 `commandId`，私有 adapter 先写 durable command ledger，再触达 provider，保证核心传输重试不会变成第二次外部起呼；
3. adapter 到 provider 的起呼结果只收敛为 `CONFIRMED`、`REJECTED` 或 `AMBIGUOUS`。只有 adapter 声明且实际证明的 provider 幂等窗口内才允许重试外部调用；其他不确定窗口禁止盲目再拨；
4. provider 事实先进入 adapter durable ingress journal，再以稳定 `deliveryRef` 至少一次投递到核心 inbox。核心在一个短事务中同时保存 inbox 结果和推进规范投影；
5. provider→adapter 天然可能有事件缺口。gap、reconciliation case 和结果证据都是持久化事实；恢复连接、重启进程或查询不到对象都不能自动证明完整或未拨号。

这里的“双重幂等”专指两个不同危险面：

- `requestId` 防止客户端重试创建两个 interaction 和两条起呼命令；
- `commandId` 防止同一条 outbox 的至少一次投递触发两次 provider 起呼。

核心 inbox 的 `deliveryRef` 去重是第三个独立边界，只负责事实投递，不替代上述任一层。

## 2. 可承诺与不可承诺的保证

| 边界 | B22-R 保证 | 明确不保证 |
| --- | --- | --- |
| human→core | 同一 caller 作用域、同一 `requestId`、同一规范请求返回同一本地接收结果 | 用户成功响应不表示 provider 已拨号 |
| core transaction | interaction、请求幂等绑定和 start outbox 全部提交或全部回滚 | 普通 post-commit 事件不承担外部呼叫投递 |
| core→adapter | 同一个 `commandId` 至少一次投递，传输失败后仍只重投同一命令 | 核心网络超时不用于推断 adapter 是否已调用 provider |
| adapter ledger→provider | `IDEMPOTENT_START` 或 `AT_MOST_ONCE_START` 中 adapter 声明的那一种保证 | 不存在跨所有 provider 的 exactly-once 起呼 |
| provider→adapter | 已通过原始来源验证并成功写入 journal 的事实不会因 adapter→core 故障丢失 | Webhook 未配置、provider 重试耗尽或 PBX 断线期间的事实不保证可恢复 |
| adapter→core | 同一 journal 项以稳定 `deliveryRef` 重投；相同 delivery 不会重复推进核心状态 | 不同 delivery 的语义重复不依赖到达顺序 |
| core projection | 规范状态单调推进，晚到或重复事实不会倒退终态 | 没有已知 gap 不等于事件序列已被证明完整 |

MySQL 是核心 interaction、request binding、outbox、inbox、gap 和 reconciliation case 的正确性来源。adapter 的 durable store 是 command ledger、ingress journal、session registry 和连接 fencing 的正确性来源。Redis、进程内队列、普通日志和指标都不能替代其中任何一项。

## 3. 持久化职责分层

### 3.1 核心持久化记录

规划概念不要求一概念一张表，但实现必须保留以下独立不变量：

| 记录 | 最小职责 | 权威唯一性 |
| --- | --- | --- |
| request binding | 绑定人工重试、规范请求 digest、interaction 和首次接收结果 | `(tenant, initiatingMembership, requestId)` |
| interaction reliability projection | 保存 dispatch 状态、已知 observation assessment 和规范生命周期引用 | tenant-scoped `interactionRef` |
| command outbox | 保存一个 typed start command、`commandId`、connector、digest、投递状态和租约 | `(tenant, connector, commandId)`；一个 interaction 首期最多一个 start command |
| fact inbox | 保存稳定 delivery、核心计算 digest、处理结果和安全分类 | `(tenant, connector, deliveryRef)` |
| observation gap | 保存 connector 或 interaction 范围的已知观测缺口 | `(tenant, connector, gapRef)` |
| reconciliation case | 保存 AMBIGUOUS/gap/超时观察的对账进度与最终证据分类 | tenant/connector/subject/reason 的确定性 case key |

所有记录在 tenant 解析后显式携带 tenant 和 connector。数据库用 tenant/connector 复合外键或等价约束重复保护 interaction、command、inbox、gap 和 case 的关联，不能只依赖 application 已经检查过一次。

### 3.2 私有 adapter 持久化记录

| 记录 | 最小职责 | 不能混入的职责 |
| --- | --- | --- |
| command ledger | 吸收重复 `commandId`，记录外部调用是否可能已经发生以及安全结果 | 不保存 CRM authority，不以 provider 错误原文作为核心状态 |
| ingress journal | 在应答或异步转发前持久化最小规范事实，负责 adapter→core 重投 | 不充当无界原始 Webhook/frame archive，不负责跨帧会话归并 |
| correlation/session registry | 稳定解析一次外部会话、legs、核心 interaction 与 provider correlation | 不替代 journal delivery identity，不把连接世代当会话 identity |
| connection ownership/fence | 对单 connector 流的租约、世代和旧 owner 写入做 fencing | 不选择 tenant，不进入 `AuthenticatedConnector` |

这四类记录可以位于同一个私有数据库，但必须有独立唯一键、状态、容量和保留策略。只写本地文件日志、只依赖内存、Redis-only 或在崩溃后可能丢失已确认数据的异步 flush 都不满足 durable 要求。

## 4. 第一重幂等：人工请求

### 4.1 作用域和 digest

人工发起使用 caller-scoped `requestId`。规划唯一作用域为 `(tenant, initiatingMembership, requestId)`，避免不同成员的客户端 key 互相碰撞，也避免 key 被用来探测其他成员的呼叫。

请求 digest 只覆盖客户端实际选择且会改变意图的规范字段，例如业务对象类型、对象 ID 和选中的受治理电话号码字段；它不包含 raw JSON、当前权限结果、provider 配置或普通日志不可记录的完整号码。完整拨号目标在首次执行时从当前可见且未脱敏的服务端字段解析并形成单独受保护的授权时快照，不能由重试时重新读取的新号码改写原 interaction。

同一 key 的语义固定为：

- digest 相同：在重新验证当前活动成员和入口能力后，返回首次 interaction 的同一个“本地已接收”回执；
- digest 不同：返回稳定幂等冲突，不创建第二个 interaction 或 outbox；
- 首次事务未提交：request binding、interaction 和 outbox 都不存在，客户端可用同一 key 安全重试。

幂等命中不绕过当前身份检查，也不直接返回可能已被当前数据范围隐藏的完整呼叫详情。它只返回首次接收时允许公开的最小回执；后续详情仍走独立查询授权。

### 4.2 原子提交和并发仲裁

首次请求在一个 `CommandServiceTemplate` 写事务中完成：

```text
revalidate caller/member/capability, target field, active connector and unexpired readiness snapshot
  -> reserve or lock request binding
  -> compare canonical request digest
  -> create interaction
  -> create exactly one start command outbox row
  -> commit all records together
```

实现可以用确定性幂等 guard 行或唯一键插入加持锁重读，但不能只做“先查再插”的无锁 pre-check。数据库唯一约束是最终并发仲裁者。冲突分支必须能读取已提交的首次 digest 和回执，不能把重复键异常直接泄露为任意基础设施消息。

明确禁止：

- 把 `requestId` 直接当 `interactionRef`、`commandId`、provider call ID 或 credential；
- 在原 interaction 为 `AMBIGUOUS` 时把同一 request 重新变成 `PENDING`；
- 通过改变 request digest、connector 或目标号码复用旧 key 发起第二次呼叫；
- 删除 interaction 时立即释放幂等 key 供再次拨号。

需要明确发起第二次呼叫时，客户端必须在重新授权后提交新的 `requestId`，核心生成新的 interaction 和 `commandId`。

## 5. 第二重幂等：command outbox 与 adapter ledger

### 5.1 核心 outbox

start outbox 是 typed command，不是任意 domain-event JSON。它与 interaction 同事务创建，至少包含核心生成的 `commandId`、tenant/connector 关联、interaction correlation、规范 command digest、创建时间，以及独立保护的最小拨号快照引用。

规划状态为：

| 状态 | 含义 | 允许迁移 |
| --- | --- | --- |
| `READY` | 已提交，尚未被有效租约持有 | `LEASED` |
| `LEASED` | 某 worker 持有有界租约；外部 I/O 在事务外执行 | 结果到达后 `SETTLED`；租约过期回 `READY` |
| `SETTLED` | 核心已持久化 `CONFIRMED`、`REJECTED` 或 `AMBIGUOUS` 结果 | 不再投递 start；后续只允许对账补证据 |

worker 以 `FOR UPDATE SKIP LOCKED` 或等价方式有界领取记录，递增 fencing token 并提交租约后才做网络调用。网络调用不得占用数据库事务。旧 lease 的本地完成不能覆盖新 lease 已提交的状态，但任何经 `AuthenticatedConnector` 返回、能证明 provider 事实的晚到结果仍按 command/inbox 规则接受和仲裁。

core→adapter 的连接超时、进程崩溃或响应丢失只会使同一个 `commandId` 再次投递。它们本身不会把 dispatch 标记为 `AMBIGUOUS`；只有 adapter 对 provider 调用的结果不确定时才产生该状态。

### 5.2 adapter command ledger

adapter 收到 command 后必须先按 `(connectorRef, commandId)` 插入或锁定 durable ledger，并比较 command digest，然后才能向核心确认自己已持久接收。相同 ID 不同 digest 是永久冲突和安全告警，不允许覆盖首次命令。

规划 ledger 状态为：

| 状态 | 含义 |
| --- | --- |
| `RECEIVED` | 命令已持久化，尚未越过外部调用门槛 |
| `DISPATCHING` | “外部调用可能发生”的位已经在 provider I/O 前提交 |
| `CONFIRMED` | provider 明确接受或后续相关事实证明对象存在 |
| `REJECTED` | provider 明确未接受，保存安全分类 |
| `AMBIGUOUS` | provider 是否接受无法可靠判断，禁止再次 start |

adapter 从 `RECEIVED` 领取命令后，先重新要求 command ledger、ingress journal、required callback/stream owner 和 provider control capability 均 ready，再提交 `DISPATCHING`，最后做 provider I/O。readiness 不满足时命令保持 `RECEIVED` 并告警，不能在无法可靠观察 callback/event 时继续起呼。这个顺序可能把“提交位后、实际调用前崩溃”保守地判为 `AMBIGUOUS`，但不会为了减少假不确定而冒重复拨号风险。

每个 adapter 必须在 readiness/capability 中恰好声明一种 start guarantee：

- `IDEMPOTENT_START(window)`：provider 明确支持调用方稳定幂等键或等价的历史去重，并声明有效窗口。adapter 只可在窗口内以相同 provider key 和相同 digest 重试；窗口到期仍无法确认时进入 `AMBIGUOUS`；
- `AT_MOST_ONCE_START`：adapter 对每个 command 最多执行一次可能触达 provider 的 start。超时、断线或 `DISPATCHING` 后崩溃都进入 `AMBIGUOUS`，不得再次调用 start。

“可按相关性查询”“可查询活动对象”“可查询终态/CDR”是正交 reconciliation capabilities，不得被包装成无限期 `IDEMPOTENT_START`。

每次 ledger 收敛为 `CONFIRMED`、`REJECTED` 或 `AMBIGUOUS` 时，必须在同一 adapter 事务中写入或关联一条具有稳定 `deliveryRef` 的 core-result delivery；也可以把 ledger 自身实现为可重投结果 outbox，但不能只更新状态后依赖一次内存回调。provider 结果写 ledger 后即使 adapter→core 响应丢失，也能重复发送相同结果；若 adapter 在 provider 返回后、ledger 终态提交前崩溃，则从 `DISPATCHING` 按 start guarantee 和 reconciliation capability 恢复，不能猜测结果或直接再拨。

### 5.3 callback-before-response

provider callback 或 PBX event 可能早于 start HTTP/command 响应。只要已验证事实能按 command correlation、external session registry 和同 tenant/connector 约束证明该外部对象属于当前 command：

- adapter ledger 可以从 `DISPATCHING` 收敛为 `CONFIRMED`；
- 核心 fact inbox 可以在 outbox result 之前把 dispatch 投影收敛为 `CONFIRMED`，并在同一事务中 settle 对应 start outbox，防止 lease 到期后继续投递；
- 之后到达的相同 `CONFIRMED` 是幂等 no-op；
- 之后到达的 `REJECTED` 是证据冲突，进入 reliability incident，不得把已观察到的呼叫改回“未拨出”。

## 6. `AMBIGUOUS` 的稳定语义

dispatch 状态与 interaction lifecycle 分离。`CONFIRMED` 只表示 provider 对象已被接受或观察到，不表示振铃、接通或终止；`AMBIGUOUS` 也不是业务失败或呼叫终态。

允许的 dispatch 迁移为：

```text
PENDING -> CONFIRMED
PENDING -> REJECTED
PENDING -> AMBIGUOUS
AMBIGUOUS -> CONFIRMED       (发现 provider 对象或相关事实)
AMBIGUOUS -> REJECTED        (仅有明确拒绝或可证明的权威不存在)
```

`CONFIRMED` 和 `REJECTED` 是普通终点。若它们之后出现相反的强证据，核心保留原证据并标记 reliability conflict；不能静默 last-write-wins。已观察到 provider interaction 的事实优先阻止重复拨号，但不伪造缺失的生命周期里程碑。

`AMBIGUOUS` 是弱于已确认结果的不确定证据。晚到的 `AMBIGUOUS` 不能把 `CONFIRMED` 或 `REJECTED` 降级；它只追加一次可靠性诊断或成为 no-op。

进入 `AMBIGUOUS` 时，核心在同一事务中停止 start outbox 重投并创建或取得一个 durable reconciliation case。后续规则固定为：

- 不根据等待超时、查询为空、连接恢复或人工刷新自动重试 start；
- 任意可信的 correlated lifecycle/provider object 证据可收敛为 `CONFIRMED`；
- 只有 adapter 声明 `AUTHORITATIVE_COMMAND_ABSENCE`，并在声明的最终一致窗口之后对完整查询范围给出权威不存在证据，才可收敛为 `REJECTED`；
- 非权威 `NOT_FOUND`、查询范围不完整、provider 不支持历史查询或对账超时都保持 `AMBIGUOUS`，case 可关闭为 `UNRECOVERABLE`，但不能把事实改写成未拨号；
- 运维人员可以给 case 添加安全处置结论或关闭告警，不能修改 provider 事实。需要再拨时走新的人工请求、当前授权、新 `requestId` 和新 interaction。

普通用户回执应表达“本地已接收”“拨号已确认”“拨号被拒绝”或“拨号结果不确定”，不能把 `AMBIGUOUS` 映射为普通 5xx 后诱导客户端自动重试。

## 7. 核心 fact inbox

### 7.1 envelope 与唯一性

每个规范事实必须携带 connector 内稳定的 `deliveryRef`；`sourceEventRef` 仍是可选诊断相关性。核心从 `AuthenticatedConnector` 解析并在事务内重锁活动 registration 后，自行派生 tenant、数据库 `receivedAt` 和 `normalizedDigest`。

digest 覆盖有界规范字段及专用敏感输入的 keyed digest，不保存或拼接完整号码、原始 callback、provider URL、SIP header 或厂商异常。相同 `(tenant, connector, deliveryRef)`：

- digest 相同：返回首次持久化的处理结果；
- digest 不同：返回永久 `DELIVERY_CONFLICT`，保留首次事实，触发安全告警，不以新内容覆盖；
- 不同 delivery 的语义重复：由 interaction 的单调状态、稳定外部引用约束和下游副作用唯一键收敛为 no-op，而不是依赖事件到达顺序。

### 7.2 短事务处理

首期固定使用“保存 inbox 结果与推进核心投影同事务”的模式：

```text
lock and recheck active connector registration
  -> insert or lock inbox delivery
  -> compare normalized digest
  -> validate tenant/connector correlation
  -> lock affected command/interaction/gap/case
  -> apply monotonic fact or persist safe permanent rejection
  -> commit inbox outcome and projection together
```

处理结果分为：

| 结果 | adapter 行为 |
| --- | --- |
| `APPLIED` | journal 项完成，可进入已确认保留 |
| `NO_CHANGE` | 合法晚到或语义重复，视为成功完成 |
| `DUPLICATE` | 同 delivery/digest 已有相同结果，视为成功完成 |
| `REJECTED_FINAL` | 规范无效、scope mismatch、引用冲突或 delivery conflict；journal 隔离该项并告警 |
| 未形成持久化结果的可重试故障 | 保持 journal pending，以相同 delivery 重投 |

永久业务拒绝必须以安全分类提交为稳定 inbox 结果，避免同一 poison fact 无限占用核心；数据库不可用、事务死锁耗尽、connector identity lookup 不可用等基础设施失败则整个事务回滚，不伪装成永久拒绝。

任何由事实触发的“一次性”下游动作，例如 interaction 终结后的 Activity 摘要，都必须有 `(tenant, interaction, effectKind)` 一类数据库唯一性或自己的 transactional outbox，不能从重复 inbox 调用普通 post-commit listener 来猜测一次性。

## 8. adapter durable ingress journal

### 8.1 通用 journal 协议

adapter 在验证 provider 原始来源并完成白名单规范化后，先持久化最小 journal 项，再对原始来源确认或异步向核心转发。journal 项至少拥有：

- connectorRef、不可复用的 journal identity 和稳定 `deliveryRef`；
- 可选 sourceEventRef、external session/leg correlation 和有界 fact kind；
- 规范 envelope digest、provider observed time、journal received time；
- `PENDING`、`LEASED`、`ACKED` 或 `QUARANTINED` 状态；
- attempt、next attempt、lease token/deadline 和最后一个安全错误分类；
- 仅在呼入发现等必要场景存在的独立加密敏感 payload 引用。

状态规则为：

```text
PENDING -> LEASED -> ACKED
                  -> QUARANTINED
LEASED --lease expired--> PENDING
```

瞬时 core 故障没有“重试若干次后丢弃”分支。journal 使用有上限的指数退避和抖动，超过告警阈值仍保留 pending。`REJECTED_FINAL` 才进入 `QUARANTINED`；运维处置不能原地修改 envelope 后复用相同 delivery，修正后必须产生新、可审计的投递。

source replay/cursor 的 checkpoint 只需在 journal 事务提交后推进，因为此时 adapter 已接管后续交付；adapter→core 的逐项完成则以 `ACKED` 表示。没有协议 cursor 的 PBX 不能从本地 journal sequence 反推 provider 事件完整性。

### 8.2 云 Webhook

云 adapter 必须先按原始 URL/body/header 验签，随后只持久化规范白名单字段。成功写 journal 后才可返回 provider 2xx；不能等待核心 CRM 查询、录音下载或完整 lifecycle 处理后才响应。

如果 journal 不可用或已达到 hard capacity，adapter 返回 provider 配置允许重试的失败响应，不能先返回 2xx 再把事实留在内存。provider 最终不再重试时形成 provider→adapter gap，核心 inbox 无法补救。

同一逻辑 callback 的 provider retry 必须命中相同 journal identity 或产生相同 `deliveryRef`。传输 attempt token 只有在 provider 明确承诺稳定语义时才能参与；否则 adapter 用经 spike 验证的有界语义键或首次 journal identity 固定 delivery。

### 8.3 PBX 事件流

PBX frame 在来源连接认证和白名单解析后立即写 journal。WebSocket/ESL 没有逐帧 durable ack，因此“frame 到达进程但在 journal commit 前崩溃”的窗口不能被消除；连接断开、owner 被替换、core/provider 世代变化或容量迫使断流时必须打开 observation gap。

每个逻辑 connector/stream 只有一个有效 owner。新 owner 取得租约时递增 fencing token；journal、checkpoint 和 session registry 的写事务都验证当前 token，旧进程失去租约后不能继续提交。连接世代只用于 gap/fence，不参与 tenant、principal、deliveryRef 或 `externalInteractionRef` 的身份推导。

如果上一 owner 没有提交可证明的 clean close，新 owner 必须在同一个 fenced control transaction 中先持久化 gap open，再把新世代标记为 ready；不能先消费新 frame，之后再尽力补写缺口。

### 8.4 journal 与 session registry 分离

journal 的一行只代表一次可重投的规范事实。跨 frame、跨 leg、跨重连的会话归并由 session registry 持久化：

- `(connector, providerSessionKey)` 只能映射到一个稳定 `externalInteractionRef`；
- provider leg key 在同 connector 内只能属于一个 session；
- 本地 `interactionRef` 绑定后不可改到另一 tenant/connector/session；
- core interaction 尚未创建的呼入 session 可以先存在，随后通过幂等 inbound discovery 绑定；
- terminal session 在 provider callback、对账和 journal 最大迟到窗口结束前保留 tombstone，不能因 adapter 重启生成新的 external interaction。

session registry 不保存无界腿图、原始 variables 或媒体路径。它只保存恢复 correlation 必需的最小 opaque key、状态和时间。

## 9. observation gap

gap 是可靠性事实，不是 interaction lifecycle 状态。它可以作用于 connector 时间窗口，也可以作用于一个已知 command/interaction。adapter 生成稳定 `gapRef`，以普通 durable journal/inbox 路径上报 open、coverage 和 close 事实。

规划 gap 状态为：

| 状态 | 含义 |
| --- | --- |
| `OPEN` | 已知可能缺失 provider 观察，尚未完成对账 |
| `RECONCILING` | 正在按声明能力查询或 replay |
| `CLOSED_RECOVERED` | adapter 以 replay/cursor 或等价证据证明缺口覆盖完整 |
| `CLOSED_PARTIAL` | 已补当前/终态事实，但无法证明中间里程碑完整 |
| `CLOSED_UNRECOVERABLE` | provider 无 replay/历史查询或恢复窗口已消失 |

连接恢复只允许 `OPEN -> RECONCILING`，不能直接变成 `CLOSED_RECOVERED`。活动资源 snapshot 通常只能关闭为 `CLOSED_PARTIAL`；ARI 一类无 replay 且查询不到已结束资源的缺口通常为 `CLOSED_UNRECOVERABLE`。

已知 interaction 的 observation assessment 从 gap 和 case 投影得到：

- `NOMINAL`：当前没有已知缺口；这个词不表示已证明完整；
- `RECONCILIATION_REQUIRED`：受 open gap、`AMBIGUOUS` 或观察 watchdog 影响；
- `RECONCILED`：已得到足以恢复当前/终态的证据；
- `OBSERVATION_INCOMPLETE`：缺口只能部分恢复或不可恢复。

gap 不会自动生成 `RINGING`、`CONNECTED` 或 `TERMINATED`，也不会因为超过预期时长把 interaction 判为失败。一个 connector gap 可能包含核心从未发现的呼入，因此对账必须支持 connector-level orphan discovery，不能只扫描已存在 interaction。

至少以下情况会打开 gap 或 reconciliation case：

- PBX stream disconnect、`ApplicationReplaced`、lease/fence 切换或 provider core generation 变化；
- required callback/recording subscription readiness 漂移；
- provider 有限 retry 已知耗尽，或 Webhook journal 因容量/持久化故障未能接收；
- `CONFIRMED` command 在 adapter 声明的观察期限内没有任何关联 lifecycle fact；
- 活动 interaction 超过有界 watchdog 仍无可证明终态；watchdog 只触发对账，不推断终态；
- dispatch 进入 `AMBIGUOUS`。

## 10. durable reconciliation

### 10.1 capability snapshot

adapter readiness 必须输出可审计、带 adapter 版本和检查时间的 reconciliation capability snapshot，至少区分：

- active object query；
- terminal/history query；
- replay/cursor 及其覆盖范围；
- CDR/CEL 或等价终态数据；
- query 的最终一致窗口；
- command correlation lookup；
- `AUTHORITATIVE_COMMAND_ABSENCE`；
- connector-level orphan discovery；
- provider 查询的最早可恢复时间和最大分页范围。

核心只按 case 创建时记录的 capability snapshot 解释结果。readiness 后续变好不会篡改旧 gap 的证据；可以创建新一轮 case 重新对账。

[B22-A](B22-A-adapter-readiness.md) 在此基础上固定完整的按操作 readiness snapshot、最长有效期、binding revision/sequence、隔离证据和配置漂移 gap；它不改变本节对旧 case 使用创建时 capability evidence 的规则。

### 10.2 case 和任务

reconciliation case 是 durable、幂等且有租约的核心记录。触发同一 subject/reason/evidence window 的并发 watchdog 只能创建一个 open case。规划状态为 `PENDING`、`RUNNING`、`RESOLVED`、`PARTIAL` 或 `UNRECOVERABLE`。

向 adapter 发出的 reconciliation 请求是只读命令，拥有独立 `reconciliationId`，可以至少一次重投；它绝不能调用 start、hangup 或其他改变 provider 通话的控制 API。adapter 对相同 ID/digest 返回相同查询进度或继续相同 cursor，不创建第二套会话。

adapter 必须把 `(connectorRef, reconciliationId, digest)`、provider 查询 cursor、已提交 page 和 coverage 进度持久化为独立 query ledger 或等价 durable 记录；不能只在内存中记住分页位置。相同 ID 不同 digest 永久冲突，adapter 重启后从最后已提交 cursor 继续，已发送 page 仍使用原 delivery identity。

adapter 通过 journal/inbox 返回有界事实页和最终 coverage 结果：

| 结果 | 核心语义 |
| --- | --- |
| `FACTS_RECOVERED` | 每个事实走普通 inbox，按单调规则应用 |
| `AUTHORITATIVE_ABSENCE` | 仅在 capability 和一致窗口都满足时可解决 AMBIGUOUS command |
| `CURRENT_STATE_ONLY` | 可修复当前/终态，不证明中间事件完整 |
| `NOT_YET_VISIBLE` | 在最终一致窗口内延期重试 |
| `UNAVAILABLE` | 基础设施/限流失败，保留 case 并退避 |
| `UNSUPPORTED` | case 关闭为不可恢复，不伪造业务事实 |
| `CONFLICT` | 保留相反证据并升级 reliability incident |

批次和分页都必须有固定上限、稳定 page/delivery identity 和最终 completion marker。completion 只有在此前所有页都已被核心 `APPLIED`、`NO_CHANGE` 或 `DUPLICATE` 后才可关闭 case，不能先关闭 coverage 再异步丢页。

### 10.3 对账不会补造历史

如果 provider 只返回“当前已结束”，核心可以保存一个带 reconciliation evidence 的规范 `TERMINATED` 事实及安全终止分类，但不能反推它曾经 `RINGING` 或 `CONNECTED`。如果 CDR/CEL 能证明接通区间，adapter 才可按公共拓扑规则提交对应事实。

人工处置可以：

- 确认已知外部引用并触发受控再查询；
- 标记 case 已知不可恢复；
- 在重新授权后创建一条全新的呼叫请求。

人工处置不能直接编辑 inbox、删除 `AMBIGUOUS` 证据、把 gap 改成完整、复用旧 `commandId` 或从任意号码发起“补偿拨号”。

## 11. 事务、锁顺序和停用竞态

核心侧统一锁顺序固定为：

```text
tenant/connector registration
  -> request binding or command/inbox delivery
  -> interaction
  -> gap/reconciliation case
```

只涉及人工请求时，caller/member 和 CRM 目标的既有锁顺序仍先按所属业务规则执行；一旦选择 connector，registration 必须在 interaction/outbox 写入前锁定并重新要求 active。connector ingress 继续遵循 B22 第 12 节：早期 guard 解析后，在写事务首先锁定同一 registration，再访问 inbox 或业务记录。

outbox claim 在同一短事务中锁定 registration、重新检查 active 和未过期的持久化 readiness/capability snapshot，再锁 outbox 并提交 lease；不得在该事务内同步调用 adapter readiness。adapter/provider I/O 全部在事务外。connector 停用/退役和 ingress/dispatch claim 因同一 registration 行形成全序：

- 停用先完成：不领取新 outbox，不接受新 ingress；
- ingress 或 claim 先完成：该次本地提交有效，但 provider 结果仍必须用原 connector identity 返回；
- 停用后的 adapter backlog 不改写 connector 或 tenant，恢复后用原 delivery 重投；退役 backlog 进入显式运维处置，不能迁移到新 tenant。

任何乐观锁冲突、死锁重试或 worker lease 过期都不能生成新 `requestId`、`commandId`、deliveryRef 或 external interaction。重试始终复用首次持久化 identity。

## 12. 容量、保留和敏感数据

### 12.1 不得 TTL 淘汰的记录

以下记录在未解决时不允许因 TTL、LRU 或容量压力删除：

- `READY`/`LEASED` core outbox；
- `RECEIVED`/`DISPATCHING`/`AMBIGUOUS` adapter command ledger；
- `PENDING`/`LEASED` journal；
- open gap、open reconciliation case；
- active session registry 和未完成 connector fence/checkpoint。

达到 high-water 时 adapter readiness 失败并停止领取新 start；云 Webhook 在无法 durable journal 时返回可重试失败，PBX 主动断开或施加协议允许的 flow control 并打开 gap。不得为了保持“健康”指标而删除最老 pending 记录。

journal/store 必须为 gap marker、fence 和安全关闭记录保留独立 control-plane 容量，避免数据区满后连“发生缺口”都无法持久化。每个 connector 分区有 row/byte high-water、hard limit、oldest-pending-age 和 quarantine limit；一个 connector 的洪泛不能淘汰另一个 connector 的未完成数据。

### 12.2 tombstone 与 payload

settled/acked 记录分成最小幂等 tombstone 和可删除 payload：

- request binding 在 interaction 的完整审计生命周期内保留；interaction 被受治理清理后仍保留下述最小 requestId/digest/result tombstone，删除或软删除 interaction 不会提前释放 key；
- core outbox、adapter command ledger、core inbox、adapter journal 和 session registry 的最小 identity/digest/outcome tombstone，至少保留 30 天，并且不得短于相邻组件声明的最大 redelivery、provider callback、reconciliation 和备份恢复窗口加安全余量；
- 若部署声明的任一窗口超过 30 天，所有相关 tombstone 使用其中最长窗口；readiness 在保留配置小于该窗口时失败关闭；
- unresolved `AMBIGUOUS`、不可恢复 gap 和 reliability conflict 按 interaction 审计生命周期保留，不因 30 天到期抹去不确定证据；
- 已 `ACKED` journal 的规范 payload 可以按更短受治理期限清理，但先保留上述 tombstone；`QUARANTINED` 只保存排障必需的白名单字段和安全分类。

deliveryRef、requestId、commandId 和 external session key 在各自 connector/tenant 作用域内都不得复用，即使 tombstone 已按策略清理。

### 12.3 敏感 payload

可靠性记录默认不保存完整号码、raw body/frame、dial string、channel variables、SIP header、recording URL/path、credential 或 provider 错误原文。

外呼 outbox 必须 durable 保存授权时目标快照，否则进程重启后无法完成已接收意图；该值只能位于独立加密 command payload，通过 key ID 与普通 outbox 元数据关联。呼入 journal 若必须在 core ack 前保留 `partyAddress`，也使用独立加密 payload。确定性匹配和 digest 使用 tenant/connector-scoped keyed HMAC，不使用可离线枚举的普通号码哈希。

B22-R 固定“必须加密、分离、最小化并可销毁”的可靠性要求，但不选择密钥托管、轮换实现或最终号码保留期限；这些后来由 B22-D 闭合。任何实现仍禁止以明文列实现 outbox/journal。

## 13. 可观测性和运维门槛

必须按 connector 分区暴露但不含敏感值的指标：

- core outbox ready/leased 数量、oldest age、lease expiry 和 delivery attempts；
- adapter ledger 各状态数量、`DISPATCHING` age、`AMBIGUOUS` 数量和 provider invocation count；
- journal pending/quarantined 数量、oldest age、容量利用率和 core rejection 分类；
- inbox applied/duplicate/no-change/rejected/delivery-conflict 数量；
- open gap、最长 gap、reconciliation case 状态、查询延迟和 coverage 结果；
- callback/readiness 配置漂移、stream owner/fence 变化、session correlation conflict。

日志只记录本地 interaction/command/delivery/gap/case identity、connectorRef 的安全表示、状态迁移和稳定分类。禁止记录完整 target、partyAddress、认证头、raw provider input、provider URL、recording reference 的可下载形式或任意异常消息。

readiness 不是“TCP 可连接”即可通过。至少要求：durable store 可提交、容量低于 hard limit、唯一约束和 lease/fence 可用、start guarantee 已声明、required callback/订阅有效、reconciliation capability 已探测。readiness 失败阻止新 start，但不停止 journal backlog、已有 case 和安全停用流程的恢复处理；完整 operation gate、隔离与漂移语义见 [B22-A](B22-A-adapter-readiness.md)。

## 14. 实现前契约测试门槛

### 14.1 核心 MySQL 集成测试

- 同 caller/key/digest 的串行与并发请求只提交一个 interaction 和一个 start outbox；改变 digest 稳定冲突；首次事务回滚后同 key 可成功重试；
- 幂等命中重新验证活动成员和入口能力，只返回最小首次回执；
- interaction、request binding 和 outbox 任一写失败时全部回滚；普通 after-commit event 失败不影响 durable outbox；
- outbox 多 worker 领取、lease 过期、旧 fencing token 完成和进程重启始终复用同一 commandId；
- connector 停用/退役与创建、claim、dispatch result、fact ingress 真实并发时遵循 registration 锁全序；
- inbox 相同 delivery/digest 返回同结果，不同 digest 永不覆盖；基础设施回滚不留下假 `REJECTED_FINAL`；
- callback-before-dispatch-result 能确认 command，晚到相同结果 no-op，相反结果产生 reliability conflict；
- 重复、乱序和 reconciliation 事实不倒退 interaction，不重复生成 Activity/outbox 副作用；
- gap/case 的确定性唯一键、租约、分页完成条件和 tenant/connector 复合外键阻止跨租户关联。

### 14.2 adapter durable contract tests

- command 在返回 durable receipt 前已写 ledger；同 command/digest 重投不产生第二条 ledger；不同 digest 永久冲突；
- 每个定义的崩溃点都可恢复：ledger insert 前、`DISPATCHING` commit 前后、provider 接受前后、结果写入前后、core response 丢失；
- `AT_MOST_ONCE_START` fixture 对任意崩溃/超时证明 provider start invocation count 不超过 1；不确定窗口落入 `AMBIGUOUS`；
- `IDEMPOTENT_START(window)` fixture 只在窗口内用相同 provider key 重试，窗口外不再调用；
- callback-before-command-response 经 session registry 稳定关联，adapter 和 core 重启后仍返回同一 externalInteractionRef；
- Webhook 只有 journal commit 后才 2xx；journal 不可用/容量已满时不 ack；不保存 raw body；
- PBX owner replacement、lease expiry、旧 fencing token、连接世代变化和 adapter 重启不会让旧 owner 写 checkpoint，也不会把 generation 当 session identity；
- journal lease expiry以相同 deliveryRef 重投，core transient failure 不丢弃，永久 rejection 进入 quarantine；
- high-water/hard-limit 不跨 connector 淘汰数据，并能使用保留 control capacity 打开 gap；
- tombstone 配置短于 callback/redelivery/reconciliation 最大窗口时 readiness 失败。

### 14.3 云/PBX 共同对账 fixtures

- 默认只回 completed、required callback 漏配、queued 取消无 callback 和 provider retry 耗尽形成 gap，而不是正常终态；
- PBX disconnect、无 replay、活动 snapshot、CDR/CEL 部分恢复分别关闭为 recovered、partial 或 unrecoverable 的正确状态；
- 非权威 `NOT_FOUND` 不能解决 `AMBIGUOUS`；最终一致窗口内只延期；只有 `AUTHORITATIVE_COMMAND_ABSENCE` 可收敛为 rejected；
- connector-level orphan discovery 能幂等创建漏掉的呼入 interaction；重复发现仍绑定同一 external session；
- reconciliation 只能调用 read/query/replay 能力，测试替身对任何 start/hangup 调用直接失败；
- recovered terminal 不补造 ringing/connected；录音事实仍可在呼叫终态后独立乱序到达。

## 15. 与 B22 其他前置的关系

B22-R 闭合 B22 第 11.2 节的“双重幂等”和“事件可靠性”门槛，并闭合 PBX durable journal、command ledger、session registry 的可靠性职责、容量与保留语义。B22-R 本身不定义以下内容；其中状态/腿与坐席设备后来由 [B22-S](spikes/B22-state-and-topology.md) 闭合，敏感数据由 [B22-D](../batch-B22.md#14-b22-d-敏感数据录音访问与保留设计闭合) 闭合，媒体传输由 [B22-M](spikes/B22-media-transfer.md) 闭合，真实 readiness/隔离设计由 [B22-A](B22-A-adapter-readiness.md) 闭合：

- interaction、多 leg、首期呼叫拓扑和 `CONNECTED` 的最终 domain 状态设计；
- 外呼字段/E.164、加密密钥托管、号码保留和销毁的完整敏感数据策略；
- 坐席/设备映射、呼入业务匹配和媒体访问授权/传输；
- 具体云/PBX adapter、connector 双向传输协议、API 路径或表结构；
- provider-specific readiness 实现和真实 fixture。

因此 B22 仍停留在前置设计阶段。B22-S/D/M/A 已补齐上述对应设计，[B22-CW](B22-CW-cloud-callback-fixture.md) 进一步闭合云 callback 的 duplicate、out-of-order、callback-before-response、两腿、录音、媒体取消和 readiness 漂移组合；[B22-CP](B22-CP-pbx-composed-contract.md) 后来把 PBX dirty reconnect/fencing、session registry、journal/inbox、两腿接通证明、订阅隔离和 gap/reconciliation 放入同一 deterministic trace；[B22-PC](B22-PC-pbx-command-contract.md) 又把 core outbox/adapter ledger、两种 start guarantee、provider crash、event-before-response、`AMBIGUOUS` 与 authoritative absence 放入同一 command oracle；[B22-PD](B22-PD-pbx-sensitive-data-contract.md) 再把 PBX 号码、key、retention、quarantine、泄漏和分层销毁放入同一敏感数据 oracle；[B22-PM](B22-PM-pbx-media-contract.md) 进一步把 PBX late stored recording、full/range、取消/上限、durable delete 与 confirmation 放入同一 media oracle。provider-specific 实现和真实 fixture 仍不存在，下一步须完成真实 canary，之后才可拆正式实现子批次。
