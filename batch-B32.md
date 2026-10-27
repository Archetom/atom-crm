# B32 Opportunity 销售管线与阶段目录盘点及治理边界规划

> 基线：`d5d9b219 docs(B31): 盘点 B30a 后剩余本地业务接缝并冻结下一实现边界`
>
> 对账日期：2026-07-24
>
> 状态：B32a 已实现并由 [验收记录](docs/acceptance/B32a.md) 覆盖；B31a Contact CSV 已独立实现；B32b 尚未预占

## 1. 对账结论

B10 已把 `pipelineCode` 和 `stageCode` 作为商机聚合的必填值保存，并在创建、完整更新和独立阶段更新时要求调用者同时拥有商机写范围和两个内置字段的 `WRITE`。但它只验证两个字符串非空、最长 64 字符且符合 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`；当前没有 tenant 销售管线、阶段目录、启停状态、展示名、顺序、概率、目录权限、目录守卫或数据库引用约束。

V34 的 `crm_opportunity` 直接保存两个 `utf8mb4_unicode_ci` 字符串并建立普通查询索引。应用域把 code 当成区分大小写的值，MySQL 比较却可能折叠大小写和 Unicode collation 等价项；历史 tenant 还可以拥有任意数量的不同 code 组合。这个差异不能通过在 Controller 增加一个列表端点闭合。

B05 把 `pipeline_code` 和 `stage_code` 定义为必填、可搜索的 `TEXT` 内置字段。它拥有字段可见、脱敏、写入和保存视图条件治理，但不应成为 tenant 销售目录或把动态目录复制为 `FieldOption`。B11 的 `StageWorkflowDefinition` 也拥有 workflow-local stage code、顺序、概率、必填字段、任务和审批门；其运行记录不会改写 `Opportunity.pipelineCode/stageCode`，因此 B11 不是销售目录的所有者。

B32 将下一条 Opportunity 治理纵切冻结为：

**B32a tenant-scoped Opportunity 销售管线与阶段目录**。

B32a 增加 Opportunity 自有的版本化 `OpportunityPipeline` 与 `OpportunityPipelineStage` 目录、独立的 tenant 级读/管理能力、数据库目录守卫、历史 backfill、exact binary code identity，以及商机创建和阶段迁移时的活动目录校验。既有商机继续保存并返回稳定 code；目录名称、顺序和概率是当前配置，不复制进商机，不改写历史审批、工作流、保存视图或报表定义。

B32a 不把 B11 workflow stage 与销售 stage 合并，也不让任一方向隐式推进另一方向。若未来需要“销售阶段迁移必须完成 B11 任务/审批”，必须另行冻结一条协调事务、幂等、锁序、失败恢复和历史升级边界。

## 2. 当前边界盘点

| 面 | 当前事实 | B32a 决策 |
| --- | --- | --- |
| 商机聚合 | `Opportunity` 必填保存 `pipelineCode/stageCode`；创建、完整更新和 `/stage` 都接受 caller 提供的 code | 保留公开 code 对；新建或实际变更时解析为同 tenant 的活动目录 pair |
| 字符串规则 | Java 裁剪后只允许 1..64 个 ASCII 字母、数字、点、下划线和连字符；大小写不折叠 | 既有 exact bytes 不改写；目录和根列统一为 `ascii_bin`，大小写变体是不同身份 |
| B05 字段 | 两个内置字段均为 required/searchable `TEXT`，字段策略控制读写与脱敏 | 保持 `TEXT` 和既有字段 code；目录不写入动态 `FieldOption`，也不绕过字段策略 |
| 写路径 | create、full update、stage update 只做字段授权和聚合字符串校验 | `CustomizationObjectLock(OPPORTUNITY)` 后取得销售目录守卫，再验证 exact 活动 pair |
| 读路径 | 普通详情/列表按字段策略返回 code；保存视图和报表直接使用根列 | 不自动拼接目录名称；历史 code 在目录停用/删除后仍可读、筛选、分组和聚合 |
| 持久化 | V34 无目录表、外键或数据库 code CHECK；索引 collation 与 Java 身份语义不一致 | 新增 V85，不修改 V34；backfill 后增加同 tenant exact pair FK 和 binary collation |
| tenant bootstrap | 只 provision OPPORTUNITY 内置字段，没有销售目录 | 新 tenant 幂等 provision `direct` 管线及 `qualification`、`negotiation` 两个活动阶段 |
| B11 approval facts | `BusinessRecordFactResolver` 把当前两个 code 作为权威字符串事实 | 继续如此；目录名称、概率、状态不进入审批事实，历史 snapshot 不重写 |
| B11 stage workflow | workflow-local stage 存在自己的 code/order/probability 和运行记录 | 保持独立命名空间、表、权限、状态机和锁；不增加目录 FK 或隐式同步 |
| 测试夹具 | 多个 MySQL 测试在 bootstrap 后用 `direct/qualification`，另有测试直接插入任意 pair | bootstrap 默认保持主路径兼容；所有直接 SQL fixture 必须显式 provision exact 目录 pair |

该盘点只冻结 B32a 需要闭合的 Opportunity 目录接缝。它不把阶段漏斗、预测、自动化、审批协调、导入导出或任意 CRM 配置统一升级为本批范围。

## 3. 用户故事与纳入范围

作为拥有销售管线目录读取权限的当前 tenant 成员，我可以分页查看当前 tenant 的管线和阶段，并按稳定 code、展示名、状态和顺序选择一个可用 pair。

作为拥有销售管线目录管理权限的当前 tenant 成员，我可以创建、重命名、排序、启停和软删除管线或阶段，并维护阶段的展示概率；这些操作不能修改既有商机保存的 code，也不能越过 tenant。

作为具有商机写范围和相应 B05 字段写权限的当前成员，我只能把新商机或发生实际阶段变化的商机放入当前活动管线中的当前活动阶段。目录权限不替代商机权限，商机权限也不自动授予 tenant 目录管理权。

B32a 只纳入：

1. tenant-scoped、分页读取的销售管线与阶段目录；
2. 两个不带数据范围的独立目录能力；
3. immutable exact code、可变名称、展示顺序、阶段概率和显式生命周期；
4. 新 tenant 默认目录和历史 tenant exact backfill；
5. 商机创建、完整更新和独立阶段更新的目录校验；
6. 一个 tenant 级数据库目录守卫、乐观锁和最终数据库外键；
7. B05、保存视图、报表、审批事实和 B11 workflow 的兼容边界；
8. 真实 MySQL 的升级、并发、跨 tenant、索引和外键证据。

## 4. 目录领域模型与稳定身份

### 4.1 两个显式版本化聚合

`OpportunityPipeline` 是 tenant-owned 版本化聚合，首期状态为：

```text
id
tenantId
code          immutable
name
displayOrder
status        ACTIVE | INACTIVE | DELETED
version
createdTime
updatedTime
```

`OpportunityPipelineStage` 是同一 Opportunity 目录边界中的独立版本化聚合，首期状态为：

```text
id
tenantId
pipelineCode immutable
code         immutable within that pipeline
name
displayOrder
probability  0..100
status       ACTIVE | INACTIVE | DELETED
version
createdTime
updatedTime
```

阶段使用独立根而不是让一个 pipeline 命令无界 hydrate 全部历史 stage。B10 从未限制一个 tenant 使用过多少 code；升级必须能够 backfill 合法历史，而不能先假设历史已经符合新目录容量。跨根不变量由 application catalog policy、tenant 目录守卫和数据库唯一键共同仲裁。

管线 code 在 tenant 内永久唯一；阶段 code 在 `(tenant, pipelineCode)` 内永久唯一。`DELETED` 不释放 code。展示名不要求唯一，排序值允许相同；分页固定按：

```text
display_order ASC, code ASC(binary), id ASC
```

相同顺序不会产生不稳定页，也不要求为了移动一个条目而批量改写同级全部版本。

### 4.2 exact code 规则

目录沿用 B10 已公开的 code 语法：

```text
[A-Za-z0-9][A-Za-z0-9._-]{0,63}
```

code 在进入领域前裁剪首尾空白，之后按 ASCII binary exact identity 比较。B32a 不做 NFKC、lowercase、slug 修复、alias、重定向或模糊匹配：

- `DIRECT`、`Direct` 和 `direct` 是三个不同 code；
- 历史根值和公开响应不因升级而改写；
- 管线或阶段“改名”只修改 `name`，绝不修改 code；
- caller 提供一个不存在的大小写变体时按目录 pair 不存在处理；
- 日志和安全错误不回显 caller 提供的 code 或名称。

### 4.3 容量与分页

新建目录条目的代码上限固定为：

| 资源 | 上限 | 语义 |
| --- | ---: | --- |
| 非删除 pipeline / tenant | 100 | 达到后禁止创建新 pipeline |
| 非删除 stage / pipeline | 100 | 达到后禁止创建新 stage |
| 管线或阶段名称 | 200 字符 | 裁剪后校验 |
| code | 64 ASCII 字符 | immutable exact identity |
| displayOrder | 0..1,000,000 | 相同值以 binary code/id 稳定排序 |
| probability | 0..100 | 仅当前展示/配置事实 |
| query page size | 200 | SQL 分页，禁止 tenant 全量加载 |

历史 backfill 超过 100 的合法 tenant 不会被迁移截断或拒绝读取。它们保留全部目录行，但在非删除数量回到上限以下之前不能继续新增；重命名、排序、停用和软删除既有条目仍可用。B32a 不把一个任意大的历史目录装入 JVM 后再计数或分页。

## 5. HTTP、operation 与请求边界

### 5.1 固定路由

管线路由冻结为：

```text
POST   /api/v1/opportunity-pipelines/query
GET    /api/v1/opportunity-pipelines/{pipelineId}
POST   /api/v1/opportunity-pipelines
PUT    /api/v1/opportunity-pipelines/{pipelineId}
PUT    /api/v1/opportunity-pipelines/{pipelineId}/status
DELETE /api/v1/opportunity-pipelines/{pipelineId}?version={version}
```

阶段路由冻结为：

```text
POST   /api/v1/opportunity-pipelines/{pipelineId}/stages/query
GET    /api/v1/opportunity-pipelines/{pipelineId}/stages/{stageId}
POST   /api/v1/opportunity-pipelines/{pipelineId}/stages
PUT    /api/v1/opportunity-pipelines/{pipelineId}/stages/{stageId}
PUT    /api/v1/opportunity-pipelines/{pipelineId}/stages/{stageId}/status
DELETE /api/v1/opportunity-pipelines/{pipelineId}/stages/{stageId}?version={version}
```

operation 编号冻结为：

```text
OPPORTUNITY_PIPELINE_QUERY         = 6315
OPPORTUNITY_PIPELINE_GET           = 6316
OPPORTUNITY_PIPELINE_CREATE        = 6317
OPPORTUNITY_PIPELINE_UPDATE        = 6318
OPPORTUNITY_PIPELINE_STATUS_UPDATE = 6319
OPPORTUNITY_PIPELINE_DELETE        = 6320
OPPORTUNITY_PIPELINE_STAGE_QUERY         = 6321
OPPORTUNITY_PIPELINE_STAGE_GET           = 6322
OPPORTUNITY_PIPELINE_STAGE_CREATE        = 6323
OPPORTUNITY_PIPELINE_STAGE_UPDATE        = 6324
OPPORTUNITY_PIPELINE_STAGE_STATUS_UPDATE = 6325
OPPORTUNITY_PIPELINE_STAGE_DELETE        = 6326
```

### 5.2 请求与响应

query 请求只允许：

```text
keyword  可选，按 code/name 的 escaped literal prefix
status   可选，ACTIVE | INACTIVE | DELETED
page     1-based
size     1..200
```

pipeline create 只接受 `code/name/displayOrder`，创建状态固定为 `INACTIVE`。pipeline update 只接受 `version/name/displayOrder`；status update 只接受 `version/status`，且公开 status command 只允许 `ACTIVE/INACTIVE`。

stage create 只接受 `code/name/displayOrder/probability`，创建状态固定为 `INACTIVE`。stage update 只接受 `version/name/displayOrder/probability`；status update 同样只允许 `ACTIVE/INACTIVE`。

删除使用当前版本并进入终态 `DELETED`。请求不接受 tenant、actor、role、authority、数据范围、默认管线、商机 ID、结果状态、B05 field definition、B11 workflow/approval ID、任务、SQL、表达式、脚本、颜色、图标、货币、预测公式或自动化 payload。

目录响应只返回上述目录字段，不返回商机数量、金额、负责人、客户、保存视图、审批、workflow runtime、Activity 或任何跨域数据。知道目录 ID/code 也不产生任何商机可见性。

## 6. 授权与 tenant 隔离

新增两个 tenant-assignable、无数据范围能力：

```text
opportunity-pipelines:read
opportunity-pipelines:manage
```

- query/get 要求活动成员和 `opportunity-pipelines:read`；
- create/update/status/delete 要求活动成员和 `opportunity-pipelines:manage`；
- tenant owner 继续由代码权威目录动态获得能力；
- 现有自定义角色不自动补 grant；
- `opportunities:read/write/delete/assign`、`fields:*`、`workflows:*`、`approvals:*`、`products:*` 或管理员名称不能替代目录能力；
- 目录能力不授予任何 Opportunity record scope、字段 READ/WRITE、Customer/Contact 可见性或 B11 操作权。

Opportunity create/update 内部校验 active pair 不额外要求 caller 拥有 `opportunity-pipelines:read`。该校验是 `opportunities:write` 用例的不变量，不是目录读取。没有目录读能力的 caller 不能通过错误差异枚举目录：missing、inactive、deleted、cross-tenant 和 pipeline/stage mismatch 统一返回安全的不可用 pair 错误，不回显命中的一半。

每个 repository、guard、query 和外键都显式携带 tenant。stage path 同时使用 verified tenant、`pipelineId` 和 `stageId`；另一个 pipeline 或 tenant 中存在相同 stage ID/code 不能满足目标。

## 7. 生命周期与目录不变量

### 7.1 pipeline

- 新 pipeline 固定从 `INACTIVE` 开始；
- 激活前必须在该 pipeline 下存在至少一个 `ACTIVE` stage；
- `ACTIVE -> INACTIVE` 始终允许，用于立即停止新分配；
- `INACTIVE -> ACTIVE` 重新检查至少一个活动 stage；
- 删除前 pipeline 必须 `INACTIVE`，且其所有 stage 都已经 `DELETED`；
- `DELETED` 是终态，不能恢复、改名、排序或新增 stage；
- 停用或软删除不扫描、不修改也不删除历史 Opportunity。

### 7.2 stage

- 新 stage 固定从 `INACTIVE` 开始；
- 只要 parent pipeline 未删除，stage 可在 `INACTIVE/ACTIVE` 间切换；
- `ACTIVE` pipeline 的最后一个活动 stage 不能停用或删除；应先停用 pipeline；
- 删除前 stage 必须 `INACTIVE`；
- `DELETED` 是终态且 code 不可复用；
- stage 可在 parent pipeline 为 `INACTIVE` 时保持 `ACTIVE`，但 effective assignment 仍要求 parent 和 stage 同时 `ACTIVE`；
- 概率为 100 不自动把 Opportunity 改成 `WON`，概率为 0 也不自动产生 `LOST/INVALID`。

目录名称、顺序和概率更新不增加 Opportunity version，不生成 Opportunity Activity、事件、通知、审批或 workflow 记录。B32a 不提供批量重排；同序值的稳定 tie-break 足以保证分页和展示确定性。

## 8. Opportunity 写入与历史兼容

### 8.1 创建与阶段迁移

以下写入必须在其 command transaction 中解析 exact tenant catalog pair：

- `POST /api/v1/opportunities`：parent pipeline 与 stage 都必须 `ACTIVE`；
- `PUT /api/v1/opportunities/{id}/stage`：只要 pair 与已保存 pair 不同，目标必须双活动；
- `PUT /api/v1/opportunities/{id}`：pair 发生实际变化时同样要求双活动。

pair 校验不能只查 stage code，也不能接受另一个 pipeline 下同名 stage。应用校验后，MySQL `(tenant_id, pipeline_code, stage_code)` 外键继续作为最终同 tenant pair 仲裁。

### 8.2 grandfathered 当前值

停用目录的目的只是阻止新分配，不是冻结全部既有商机。完整更新请求仍携带两个 code，因此 B32a 固定：

- 若请求 pair 与持久化 pair binary exact 相同，允许继续修改名称、客户、日期、金额、描述和 custom/FILE 值，即使目录后来 `INACTIVE/DELETED`；
- 若任一 code 不同，则视为阶段迁移并要求目标双活动；
- `/stage` 的 exact 同值请求在校验当前 Opportunity version 和两个字段的 `WRITE` 后返回当前投影，不保存根、不增加 version，也不要求当前 pair 仍活动；
- 关闭、重开、负责人、联系人、协作者、产品和删除命令不重新验证当前目录 pair；
- closed Opportunity 仍必须先按 B10 显式重开，目录治理不改变 outcome 状态机。

这样目录停用不会使历史商机无法补充普通资料，也不会让旧 pair 再被分配给其他商机。

### 8.3 公开投影

B32a 不改变 `OpportunityResponse` 的 code-only 形状。`pipelineCode/stageCode` 继续由 B05 当前字段访问控制并按既有规则隐藏或脱敏。目录名称、顺序和概率只通过独立目录 API 返回：

- Opportunity read 不能因为可见一个记录而读取完整 tenant 目录；
- 目录 read 也不能得知任一 Opportunity 使用了哪个 stage；
- 目录改名不会改写历史响应中的 code；
- 若以后要在 Opportunity DTO 中加入名称，必须另行冻结字段权限、批量解析、停用/删除回退和同快照语义。

## 9. 持久化与升级

B32a 使用新的：

```text
V85__create_opportunity_pipeline_catalog.sql
```

V85 至少建立：

1. 每 tenant 一行的 `crm_opportunity_pipeline_catalog_guard`；
2. versioned `crm_opportunity_pipeline`；
3. versioned `crm_opportunity_pipeline_stage`；
4. `(tenant, pipelineCode)` 与 `(tenant, pipelineCode, stageCode)` 永久唯一键；
5. pipeline/stage 状态、版本、概率、顺序和 ASCII code CHECK；
6. stage 到 exact tenant pipeline 的复合外键；
7. `crm_opportunity` 到 exact tenant pipeline/stage pair 的复合外键；
8. 管线/阶段分页、活动 pair lookup 和现有 Opportunity pair 查询所需索引。

迁移不能编辑 V34。升级顺序冻结为：

1. 预检所有已有根 code 是否满足 B10 已公开的 ASCII 语法；越过应用直接写入的非法值使升级失败关闭，不做猜测性修复；
2. 把 `crm_opportunity.pipeline_code/stage_code` 转为 `ascii_bin`，不改变任何合法字节；
3. 为每个 exact distinct pipeline 和 pair backfill 一行 `ACTIVE` 目录，名称等于原 code、顺序与概率为 0；
4. 对完全没有 Opportunity 的历史 tenant provision `direct` pipeline，以及 `qualification`、`negotiation` stages；
5. 验证每个 Opportunity 恰好匹配一条同 tenant stage 后再增加复合外键；
6. 保留 saved-view、report-definition、approval fact snapshot、stage-workflow config/runtime 和 Activity 数据原样。

历史大小写变体必须 backfill 为不同 binary identity，不选择“最常见”或字典序 winner，也不重写保存视图值。由 `utf8mb4_unicode_ci` 改为 binary 后，pipeline/stage 的 SQL 条件与 Java identity 统一为大小写敏感；升级验收必须显式覆盖这一可观察兼容变化。

新 tenant bootstrap 在创建 tenant 和 OPPORTUNITY 字段的同一事务中幂等建立 guard 与默认目录：

| pipeline | stage | name | status | order | probability |
| --- | --- | --- | --- | ---: | ---: |
| `direct` | — | Direct | `ACTIVE` | 10 | — |
| `direct` | `qualification` | Qualification | `ACTIVE` | 10 | 0 |
| `direct` | `negotiation` | Negotiation | `ACTIVE` | 20 | 0 |

bootstrap 不覆盖已存在目录，也不把默认 code 放入配置文件。默认目录是普通 tenant 数据，后续受相同版本、权限和生命周期规则管理。

## 10. 事务、锁序与并发

`OpportunityPipelineCatalogLock` 是 application output port，由 MySQL tenant guard 实现。所有目录 create/update/status/delete 和所有可能改变 Opportunity pair 的 command 都必须取得它。

固定锁序为：

```text
Opportunity create/full update/stage update:
  CustomizationObjectLock(tenant, OPPORTUNITY)
    -> OpportunityPipelineCatalogLock(tenant)
    -> Opportunity root when present
    -> exact catalog reads / existing owning-domain relation checks
    -> Opportunity save

