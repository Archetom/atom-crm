# B30 剩余业务接缝盘点与有界 Customer CSV 导出边界规划

> 基线：`9617abdb feat(B29d): 实现 OA 申请协作附件与审批终态冻结`
>
> 对账日期：2026-07-24
>
> 状态：B30a 已实现并完成验收记录；B30b 尚未预占；后续本地接缝总账与下一实现边界见 [B31](batch-B31.md)

## 1. 对账结论

B29a、B29b、B29c 和 B29d 已全部实现。Product/ReturnVisit、MarketingForm、OA 公告和 OA 申请不再是“剩余文件业务接缝”；B24 production 默认关闭和真实 storage/scanner conformance 仍是部署门槛，不应继续扩成新的业务 target 来规避。

B22、B23 和 B24 的剩余工作分别依赖真实呼叫 adapter、经审查的数据源权利/完整 conformance、批准部署环境中的文件证据。它们都已有明确的失败关闭边界，缺少的是外部状态或部署证明，不适合作为 B30 的普通本地业务实现。

仓库中仍被多处明确后置、且可以完全在现有 tenant、数据范围、字段策略和 MySQL 边界内实现的接缝，是数据导出。B30 不从“任意对象导入导出平台”开始，而选择一个最小、只读、同步且严格有界的纵切：

**B30a 权限感知的 Customer CSV 导出**。

它只导出普通 Customer 列表中当前 caller 可见、当前字段策略允许读取且未脱敏的标量字段；使用独立 `customers:export` 数据范围进一步收窄 `customers:read`；在一个只读 `REPEATABLE_READ` 快照内完整生成至多 5,000 行、50 个所选字段和 16 MiB 的 CSV，确认成功后才开始 HTTP body。

B30a 不建立异步任务、持久化导出文件或跨请求下载能力，因此不复用 B24 `AttachmentFile`，也不需要用一个尚未冻结的“可撤销文件任务”假装已经解决大规模迁移。CSV 导入、Excel、跨对象、全量迁移、批量写入和回滚继续后置。

## 2. B29 后剩余接缝总账

| 接缝 | 当前事实 | B30 决策 | 理由 |
| --- | --- | --- | --- |
| B22 呼叫 runtime、录音与真实线路 | connector 控制面、core durable foundation、私有 durability oracle 和 Asterisk transport canary 已有；`PBX_REAL`/`LINE_REAL` 仍阻断 | 继续由 B22 证据门推进 | 不能把 synthetic/durable lane 写成真实双腿媒体、敏感数据和确认删除证据 |
| B23 Customer 外部事实采用的生产启用 | B23e 身份仲裁、preview/re-exact 写入、provenance 与 retention 已实现 | 继续由 source policy/conformance 推进 | 当前没有允许 `ADOPT_SELECTED_FACTS` 的经审查真实来源，普通代码批次不能授予数据权利 |
| B24 文件 production readiness | 业务内核和 B29 覆盖已实现，真实 Linux/ClamAV 仍只有部分场景证据 | 保持 deployment gate | 扩大 MIME、target 或配置不能替代完整 current-build S/I/X conformance |
| Customer/Contact 等业务数据导出 | 列表、保存视图、数据范围和字段策略已实现；Customer 导出后来由 B30a 闭合 | **B30a 已实现 Customer CSV；Contact 见 [B31](batch-B31.md)** | 只读、同域、严格上限、无外部副作用；每个新对象仍须独立证明权限与关系语义 |
| CSV/Excel 导入与批量迁移 | 尚无行级预览、引用解析、幂等、批次原子性、补偿或回滚契约 | B30a 不纳入；B30b 不预占 | 导入是命令和数据治理问题，不能由导出格式或 B24 上传旁路推导 |
| Work 多责任人、子任务、依赖和检查清单 | 单责任人任务、协作、周期、提醒、通知和附件已实现 | 后置到独立 Work 设计 | 每人状态、完成仲裁、有向图与 series 模板语义尚未冻结；不在数据导出批次顺带改变根 |
| 日历、会议和会议室 | B27 已有 IANA zone/DST recurrence，B26 有 durable pulse | 后置到 Calendar 批次 | 参与者响应、资源占用、冲突、取消和外部日历仍是新领域，不是 scheduler payload |
| 文件预览/OCR/分享与外部协作者 | B24/B29 只有受治理原文件和内部主体授权 | 后置到内容处理或外部身份批次 | 衍生对象删除传播、解析器隔离、分享撤销和外部主体均未建立 |
| 新通知通道与 CRM 自动触发 | B27/B28 只实现默认关闭的 typed EMAIL source | 后置到独立集成批次 | SMS/IM/Webhook/Calendar 的 endpoint、同意、模板和歧义协议不能靠字符串扩枚举 |
| Opportunity/Contract 上下文组合与 Activity timeline | B25a 只冻结 Customer 七来源固定概览 | 继续后置 | 尚无一个比 B30a 更明确的固定卡片、排序、字段与权限故事，不建立通用 graph/timeline |

