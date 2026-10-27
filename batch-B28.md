# B28 OA 公告定时发布与可靠通知接入边界规划

> 基线：`6fc182b0 feat(B27c): 实现外部通知可靠投递内核`
>
> 对账日期：2026-07-22
>
> 状态：B28a/B28b 已实现；B28b 通过 V76 将 OA 公告接入 B27c typed delivery，不改变 OA inbox/read ledger。

## 1. 结论

B28 在 B20c 已实现的公告受众快照与已读回执，以及 B27c 已实现的 EMAIL delivery outbox 之上，补齐两个必须分开证明的事实：

1. **公告发布事实**：一个处于 `SCHEDULED` 的公告，在其 exact UTC 时点之后，由仍具备当前管理资格的调度成员授权，最多提交一次 `PUBLISHED` 根状态与不可变接收人快照；
2. **外部通知事实**：一个已发布公告的一个快照接收成员，可以派生一条独立 EMAIL delivery；provider 是否受理、成员是否在站内读过公告以及公告是否仍公开可见是三个不同状态。

B28a 不增加 B26 `AutomationKind`。公告已经拥有 exact 一次性计划、generation 和 publication occurrence，重复复制成 `crm_automation_run` 只会产生两个竞争的执行真相。它注册自己的有界 `BackgroundWorkPump`，复用统一 composition-root pulse，但 occurrence identity、lease/fence、重试、misfire 和终态都由 OA 公告边界拥有。

B28b 不把公告塞进 B27b 的 Work-only `Notification`，也不复制第二套 EMAIL outbox。它把 B27c 当前 Work 专属的 delivery source 明确升级为 closed typed catalog：`WORK_TASK_NOTIFICATION` 与 `OA_ANNOUNCEMENT_RECIPIENT`。两种来源各自拥有当前可见性和锁序，通道层只复用可靠 dispatch、readiness、endpoint binding 与 effect-certainty 协议。

**“到达计划时间”“公告已发布”“站内接收人已读”“EMAIL provider 已受理”不能互相推导。** 定时发布不会等待外部 provider，EMAIL `ACCEPTED` 不会写公告 read receipt，撤回也不能伪称已撤回一个已经开始的外部 effect。

## 2. 当前事实盘点

| 能力 | 当前事实 | B28 决策 |
| --- | --- | --- |
| B20c 公告根 | `DRAFT/PUBLISHED/WITHDRAWN/DELETED`、纯文本、typed selectors、乐观锁已实现 | B28a 只增加 `SCHEDULED` 与一次性 publication generation，不引入 cron/RRULE |
| 发布事务 | `organization_tenant -> announcement`，在同一事务固化最多 10,000 个活动接收人 | 人工立即发布与后台到期发布共用一个 transaction-participating coordinator |
| 公告 inbox/read | `oa_announcement_recipient` 是不可变站内接收人快照，read receipt 保存首次数据库 UTC | 保持 OA 自有 inbox；不迁移到 `crm_notification`，不以 EMAIL 状态代写 read receipt |
| B26/B27 pulse | 一个 composition-root pulse 可唤醒独立 durable pump | B28a 只注册 `OaAnnouncementPublicationPump`，不增加 generic automation payload |
| B27b Notification | 当前只允许 Work reminder/escalation，并强绑定 task/reminder occurrence | 继续 Work-only；OA 不创建重复站内 Notification 行 |
| B27c delivery | EMAIL outbox、lease/fence、readiness、HMAC endpoint binding、幂等/歧义已实现，但 schema 和 admission 强绑定 Work | B28b 以 typed source 和 exact-one FK 升级，不复制状态机或 provider adapter |
| 通知偏好 | 当前成员只有一个默认 OFF 的 Work EMAIL 开关 | 保留既有 `emailEnabled` 语义，新增默认 OFF 的 `oaAnnouncementEmailEnabled`，避免升级后扩大既有同意 |
| 外部内容 | Work EMAIL 只发送固定 title/priority/due payload | OA EMAIL 使用独立固定 template，只含 kind、公告 ID、标题与实际发布时间，不发送正文或受众 |

