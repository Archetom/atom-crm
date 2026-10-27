# B27 Work 周期任务、提醒升级与可靠通知边界规划

> 基线：`038edbd6 feat(B26a): 实现统一调度与自动化内核`
>
> 对账日期：2026-07-22
>
> 状态：B27a/B27b/B27c 内核已实现；B27c 保持真实 provider adapter、credential 与生产 conformance 为独立后续交付；OA 公告接入见 [B28](batch-B28.md)

## 1. 结论

B27 在 B21 已实现的单次 Work 任务和 B26a 已实现的 durable 调度内核之上，补齐三个必须分开证明的事实：

1. **周期发生**：一个受治理的 `WorkTaskSeries` 在一个确定的本地日历时刻最多生成一个 Work 任务 occurrence；
2. **提醒发生**：一个任务的一个提醒步骤对一个当时仍合格的接收成员最多生成一条站内通知事实；
3. **外部投递**：一条站内通知可派生独立的通道 delivery，拥有自己的 outbox、尝试、幂等或歧义结果，但不把供应商受理描述成用户已收到或已读。

B26 继续只拥有跨副本唤醒、schedule/run、lease/fence 和本地执行事务。它不保存周期规则、任务模板、提醒接收人、通知正文、邮件地址或 provider payload。B27 的提醒 occurrence 和外部 delivery 已经拥有各自的 durable identity，因此只向 B26 composition-root pulse 注册有界 pump，不复制成 generic automation run。

**“任务创建成功”“站内通知已提交”“外部供应商已受理”“用户已读”是四个不同状态。** 任一前序状态都不能推导后序状态，普通 post-commit event 也不能替代其中任何一个 durable 边界。

## 2. 当前事实盘点

| 能力 | 当前事实 | B27 决策 |
| --- | --- | --- |
| B21 Work 任务 | 单责任人、最多 50 个关注人、可选 UTC 截止时间、固定状态机、任务参与可见性、评论和一个可选 CRM 关系均已实现 | 继续把每个生成结果保存为普通 `WorkTask`；series 不代理 occurrence 状态 |
| B15 工作台 | 当前责任人的当天到期任务以 `WORK_TASK` 来源出现，确认指纹使用任务投影版本与 due time | 保持不变；周期生成的任务自然进入现有来源，工作台确认不确认提醒或通知 |
| B26a 调度内核 | 两个固定 pool kind、MySQL UTC occurrence、lease/fence、稳定 evaluation time、coalesced misfire 和统一 pulse 已实现 | B27a 增加 closed `WORK_TASK_RECURRENCE` kind；提醒和 delivery 使用自己的 durable pump |
| Work 周期定义 | 尚无 series、时区、DST、月末、occurrence identity 或重复任务来源 | B27a 新建 Work-owned series，不把 RRULE/cron/payload 塞入 B26 schedule |
| 自动提醒与升级 | 只有工作台按日投影；没有 task reminder plan、步骤 occurrence 或升级接收人 | B27b 以 due time 为锚点创建有界步骤；升级只扩大通知对象，不改变任务状态、优先级或责任人 |
| 站内通知 | OA 公告 inbox 是公告自己的接收人快照；不能作为通用通知表复用 | B27b 新建 self-only 通知 inbox，首个来源只允许 Work reminder/escalation |
| 外部通知 | `UserNotificationPort`、`UserEventListener` 和非生产 logging adapter 只是示例性的进程内 post-commit 路径 | 明确不复用、不宣称可靠；B27c 使用 transactional delivery outbox 和独立 adapter contract |
| 通知地址与偏好 | 没有 tenant/member 通道偏好、退订、当前 endpoint 解析或 provider readiness | B27c 只允许服务端从当前活动成员及其已验证资料解析 endpoint；普通请求不能提交地址 |

## 3. 子批次拆分

| 子批次 | 交付物 | 依赖 | 计划迁移 |
| --- | --- | --- | --- |
| B27a | `WorkTaskSeries`、有界日历规则、DST 解析、single-open occurrence、B26 fixed kind 接入 | B21a/B21b、B26a | V67 |
| B27b | task/series reminder plan、durable reminder occurrence、升级接收人、站内通知 inbox/read | B27a 可选；单次任务可独立使用 | V69 |
| B27c | member 通道偏好、typed delivery outbox、lease/fence、readiness、幂等/歧义协议和 EMAIL contract | B27b、B26 unified pulse | V71 |

B27a 与 B27b 都必须形成可独立使用的本地 MySQL 闭环。B27c 即使没有安装真实 provider adapter，也不能削弱站内通知；外部通道默认 `NOT_READY`，而不是把 logging adapter 当作成功投递。

## 4. 分层与对象所有权

### 4.1 Work domain