该总账只记录现有文档已经明确后置、并会影响下一批选择的接缝；它不是产品愿望清单，也不把每个非目标自动升级成 roadmap。

## 3. B30a 用户故事与范围

作为同时拥有 Customer 读取与导出权限的当前成员，我希望把一个与普通客户列表条件一致的有界结果导出为安全 CSV；导出必须只包含两个权限范围交集中当前可见的普通客户和我明确选择的当前可读、未脱敏标量字段，不能因为“导出”而看见公海、隐藏字段、关联对象、文件或更宽数据范围。

B30a 只纳入：

1. 一个 Customer 专属、同步返回 CSV 的导出端点；
2. 独立且支持数据范围的 `customers:export` 能力；
3. 与普通 Customer query 相同的 keyword、name、owner、starred 和当前成员 saved-view 条件；
4. 一个固定、显式的可导出标量类型目录；
5. 单个 `REPEATABLE_READ` 快照中的字段目录、有效字段权限、可见 Customer 和全部输出单元格；
6. 完整文档生成后的固定 HTTP 下载响应；
7. CSV 公式注入、控制字符、引号、分隔符、换行、编码和资源上限的确定性处理。

B30a 不保存导出定义、偏好、任务、文件、下载凭据或结果历史，也不把一次导出描述成已经被用户打开、保存或读取。

## 4. HTTP 与 operation 契约

实现路由冻结为：

```text
POST /api/v1/customers/export/csv
```

建议 operation 固定为：

```text
CUSTOMER_CSV_EXPORT = 6035
```

请求使用独立的 `CustomerCsvExportRequest`，概念字段只有：

```text
fieldCodes  1..50 个有序且不重复的字段 code
keyword     与 CustomerQueryRequest 相同
name        与 CustomerQueryRequest 相同
ownerActorId
starred
savedViewId
```

请求不接受 `page`、`size`、tenant、actor、role、authority、数据范围、对象类型、分隔符、字符集、时区、文件名、排序字段、SQL、表达式或任意格式参数。

成功响应固定为：

```text
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename="customers.csv"
Cache-Control: no-store
X-Content-Type-Options: nosniff
```

响应内容为 UTF-8 BOM、一个 header row 和零到 5,000 个 data row。header 第一列固定为 `customer_id`，其余列严格按请求 `fieldCodes` 顺序使用稳定字段 code；不使用可变字段 label、布局名、保存视图名或 caller 提供的文件名。

任何权限、字段、查询、行数、字节数、编码或基础设施失败都必须在 HTTP body 开始前转换为现有安全 JSON 错误。业务成功后只剩向客户端写入已完整生成文档的本地 I/O；不能边翻页查询数据库边开始 `200` 流，随后再用截断 CSV 表示失败。

## 5. 授权与数据范围

### 5.1 独立导出能力

新增 `customers:export`，tenant-assignable 且支持 `SELF`、`DEPARTMENT`、`DEPARTMENT_SUBTREE` 和 `TENANT` 数据范围。现有普通角色不自动获得它；tenant owner 继续只通过当前代码目录动态解析。该能力按 owner-derived scope 解释，不自行加入 Customer collaborator；协作者可见性仍只属于 `customers:read` 的既有 READ predicate。

一次 B30a 导出必须同时满足：

```text
active human membership
  ∩ customers:export effective data scope
  ∩ customers:read effective data scope
  ∩ ordinary Customer active/non-pool predicate
  ∩ request filters
  ∩ current field READ with mask NONE
```

持久化查询必须同时应用两个已经解析的 predicate，而不是只选择一个 scope 枚举：`customers:export` 提供 owner-derived export predicate，`customers:read` 提供原有 owner/department/collaborator READ predicate。`customers:export=TENANT` 不能扩大 `customers:read=SELF`，`customers:read=TENANT` 也不能让 `customers:export=SELF` 导出协作但非本人负责的记录。缺少任一能力、数据范围不可用或成员已失活时，整个请求失败。