## 3. 子批次拆分

| 子批次 | 交付物 | 依赖 | 计划迁移 |
| --- | --- | --- | --- |
| B28a | `SCHEDULED` 生命周期、schedule/cancel API、durable publication occurrence、当前管理授权复核、同事务发布 | B20c、B26 unified pulse | V74 |
| B28b | source-scoped preference、typed delivery source、OA EMAIL outbox creation/admission/suppression、closed per-template readiness | B28a、B27c | V76 |

B28a 必须在完全禁用外部通道时形成可独立使用的本地 MySQL 闭环。B28b 可以在人工立即发布和 B28a 到期发布上使用同一个 publication coordinator；没有真实 EMAIL adapter 时公告发布与站内 inbox 仍必须正常工作。

## 4. 分层与对象所有权

### 4.1 OA announcement domain

`OaAnnouncement` 继续拥有标题、正文、typed selectors、业务生命周期和版本。B28a 增加 `SCHEDULED`、当前 scheduled UTC、scheduling member、publication generation，以及只对管理端可见的固定 `lastPublicationSafeReason`。调度不是一个通用 job DTO，也不是 B26 schedule PO。

`OaAnnouncementPublicationOccurrence` 是 application-owned durable work fact。它保存 exact `(tenant, announcement, generation)`、计划 UTC、available/deadline、attempt、lease/fence、终态和固定 safe reason；它不作为无界集合水合进公告聚合。一个公告重新排期会创建新 generation，旧 occurrence 保持终态证据并按固定保留期清理。

`oa_announcement_recipient` 继续是站内投递与可见性的唯一快照。它不是 notification outbox，也不是“EMAIL 已送达”记录。`oa_announcement_read_receipt` 继续只表示成员通过受保护 OA API 首次阅读。

### 4.2 Application publication boundary

`OaAnnouncementPublicationCoordinator` 是可参加当前 `CommandServiceTemplate` 事务的内部协作器。人工 `publish` 已经通过 `AuthenticatedCaller` 验证管理权限后调用它；后台 worker 通过 exact claimed occurrence 和专用当前授权投影调用它。两条路径都不能再次调用 public `OaAnnouncementService`、打开嵌套 `REQUIRES_NEW` 或构造 synthetic caller。

coordinator 固定执行：锁 tenant、锁公告、验证 exact generation/状态、验证发布授权、重验 selectors、解析 bounded 接收人、写根状态、写 recipient snapshot、可选规划 typed external delivery、结算 publication occurrence。外部 provider I/O 永远不在这个事务内。

### 4.3 Reliable delivery boundary

`NotificationDeliverySourceKind` 是 application closed catalog，首期只有：

- `WORK_TASK_NOTIFICATION`：保留 B27c 当前 task-first admission 和既有 payload/idempotency 语义；
- `OA_ANNOUNCEMENT_RECIPIENT`：以 exact `(tenant, announcement, recipient)` 快照为父事实，按公告当前状态和权限 admission。

每个 kind 注册一个显式 `NotificationDeliverySourceAdmission`，registry 必须完整且拒绝重复或缺项。它返回固定的 source-owned dispatch content，不返回 `Map<String,Object>`、任意模板变量、表名、SQL、URL 或 provider DTO。

`NotificationChannelAdapter` 仍只看到 typed EMAIL command、deadline/cancellation budget 和 exact template revision。provider account、credential、native message ID、request/response、header 和异常正文继续终止在 `infra/external`。

## 5. 人工 API 与生命周期

计划新增：

```text
POST /api/v1/oa/announcements/{announcementId}/schedule
POST /api/v1/oa/announcements/{announcementId}/cancel-schedule
```

