# B17 报表与分析批次规划

> 基线：`4708eaa feat(B17a): 实现核心报表`
>
> 对账日期：2026-07-18
>
> 状态：B17a、B17b、B17c 已完成

## 1. 批次目标

B17 在不扩大任何业务对象可见范围的前提下，提供商业概览、固定趋势/排名和受限的个人自定义报表。

本批次坚持以下边界：

- `reports:read` 只允许进入报表能力，不建立跨对象的共享数据范围。
- 每次执行都使用已验证的 `AuthenticatedCaller`，从 caller 派生 `TenantId`，并重新解析所选来源的 `*:read` 能力和当前数据范围。
- 计数、金额、时间分桶、状态分桶和负责人分组都受 B05 当前字段权限约束；参与筛选、分组或聚合的字段必须可读且未脱敏。
- 一个请求内的字段策略读取和聚合查询运行在同一个只读 `REPEATABLE_READ` 快照中。
- 只执行服务端定义的指标或经过类型校验的有界定义；请求、数据库记录和 JSON 均不能提供 SQL、表名、列名、脚本或公式。
- 聚合在数据库内完成，不先加载全部业务聚合到 JVM，也不通过内部 HTTP 调用现有业务端点拼装报表。

## 2. 范围拆分

| 子批次 | 状态 | 交付物 | 数据库迁移 |
| --- | --- | --- | --- |
| B17a | 已完成 | 核心商业概览：商机漏斗、有效合同、活动回款 | 无 |
| B17b | 已完成 | 固定商业趋势、按负责人排名 | V48 来源日期索引 |
| B17c | 已完成 | 报表字段目录、个人自定义报表定义、受限执行 | V49 报表定义表 |

B17b 和 B17c 分开交付。B17c 可以复用 B17b 的日期范围、粒度、聚合值和 SQL 安全白名单，但不得依赖 B17b 的 Controller、Request 或 Response 类型。

## 3. 端点对账

### 3.1 B17 对外端点总账

| 子批次 | 方法与路径 | 权限 | 状态 | 说明 |
| --- | --- | --- | --- | --- |
| B17a | `GET /api/v1/reports/core` | `reports:read` + 三个来源的 `*:read` | 已实现 | 保持现有路径和响应兼容，不改名、不改为 POST |
| B17b | `POST /api/v1/reports/trends` | `reports:read` + 所选来源的 `*:read` | 已实现 | 单一固定指标的时间趋势 |
| B17b | `POST /api/v1/reports/rankings` | `reports:read` + 所选来源的 `*:read` | 已实现 | 单一固定指标的负责人排名 |
| B17c | `GET /api/v1/reports/catalog?objectType=...` | `reports:read` + 所选来源的 `*:read` | 已实现 | 返回当前 caller 可用于报表的字段和操作，不复用原始字段元数据响应 |
| B17c | `POST /api/v1/reports/definitions/query` | `reports:read` | 已实现 | 分页查询当前成员自己的活动定义，只返回摘要 |
| B17c | `POST /api/v1/reports/definitions` | `reports:write` + 所选来源的 `*:read` | 已实现 | 创建个人报表定义 |
| B17c | `GET /api/v1/reports/definitions/{definitionId}` | `reports:read` + 所选来源的 `*:read` | 已实现 | 获取自己的定义并复核当前字段可用性 |
| B17c | `PUT /api/v1/reports/definitions/{definitionId}` | `reports:write` + 所选来源的 `*:read` | 已实现 | 乐观锁替换自己的定义 |
| B17c | `DELETE /api/v1/reports/definitions/{definitionId}?version=...` | `reports:write` | 已实现 | 乐观锁软删除自己的定义；删除不要求仍能读取来源字段 |
| B17c | `POST /api/v1/reports/definitions/{definitionId}/run` | `reports:read` + 所选来源的 `*:read` | 已实现 | 在当前权限和数据范围下执行自己的定义 |