`WorkTaskSeries` 是 Work 域的新版本化聚合。它拥有任务模板、当前责任人、可选关注人模板、周期规则、时区、下一 occurrence、series generation、状态和总生成数。它不是 B26 schedule PO，也不是一组 `WorkTask` 的聚合容器。

每个已生成 occurrence 仍是独立的 `WorkTask`。任务增加只读来源值 `WorkTaskOrigin`，区分 `MANUAL` 与 `RECURRENCE`，并在周期来源时保存 series ID、series generation、occurrence sequence、名义本地时间、解析 offset 和 resolved UTC due time。`WorkTaskSeriesOccurrence` 是独立、可分页的 MATERIALIZED/SKIPPED 发生事实，不作为无界子集合水合进 series。任务状态、评论、附件、CRM 关系、改派和完成都继续由该任务根拥有；修改 occurrence 永远不反写 series。

`WorkRecurrenceRule` 是 domain value object，只允许第 6 节的 closed rule。`WorkTaskReminderPlan` 与 `WorkTaskReminderStep` 是任务拥有的有界策略；series 只保存 future occurrence 要复制的 reminder template。它们不是 cron、脚本或 provider 消息模板。

### 4.2 Application control plane

B27a 扩展现有 closed `AutomationKind`，只增加 `WORK_TASK_RECURRENCE`。`AutomationExecutionContext` 仍只携带 claimed tenant、kind、run/fence 和稳定 evaluation time，不增加 series、actor、规则 JSON 或消息 payload。

当前 B26 `AutomationPolicy.poolCadence` 必须先收敛为 code-owned per-kind policy catalog；pool kind 的 cadence 和上限保持兼容，Work recurrence 使用自己的固定 cadence 和 record limit。全局 monotonic revision 继续使用兼容旧副本的 `B26A_Vn` 格式并升级到新 revision，使不认识 B27 kind 的旧副本只能 `REVISION_DRIFT/NOT_READY`，不能 claim 新 run。V66 已应用的 control row 不改写为历史迁移；必要约束变更只进入 V67。

提醒 occurrence 与 notification delivery 各自拥有 exact identity、状态和恢复协议。它们实现独立 `BackgroundWorkPump`，由统一 pulse 唤醒，但不进入 `crm_automation_run`，也不让 `AutomationHandlerRegistry` 解释提醒步骤或 provider 状态。

### 4.3 Notification boundary

`Notification` 是 tenant/member-scoped 的站内事实，首期 source catalog 只有 `WORK_TASK_REMINDER` 和 `WORK_TASK_ESCALATION`。`NotificationDelivery` 是 application-owned external-effect ledger；一个 notification 可以有零或一个首期 `EMAIL` delivery。站内已读状态不属于 delivery，provider receipt 也不属于 notification read receipt。

`NotificationChannelAdapter`、`NotificationReadinessSnapshot`、`NotificationDispatchCommand` 和 `NotificationDispatchResult` 属于 `application.port.out.notification`。HTTP client、provider account、message ID、endpoint、header、SDK response 和错误正文终止在 `infra/external`。

## 5. 人工 API、能力与可见性

### 5.1 周期任务

计划路由为：

```text
GET/POST /api/v1/work/task-series
GET/PUT/DELETE /api/v1/work/task-series/{seriesId}
GET /api/v1/work/task-series/{seriesId}/occurrences
POST /api/v1/work/task-series/{seriesId}/activate
POST /api/v1/work/task-series/{seriesId}/pause
```

- 继续使用 `work-tasks:read/write/manage`，不增加可绕过任务域的 `automations:*`；
- 普通 `read` 只能读取自己是创建者、当前责任人或当前关注人模板的 series；`read + manage` 才扩大到 tenant；
- 创建者或同时具有 `write + manage` 的成员可以修改、启停和软删除；当前责任人不因被分派而自动获得 series 管理权；
- 所有写入从 `AuthenticatedCaller` 派生 tenant，并在事务内重新校验当前活动成员；请求不能提交 tenant、创建者、system actor 或 occurrence identity。

series 激活与每次 occurrence 都通过专用的当前授权投影重新解析创建者的 `work-tasks:read + work-tasks:write`，以及责任人的 `work-tasks:read + work-tasks:write`；解析过程不构造 `AuthenticatedCaller`。任一必需能力或活动成员资格被撤销都会暂停 series，防止后台继续借用历史权限生成任务。关注人模板只在该成员当前活动并具有 `work-tasks:read` 时进入新任务，失效关注人按固定规则省略且不扩大其他人的可见性。

### 5.2 提醒与通知

计划路由为：