`schedule` 请求只接受当前 optimistic `version` 与一个带显式 `Z`/offset 的 publication instant；应用规范化成 UTC `DATETIME(6)`。不接受 tenant、actor、timezone、cron、RRULE、locale、任意 retry policy 或通知地址。计划时间必须晚于数据库当前 UTC 至少一分钟且不超过 365 天；立即发布继续使用既有 `/publish`。

生命周期固定为：

```text
DRAFT -> SCHEDULED -> PUBLISHED -> WITHDRAWN -> DELETED
  |          |             ^
  |          +-- publish now
  +------------- publish now
```

- 只有 `DRAFT` 可编辑标题、正文和 selectors；排期会冻结本次获授权的内容与受众表达式；
- `cancel-schedule` 把 `SCHEDULED` 恢复为 `DRAFT` 并在同一事务取消 exact pending occurrence；
- 人工 `publish` 可从 `DRAFT` 或 `SCHEDULED` 发布；从 `SCHEDULED` 发布时必须先按相同锁序取消未开始或尚未结算的 occurrence；
- `SCHEDULED` 可直接软删除，但删除事务必须先取消 occurrence；`PUBLISHED` 仍必须先 withdraw；
- 重新排期必须先取消旧计划，再以增加后的 generation 创建新 occurrence；旧 generation 永远不能命中新根状态；
- 每 tenant 最多 1,000 个当前 `SCHEDULED` 公告，计数在现有 tenant lock 内完成，不能靠进程内预查仲裁。

管理 detail/summary 增加 `scheduledPublishTime`、`scheduledByActorId` 和固定 `lastPublicationSafeReason`。普通 `/mine` inbox 不返回调度人、generation、attempt、lease、safe reason 或 delivery 状态。

## 6. B28a 时间、misfire 与 occurrence 语义

### 6.1 时间真相

计划时点、claim、lease、retry、deadline 和实际 `publishedTime` 全部使用 MySQL UTC。应用节点 `Clock` 只能用于纯输入准备或测试，不能决定 due、lease 是否有效或最终发布时间。

`scheduledPublishTime` 是 earliest eligible instant，不是精确 wall-clock SLA。实际发布只能等于或晚于该时点，并保存发布事务取得的数据库 UTC，不能把请求中的计划时间伪装为实际发布时间。

B28a 是一次性 UTC instant，不存在本地日历重复、DST gap/overlap、月末或 tzdb revision。需要这些规则的未来能力必须另立 series，不得复用公告 request 字段偷渡。

### 6.2 Identity、claim 与恢复

occurrence identity 固定为：

```text
(tenantId, announcementId, publicationGeneration)
```

一个 active-slot 唯一约束保证同一公告最多一个 `READY/RETRY_WAIT/LEASED` publication occurrence。claim 是独立短事务，按数据库 due/available time 写 boot-scoped owner、递增 fence 和被 deadline 截断的 lease；effect 事务重新从 tenant 与公告根开始，再锁 exact occurrence 并验证 fence。

同一个 announcement generation 不创建 replacement occurrence。事务失败、lease takeover 或 commit acknowledgment 丢失都只能继续同一 row；`PUBLISHED` 根、recipient 主键、delivery source 唯一键和 occurrence terminal settlement 共同阻止第二次本地 effect。

### 6.3 Misfire 与失败分类

一次性公告不能像周期任务一样 `COALESCE_LATEST` 掉较早公告。每个仍在有效 publication window 内的 due occurrence 都保留自己的顺序和身份，由 bounded pump 逐条处理。

部署配置一个有绝对上限的 `max-lateness`，计划时固化 `deadlineAt = scheduledPublishTime + maxLateness`。系统停机后：

