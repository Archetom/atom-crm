# B31 B30a 后本地业务接缝盘点与有界 Contact CSV 导出边界规划

> 基线：`a5920f46 feat(B30a): 实现权限感知的有界 Customer CSV 导出`
>
> 对账日期：2026-07-24
>
> 状态：B31a 已实现并通过完整验收；B31b 尚未预占；Opportunity 销售管线与阶段目录已由 [B32](batch-B32.md) 单独冻结

## 1. 对账结论

B30a 已实现并完成验收记录。Customer 普通列表现在拥有一个独立数据范围、字段权限感知、同步且严格有界的 CSV 导出；它没有建立通用对象导出器、异步文件或导入模板。Customer 导出因此不再是“剩余本地业务接缝”，B31 也不把已经完成的 B30a 改名或扩写成平台能力。

B22、B23 和 B24 仍分别受真实呼叫 adapter、经审查的 source policy/conformance 和批准部署环境中的 storage/scanner 证据约束。这些边界已经失败关闭，缺少的是外部状态和部署证明，不是下一次普通本地代码提交能够补齐的业务纵切。

B30a 之后最清晰、可完全在既有 tenant、Contact 数据范围、字段策略和 MySQL 边界中闭合的本地接缝，是：

**B31a 权限感知的 Contact CSV 导出**。

Contact 已经拥有独立的 `contacts:read` 可见性、协作者规则、ContactPool 生命周期、字段目录、保存视图、关键字查询和可选 Customer/上级 Contact 关系。B31a 只为这个域增加独立的 `contacts:export` owner-derived 范围，并与普通 `contacts:read` SQL 可见性取交集；它不会把 B30a 的 Customer repository 泛化成动态对象仓储。

B31a 继续使用同步完成后再写 HTTP body 的严格边界：最多 5,000 行、50 个所选字段和 16 MiB。它可以与 B30a 共享纯 CSV 编码和进程资源 admission 原语，但 Contact 请求、授权、字段目录、关系验证、查询投影、repository 和响应文件名都保持显式分离。

## 2. B30a 后剩余本地业务接缝总账

| 接缝 | 当前事实 | B31 决策 | 理由 |
| --- | --- | --- | --- |
| Contact 及其他业务对象导出 | B30a 只实现 Customer；Contact 已有完整普通列表、字段策略、公海和保存视图语义 | **B31a 只冻结 Contact CSV** | 可以复用格式与资源上限，但必须独立证明 Contact 协作者、公海和 Customer/上级 Contact 关系过滤 |
| Lead/Opportunity/Contract 等其他对象导出 | 各域生命周期、关系字段、敏感值和数据范围不同 | B31a 不纳入；B31b 不预占 | Contact 通过不能自动证明其他域；仍禁止动态 `BusinessObjectType` 路由和通用 repository |
| CSV/Excel 导入与批量迁移 | 仍无行级预览、引用解析、幂等、批次原子性、补偿或回滚契约 | 继续后置 | B30a/B31a 的展示格式不是可逆导入协议，B24 上传也不是批量写入授权 |
| Opportunity 销售管线与阶段目录 | B10 仍接受有界 `pipelineCode/stageCode`，B11 阶段工作流不拥有销售管线目录 | 已由 [B32a](batch-B32.md) 冻结为独立 Opportunity 治理边界 | exact code、升级兼容、目录锁序和 B11 正交关系均由 B32 单独负责，不在 Contact 导出中改变 |
| Work 多责任人、子任务、依赖和检查清单 | 单责任人、协作、周期、提醒、通知和附件已实现 | 后置到独立 Work 设计 | 每人状态、完成仲裁、依赖图和 series 模板传播仍是新的聚合语义 |
| 日历、会议和会议室 | B26/B27 只有 durable 调度和 Work recurrence | 后置到 Calendar 批次 | 参与者响应、资源占用、冲突、取消与外部日历均不是 scheduler payload |
| 文件预览、OCR、分享和外部协作者 | B24/B29 只覆盖受治理原文件与内部主体 | 后置到内容处理或外部身份批次 | 衍生对象删除传播、解析器隔离、分享撤销和外部主体尚未建立 |
| 新通知通道与 CRM 自动触发 | B27/B28 只实现默认关闭的 typed EMAIL source | 后置到独立集成批次 | SMS、IM、Webhook 和 Calendar 各有独立 endpoint、同意、模板与歧义语义 |
| 更多上下文组合与跨域时间线 | B25a 已有 Customer 七来源固定概览，B15 已有单一可见 subject 的 Activity timeline | 继续后置 | 当前没有另一个已冻结的固定卡片、排序和权限故事；不建立通用 graph、feed 或跨域 repository |