```text
GET/PUT /api/v1/work/tasks/{taskId}/reminders
GET/PUT /api/v1/work/task-series/{seriesId}/reminders
GET /api/v1/notifications
POST /api/v1/notifications/{notificationId}/read
GET/PUT /api/v1/notifications/preferences
```

- task reminder plan 是结构配置，沿用任务创建者或 `work-tasks:write + work-tasks:manage` 的结构管理规则；
- series reminder template 使用同一 series 管理规则，只影响未来 occurrence；
- personal inbox、幂等已读和个人通道偏好使用新的 `notifications:read` 入口能力，但永远只操作当前成员自己的行；
- notification 查询还必须重新证明当前活动成员和所属 Work 任务可见性。失权通知不占页、不进入 total，也不返回 task ID、标题或 due time；
- 不提供 public send、任意 recipient、任意 address、任意 template、群发、管理员代读或 provider retry API。

## 6. B27a 周期规则与时间语义

### 6.1 Closed recurrence catalog

首期只允许一个本地执行时间和以下规则：

| 规则 | 参数 | 固定语义 |
| --- | --- | --- |
| `DAILY` | interval 1..365 | 从 start local date 起每 N 天 |
| `WEEKLY` | interval 1..52；1..7 个 weekday | 以 start 所在 ISO week 为锚，每 N 周在有序 weekday 发生 |
| `MONTHLY` | interval 1..24；day 1..31 或 `LAST_DAY` | day 超出当月时固定 clamp 到月末；下一月仍使用原 anchor day |

series 必须保存 IANA zone ID、local time、start local date、可选 end local date 和固定 overlap policy。首期不接受 RRULE、cron、秒级时间、节假日日历、工作日表达式、农历、任意 skip list 或客户端 offset 代替 zone ID。

硬上限计划为每 tenant 最多 1,000 个未删除 series、每 series 最多 50 个关注人模板和 10,000 个 occurrence。达到 occurrence cap 后 series 进入 `ENDED`，不能静默回绕 sequence。

### 6.2 DST 与 tzdb

- 正常本地时间映射到唯一 UTC instant；
- spring-forward gap 固定使用 `SHIFT_FORWARD`：移动到 transition 后第一个有效本地时间；
- fall-back overlap 固定选择 earlier offset，并把实际 offset seconds 与 resolved UTC 固化到 occurrence；
- month clamp、gap shift 或 overlap choice 都必须在 API 输出中通过名义 local time、zone、offset 和 resolved UTC 可解释，不只返回节点计算后的裸 UTC；
- mixed replicas 必须提供相同的 recurrence policy revision 和 tzdb fingerprint。V67 的 recurrence readiness control 保存部署声明的 fingerprint；不一致、未知 zone 或规则漂移使 Work recurrence lane `NOT_READY`，普通 Work 请求仍可用。

未来 tzdb 更新只能影响尚未物化的 occurrence。已经生成的任务保留当时的 zone/offset/due snapshot；不得在启动时批量重写历史任务。

### 6.3 Misfire、open overlap 与 occurrence identity

首期策略固定为 `COALESCE_LATEST + SKIP_IF_OPEN`：

1. 长停机后不为每个错过时点建立 backlog；在稳定 evaluation time 下计算最新一个已到期名义 occurrence，并直接推进到第一个未来 occurrence；
2. 如果同一 series 已有 `TODO/IN_PROGRESS` 的 generated task，本次到期只记录有界的 skipped count/last safe reason，不生成第二个 open task；
3. 如果没有 open occurrence，series cursor、唯一 occurrence 行、普通 WorkTask 根和 B26 run success 在同一事务提交；
4. 数据库唯一键同时约束 `(tenant, series, generation, occurrenceSequence)` 和每 series 最多一个 generated open-task slot；应用预查不是并发真相；
5. retry 复用同一 B26 run evaluation time。任务 due time 保留名义 occurrence 的 resolved UTC，created/updated time 使用最终数据库提交 UTC。

一个生成数为零的 run 可以成功。成员失活或模板确定性失效不是无限重试错误：创建者或责任人失活会把 series 自动暂停并记录固定 safe reason；失活关注人只从该次 occurrence 的关注人快照中省略。数据库、时区目录或未知代码故障回滚并按 B26 policy 重试。

### 6.4 Series 生命周期

series 状态固定为 `DRAFT`、`ACTIVE`、`PAUSED`、`ENDED` 和 `DELETED`。激活重新验证规则、creator、assignee、关注人、tenant capacity、B26 readiness 与 next occurrence。修改规则、时区、模板、责任人或关注人会增加 series generation，只影响未来任务；既有 occurrence 不改派、不重命名、不删除，也不重新计算 due time。

暂停/删除只停止未来生成。删除使用 `status=DELETED`，不会级联删除 WorkTask、评论、附件或通知。重新激活从当前数据库 UTC 计算下一 occurrence，不补发暂停期间的任务。

