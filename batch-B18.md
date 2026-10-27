# B18 仪表盘批次规划

> 基线：`491a3db fix(B19a): 收紧营销表单安全与并发边界`
>
> 对账日期：2026-07-18
>
> 状态：B18a 已完成；B18b 已完成并复审

## 1. 批次目标

B18 把 B17 已治理的商业指标组织为当前成员可直接使用的仪表盘，同时保证仪表盘偏好只能收窄、不能扩大任何来源对象的权限。

本批次坚持以下边界：

- `reports:read` 只允许进入仪表盘，不建立新的跨对象数据范围。
- 每个来源仍独立解析当前 `*:read`、数据范围和 B05 字段权限；不能借另一个来源的权限补齐。
- B18a 保存的是展示偏好，不是授权事实。B18b 必须把偏好范围与每个来源的有效范围取交集。
- 固定指标继续由来源仓储在 MySQL 内聚合，不把记录全量加载到 JVM，也不调用 B17 HTTP 端点拼装结果。
- 字段策略与聚合数据在同一个只读 `REPEATABLE_READ` 快照中读取。
- 首期只做服务端固定仪表盘，不接收 SQL、字段 code、公式、脚本、任意筛选或客户端组件定义。

## 2. 范围拆分

| 子批次 | 状态 | 交付物 | 数据库迁移 |
| --- | --- | --- | --- |
| B18a | 已完成 | 当前成员的默认时间范围和展示数据范围 | `V46__create_dashboard_preference.sql` |
| B18b | 已完成 | 权限感知的固定销售仪表盘概览 | 无；复用 B17b 的 `V47` 日期索引 |

## 3. B18a 基线

B18b 不得破坏以下已交付契约：

- `GET/PUT /api/v1/dashboard/preferences` 按 `(tenant, member)` 读取或保存偏好；
- 未保存时使用 `CURRENT_MONTH` 和 `SELF`，读取不创建数据库记录；
- 相对时间支持 `TODAY`、`CURRENT_WEEK`、`CURRENT_MONTH`、`CURRENT_QUARTER`、`CURRENT_YEAR`；
- `CUSTOM` 必须提供包含首尾且最多 366 天的日期范围；
- 展示范围只接受 `SELF`、`DEPARTMENT`、`DEPARTMENT_SUBTREE`、`TENANT`；
- 偏好更新使用乐观锁，且请求不能携带 tenant、actor、role 或 authority。

## 4. B18b 任务卡：固定销售仪表盘概览

### 4.1 用户故事

作为具有报表读取能力的当前成员，我希望用自己保存的时间和数据范围偏好打开一个固定销售仪表盘；其中每张可用卡片只统计我当前真正可见且字段权限允许的数据，权限不足的来源不会阻止其他卡片展示，也不会返回可推断隐藏数据的原因或部分数值。

### 4.2 推荐交付

新增一个只读端点：

| 方法与路径 | 路由权限 | 说明 |
| --- | --- | --- |
| `GET /api/v1/dashboard/overview` | `reports:read` | 使用 B18a 当前偏好返回固定的三来源 KPI 与趋势 |

请求不接收 body、tenant、actor、来源范围、字段、指标或排序。临时筛选和组件配置不进入 B18b；客户端要改变默认展示范围时使用现有偏好 `PUT`。

建议 operation code 使用 `6802`（`DASHBOARD_OVERVIEW_GET`）。

### 4.3 固定卡片

响应固定按下列顺序返回三张卡片：

| `metric` | 来源 | 记录条件 | 日期字段 | 数量 | 金额 |
| --- | --- | --- | --- | --- | --- |
| `WON_OPPORTUNITY` | OPPORTUNITY | `status=ACTIVE AND outcome=WON` | `outcome_time` 的 UTC 日期 | 商机数 | `estimated_amount` 合计 |
| `ACTIVE_CONTRACT` | CONTRACT | `status=ACTIVE AND order_date IS NOT NULL` | `order_date` | 合同数 | `total_amount` 合计 |
| `ACTIVE_RECEIVABLE` | RECEIVABLE | `status=ACTIVE` | `received_date` | 回款数 | `amount` 合计 |

这些定义与 B17b 保持一致。卡片总计由同一次趋势聚合结果求和，不增加一条语义可能漂移的独立汇总查询。

### 4.4 时间解析与趋势粒度