该总账只收录仓库已经明确后置、并会影响下一实现选择的本地接缝。它不是产品愿望清单，也不把外部 readiness、每个现有端点的非目标或任意行业功能自动升级成 roadmap。

## 3. B31a 用户故事与范围

作为同时拥有 Contact 读取与导出权限的当前成员，我希望把一个与普通联系人列表条件一致的有界结果导出为安全 CSV；导出必须只包含 `contacts:export` owner scope 与 `contacts:read` 普通可见性的交集，并且只输出我明确选择的当前可读、未脱敏 Contact 标量字段。

B31a 只纳入：

1. 一个 Contact 专属、同步返回 CSV 的导出端点；
2. 独立且支持数据范围的 `contacts:export` 能力；
3. 与普通 Contact query 相同的 keyword、name、owner、customer、starred 和当前成员 saved-view 条件；
4. Contact 当前字段目录中的固定可导出标量类型；
5. Customer 与上级 Contact 关系过滤的现有目标可见性复核；
6. 一个只读 `REPEATABLE_READ` 快照中的字段、权限、保存视图、关系目标、可见 Contact 和全部输出单元格；
7. B30a 与 B31a 共用的进程内非排队 admission 上限；
8. 完整文档生成后的固定 HTTP 下载响应。

B31a 不保存导出定义、偏好、任务、文件、下载凭据、结果历史或“已下载”事实，不把 CSV 定义为联系人导入模板。

## 4. HTTP 与 operation 契约

实现路由冻结为：

```text
POST /api/v1/contacts/export/csv
```

operation 固定为：

```text
CONTACT_CSV_EXPORT = 6135
```

请求使用独立的 `ContactCsvExportRequest`，概念字段只有：

```text
fieldCodes  1..50 个有序且不重复的字段 code
keyword     与 ContactQueryRequest 相同
name        与 ContactQueryRequest 相同
ownerActorId
customerId
starred
savedViewId
```

请求不接受 `page`、`size`、tenant、actor、role、authority、数据范围、对象类型、ContactPool、分隔符、字符集、时区、文件名、排序字段、SQL、表达式或任意格式参数。

成功响应固定为：

```text
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename="contacts.csv"
Cache-Control: no-store
X-Content-Type-Options: nosniff
```

响应内容为 UTF-8 BOM、一个 header row 和零到 5,000 个 data row。header 第一列固定为 `contact_id`，其余列严格按请求 `fieldCodes` 顺序使用稳定字段 code；不使用可变字段 label、Customer/Contact 名称、保存视图名或 caller 提供的文件名。

任何认证、授权、字段、关系目标、查询、行数、字节数、编码或基础设施失败都必须在 HTTP body 开始前转换为现有安全 JSON 错误。成功后只剩写入已完整生成文档的本地 I/O，不得边查询数据库边开始 `200` body。

## 5. 授权、数据范围与生命周期

### 5.1 独立 Contact 导出能力

新增 `contacts:export`，tenant-assignable 且支持 `SELF`、`DEPARTMENT`、`DEPARTMENT_SUBTREE` 和 `TENANT` 数据范围。现有普通角色不自动获得它；tenant owner 继续只通过当前代码权威目录动态解析。

导出范围按 Contact owner-derived scope 解释，不自行加入 Contact collaborator。一次 B31a 导出必须同时满足：

```text
active human membership
  ∩ contacts:export effective owner scope
  ∩ contacts:read ordinary visibility
  ∩ Contact ACTIVE/non-pool predicate
  ∩ request filters and relation-target checks
  ∩ current Contact field READ with mask NONE
```