首期 series 模板不包含 CRM 业务关系、附件、评论、FILE、子任务或任意请求 payload。生成后，具有既有 B21 权限的成员可以通过普通任务 API 显式添加关系或附件。

## 7. B27b 提醒计划与升级语义

### 7.1 有界 reminder plan

一个有 due time 的 task 最多保存 8 个去重步骤：

- `BEFORE_DUE`：提前 1 分钟到 30 天；
- `AT_DUE`：offset 为零；
- `OVERDUE`：逾后 1 分钟到 30 天。

每步只选择 `ASSIGNEE`、`CREATOR`、`FOLLOWERS` 的非空组合。接收人必须来自 B21 已有任务参与关系，升级不能把任意成员、部门、角色或外部地址变成任务读者。`OVERDUE` 只是使用更明显的 notification kind 并可扩大到 creator/followers；它绝不自动提高 priority、改派、开始、完成、取消、评论或写 Activity。

reminder effect 对每个候选接收人重新取 `active membership ∩ notifications:read ∩ work-tasks:read ∩ current task participation`。不满足交集的成员不生成 Notification，也不进入 external delivery；能力或成员关系不能从 reminder plan 的历史快照恢复。

无 due time 的任务不能保存 active reminder plan。清空 due time、完成、取消或删除任务会在同一任务事务中取消所有尚未提交的 reminder occurrence。改变 title、priority、assignee、followers、due time、status 或 reminder plan 会增加独立 `notificationProjectionVersion`；description、评论、CRM 关系和附件不增加该版本。

series reminder template 在 occurrence 创建事务中复制为任务自己的 plan。后续 series 编辑不改写已生成任务的步骤；任务自己的 plan 编辑也不反写 series。

### 7.2 Durable occurrence 与竞态总序

每个 reminder occurrence 的 identity 固定为：

```text
(tenant, taskId, notificationProjectionVersion, stepKey)
```

创建/更新任务计划时，在任务根锁内取消旧 pending rows 并插入新版本步骤。worker 先以短事务 claim exact due row 并写 lease/fence，再在 effect 事务中按 task root → reminder occurrence 顺序重读任务，验证 tenant、版本、due、状态、接收人和 fence。每个合格 recipient 的 `Notification` 与 occurrence terminal state 在一个 MySQL 事务中提交。

如果任务变更先提交，旧 occurrence 只能 `CANCELLED`；如果 reminder effect 先提交，该通知是变更前合法事实，随后任务事务可以撤销尚未 admission 的外部 delivery。该总序不等待外部 I/O，也不承诺收回 provider 已受理的消息。

### 7.3 与 B15 工作台的边界

工作台 `WORK_TASK` 条目仍由 due date、当前责任人和 worklist projection version 计算。它不是 reminder occurrence，也不产生 notification delivery：

- 确认工作台条目不取消提醒、不标记通知已读、不完成任务；
- 标记通知已读不写 worklist acknowledgement；
- 新生成 occurrence 复用现有 Work 来源和 cursor 版本；一个已开始的 cursor 链不回插更早的新任务，成员开启无 cursor 的新查询时即可看到；
- reminder/notification 表不参与工作台排序、total 或确认指纹。

## 8. 站内 Notification inbox

B27b 的站内通知以 MySQL 为真相。一个 reminder step 对一个 recipient 最多插入一条 notification，唯一键包含 exact reminder occurrence 和 member。通知只保存固定 kind/template revision、task ID、task notification version、bounded title snapshot、priority、due time、recipient 和创建时间；不保存 description、评论、CRM relation、附件名/内容、请求 body 或任意 JSON template variables。

inbox 只返回当前活动 member 自己、状态仍可见且所属 task 仍通过 B21 当前参与谓词的通知。任务改派、关注人移除或任务删除时，任务事务尽可能把已受影响通知标记为 `WITHDRAWN`；成员失活、能力撤销或其他未同步写入仍由查询的当前 SQL 谓词失败关闭。普通查询必须在分页和 total 前排除这些行，不能把 `WITHDRAWN` 变成旧任务标题或 ID 的旁路。

已读写入第一次数据库 UTC，重复调用幂等，不改变任务、reminder occurrence、外部 delivery 或工作台确认。`UNREAD/READ` 是 member inbox 状态；`WITHDRAWN` 是当前不可投影状态，不等于 provider 撤回或物理删除。

## 9. B27c 外部可靠投递

### 9.1 通道、偏好与 endpoint

首个外部 channel catalog 只预留 `EMAIL`。SMS、IM、push、Webhook 和 calendar 不通过字符串动态加入。有效 delivery 是以下交集：