对账结论：B17 共 10 个端点，现有 1 个，B17b 新增 2 个，B17c 新增 7 个。所有新查询使用 POST body 或显式 GET query；不增加含糊的 `GET /api/v1/reports/{id}`，避免与固定报表路径冲突。

### 3.2 现有相邻端点与复用边界

| 现有端点 | 对账结论 |
| --- | --- |
| `POST /api/v1/opportunities/summary` | 是商机域内摘要，只受商机能力控制；保留原契约。B17 可以复用仓储可见性谓词，不能调用或冒充该 HTTP 端点。 |
| `POST /api/v1/opportunities/query` | 是记录分页，不应用于 JVM 全量拉取后聚合。 |
| `POST /api/v1/contracts/query` | 是合同记录分页，没有 B17 固定趋势或排名语义。 |
| `POST /api/v1/receivables/query` | 是回款记录分页，没有 B17 固定趋势或排名语义。 |
| `GET /api/v1/fields/objects/{objectType}` | 返回字段管理元数据；B17c 的 catalog 只返回当前 caller 可读、未脱敏且适合分组/聚合的子集及报表操作。 |
| `/api/v1/saved-views/**` | 保存视图服务于业务列表筛选。B17c 可复用 `FilterCondition` 和类型化操作符规则，但定义、依赖、生命周期和端点保持独立。 |

### 3.3 路由安全对账

当前 `SecurityConfig` 只显式保护 `GET /api/v1/reports/**`。实现 B17b/B17c 时必须在通用匹配器之前补齐并测试：

- `POST /api/v1/reports/trends`、`POST /api/v1/reports/rankings`、`POST /api/v1/reports/definitions/query`、`POST /api/v1/reports/definitions/*/run` 要求 `reports:read`；
- `POST /api/v1/reports/definitions`、`PUT /api/v1/reports/definitions/**`、`DELETE /api/v1/reports/definitions/**` 要求 `reports:write`；
- 应用层重复检查对应能力、活动成员关系、定义所有权和来源能力，不能只依赖 HTTP 路由；
- 静态路径 `definitions/query` 与 `definitions/{definitionId}` 必须分别做路由集成测试，防止安全匹配器误把读取当写入或反之。

## 4. B17a 基线验收

B17a 已由基线提交交付，后续子批次不得破坏：

- `GET /api/v1/reports/core` 返回 OPEN/WON/LOST 商机数量与预计金额、ACTIVE 合同数量与金额、ACTIVE 回款数量与金额；
- INVALID 商机、VOIDED/DELETED 合同和 DELETED 回款不计入；
- 必须同时具备 `opportunities:read`、`contracts:read`、`receivables:read`，缺少任一来源时整份核心报表拒绝；
- `estimated_amount`、`outcome`、`total_amount`、`status`、`amount` 任一隐藏或脱敏时整份核心报表拒绝；
- 三个来源各自使用当前数据范围，并在一个一致性快照内聚合。

B17b/B17c 开始前先保留现有 B17a 集成测试作为回归门。

## 5. B17b：固定趋势与排名

### 5.1 固定指标

B17b 只开放以下服务端枚举，两个端点共享同一指标定义：

| 指标 | 来源对象 | 记录条件 | 业务日期 | 数量 | 金额 | 排名维度 |
| --- | --- | --- | --- | --- | --- | --- |
| `WON_OPPORTUNITY` | OPPORTUNITY | `status=ACTIVE AND outcome=WON` | `outcome_time` 的 UTC 日期 | 商机数 | `estimated_amount` 合计 | `owner_actor` |
| `ACTIVE_CONTRACT` | CONTRACT | `status=ACTIVE` | `order_date` | 合同数 | `total_amount` 合计 | `owner_actor` |
| `ACTIVE_RECEIVABLE` | RECEIVABLE | `status=ACTIVE` | `received_date` | 回款数 | `amount` 合计 | `owner_actor` |

