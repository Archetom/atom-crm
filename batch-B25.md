# B25 剩余业务接缝盘点与客户业务概览边界规划

> 基线：`6ce76fc feat(B24c): 实现受治理 FILE 字段、创建前文件草稿及 OA 提交文件`
>
> 对账日期：2026-07-21
>
> 状态：B25a 已实现并完成文档与验收记录；B25b 尚未预占；B26/B27/B28/B29 接缝均已实现，B30a Customer CSV 亦已实现；当前总账与下一实现边界见 [B31](batch-B31.md)

## 1. 对账结论

B24a、B24b 和 B24c 已完成，因此 B05 `FILE`、Work Task 附件、八类 CRM 目标附件和 OA 提交前文件不再是“未实现业务接缝”。默认 production 文件 profile 仍禁用，真实 storage/scanner readiness 仍需部署证据，但这是运行环境门槛，不应伪装成新的业务批次。

当前主要缺口已从“单域根、状态和附件能否安全保存”转向“一个已授权的业务上下文如何组合多个已实现域”。仓库已分别拥有 Contact、Opportunity、Contract、Invoice、Receivable、ReceivablePlan 和 ReturnVisit 根，它们都保存同 tenant `customer_id`、使用独立读权限和数据范围，也都已有 customer 路径索引；B25a 已补齐客户详情中的权限感知固定读模型。

B25 因此选择一个最小、只读、不产生外部副作用的纵切：**B25a 客户业务概览**。它不建立新的共享数据范围，也不使用 Customer 可见性代替任一来源域的读授权。

## 2. 剩余接缝总账

| 接缝 | 当前事实 | 决策 | 理由 |
| --- | --- | --- | --- |
| 客户上下文的跨域概览 | 七个关联域已实现且有 same-tenant customer 关系 | **B25a 已实现** | 固定本地读模型复用各来源 access resolver、字段策略、可见性 SQL 和索引，不建立共享范围 |
| Customer/Contact 公海自动收回的生产调度 | 单次有界扫描、公平游标、并发仲裁和 B26a 多副本 durable schedule/run 已实现 | **B26a 已实现** | 使用数据库 UTC、lease/fence、重试和安全可观测性；不伪造 human caller |
| Work 重复任务、自动提醒和逾期升级 | B27a/B27b/B27c 的 series、reminder、站内通知和 EMAIL outbox 内核已实现 | **[B27](batch-B27.md) 已实现** | 四类事实保持独立，不把 best-effort event 或 provider acceptance 冒充可靠到达/已读 |
| OA 公告定时发布与外部通知 | B28a/B28b 已实现一次性 publication occurrence 与 closed Work/OA EMAIL source | **[B28](batch-B28.md) 已实现** | 公告发布、站内已读和 provider 受理保持独立 |
| B23 外部企业事实采用到 Customer | B23e 已实现身份占用、preview/re-exact 写入、provenance 与 retention | 继续受真实 source policy/conformance 阻断 | 当前没有经审查且允许 `ADOPT_SELECTED_FACTS` 的真实来源 |
| B22 呼叫事实、媒体与真实 adapter | connector 控制面、core durable foundation、私有 oracle 和 Asterisk transport canary 已有 | 继续受 real-provider runtime conformance 阻断 | 不能用开源核心中的合成 fixture 代替真实线路、双腿媒体、密钥和确认删除证据 |
| 文件的业务覆盖扩展 | Product/ReturnVisit FILE、MarketingForm FILE、公告附件和 OA 提交后协作附件曾未纳入 B24c | **[B29](batch-B29.md) 已实现** | 四类主体、发布/终态冻结、字段生命周期和父锁序已分别证明 |
| CSV/Excel 导入导出与批量迁移 | 尚无独立数据集权限、脱敏、批量写入回滚或文件任务契约 | **[B30a](batch-B30.md) 只冻结有界 Customer CSV 导出** | 不允许通过 B24 附件流绕过单记录数据范围和字段掩码；导入/Excel/迁移继续后置 |

上表不是产品功能愿望清单。只有在现有文档或代码中已经明确后置、且会影响下一批选择的接缝才进入总账。

## 3. B25a 用户故事与范围

作为一个已有权读取某客户的成员，我希望在客户详情中一次看到我当前可见的直接联系人、开放商机、有效合同、待开票申请、有效回款、有效回款计划和回访数量/金额摘要；我没有权限的来源不应泄露记录是否存在，一个来源不可用也不应阻止其他可用来源返回。