`customers:write`、`customers:assign`、`reports:read`、`files:read`、tenant 管理角色或 Customer 协作者身份都不能替代 `customers:export`。协作者只可能满足 `customers:read` 一侧；还必须由独立 export owner-scope predicate 接受该记录，导出能力不新建协作者规则。

### 5.2 查询条件不成为旁路

keyword 候选继续取服务端固定目录与当前 active/searchable/readable/unmasked 字段的交集；交集为空返回 header-only CSV，而不是放宽候选。name、owner 和 saved-view 条件继续使用现有 Customer query 的字段和目标验证：

- 非空 `name` 要求 `customer_name` 可读且未脱敏；
- `ownerActorId` 要求 `owner_actor` 可读且未脱敏；
- `savedViewId` 只解析当前 tenant、当前成员自己的有效视图，并重新验证活动、可搜索、可读且未脱敏字段；
- `starred` 仍是当前成员自己的 personal fact，不授予记录可见性；
- 普通查询继续排除 `IN_POOL` 和 `DELETED`，B30a 不导出 CustomerPool 记录。

知道一个字段 code、Customer ID、owner ID 或 saved-view ID 不会产生字段、记录或视图可见性。

## 6. 字段目录与单元格语义

### 6.1 首期可导出类型

除固定 `customer_id` 外，所选字段必须是 exact `CUSTOMER` 当前活动定义，调用成员对其具有 `READ + mask NONE`，并且类型属于：

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

以下内容首期显式排除：

- `MULTI_SELECT`、`FILE`、`USER`、`DEPARTMENT`、`RELATION` 和 `ADDRESS`；
- 内置 `tags` 与 `addresses`；
- owner/协作者、父客户、层级、星标、普通附件、FILE、登记身份和 B23 provenance；
- Contact、Opportunity、Contract 或其他域的任何关系展示值。

排除不是静默删列。请求包含 unsupported、inactive、hidden、masked、重复或未知字段时，整次导出失败并且不返回部分 CSV。B30a 的 CSV 不是未来导入模板；没有无损多值/关系语法之前，不把集合用逗号、JSON、名称或 ID 猜测性压进一个 cell。

### 6.2 确定性编码

- `customer_id` 与 INTEGER 使用无分组十进制；
- DECIMAL 使用 `BigDecimal.toPlainString()` 语义，不使用 locale、科学计数法或 `double`；
- BOOLEAN 固定为小写 `true`/`false`；
- DATE 固定 ISO `yyyy-MM-dd`；
- DATETIME 固定为既有服务端 UTC 值的 ISO 表示，不按浏览器或成员时区改写；
- SELECT 输出稳定 option code，不输出可变 label；
- null 输出空 cell；空字符串与 null 在 B30a CSV 中不承诺可逆区分；
- 文本类值保留业务文本，再应用下述 spreadsheet 公式防护和 RFC 4180 quoting。

header 和每个 cell 都经过同一个 CSV encoder。双引号按 `""` 转义；包含逗号、双引号、CR 或 LF 的 cell 必须引用；row separator 固定为 CRLF。

对 TEXT/TEXTAREA/EMAIL/PHONE/URL/SELECT cell，如果值以 spreadsheet 公式前缀或可把其隐藏在前导控制空白中的危险序列开始，encoder 在 CSV 值前增加单引号文本哨兵。该变换只用于安全展示导出，不修改 Customer 数据，也不声明 round-trip。公式防护必须在 CSV quoting 前执行，并用 Excel/LibreOffice 兼容 fixture 覆盖 `=`, `+`, `-`, `@`, TAB, CR/LF 和前导空白变体。

## 7. 快照、查询与资源边界

### 7.1 单一快照

`CustomerCsvExportService` 使用 `QueryServiceTemplate`。字段定义、有效字段访问、saved-view 解析、两个数据范围的交集、最多 5,001 个候选 ID、全部 export row/cell 和最终 CSV 编码都在一个只读 `REPEATABLE_READ` 事务中完成。

CSV 完整成功生成并通过字节上限后，事务结束；REST 才把已完成文档写入响应。HTTP 客户端慢读、断开或取消不会延长数据库事务，也不会触发再次查询。

固定行顺序为 `customer_id ASC`。仓储先以 exact tenant、双范围交集、普通生命周期和全部条件取得最多 5,001 个 ID；达到第 5,001 个时整体拒绝，不截断。之后在同一快照内按最多 200 个 ID 一批取得所选固定列与 custom scalar values。不能先查 tenant 全量、在 JVM 应用数据范围，也不能按行或字段产生 N+1 查询。