```text
current active recipient membership
  ∩ notifications:read
  ∩ current Work-task visibility
  ∩ deployment channel enabled/readiness
  ∩ tenant channel policy
  ∩ current member opt-in preference
  ∩ server-resolved verified endpoint
```

personal preference 默认 external `OFF`，站内 inbox 不可通过 external opt-out 关闭。普通 API 只能修改当前成员自己的 typed preference，不能提交 email/phone/URL、provider、account、tenant 或其他 recipient。endpoint 在 dispatch admission 时从受信身份资料解析并只以短生命周期专用值交给 adapter；首次跨 dispatch boundary 后只保存带 tenant 与用途域隔离的 keyed-HMAC binding digest 及其 key version，以保证 idempotent retry 仍是 exact command 并支持密钥轮换。普通 outbox、日志、metric 和 Notification API 不保存或返回完整 endpoint。

### 9.2 Transactional delivery outbox

notification effect 与其零或一个 EMAIL delivery row 在同一事务创建；disabled 或 invalid dispatch deployment 不创建 row，但仍运行历史 row 的 deadline/retention maintenance。delivery 至少保存 notification ID、recipient、channel、template revision、canonical payload digest、首次 dispatch 后的 endpoint binding digest/version、effect certainty、状态、attempt、lease owner/fence、deadline、available time 和固定 safe reason。payload 只由第 8 节的固定字段生成，不能保存任意 HTML、header map、provider JSON 或业务 description。

状态固定为 `READY`、`LEASED`、`DISPATCHING`、`RETRY_WAIT`、`ACCEPTED`、`REJECTED`、`SUPPRESSED`、`AMBIGUOUS` 和 `DEAD`：

- `ACCEPTED` 只表示 adapter/provider 明确受理，不表示到达邮箱、展示、点击或阅读；
- `REJECTED` 是确定性未受理；
- `SUPPRESSED` 表示尚未跨 dispatch boundary 的 admission 时权限、偏好、endpoint、task 状态或 readiness 已不满足，且不会调用 provider；已有 guarantee/dispatch history 的同类变化必须保留为 `AMBIGUOUS`；
- `AMBIGUOUS` 表示 effect 是否发生未知，默认不自动创建 replacement delivery；
- `DEAD` 表示在没有已知 provider effect 的阶段耗尽有界重试或 deadline。

### 9.3 Dispatch guarantee profiles

adapter readiness 必须逐 operation 声明以下一种 guarantee：

1. `IDEMPOTENT_SEND(window)`：provider 接受由 delivery ID 与 payload digest 导出的稳定 idempotency key，core 只在原 window 内重试 exact command（包括冻结的 endpoint digest）；
2. `AT_MOST_ONCE_SEND`：进入 durable `DISPATCHING`/effect boundary 后不自动重发；进程崩溃或 timeout 产生 `AMBIGUOUS`；
3. `UNSUPPORTED`：不 claim delivery。

不存在 generic “exactly once email”。worker 在 claim 前预检 readiness，并在 admission 事务按 task → notification → delivery 重验当前资格并冻结 exact attempt；`UNSUPPORTED` 和临时 not-ready 不夺取或终态化待投行。外部 I/O 在事务外执行，adapter 只获得 `min(leaseUntil, deadlineAt) - DB now - settlement reserve` 的实际预算并必须支持 interruption/cancellation；provider 调用后的 settlement 丢失使 lane 失败。任务变更在 admission 之前提交会 suppress 尚未跨 boundary 的 delivery，已有 guarantee/history 的记录一律保守结算 `AMBIGUOUS`，不会伪称撤回已开始的 provider effect。回执提交丢失时，只有 `IDEMPOTENT_SEND` 可在相同原 window 内安全复用原 key/digest/HMAC endpoint binding；endpoint 漂移或 dispatch timeout 不创建 replacement delivery。

readiness snapshot 绑定 channel、adapter contract/build、binding revision、guarantee、template catalog revision 和不超过五分钟的正 TTL。缺失、过期、漂移、synthetic-only 或 logging adapter 都是 `NOT_READY`。readiness probe 只能做无用户副作用的 capability/configuration 检查，不能发送真实邮件。

### 9.4 现有示例通知的明确隔离

`UserEventListener -> UserNotificationPort -> LoggingUserNotificationAdapter` 是生成仓库的示例 post-commit 路径。它可能在 commit 后进程退出时丢失，logging adapter 也明确没有投递。B27 不把它注册为 `NotificationChannelAdapter`、不从它生成 readiness、不让它消费 Work delivery，也不因它存在而启用 production notification。

如果未来 welcome notification 需要可靠性，应另行把该来源迁入 typed notification/outbox，并给出用户身份、去重、偏好和内容保留规则；这不是 B27 Work 纵切的完成条件。

