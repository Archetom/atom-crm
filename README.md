# atom-crm

基于 Java 27 和 Spring Boot 4.1.2 的开源 CRM 后端，采用领域驱动设计与多模块架构。

[English](README.en.md) · [快速开始](#快速开始) · [文档](#文档) · [Web 前端](https://github.com/Archetom/atom-crm-web)

## 主要功能

- **客户管理**：客户、联系人、线索、公海与跟进记录。
- **销售流程**：商机与销售阶段、产品、合同、发票、回款与回款计划。
- **团队协作**：组织成员、工作任务、审批、公告与工作台。
- **数据分析**：销售报表、仪表盘与业绩目标。
- **权限与定制**：多租户隔离、角色授权、记录范围、字段权限与自定义字段。

本仓库提供后端 API，浏览器界面由独立的 [atom-crm-web](https://github.com/Archetom/atom-crm-web) 提供。电话、企业数据源、文件存储等集成能力需要额外配置，具体前提见[配置指南](docs/configuration.md)。

## 快速开始

### 环境要求

- JDK 27.0.1 或更新的 Java 27 补丁（非 LTS；CI 与容器使用 Temurin 27.0.1.0.2+1）
- Docker 与 Docker Compose
- Maven Wrapper 3.10.0 已随仓库提供，无需单独安装 Maven。Redis 可选，默认关闭。

### 启动后端

```bash
git clone https://github.com/Archetom/atom-crm.git
cd atom-crm
docker compose up -d mysql
sh ./mvnw clean install
sh ./mvnw -pl start -am spring-boot:run -Dspring-boot.run.profiles=dev
```

启动后可访问[健康检查](http://localhost:8080/actuator/health)和 [Swagger UI](http://localhost:8080/swagger-ui/index.html)。开发配置位于 [`conf/application-dev.yml`](conf/application-dev.yml)。

### 启动前端与首次安装

在另一个终端启动前端，Node.js 与 pnpm 版本以[前端仓库说明](https://github.com/Archetom/atom-crm-web#readme)为准：

```bash
git clone https://github.com/Archetom/atom-crm-web.git
cd atom-crm-web
pnpm install --frozen-lockfile
pnpm dev
```

打开前端终端显示的地址，在 `/setup` 创建首个管理员，之后通过 `/login` 登录。系统没有默认账号或密码，普通使用无需开启开发身份头。前端默认将 API 请求代理到 `http://localhost:8080`。

以上为本地开发流程。生产环境的 HTTPS、数据库、会话密钥与邮件配置见[部署指南](docs/deployment.md)。

## 开发与测试

工程基线已合并 Atom Archetype 2.3.0，使用 atom-common 1.2.0；CRM 的业务模块、身份与授权契约及数据库迁移历史保持独立维护。开发规则见 [AGENTS.md](AGENTS.md)，五个按需使用的工作流见[任务索引](llms.txt#task-workflows)。

```bash
make agent-check SCOPE=fast       # 显式跳过数据库测试
make agent-check SCOPE=affected   # 根据 Git 变更保守选择范围
make agent-check                 # 默认 full，要求 Docker 和完整数据库测试证据
```

验证入口输出 JSON，详细日志写到标准错误；缺少必需的 Docker 会返回阻塞和非零退出码。直接运行 `sh ./mvnw test` 仍会在 Docker 不可用时跳过数据库测试；`CI=true/false` 不控制该行为，只有 `-Datom.test.skip-database-tests=true` 显式跳过。快速测试成功不代表数据库验证通过，完整说明见[测试指南](docs/test-guide.md)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [部署指南](docs/deployment.md) | 构建产物、容器运行、生产配置与升级 |
| [配置指南](docs/configuration.md) | 身份认证、数据库、缓存与可选集成 |
| [API 参考](docs/api-reference.md) | HTTP 接口、请求响应与权限约束 |
| [架构设计](docs/architecture.md) | 模块职责、领域模型与事务边界 |
| [开发指南](docs/usage-guide.md) | 新增用例、接口与数据库迁移 |
| [测试指南](docs/test-guide.md) | 测试分层与验证命令 |
| [领域契约指南](docs/domain/README.md) | 按需记录业务事实、待确认假设与可执行约束 |

[变更记录](CHANGELOG.md) · [1.0.0 部署与发布](docs/deployment.md) · [完整文档索引](llms.txt)

## 参与贡献

欢迎提交 Issue 和 Pull Request。开始前请阅读[贡献指南](CONTRIBUTING.md)；安全问题请按[安全政策](SECURITY.md)私下报告。

## 许可证

[MIT](LICENSE)