B25a 只纳入：

1. 一个从已授权 Customer 根开始的固定概览端点；
2. 七张服务端固定定义、顺序稳定的来源卡片；
3. 每个来源自己的当前读能力、数据范围、字段访问和生命周期谓词；
4. 同一个只读 `REPEATABLE_READ` 快照中的字段策略和 SQL 聚合；
5. Contact 列表缺失的显式 `customerId` 过滤，使七个来源都能通过自己的既有列表端点下钻。

B25a 不保存任何面板状态、卡片配置或缓存结果，不创建新聚合、新授权或新数据范围。

## 4. HTTP 与返回契约

实现路由冻结为：

```text
GET /api/v1/customers/{customerId}/business-overview
```

- HTTP 入口要求 `customers:read`，应用层使用 `CustomerAccessResolver` 重复检查活动成员关系、tenant 和 Customer READ 数据范围；
- `customerId` 是正数本地身份，tenant 仍只能从 `AuthenticatedCaller` 派生；
- 建议使用 `UseCaseOperation.CUSTOMER_BUSINESS_OVERVIEW_GET` 和尚未占用的稳定 scene code `6019`；
- 返回 `customerId` 和七张固定卡片，不返回 customer 名称、字段值或任一来源记录 ID；客户本身由既有详情端点返回；
- 卡片只有 `AVAILABLE` 和 `UNAVAILABLE`。`AVAILABLE` 显式返回零或正的数量及该卡片定义的金额；`UNAVAILABLE` 必须整体省略数量、金额和不可用原因；
- 卡片顺序是公共契约，不接受 caller 提交的来源、字段、分组、排序、SQL、脚本或公式。

不设置新的 `customer-overview:*` 或 `reports:*` 权限。这是客户详情上下文的固定读模型，不是可配置报表。

## 5. 固定卡片目录

| 卡片 | 聚合口径 | 必须的来源读能力 | 必须可读且未脱敏的字段 |
| --- | --- | --- | --- |
| `CONTACT` | 直接 `customer_id` 匹配且 `ACTIVE` 的联系人数量；排除公海和删除记录 | `contacts:read` + 当前 Contact READ 数据范围 | `customer`、`status` |
| `OPEN_OPPORTUNITY` | 直接客户的 `ACTIVE + OPEN` 商机数量和 `estimated_amount` 合计 | `opportunities:read` + 当前 Opportunity READ 数据范围 | `customer`、`status`、`outcome`、`estimated_amount` |
| `ACTIVE_CONTRACT` | 直接客户的 `ACTIVE` 合同数量和 `total_amount` 合计；不计 `VOIDED/DELETED` | `contracts:read` + 当前 Contract READ 数据范围 | `customer`、`status`、`total_amount` |
| `PENDING_INVOICE` | 直接客户的 `PENDING` 开票申请数量和 `amount` 合计 | `invoices:read` + 当前 Invoice READ 数据范围 | `customer`、`status`、`amount` |
| `ACTIVE_RECEIVABLE` | 直接客户的 `ACTIVE` 回款数量和 `amount` 合计 | `receivables:read` + 当前 Receivable READ 数据范围 | `customer`、`status`、`amount` |
| `ACTIVE_RECEIVABLE_PLAN` | 直接客户的 `ACTIVE` 回款计划数量和 `planned_amount` 合计；不计算已收或剩余金额 | `receivable-plans:read` + 当前 ReceivablePlan READ 数据范围 | `customer`、`status`、`planned_amount` |
| `RETURN_VISIT` | 直接客户的 `ACTIVE` 回访数量；不以节点本地时间推导“未来/逾期” | `return-visits:read` + 当前 ReturnVisit READ 数据范围 | `customer`、`status` |

“直接客户”只指来源根上已由 tenant 复合外键约束的 `customer_id`。B25a 不通过 Contact 再扩展 Opportunity，不通过 Contract 再扩展 Invoice/Receivable，不沿 Activity 的 related records 或 Work Task 的可选 CRM 关系做传递闭包。这避免一个弱关联静默扩大面板范围。

`ACTIVE_RECEIVABLE_PLAN` 只聚合计划根的权威 `planned_amount`。`received_amount`、`remaining_amount` 和 `progress` 依赖当前回款集合；在没有同时冻结 Receivable 权限交集之前，B25a 不在计划卡中暴露这些派生量。