## 10. Tenant、授权与敏感数据

- human series/reminder/preference/read commands 都接收显式 `AuthenticatedCaller`；background context 不伪造 caller、owner 或管理员；
- recurrence tenant 只来自 claimed B26 run；reminder/delivery tenant 只来自 exact foreign-keyed durable row；每次进入业务表后所有查询与写入显式 tenant scoped；
- series creator 是人工接受配置的归属事实，不是后台执行主体。每次 occurrence 都以专用授权投影重新证明其当前 Work read/write，而不是构造 caller；生成任务同时保存 `origin=RECURRENCE`，不能把 lease owner、scheduler 或 tenant owner 写入 `created_by_actor_id`；
- reminder targets 只能从当前 task participant 关系解析，CRM 可见性、通知能力和 tenant 管理能力都不能凭空增加 recipient；
- title 是允许进入固定通知 payload 的唯一自由文本，仍按 200 Unicode code point 上限；description、评论、附件、CRM 字段和关系不进入 delivery；
- endpoint、credential、provider account/message ID、错误正文和 response body 不进入普通 API、日志、trace、metric、Activity、domain event 或 automation run；
- public not-found 对跨 tenant、不可见 task/series/notification 使用相同安全语义。

## 11. 事务与锁序

所有会相遇的路径共享以下相对顺序；不经过某层时直接跳过，不能反向取得：

```text
organization_tenant
  -> automation schedule
  -> exact automation run
  -> work task series
  -> work task root
  -> reminder occurrence
  -> notification
  -> notification delivery
```

- series 配置写：organization tenant → schedule → series；首个/最后一个 active series 在同事务激活/暂停 `WORK_TASK_RECURRENCE` schedule；
- recurrence effect：organization tenant → schedule/run fence → due series ordered by ID → generated task；series cursor、task 和 run terminal 同事务；
- 普通 task mutation：organization tenant → task → reminder → pending notification/delivery cancellation；保持 B21 既有 tenant → task 顺序；
- reminder claim 是独立短事务；effect 重新从 task root 开始，再锁 exact leased occurrence；
- delivery claim 是独立短事务；admission 重新从 task root 开始，再锁 notification/delivery；provider I/O 绝不持有 MySQL transaction；
- comment、attachment 和 CRM relationship 不加入 reminder/delivery payload，也不为通知改变 B24 或 B21 的锁序。

`CommandServiceTemplate` 继续拥有 human 和本地 effect 事务。handler/coordinator 不调用 public command service，不嵌套 `REQUIRES_NEW`，也不在事务内调用 provider。

## 12. 持久化与迁移计划

B27 只能新增迁移，不能修改 V53、V56、V65 或 V66：

### V67 — Work recurrence

- `crm_work_task_series` 与有界 follower template；
- `crm_work_task_series_occurrence` 的 MATERIALIZED/SKIPPED 事实、可选 generated task link、精确 occurrence 唯一键与分页索引；
- series generation、next nominal local/offset/resolved UTC、occurrence sequence、skip counters、状态/version 和 tenant/member 复合外键；
- `crm_work_task` 的 typed recurrence origin、series/occurrence 唯一约束和 generated open-slot 仲裁；
- 扩展 `crm_automation_schedule/run` kind CHECK，升级兼容格式的 global cadence revision，并增加 recurrence/tzdb readiness control；
- `(tenant, status, next_due_at, id)` 与 generated open lookup 索引。

### V69 — Reminder 与站内通知

- task reminder plan/step、series future reminder template、task `notification_projection_version`；
- durable reminder occurrence 的 due/claim/fence/terminal/cancellation/retention 索引；
- `crm_notification` 的 exact source-recipient 唯一键、recipient inbox/read/withdrawn 索引和 task same-tenant proof；
- 所有集合继续使用 typed child rows，不把规则、targets 或 payload 存成 executable JSON。

### V71 — External delivery

- current-member typed notification preference；
- `crm_notification_delivery` 的 channel、payload digest、lease/fence、attempt/deadline/result 与 active claim/retention 索引；
- terminal delivery 的最小幂等 tombstone 与 payload-field purge deadline；
- 不保存 plaintext endpoint、credential、provider request/response、header map 或 exception text。

### V72 — Frozen endpoint binding

- `crm_notification_delivery.endpoint_digest` 在 V72 仅保存首次 dispatch 的 binding digest，供后续 exact-command 检查；
- digest 不可逆、不可由 API/日志/metric 返回，并受固定十六进制约束。

### V73 — Effect certainty and endpoint-binding hardening

- 将 endpoint binding 升级为 tenant/purpose keyed-HMAC，并保存 `endpoint_digest_version` 与 effect certainty，供后续 exact-command 检查和密钥轮换；