持久化查询必须同时应用两个已经解析的 predicate：

- `contacts:export` 只按当前 Contact owner 及 owner 的活动部门归属解释；
- `contacts:read` 保留既有 owner/department/collaborator READ 可见性；
- `contacts:export=TENANT` 不能扩大 `contacts:read=SELF`；
- `contacts:read=TENANT` 也不能让 `contacts:export=SELF` 导出协作但非本人负责的 Contact；
- 一个 Contact 协作者只可能满足 read 一侧，仍必须由 export owner predicate 接受。

`contacts:write`、`contacts:assign`、`contact-pools:*`、`customers:*`、`reports:read`、`files:read`、tenant 管理角色或 Contact 协作者身份都不能替代 `contacts:export`。

### 5.2 普通 Contact 与 ContactPool 分离

B31a 只导出 `status=ACTIVE` 的普通 Contact，固定排除 `IN_POOL` 和 `DELETED`。ContactPool 的管理员、可领取成员、前负责人、领取历史或池内可见性都不进入导出 predicate。

知道 pool、Contact ID 或负责人 ID 不产生普通 Contact 可见性。B31a 不提供 pool 参数，也不为 ContactPool 增加导出端点。

## 6. 查询条件与关系目标

### 6.1 与普通列表保持同一安全语义

keyword 候选继续取 Contact 服务端固定目录与当前 active/searchable/readable/unmasked 字段策略的交集；交集为空时返回 header-only CSV，而不是放宽候选。

- 非空 `name` 要求 `contact_name` 可读且未脱敏；
- `ownerActorId` 要求 `owner_actor` 可读且未脱敏；
- `starred` 仍是当前成员自己的 personal fact，只参与过滤，不授予记录可见性；
- `savedViewId` 只解析当前 tenant、当前成员自己的有效 Contact 视图，并重新验证活动、可搜索、可读且未脱敏字段；
- 固定过滤和保存视图条件必须在 5,001 行 probe 前进入同一 SQL predicate。

### 6.2 Customer 过滤

`customerId` 只作为过滤条件，不成为导出列或授权来源。使用它时必须同时：

1. 要求 Contact `customer` 字段当前可读且未脱敏；
2. 独立解析 `customers:read`；
3. 在同一 tenant 和查询快照内证明 exact Customer 对 caller 当前可见；
4. 将 `customer_id` predicate 在 Contact SQL 的计数/probe 前应用。

缺少 Customer 权限、目标不可见或跨 tenant 时，整次导出失败；不能把不可见 Customer 当成零结果，也不能因为 Customer 可见而扩大 Contact 范围。

### 6.3 saved-view 关系条件

Contact saved view 中的 Customer 和上级 Contact 条件继续只接受现有受支持的关系操作符。每个非空目标都必须在同一快照内重新验证：

- Customer 目标使用当前 `customers:read` 与 Customer 普通可见性；
- 上级 Contact 目标使用当前 `contacts:read` 普通可见性；
- 目标校验只允许构造过滤 predicate，不把关系 ID 或显示名加入 CSV；
- 一个不可见、删除、公海或跨 tenant 目标使整个请求失败，不得静默移除该条件。

## 7. 字段目录与单元格语义

### 7.1 首期可导出类型

除固定 `contact_id` 外，所选字段必须是 exact `CONTACT` 当前活动定义，调用成员对其具有 `READ + mask NONE`，且类型属于：

```text
TEXT
TEXTAREA
SELECT
DATE
DATETIME
INTEGER
DECIMAL
BOOLEAN
EMAIL
PHONE
URL
```

这允许在字段策略通过时导出 `contact_name`、`phone_number`、`telephone`、`job_title`、`email`、`website`、`description`、`next_contact_time`、`locked`、`deal_status`、`status`、`created_time`、`updated_time` 和相同标量类型的 custom Contact 字段。

以下内容首期显式排除：

