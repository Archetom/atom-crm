# R1 / 1.0.0 发布范围冻结

> 功能基线：`main@30550d46`（B32a 已合入）
>
> 冻结日期：2026-07-24
>
> 发布版本：`io.github.archetom:atom-crm:1.0.0`
>
> 状态：范围已冻结；发布结论以 [R1 主干验收记录](docs/acceptance/R1.md) 为准

> 本文记录 2026-07-24 的冻结范围，不代表当前主干的完整功能清单。后续密码认证、首次安装、有界 CSV 导入等变化见 [CHANGELOG](CHANGELOG.md)、[README](README.md) 与当前代码；部署以 [部署指南](docs/deployment.md) 和 [配置文档](docs/configuration.md) 为准。

## 1. 发布定义

R1 是 atom-crm 的第一个稳定开源主干版本。1.0 承诺的是仓库中已经实现、由公开
测试和 MySQL Testcontainers 可复现的应用边界，不把设计文档、合成 fixture、默认
关闭的适配器或需要私有部署证据的能力提升为通用生产就绪。

R1 保持现有依赖方向、显式 caller/connector 身份、tenant 隔离、字段策略、数据库
锁序、乐观锁、事务后副作用和稳定公共错误契约。`/api/v1` 是 1.x 的公共 HTTP
命名空间；不兼容的请求、响应或持久化语义变更必须进入新的版本化边界。Flyway
`V1` 至 `V85` 均视为已发布迁移，只能追加，不能修改。

## 2. 1.0 纳入范围

R1 冻结以下已经实现的纵切：

1. 本地组织与成员、tenant 授权和导航、B05 字段/布局/视图/嵌入页治理；
2. Customer、Contact、Lead、Opportunity、Product、Contract、Invoice、
   Receivable、ReturnVisit 及其数据范围、字段策略、层级、池、转换和关系约束；
3. Opportunity 销售管线/阶段目录，以及独立的 B11 审批与阶段工作流；
4. Activity、九来源工作台、Work 任务/协作/周期/提醒/站内通知；
5. 固定报表、自定义报表、仪表盘偏好与固定销售概览；
6. MarketingForm、OA 表单/申请/公告及其幂等、受众快照和协作边界；
7. Customer/Contact 有界、权限感知、公式安全的 CSV 导出；
8. B24 文件内核以及 Work、CRM、Product、ReturnVisit、MarketingForm、OA
   目标接入，但仅在部署同时满足存储、检查、容量和独立 conformance 门槛时启用；
9. B26 固定调度内核、Work recurrence、OA 一次性发布和可靠通知 outbox，
   均保留各自显式配置与 readiness 门；
10. B22 connector 控制面和可靠性基础、B23 企业数据查询/采用基础，按下节的
    受限状态交付，不代表真实电话线路或真实外部数据源已可用。

各纵切的详细 HTTP、领域、迁移和验收契约继续由 `batch-B17.md` 至
`batch-B32.md`、`docs/architecture.md`、`docs/api-reference.md` 与
`docs/acceptance/` 共同定义。R1 不复制或放宽这些安全不变量。

## 3. 受限能力与默认关闭项

以下代码可以随 1.0.0 构建交付，但不属于“开箱即用的通用生产能力”：

| 能力 | R1 冻结状态 | 启用条件 |
| --- | --- | --- |
| 生产身份认证 | 当前主干已提供密码登录、首次安装与签名会话；trusted header 仅限 dev/test | HTTPS、稳定签名密钥、找回密码 URL，以及已配置的 SMTP 欢迎通知或自定义通知适配器 |
| B22 电话运行时 | connector 控制面、MySQL 基础和合成/传输证据已交付；真实 PBX/线路仍阻断 | 私有适配器完成 fencing、journal/ledger、媒体、敏感数据和真实 canary |
| B23 外部企业数据 | runtime/policy/adoption 默认失败关闭 | 当前 build 的完整 live conformance、权利/隐私/版权批准与 source policy |
| B24 文件 | production 默认 disabled | 受支持的 storage/scanner、正值容量策略和独立三层 conformance report |
| B26/B27/B28 后台 lane | 默认 disabled 或按 lane 失败关闭 | 显式有效调度策略及相应 runtime readiness |
| EMAIL delivery | 偏好默认关闭，provider adapter 不随仓库承诺 | 当前 provider conformance、endpoint binding 和部署 readiness |
| 匿名 MarketingForm FILE | 默认关闭 | B24 ready 且 public-marketing 独立策略有效 |
| Redis | 可选且默认关闭 | 显式配置；MySQL 仍为事实源 |

任一受限能力未满足门槛时必须继续失败关闭，且不得阻断普通 CRM 请求。R1 的
版本号、通过的合成测试或一份局部 canary 报告都不能替代部署级证据。

## 4. 明确后置

R1 不纳入 B32b 或任何尚未冻结的后续批次，也不预占：

- 真实电话 provider/PBX 适配器、外部线路和录音生产服务；
- 未完成 live conformance 与权利审查的企业数据源；
- S3-like 生产 bean、通用文件分享、预览、OCR 或外部下载授权；
- CSV/Excel 导入、异步或无界导出、跨对象迁移与批量回滚；
- 任意脚本/cron/通用自动化 API、通用 webhook 或任意通知模板；
- Opportunity 销售阶段与 B11 workflow 的隐式同步；
- 多责任人任务、子任务依赖图或未单独冻结的跨域 timeline。

这些项目进入 1.x 前仍需独立的范围、授权、tenant、事务、失败恢复、迁移和验收
设计；不能以“补齐 1.0”名义绕过既有门槛。

## 5. 发布门禁

R1 候选必须在 JDK 25、仓库内固定版本的 Maven Wrapper 和可用 Docker 环境中通过：

```bash
make release-verify
```

该命令固定执行：

1. 根 Maven 版本必须等于 `1.0.0`，且不是 SNAPSHOT；
2. 所有 POM、运行时 API 元数据、README、`llms.txt` 和 OpenAPI 验证不得残留
   SNAPSHOT 发布版本；
3. `git diff --check` 必须通过；
4. Docker 必须可访问；`CI=true sh ./mvnw --batch-mode --no-transfer-progress clean install` 必须通过，
   因而同时执行架构/单元/边界测试、Flyway/MySQL Testcontainers 集成回归并生成
   所有模块的可安装构件；
5. 从当前数据库测试源码发现测试类，并核验 Surefire XML 中每个类均有实际执行、无失败且无跳过。普通 `mvnw test` 在 Docker 不可用时可跳过数据库测试，发布门禁不接受此结果。

发布候选还必须保持生产 trusted header 不能启用、迁移校验开启、Flyway clean
关闭、Redis/调度/文件/外部适配默认安全状态，以及日志和差异中不存在凭据或敏感
请求数据。

## 6. 发布操作边界

本文件和验收记录完成的是可合入主干的 R1 候选。创建 Git tag、GitHub Release、
推送 Maven 制品、部署生产环境和启用任何受限适配器属于独立的发布/运维授权；
这些外部写操作不由主干验收隐式执行。