取消的 reminder occurrence、terminal delivery 和内容 snapshot 使用固定小批量物理 retention。删除前必须保留足以阻止旧版本重复 emission/dispatch 的版本或 tombstone；无限历史不能充当审计系统。delivery terminal retention 不能超过 notification 内容保留期，Notification inbox 的产品保留期与 delivery 运行账本分别配置并有代码绝对上限。

## 13. 配置、readiness 与可观测性

计划配置面分离为：

- `atom.work.recurrence.*`：enabled、fixed cadence、每 run series limit、series/occurrence hard limit、policy/tzdb revision；
- `atom.notifications.reminders.*`：pump concurrency、claim size、lease、deadline、attempt、backoff、terminal retention；
- `atom.notifications.channels.email.*`：enabled、adapter contract/build/binding policy、readiness TTL、endpoint-binding active key version/keyring、guarantee、delivery limits、settlement reserve 与小于等于 lease 的 cancellation-aware dispatch deadline；
- `atom.notifications.retention.*`：inbox、delivery metadata、payload snapshot 和 tombstone 的分别期限。

production external channel 默认 disabled。非法 recurrence 配置只使 Work recurrence `NOT_READY`；非法 EMAIL 配置只使 EMAIL delivery `NOT_READY`；普通任务、工作台和站内通知不能因此不可用。`atom.scheduling.enabled=false` 时不生成周期任务，但 human task API 仍可写 series 为 `DRAFT/PAUSED`，不能伪装 active execution。

metrics 只使用 fixed kind/channel/outcome/status label，覆盖 recurrence due lag/materialized/skipped/paused、reminder claimed/emitted/cancelled/dead、notification unread/read/withdrawn 计数和 delivery accepted/rejected/suppressed/ambiguous/dead。deadline 维护与任务变更的 `SUPPRESSED`/`AMBIGUOUS`/`DEAD` 分类计数只在所属事务成功提交后记录。不得用 tenant、member、series/task/notification/delivery ID、title、endpoint、provider message ID 或异常类型/原文作为 label。

日志至多包含 fixed kind/channel、opaque occurrence/delivery ID、attempt/fence 和 safe reason。受控 health 只给出 fixed-cardinality readiness 与有界 ledger census，不提供 public retry/send 或跨 tenant 运维列表。

## 14. B27a 实现任务卡

1. 增加 series aggregate/value objects、Request/VO/Response、权限守卫与 public routes；
2. 实现 V67、PO/converter/mapper、series page/detail、tenant capacity、same-tenant member 约束和 occurrence unique/open-slot；
3. 把 B26 policy 收敛为 per-kind catalog，新增 `WORK_TASK_RECURRENCE` handler、全局 revision 升级、tzdb readiness 和 active-series schedule 同步；
4. 实现 DAILY/WEEKLY/MONTHLY、gap/overlap/month-clamp、coalesced downtime、single-open、generation 和 future-only edit；
5. 用现有 Work application coordinator 在同一 effect transaction 创建普通任务，不能调用 public `WorkTaskCommandService` 或伪造 caller；
6. 更新 README/API/configuration/architecture/object-layering/test-guide/`llms.txt` 并增加 `docs/acceptance/B27a.md`。

## 15. B27b 实现任务卡

1. 增加 task/series reminder plan、8-step 校验、participant-only targets 和 notification projection version；
2. 实现 V69 reminder occurrence、claim/fence、task mutation cancellation 和固定 retention；
3. 实现 Work-only Notification aggregate/inbox/read、当前 task 可见性 SQL 和 exact recipient uniqueness；
4. 将 series reminder template 在 occurrence transaction 中复制，证明 series/task edits 相互不回写；
5. 证明工作台 acknowledgement、notification read、task state 和 reminder terminal 四者完全独立；
6. 增加 `docs/acceptance/B27b.md` 和对应公共文档。

## 16. B27c 实现任务卡

1. 增加 `NotificationChannelAdapter`、readiness、guarantee、typed dispatch command/result 和 explicit registry；
2. 实现 V70 member preference、delivery outbox、claim/fence/retry/ambiguous/dead 和 retention/purge；
3. 实现 task-first dispatch admission、当前 endpoint 解析与首次 endpoint binding digest、固定 template revision/digest、deadline-bounded cancellation-aware 事务外 I/O；
4. 提供 disabled production adapter 与 synthetic dev/test conformance adapter；logging welcome adapter 不参与 registry；
5. 增加 provider-independent crash oracle，分别运行 `IDEMPOTENT_SEND` 和 `AT_MOST_ONCE_SEND` profile；
6. 增加 `docs/acceptance/B27c.md`；真实 provider adapter 只有在 current-build readiness/conformance 完整时才能单独启用。