- deadline 前恢复的公告仍按 `(scheduledTime, id)` 顺序发布；
- deadline 已过而从未提交发布的 occurrence 转 `DEAD/MISFIRE_EXPIRED`，公告回到可编辑 `DRAFT`；
- 数据库/锁等短暂基础设施失败进入同 row 的 `RETRY_WAIT`；达到 attempt 或 deadline 上限时由持有 exact fence 的事务同时终态化 occurrence 并把根恢复为 `DRAFT`；
- scheduling member 失活或失去 `oa-announcements:write`、selector 目标失效、受众为空或超过 10,000 是确定性取消；同一事务写 `CANCELLED`、清除 active schedule，并以固定 safe reason 返回 `DRAFT`，不会无限重试；
- 未知异常不捕获 `Throwable`，不把失败公告标为已发布，也不把异常 class/message 写入根或 occurrence。

## 7. 发布时授权与受众快照

排期动作只证明“当前成员有权创建这个未来意图”。真正发布时仍必须重新证明：

```text
exact scheduled occurrence
  ∩ current active scheduling membership
  ∩ current oa-announcements:write
  ∩ current valid same-tenant selectors
  ∩ current active recipient expansion within 1..10,000
```

后台通过 tenant-scoped authorization projection 验证 scheduling member，不构造 `AuthenticatedCaller`，也不回退到 creator、tenant owner、lease owner 或任意当前管理员。其他管理员若要接管，必须显式 cancel 并重新 schedule，形成新 generation 和新的授权事实。

受众仍在实际发布事务中解析，而不是排期时预快照。部门移动、成员加入或失活在 tenant lock 的数据库总序中决定是否进入快照；计划之后新加入且在发布时满足 selector 的成员可以进入，计划之后离开或失活的成员不会进入。selectors 自排期后不可编辑，因此组织变化只改变 selector 的当前解析结果，不改变管理者已批准的表达式。

人工立即发布和到期发布都调用同一 recipient resolver，读取最多 10,001 个去重 ID；零接收人或第 10,001 个接收人使整个发布事务失败关闭，不截断、不分批发布、不先暴露部分 inbox。

## 8. B28b 外部通知派生

### 8.1 不复制站内通知

公告发布后，`oa_announcement_recipient` 已经是每个成员的 durable in-app fact。B28b 不为同一成员再插入 `crm_notification`，否则会产生两个收件箱、两个已读状态和两个撤回规则。

可选 EMAIL delivery 在 publication coordinator 的同一事务中，从最终 recipient snapshot 派生。只有 deployment EMAIL 配置有效且该成员当前明确打开 `oaAnnouncementEmailEnabled` 时才创建 row；缺失偏好固定为 OFF。通道关闭或配置非法不创建新 delivery，但不影响公告根、站内 snapshot 或 read receipt，且历史 delivery 的 deadline/retention maintenance 继续运行。

如果一个本应创建的 delivery 插入失败，已启用 EMAIL 的发布事务整体回滚，不能留下已发布公告却缺少其已承诺 outbox 的半事实。成员偏好和授权交集使用对最多 10,000 个 snapshot member 的 bounded SQL/batch projection，不做每收件人无界 repository/角色水合。

### 8.2 Preference compatibility

`GET/PUT /api/v1/notifications/preferences` 保留现有 `emailEnabled` 作为 Work reminder EMAIL 开关，并新增 `oaAnnouncementEmailEnabled`。新列和缺失请求值默认不扩大同意：升级后所有成员的 OA EMAIL 都是 OFF；兼容旧客户端的 PUT 在未提供新字段时保留当前 OA 值，而不是隐式打开或重置。请求继续严格拒绝 endpoint、email address、provider、tenant、recipient 和未知属性。

修改偏好仍要求当前成员和 `notifications:read`，只能操作自己。OA delivery admission 还要求 `oa-announcements:read`；因此最终外部资格是：

```text
current active membership
  ∩ immutable announcement recipient snapshot
  ∩ announcement status PUBLISHED
  ∩ notifications:read
  ∩ oa-announcements:read
  ∩ oaAnnouncementEmailEnabled
  ∩ deployment EMAIL enabled/ready
  ∩ current trusted profile EMAIL endpoint
```

### 8.3 Fixed content and privacy