- `MULTI_SELECT`、`FILE`、`USER`、`DEPARTMENT`、`RELATION` 和 `ADDRESS`；
- 内置 `tags`、`addresses`、`owner_actor`、`customer` 和 `parent_contact`；
- 协作者、层级、星标、ContactPool 成员关系与领取历史；
- 普通附件、FILE、Activity、Work、Customer 或其他域的显示值；
- 任何由关系名称、ID 列表、JSON 或逗号拼接猜测出的扁平单元格。

请求包含 unsupported、inactive、hidden、masked、重复或未知字段时，整次导出失败，不返回静默删列、空列或部分 CSV。`PHONE` 和 `EMAIL` 只有在当前字段策略完全未脱敏时才可导出；B31a 不绕过现有掩码。

### 7.2 确定性编码

- `contact_id` 与 INTEGER 使用无分组十进制；
- DECIMAL 使用 `BigDecimal.toPlainString()` 语义；
- BOOLEAN 固定为小写 `true`/`false`；
- DATE 固定 ISO `yyyy-MM-dd`；
- DATETIME 固定为既有服务端 UTC 值的 ISO 表示，不按浏览器或成员时区改写；
- SELECT 输出稳定 option code，不输出可变 label；
- null 输出空 cell；空字符串与 null 不承诺可逆区分；
- row separator 固定 CRLF，双引号按 `""` 转义，包含逗号、双引号、CR 或 LF 的 cell 使用 RFC 4180 quoting。

对 TEXT、TEXTAREA、EMAIL、PHONE、URL 和 SELECT cell，公式防护在 quoting 前执行。若值以 spreadsheet 公式前缀开始，或前导控制空白可隐藏该前缀，则在值前增加单引号文本哨兵。`+` 开头的电话号码同样按文本防护；该变换只影响 CSV 展示，不修改 Contact 数据，也不声明 round-trip。

## 8. 快照、查询与资源边界

### 8.1 单一只读快照

`ContactCsvExportService` 使用 `QueryServiceTemplate`。下列事实必须在一个只读 `REPEATABLE_READ` 事务中解析并使用：

- 活动成员资格与 `contacts:read`、`contacts:export` 两个有效范围；
- Contact 字段定义和当前有效字段访问；
- 当前成员的 saved view；
- `customerId` 或 saved-view Customer/上级 Contact 目标可见性；
- 最多 5,001 个候选 Contact ID；
- 全部 selected scalar cells 和最终 CSV bytes。

CSV 完整成功生成并通过字节上限后事务结束；REST 才开始写 body。慢客户端、取消或断开不会延长数据库事务，也不会触发第二次 repository 查询。

固定行顺序为 `contact_id ASC`。Contact repository 先以 exact tenant、双范围交集、普通生命周期和全部条件取得最多 5,001 个 ID；达到第 5,001 个时整体拒绝，不截断。随后在同一快照内按最多 200 个 ID 一批读取固定 built-in 列与参数化 custom scalar values。

不能先查 tenant 全量再在 JVM 应用数据范围，不能先分页后过滤关系目标，也不能按 Contact 或字段产生 N+1 查询。

### 8.2 硬上限与共享 admission

| 资源 | 上限 | 超限语义 |
| --- | ---: | --- |
| 所选字段 | 50 | 请求失败 |
| data rows | 5,000 | 以 5,001 probe 整体失败 |
| encoded CSV | 16 MiB | body 开始前整体失败 |
| 每批 ID/cell 查询 | 200 | 继续同一 snapshot 的下一有界批 |
| active Customer/Contact CSV per member per replica | 1 | 不排队，安全拒绝 |
| active Customer/Contact CSV per replica | 4 | 不排队，安全拒绝 |

B31a 实现时将 B30a 当前 admission 收敛为 Customer/Contact 两条已实现同步 CSV route 共用的进程资源门。它只保护当前副本的内存和数据库并发，不是授权或多副本全局配额。

slot 从进入任一导出用例一直持有到响应 body 写完、失败或客户端取消；不能在数据库快照结束时提前释放。Customer 与 Contact 各自保留显式文档类型和响应文件名，共享 admission 不产生通用对象导出 API。