`ACTIVE_CONTRACT` 中 `order_date` 为空的合同不进入带日期范围的趋势或排名；这不会改变 B17a 核心报表对活动合同的全量统计。响应不额外返回“缺少日期数量”，避免形成绕过字段权限的旁路统计。

### 5.2 趋势契约

`POST /api/v1/reports/trends`

请求概念字段：

- `metric`：上述固定指标之一；
- `from`、`to`：包含首尾的 ISO `LocalDate`；
- `granularity`：`DAY`、`WEEK` 或 `MONTH`。

响应概念字段：

- 原样返回规范化后的 `metric`、`from`、`to`、`granularity`；
- `points[]` 按 `periodStart` 升序，元素为 `{ periodStart, count, amount }`；
- 范围内没有记录的周期由应用层补零，金额始终使用与源金额一致的两位小数语义；
- 周以 ISO 周一开始，月以自然月开始，日期/日期时间统一按项目已配置的 UTC 语义分桶。

边界：最多 120 个桶且日期跨度最多 10 年；超限直接返回稳定参数错误，不截断。

### 5.3 排名契约

`POST /api/v1/reports/rankings`

请求概念字段：

- `metric`、`from`、`to` 与趋势一致；
- `limit` 默认 10，范围 1..100。

响应概念字段：

- `items[]` 为 `{ rank, ownerActorId, count, amount }`；
- 固定按 `amount DESC, count DESC, ownerActorId ASC` 排序，`rank` 是稳定的顺序序号；
- 不返回成员姓名，避免借报表扩大组织目录访问；有权限的客户端可通过组织端点解析 actor；
- 不返回 top-N 之外的汇总或“其他”桶，避免额外泄露。

排名日期跨度最多 366 天。负责人字段是分组键，因此 `owner_actor` 必须可读且未脱敏。

### 5.4 字段与来源权限矩阵

| 指标 | 趋势必需字段 | 排名额外字段 | 来源能力 |
| --- | --- | --- | --- |
| `WON_OPPORTUNITY` | `outcome`, `outcome_time`, `estimated_amount`, `status` | `owner_actor` | `opportunities:read` + 当前商机读取数据范围 |
| `ACTIVE_CONTRACT` | `status`, `order_date`, `total_amount` | `owner_actor` | `contracts:read` + 当前合同读取数据范围 |
| `ACTIVE_RECEIVABLE` | `status`, `received_date`, `amount` | `owner_actor` | `receivables:read` + 当前回款读取数据范围 |

任一必需字段不存在、已停用、`HIDDEN` 或 mask 不是 `NONE` 时，所请求的整个趋势或排名失败；不返回部分序列。只解析所选指标的来源，不要求 caller 同时拥有其他两个来源能力。

### 5.5 实现切片

1. 在 domain 定义固定指标、时间粒度、日期范围和值投影；校验值对象不依赖 API 或持久化类型。
2. 为 Opportunity、Contract、Receivable 各自的仓储端口增加趋势和负责人排名能力，继续接收显式 `TenantId` 和该对象的可见性条件。
3. 在各自持久化适配器内使用固定 SQL 分组；禁止把客户端字符串拼接为列名、排序或表达式。
4. 应用服务用 `QueryServiceTemplate` 编排来源 access resolver、字段 access resolver 和所选仓储；不创建跨对象 `ReportRepository` 来绕过对象边界。
5. API/Facade/Controller 增加独立 Request、VO、Response 映射；建议 operation code 使用 `6710`（趋势）和 `6711`（排名）。
6. 补齐 `SecurityConfig` 的 POST 读取路由，并更新 README、README.en、API reference、architecture、llms.txt。

### 5.6 B17b 验收