OA EMAIL 使用独立 code-owned template revision，例如 `B28B_OA_ANNOUNCEMENT_EMAIL_V1`。canonical payload 只含 template revision、source kind、announcement ID、bounded title 和实际 `publishedTime`。20,000-code-point body、selectors、部门/成员列表、recipient/read totals、read receipt、creator/scheduler、附件、任意 URL 和请求 body 都不进入 outbox、digest input 或 adapter command。

readiness 必须按 template 显式声明当前 adapter build/binding 支持 exact revision。Work row 继续只要求不变的 B27c Work template，OA row 独立要求 OA template；支持一个不能推导支持另一个，因此仅 Work-ready 的部署不会因 B28 升级而回归。一个 adapter 可以声明两个 closed template，但不能通过 wildcard、任意 string template 或 provider-side 动态变量绕过核心 catalog。

既有 Work delivery 的 `B27C_EMAIL_V1` payload digest、`b27c:` idempotency-key derivation、endpoint-binding HMAC purpose 和已冻结 guarantee/window 不得被 V76 改写。OA 使用自己的稳定 idempotency-key namespace；历史 Work row 在升级前后必须生成完全相同的 exact command。

## 9. Dispatch admission、撤回与 effect certainty

OA source admission 以 publication row 中的 tenant 开始，固定锁 announcement root → exact recipient snapshot → delivery。它必须重新验证当前 `PUBLISHED`、snapshot member、活动成员、两个能力、source-specific preference、profile endpoint、template/readiness 和 exact payload digest，才进入现有 `DISPATCHING` boundary。

撤回或删除公告时，在公告根锁内按 source-specific delivery 索引处理尚未完成的 row：

- 尚未跨 dispatch boundary 的 `READY/RETRY_WAIT/LEASED` row 结算为 `SUPPRESSED/ANNOUNCEMENT_WITHDRAWN`，零 provider call；
- 已经拥有 guarantee/dispatch history 或 effect certainty 不为 `NO_EFFECT` 的 row 保守结算 `AMBIGUOUS`，不能伪称撤回 provider effect；
- `ACCEPTED/REJECTED/SUPPRESSED/AMBIGUOUS/DEAD` 等既有终态不被公告状态重写；
- EMAIL 状态不改变公告 `PUBLISHED/WITHDRAWN`、recipient snapshot 或 read receipt。

B27c 的 `IDEMPOTENT_SEND(window)`、`AT_MOST_ONCE_SEND`、dispatch budget、settlement reserve、endpoint drift、deadline、retry 和 retention 语义原样适用于 OA source。`ACCEPTED` 仍只表示 provider 受理；B28 不实现到达、退信、打开、点击或已读映射。

## 10. Tenant、安全与敏感数据

- schedule/cancel/publish/withdraw/delete 都接收显式 `AuthenticatedCaller` 并从验证上下文派生 tenant；请求不能提交 scheduling actor 或 tenant；
- publication occurrence 的 tenant 只来自 exact foreign-keyed row；进入公告、成员、偏好或 delivery 后每个操作仍显式 tenant scoped；
- scheduling member 是未来发布授权归属，不是 system actor；lease owner、scheduler thread、tenant owner 或公告 creator 都不能替代它；
- 普通 inbox 仍只使用 `oa-announcements:read + snapshot + active membership`，不会因为成员拥有 `notifications:read` 或 EMAIL preference 扩大公告可见性；
- plaintext endpoint 只在 dispatch admission 到 adapter command 的短生命周期内存在；数据库继续只保存 tenant/purpose HMAC binding 与 key version；
- 标题虽可进入固定 EMAIL content，也不能进入日志、metric label、health、occurrence safe reason 或普通 delivery ledger；
- body、selector、recipient 列表、email、credential、provider response、异常原文和 full request 不能进入日志、trace、metrics 或 Activity；
- 跨 tenant、不可见公告和不可见 delivery source 使用相同安全 not-found/suppression 语义。

## 11. 事务与锁序