## 9. 分层与所有权

- `api` 拥有 `ContactCsvExportRequest` 和字段级验证；成功 body 不是 JSON `Response` DTO；
- `application` 拥有 `ContactCsvExportService`、双范围收窄、关系目标协调、字段选择、标量规范化、绝对字节上限和已完成的 `ContactCsvDocument`；
- Customer/Contact 可共享 application 内部纯格式的有界安全 CSV encoder 与资源 admission，但不能共享业务 query、field catalog、access resolver 或 repository；
- `domain` 的 Contact 边界拥有 typed `ContactExportQuery`、`ContactExportRow` 与有序 `ContactExportCell` 投影；
- `ContactRepository` 增加 Contact 专属 ID probe 与 scalar projection；持久化适配器只解释固定 Contact built-in 列和参数化 custom values；
- `infra/rest` 只建立认证 caller、调用用例、设置固定响应头并写出已完成文档；
- `start` 只组合共享的有界 per-replica admission，不拥有 Contact 授权、字段、关系或查询规则。

不得建立 `GenericExportRepository`、动态表/列 registry、反射 `BusinessObjectType` 导出器、任意 `Map<String,Object>` row、JDBC metadata 查询或跨域 SQL builder。纯字节编码和资源门复用不等于业务对象泛化。

`ContactCsvDocument` 只在当前请求栈内单次消费，不进入 cache、event、Activity、notification、outbox 或日志，并使用固定 redacted `toString`。响应完成或取消后立即释放应用引用；heap dump、swap 与诊断访问仍由部署环境按敏感 CRM 数据治理。

## 10. 持久化、配置与兼容

B31a 计划不新增 Flyway 迁移：

- `contacts:export` 由代码权威目录提供，现有 tenant role grant 不自动获得；
- 现有 Contact、custom value、saved view、star、Customer 与 collaborator 表继续作为真相；
- 导出不创建 job、artifact、receipt、download token、content blob 或 audit payload；
- 不修改 B24 文件迁移，也不增加 CSV MIME 或 Contact attachment target；
- B30a Customer API、请求字段、CSV bytes 和 5,000/50/16 MiB 绝对上限保持不变。

共享 admission 会把 Customer 与 Contact 的当前副本总并发共同限制为 4、同一成员共同限制为 1；这不会放宽 B30a 已冻结的任一上限。若以后需要配置并发，只允许正值且代码 hard ceiling 不得超过本节值；非法值必须让导出 route admission 失败关闭，不影响普通 Customer/Contact API。

普通可观测性只允许固定 operation、outcome、对象种类、row-count bucket、byte-count bucket 和耗时 bucket；不得记录筛选值、字段 code、Contact/Customer ID、CSV cell、文件内容、保存视图内容、电话号码、邮箱或异常原文。

## 11. B31a 实现任务卡

1. 增加 `contacts:export`、`6135` operation 和 `/api/v1/contacts/export/csv` 独立安全路由；
2. 增加 `ContactCsvExportRequest`、Contact 专属 query/row/cell 投影和双数据范围 SQL；
3. 复用普通 Contact keyword/name/owner/customer/starred/saved-view 安全验证，并在同一快照复核 Customer/上级 Contact 目标；
4. 实现 Contact fixed scalar field catalog 与关系、集合、FILE、mask 的整请求失败关闭；
5. 实现 `contact_id ASC` 的 5,001 ID probe 与 200-ID batched scalar projection；
6. 只抽取 format/resource 层的安全 CSV encoder 与 Customer/Contact 共享 admission，不建立通用业务 export service；
7. 补充 application 单元、REST security/header、MySQL data-scope/pool/relation/snapshot/EXPLAIN 和跨 tenant fixture；
8. 实现后更新 README、API reference、architecture、object layering、test guide、`llms.txt` 与 `docs/acceptance/B31a.md`。

## 12. 验收门槛

B31a 完成必须同时证明：