- 通过注入的 UTC `Clock` 在一次请求开始时解析唯一 `asOfDate`，禁止在多个来源中分别调用系统时间。
- `CURRENT_WEEK` 使用 ISO 周一；月份、季度和年份使用 UTC 自然日历边界。
- 相对时间返回实际解析后的包含首尾 `from`、`to`；`CUSTOM` 原样使用已验证日期。
- 自动粒度固定为：不超过 31 天使用 `DAY`，32..120 天使用 `WEEK`，121..366 天使用 `MONTH`。
- 空周期由应用层补零，`points[]` 按 `periodStart` 升序；金额保持两位小数语义。
- 所有 DATETIME 范围在 SQL 中使用 UTC 半开区间 `[from 00:00, to + 1 day 00:00)`；DATE 使用包含首尾语义。

概念响应：

```json
{
  "asOfDate": "2026-07-18",
  "timeRange": "CURRENT_MONTH",
  "from": "2026-07-01",
  "to": "2026-07-31",
  "preferredDataScope": "SELF",
  "granularity": "DAY",
  "cards": [
    {
      "metric": "WON_OPPORTUNITY",
      "status": "AVAILABLE",
      "count": 3,
      "amount": 1200.00,
      "points": []
    },
    {
      "metric": "ACTIVE_CONTRACT",
      "status": "UNAVAILABLE",
      "count": null,
      "amount": null,
      "points": []
    },
    {
      "metric": "ACTIVE_RECEIVABLE",
      "status": "AVAILABLE",
      "count": 8,
      "amount": 3200.00,
      "points": []
    }
  ]
}
```

`UNAVAILABLE` 卡片不返回权限缺失、字段隐藏或脱敏等细分原因，也不返回局部数量、金额或时间点。

### 4.5 展示范围与真实授权取交集

B18b 为每个来源分别计算：

```text
dashboard visibility = source effective visibility ∩ saved presentation scope
```

四种范围按 `SELF < DEPARTMENT < DEPARTMENT_SUBTREE < TENANT` 收窄，但不能只比较枚举后直接复用来源 resolver 已生成的部门集合：当来源授权为 `TENANT`、偏好为部门范围时，仍需从当前活动成员关系重新解析当前部门和活动子部门。

建议在应用安全层增加一个共享的“最大展示范围”收窄能力，由现有 Opportunity、Contract、Receivable access resolver 构造最终来源 `Visibility`。不要把 `DashboardDataScope` 写入角色授权，也不要在仓储内读取偏好或成员上下文。

边界行为：

- 偏好比来源授权更宽时，以来源授权为准；
- 偏好比来源授权更窄时，以偏好为准；
- 成员没有部门但偏好为 `DEPARTMENT` 或 `DEPARTMENT_SUBTREE` 时，安全降级为 `SELF`；
- `SELF` 继续沿用各来源现有 READ 可见性语义，包括该来源已明确允许的协作者读取，不重新发明“仅负责人”谓词；
- 每张卡片独立取交集，不共享另一个来源解析出的部门或数据范围。

### 4.6 来源与字段权限

| 指标 | 来源能力 | 必须可读且未脱敏的字段 |
| --- | --- | --- |
| `WON_OPPORTUNITY` | `opportunities:read` | `outcome`, `outcome_time`, `estimated_amount`, `status` |
| `ACTIVE_CONTRACT` | `contracts:read` | `status`, `order_date`, `total_amount` |
| `ACTIVE_RECEIVABLE` | `receivables:read` | `status`, `received_date`, `amount` |

整个端点首先要求活动成员关系和 `reports:read`。随后每张卡片独立处理：

- 缺少来源能力、有效数据范围或任一必需字段时，该卡片返回 `UNAVAILABLE`；
- 只捕获已知的访问拒绝或字段不可用结果来形成 `UNAVAILABLE`；数据库、映射和未知运行时失败必须使整个请求失败，不能被误报为权限不足；
- 一个可用来源的字段策略读取与聚合在同一查询快照中；
- `UNAVAILABLE` 不改变 B17a/B17b 的失败关闭契约，B17 端点仍按自身规则拒绝请求。

### 4.7 复用与架构切片