所有会相遇的路径共享以下相对顺序；不经过某层时直接跳过：

```text
organization_tenant
  -> oa_announcement
  -> oa_announcement_publication_occurrence
  -> oa_announcement_recipient
  -> notification_delivery
```

- schedule/cancel/update/delete：tenant → announcement → exact current occurrence；
- 人工或后台 publish：tenant → announcement → occurrence（若有）→ ordered recipient inserts → optional delivery inserts；
- publication claim：只锁 occurrence 并短事务提交；effect 释放 claim 事务后重新按 tenant → announcement → occurrence 进入；
- read receipt：继续 membership shared lock → visible announcement shared lock → receipt unique insert，不接触 delivery；
- withdraw/delete：tenant → announcement → source-specific pending deliveries，并使用 B27c effect certainty 分类；
- delivery claim：只锁 delivery 短事务；OA admission 从 announcement 开始，再锁 recipient 和 exact delivery；provider I/O 不持有 MySQL transaction。

Work delivery 继续使用 task → notification → delivery。source registry必须选择所属锁序，不能先锁 generic delivery 再反向取得 task 或 announcement。不同 source 不互相取得业务根。

## 12. 持久化与迁移计划

B28 只能新增迁移，不能编辑 V55、V57 或 V69–V73。

### V74 — Scheduled announcement publication

- 为 `oa_announcement` 增加 `SCHEDULED` lifecycle、scheduled UTC/scheduling member/publication generation/last safe reason，以及 `(tenant, status, scheduled_time, id)` 管理索引；
- `oa_announcement_publication_occurrence` 保存 exact generation、due/available/deadline、attempt、lease/fence、terminal/safe reason；
- 数据库唯一键同时证明 `(tenant, announcement, generation)` identity 与每公告一个 active slot；
- tenant、announcement 和 scheduling member 使用 composite FK，状态/CHECK 保证 lease、terminal 和时间列一致；
- 没有历史 backfill；已有 DRAFT/PUBLISHED/WITHDRAWN/DELETED 行保持原生命周期与版本。

### V76 — Typed external-delivery sources

- 为 `crm_notification_preference` 增加默认 OFF 的 `oa_announcement_email_enabled`，保留现有 `email_enabled` 的 Work 语义；
- 为 `crm_notification_delivery` 先增加 nullable staging `source_kind`，把全部现有行回填为 `WORK_TASK_NOTIFICATION`，验证后再改为非空；
- 把 `notification_id/work_task_id` 改为 Work source 的 typed nullable pair，增加 OA source 的 `oa_announcement_id`；exact-one CHECK 拒绝混合、全空或未知 source；
- Work source 在 `crm_notification` 增加 `(tenant, notification, recipient, task)` 候选键，并以一个 composite FK 证明 delivery 的 notification、recipient 与 task 属于同一事实，同时保留既有 source/channel 唯一性；
- OA source 使用 `(tenant, announcement, recipient)` 到 `oa_announcement_recipient` 的 composite FK，并以 `(tenant, announcement, recipient, channel)` 唯一；
- 增加按 OA root/status 查找 pending delivery 的覆盖索引，以保持 withdraw 的 root-first bounded mutation；
- 不新增 plaintext endpoint、body、selector、provider ID、request/response、header、exception 或任意 JSON payload 列。

terminal occurrence 与 delivery 使用固定小批量物理清理。当前 active schedule 的 exact occurrence 必须保留；terminal occurrence 清理不能删除仍被根状态或诊断所需的最新 generation。delivery terminal retention 继续受 B27c notification-content ceiling 约束，并增加 OA source 的独立代码绝对上限，不用公告长期保留反向延长运行账本。

## 13. 配置、readiness 与可观测性