- 三个指标的 DAY/WEEK/MONTH 边界、零桶、金额精度和固定排序正确；
- 首尾日期包含语义正确，DATETIME 在 UTC 零点使用半开区间，没有跨日重复或遗漏；
- 合同空 `order_date` 不计入趋势/排名，但仍计入 B17a；
- SELF、DEPARTMENT、DEPARTMENT_SUBTREE、TENANT 对每个来源独立生效；
- 缺少 `reports:read`、来源能力、活动成员关系或字段访问时失败关闭；
- 金额、日期、状态/结果、负责人任一被隐藏或脱敏时失败关闭；
- 同 tenant 和跨 tenant 数据、软删/作废/无效数据均按定义隔离；
- 相同金额与数量的排名按 actor ID 稳定，limit 边界正确；
- 字段策略与数据在同一 `REPEATABLE_READ` 快照内；
- MySQL Testcontainers 覆盖实际聚合 SQL 和必要的执行计划检查；`CI=true sh ./mvnw test` 通过。

## 6. B17c：个人自定义报表

### 6.1 首期能力边界

B17c 首期只支持 `OPPORTUNITY`、`CONTRACT`、`RECEIVABLE` 三种单一来源对象，与 B17a/B17b 的已治理来源保持一致。一个定义不能跨对象 join。

每个定义包含：

- 名称、可选描述、来源 `objectType`；
- 一个必填 DATE/DATETIME 时间字段和 `DAY/WEEK/MONTH` 粒度；
- 零个或一个普通维度字段；
- 1..8 个聚合度量；
- 最多 20 个字段不重复的类型化过滤条件；
- 创建成员、状态、版本和审计时间。

首期普通维度只允许 `SELECT`、`BOOLEAN`、`USER`、`DEPARTMENT`。不支持 `MULTI_SELECT`，避免一条记录进入多个桶；不支持 `RELATION`，避免绕过关联对象的独立数据范围；不支持自由文本，避免无界高基数结果。

过滤字段同样不支持 `RELATION`：catalog 不返回该类型，定义校验和来源 SQL 执行器都必须失败关闭，避免通过不可见关联 ID 的聚合结果形成侧信道。

首期度量只允许：

- `COUNT`：不引用字段；
- `SUM(fieldCode)`：字段必须为 `INTEGER` 或 `DECIMAL`。

不支持 AVG/MIN/MAX、去重计数、公式、脚本、同比/环比、跨对象 join、文件导出、共享、收藏、定时任务、外部 BI、物化视图或 Redis 结果缓存。

### 6.2 定义所有权与容量

- 定义显式 tenant scoped，并属于创建时的 `memberId`；普通成员、tenant owner 和管理员都不能读取或修改其他成员的定义。
- 每个 `(tenant, member, objectType)` 最多 100 个活动定义。
- 名称去除首尾空白后非空、最多 100 个 Unicode code point；规范名使用与项目一致的 NFKC + lowercase 规则并完整保存，活动名称唯一约束使用固定 SHA-256 键仲裁，避免 NFKC 扩张超过索引列长度。
- 描述最多 500 个 Unicode code point。
- 定义使用 `ACTIVE/DELETED` 状态和乐观锁；删除后不可运行或更新。
- 创建计数、规范名唯一性、字段依赖登记和写入必须在取得现有 `CustomizationObjectLock` 的 `(tenant, objectType)` 锁后完成；数据库唯一约束负责最终仲裁。

### 6.3 报表 catalog

`GET /api/v1/reports/catalog?objectType=...` 不是 B05 字段管理端点的别名。它应返回：

- 当前支持的来源对象；
- 可用时间字段及允许粒度；
- 可用普通维度字段；
- 可用 SUM 字段；
- 可用过滤字段、类型、选项和操作符；
- 固定的数量度量。

catalog 只包含当前活动、searchable、可读且未脱敏的字段。它必须先解析来源 `*:read` 和数据范围；即使暂时没有可见记录，也不能跳过来源能力检查。返回的选项来自受治理字段定义，不扫描业务记录生成高基数候选值。

### 6.4 定义 CRUD