1. 在 domain 增加可单测的展示范围收窄规则和相对时间解析值对象；domain 不依赖 API、Spring 或持久化类型。
2. 在 application 增加 `DashboardOverviewService`，通过 `QueryServiceTemplate` 编排偏好、UTC 时钟、来源 access resolver、字段 access resolver 和固定指标查询。
3. 从 B17b 提取应用内部的固定指标描述和查询协作逻辑，供 B17b/B18b 共用；不得调用 `ReportController`、Facade 或公共 Request/Response。
4. Opportunity、Contract、Receivable 仍由各自仓储执行固定 SQL；不创建能绕过来源边界的跨对象 `DashboardRepository`。
5. API/Facade/REST 增加独立 `DashboardOverviewResponse`，并在 `SecurityConfig` 通用匹配器之前保护精确 GET 路径。
6. 对 B17b `V47` 索引运行 MySQL `EXPLAIN`；只有执行计划证明必要时才新增下一版本迁移。
7. 更新 README、README.en、API reference、architecture、test guide 和 `llms.txt`。

### 4.8 验收标准

- 没有偏好记录时按 `CURRENT_MONTH`、`SELF` 查询，且读取不创建偏好记录；
- 六种时间范围的 UTC 首尾、ISO 周一、季度边界、闰年和自动粒度正确；
- 三个指标的生命周期、日期字段、数量、金额、零桶和金额精度与 B17b 定义一致；
- 每个来源的 `SELF`、`DEPARTMENT`、`DEPARTMENT_SUBTREE`、`TENANT` 分别与四种偏好取交集正确；
- 来源为 `TENANT` 且偏好为部门范围时会真正加载当前部门谓词，不会退回 tenant 全量；
- 无部门成员的部门偏好安全降级为 `SELF`；
- 缺少一个来源能力或字段权限只使对应卡片 `UNAVAILABLE`，其他卡片仍按其独立范围返回；
- 隐藏或脱敏的金额、日期、状态/结果字段不会产生任何局部值或趋势；
- 同 tenant、跨 tenant、软删/作废/无效记录按固定定义隔离；
- 字段策略与数据聚合位于同一个只读 `REPEATABLE_READ` 快照；
- 未知基础设施失败不会被吞掉或伪装成 `UNAVAILABLE`；
- 保留 B17a/B17b 和 B18a 全部回归测试，MySQL Testcontainers 覆盖真实范围 SQL 与执行计划；
- `sh ./mvnw test` 与 `CI=true sh ./mvnw test` 通过。

## 5. 范围取舍

| 议题 | 推荐选择 | 本批次不选的方案 | 原因 |
| --- | --- | --- | --- |
| 仪表盘形态 | 固定三来源 KPI + 趋势 | 自定义组件和布局 | 先验证权限收窄与查询成本，不引入另一套定义生命周期 |
| 查询入口 | GET 使用已保存偏好 | POST 临时覆盖、任意筛选 | 避免 B18a 偏好与临时过滤形成两套优先级契约 |
| 缺少来源权限 | 单卡 `UNAVAILABLE` | 整页失败或静默删卡 | 保持仪表盘可用，响应结构稳定，同时不返回安全细节 |
| 趋势粒度 | 服务端自动选择 | 客户端任意选择 | 保证最多 120 桶并减少首期参数面 |
| 排名 | 继续使用 B17b 报表端点 | 在概览中追加三份 Top-N | 避免一次页面请求扩成六次聚合和负责人字段权限依赖 |
| 数据计算 | 来源仓储实时聚合 | Redis 结果缓存、物化表 | 当前索引和有界日期足够，先保留强一致与简单失效语义 |
| 时区 | 项目统一 UTC | tenant/用户时区 | 当前没有受治理的 tenant 时区配置，不能从请求任意信任 |

## 6. 明确非目标

- 自定义仪表盘、拖拽布局、组件 CRUD、共享模板或管理员下发；
- 临时筛选、任意字段、保存视图、公式、同比/环比、预测、销售目标或跨对象 join；
- 负责人排名、漏斗明细、记录下钻响应或导出；
- Redis 缓存、物化视图、异步刷新、定时快照或外部 BI；
- tenant/成员时区配置；
- 修改 B17a/B17b 公共路径、Request、Response 或错误语义。

## 7. 实施顺序建议

1. 先实现并单测 UTC 时间解析和数据范围收窄规则。
2. 提取 B17b 固定指标内部协作，保持 B17 公共契约不变。
3. 完成三来源查询与部分可用响应。
4. 补 REST 安全、Facade 映射和 MySQL 集成测试。
5. 最后更新对外文档并运行完整 Docker 测试。