B28a 计划配置 `atom.oa.announcements.publication.*`：enabled、pump concurrency、lease、max attempts、retry backoff、max lateness 和 terminal retention。所有值都有代码绝对上限；非法配置只关闭 publication pump，人工 CRUD/立即发布、公告 inbox 和历史 read receipt 继续可用。新的 schedule command 只在 lane enabled、配置合法且统一 pulse 可用时接受，避免制造部署明确不会执行的新承诺；之后关闭 lane 仍保留既有 durable occurrence。`atom.scheduling.pulses-enabled=false` 时 enabled lane 报告 `SCHEDULER_DISABLED`，不丢失 durable occurrence。

B28b 复用 `atom.notifications.channels.email.*`、现有 keyring、dispatch budget 和 maintenance。新增 OA template readiness 不增加 provider、account、credential 或 endpoint 请求配置，也不允许 tenant 自定义模板。production EMAIL 仍默认 disabled。

metrics 只使用 fixed source/outcome/status label：publication scheduled/claimed/published/cancelled/retry/dead/misfire、OA delivery created/accepted/rejected/suppressed/ambiguous/dead。不得用 tenant、member、announcement/delivery ID、title、scheduled time、endpoint、provider ID 或异常类型/原文作 label。指标只在所属事务成功提交后增加。

health 只报告 `DISABLED`、`CONFIG_INVALID`、`SCHEDULER_DISABLED`、`RUNTIME_FAILURE` 或 lane `READY`。publication ready 不表示某个公告会成功通过未来授权/受众复核；EMAIL lane ready 不表示 provider 到达或成员已读。

## 14. B28a 实现任务卡

1. 扩展 `OaAnnouncement` lifecycle、schedule/cancel request/VO/response 与严格 HTTP 路由；
2. 实现 V74、publication occurrence store、claim/fence/retry/deadline/retention 与 root generation/active-slot；
3. 抽取人工/后台共用的 `OaAnnouncementPublicationCoordinator`，保持 tenant → root → occurrence → recipient 锁序；
4. 实现当前 scheduling-member authorization projection、bounded recipient resolution、deterministic cancellation 和数据库 UTC；
5. 注册独立 `OaAnnouncementPublicationPump`，不扩展 `AutomationKind` 或 `crm_automation_run`；
6. 更新 README、API/configuration/architecture/object-layering/test-guide/`llms.txt` 并增加 `docs/acceptance/B28a.md`。

## 15. B28b 实现任务卡

1. 将 delivery source 提升为 closed typed catalog 与 exhaustive admission registry，保留 Work exact command 兼容；
2. 实现 V76 exact-one schema、OA recipient composite FK、source-specific uniqueness/index和 source-scoped preference；
3. 在 publication coordinator 同事务批量派生 OA EMAIL rows，disabled/OFF 时保持零 row；
4. 增加固定 OA template/digest/command、closed per-template readiness 和 OA root-first dispatch admission；
5. 接入 withdraw/delete 的 `SUPPRESSED/AMBIGUOUS` 分类，证明不代写 recipient/read或公告状态；
6. 扩展 provider-independent crash oracle 与 `docs/acceptance/B28b.md`；真实 provider adapter/readiness 仍是独立部署交付。

## 16. 验收门槛

B28 完成必须同时证明：