### 7.2 硬上限

首期代码绝对边界固定为：

| 资源 | 上限 | 超限语义 |
| --- | ---: | --- |
| 所选字段 | 50 | 请求失败 |
| data rows | 5,000 | 以 5,001 probe 整体失败 |
| encoded CSV | 16 MiB | body 开始前整体失败 |
| 每批 ID/cell 查询 | 200 | 继续同一 snapshot 的下一有界批 |
| active export per member per replica | 1 | 不排队，安全拒绝 |
| active export per replica | 4 | 不排队，安全拒绝 |

进程内 admission 只保护当前副本的内存和数据库并发，不是授权或多副本全局配额。部署需要全局速率限制时由受信网关执行；不能因为 limiter 不共享就放宽行、列或字节绝对上限。

admission slot 从进入导出用例一直持有到响应 body 写完、失败或客户端取消；不能在数据库快照结束时提前释放，否则慢客户端可以让任意多个 16 MiB 文档同时滞留。slot 等待不排队，释放必须在所有成功和异常路径执行。

16 MiB 是 B30a 已完成 CSV 文档的内存上限，不会扩大 B24 单文件、storage、scanner、tenant/global usage 或 MIME allowlist。B30a 不向 B24 写入任何 CSV。

## 8. 分层与所有权

- `api` 拥有 `CustomerCsvExportRequest` 和字段级验证；成功 body 不是 JSON `Response` DTO；
- `application` 拥有 `CustomerCsvExportService`、双范围收窄、字段选择、单元格规范化、安全 CSV encoder、绝对字节上限和已完成的 `CustomerCsvDocument`；
- `domain` 的 Customer 边界拥有 typed `CustomerExportQuery`、`CustomerExportRow` 与有序 `CustomerExportCell` 投影，不返回 PO、`Map<String,Object>` 或 CSV bytes；
- `CustomerRepository` 增加 Customer 专属 export projection 能力；持久化适配器只解释固定 Customer built-in 列和参数化 custom value 查询；
- `infra/rest` 只建立认证 caller、调用用例、设置固定响应头并写出已经生成的文档；它不读取 repository、字段策略或数据库 cursor；
- `start` 只组合有界 per-replica admission，不拥有字段目录、授权、CSV 规则或业务查询。

`CustomerCsvDocument` 只在当前请求栈内单次消费，不实现通用 JSON/Java serialization，不进入 cache/event/log，并使用固定 redacted `toString`；响应完成或取消后立即释放应用引用。该生命周期不声称能够从 JVM heap dump 中选择性擦除已经产生的字节，因此部署的 dump、swap 和诊断访问仍须按敏感 CRM 数据治理。

不建立 `GenericExportRepository`、动态表/列 registry、反射 `BusinessObjectType` 导出器、任意 `Map` row、JDBC metadata 查询或跨域 SQL builder。以后增加 Contact/Lead 等来源时，必须分别证明其生命周期、范围、字段与关系语义，再显式接入。

## 9. 持久化、配置与兼容

B30a 计划不新增 Flyway 迁移：

- `customers:export` 由代码权威目录提供，现有 tenant role grant 不会自动获得；
- 导出没有数据库 job、artifact、receipt、download token、content blob 或 audit payload；
- 现有 Customer、custom value、saved view 和 role grant 表继续作为真相；
- 不修改 B24 V59–V64/V77/V78/V83/V84，也不增加 CSV MIME。

实现应优先使用固定代码上限。若 per-replica concurrency 需要配置，只允许正值且代码 hard ceiling 不得超过本节的 1/member 与 4/replica；非法值使 B30a route admission 失败关闭，不影响普通 Customer API。

成功响应不进入 Redis、附件缓存、浏览器共享缓存、Activity、DomainEvent、notification 或 outbox。普通可观测性只允许固定 operation、outcome、row-count bucket、byte-count bucket 和耗时 bucket；不得记录筛选值、字段 code、Customer ID、CSV cell、文件内容、用户提供的名称或异常原文。

## 10. B30a 实现任务卡

