# B19 营销表单批次规划

> 基线：`491a3db fix(B19a): 收紧营销表单安全与并发边界`
>
> 对账日期：2026-07-18
>
> 状态：B19a、B19b 已完成并复审；公开 MarketingForm FILE 后续边界见 [B29b](batch-B29.md#6-b29bmarketingform-匿名-file)

## 1. 批次目标

B19 在不信任匿名输入、不扩大线索可见范围的前提下，把租户管理的公开表单安全地转化为线索，并为成功提交建立最小、可追溯的来源台账。

B19a 已完成配置、公开渲染和匿名提交。B19b 推荐只补齐“哪一个成功提交创建了哪一条线索”以及表单自身的成功量趋势，不在同一批次引入完整营销自动化。

## 2. 范围拆分

| 子批次 | 状态 | 交付物 | 数据库迁移 |
| --- | --- | --- | --- |
| B19a | 已完成 | 表单配置、生命周期、公开渲染、匿名提交、限流与 B09 原子创建 | `V48__create_marketing_form.sql` |
| B19b | 已完成 | 成功提交归因台账、可见线索链接分页、成功量趋势 | `V50__create_marketing_form_submission.sql` |

## 3. B19a 基线

B19b 不得破坏以下已复审边界：

- tenant 与负责人只从规范 public ID 定位的持久化活动表单派生；
- 每次渲染和提交重新验证负责人活动成员关系、B09 `leads:write` 有效范围及 B05 字段写权限；
- 公开请求使用严格顶层和字段白名单；
- 提交先锁表单根，再在同一外层事务和连接中运行 B09 线索创建；
- 表单变更、停用和提交通过相同根锁形成数据库顺序；
- B09 预期失败和安全包装的内部失败统一属于 `MARKETING_FORM_SUBMIT`（6907）；
- 公开响应只含 `accepted` 与成功提示，不含 tenant、负责人、线索 ID 或版本；
- 规范 public ID 的直接、别名和等价编码路径共享限流桶。

## 4. B19b 任务卡：成功提交归因台账

### 4.1 用户故事

作为具有营销表单读取权限的当前成员，我希望查看某个表单在一段时间内成功创建线索的数量趋势；当我同时具有线索读取能力时，还希望分页打开自己当前可见的来源线索。系统不能保存匿名请求原文、来源 IP 或其他不必要的个人数据，也不能通过台账绕过当前线索数据范围。

### 4.2 推荐交付

新增两个管理端端点：

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `POST /api/v1/marketing-forms/{formId}/submissions/trends` | `marketing-forms:read` | 按成功提交时间返回该表单的成功量趋势 |
| `POST /api/v1/marketing-forms/{formId}/submissions/query` | `marketing-forms:read` + `leads:read` | 分页返回当前 caller 仍可见的成功提交与线索链接 |

建议 operation code 使用 `6908`（趋势）和 `6909`（可见链接分页）。路由权限只是第一层；应用层必须重复验证活动成员、tenant、表单以及线索读取范围。

### 4.3 成功量趋势契约

请求概念字段：

- `from`、`to`：包含首尾的 ISO `LocalDate`；
- `granularity`：`DAY`、`WEEK`、`MONTH`。

响应概念字段：

- `formId`、`from`、`to`、`granularity`；
- `points[]` 为 `{ periodStart, acceptedCount }`，按周期升序并补齐零桶；
- 周以 ISO 周一开始，DATETIME 按 UTC 半开区间查询。

趋势最多 120 个桶且日期跨度最多 10 年，超限失败，不截断。它只统计已与线索在同一事务提交成功的台账行，不统计限流拒绝、校验失败、回滚或未知失败。

`acceptedCount` 是营销表单自身的成功接收事实，只要求 `marketing-forms:read`。它不返回线索 ID、字段、负责人、状态或转化结果，也不因线索后来转换、软删除或变得不可见而改写历史成功数。

### 4.4 可见线索链接分页契约

请求概念字段：

- `page` 从 1 开始，`size` 为 1..100；
- 可选 `from`、`to` 必须同时提供，包含首尾且最多 366 天；都不提供时查询该表单全部历史。

响应项固定为：

```json
{
  "submissionId": 1001,
  "leadId": 2001,
  "formVersion": 3,
  "submittedTime": "2026-07-18T08:30:00"
}
```

固定按 `submitted_time DESC, id DESC` 排序。首期不返回线索名称、电话、邮箱、负责人、状态、客户关系或自定义字段，因此不需要为本端点复制 Lead Response 和遮罩逻辑。

分页仓储查询必须把当前 caller 的 B09 `leads:read` `LeadVisibility` 直接放进 SQL，再计算 `total` 和截取页面。禁止先按台账分页、加载线索后过滤，否则隐藏记录会占页或通过总数泄露。

当前不可见、跨 tenant 或已软删除的线索不出现在链接分页中。趋势的 `acceptedCount` 与链接分页 `total` 分别表示“历史成功接收”和“当前可见链接”，产品和文档不得暗示二者必然相等。

### 4.5 持久化模型

新增 append-only `crm_marketing_form_submission`：

| 字段 | 语义 |
| --- | --- |
| `id` | 服务端生成的提交台账 ID |
| `tenant_id` | 从已解析 public ID 和持久化表单派生 |
| `marketing_form_id` | 被锁定的表单根 ID |
| `form_version` | 创建线索时实际使用的表单版本 |
| `lead_id` | 同一事务创建的 B09 线索 ID |
| `submitted_time` | 数据库 UTC 成功提交时间 |

约束与索引：

- 主键 `id`，并提供 `(tenant_id, id)` tenant 复合唯一键；
- `(tenant_id, lead_id)` 唯一，保证一条线索最多归属于一个公开表单成功提交；
- `(tenant_id, marketing_form_id, submitted_time, id)` 支持表单趋势和稳定分页；
- 表单和线索都使用 tenant 复合外键，禁止跨 tenant 关系；
- `form_version >= 0`；
- 不设置物理级联删除。表单和线索现有软删除保留历史行。

每次有效公开提交继续持有 B19a 表单根锁。B09 创建返回持久化后的 `LeadVO` 后，在同一 `CommandServiceTemplate` 事务中插入台账；台账插入失败必须回滚线索创建，线索创建失败也不能留下台账。公开响应契约保持不变。

台账是一条成功事实，不是 `MarketingForm` 聚合内需要整体加载和替换的子集合。建议建立独立的 tenant-scoped `MarketingFormSubmissionRepository` 端口与不可变领域投影，避免读取表单时无界水合提交历史。

### 4.6 表单生命周期行为

- ACTIVE 表单可继续产生新台账；
- INACTIVE 或 DELETED 表单不能产生新台账，但管理端可查询既有趋势和当前可见链接；
- 管理查询使用 tenant-scoped 内部 form ID，不使用 public ID；
- 查询历史不重新验证表单负责人的 `leads:write`，因为该权限决定能否新建线索，不决定其他管理员能否读取既有表单事实；
- 链接分页始终按查询 caller 自己当前的 `leads:read` 和数据范围过滤，不能复用表单负责人提交时的权限快照；
- 线索转化、负责人变化、范围变化或软删除不会修改台账；只影响当前链接是否可见。

### 4.7 数据最小化

B19b 台账明确不保存：

- 提交 values 原文、字段快照、电话、邮箱或其他线索字段；
- 来源 IP、`X-Forwarded-For`、User-Agent、Referer、Cookie 或设备指纹；
- 限流桶状态、失败请求、异常消息或堆栈；
- public ID 原文或匿名请求头；
- UTM、广告 click ID、页面 URL 或自由来源参数。

成功量趋势只需要表单、版本、线索关系和数据库 UTC 时间。后续若引入受治理归因字段，应单独定义白名单、长度、规范化、保留期和输出权限，不能回填或复用原始请求日志。

### 4.8 架构切片

1. 在 domain 定义不可变成功提交事实、ID、趋势点、日期范围和 tenant-scoped repository 端口。
2. 新增 V50 迁移、PO、converter、mapper 和 repository adapter，保持 form/lead tenant 复合关系一致。
3. 扩展 B19a 提交编排：获取 B09 已持久化 `LeadVO`，同事务追加台账，不改变公开 Response。
4. 应用查询服务分别编排表单可见性、趋势和当前 caller 的 `LeadAccessResolver`；趋势不加载线索聚合。
5. API/Facade/REST 增加独立 Request/VO/Response，不复用 Lead API Response，也不把 persistence PO 暴露出去。
6. `SecurityConfig` 在通用 marketing-form 匹配器之前精确保护两个 POST 路径，并覆盖静态 `trends`/`query` 路由。
7. 更新 README、README.en、API reference、architecture、test guide、configuration（若无新配置则只说明无配置）和 `llms.txt`。

### 4.9 验收标准

- 每次成功公开提交恰好创建一条线索和一条同 tenant 台账，任一写入失败都整体回滚；
- 台账保存实际表单 ID、版本、线索 ID 和由 MySQL `UTC_TIMESTAMP(6)` 生成并回读的时间，不接受匿名请求提供这些字段；
- B19a 公开成功和错误响应不新增内部 ID、tenant、负责人或版本；
- 限流、验证失败、B09 唯一冲突、并发失败和事务回滚不产生台账；
- ACTIVE/INACTIVE/DELETED 表单的历史查询与新提交行为符合生命周期规则；
- 趋势的 UTC 首尾、DAY/WEEK/MONTH、零桶、120 桶和 10 年边界正确；
- 趋势只需要 `marketing-forms:read`，不会查询或返回线索字段；
- 链接分页缺少 `leads:read`、活动成员关系或有效数据范围时失败关闭；
- 链接分页对 `SELF`、`DEPARTMENT`、`DEPARTMENT_SUBTREE`、`TENANT` 使用当前 caller 范围，隐藏记录不占页且不计入 total；
- 跨 tenant form/lead ID 与不可访问 ID 返回一致的安全结果；
- 线索后续转化不改变台账，软删除后不再出现在当前可见链接中，但成功量趋势不减少；
- 数据库唯一键和复合外键覆盖重复关系与跨 tenant 关系；
- 迁移测试覆盖从 V48 升级到 V50，MySQL Testcontainers 覆盖真实 join、分页和趋势 SQL；
- 保留 B19a 复审、限流、并发停用和单连接事务回归；
- `sh ./mvnw test` 与 `CI=true sh ./mvnw test` 通过。

## 5. 范围取舍

| 议题 | 推荐选择 | 本批次不选的方案 | 原因 |
| --- | --- | --- | --- |
| 来源建模 | 独立 append-only 关系表 | 在 Lead 根增加 `marketingFormId` | 避免把渠道来源生命周期塞入线索聚合，并保留表单版本事实 |
| 记录范围 | 只记录成功提交 | 记录失败、限流和完整请求日志 | 失败量应进入有保留期的可观测性系统，不应形成含敏感输入的业务表 |
| 表单统计 | `marketing-forms:read` 可看成功量 | 强制 `leads:read` | 成功接收是表单自身事实，且趋势不暴露线索字段或状态 |
| 线索链接 | 额外要求当前 `leads:read` 和数据范围 | 按表单负责人历史权限展示 | 查询必须遵循当前 caller，不能把提交时写权限变成长期读授权 |
| 历史删除 | 成功量保留，链接按当前线索可见性 | 删除线索后同步删台账 | 表单成功事实应稳定，业务记录可见性仍实时失败关闭 |
| 转化分析 | 后续独立子批次 | B19b 同时做转化率和漏斗 | 需要定义状态字段权限、当前值与历史快照、删除后的分母语义 |
| 幂等重试 | 后续独立子批次 | B19b 增加 `Idempotency-Key` | 需要单独确定 key 生命周期、请求指纹、停用后重放和冲突响应 |
| 来源归因 | 后续受治理字段 | 直接保存 UTM/Referer/IP | 隐私、白名单、规范化、保留期和输出权限尚未定义 |
| 下游动作 | 不发布 webhook | 同步 CRM/广告平台回调 | 需要 outbox、签名、重试、幂等和目标 SSRF 边界 |

## 6. 明确非目标

- 匿名提交幂等键、重复检测、验证码、蜜罐字段或第三方反滥用服务；
- 失败提交审计、来源 IP/设备分析、原始 payload 留存；
- UTM、广告点击、活动 Campaign、渠道成本、归因窗口或多触点归因；
- 转化率、转化时间、客户/联系人/商机漏斗；
- 字段值快照、表单版本渲染回放或提交内容详情；
- FILE 字段与匿名上传；B19 保持失败关闭，后续仅按 [B29b](batch-B29.md#6-b29bmarketingform-匿名-file) 的一次性 public draft credential 接入；
- Webhook、消息通知、外部营销平台、邮件或自动化工作流；
- CSV/Excel 导出、批量删除、物理清理、归档和保留期调度；
- 分布式全局限流；该边界仍由网关或共享限流服务负责。

## 7. 实施顺序建议

1. 先完成 V50、提交事实和原子写入，保留 B19a 全部事务回归。
2. 实现只依赖表单权限的成功量趋势。
3. 实现使用现有 LeadVisibility SQL 的可见链接分页。
4. 补 REST 路由安全、迁移升级和跨 tenant 集成测试。
5. 最后更新对外文档并运行完整 Docker 测试。

在 B18b 与 B19b 之间，建议先实现 B19b：它直接延续刚完成的 B19a 事务边界，改动集中且能尽早补齐来源可追溯性；B18b 随后进行，单独处理跨三个来源的权限收窄和部分可用查询语义。
