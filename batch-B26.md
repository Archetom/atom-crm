# B26 统一调度与自动化内核边界规划

> 基线：`23703976 feat(B25a): 实现客户权限感知业务概览`
>
> 对账日期：2026-07-21
>
> 状态：B26a 已实现并完成验收；Work recurrence/提醒见 [B27](batch-B27.md)，OA 公告一次性发布见 [B28](batch-B28.md)

## 1. 结论

B26 首先解决的不是“让用户配置任意自动化规则”，而是建立一个可在多副本生产环境中安全唤醒、认领、执行和观察既有有界业务命令的内核。首个业务纵切只接入 B07/B08 已实现的 Customer/Contact 公海自动收回；它们已有 tenant 内公平游标、候选上限和 MySQL 并发仲裁，缺少的是跨 tenant 的 durable schedule、run identity、lease/fence、稳定时间、重试和运维证据。

附件的六类 worker 已经拥有独立的 durable job、lease/fence、deadline、effect evidence 和恢复语义。B26 只把它们的进程唤醒入口统一到 composition root，不把 `crm_attachment_job` 搬入通用 automation run，也不让调度器解释文件状态。**统一的是触发、资源隔离和运维表面，不是把所有后台业务压成一个万能任务表。**

B26a 因而固定两个执行平面：

1. **scheduled automation lane**：由 B26 的 schedule/run 账本拥有 occurrence、租约、重试和终态，首期只注册 `CUSTOMER_POOL_RELEASE` 与 `CONTACT_POOL_RELEASE`；
2. **durable work-pump lane**：B24 等已经拥有任务账本的能力只注册一个有界唤醒器，任务身份、lease、重试和业务提交仍完全归原边界所有。

## 2. 当前事实盘点

| 能力 | 已有事实 | 缺口或 B26 决策 |
| --- | --- | --- |
| Customer 公海自动收回 | `CustomerPoolAutomationServiceImpl` 接受 caller/as-of/limit；tenant 游标每页 25 个池、每次最多 50 个池和 1,000 个客户 | 不再要求 scheduler 伪造 human caller；接入 B26 durable occurrence，首期每次最多仍用既有有界值 |
| Contact 公海自动收回 | 与 Customer 分离的根、表、游标、锁和 scene code | 作为独立 automation kind 接入，不能借 Customer schedule 或 cursor 扩大范围 |
| B24 attachment workers | 六类 `AttachmentWorker`、`crm_attachment_job`、`SKIP LOCKED` claim、lease fence、heartbeat、deadline 和 terminal recovery 已实现 | 只替换现有专属 `@Scheduled` 唤醒，不复制 job 行、不改变 B24 readiness/锁序/外部 I/O 规则 |
| Work 重复任务 | 只有单次任务根、UTC due time、工作台投影和确认 | 不进入 B26a；[B27a](batch-B27.md) 规划独立的 series/occurrence、时区/DST、改派和删除语义 |
| 自动提醒、逾期升级、外部通知 | 尚无通知目标、偏好、退订和 transactional outbox | 不进入 B26a；[B27b/B27c](batch-B27.md) 分离提醒事实、站内 inbox 与外部 delivery，scheduler 不能把 best-effort event 冒充可靠通知 |
| OA 公告定时发布 | 现有发布会原子冻结受众，且要求当前人工管理权限 | 不进入 B26 run；[B28a](batch-B28.md) 以公告自有 exact occurrence/pump 复核排期成员权限，不让 generic job 绕过发布语义 |
| B22/B23/文件保留与对账 | 各自已有或规划专用可靠性、readiness 和 retention 契约 | 后续只能注册自己的 durable pump；不能让 B26 run payload 承载 connector、provider、文件 locator 或敏感值 |

## 3. B26a 范围与交付切面

B26a 是一个完整纵切，不先提交无人使用的通用框架。它同时交付：

1. 一个 closed kind catalog、durable schedule/run store、显式 handler registry 和统一 composition-root pulse；
2. MySQL 共享 UTC 时间、跨副本 claim、lease owner、递增 fence、同 occurrence 重试和有界 terminal retention；
3. Customer/Contact 公海自动收回的两个 system automation handler；
4. pool 配置与 schedule 激活/暂停的同事务联动，以及既有 tenant pool cursor 的继续复用；
5. B24 attachment workers 到统一 work-pump 触发面的迁移，但不改变任何 B24 durable work 契约；
6. 无 tenant 高基数标签、无业务 ID 和无异常原文的 health、metrics 与安全日志。

B26a 不增加 public HTTP API、tenant capability、导航项、用户可编辑 cron、任意条件、脚本、Webhook 或通知通道。