- `query` 只分页返回当前成员自己的活动定义摘要，关键词只匹配规范化名称，`size` 最大 100；
- `get/create/update` 都用当前 catalog 重新校验时间字段、维度、度量和过滤条件；
- 过滤复用 `FilterCondition` 的参数数量及类型兼容规则，但 B17c 自己施加 20 条上限；
- 所有字段 code 在持久化前解析为当前字段定义，domain 保存有界、已验证的命名配置，不能保存原始 SQL 或任意表达式；
- `update/delete` 同时检查 tenant、创建成员、活动状态和 version，零行更新转换为稳定的版本冲突或不存在错误；
- 列表只返回摘要。详情若引用字段当前已隐藏、脱敏、停用或不存在，则失败关闭；删除仍允许，以便用户清理失效定义。

### 6.5 字段生命周期依赖

活动报表定义是 B05 字段依赖方：

- 时间、维度、SUM 或过滤引用的字段不能在定义仍活动时被删除、停用或关闭 searchable；
- `FieldCustomizationServiceImpl` 的现有依赖检查要注入 `ReportDefinitionRepository`，并调用 `existsReferencingField(tenantId, objectType, fieldCode)`；
- 定义写入和字段生命周期写入使用相同的 `(tenant, objectType)` 数据库守卫，避免检查后并发插入形成悬空引用；
- 字段角色访问级别或 mask 改变不阻止配置变更，但会使受影响 caller 的详情和执行立即失败关闭。

### 6.6 执行契约

`POST /api/v1/reports/definitions/{definitionId}/run`

请求只提供执行范围：

- `from`、`to` 为包含首尾的 ISO 日期；
- 最多 120 个时间桶且日期跨度最多 10 年。

执行流程：

1. 按 tenant、当前 member 和 ID 读取活动定义；
2. 解析来源对象的 `*:read` 和当前数据范围；
3. 在同一查询快照中重读字段目录和有效字段权限；
4. 校验时间、维度、SUM 和过滤字段均活动、searchable、可读且未脱敏；
5. 按字段类型规范化所有过滤值；
6. 通过来源专属执行器生成参数化 SQL，应用 tenant、数据范围、生命周期和过滤谓词；默认排除 `DELETED`，但保留查询本来可见的 `INVALID` 商机和 `VOIDED` 合同，除非定义用字段安全的条件进一步过滤；
7. 返回按 `periodStart ASC`、维度规范键稳定排序的结果；SELECT key 的过滤、分组和排序使用 `utf8mb4_bin` 精确语义，大小写不同的合法选项不能合并。

响应概念结构：

- `definitionId`、`definitionVersion`、`objectType`、`from`、`to`、`granularity`；
- `columns[]` 描述时间、可选维度和度量；
- `rows[]` 每行包含 `periodStart`、可选的 `{ key, label }` 维度和值映射；
- 最大 500 行，超过时整次执行以稳定的结果过大错误失败，不静默截断；
- SELECT/BOOLEAN 的 `label` 来自字段目录；USER/DEPARTMENT 只返回稳定 `key` 且 `label=null`，不额外扩大组织目录权限。

动态字段执行只能通过 `(objectType, fieldCode)` 服务端字段描述符选择预定义根列或参数化自定义值连接。MyBatis `${...}`、客户端排序片段、表名/列名字符串和 JSON 内表达式全部禁止。

### 6.7 持久化模型

新增 `ReportDefinition` 聚合和 `ReportDefinitionRepository` 端口，持久化表至少包含：

- `id`, `tenant_id`, `creator_member_id`；
- `name`, 完整 `normalized_name`, 固定长度规范名哈希、`description`, `object_type`；
- `time_field_code`, `granularity`, `dimension_field_code`；
- `filters_json`, `measures_json`；
- `status`, `version`, `created_time`, `updated_time`。

JSON 只保存经过 domain 构造器验证的有界 Filter/Measure 值。PO 使用命名 `*Json` 字段，PO converter 显式解码并通过命名 snapshot `reconstitute`；读取损坏 JSON 失败关闭。迁移需包含 tenant/member/object 活动名称唯一性和常用列表索引，不编辑既有迁移。