Pipeline/stage catalog mutation:
  OpportunityPipelineCatalogLock(tenant)
    -> pipeline root
    -> stage root when present
```

目录 command 在取得 guard 后才做容量、parent status、最后活动 stage、版本和重复 code 检查。等待 guard 的事务不能继续使用 guard 前的 `REPEATABLE_READ` 目录快照。

该顺序保证：

- 并发 stage 停用与 Opportunity 迁入有确定先后；先提交的迁入可保留，停用只阻止其后的新分配；
- 两个并发 create 由 guard、唯一键和乐观锁共同仲裁；
- 最后活动 stage 的并发停用不会同时成功；
- pipeline 删除不能与新 stage 创建交错；
- B05 字段生命周期和 Opportunity 写入仍保持既有 object guard 顺序。

B11 approval/workflow 不取得该目录 guard，目录 command 也不取得 B11 definition/record lock，因为 B32a 明确没有跨边界不变量。不得为了“顺便同步”临时引入相反锁序。

## 11. B05、查询、报表与审批兼容

`pipeline_code` 和 `stage_code` 继续是 OPPORTUNITY 的 required/searchable `TEXT` built-ins：

- B05 `READ/HIDDEN/MASKED` 继续控制 Opportunity 输出和条件可用性；
- B05 `WRITE` 继续控制创建和迁移，目录活动校验是额外交集；
- 目录名称、顺序、概率和状态不是 custom field，也不进入 `customFields`；
- 字段停用/搜索依赖规则不扩展为目录依赖，目录停用也不修改字段定义；
- saved view 与 B17 custom report 继续保存 stable string condition/group values；
- inactive/deleted code 继续可筛选和分组，目录状态不会静默排除历史 Opportunity；
- B17/B18/B25a 的金额、结果和客户概览计算不应用概率，不按目录状态过滤；
- B11 approval fact snapshot 继续捕获 exact 当前 code，目录后续变化不重写 snapshot 或既有 route。

目录 query 的 `keyword` 只搜索目录 code/name，不查询 Opportunity 根，因此不会产生记录计数或数据范围旁路。普通 Opportunity keyword 候选仍只有服务端固定的 `opportunity_name`，B32a 不把目录名称注入全文检索。

## 12. 与 B11 StageWorkflow 的冻结关系

B11 workflow stage 与 B32 sales stage 是两个正交概念：

| B32 sales stage | B11 workflow stage |
| --- | --- |
| Opportunity-owned tenant catalog | generic `BusinessObjectType` workflow definition child |
| 控制新商机和显式销售阶段迁移的合法 pair | 控制 workflow record 的任务、必填字段和可选审批门 |
| code scoped by pipeline | code scoped by workflow definition |
| 独立 `opportunity-pipelines:*` 能力 | 独立 `workflows:*` 能力 |
| 当前名称、顺序和展示概率 | workflow snapshot 中的名称、顺序和 workflow probability |
| 不拥有 workflow runtime | 不拥有 Opportunity pipeline catalog |

因此 B32a 固定：

- 相同字符串 code 不表示同一身份；
- B11 definition/config/runtime 不增加 pipeline/stage FK；
- Opportunity `/stage` 不启动、推进、完成或取消 B11 record；
- B11 `advance/outcome` 不修改 Opportunity pair 或 outcome；
- pipeline/stage 停用、删除或改名不阻止 B11 definition，也不改写已有 record；
- B11 可以继续把当前 `pipeline_code/stage_code` 当作 B05 权威事实参与条件或 snapshot；
- `workflows:manage` 不授予目录管理，`opportunity-pipelines:manage` 也不授予 workflow 管理。

未来协调批次至少要冻结“一商机是否只能有一个绑定 workflow”、迁移请求的幂等 identity、审批 pending 时的写入语义、Opportunity/catalog/workflow/approval 锁序、终态与重试，以及既有独立 record 的升级。B32a 不预占这些答案。

## 13. 分层与所有权

- `api` 拥有 `OpportunityPipeline*Request/Response` 和 `OpportunityPipelineStage*Request/Response`；
- `domain` 拥有两个版本化聚合、状态、code value、catalog query、容量和生命周期 policy，以及 tenant-scoped repository port；
- `application` 拥有目录权限校验、guard 编排、parent/stage 跨根不变量、bootstrap provision 和 Opportunity pair validation；
- `infra/persistence` 拥有 V85、PO、converter、mapper、binary exact SQL、复合外键和 MySQL guard；
- `infra/facade` 只做 Request/VO/Response 映射；
- `infra/rest` 只建立 verified caller、固定路由和安全错误映射；
- `start` 只组合 provisioner、lock adapter 和集成测试，不拥有目录业务规则。

不得建立通用 `BusinessObjectCatalog`、任意 `objectType/code/status` 表、反射目录 router、动态表名、`Map<String,Object>` 配置、B05 option mirror、B11 stage adapter、Redis 真相源或跨 tenant cache。若以后为其他对象增加状态目录，必须分别证明其身份、历史和写入语义。

目录根、stage、guard 和 Opportunity repository 始终显式传 `TenantId`。PO 不进入 domain/API；Request/Response 不进入 domain；目录对象不持有 `AuthenticatedCaller`。

## 14. B32a 实现任务卡

1. 增加 `opportunity-pipelines:read/manage`、`6315..6326` operation 和独立安全路由；
2. 实现 `OpportunityPipeline`、`OpportunityPipelineStage`、typed request/VO/response、repository 和 catalog policy；
3. 增加 V85 的 guard、目录表、binary code backfill、默认目录、复合外键与索引；
4. 为新 tenant bootstrap 增加幂等 `OpportunityPipelineProvisioner`；
5. 在 Opportunity create/full update/stage update 中按固定锁序校验 exact active pair，并保留 unchanged grandfathered pair；
6. 实现分页目录 query、生命周期、概率/顺序、容量、版本和跨 tenant 失败关闭；
7. 明确保持 B05 `TEXT` fields、code-only Opportunity response、历史 saved views/reports/approval facts 和 B11 runtime 不变；
8. 把所有直接插入 `crm_opportunity` 的 MySQL fixture 改为先 provision exact catalog pair；
9. 补充 domain/application/REST/MySQL migration/concurrency/FK/EXPLAIN 测试；
10. 实现后更新 README、API reference、architecture、object layering、test guide、`llms.txt` 与 `docs/acceptance/B32a.md`。

## 15. 验收门槛

B32a 完成必须同时证明：

1. 匿名、失活成员、缺少 exact 目录能力和跨 tenant 的所有目录请求均失败关闭；
2. `opportunity-pipelines:read/manage` 无数据范围，且不能替代任何 `opportunities:*`、B05 或 B11 能力；
3. code 裁剪后严格符合 B10 ASCII 语法，binary 大小写变体可独立 backfill、查询和引用；
4. code 创建后不可改且删除后不可复用；名称可重复，同序分页仍稳定；
5. pipeline/stage create 从 `INACTIVE` 开始，激活、最后活动 stage、停用和删除前置条件全部由 guard 内当前状态决定；
6. 100 pipelines、每 pipeline 100 stages 和 page size 200 的新建/查询边界生效；合法历史超限不被截断且不能继续新增；
7. create 与实际 pair 变化只接受 exact 双活动 pair；stage 不能跨 pipeline 或 tenant；
8. full update 的 binary exact unchanged 历史 pair 在目录停用/删除后仍可编辑其他字段，变更任一 code 则必须迁入活动 pair；
9. catalog 停用与 Opportunity 迁入、最后 stage 并发停用、重复 code 创建、pipeline 删除与 stage 创建都有唯一确定结果；
10. V85 从空库和 V84 升级均成功，非法历史 code 失败关闭，合法 code bytes、Opportunity version 和全部 JSON snapshot/definition 保持不变；
11. 数据库直接写入未知、跨 pipeline 或跨 tenant pair 被复合外键拒绝；
12. 新 tenant 精确得到一套 `direct/qualification/negotiation` 默认目录，重复 bootstrap 不覆盖或重复；
13. saved views、自定义报表、固定报表、dashboard、B25a 和 approval facts 对 inactive/deleted code 继续工作，且不应用 probability；
14. B11 同名 stage 不建立引用或联动，workflow advance 与 Opportunity stage update 互不修改；
15. pipeline/stage query 使用 tenant、status、稳定顺序和有界分页索引，不做 tenant 全量 JVM 过滤或 N+1 Opportunity count；
16. 日志、metric、exception 和测试输出不含 caller 提供的目录名称、code、原始请求或数据库异常；
17. `sh ./mvnw test` 与 `CI=true sh ./mvnw test` 全部通过。

## 16. 明确非目标

B32a 不包含：

- B31a Contact CSV 的实现、Opportunity/Lead/Contract 导出、导入或批量迁移；
- 阶段漏斗、加权金额、预测、目标、配额、转化率、停留时长、阶段历史或看板；
- 自动 outcome、自动关闭/重开、自动 Activity、提醒、通知、Webhook 或 durable automation；
- B11 workflow 绑定、stage 同步、审批门接管 `/stage` 或一个通用业务流程引擎；
- pipeline template marketplace、跨 tenant 复制、导入导出、版本发布、草稿或回滚；
- caller-defined code normalizer、alias、大小写折叠、模糊匹配、删除后复用或历史 code 重写；
- 目录名称进入 Opportunity response、keyword、saved-view option label、report label 或审批 snapshot；
- 多币种、金额汇率、按阶段 probability 自动计算 forecast；
- Product catalog、Customer/Contact pool、Activity options、B05 field catalog 或其他对象目录的泛化；
- Redis catalog truth、缓存一致性协议、event/outbox、Activity audit payload 或外部副作用。

## 17. 实施顺序

1. 先实现 V85 upgrade fixture，证明 exact binary backfill、默认目录、超限 grandfather 和复合外键；
2. 再建立两个目录 aggregate、guard、权限与生命周期负向测试；
3. 接入 Opportunity create/full update/stage update，优先证明 unchanged inactive pair 与 concurrent deactivate 的边界；
4. 再开放分页管理 API，并验证目录读取不查询 Opportunity record 或泄漏 count；
5. 补齐 B05/saved-view/report/approval/B11 兼容回归和所有直接 SQL fixture；
6. 最后运行完整 fast/MySQL 套件并回写 `docs/acceptance/B32a.md`；
7. B32a 验收前不拆 B32b，也不把 B11 同名 stage 解释为已完成销售阶段工作流集成。