## 4. 分层与对象所有权

### 4.1 Application control plane

`AutomationKind`、`AutomationScheduleStore`、`AutomationRunLease`、`AutomationExecutionContext`、`AutomationHandler`、`AutomationHandlerRegistry` 和 `BackgroundWorkPump` 属于 `application` 或 `application.port.out.automation`。它们描述应用内部执行控制，不是 CRM domain aggregate、API DTO 或通用消息协议。

- `AutomationKind` 是代码定义的 closed catalog；未知数据库值或重复 handler 使该 lane 启动失败；
- `AutomationExecutionContext` 只包含 exact `TenantId`、kind、run identity/fence、`dueAt` 和不可变 `evaluationTime`；
- context 不包含 actor、member、authority、role、`AuthenticatedCaller`、`AuthenticatedConnector`、请求 body、任意参数 map 或序列化 payload；
- handler 只能返回受控 outcome，例如 affected count 和固定 result code，不能返回业务 ID 列表或任意 JSON；
- registry 必须显式完整注册 first-party kind，不使用 class name、reflection、Spring bean name 或数据库字符串动态加载代码。

`AutomationSchedulePO` 与 `AutomationRunPO`、MyBatis mapper、claim SQL 和 Flyway 只属于 `infra/persistence`。`start` 只组装 `BackgroundWorkScheduler`、受限执行 lane 和配置，不拥有 due 计算、重试分类或业务规则。

### 4.2 两种 pump 不共享 durability

`BackgroundWorkPump.runOnce()` 只是 scheduler-facing 的有界唤醒契约：

- `AutomationRunPump` 每次最多 claim 一个 B26 run，并把它交给 exact handler；
- B24 adapter pump 调用一个 exact `AttachmentWorker.runOnce()`；该 worker 仍从 `crm_attachment_job` claim 一个文件 job；
- 本地线程池、in-flight 标记和队列只限制当前进程资源，不形成 durable ownership；进程丢失这些状态后，MySQL lease 仍是唯一恢复依据。

不得让 `BackgroundWorkPump` 接收 tenant、kind、class name、cron 或 payload。它也不提供“执行成功”语义；成功与否由 owning durable store 决定。

## 5. 系统执行身份与 tenant 来源

B26 不创建 `SystemUser`，不合成租户 owner，也不扩展 `AuthenticatedCaller` 或 `AuthenticatedConnector`。

scheduled automation 的 tenant 只能来自被 MySQL claim 的 exact `crm_automation_run`，该行又通过 `(tenant_id, kind, generation)` 绑定一个 durable schedule，并以外键绑定 `organization_tenant`。跨 tenant claim 只发生在不读取业务表的内部 control-plane store；claim 返回一个 exact tenant 后，所有业务 repository、object lock、cursor 和 aggregate 操作都显式携带该 `TenantId`。

lease owner 和 fence 只证明某个进程当前拥有一次执行尝试，不是认证主体，也不能进入业务审计 actor、负责人、创建人或成员外键。若一个未来 automation 必须以某个人的权限或身份产生事实，它不能复用 B26a context，必须另行冻结授权快照、撤销和审计语义。

Customer/Contact 公海自动收回是 tenant 配置驱动的系统行为，不要求某个历史管理员持续存在。人工创建、修改、启停或删除 pool/rule 仍通过现有 caller、`*:manage` 能力和活动成员校验；后台 handler 只消费该事务已经接受的当前活动配置。

## 6. Schedule 与时间语义

### 6.1 固定 schedule 形状

每个 `(tenant, automationKind)` 最多有一个 durable schedule。首期 schedule 只保存 typed kind、`ACTIVE/PAUSED`、generation、下一次 `next_due_at`、固定 catalog cadence revision 和乐观版本；不保存 cron 文本、IANA 时区、Java class、表达式、SQL、脚本、参数 JSON 或业务记录 ID。

Customer/Contact 的 schedule 是否活动由本 tenant 是否至少存在一个 `ACTIVE + automatic_release_enabled` 的对应 pool 派生：

- 第一个活动自动规则在 pool 配置事务中激活或重激活 schedule；
- 最后一个活动自动规则被停用、删除或取消时，同一事务暂停 schedule；
- 暂停或 cadence revision 变化增加 generation，使尚未开始业务提交的旧 run 只能安全取消；
- 运行时仍在 owning object lock 下重读 pool 与规则，schedule 永远不是业务配置真相。

### 6.2 共享 UTC 与 misfire