### 6.8 应用与操作码

建议预留：

| Operation | code |
| --- | --- |
| `REPORT_CATALOG_GET` | `6720` |
| `REPORT_DEFINITION_QUERY` | `6721` |
| `REPORT_DEFINITION_GET` | `6722` |
| `REPORT_DEFINITION_CREATE` | `6723` |
| `REPORT_DEFINITION_UPDATE` | `6724` |
| `REPORT_DEFINITION_DELETE` | `6725` |
| `REPORT_DEFINITION_RUN` | `6726` |

新增 `reports:write`，作为 tenant 可委派、非 data-scoped 的能力。`reports:read`/`reports:write` 都不能替代来源对象能力。

写入经 `CommandServiceTemplate`；查询、catalog 和执行经 `QueryServiceTemplate`。报告定义的 CRUD repository 与三个业务来源的聚合 executor 分离，不能让 ReportDefinitionRepository 直接查询业务表。

### 6.9 B17c 验收

- 端点路由和 `reports:read`/`reports:write` 分离正确，应用层重复授权；
- 定义只能由同 tenant 的创建成员查看、更新、删除和运行，owner 不自动旁路；
- 100 个活动定义上限、规范名并发唯一和乐观锁由 MySQL 最终仲裁；
- 非法字段类型、重复字段条件、超限条件/度量/时间桶/结果行全部失败；
- 创建和字段停用/删除并发时不存在活动悬空定义；
- 来源能力、数据范围和字段 access 在每次执行时重新解析，权限收回立即生效；
- HIDDEN 或任意 mask 的时间、维度、度量、筛选字段均使整次执行失败；
- 自定义字段过滤和 SUM 使用参数化查询，恶意 code/value 不能改变 SQL 结构；
- 跨 tenant、跨 member、DELETED 定义和软删业务记录均不可见；
- 定义读取、字段策略和聚合数据处于同一只读快照；
- 空结果、null 维度桶、UTC 边界、金额精度和稳定排序有覆盖；
- Flyway 空库迁移、上一版本升级和 MySQL Testcontainers 集成测试通过；
- 更新 README、README.en、docs/api-reference.md、docs/architecture.md、docs/object-layering.md（如新增命名角色）、docs/test-guide.md 和 llms.txt。

## 7. 实施顺序与提交边界

### B17b

1. 固定指标/范围/粒度 domain 模型与单元测试；
2. 三个来源仓储端口、MyBatis 聚合与持久化测试；
3. 应用授权、字段守卫、一致性快照测试；
4. API/Facade/Controller/SecurityConfig 和路由测试；
5. B17a 回归、全量 fast suite、Docker 集成、文档。

### B17c

1. catalog 类型规则、定义聚合、容量/所有权/版本测试；
2. 新 Flyway 迁移、PO/converter/mapper/repository；
3. B05 字段依赖接入及并发集成测试；
4. 来源专属执行器和 SQL 注入/边界测试；
5. 应用模板、权限复核和一致性快照；
6. API/Facade/Controller/SecurityConfig 与完整路由集成测试；
7. B17a/B17b 回归、全量 fast suite、Docker 集成、文档。

每个子批次独立提交和复审。不要把 B17c 迁移、定义 CRUD 或动态执行提前混入 B17b。

## 8. 完成定义

B17 完成需同时满足：

- 本文 10 个端点中 B17a 保持兼容，B17b/B17c 全部实现并进入 API reference；
- 所有聚合都保留来源对象的 tenant、数据范围、生命周期和字段安全边界；
- 没有任意 SQL/公式、跨对象 join、全量 JVM 聚合或通过 HTTP 自调用拼装；
- focused tests、`sh ./mvnw test` 和 `CI=true sh ./mvnw test` 全部通过；
- 迁移、PO、converter、mapper XML、domain 重建、权限目录和 SecurityConfig 对齐；
- diff 复核无敏感日志、跨租户访问、失效字段引用、未界定结果集或重复配置。