## 17. 验收门槛

B27 完成必须同时证明：

1. DAILY/WEEKLY/MONTHLY 在月末、leap day、gap、overlap 和 tzdb/revision 漂移下产生文档定义的 exact nominal/offset/UTC；
2. 两副本、materialize/claim/effect/commit acknowledgment crash cuts 下，同一 series occurrence 最多有一个 WorkTask；stale fence 不能推进 series 或提交 task；
3. 长停机只 coalesce 最新 due occurrence，暂停期不补发，single-open 不堆积任务，series edit 只改变未来 generation；
4. creator/assignee 失活或丢失必需 Work 能力会暂停 series，失活/失权关注人只按固定规则从新 occurrence 省略，跨 tenant member、series capacity 与 10,000 occurrence cap 均失败关闭且不伪造 actor；
5. manual 与 generated task 继续遵守相同 B21 read/write/manage、状态、评论、附件、CRM 关系和 worklist 规则；
6. reminder step 只为同时满足活动成员、`notifications:read`、`work-tasks:read` 和当前参与关系的成员生成通知，任务完成/取消/改派/改 due/改 follower 与 worker 竞争遵守 task → occurrence 总序；
7. 同 `(task, notificationVersion, step, recipient)` 在 retry、lease takeover 和 commit acknowledgment 丢失后仍只有一条站内通知；
8. escalation 不修改 priority、assignee、status、Activity、worklist acknowledgement 或 CRM 记录；
9. inbox SQL 在 count/page 前应用 tenant、active membership、recipient 和当前 Work visibility；失权行不占页且不能泄露 task/title；
10. notification read、worklist acknowledgement、task completion 和 external delivery 状态彼此不代写；
11. external delivery 在 provider 调用前完成 current membership/capability/task/preference/endpoint/readiness 交集；任一缺失产生零 provider call；
12. idempotent profile 只复用 exact delivery key/digest/window，at-most-once profile 在 dispatch boundary 后不自动重发；timeout/crash 的未知效果为 `AMBIGUOUS`，不是 success 或 replacement；
13. `ACCEPTED` 从不映射为 delivered/read，bounce/open/click 等未实现事实不会被合成；
14. logs、metrics、outbox、run rows 和 conformance reports 不含 endpoint、title、description、评论、CRM/附件值、credential、provider payload/response 或异常原文；
15. V67/V68（B27a）及计划中的 V69/V70 empty-schema、V66 upgrade、旧副本 revision drift、索引/外键/CHECK、retention 与 MySQL lock oracle 通过；
16. `sh ./mvnw test` 与 `CI=true sh ./mvnw test` 全部通过。B27 涉及 schema、权限、事务、调度和外部-effect 边界，Docker/MySQL 套件是必需门槛。

## 18. 明确非目标

B27 不包含：

- arbitrary cron/RRULE、条件树、SQL、脚本、低代码 workflow 或客户上传 job class；
- 多责任人、子任务、依赖图、检查清单、项目/看板/甘特、SLA 计时或值班轮转；
- 自动改变 priority/status/assignee、自动评论、自动创建 Activity 或根据 CRM 字段触发任务；
- recurrence template 中的 CRM 关系、附件、FILE、评论、HTML/Markdown 或任意 payload；
- 把 ApprovalTask、StageTaskDefinition、OA 公告、回款提醒或其他 B15 来源迁入 Work series/reminder 表；OA 公告只在 [B28](batch-B28.md) 以独立 inbox/read 和 typed delivery source 接入；
- 任意成员/部门/角色群发、外部联系人、营销订阅、公开地址、Webhook、SMS、IM、push 或日历同步；
- 邮件 provider 选型、真实 credential、bounce/open/click tracking 或 provider-wide exactly-once；
- 用 Redis、进程内 timer、`@Async`、普通 `@EventListener`、logging adapter 或 worklist acknowledgement 代替 durable ledger；
- public notification send/retry、跨 tenant 运维查询或在 API 中暴露安全失败原因。

## 19. 实施顺序

1. 先完成 B27a 的纯 recurrence model 与 MySQL occurrence oracle，再扩展 B26 kind/revision，最后接入真实 WorkTask 创建；
2. B27a 稳定后实现 B27b 的 task-local plan/occurrence/inbox；先覆盖 manual task，再让 series 复制 future template；
3. 只有站内 exact notification 与 task mutation 总序通过后才实现 B27c external outbox；
4. B27c 先运行 synthetic `IDEMPOTENT_SEND/AT_MOST_ONCE_SEND` crash fixture，production 保持 disabled；
5. 真实 EMAIL adapter、credential、rights、quota 和 current-build conformance 作为独立后续交付，不能用 B27 core 测试替代。