1. 缺少 `contacts:export` 或 `contacts:read`、任一数据范围不可用、成员失活或跨 tenant 请求均在查询 Contact 业务数据前失败；
2. `SELF/DEPARTMENT/DEPARTMENT_SUBTREE/TENANT` 的 read 与 owner-derived export predicate 同时在 SQL 生效，协作者只能满足 read 一侧；
3. 普通导出排除 `IN_POOL`/`DELETED`，ContactPool 管理、领取或可见性不能进入 B31a；
4. hidden、masked、inactive、unknown、duplicate 或 unsupported field 使整次请求失败；
5. keyword/name/owner/starred/saved-view 与普通 Contact query 的安全语义一致，过滤发生在 5,001 probe 前；
6. `customerId` 和 saved-view Customer 目标要求当前 `customers:read`、同 tenant 可见 Customer 与未脱敏 Contact `customer` 字段；上级 Contact 目标要求当前 Contact 可见性；
7. Customer 或上级 Contact 关系只参与过滤，不出现在 CSV cell、日志、错误或 metric；
8. 0 行返回 BOM + header，1..5,000 行按 `contact_id ASC` 返回；5,001 行和 16 MiB+1 在 body 开始前整体拒绝；
9. CSV 对逗号、双引号、CR/LF、Unicode、null、空字符串、decimal/date/datetime/boolean/option code、电话/邮箱和全部公式前缀产生固定 golden fixture；
10. 字段策略、关系目标或 Contact 在批次读取期间并发提交时，导出仍只观察一个 `REPEATABLE_READ` 快照；
11. MySQL 查询始终带 exact tenant、双范围交集、活动/非公海条件和固定 ID keyset，不出现 tenant 全量 JVM 过滤、逐 row/field N+1 或动态 SQL identifier；
12. Customer 与 Contact 同一成员不能并发各占一个 slot，两个 route 的当前副本合计不能超过 4；slow-client/cancel 直到 body 终止才释放；
13. 所有用例失败保持 pre-body JSON，响应开始后不再访问 repository；
14. 日志、metric、exception 和测试报告不含筛选值、field code、业务 ID、CSV 内容、电话、邮箱或原始异常；
15. `sh ./mvnw test` 与 `CI=true sh ./mvnw test` 全部通过。

## 13. 明确非目标

B31a 不包含：

- CSV/XLSX 导入、批量创建/更新/删除、upsert、错误行重试、补偿、回滚或迁移；
- Excel `.xlsx`、ODS、JSONL、XML、ZIP、压缩、密码保护、数字签名或邮件发送；
- 异步 export job、进度、取消、重试、通知、持久化 artifact、历史下载或公共链接；
- B24 Attachment/FileField、对象存储、scanner、文件 target、预签名 URL 或 storage readiness；
- Customer 之外对 B30a 的行为扩展，以及 Lead、Opportunity、Product、Contract、Invoice、Receivable、ReturnVisit、OA、Work、Activity、Report 或 EnterpriseData 导出；
- ContactPool、跨 tenant、管理员“导出全部”、联系人层级子树或关系闭包；
- tags、addresses、collaborators、owner/customer/parent display、FILE、普通附件、Activity timeline 或 Work relation；
- caller-defined label、separator、encoding、locale、timezone、filename、sort、formula、script、SQL、table、column 或 arbitrary object type；
- Opportunity pipeline/stage 目录（见独立 [B32](batch-B32.md)）、B11 StageWorkflow 改造、Work 图结构、Calendar 或新通知通道；
- 持久化审计台账或“用户已下载/已保存”的交付证明。

## 14. 实施顺序

1. 先增加 `contacts:export` 与双 scope 负向测试，证明新能力只能收窄普通 Contact READ；
2. 再冻结 Customer/上级 Contact 条件验证与 pool 排除，并以真实 MySQL 证明 exact tenant 和同快照；
3. 实现 Contact 专属 ID probe/batched scalar projection，再接入共享 format encoder；
4. 在 REST raw response 前完成全部 bytes，并把 Customer/Contact admission 收敛到同一资源门；
5. 最后补齐 slow-client/cancel、完整 Docker 套件和文档回写；
6. B31a 验收完成前不拆 B31b，也不把两条 CSV route 抽象成通用对象导出平台。