## 6. 授权、字段与安全失败

### 6.1 先证明 Customer，再运行来源

概览不能把隐藏客户和“可见但没有关联记录”混成可观测的两种结果。应用先使用已有 Customer READ 守卫证明 exact customer 对 caller 可见；失败时整个请求返回与 Customer 详情一致的安全拒绝，且不调用任一来源聚合。只有 Customer 已证明可见后，真实零记录才能以 `AVAILABLE + 0` 返回。

### 6.2 每张卡独立取交集

每张卡片都必须独立执行：

```text
active human membership
  ∩ source *:read authority
  ∩ source READ data scope
  ∩ direct customer relation
  ∩ source lifecycle predicate
  ∩ required B05 fields READ with mask NONE
```

只缺某个来源权限、数据范围或字段时，该卡是不带原因的 `UNAVAILABLE`，其他卡继续返回。不能使用 `customers:read`、tenant owner、客户负责人或另一个来源的数据范围替代当前来源。

只有经识别的 `ACCESS_DENIED` 类业务结果可以降级为 `UNAVAILABLE`。数据库、字段目录损坏、未知异常或代码缺陷使整个请求失败；不得捕获 `Throwable` 或把基础设施故障伪装成用户无权。

### 6.3 关系字段也受治理

即使 caller 已知道 customer ID，来源根与 Customer 的关联数量仍是该来源的字段事实。因此每张卡都要求来源 `customer` 字段可读且未脱敏；状态、结果和金额字段按第 5 节取交集。隐藏、任意 mask 或字段不存在都使整张卡 `UNAVAILABLE`，不返回部分数量或只删掉金额。

## 7. 查询、事务与分层

- 应用新增 `CustomerBusinessOverviewService`，由 `QueryServiceTemplate` 在一个只读 `REPEATABLE_READ` 事务中编排 Customer 守卫、七个 source access resolver、字段策略与聚合；
- 不创建可以跳过所属域的 `CrossDomainRepository`、`GenericOverviewRepository` 或动态表目录。Contact、Opportunity、Contract、Invoice、Receivable、ReceivablePlan 和 ReturnVisit 各自的 domain repository 增加一个显式、类型化的 customer summary 能力；
- 每个持久化适配器使用固定 SQL 在 MySQL 内完成可见性谓词、customer 谓词、生命周期谓词和聚合；不先分页或加载聚合根再在 JVM 过滤/求和；
- 不调用现有 HTTP Controller/Facade 拼面板，不使用 Redis 保存授权或聚合结果；
- API 层使用 `CustomerBusinessOverviewResponse`，application 使用 `CustomerBusinessOverviewVO`，domain 使用来源专属 summary 投影；不返回 `Map<String,Object>` 或 mapper PO；
- 金额保持各来源已有的两位小数与 `BigDecimal` 语义，不把 Opportunity `DECIMAL(18,2)` 或其他来源 `DECIMAL(22,2)` 窄化为 `double`；空聚合返回 `0.00`。

现有七个来源表均有 `(tenant_id, customer_id, status, ...)` 路径索引，因此 B25a 预期不需要 Flyway 迁移。正式实现必须在真实 MySQL 中提交七条聚合 SQL 的 `EXPLAIN` 回归；如果实际可见性谓词仍需新索引，只能以当时的下一个 Flyway 版本新增，不修改 V15/V34/V38/V40/V42。

## 8. Contact 下钻补缝

Opportunity、Contract、Invoice、Receivable、ReceivablePlan 和 ReturnVisit 的既有 query request 已有 `customerId`，Contact 列表目前没有。B25a 将 `ContactQueryRequest.customerId` 作为同一纵切的最小下钻补缝，但它不改变概览的授权结论：

1. 请求值必须为正数；
2. 列表查询先解析 `contacts:read` 及 Contact READ 数据范围；
3. 必须证明 exact Customer 在当前 tenant 中对 caller 可见，并要求 Contact `customer` 字段可读且未脱敏；
4. `customer_id` 谓词与 Contact 生命周期、公海排除和数据范围在 SQL 计数/分页之前同时生效；
5. 不能先分页 Contact 再在 JVM 删掉其他 Customer，也不能因为 caller 看得到 Customer 就扩大 Contact 范围。

## 9. 实现任务卡

### 9.1 B25a 唯一交付