1. schedule 只接受规范 future instant，边界为数据库 UTC + 1 分钟至 +365 天；跨 offset 等价值归一到同一 UTC；
2. 同公告的 schedule/cancel/reschedule/publish-now/update/delete 并发遵守版本和 tenant → root → occurrence 总序，最多一个 active generation；
3. 两副本 claim、lease takeover、effect、commit acknowledgment 丢失下，同 generation 最多有一个 PUBLISHED 根和一份 recipient snapshot；stale fence 不能提交；
4. 到期发布重新验证 exact scheduling member 的 active membership 与 `oa-announcements:write`，绝不使用 creator/owner/lease owner 或 synthetic caller 回退；
5. selectors 在 SCHEDULED 时不可编辑，但实际 recipient 在发布事务按当前组织状态解析；零、10,001、失效目标和跨 tenant 全部原子失败且无部分 snapshot；
6. max-lateness 内的多个公告分别发布而不 coalesce，过期、授权撤销或确定性受众失败安全回到 DRAFT 并留下 fixed terminal reason；
7. 人工立即发布与 scheduled publish 产生相同根、recipient、inbox和 optional outbox 语义，实际 published time 取数据库提交 UTC；
8. OA inbox/read 继续只依赖 OA read + snapshot + active membership；EMAIL preference/accepted/suppressed 不改变 total、unread或首读时间；
9. V76 升级后所有既有 Work delivery 的 source、payload digest、idempotency key、endpoint binding、guarantee/window、singleton Work-template readiness 和 admission 结果保持兼容；
10. OA delivery 只为 snapshot ∩ active member ∩ 两项能力 ∩ source opt-in 创建/dispatch；无授权、opt-out、endpoint缺失或 not-ready 在 provider 前失败关闭；
11. publication + recipient + eligible delivery 在一个事务提交；任何 enabled outbox 插入失败全部回滚，disabled/OFF 不阻止站内发布；
12. withdraw 与 dispatch 竞争按 announcement → recipient → delivery 总序分类：未跨 boundary 为 SUPPRESSED，已有不确定 effect 保持 AMBIGUOUS，不伪称远端撤回；
13. OA adapter command 和 durable rows 不含 body、selector、recipient list、email、credential、provider payload/response 或异常原文，Work command 也不因泛化增加任意 map；
14. migration empty-schema/upgrade、CHECK/FK/index、两 source claim/admission、retention和真实 MySQL lock oracle 通过；
15. `sh ./mvnw test` 与 `CI=true sh ./mvnw test` 全部通过。B28 涉及 schema、权限、事务、调度、mapper 和外部-effect 边界，因此 Docker/MySQL 套件是必需门槛。

## 17. 明确非目标

B28 不包含：

- recurring announcement、cron/RRULE、时区日历、节假日、审批后自动发布或按 CRM 条件触发；
- 动态受众、发布后补投新成员、超过 10,000 后分批截断、跨 tenant 或外部联系人；
- HTML/Markdown、富文本、图片、模板变量、评论、点赞、置顶、确认阅读或阅读时长；受治理的非 inline 公告附件由 [B29c](batch-B29.md#7-b29coa-公告附件) 独立规划，仍不进入 EMAIL；
- 把 OA recipient/read receipt 迁入 `crm_notification`，或把 Work Notification 改成公告正文容器；
- 任意 member/department/role 的 public send API、管理员代改偏好、任意 email/phone/URL或 provider account；
- SMS、IM、push、Webhook、Slack、Teams、企业微信、钉钉、飞书或日历邀请；
- provider 选型、真实 credential、bounce/open/click、远端撤回或 provider-wide exactly-once；
- 用 B26 generic run payload、Redis timer、`@Async`、普通 event、logging adapter 或 read receipt代替 durable occurrence/outbox；
- 跨 tenant delivery 运维列表、public retry/send、在普通 inbox 暴露 safe reason/attempt/endpoint readiness。

## 18. 实施顺序

1. 先以 domain/MySQL fixture 固定 `SCHEDULED` lifecycle、generation、claim/fence、misfire和 publish/cancel crash oracle；
2. 实现 V74 与 publication coordinator，先让人工 publish 走同一 coordinator，再注册后台 pump；
3. 在完全关闭 EMAIL 时完成 B28a 全量验收，证明定时发布不依赖 B27c adapter；
4. 再以 V76 backfill/compatibility fixture 泛化 delivery source，先证明既有 Work row bit-for-bit command兼容；
5. 最后接入 OA preference、publication outbox 和 withdraw/admission，并用 synthetic 两 guarantee adapter 执行 crash matrix；
6. 只有 current-build adapter 对 OA template 提供真实 readiness/conformance 时，部署才可让 OA delivery 进入生产 dispatch；Work template 保持自己既有的独立证据，B28 core 测试不能替代任何 provider 证据。