due 判断、materialization、claim、lease、retry、完成时间和 retention 都使用 MySQL 共享 UTC 时间；不使用节点默认时区或散落的 `LocalDateTime.now()`。单元 fixture 可替换 control-plane time source，真实多副本实现必须以数据库时间为准。

materializer 锁定 due schedule 后，在一个短事务中：

1. 保留原 `next_due_at` 为 run 的 `dueAt`；
2. 把本次数据库 UTC 固化为 `evaluationTime`；
3. 幂等创建唯一 occurrence；
4. 由同一 catalog cadence 推进下一次 due。

首期 misfire policy 固定为 `COALESCE_LATEST`：长时间停机不会为每个错过的小时生成一条 backlog，只创建一个当前 evaluation occurrence。重试永远复用同一 run 和 `evaluationTime`。公海 inactivity cutoff 使用该稳定 evaluation time；真正的 membership `entered_time`、aggregate 更新时间和 run completion 使用实际提交时的数据库 UTC，不能把迟到执行伪装成历史上已经发生。

首期不支持 tenant/member 时区、DST、月末、工作日历或 caller-defined cadence。它们是 Work recurrence 等后续业务定义的组成部分，不属于 scheduler 默认行为。

## 7. Durable run、租约与失败语义

run 状态固定为 `READY`、`LEASED`、`RETRY_WAIT`、`SUCCEEDED`、`CANCELLED` 和 `DEAD`。数据库唯一键保证同一 `(tenant, kind, generation, dueAt)` 只有一个 run；一个条件生成的 active slot 唯一键保证同一 tenant/kind 同时最多一个非终态 run。

执行协议为：

1. claim 在独立短事务中以 `FOR UPDATE SKIP LOCKED` 选择 due `READY/RETRY_WAIT` 或过期 `LEASED`，写入随机 boot-scoped owner、递增 fence、lease deadline 和 attempt；
2. worker 在 `CommandServiceTemplate` 的独立事务中先取得 owning business guard，再锁 schedule 和 exact run，验证 tenant/kind/generation/owner/fence/lease；
3. handler 的全部本地业务写入与 `SUCCEEDED/CANCELLED` run 终态在同一事务提交；
4. 回滚不会暴露 pool membership、cursor 或 run success；commit acknowledgment 丢失时，已终态 run 不会再次被 claim；
5. 失败事务之外只按固定安全分类把同一 run 置为 `RETRY_WAIT`，使用有界 backoff；达到 deadline 或 max attempts 后转 `DEAD`，不创建 replacement occurrence。

这个保证只覆盖同一个 MySQL 事务中的本地 effect。未来外部 I/O handler 必须在成功事务中写入其 owning typed outbox 或 durable work，再由自己的 effect ledger/确认协议执行；B26 不宣称邮件、Webhook、provider 调用或对象存储的端到端 exactly-once。

worker 不捕获 `Throwable`。异常 class/message、SQL、stack、客户/联系人/池 ID 和候选内容都不写入 run；持久化只允许固定 safe reason、attempt、时间、affected count 和受控终态。未知异常重试至上限并告警，不能降级成业务成功。

## 8. 公海 handler 边界

现有两个 caller-facing automation service 不能直接被 scheduler 调用。B26a 将可复用逻辑收敛为两个 transaction-participating handler/coordinator：

```text
AutomationExecutionContext
  -> exact Customer or Contact automation handler
  -> owning customization object guard
  -> current pool configuration and existing tenant cursor
  -> bounded candidates and aggregate behavior
  -> same-transaction run terminal state
```

- handler 不再接受 nullable `asOf` 或 caller-provided limit；evaluation time 来自 run，limit 来自 validated deployment policy 并仍受代码绝对上限约束；
- 继续保留 Customer 与 Contact 各自的 cursor、repository、field catalog、aggregate save 和 membership 表，不建立 generic pool repository；
- 每个 occurrence 最多扫描 50 个 distinct pool、每页最多 25 个，changed records 保持既有绝对上限 1,000；生产默认建议值仍需在实现时固化为更小的正值；
- 所有 pool、rule、candidate 和 target aggregate 在持锁后重验，已停用、已入池、已删除或不再满足规则的记录成为安全 no-op；
- 影响数为零是 `SUCCEEDED`，不是失败；两个 kind 相互失败隔离，也不共享 fairness cursor。

既有 `UseCaseOperation.CUSTOMER_POOL_AUTOMATION`（6034）和 `CONTACT_POOL_AUTOMATION`（6134）保留为稳定应用错误 scene；它们不因此成为公开 API。若保留人工诊断入口，它必须仍由真实 caller 授权并调用同一 coordinator，但不能伪造 schedule/run 成功，也不能接受任意 tenant 或时间。