1. 增加 `6019` operation、概览 Request/VO/Response 边界和 `GET /api/v1/customers/{customerId}/business-overview`；
2. 增加七个来源专属 summary 投影及 repository 方法，保持来源域所有权；
3. 实现 Customer-first 守卫、每卡独立授权/字段交集、有界降级和同快照聚合；
4. 为 Contact query 增加安全的 `customerId` 过滤，不改动其他列表契约；
5. 补充 application 单元测试、REST 路由/安全测试、MySQL/Testcontainers 权限组合与 `EXPLAIN` 回归；
6. 实现后更新 README 中 B10 的跨域面板状态、API reference、architecture、object layering、`llms.txt` 和新的 `docs/acceptance/B25a.md`。

B25a 已按本任务卡完成；实现细节和真实 MySQL 证据见 [B25a 验收记录](docs/acceptance/B25a.md)。本规划不预占 B25b，后续是否扩展 Opportunity/Contract 上下文、timeline 或相关列表组合仍需独立冻结边界。

## 10. 验收门槛

B25a 完成必须同时证明：

1. 隐藏、跨 tenant、`IN_POOL` 或已删除 Customer 在七个来源查询前就使整个概览安全失败；
2. 根 Customer 可见且某来源真实为空时，该卡是 `AVAILABLE` 且返回 `0`/`0.00`；
3. 缺少任一来源能力、数据范围或必需字段访问时，只有该卡变成不含数据和原因的 `UNAVAILABLE`；
4. SELF、DEPARTMENT、DEPARTMENT_SUBTREE 和 TENANT 在七个来源中独立生效，协作者规则只在所属域本来支持时生效；`B25aIntegrationTest` 逐源覆盖四种范围、每卡必需字段，以及 Contact/Opportunity/Contract 协作者不会扩大其他四个来源；
5. 相同 Customer 下的不可见来源记录不进入数量、金额或 SQL total，而不是聚合后再过滤；
6. 直接 customer 关系与生命周期口径精确符合第 5 节，不沿合同、联系人、Activity 或 Work Task 做传递统计；
7. 来源数据在一次请求中并发变更时，所有卡与字段策略仍来自同一 `REPEATABLE_READ` 快照；`B25aIntegrationTest` 在根 Customer 查询后暂停请求、提交 Contact、再放行来源聚合，以确定性证明该语义；
8. Contact `customerId` 过滤在 SQL 计数和分页前应用 Customer 可见性、Contact 数据范围、字段权限、活动状态与公海排除；`B25aIntegrationTest` 覆盖 Customer 不可见与 Contact 不可见两侧的独立失败关闭；
9. 七条聚合 SQL 在真实 MySQL 上保留 tenant/customer 路径索引候选且不出现无界全表扫描、根聚合加载或循环内查询；
10. `sh ./mvnw test` 和 `CI=true sh ./mvnw test` 全部通过，差异不引入新的 secret、敏感日志、客户可控权限或 tenant 来源。

## 11. 明确非目标

B25a 不包含：

- 任意对象类型、任意字段、动态卡片、客户自定义 SQL 或通用 graph query；
- 返回关联记录 ID/名称、在一个端点内分页七类记录、跨域排序或统一 cursor；
- Activity timeline、Work Task、Approval、Stage、Attachment、FILE 字段、Lead conversion 历史或 MarketingForm 归因的组合；
- 客户层级子树汇总；父客户的概览只聚合父根 ID，不包含子客户业务；
- 报表定义、仪表盘偏好、钻取缓存、物化视图、数据仓库或导出；
- 任何命令、自动跟进、通知、调度、外部 HTTP 调用或新的 after-commit 副作用；
- 为了面板方便而改变七个来源根的状态、可见性、金额真相或复合外键。

## 12. 实施顺序

1. 先保留当前 Customer 详情和七个来源 query 的权限回归，建立与概览独立的预期 oracle；
2. 逐个在所属 repository 增加 customer summary SQL 并用真实 MySQL 证明数据范围、生命周期与索引；
3. 完成 Customer-first 应用编排和部分不可用语义，再接入 API/Facade/REST 和 `6019` 错误场景；
4. 最后补齐 Contact `customerId` 下钻、HTTP 安全、并发快照、跨 tenant 与完整 Docker 套件；
5. B25a 已在验收记录中回写实际命令、测试结果和无迁移结论。