1. 增加 `customers:export`、`6035` operation 和 `/api/v1/customers/export/csv` 的独立安全路由；
2. 增加 `CustomerCsvExportRequest`、Customer 专属 query/row/cell 投影和双数据范围交集；
3. 实现 fixed scalar field catalog、READ/unmasked 失败关闭、saved-view/keyword 复用和 `customer_id ASC` 有界查询；
4. 实现 UTF-8 BOM、CRLF、RFC 4180 quoting、typed scalar encoding 与 spreadsheet formula 防护；
5. 确保完整文档在一个 `REPEATABLE_READ` 查询事务中生成，事务结束后才开始 HTTP body；
6. 增加 per-member/per-replica admission、5,001 row probe、50-field 与 16 MiB hard limit；
7. 补充 application 单元、REST security/header、MySQL data-scope/field-policy/snapshot/EXPLAIN 和跨 tenant fixture；
8. 实现后更新 README、API reference、architecture、object layering、test guide、`llms.txt` 与 `docs/acceptance/B30a.md`。

## 11. 验收门槛

B30a 完成必须同时证明：

1. 缺少 `customers:export` 或 `customers:read`、任一数据范围不可用、成员失活或跨 tenant 请求均在查询业务数据前失败；
2. `SELF/DEPARTMENT/DEPARTMENT_SUBTREE/TENANT` 的 read 与 owner-derived export predicate 同时在 SQL 生效，协作者只能满足 read 一侧，不能扩大 export scope；
3. 普通导出排除 `IN_POOL`/`DELETED`，公海管理、领取或可见性不能进入 B30a；
4. hidden、masked、inactive、unknown、duplicate 或 unsupported field 使整次请求失败，没有静默删列、空列或部分 CSV；
5. keyword/name/owner/starred/saved-view 与普通 Customer query 的安全语义一致，过滤发生在 5,001 probe 与输出之前；
6. 0 行返回 BOM + header，1..5,000 行按 `customer_id ASC` 返回；5,001 行和 16 MiB+1 都在 body 开始前拒绝；
7. CSV 对逗号、双引号、CR/LF、Unicode、null、空字符串、decimal/date/datetime/boolean/option code 和全部公式前缀产生固定字节 golden fixture；
8. 字段策略或 Customer 在分页期间并发提交时，导出仍只观察一个 `REPEATABLE_READ` 快照；
9. MySQL 查询始终带 exact tenant、双范围交集、活动/非公海条件和固定 ID keyset，不出现全 tenant JVM 过滤、逐 row/field N+1 或动态 SQL identifier；
10. 慢客户端和取消发生在数据库事务结束后，响应开始后不会再访问 repository，并且 admission slot 直到 body 完成/失败/取消才释放；
11. 日志、metric、exception 和测试报告不含过滤值、field code、Customer ID、CSV 内容或原始异常；
12. `sh ./mvnw test` 与 `CI=true sh ./mvnw test` 全部通过。

## 12. 明确非目标

B30a 不包含：

- CSV/XLSX 导入、批量创建/更新/删除、upsert、错误行重试、补偿、回滚或迁移；
- Excel `.xlsx`、ODS、JSONL、XML、ZIP、压缩、密码保护、数字签名或邮件发送；
- 异步 export job、进度、取消、重试、通知、持久化 artifact、历史下载或公共链接；
- B24 Attachment/FileField、对象存储、scanner、文件 target、预签名 URL 或 storage readiness；
- Contact、Lead、Opportunity、Product、Contract、Invoice、Receivable、ReturnVisit、OA、Work、Activity、Report 或 EnterpriseData 导出；
- 公海 Customer、跨 tenant、管理员“导出全部”、客户层级子树、七来源业务概览、Activity timeline 或关系闭包；
- tags、addresses、collaborators、owner/department/relation display、FILE、普通附件、登记身份或 provenance；
- caller-defined label、separator、encoding、locale、timezone、filename、sort、formula、script、SQL、table、column 或 arbitrary object type；
- 持久化审计台账或“用户已下载/已保存”的交付证明；如监管要求这种事实，必须独立设计可靠 receipt，而不能从 HTTP 200 推断。

## 13. 实施顺序

1. 先建立 `customers:export` 与双 scope 负向测试，证明新能力只能收窄普通 Customer READ；
2. 再实现 Customer 专属 ID probe/batched scalar projection，并用真实 MySQL 固定同快照、跨 tenant 和索引 oracle；
3. 完成 typed cell 与 CSV golden fixture后，再连接 REST raw response，确保所有业务失败发生在 body 之前；
4. 最后加入并发 admission、slow-client/cancel、完整 Docker 套件和文档回写；
5. B30a 已完成验收且没有拆分 B30b；后续对象导出与剩余本地接缝由 [B31](batch-B31.md) 重新对账，当前 CSV 仍不是导入模板。