## 9. B24 worker 的统一触发边界

B26a 移除 attachment 专属 composition-root `@Scheduled` 所造成的第二套触发表面，只保留一个 `@EnableScheduling` 位置和显式 pump registry。每个 pump 在独立、有界的执行 lane 中运行；automation 数据库命令不会排在 scanner/storage I/O 后面，某个 attachment worker 卡住也不能阻塞其他 kind 的 due materialization。

以下 B24 事实保持不变：

- `crm_attachment_job` 仍是唯一 job ledger，B26 不为每个 attachment job 创建 automation run；
- 六个 `JobKind`、exact file/content generation、lease heartbeat、effect digest、pause、deadline、terminal recovery 和 target→relation→file→job 锁序不变；
- `atom.files.enabled`、worker readiness 和 deletion control lane 独立生效；B26 全局调度开关不能把一个 NOT_READY file profile 变为 ready；
- 本地 pump 提交失败或执行池饱和只跳过本次唤醒，durable attachment job 不丢失、不增加 attempt。

## 10. 事务与锁序

materializer 只锁 automation schedule/run control-plane 行，不读取 pool 或业务根。claim 只锁 run 并立即提交。真正的 pool execution 与配置写共享以下顺序：

```text
CUSTOMER or CONTACT customization object guard
  -> automation schedule
  -> exact leased automation run
  -> existing pool automation cursor
  -> pool roots in numeric order
  -> customer or contact roots
  -> membership/history rows
```

pool 创建、替换、状态变化和删除在 object guard 后先锁对应 schedule，再锁 pool 根并于提交前同步派生的 schedule active state。这样：

- 配置事务先完成时，旧 generation worker 在任何业务写前取消；
- worker 先取得 object guard 时，它按旧配置完整提交，随后配置变化形成清晰的数据库总序；
- materializer 即使先创建旧 generation run，后续 execution 也不能越过暂停；
- 不在 schedule/run 事务中调用外部 I/O、HTTP、Redis、scanner、storage 或通知通道。

`CommandServiceTemplate` 仍拥有业务执行事务。handler/coordinator 不得再次调用 public command service 或开启嵌套 `REQUIRES_NEW`。

## 11. 持久化、配置与保留

B26a 预留下一 migration `V65__create_automation_scheduler.sql`，只创建：

1. `crm_automation_schedule`：tenant/kind 主键、状态、generation、cadence revision、next due、version 与 tenant 外键；
2. `crm_automation_run`：exact occurrence、due/evaluation/attempt/lease/fence/deadline/result/affected count、active-slot 唯一约束、claim/retention 索引和 schedule/tenant 完整性；
3. 从当前 `ACTIVE + automatic_release_enabled` Customer/Contact pool 有界回填对应 schedule；V14/V21 cursor 不修改也不合并。

新 tenant 不预建空 schedule；pool 配置写在第一次需要时以原子 upsert 创建并锁定它。schedule 不物理删除，暂停保留 generation 和下次可解释状态；terminal run 是 application control-plane 历史，可由固定小批量 retention 物理删除。保留期限必须正值且有部署上限，不能用无限 run 历史充当业务审计。

计划的 production-safe 配置面位于 `atom.scheduling.*`，至少区分全局 enabled、pulse delay、automation/attachment lane concurrency、claim size、lease、run deadline、max attempts、retry backoff、pool cadence、每次 record limit 和 terminal retention。默认 production 保持 disabled；缺失、非正、相互矛盾或超过代码绝对上限的值使 scheduled automation lane `NOT_READY`，但不阻止普通 CRM 请求启动。所有副本必须使用同一 cadence/retry policy revision，漂移实例不 claim 新 run。

`atom.files.workers.enabled` 继续决定 B24 pump 是否参与；它不能隐式启用 pool automation，`atom.scheduling.enabled` 也不能绕过 `atom.files.enabled`。

## 12. 运维可观测性

B26a 提供的安全观测只包含：

- 每 kind 的 due lag、materialized、claimed、succeeded、cancelled、retry、dead 和 affected count；
- 每 lane 的 active/queued/rejected pulse、执行耗时和最近成功 UTC；
- schedule/run 表的有界状态计数与 health 分类。

metric label 不使用 tenant、run ID、pool ID、record ID、actor、异常 class/message 或任意业务值。日志至多包含 kind、opaque run ID、attempt/fence 和固定 safe reason；tenant 诊断如未来确有需要，应通过受控运维查询而不是高基数日志或 public CRM API。

## 13. B26a 实现任务卡

1. 增加 application automation vocabulary、store port、explicit handler/pump registry、run worker 和安全 outcome；
2. 实现 V65、PO/mapper/claim/fence/retry/retention adapter，以及 migration empty-schema/upgrade/backfill 测试；
3. 把 Customer/Contact 自动收回重构为无 synthetic caller、可参加现有 command transaction 的 owning handlers，并保留各自 scene/cursor/锁；
4. 在所有对应 pool 配置写中同步 schedule active/generation，证明 object→schedule→pool 的统一锁序；
5. 用一个 composition-root scheduler 和隔离的 bounded lanes 替换 attachment 专属 scheduler，保持 B24 job/worker/config 不变；
6. 增加 config validation、readiness、health、metrics 和安全日志；
7. 更新 README、configuration、architecture、object layering、test guide、`llms.txt` 和 `docs/acceptance/B26a.md`。

## 14. 验收门槛

B26a 实现必须同时证明：

1. 两个副本同时 materialize/claim 同一 tenant/kind/due 时只有一个 run identity 和一个 committed handler effect；
2. materialize 前后、claim 后、业务事务中、commit 前后及 acknowledgment 丢失的 crash cuts 均不会产生第二 occurrence、部分 pool membership 或重复 cursor advancement；
3. lease expiry/takeover 后旧 owner/fence 不能提交业务状态、run success 或 retry decision；
4. schedule 暂停/重启与 worker 竞争遵守 object→schedule→run→cursor→pool→record 总序，暂停完成后旧 generation 不再释放记录；
5. 长停机只生成一个 `COALESCE_LATEST` occurrence；其 retry 使用同一 evaluation time，而真实业务时间来自最终数据库提交；
6. Customer 与 Contact 各自扫描不超过 50 个 pool、25 个/page 和配置 record limit，保留 tenant cursor 公平性且互不共享状态；
7. 一个 tenant 的大池、失败、DEAD run 或锁等待不会让另一个 tenant/kind 永久饥饿；claim SQL 使用 due/active 索引且不扫描业务表；
8. run tenant 只能来自 schedule 外键与 claimed row；不存在 fabricated caller、request tenant、actor fallback 或跨 tenant business repository call；
9. config 关闭/漂移/非法时不 claim pool run，普通 CRM 仍可用；B24 NOT_READY/disabled 继续独立失败关闭；
10. B24 六类 worker 经统一 pump 仍一次最多 lease 一个 exact job，scanner/storage I/O 不阻塞 automation lane，且 V59–V64 schema/lock/recovery oracle 不变；
11. retry/dead/retention、线程池 rejection、shutdown 和进程重启都不丢 durable work，terminal history 保持有界；
12. log/trace/metric/run row 不含业务 ID 列表、caller、credential、文件 locator/checksum、异常原文或任意 payload；
13. `sh ./mvnw test` 与 `CI=true sh ./mvnw test` 全部通过；涉及调度、事务、mapper、schema 和 worker，因此 Docker/MySQL 集成套件是必需门槛。

## 15. 明确非目标

B26a 不包含：

- `/api/v1/automations`、用户自定义 cron、时区、条件树、SQL、脚本、表达式、低代码 workflow 或第三方 job class；
- Work task recurrence、自动创建任务、自动跟进、逾期升级、SLA、日历同步或工作台新来源；
- 邮件、短信、IM、push、Webhook、公告定时发布、通知偏好、退订、签名和可靠外部投递；
- 把 Approval/Stage runtime 变成 scheduler workflow，或让 automation 绕过当前业务锁、状态机、字段策略与数据范围；
- 把 B24 attachment job、未来 B22 outbox/inbox/reconciliation 或 B23 source admission 合并到 generic run；
- Kubernetes CronJob、Quartz、ShedLock、Redis lock 或消息队列产品选择。B26 先固定应用契约；部署适配器必须满足同一 MySQL oracle；
- provider-wide 或外部副作用的 exactly-once 声明。

## 16. 实施顺序

1. 先以独立 model/MySQL fixture 固定 schedule materialization、active-slot、claim/fence、misfire、retry 和 crash oracle；
2. 实现 V65 与 application/persistence control plane，再用 synthetic no-op handler 只验证账本，不启用 production scheduling；
3. 接入 Customer 自动收回并完成双副本/配置竞态/公平游标验证，再以同一协议接入独立 Contact handler；
4. 最后迁移 B24 composition-root pulse，参数化慢 scanner/storage 证明 lane 隔离且不改写 attachment durability；
5. 只有完整 MySQL、配置/readiness、安全观测和全量回归通过后，才允许在明确生产配置中开启 `atom.scheduling.enabled`。
