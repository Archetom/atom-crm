# atom-crm

[English](README.en.md)

[快速开始](#快速开始) · [部署指南](docs/deployment.md) · [参与贡献](CONTRIBUTING.md) · [报告安全问题](SECURITY.md) · [变更记录](CHANGELOG.md)

项目仓库：[后端 atom-crm](https://github.com/Archetom/atom-crm) · [前端 atom-crm-web](https://github.com/Archetom/atom-crm-web)。

`atom-crm` 基于 Java 25 和 Spring Boot 4.1，采用多模块 Maven 结构。项目由 Atom Archetype 生成，提供用户与组织管理、租户授权与自定义、客户/联系人/线索/商机/产品聚合、公海和线索开放接入能力。用户查询以具名 `UserPageResponse` 返回分页元数据和 `UserResponse` 列表，方便生成的客户端保持完整类型信息。

仪表盘概览的 `salesBrief` 额外发布九项固定、权限感知的当前时间窗统计：新增客户、联系人、商机、合同、合同金额、商机金额、回款金额、线索和跟进。前八项由其来源对象的读取能力、数据范围与字段策略独立裁决；“新增跟进”只在 `activities:read`、活动成员资格和 `SELF` 范围同时成立时，统计当前成员创建的有效跟进记录。可用项给出服务器汇总的数量或金额以及上一可比周期的 `comparisonRate`，不可用项保持不含数值的 `UNAVAILABLE`，不会以零值替代受限数据。

同一概览还发布闭合的 16 项 `dataSummary` 布局。新增/转成交客户、新增/赢单/输单商机、商机金额、签约合同、合同金额、即将/已到期合同、跟进客户、新增未跟进客户、回款金额和预计回款均由各自已收窄来源独立裁决；转成交只按可见、有效且 `WON` 客户的持久 `deal_time` 在当前窗口计数，输单只按可见、有效商机的不可变 `outcome_time` 和 `LOST` 结果计数，合同到期按结束日相对请求固定基准日分桶，跟进覆盖按有效客户跟进与客户创建时间计数，预计回款按有效计划的到期日和计划金额汇总。公海“放入”和“领取”由追加式历史而非当前池成员推导：人工或自动入池在同一事务写入释放历史，直接领取保留既有领取历史且排除管理员指派；概览还要求 `customer-pools:read`、`customers:read`、当前可访问的公海目录，并按释放前负责人或领取人的当前客户数据范围收窄。这样既不把已领取客户的现态伪造成释放历史，也不会通过不可访问公海扩大统计范围。历史表上线前的旧流转不会被回填为猜测值；不可用来源始终返回不含数值的 `UNAVAILABLE`，浏览器不得以客户、公海或活动列表补造指标。

R1 发布坐标：`io.github.archetom:atom-crm:1.0.0`。1.0 的冻结范围、部署门槛和明确后置项见 [R1 发布范围](release-R1.md)。本仓库提供后端 API；浏览器界面位于独立的 `atom-crm-web` 仓库，运行完整 CRM 需要同时启动二者。

## 回款计划查询场景与高级筛选

`POST /api/v1/receivable-plans/query` 与 `POST /api/v1/receivable-plans/summary` 现在同时支持明确负责人 `ownerActorId` 或下属负责人场景 `subordinateOwnerOnly`，两者互斥。下属集合只能由服务器依据已验证调用者、当前租户组织树和已解析的数据范围取得；空集合明确返回空结果，列表与金额汇总复用同一谓词。两端点也接受当前成员自有的 `savedViewId` 或一组临时 `conditions`（最多 50 条、两者互斥）；服务器按当前可搜索、可读且未脱敏的 RECEIVABLE_PLAN 字段目录重验类型、操作符、关系记录和数据范围，再将参数化谓词同时用于分页与三项金额汇总。

## 环境要求

- JDK 25
- 内置 Maven Wrapper 3.9.16
- Docker 与 Docker Compose，用于本地 MySQL 和集成测试
- Make（可选）

Redis 默认关闭，不是构建或启动项目的必要条件。

## 快速开始

在本仓库根目录启动 MySQL 并构建全部模块：

```bash
docker compose up -d mysql
sh ./mvnw clean install
```

使用 `dev` profile 启动应用：

```bash
sh ./mvnw -f start/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

检查健康端点：

```bash
curl http://localhost:8080/actuator/health
```

配置文件位于 `conf/`。开发环境的 API 文档地址为 `http://localhost:8080/swagger-ui/index.html`。

### 启动前端

从上方前端仓库入口取得配套版本的 `atom-crm-web`，检出到独立目录。在该目录使用其 `.node-version` 与 `package.json` 指定的 Node.js 和 pnpm 版本执行：

```bash
pnpm install --frozen-lockfile
pnpm dev
```

打开前端终端输出的本地地址。开发服务器默认把同源 `/api` 请求代理到 `http://localhost:8080`；后端使用其他地址时，按前端 `.env.example` 配置 `VITE_API_PROXY_TARGET`。仓库已包含 OpenAPI 快照与生成类型，首次运行无需刷新；修改接口后再按前端 `openapi/README.md` 同步契约。两仓库应使用一同验证的版本，并在发布说明中记录各自提交号。

### 首次安装

普通使用不需要开启开发身份头。尚无可登录的活跃 tenant owner 时，前端应先打开 `/setup`，并通过 `GET /api/v1/installation` 确认可以初始化。提交 `POST /api/v1/installation/tenant-admin` 会原子创建固定工作区 1、其 owner 管理员、成员资料、成员资格、owner 角色及默认业务配置，并在当前浏览器签发登录会话；流程随后永久关闭。系统不提供默认账号或密码，部署人员必须在这个页面自行设置管理员凭据。完成后通过 `/login` 使用密码与浏览器会话登录；新部署在初始化完成前应只允许部署人员访问。仅有历史租户、成员或角色记录并不代表完成安装；升级迁移只会为已有可登录活跃 owner 保持关闭状态。

### 生产部署边界

以上命令仅用于本地开发。完整构建、容器运行与发布前检查见[部署指南](docs/deployment.md)；[.env.example](.env.example)提供无真实凭据的生产变量模板。Spring Boot 不会自动加载 `.env`，需通过部署平台或容器注入。生产使用 `prod` profile，按[生产配置](docs/configuration.md#production-settings)提供数据库凭据、稳定的会话签名密钥、HTTPS 同源代理与密码重置落地地址。欢迎通知需要配置 SMTP（`ATOM_USER_NOTIFICATION_MAIL_ENABLED=true`、`ATOM_USER_NOTIFICATION_MAIL_FROM` 与 `spring.mail.*`）或提供自己的 `UserNotificationPort` 实现；密码重置邮件有独立开关。不要把开发身份头或 Compose 中的本地数据库密码用于生产。

文件、企业数据、呼叫和后台自动化等能力各有部署前提；功能代码或测试夹具存在不代表生产已启用。发布前按 [R1 范围与门禁](release-R1.md)核对实际支持范围，并验证配套前后端版本。

## 常用命令

| 命令 | 用途 |
|---|---|
| `make compile` | 编译全部模块 |
| `make test` | 运行测试；Docker 可用时自动包含 MySQL 集成测试 |
| `make integration-test` | 同样按 Docker 可用性运行测试；验收时需确认集成用例实际执行 |
| `make infra-up` | 启动本地 MySQL 和 Redis |
| `make run` | 使用 `dev` profile 启动应用 |

没有安装 Make 时，可查看 `Makefile`，直接运行对应的 Maven Wrapper 或 Docker Compose 命令。

## 项目结构

| 模块 | 职责 |
|---|---|
| `api` | 对外请求、响应、Facade 契约和 `AuthenticatedCaller` |
| `domain` | 聚合、值对象、领域事件、仓储与领域服务端口 |
| `application` | 用例编排、命令/查询模板、输出端口和事务回调 |
| `shared` | 框架中立的结果与错误类型 |
| `infra/rest` | Spring MVC、Spring Security 和 HTTP 错误映射 |
| `infra/persistence` | MyBatis-Plus、Flyway 和缓存适配器 |
| `infra/external` | 第三方系统适配器 |
| `infra/security` | 密码哈希等安全适配器 |
| `infra/facade` | Facade 契约实现 |
| `start` | Spring Boot 组合根和集成测试 |

依赖方向为 `infra → application → domain`。`domain` 不依赖 `application`、`api` 或任何 `infra` 模块，详细规则见[架构文档](docs/architecture.md)。

## 配置

- `conf/application-dev.yml`：本地开发配置。
- `conf/application-test.yml`：自动化测试配置。
- `conf/application-prod.yml`：生产配置，不提供数据源凭据默认值。

`X-Dev-User-Id` 和 `X-Dev-Tenant-Id` 请求头仅用于显式启用的 `dev`、`test` 环境。正常浏览器访问使用已实现的密码登录、HttpOnly 会话 Cookie 与同源 CSRF 校验；生产部署必须配置签名密钥、HTTPS 和同源代理，详见[浏览器认证配置](docs/configuration.md#browser-password-authentication)。

组织接口位于 `/api/v1/organization`。成员关系由已验证 actor 与租户的显式记录建立；部门、成员和申请的写入都在当前激活租户内校验。部门结构写操作和目标租户成员申请按 tenant 串行化，避免并发迁移形成环或保留过期的资格检查；删除要求没有任何子部门或成员归属，停用仅拒绝活跃依赖。部门成员接口会分页返回可见部门中的活跃成员；`includeDescendants=true` 时，服务端从当前可见部门出发解析其活动子部门，再以同一个数据库分页查询成员，浏览器不能用目录缓存扩展范围。部门成员工作台专用行同时提供已解析的所属部门；仅当已验证 caller 分别拥有 `users:read` 与 `roles:read` 时才会提供登录名与活动角色名称，通用成员接口不会返回这些字段。管理员的成员申请分页只读取当前激活租户目标的记录；`GET /membership-requests/mine` 只按已验证申请人分页其自身记录，并与提交、撤回共同仅要求 `organization:request`，不会枚举目标租户、部门或其他申请人。`POST /api/v1/users` 在同一事务内还会为本地新用户建立活动成员资料、当前租户成员资格和基础成员角色；用户停用、锁定、恢复或软删除会同步该成员入口并失效授权缓存。`targetTenantId` 仅用于提交成员申请或检查既有访问权，绝不会切换 caller 或签发新凭证。区域参考数据独立位于 `/api/v1/reference/regions`；默认没有内置数据集，部署可提供一个标注 `@Primary` 的经治理数据适配器。

授权与导航接口位于 `/api/v1/authorization` 和 `/api/v1/navigation`。`GET /authorization/me` 返回数据库解析的租户角色授权、数据范围以及已验证认证上下文的有效平台权限，使浏览器的页面防护与服务端下发导航使用同一能力快照；调用方身份与租户仍只来自受信认证层。租户 owner 的能力由代码目录动态计算，自定义角色只能授予目录中可委派的能力。B05 自定义接口位于 `/api/v1/fields`、`/api/v1/saved-views` 和 `/api/v1/embedded-pages`：字段元数据与角色字段策略按 tenant 隔离，布局和保存视图属于当前成员；字段生命周期变更与布局、保存视图写入按 `(tenant, objectType)` 串行化，生命周期变更会拒绝仍存在的布局或视图依赖，读取也会按当前字段目录失败关闭；保存条件是有界的类型化结构，不是可执行查询；保存视图的每个条件还可显式标记为浏览器可外露条件，该展示元数据不会进入临时查询合同或 SQL 谓词；字段目录同时返回每个字段由服务端确定的 `filterOperators`，其中包含每个操作符可接受值的最小和最大数量；保存视图的列表、创建、更新和设为默认均返回具名 `SavedViewResponse`，删除为明确空响应；嵌入页的配置、分页、写入和活动渲染都发布具名响应，创建与生命周期候选仅由同一服务端配置端点返回；嵌入页只渲染经过校验的 HTTPS URL 模板，服务端不会访问目标地址。

B06 客户接口位于 `/api/v1/customers`。客户资料、标签、地址、协作者、层级和自定义字段值作为一个版本化聚合原子保存；标签保留展示名，但增删、去重和目录都使用 NFKC 加小写的独立规范键。普通查询只返回当前 tenant 中由 `SELF`、`DEPARTMENT`、`DEPARTMENT_SUBTREE` 或 `TENANT` 数据范围允许的记录，并再次应用字段访问和遮罩策略，时间字段被遮罩时也不会返回精确值。具有 `activities:read` 的客户读取会以一次受限批量查询汇总该客户直接主题的最新 ACTIVE 跟进，并在 `CustomerResponse` 回显可选 `lastActivityTime` 与 `lastActivityContent`；无该能力时字段省略，活动关系不会被当作客户摘要。客户列表的静态排序使用固定服务端字段；也可通过 `customSortFieldCode` 选择当前目录中的活动、非内置、标量可排序字段，服务端会先解析字段定义、检查未遮罩读取权限，再使用已解析的定义 ID 排序，绝不把请求字段名拼接进 SQL。客户保存视图只执行已验证的类型化条件；非空名称、负责人、查重、附近和标签查询还要求相关字段可读且未遮罩，层级上限只按过滤后的可见记录计算，写入侧保证整棵层级树不超过 64 层和 2,000 个节点。所有客户聚合保存与字段生命周期按 `(tenant, CUSTOMER)` 串行化，自定义字段的唯一性在创建后不可变。删除写入 `status=DELETED`，不会物理删除客户。

B25a 在客户上下文增加 `GET /api/v1/customers/{customerId}/business-overview`：先证明该客户当前可见，再按各自的读能力、数据范围和未遮罩字段策略返回七张固定卡片。一个来源不可读只返回不含数据的 `UNAVAILABLE` 卡片，绝不以客户权限替代来源域权限。

B07 客户公海接口位于 `/api/v1/customer-pools`。公海配置、管理员、可领取成员/部门和自动收回规则属于版本化 tenant 聚合；一个 tenant 最多保留 500 个未删除池，一个客户最多同时处于一个公海。入池要求独立的 `customer-pools:release` 能力，并继续受 `customers:write` 数据范围约束；领取、分配和池内删除分别使用独立能力。入池会把客户置为 `IN_POOL` 并移除负责人和协作者，领取或分配后恢复为 `ACTIVE`。MySQL 记录锁和领取历史负责日限额、前负责人冷却与并发领取仲裁，池内查询仍应用 B05 字段访问和遮罩，并仅在父客户仍位于同一可访问公海时返回其关系。成员目录通过轻量根投影在数据库中完成全部资格过滤和当前成员管理员判定，不加载配置关系；tenant owner 可查看所有活跃池，客户数按列表批量聚合。B26a 已将自动收回接入固定的 durable schedule/run：每 tenant 游标仍最多扫描 50 个池、每页 25 个，而 schedule 只由当前活动自动规则派生，后台不会伪造人工 caller。

B08 联系人与联系人公海接口分别位于 `/api/v1/contacts` 和 `/api/v1/contact-pools`。联系人是独立的版本化聚合，拥有负责人、协作者、标签、地址、上下级、职务、下次联系时间和 CONTACT 自定义字段；可选客户关联仅在写入时验证为当前 caller 可见的同 tenant 客户，读取也再次按客户数据范围失败关闭，并可在更新请求中明确传 `customerId: null` 解除关联，或传 `nextContactTime: null` 清空下次联系时间。具有 `activities:read` 的联系人列表会以受限批量查询汇总每个已可见直接 CONTACT 主题的最新 ACTIVE 跟进，并在 `ContactResponse` 中可选回显 `lastActivityTime` 与 `lastActivityContent`；无该能力时字段省略，关联对象的活动不会参与摘要。联系人列表的静态排序使用固定服务端字段；也可通过 `customSortFieldCode` 选择当前目录中的活动、非内置、标量可排序字段。服务端先解析字段定义、确认未遮罩读取权限，再以已解析的定义 ID 排序；自定义与静态排序字段互斥，请求字段名绝不进入 SQL。保存视图中的客户和上级联系人关系仅支持 `EQ`/`IN`，且每个目标必须对当前 caller 可见，避免通过关系条件旁路推断隐藏数据。联系人和联系人公海分别使用 `contacts:*` 与 `contact-pools:*` 能力及独立表，复用字段策略、成员资格、限额、冷却和公平自动收回的不变量，不会借由客户或客户公海扩大访问范围。

B09 线索接口位于 `/api/v1/leads`。线索拥有独立的字段策略、数据范围、标签、协作者、地址、层级、保存视图和乐观锁；具有 `activities:read` 的线索列表会以受限批量查询汇总每个已可见直接 LEAD 主题的最新 ACTIVE 跟进，并在 `LeadResponse` 中可选回显 `lastActivityTime` 与 `lastActivityContent`；无该能力时字段省略，关联对象的活动不会参与摘要。列表静态排序使用固定服务端字段，也可通过 `customSortFieldCode` 选择当前目录中的活动、非内置、标量可排序字段。服务端先解析该字段定义、确认未遮罩读取权限，再以已解析的定义 ID 排序；自定义与静态排序字段互斥，请求字段名绝不进入 SQL。`POST /api/v1/leads/{leadId}/convert` 在一个事务中创建客户和联系人，并在不可变的已转换线索上持久化 `customerId` 与 `convertedContactId`。已转换线索仍可查询和读取，但不能再次转换或修改。开放提交仅限 `POST /api/v1/open/leads` 的 `X-API-Key: <tenantLocator>.<keyId>.<secret>`；locator 必须是规范的无填充 Base64URL 编码以限定 tenant 查询，大小写敏感的 keyId 在该 tenant 内精确标识客户端，租户与负责人仍只从通过 secret 校验的已启用接入客户端派生。认证还要求负责人维持启用成员关系，并只向请求上下文授予 `leads:write`；应用层继续解析当前数据范围和字段策略。`GET /api/v1/lead-intake-clients`、创建与版本化停用接入客户端都要求 `leads:intake-manage`；列表和停用只返回不含密钥的具名客户端摘要。创建接入客户端还要求该负责人当前拥有 `leads:write`、可用的线索数据范围，以及 `lead_name`、`owner_actor` 的写权限；每次公开提交仍按负责人当前的业务和字段策略校验。成功提交只返回接收回执，不返回内部线索、负责人或版本标识。服务端生成密钥，创建响应以 `{ client, apiKey }` 返回完整 `apiKey` 一次，之后只保存哈希且不会进入列表、后续响应或日志；引入该格式的迁移会停用旧的两段式密钥，需重新创建客户端。每个 tenant 最多 100 个启用客户端；认证查询前原子预留来源 in-flight 名额，成功后释放、失败后计数，认证基础设施异常则取消预留。已耗尽的已验证 key 会直接拒绝；未知凭证指纹仅占用独立且有界的 pending 缓存，secret 验证成功后才进入按 tenant 分区的已验证 key 桶，因此伪造凭证不会占满租户的已验证 key 容量。预留在查询前已绑定 keyId，停用客户端会立即释放其 settled 或 in-flight 本地 key 桶以支持密钥轮换。已验证 key 默认每分钟最多提交 60 次；每来源每分钟最多 20 次失败认证，来源桶有界且过期回收，已验证 key 桶按 tenant 隔离且不会为了新 tenant 淘汰未过期限额。部署在反向代理后应设置 `atom.lead-intake.behind-proxy=true` 并配置受信代理地址，或在网关执行来源限流。内置限流器是单实例内存态；多副本部署若要求全局配额，应由网关或共享限流服务执行。

B10 商机接口位于 `/api/v1/opportunities`。商机是 tenant-scoped 的版本化聚合，原子保存必填客户、负责人、销售管线/阶段、预计成交日期与金额、联系人和首要联系人、协作者及 OPPORTUNITY 自定义字段。`opportunities:read/write/delete/assign` 独立解析数据范围，关系写入与输出还会按当前客户、联系人能力再次失败关闭；创建、完整客户替换、联系人替换和首要联系人更新都要求每个可见联系人属于目标客户，首要联系人仍必须属于关联集合。具有 `activities:read` 的商机查询会以受限批量查询汇总每个已可见直接 OPPORTUNITY 主题的最新 ACTIVE 跟进时间，并在 `OpportunityResponse` 中可选回显 `lastActivityTime`；无该能力时字段省略，关联对象的活动不会参与摘要。V96 在关系表持久化客户 ID，并以 tenant、商机、联系人、客户复合外键阻止并发或旁路关系改写破坏该不变量；负责人变更受该次 `assign` 授权的数据范围限制，普通协作者可通过独立端点仅移除自己。查询、保存视图和金额汇总都要求相关字段可读且未遮罩，保存条件值在进入 SQL 前按字段类型校验和规范化，唯一自定义值由 MySQL 最终仲裁。结果支持 `OPEN`、`WON`、`LOST` 和 `INVALID`，结果备注的替换或清空均受字段写权限保护；完整更新请求中的 `nextContactTime: null` 会清空下次联系时间。结束后的商机仍可查询，但必须先显式重开才能修改普通资料。通用附件与受治理 FILE 字段已由 B24 接入；B25a 已在客户上下文提供七来源的权限感知业务概览，且不扩大任何来源域自身的可见性。

商机列表的静态排序使用固定服务端字段；也可通过 `customSortFieldCode` 选择当前目录中的活动、非内置、标量可排序字段。服务端先解析字段定义、确认未遮罩读取权限，再以已解析的定义 ID 排序；自定义与静态排序字段互斥，请求字段名绝不进入 SQL，数值字段按规范化数值而不是字符串排序。

B11 审批与阶段工作流接口位于 `/api/v1/approval-definitions`、`/api/v1/approvals`、`/api/v1/stage-workflows` 和 `/api/v1/stage-workflow-records`。审批定义使用 B05 活动且可搜索字段构造有界类型化条件，支持固定成员、活动角色和发起人自选审批人，以及顺签、会签、或签、重复审批和显式无人回退。公开提交和预览从所属业务聚合读取权威条件值，请求 `facts` 不能改写路由；阶段审批使用权威业务快照，并仅允许通过 B05 `WRITE` 校验的待保存值覆盖。发起页通过受对象、定义和选择项限制的候选人分页接口取得自选审批人，阶段运行页通过受运行记录、当前阶段和字段限制的候选接口取得审批人与人员、部门、关联对象值；两条提交路径都会在同一服务端路由解析中重新验证成员当前资格。运行记录下发闭合的 `allowedOutcomes`，以及每个 `writableField` 的专用输入合同：`USER`、`DEPARTMENT` 和已声明目标的 `RELATION` 只提交受限 ID，`ADDRESS` 使用结构化地址，`FILE` 必须先通过运行记录/字段限定的草稿入口取得草稿并提交精确文件 ID；提交时重新校验候选可见性、成员/部门活动状态、地址结构、草稿及文件证据。`/stage-workflows/configuration-options` 和其成员/部门候选接口在 `workflows:manage` 下提供可配置对象、当前字段 B05 写入/适用性、活动审批定义和候选目录，未声明关联目标等不支持字段给出稳定 blocker。实例保存定义版本、事实、管理员和已解析路由快照，后续定义变更不会改写运行实例的访问或取消权限。审批详情与列表只下发安全展示投影、`version`、时间线和服务器计算的 `allowedActions`，不暴露申请人、任务受理人、审批事实或业务对象元数据；动作、撤回和取消统一返回 `ApprovalActionResult`，批量动作返回带原子结果与稳定 `errCode` 的 DTO。待办计数、预览、可发起选项、受限候选人、转审候选和 `/authorization/me` 同样具有具名 OpenAPI 响应。审批详情、动作、批量、撤回、取消、分页和待办计数都会重新应用所属客户、联系人、线索、商机、合同或发票的当前数据范围；隐藏记录不会占用分页位置或计入总数。分页先扫描轻量根引用并只读取最终页的根摘要。公开幂等键统一裁剪后查询和保存，且不能使用阶段审批保留的 `stage:` 命名空间。动态审批人来源统一限制为每步 100 个唯一有效成员，规范化后的自选步骤键必须唯一。初始路由与转审历史共享 5000 条任务总上限，`ONCE` 拒绝转给前序已审批成员，仓储仅插入替代任务或更新实际变化的任务。审批动作通过根记录锁、任务版本和聚合乐观锁仲裁，批量审核按 ID 排序并在一个事务中原子执行。阶段启动在对象锁内重载当前配置，活动流程只能引用活动审批定义；阶段审批标题限制在 200 字符内，必填字段与审批条件字段并集在工作流及审批定义变更时统一限制为 100。流程适用性回显请求对象，并逐项返回 `canStart` 和稳定阻塞码。运行记录下发当前阶段、审批门及其自选项、`allowedActions`、`writableFieldCodes`、可渲染的 `writableFields` 和 `allowedTaskCodes`，并在写入时重复约束这些集合；阶段文件字段仅下发受控下载所需的最小文件投影；评论只下发 `canDelete`。所有集合元素在传输及应用边界失败关闭，所有定义、实例、任务、流程记录与评论都显式 tenant scoped。

阶段门被驳回、取消或撤回后，第一次 `advance` 只消费终态并恢复 `ACTIVE`，后续独立调用才重新捕获字段并增加审批次数。只要仍有 `ACTIVE` 或 `AWAITING_APPROVAL` 运行记录，流程就不能软删除，其字段与审批定义依赖会继续受到保护。

阶段 `FILE` 草稿还会持久化绑定到唯一的运行记录：上传意图、内容上传和状态查询分别使用具名的 `FileFieldUploadIntentResponse` 与 `FileFieldUploadedFileResponse`，推进只消费同一草稿、字段、运行记录内已完成且未过期的文件。流程配置候选 `candidateType` 是 `MEMBER`/`DEPARTMENT` 枚举，详情在允许变更状态时下发 `allowedStatuses`；ADDRESS 的可写字段以 `currentAddress` 下发结构化当前值，浏览器不解析内部编码的 `currentValues`。

B12 产品与产品分类接口位于 `/api/v1/products` 和 `/api/v1/product-categories`，商机产品位于 `/api/v1/opportunities/{opportunityId}/products`。产品目录由 tenant 共享，使用不带数据范围的 `products:read/write/delete/assign/export` 能力；分类树限制为 16 层和 5,000 个活动节点，目录写入经数据库守卫串行化，循环、同级规范重名、依赖删除和乐观锁冲突都会失败关闭。产品编码按 tenant 使用 NFKC 加小写的规范键唯一，产品支持 PRODUCT 自定义字段及字段策略，唯一自定义值由 MySQL 仲裁；删除仅写 `status=DELETED`。`PUT /api/v1/products/{productId}` 是完整替换，因此 `GET /api/v1/products/{productId}/editable-baseline` 在同时具备 `products:read` 与 `products:write` 时，只有所有替换字段都可读、未遮罩且可写才返回完整权威值；否则仅返回 `id`、`version` 与 `editable=false`，普通字段过滤后的产品详情不能充当替换基线。商机最多保存 100 个产品快照行，替换时要求商机可写、`products` 字段可写、目标产品可读且当前可售；名称、编码、分类、单位和目录价还必须按当前 PRODUCT 字段策略可读且未遮罩。目录守卫在事务首次一致性读之前取得，使产品与分类快照来自同一目录状态；产品根与自定义值、商机根与快照行的读取也在只读 `REPEATABLE_READ` 事务中组装。数量、成交单价、折扣率和小计由服务端校验或计算，因此目录后续修改、下架或删除不会改写历史商机。产品列表的静态排序使用固定服务端字段；也可通过 `customSortFieldCode` 选择当前目录中的活动、非内置、标量可排序字段。服务端先解析字段定义、确认未遮罩读取权限，再以已解析的定义 ID 排序；自定义与静态排序字段互斥，请求字段名绝不进入 SQL，数值字段按规范化数值而不是字符串排序。`POST /api/v1/products/export/csv` 还要求同次 `products:read + products:export`；它仅导出调用方显式选择、当前可读且未遮罩的标量字段，沿用关键词、分类、可用性、排序和保存/临时视图，在只读快照内先完成最多 5,000 行、50 字段、16 MiB 的 UTF-8 BOM CSV，再把一次性受控流交给 HTTP，不建立附件、异步任务或下载令牌。

B13 合同、开票申请和开票资料接口位于 `/api/v1/contracts`、`/api/v1/invoices` 与 `/api/v1/invoice-profiles`。合同与开票申请分别使用 `contracts:*`、`invoices:*` 能力和独立数据范围，并应用 CONTRACT/INVOICE 字段策略；HTTP 路由逐项要求读、写、删除、分配或开票能力，负责人目标还必须落在本次 `assign` 数据范围内。需要替换合同协作者时，`GET /api/v1/contracts/{contractId}/collaborators` 在 `contracts:assign` 的 owner scope 内返回权威基线：合同 `version`、完整协作者 `actorId/access` 清单，以及当前调用者的 `callerState`（`OWNER`、`READ`、`WRITE` 或 `NONE`）；因此空清单只表示确实没有协作者，不再混同于无查看范围。合同、开票资料、发票、回款和回款计划均提供版本化 `PATCH`，并使同一 `PUT` 遵循相同的安全部分更新语义：仅非 `null` 的提交字段会改变，省略或 `null` 的字段保留既有值，`version` 必填；客户端不会因详情输出的字段遮罩覆盖敏感值或历史关联。合同产品只接收产品 ID、数量和可选成交单价，目录展示值被快照，折扣、小计和整单总额由服务端计算；空产品集合无需产品读取能力。合同编号按 NFKC 加小写规范键唯一，并在规范化后再次限制长度；每行及汇总金额都按 `DECIMAL(22,2)` 边界校验。发票客户从持锁合同派生，数据库复合外键再次保证同客户；合同与可选商机、联系人也使用同客户复合外键，后续关系迁移不能破坏该约束，合同保存侧会把复合外键竞态转换为稳定领域错误。创建、改额和合同降额共用合同行锁，累计未删除发票不得超过合同总额，存在发票时合同也不能换客户。开票、重置和负责人变更响应分别复核客户与合同可见性。开票资料用 `status=ACTIVE/DELETED` 软删除且每次访问都在实际读取或写入事务内重新校验客户可见性；每客户最多 100 条活动资料，创建通过数据库守卫串行仲裁，查询也固定有界。空白查询词按未搜索处理。新租户 bootstrap 会同时 provision CONTRACT/INVOICE 内置字段，V38 负责历史租户回填。合同和发票均已接入 B11 权威事实解析及运行时可见性复核。

`POST /api/v1/contracts/summary` 复用合同列表的所有已验证筛选、保存视图、临时条件和合同数据范围，返回记录数、合同总额、已回款额与未回款额。该端点同时要求 `contracts:read` 与 `receivables:read`，并分别复核合同总额和回款金额的未遮罩字段权限；已回款额还会按调用者自己的回款数据范围再次收窄，浏览器不能据合同列表或任何本地金额推导回款事实。

合同和发票列表的静态排序使用固定服务端字段；也可通过 `customSortFieldCode` 选择当前目录中的活动、非内置、标量可排序字段。服务端先解析字段定义、确认未遮罩读取权限，再以已解析的定义 ID 排序；自定义与静态排序字段互斥，请求字段名绝不进入 SQL，整数和小数按规范化数值而不是字符串排序。

B14 回款与回款计划接口位于 `/api/v1/receivables` 和 `/api/v1/receivable-plans`，分别使用 `receivables:*`、`receivable-plans:*` 能力、独立数据范围和 RECEIVABLE/RECEIVABLE_PLAN 字段策略。回款客户从合同派生，回款计划也固定关联同一合同与客户；租户复合外键保持这个关系。编辑遵循 B13 的版本化部分更新契约：`PATCH` 与 `PUT` 只变更已提交的非 `null` 字段，省略或 `null` 保留既有值。新建和改额都先锁定合同，再对活动回款与计划金额求和，回款总额不得超过合同，关联计划时也不得超过计划金额。计划金额同样不得超过合同总额；已有回款历史的计划不能修改或删除。计划进度、已收和剩余金额按活动回款实时计算，完整核销的计划不会出现在可选计划列表。合同金额不得降到已收总额之下，且有回款或计划历史的合同不能删除。回款列表的静态排序使用固定服务端字段；也可通过 `customSortFieldCode` 选择当前目录中的活动、非内置、标量可排序字段。服务端先解析字段定义、确认未遮罩读取权限，再以已解析的定义 ID 排序；自定义与静态排序字段互斥，请求字段名绝不进入 SQL，整数和小数按规范化数值而不是字符串排序。回款 `POST /query` 以具名 `ReceivablePageResponse` 发布，记录响应可安全回显当前可见记录最新审批实例的可空闭合 `approvalStatus`，不含审批任务、人员或动作。`POST /api/v1/receivables/summary` 复用回款列表的全部已验证筛选、保存视图、临时条件和回款数据范围，只在 `amount` 字段当前可读且未遮罩时返回记录数与回款金额；浏览器不能由本地列表重算或扩大该金额。`POST /api/v1/receivable-plans/summary` 复用计划列表的关键词、负责人、客户、合同与计划数据范围，并在独立确认 `receivables:read` 和未遮罩回款 `amount` 后返回计划数量、计划总额、当前可见回款额和相应未回款额；回款范围更窄时，服务端不会用计划行、缓存或全局回款额补齐。`POST /api/v1/receivable-plans/export/csv` 还要求独立的 `receivable-plans:read + receivable-plans:export`：服务端将两种数据范围在同一固定 SQL 谓词中取交集，复用已验证的关键词、负责人、客户、合同、保存视图或临时条件，只输出显式选择且当前可读、未脱敏的固定计划字段。响应在 HTTP body 前同步生成最多 5,000 行、16 MiB 的 UTF-8 BOM CSV，并通过一次性受控流以 `no-store` 下载；不会创建附件、异步任务、下载令牌或跨对象导出器。回款已接入 B11 权威事实解析、运行时记录可见性与关系事实过滤；V40 为历史租户回填内置字段。

B15 跟进活动、回访与工作台接口分别位于 `/api/v1/activities`、`/api/v1/return-visits` 和 `/api/v1/worklist`。活动只能附着到当前 caller 具有独立数据范围的客户、联系人、线索、商机、合同、发票、回款或回款计划；每条附加关系同样重新验证。跟进方式与模板是 tenant 受治理配置，用户跟进按版本更新或软删除，受控写入生成不包含原始请求或敏感值的仅追加系统审计活动。活动完整更新的 `nextContactTime` 在 OpenAPI 3.1 中声明为 `string | null`，因此显式 `null` 会清除下次联系时间。回访采用独立 `return-visits:*` 数据范围与 `RETURN_VISIT` 字段策略，固定关联可见客户，可选联系人和合同还必须属于同一客户，并提供字段安全的简单选择接口。回访更新中未提交的 `contactId`、`contractId` 保留原关联，显式 `null` 才解除；OpenAPI 3.1 契约将两者声明为 `integer` 或 `null`。详情以 `VISIBLE`、`HIDDEN` 或 `EMPTY` 明示每个可选关联的状态，避免将不可见关联误当作空值。回访列表的静态排序使用固定服务端字段；也可通过 `customSortFieldCode` 选择当前目录中的活动、非内置、标量可排序字段。服务端先解析字段定义、确认未遮罩读取权限，再以已解析的定义 ID 排序；自定义与静态排序字段互斥，请求字段名绝不进入 SQL，整数和小数按规范化数值而不是字符串排序。回访 `POST /query` 以具名 `ReturnVisitPageResponse` 发布，记录响应可安全回显当前可见记录最新审批实例的可空闭合 `approvalStatus`，不含审批任务、人员或动作。合同、开票申请、回款和回访查询均支持二选一的成员保存视图或临时类型化条件：组合请求会被拒绝，条件会按当前字段权限、关联可见性和固定 SQL 映射复核后，才进入既有 tenant 与数据范围查询。工作台以九来源复合 cursor 稳定归并客户跟进、联系人、线索、商机、回访、回款计划提醒、合同到期、可见审批待办和当前责任人的到期 Work 任务；每次查询重新应用各来源能力、数据范围或专用参与谓词。单项或类别确认独立按 tenant/member 和源版本/到期时间保存，不修改业务记录，新版本或新日期会重新出现；类别确认无法在有界验证扫描内到达末页时失败关闭，不会写入部分结果。`v3` cursor 兼容读取旧 `v2` continuation，并在该旧分页链中把新增任务来源视为已耗尽，避免中途插入。该边界不调用外部日历或调度系统。

B16b 为客户、联系人、线索、商机、产品和回访列表提供统一 `keyword` 检索。关键字裁剪后最多 200 个 Unicode 码点，空白视为未搜索；候选字段由服务端固定目录与当前活动、可搜索、可读且未遮罩的字段策略求交，隐藏或任何遮罩字段均不会参与，交集为空时返回空页。文本按转义后的字面前缀匹配，产品编码使用 NFKC 加小写的规范键。查询继续受已验证 tenant、各自数据范围、活动/软删除状态约束，普通客户和联系人列表也始终排除公海记录。

B16c 把相同的字段感知 `keyword` 契约补齐到合同、开票申请、回款和回款计划列表。合同名称、开票申请号/抬头/发票号及回款编号使用字面前缀；合同编号使用 NFKC 加小写的规范键前缀；回款计划只按正整数期次精确匹配，无法解析的关键字返回空页。旧的回款方式前缀不再属于关键字候选，所有查询仍受各自 tenant、数据范围、状态和当前字段策略约束。

B17a 核心报表接口位于 `GET /api/v1/reports/core`。它返回当前调用者可见的商机漏斗（OPEN/WON/LOST 数量与预计金额）、有效合同数量与金额、以及活动回款数量与金额。路由要求 `reports:read`，应用层还会为每一种来源独立解析 `opportunities:read`、`contracts:read` 与 `receivables:read` 的当前数据范围；`estimated_amount`、`outcome`、`total_amount`、`status` 与 `amount` 任一被隐藏或脱敏时请求失败关闭。因此报表不会借由新能力扩大源记录、金额或分桶/筛选字段可见性。

B17b 固定趋势与负责人排名接口位于 `POST /api/v1/reports/trends` 和 `POST /api/v1/reports/rankings`。客户端只能选择 `WON_OPPORTUNITY`、`ACTIVE_CONTRACT` 或 `ACTIVE_RECEIVABLE`，并提交包含首尾的日期范围；趋势支持 DAY/WEEK/MONTH、最多 120 个桶和 10 年跨度，应用层按 ISO 周一或自然月补齐零桶；排名默认 Top 10、最多 100 条且跨度不超过 366 天，固定按金额、数量和 actor ID 排序。每次请求只解析所选来源的读取能力、当前数据范围及必需字段策略，字段隐藏或脱敏时失败关闭；三个来源在各自仓储内按成交时间、合同下单日期或回款日期聚合，不要求调用者同时拥有另外两个来源能力。

B17c 个人自定义报表位于 `/api/v1/reports/catalog` 与 `/api/v1/reports/definitions`。首期定义只允许 OPPORTUNITY、CONTRACT 或 RECEIVABLE 单一来源、一个日期字段和 DAY/WEEK/MONTH 粒度、一个可选的选择/布尔/成员/部门维度、1..8 个 COUNT/SUM 度量及最多 20 个类型化过滤条件；`RELATION` 不进入过滤目录且服务端拒绝持久化或执行，以免绕过关联对象的独立数据范围。定义按 `(tenant, creator member)` 私有，owner 或管理员也不能旁路；创建、替换和软删除要求新增的 `reports:write`，查询与执行要求 `reports:read`，每次还会重新解析来源读取能力、当前数据范围及所有引用字段的可读未脱敏状态。执行器只接受服务端字段描述符和参数值，在所属业务仓储中完成参数化 MySQL 聚合，SELECT 选项键按二进制精确比较、分组和排序；根记录使用来源的精确生命周期集合，内置时间字段保留原生 DATE/DATETIME 参数，自定义时间值使用规范 ISO 字符串，使受限区间能够使用 tenant/status/time 或字段值搜索索引。最多 120 个时间桶、10 年范围和 500 行，超限整次失败而不截断。活动定义会阻止其引用字段停用或关闭 searchable。

B18a 仪表盘偏好接口位于 `GET/PUT /api/v1/dashboard/preferences`。它按 `(tenant, member)` 保存默认时间范围、默认数据范围和封闭的模块显示偏好；未保存时返回 `CURRENT_MONTH`、`SELF` 与空 `hiddenModules`。相对时间支持当天、本周、本月、本季度和本年，`CUSTOM` 必须提供一个不超过 366 天的完整日期区间。数据范围只接受 `SELF`、`DEPARTMENT`、`DEPARTMENT_SUBTREE` 或 `TENANT`，仅用于进一步收紧仪表盘查询，不能授予或扩大任何源对象能力、数据范围或字段权限。当前模块目录只允许 `CUSTOMER_CONTACT_REMINDER`，隐藏它不会停止概览计算、改变提醒数据权限或暴露客户明细；恢复同样通过完整的版本化偏好更新。读取和写入都要求当前成员具有 `reports:read`。

B18b 固定销售仪表盘位于 `GET /api/v1/dashboard/overview`。它用当前成员的 B18a 偏好返回已赢商机、有效合同和活动回款三张固定 KPI/趋势卡片，以及同一范围、同一字段策略下的 Top 10 负责人排行；相对时间在一次请求中按 UTC 解析，跨度不超过 31 天按日、32–120 天按 ISO 周、121–366 天按月补齐零桶。`reports:read` 只允许进入页面；每张卡片和每个排行仍独立解析来源读取能力、真实数据范围和必需的未脱敏字段，再把偏好范围作为上限取交集。来源或字段不可用时只返回不含数量、金额和趋势/行的 `UNAVAILABLE` 投影，其他来源继续展示；未知基础设施失败仍使整个请求失败。三种聚合继续复用 B17b 的来源仓储和日期索引，并与字段策略读取共享一个只读 `REPEATABLE_READ` 快照。

同一概览还携带固定 `salesFunnel` 投影。它先独立确认 `opportunity-pipelines:read`、`opportunities:read`、商机有效数据范围以及创建时间、状态、流程、阶段和预计金额的未遮罩字段访问，再把 B18a 偏好作为只收窄的上限，在同一时间窗按活动流程/阶段聚合可见商机。响应始终按服务端活动目录补齐零桶；已知访问拒绝只返回无流程和数值的 `UNAVAILABLE`，没有活动阶段返回 `EMPTY`，未知故障不降级为正常结果。流程选择和金额/数量显示是客户端本地呈现，不能改变此聚合的权限、范围或窗口。

同一概览的 `customerContactReminder` 是固定六档累计客户联系投影：`OVER_7_DAYS`、`OVER_15_DAYS`、`OVER_30_DAYS`、`OVER_3_MONTHS`、`OVER_6_MONTHS` 与 `OVERDUE`。它在同一只读快照中重新确认 `customers:read`、`activities:read`、当前成员资格、客户实际范围和客户创建时间/状态的未遮罩读取策略，再将 B18a 偏好作为只收窄的上限。前五档使用可见活动客户的最新有效 `CUSTOMER/FOLLOW_UP` 发生时间；没有有效跟进时回退客户创建时间。逾期档只查看最新有效跟进的 `next_contact_time`，旧计划不会因后来跟进而残留。任一已知权限或字段拒绝返回不含档位和值的 `UNAVAILABLE`，浏览器不得以活动缓存、客户列表或本地时钟补造计数。

仪表盘合同目标管理位于 `/api/v1/dashboard/performance-targets`，成员目标管理单月 `ACTIVE_CONTRACT` 金额，按 `(tenant, member, metric, year, month)` 唯一并使用乐观锁版本更新或删除。查询要求 `dashboard-targets:read`，写入要求 `dashboard-targets:write`，管理目标成员必须仍处于活动状态；同一读取能力还会使服务端导航目录返回管理菜单 `/administration/performance-targets`。独立的 `/api/v1/dashboard/department-performance-targets` 为活动部门保留年度、季度、月度显式值，并可通过 `/batch` 在一个事务中创建或以每个周期的服务端 version 替换 1–17 条值；同一请求任一基线过期、周期重复或并发唯一冲突都会整体回滚，不会把部分批次伪装为成功。`GET /api/v1/dashboard/overview` 同时携带同一时间窗口的成员 `target` 投影：仅当调用者具有目标读取能力、保存的数据范围为 `SELF` 且有效合同卡片可用时，才按窗口覆盖的自然月求和配置目标，并以同一张合同卡片金额计算完成率；缺少任一月份以外的目标不会补零，窗口内完全未配置时返回 `EMPTY`。部门、部门树和租户范围一律返回不含金额的 `UNAVAILABLE`，不会把成员目标错误地聚合为组织目标。

部门合同目标使用独立接口 `/api/v1/dashboard/department-performance-targets`，保存活动部门的年度、季度或月度 `ACTIVE_CONTRACT` 金额；按 `(tenant, department, metric, year, period, periodNumber)` 唯一，周期分别严格限定为 `YEAR/1`、`QUARTER/1..4` 和 `MONTH/1..12`。它同样要求当前成员和 `dashboard-targets:read/write`，迁移以 `(tenant, department)` 外键阻止跨租户引用，且每次查询、修改或删除都会重新确认部门仍为活动状态。年度、季度、月度都是明确配置值，不会由成员目标相加产生，也尚未被伪装成仪表盘实际业绩或完成率。

成员和部门目标都支持独立的原子导入命令：分别为 `POST /api/v1/dashboard/performance-targets/import` 与 `POST /api/v1/dashboard/department-performance-targets/import`。浏览器必须先在本地把受限 CSV 解码为 1–200 条生成类型目标行；服务端不接收、不解析或持久化原始文件、文件名或任意附件字节。导入仅接受 `ACTIVE_CONTRACT`，逐行重新验证当前写入权限、租户、活动成员/部门、年份、周期和金额，按确定顺序锁定已有自然键后创建或替换金额；没有客户端 version 基线，任一行无效或并发唯一冲突都会使整份导入回滚。完成响应只返回导入行数，读取方仍通过普通目标查询刷新事实。

B19a 营销表单配置接口位于 `/api/v1/marketing-forms`，分别使用 `marketing-forms:read` 与 `marketing-forms:write`；每个表单在创建时显式固定为 `LEAD` 或 `CUSTOMER`，字段配置直接引用对应的 B05 当前字段目录，不复制易失效的字段类型和选项。激活要求负责人仍是有效成员，并通过目标对象的 `leads:write` 或 `customers:write` 数据范围和字段写权限校验；每个当前必填的公开安全标量字段都必须以必填方式公开，活动表单依赖的字段不能被停用。匿名渲染和提交位于 `/api/v1/open/marketing-forms/{publicId}`，公开标识只用于定位持久化的 tenant 与表单，不是身份凭据；提交体使用严格白名单，tenant、负责人、状态、未知顶层属性或未配置字段都会被拒绝。每次渲染和提交都会重新校验负责人及 B05 字段策略；提交锁定表单根记录，并在同一外层事务中执行对应目标的复用创建操作，因此更新或停用与在途提交按数据库顺序完成，不会在停用完成后再由旧快照落库，也不会因嵌套 `REQUIRES_NEW` 消耗第二条数据库连接。`LEAD` 保留受控 FILE 草稿提交，`CUSTOMER` 当前仅接受标量字段；失败统一属于 `MARKETING_FORM_SUBMIT`（6907）错误场景。匿名 POST 以 `(tenant, form, idempotencyKey)` 和请求摘要安全重放相同提交，冲突复用稳定失败，并在进入应用前先解码、解析并重建规范 public ID，使直接路径、`/submissions` 别名及等价编码变体共享同一限流桶，然后同时消耗有界的每表单和每来源分钟配额，默认分别为 60 和 20；成功只回显最小的提交确认，不泄露内部对象、负责人或版本标识。内置限流为单实例内存态，多副本的全局配额仍应由网关或共享限流服务执行。

B19b 为每次成功公开提交在同一事务内追加最小归因台账，只保存 tenant、表单及其实际版本、目标对象类型和记录 ID，以及由 MySQL `UTC_TIMESTAMP(6)` 生成并回读的提交时间；台账失败会连同目标对象一起回滚，失败请求、原始字段值、IP、请求头和 UTM 均不落库。`POST /api/v1/marketing-forms/{formId}/submissions/trends` 使用 `marketing-forms:read` 返回 DAY/WEEK/MONTH 成功量和零桶，最多 120 桶且跨度不超过 10 年；`POST .../submissions/query` 按表单目标额外要求 `leads:read` 或 `customers:read`，并在 SQL 分页和计数前应用当前 caller 对应的范围与协作者可见性。趋势保留历史成功事实；软删除或当前不可见的目标对象只会收窄链接分页。INACTIVE/DELETED 表单仍可查询既有历史，公开成功响应保持不含内部 ID。

B20a OA 表单配置位于 `/api/v1/oa/forms`，使用 `oa-forms:read/write` 管理 tenant 内版本化的 `INACTIVE`、`ACTIVE`、`DELETED` 表单；可提交目录还要求 `oa-requests:submit`。表单最多引用 100 个当前 `OA_REQUEST` 字段，只保存字段 code、顺序和必填标记，读取时重新解析 B05 元数据与角色字段访问；隐藏字段不输出，普通成员只有在审批发起范围允许且每个输入字段都具有 `WRITE` 时才看到活动表单。`/configuration-options` 只返回当前可写、非服务端派生、且非 `RELATION` 的输入字段和兼容审批；OA 尚无受治理归属模块的关系值合同，因而拒绝非空 `RELATION`。`request_title` 固定必填，`form_code`、`applicant_actor`、`applicant_department` 和 `submitted_time` 由服务端派生；B24c 已以独立 OA FILE 草稿和有序事实接入 `FILE`，文件不进入标量 `values/facts`，只有已 `CLEAN` 候选能在 OA 提交事务中冻结。激活要求绑定同 tenant、同对象且活动的 B11 审批定义，审批条件只能引用表单输入与服务端事实；活动表单会阻止引用字段停用、关闭 searchable 以及审批定义停用或不兼容更新。所有关联写入共享 `(tenant, OA_REQUEST)` 数据库互斥锁，规范 code 唯一键和 200 条未删除表单上限在并发下仍由 MySQL 仲裁。V52 为旧 tenant 回填五个内置字段，新 tenant 则在组织初始化时幂等 provision。

B20b/B29d OA 申请运行时位于 `/api/v1/oa/requests`，使用 `oa-requests:submit` 预览并一次性提交不可变申请，使用 `oa-requests:read` 进入参与申请的列表与详情；`oa-requests:manage` 只扩大到 tenant 内申请读取，不替代 B11 审批、撤回或取消能力。预览和提交会与可提交表单目录使用同一服务端资格规则：表单活动、绑定审批活动、申请人范围允许、且每个输入字段当前可写，任一变化均失败关闭；`USER`、`DEPARTMENT` 和发起人自选审批步骤通过表单作用域候选分页接口取得安全展示值，提交时仍重验活动状态。提交事务锁定当前 tenant、活动表单及审批配置，重新解析当前 B05 `WRITE` 权限和服务端申请人/部门/时间事实，先持久化权威申请，再在同一数据库连接中创建以 `oa:<requestId>` 标识的 B11 审批实例；任一步失败都会同时回滚申请、唯一值和审批。客户端 `(tenant, applicant, key)` 幂等键支持相同规范提交的并发重试，改变表单、版本或字段值则稳定冲突。申请列表在 SQL 分页与计数前应用申请人、历史审批人、管理人、抄送人或 OA 管理员的并集可见性，输出和 B11 OA 快照都重新应用当前字段隐藏/脱敏策略；审批实例仍是唯一运行状态来源。B29d 通过 `/api/v1/attachment-targets/oa-request/{requestId}/attachments` 提供独立普通附件：读取仍需 `files:read` 与当前申请可见性；上传、状态和撤回还需 `files:write`、`oa-requests:collaborate`，且只能由申请人、当前未完成审批任务的 assignee 或提交时固化的实例管理人在 `PENDING` 期间执行。协作附件不会进入提交 facts、FILE 字段、digest 或审批快照。审批 action、批量 action、撤回和取消在锁定实例后锁定申请，并在终态提交前关闭全部非 `AVAILABLE` 关系、登记 B24 清理；剩余可用附件继续按申请可见性下载，终态后不允许新建、上传或撤回，也不会由旧 worker 发布。V54 独立创建申请表及 tenant 复合完整性约束，V84 增加协作附件 typed parent；B15 继续复用既有 `APPROVAL_PENDING` 来源，未引入 B21 Work 任务表、API 或能力。

B20c/B28a/B29c 内部公告位于 `/api/v1/oa/announcements`。`oa-announcements:write` 管理版本化的 `DRAFT`、`SCHEDULED`、`PUBLISHED`、`WITHDRAWN`、`DELETED` 公告，`oa-announcements:read` 只读取发布时确实投递给当前活动成员的公告并写入自己的已读回执。受众只接受全 tenant、部门子树和显式成员三类严格选择器；发布事务先锁定组织 tenant，再锁定公告根，从活动同 tenant 成员解析、去重并固化最多 10,000 名接收人，超限整体失败。`POST /{id}/schedule` 只接收版本和带 offset 的未来发布时间，创建公告自有的一次性 durable occurrence；`POST /{id}/cancel-schedule`、重新排期、删除和立即发布都在相同锁序下结算旧 generation。到期 worker 重新验证原排期成员的当前成员资格和写权限，再验证当前受众；失败会回到可编辑草稿并只保存固定安全原因。B29c 只允许草稿管理者通过 `/api/v1/attachment-targets/oa-announcement/{announcementId}/attachments` 变更公告附件；排期或发布冻结最多 100 个 `AVAILABLE` 附件，已发布接收人须同时具有 `files:read`、`oa-announcements:read`、当前成员资格和 immutable recipient snapshot 才能下载。后续加入或调动不会扩大历史受众，失活成员、撤回或删除公告都会从普通查询和未读统计中消失。标题与最多 20,000 Unicode 码点的正文按纯文本保存，不提供 HTML/Markdown 渲染；B28 EMAIL 仅发送公告 ID、标题和实际发布时间，绝不携带正文、附件或 URL。已读使用成员与公告共享锁协调失活/撤回，并按唯一键原子保留服务端首次 UTC 时间，不会串行化同一公告的全部接收人。V55 用 tenant 复合外键连接公告、选择器、不可变接收人快照和回执，V57 移除冗余删除时间并保持 `status=DELETED` 为唯一软删除表示，V74 增加 B28a 排期 generation 与 publication occurrence，V83 增加 B29c 公告附件父证明。

V75 进一步将 OA publication occurrence 的安全原因与生命周期状态绑定：`PUBLISHED` 必为无原因，`RETRY_WAIT` 仅为 `RETRY`，取消和终止状态只允许各自的固定原因。

B21 Work 任务接口位于 `/api/v1/work/tasks`，使用不带 CRM 数据范围的 `work-tasks:read/write/manage` 能力。每个版本化任务只有一个当前责任人、最多 50 名关注人、一个可选 CRM 业务关系、可选 UTC 截止时间、固定优先级和 `TODO`、`IN_PROGRESS`、`DONE`、`CANCELLED`、`DELETED` 状态机。创建者或同时具有 write/manage 的任务管理员可编辑、改派、替换关注人、取消和受限软删除；当前责任人在具有 write 时可推进状态。关注人只扩大任务读取与纯文本评论资格，不获得任务执行或结构管理权。普通 read 只读取当前活动成员作为创建者、责任人或关注人参与的任务，read+manage 才扩大到 tenant 内任务；所有参与、活动成员、tenant、关系和固定筛选谓词均在 SQL 计数与分页前生效。

评论作为独立的版本化分页实体保存，最多 5,000 个 Unicode 码点并按 Unicode 空白裁剪，只有当前活动作者可以软删除；即使评论已删除，其历史仍会阻止把任务作为无评论误建记录删除。CRM 关系只允许 CUSTOMER、CONTACT、LEAD、OPPORTUNITY、CONTRACT、INVOICE、RECEIVABLE 或 RECEIVABLE_PLAN：建立或替换非空关系时重新校验显式新目标的所属域写能力、数据范围和 tenant；清除或覆盖不会探测不可读的旧关系，因此隐藏关系与无关系不会产生不同的授权结果。按关系查询会在同一个只读快照内先复核关系可读性再计数和分页，输出也会重新校验读范围并在失权后省略关系 ID。CRM 可见性不会授予任务参与权，反向也不会扩大 CRM 权限。V53 保存任务根并提供工作台查询索引，V56 新增关注人、评论、成对关系检查约束和查询索引。`WORK_TASK` 工作台来源同时要求 `worklist:read` 与 `work-tasks:read`，只在 SQL 中投影当前活动责任人的未结束且当天到期任务；指纹使用专用投影版本和截止时间。关注人、评论、关系与说明变更不推进投影版本，标题、优先级、改派、截止时间或状态变化才会形成新提醒；这些协作操作不创建 Activity，确认也不修改任务状态。

B22a Connector 安全与控制平面位于 `/api/v1/connectors`，以 `connectors:read/manage` 分离人工查询和管理。列表、详情、撤销和状态变更都返回不含凭据的 `ConnectorResponse`，列表再以具名 `ConnectorPageResponse` 分页；只有创建与轮换返回 `{ connector, credential }` 的 `ConnectorCredentialCreatedResponse`。connector、key ID 与 256-bit secret 全由服务端生成，完整 `connectorRef.keyId.secret` 只在创建或轮换时返回一次，数据库只保存 SHA-256 verifier；每个 connector 最多同时保留两个活动 key。`ACTIVE`、可恢复的 `DISABLED` 与不可逆 `RETIRED` 生命周期按乐观锁更新，撤销、停用和退役在事务提交后立即清除本机已验证凭据状态，退役同时撤销全部 key。V58 以全局唯一 opaque `connector_ref`、tenant 复合外键和永久凭据子记录固化不可迁移的 tenant/ref 身份。

独立非人工入口 `/api/v1/connector-ingress/probe` 只接受 connector bearer。传输认证按完整 `(connectorRef, keyId)` 做 verifier 精确查询并仅构造无权限的 `ConnectorPrincipal`；认证服务同时为本次认证签发不可由普通进程内 bean 构造的 evidence，应用 `ConnectorGuard` 验证 evidence 后再用独立、无 verifier 的精确投影解析当前活动 tenant/connector。普通 caller、trusted headers、请求 body 和 connector principal 不能互换或提供 tenant/actor/authority。来源、pending credential 与已验证 connector 限流桶各自有界，同一 connector 的轮换 key 共享分钟配额，未知 credential 不创建 connector 分区；credential 或 identity 存储故障都返回可重试不可用且不计作 secret 失败。未来 ingress 写入的 registration 锁只能在活动事务内取得。B22a 只交付身份与控制平面以及该锁契约；呼叫事实、inbox/outbox、interaction、媒体和真实 provider adapter 仍属于后续 B22 子批次。

B22b 已加入可复跑的真实 Asterisk 20.15.2 ARI/Stasis transport canary：隔离 Docker 网络中两枚 SIPp 软件端点和随机一次性 ARI 密码会实际建立 ARI WebSocket、originate 两条 endpoint channel、验证两枚 `StasisStart`，并将它们加入同一 mixing bridge 后复核 bridge 成员与 `ChannelEnteredBridge` 事件；原生 ID/event 只存在于清理的临时目录。bridge 关系不被误作双向 RTP 媒体证明。它产出 mode-`0600` 的无敏感 manifest，并明确记录 `PBX_REAL=BLOCKED_RUNTIME_CONFORMANCE`、`LINE_REAL=BLOCKED_EXTERNAL`；尚无 fenced Stasis owner、durable journal/ledger、两腿媒体证明、`AMBIGUOUS` 对账、录音/删除和 CP/PC/PD/PM runtime evidence，因此不能触发 B22-SPLIT 或生产启用。详见 [B22b 验收记录](docs/acceptance/B22b-asterisk-transport-canary.md)。

B22c 新增 V82 的 core reliability schema：call interaction、人工 request binding、start outbox、fact inbox 与 observation gap 均以 tenant/connector 复合外键和唯一键约束，且只保存 opaque identity/digest/state/lease metadata，不保存号码、原始 PBX frame、录音或 provider 内容。私有 `CallFactInboxService` 只接收两个固定 32-byte opaque 值，先在命令事务内锁定已验证 connector，再以 MySQL 唯一键仲裁 `APPLIED`/`DUPLICATE`/安全冲突；它没有公开路由或 provider 输入。`scripts/b22-core-inbox-conformance.sh` 会产出 owner-only、无敏感值 manifest；这条 core lane 仅为 `DURABLE_SIMULATED`，B22 整体仍为 `CONTRACT_CLOSED`，不开放呼叫 API 或 adapter。详见 [B22c 验收记录](docs/acceptance/B22c-runtime-reliability-foundation.md)。

B22d 以 `B22PrivateAdapterDurabilityConformanceTest` 实现 CP/PC/PD/PM 的私有 durable oracle：固定合成数据经重启后验证 Stasis owner fence/journal、双 distinct-leg proof、`DISPATCHING`→`AMBIGUOUS` 且不重复 start、connector 分区的 AES-GCM/HMAC、deadline 删除，以及 full/single-range/cancel/confirmed-delete。`scripts/b22-private-adapter-durable-conformance.sh` 只生成 `0600`、无敏感值的 `DURABLE_SIMULATED` manifest；它不是 Asterisk adapter，也不把内存/测试存储冒充 PBX 或外部线路，因此仍明确 `PBX_REAL=BLOCKED_RUNTIME_CONFORMANCE`、`LINE_REAL=BLOCKED_EXTERNAL`，不能启用 B22-SPLIT 或生产。详见 [B22d 验收记录](docs/acceptance/B22d-private-adapter-durable-oracle.md)。

B22e 的 `scripts/b22-conformance-matrix.sh` 只接受上述 private durable、MySQL core inbox 和独立 Asterisk transport 三份已存在的 `0600` 报告，逐份校验固定 profile/证据等级后以输入文件 digest 组合成新的无敏感值 manifest。它将 transport 的 `CONTRACT_CLOSED` 与两条 `DURABLE_SIMULATED` lane 清晰分层，固定 `productionReady=false`、`PBX_REAL=BLOCKED_RUNTIME_CONFORMANCE` 与 `LINE_REAL=BLOCKED_EXTERNAL`；绝不会用内部 SIPp、桥接或测试 oracle 冒充真实 PBX/线路通过。详见 [B22e 验收记录](docs/acceptance/B22e-evidence-matrix.md)。

B23a 企业数据查询接口位于 `/api/v1/enterprise-data`，使用独立的 `enterprise-data:read` 能力。它只提供 tenant 绑定的能力探测、1..200 Unicode 码点的登记名称单页查询，以及 USCC、LEI 与 Companies House number 的 scheme-specific 精确查询；候选最多 20 条且不接受供应商 filter、分页 token、账号、connector 或任意参数 map。规范输出对齐 B23-XP：必需登记身份、名称、主体状态和 `sourceDeclaredPartial`，可选法律辖区、五类 formation date、单一注册地址及受控 observation metadata，不包含 organization/legal-form 类型。adapter 只声明技术 capability 和规范事实；独立部署策略目录提供来源、正 TTL 与分上下文 attribution，核心取最严交集并把 B23a 硬性封顶为 `EPHEMERAL_ONLY`。外部调用在数据库查询事务之前完成，默认生产适配器明确不可用；dev/test 可使用只含虚构组织的合成适配器。B23-XF 进一步以同进程双 tenant 的开放/商业 native shape fixture 联合验证 identity/standing、未知状态、policy/TTL/attribution、候选与来源额度、安全错误和分区隔离。B23-OC 已在 `infra/external` test source 建立可继承的官方/开放源离线契约和纯内存参考执行器；B23-O-L1 又用 stub transport 与合成 JSON:API-like LEI payload 实现首条 GLEIF mapper lane，验证五类组织资格、LEI 状态正交、字段剥离及稳定错误。B23-RA 在同一 test scope 中把 GLEIF RA List 1.8.1 的 `RA000585/586/587` 与 RA-scoped Companies House 号码类别交叉校验，仅在 EntityID 同时通过既有 `GB_CH_NUMBER` validator 时生成 standing 为 `UNKNOWN` 的本地第二身份；未知 RA、缺失或错误号码类别只省略第二身份，主 LEI identity mismatch 仍使整次操作安全失败。B23-O/2 冻结未来 Companies House/GLEIF 真实适配器的组织资格、三条 Companies House RA 映射、来源权利、共享与 tenant 配额、五分钟 readiness/observation TTL、零内联重试和三层 conformance 证据；B23-CH 已加入 `UK_COMPANIES_HOUSE/GB_CH_NUMBER`、显式前缀目录及 API/VO 投影，但没有加入真实 Companies House adapter、credential、生产 policy 或 readiness evidence。B23-O-L1/B23-RA 仍只完成第一层 test-scope mapper，不含真实 HTTP、endpoint、credential、生产 Bean、policy/admission/build/live evidence；B23-O-L2 又以双 tenant、双 replica、文件持久化共享 admission/readiness store 和虚拟时钟执行 `O2-06/07/08/10`，证明公平 quota、exact/control reserve、零重试、短 TTL、立即撤销、漂移隔离和 replica 重建恢复。这些离线层都不表示真实 source 已 ready。B23a 不缓存或持久化候选，不写 Customer，不实现择取、身份占用、人员/关系/风险数据或真实供应商网络接入；B23-XF、B23-OC、B23-O-L1、B23-RA、B23-O-L2、B23-CH 与 B23-O/2 都不改变生产失败关闭边界。

B23-O-L3 继续以不打开 socket 的 scripted transport 执行 `O2-09/11`，覆盖 HTTP/DNS/TLS/redirect/content-type/body/schema/identity failure、按 operation 持久化的 snapshot 影响、13 类敏感 sink inventory 和无真实值的机器 manifest；它与 B23-O-L1/B23-RA/L2 一样只提供 test-scope 离线证据，不能替代 B23d 的私有 live `O2-12` 子项或完整生产证据。

企业数据 OpenAPI 对外使用具名安全响应：能力探测为 `EnterpriseDataCapabilityResponse`，名称查询数组和精确查询为 `EnterpriseDataObservationResponse`，Customer 采用预览/确认分别为 `EnterpriseDataAdoptionPreviewResponse` 与 `EnterpriseDataAdoptionResponse`。这些契约只投影已治理的企业事实和受控 attribution，不包含原始 provider payload、绑定、账号、凭据或任意扩展 map。

B23d 现有默认关闭的 GLEIF Level-1 HTTPS runtime：只接受固定官方 HTTPS base URI、固定 allowlist JSON:API 字段和五类组织 category，按 pinned RA catalog/digest 受限映射 Companies House 次身份，并把 HTTP/DNS/TLS/限额等缩减为稳定安全错误。`V79__create_enterprise_data_source_quota.sql` 在 MySQL 中为同一 provider 建立无查询历史的跨实例额度仲裁；来源 capability 还要求 tenant/region allowlist、版本化 rights/attribution/TTL/purpose policy、审批引用、当前 build 的 owner-only conformance report 与完整 `O2-01..12`。独立 `B23GleifLiveCanaryRunner` 已以真实公开 API 完成仅内存的 `O2-12` 子项；`scripts/b23-gleif-live-canary.sh` 强制显式 `--allow-live-canary`，只输出 0600、无真实值的临时报告。报告固定为 `CANARY_LIVE_ONLY` 和 `CANARY_LOCAL_ONLY`，因此 runtime 必定拒绝它；生产默认仍 `NOT_READY`，当前政策仅 `EPHEMERAL_ONLY`，不能用于 Customer adoption。详见 [B23d 验收记录](docs/acceptance/B23d-gleif-runtime-canary.md)。

B23e 实现了既有 Customer 的短期 adoption preview 与重新 exact lookup 命令：客户端不提交来源值、tenant 或 source binding；写入前重查、比较选择事实、锁定 Customer/customization，并在同一事务中保存 Customer、当前登记身份、值无关 provenance 与保留任务。`V80/V81` 用 tenant-scoped MySQL 唯一键仲裁登记身份，并覆盖并发、回滚、软删除释放、双 tenant 和物理删除；后台任务先租约一个精确 tenant，才做该 tenant 的保留清理。该能力仍独立失败关闭，只有未来经过权利审查的 `ADOPT_SELECTED_FACTS` source policy 才能启用。Companies House 已有默认关闭的官方 HTTP adapter 与 API key/数据权利/隐私/版权 policy gate；尚无独立 live conformance evidence，因此始终 `NOT_READY`。详见 [B23e 验收记录](docs/acceptance/B23e-customer-adoption.md)。

B24a 已实现 Work Task 的安全文件内核：`AttachmentFile` 的 bytes/inspection/publication 三轴状态、不可改绑的一文件一目标关系、target-first intent/streaming upload/status/list/download/withdraw API、`files:read/write` 交集授权，以及 `V59__create_attachment_foundation.sql` 的 file/relation/content/job/read-lease/tenant-usage 六类事实表和跨租户 global-reserve 仲裁行。B24b 在不复制该内核的前提下接入 Customer、Contact、Lead、Opportunity、Contract、Invoice、Receivable 与 ReceivablePlan：`V60__add_crm_attachment_targets.sql` 以八个 typed tenant FK、覆盖索引和 nine-way exact-one CHECK 扩展中心关系，`V61__add_attachment_paused_job_state.sql` 增加 durable pool defer；九个显式 target adapter 全量注册且缺项启动失败；各 CRM 根复用自己的读/写 scope 与根锁。B24c 通过 `V62__create_file_field_and_oa_file_binding.sql` 增加受治理、最多十个且有序的 FILE 值、创建前 CRM/OA draft、候选上传/状态 API，以及 OA 提交与 approval/stage capture 时的原子证据快照；`V63__repair_file_field_draft_withdrawal_and_snapshot_headers.sql` 保留撤回候选的空 evidence 并为审批/stage 空快照建立不可变 header，`V64__cascade_file_snapshot_headers_with_parent_cleanup.sql` 让 header 随唯一父根清理。FILE 值不进入标量 `customFields`/`facts`，当前值、OA 值和 workflow snapshot 的输出及内容下载都要求 `files:read`、业务可见性和未遮罩字段 READ 的交集。撤回、替换与父记录删除会立即关闭候选/旧值并排入精确清理。详见 [B24a 验收记录](docs/acceptance/B24a.md)、[B24b 验收记录](docs/acceptance/B24b.md) 与 [B24c 验收记录](docs/acceptance/B24c.md)。

CRM CSV 导入草稿与错误报告遵守七天保留边界：未确认草稿到期后由受限附件 lane 重新锁定根与私有来源，标记 `EXPIRED`、撤回关系并交由既有精确删除任务回收原始 bytes；只保留无源值、无文件名的生命周期审计元数据。已完成导入历史也只在该七天窗口内返回。

B24-I 已冻结内容检查边界：受控流式引擎与 exact-version 对象事件引擎分别保留 pinned/managed definitions 证据，经同一核心规则归一为 `CLEAN`、`REJECTED` 或 `INDETERMINATE`；类型检测、恶意内容 verdict、完整性与发布 policy 保持正交。未知格式、声明/检测冲突、scanner/container 超限、定义过期、对象版本不符或检查器故障均失败关闭，quarantine 与深度 container detector 必须在存储、IAM 和进程层隔离。该契约本身只提供双 profile conformance oracle，不选择 ClamAV、GuardDuty、Tika、对象存储或生产数值；B05 `FILE` 后由 B24c 在不改写该契约的前提下独立实现。

B24-A 固定 `files:read/write` 与 Work Task 或八类 CRM target 当前权限的交集、不可改绑的一 file 一 target、typed same-tenant parent 证明和父根锁序；隐藏目标、跨 tenant 和 target/file mismatch 使用同一安全失败，OA 与 B05 `FILE` 继续在首期通用附件之外。

市场活动作为同一受控附件 target 额外提供 `COVER` 与 `DETAIL` 两个关系级图片分组：仅活动目标可声明、仅 PNG/JPEG、每组在活动根锁下共同限制上传中和已发布关系至 9 条；分组查询、二进制传输、检查发布、下载和版本化撤回仍复用 B24 生命周期，不产生公开存储地址。

市场活动创建现在可在 `marketingFormId` 之外选择一次性嵌入 `LEAD` 或 `CUSTOMER` 公开采集表单：两种输入互斥，嵌入表单需要 `marketing-forms:write`，表单目标必须与活动关联对象相同，并与活动在同一事务创建和关联，任一方校验或写入失败都会一起回滚。创建出的表单保持 INACTIVE；活动更新明确拒绝该嵌入字段，避免表单配置被静默忽略。`CONTACT` 活动不能关联公开表单，尚未实现任意自定义对象的表单提交协议。

市场活动编辑器通过 `GET /api/v1/marketing-activities/configuration-options` 读取受 `marketing-activities:read` 保护的活动类型及关联对象能力目录。目录明确声明每个关联对象的显示名及是否支持公开采集表单，并以 `defaultRelatedObjectType` 发布新建活动的默认对象；浏览器不能自行扩展对象代码、推断默认或可创建表单的对象，或在该目录不可用时继续写入关联对象。新建或改用活动类型时仅接受稳定的服务端代码；迁移前已持久化的旧类型仍可原样回显并保存，不能被浏览器借此写入新的非目录类型。

市场活动可通过 `POST /api/v1/marketing-activities/batch-delete` 原子删除 1–100 个唯一的 `activityId + version` 目标。命令先在同一事务中锁定、读取并校验整批活动的当前版本，只有全部通过才软删除活动及撤回其受控附件；任一目标不存在、无权限或版本冲突时不留下部分删除。

B24-X 把上述边界组合成 `X-01..X-08`：双 tenant intent、storage/MySQL crash、streamed/managed 检查、目标根锁发布、full/native single range、失权与父生命周期、durable delete 和 authoritative absence。`SafeFileComposedFixtureTest` 已在 test source 执行 `XS/XM/XL` 第一层 model、关键进程重建 cuts、Range 表和 safe report；真实 MySQL durable composition 与 current adapter build 仍未实现。合成 storage/scanner 不能自报 expected state 或产生 production readiness。详见 [B24-X](docs/B24-X-composed-fixture.md)与[B24-X/1 model fixture](docs/B24-X-model-fixture.md)。

B24-SPLIT 已正式固定 `AttachmentFile`/`AttachmentTargetType`、target-first `/api/v1/attachment-targets/{targetType}/{targetId}/attachments`、`V59/V60/V61` 表职责、六类 durable worker、`atom.files.*` 配置面与每批验收 gate。首条实现 profile 为 secure-local + bounded magic + streamed engine，公共 allowlist 仅 PDF/PNG/JPEG且有效上限 16 MiB；secure-local byte store 还会在端口边界拒绝未证明长度和超过 64 MiB 的对象，避免绕过 admission 的直接调用扩大实际写入上限。`ClamdInstreamStreamingInspectionEngine` 只接受 Unix-domain socket；生产不会接受原生 TCP scanner。部署还必须提供 owner-only、runner 生成的 JSON `conformance-report`，其 build/binding/root/policy/definition/完整场景集合逐项匹配后才可 ready。独立 runner 的 loopback TCP 模式仅用于本地合成 canary，报告固定为 `CANARY_LOOPBACK_ONLY`，运行时必定拒绝；Linux 部署 mode 必须显式给出单一 `--clamd-unix-socket`，才会使用当前 build digest。`scripts/b24-linux-unix-socket-canary.sh` 已以不可变 ClamAV/JDK OCI digest 在一次性 Linux Docker volume/socket 拓扑实测这一无网络 runner 路径，并以生成的零字节流验证 1 byte/64 MiB、64 KiB buffer ceiling、错误长度、超限、超时与中断清理；同一 runner 还以真实 profile composition 验证缺少独立 report 时在 allocate/open 前失败关闭，并在五个 content/descriptor/directory/delete 进程崩溃切点中仅按精确 opaque identity 收敛为同一 receipt 或 exact cleanup。随后 `scripts/b24-conformance-matrix.sh` 以独立 backend report 重新运行 contract/model/MySQL evidence，并把层级与缺失真实场景写入无敏感 matrix。它只产生 14 个真实 backend 子场景的 `REAL_BACKEND_CONFORMANT` 证据，既非批准生产主机也不等同完整 manifest。默认 production 仍 disabled，只有当前 build/config 的真实三层 conformance 才能产生 readiness。

B24d 还要求正值 `atom.files.policy.local-free-floor-bytes`。secure-local 在构造、allocation 与从未使用过的 namespace 写入前重新采样当前 filesystem；probe 失败或低于 floor 均失败关闭，后续 I/O 失败仍走精确 cleanup。Linux report 的 `observedChecks` 记录 unsafe/symlink root、mode/root rebind 与 floor 的实际拒绝，但不替代完整 `S-10`。

B29a 已将 Product 与 ReturnVisit 接入同一受限文件边界：普通附件使用显式 target adapter，结构化 `FILE` 通过独立 `fileFields`（每字段最多 10 个有序文件）读写和返回。Product 仅以 `products:*` 加 `files:*` 的活跃成员能力授权，ReturnVisit 继续叠加当前读写范围；两类软删除会在根锁内撤回普通关系和 FILE binding，且文件不会进入产品快照、Activity 或工作台事实。详见 [B29a 验收记录](docs/acceptance/B29a.md)。

Product 图片进一步提供关系级 `PRIMARY`/`DETAIL` 分组：已有产品使用受控附件接口的 `productImageType` 过滤与上传意图，每组仅 PNG/JPEG 且最多 9 个 active 候选或已发布文件；新建产品先创建仅 uploader 可用的私有图片草稿，再在 Product 创建事务中核对并一次性发布、绑定 exact set。`V97` 为分组关系与草稿候选提供 tenant/owner/状态/版本约束；过期、撤销和后台处理都沿用既有受控文件清理，不暴露 targetless 下载或公开存储 URL。

B25 对账了 B24 后仍明确存在的本地业务接缝、调度/通知接缝与受真实外部证据阻断的边界。B25a 已实现客户上下文的权限感知业务概览，只组合当前 caller 在 Contact、Opportunity、Contract、Invoice、Receivable、ReceivablePlan 和 ReturnVisit 七个来源中独立可见的直接 Customer 摘要；不增加共享数据范围、缓存或跨域仓储。详见 [B25 规划](batch-B25.md) 和 [B25a 验收记录](docs/acceptance/B25a.md)。

B26a 已实现统一 composition-root pulse 与两条隔离 durability lane。Customer/Contact 公海自动收回使用固定 kind 的 MySQL schedule/run、共享 UTC、occurrence identity、lease/fence、同 run 有界重试、coalesced misfire 和 terminal retention；全局单调 cadence revision 会在启动时对账已有 schedule、取消旧 generation run，并使较旧混部副本 `NOT_READY` 而不能 claim。tenant 仅来自已认领 run，后台不伪造 `AuthenticatedCaller`。`atom.scheduling.enabled` 默认关闭，配置非法、未对账或 revision 漂移时只使这一 lane 失败关闭，不影响普通 CRM 请求。B24 附件 worker 只改由同一 pulse 唤醒，继续保留自己的 job ledger、readiness、外部 I/O 和恢复语义；不提供自动化 HTTP API、cron、脚本、通知或 Work recurrence。详见 [B26 规划](batch-B26.md) 与 [B26a 验收记录](docs/acceptance/B26a.md)。

B27a/B27b 已实现 Work 周期、耐久提醒与当前成员站内收件箱。周期任务 HTTP API 现在下发具名列表、详情和 mutation 投影：浏览器只能消费服务端计算的 `allowedActions`（`UPDATE`、`ACTIVATE`、`PAUSE`、`STOP`）及稳定 blocker，不能由创建人或全局权限自行推断；激活仍在写入时复验当前参与者、规则、乐观锁和调度就绪状态。B27c 现增加可选 EMAIL delivery outbox：`GET/PUT /api/v1/notifications/preferences` 仅允许成员更新自己的 `emailEnabled`，缺失偏好默认为 OFF；仅在 deployment 可 dispatch 时提醒 effect 才与零或一条 EMAIL delivery 同事务提交。readiness 会在 claim 前预检、并在 task → notification → delivery 的 admission 事务内复验当前成员、能力、任务参与、偏好与受信 profile endpoint；`UNSUPPORTED` 或短暂 not-ready 都不会夺取或终态化待投行。首次 dispatch 冻结 guarantee、原 idempotency window 与带租户/用途域隔离及 key version 的 HMAC endpoint binding；`IDEMPOTENT_SEND` 只在原窗口内复用原 exact command，一旦 provider effect 不确定，deadline、尝试耗尽、任务变更和 admission 恢复均保持 `AMBIGUOUS`。adapter 调用只使用扣除 settlement reserve 后的 lease/deadline 剩余预算，丢失 settlement 会令 lane 失败；deadline/retention 维护在通道关闭时仍执行，且终态 delivery 保留不会超过 notification 内容保留期。`ACCEPTED` 仅表示 provider 受理。默认 EMAIL adapter disabled，示例 logging welcome 通知不参与；B27c 不改变任务、worklist acknowledgement、站内已读或 reminder 终态。详见 [B27 规划](batch-B27.md)、[B27b 验收记录](docs/acceptance/B27b.md) 与 [B27c 验收记录](docs/acceptance/B27c.md)。

B28a/B28b 已实现 OA 公告的一次性定时发布与可靠 EMAIL 接入：发布协调器在同一事务提交公告根、不可变 recipient snapshot 以及当前已明确 opt-in 且仍具有 `notifications:read`/`oa-announcements:read` 的成员的 outbox 行。`crm_notification_delivery` 是 `WORK_TASK_NOTIFICATION` 与 `OA_ANNOUNCEMENT_RECIPIENT` 两种封闭 typed source；已有 Work 行保持原 digest、`b27c:` 幂等键、HMAC binding、guarantee 与 admission 语义。OA 偏好 `oaAnnouncementEmailEnabled` 独立且默认 OFF，OA template 仅含公告 ID、标题和实际发布时间，绝不发送正文或受众。撤回/删除会 suppress 未跨 dispatch boundary 的行，已有 dispatch history 的 effect 保持 `AMBIGUOUS`；公告发布、站内已读和 provider 受理仍是三个不同事实。详见 [B28 规划](batch-B28.md)、[B28a 验收记录](docs/acceptance/B28a.md) 与 [B28b 验收记录](docs/acceptance/B28b.md)。

B29a/B29b/B29c/B29d 已实现受限文件边界。B29b 让活动 MarketingForm 配置 custom LEAD `FILE` 字段，并通过一次返回、仅保存 purpose-separated digest 的匿名草稿凭据创建候选；公开候选始终记录 exact public draft upload origin，不会把表单 owner 伪装成 uploader/caller。能力仅以 `X-Public-Upload-Capability` 传输，绝非 `Authorization`，且公开 OpenAPI 分别声明草稿、intent、二进制上传、安全状态和撤销确认。B29c 将 OA 公告接入普通 typed attachment relation：只有草稿管理者能变更，排期/立即/到期发布在公告根锁内冻结至多 100 个全 `AVAILABLE` 附件；已发布下载还要求 `files:read`、`oa-announcements:read`、当前成员资格和 immutable recipient snapshot，且 B28 EMAIL 仍无附件或 URL。B29d 将 OA 申请协作附件限制为 `PENDING` 中的申请人、当前任务 assignee 或实例管理人，并在审批终态前清理全部未就绪关系；提交 FILE 事实、审批快照与已可用协作附件读取均不受改写。详见 [B29 规划](batch-B29.md)、[B29b 验收记录](docs/acceptance/B29b.md)、[B29c 验收记录](docs/acceptance/B29c.md) 与 [B29d 验收记录](docs/acceptance/B29d.md)。

B30a 已实现权限感知的有界 Customer CSV 导出：`POST /api/v1/customers/export/csv` 同时要求独立、owner-derived 的 `customers:export` 数据范围和既有 `customers:read` 可见性，并仅输出明确选择的当前可读、未脱敏标量字段。一个只读快照会在 HTTP body 前生成最多 5,000 行、50 个字段和 16 MiB 的 UTF-8 BOM CSV；输出固定按 `customer_id ASC`、使用 RFC 4180、稳定字段 code header 与 spreadsheet 公式防护。它不复用 B24、不建立异步文件，也不预占导入、Excel、跨对象或批量迁移。详见 [B30 规划](batch-B30.md) 与 [B30a 验收记录](docs/acceptance/B30a.md)。

B31a 已实现权限感知的有界 Contact CSV 导出：`POST /api/v1/contacts/export/csv` 同时要求独立、owner-derived 的 `contacts:export` 数据范围与既有 `contacts:read` 可见性，并只输出明确选择的当前可读、未脱敏标量字段。它复核 Customer 与上级 Contact 保存视图目标的当前可见性，固定排除 ContactPool、按 `contact_id ASC` 在 HTTP body 前生成最多 5,000 行、50 字段和 16 MiB 的 UTF-8 BOM CSV。Customer 与 Contact 只共享公式安全的 CSV 编码和同一副本资源门；不建立动态对象仓储、导入协议或异步文件。详见 [B31 规划](batch-B31.md) 与 [B31a 验收记录](docs/acceptance/B31a.md)。

B33a 已实现权限感知的有界 Lead CSV 导出：`POST /api/v1/leads/export/csv` 同时要求独立、owner-derived 的 `leads:export` 数据范围和既有 `leads:read` 可见性，并仅输出调用方显式选择的当前可读、未脱敏标量字段。导出保持 Lead 的 keyword、名称、负责人、下属负责人、个人关注、转换状态和保存视图/临时筛选语义；Customer 与上级 Lead 条件仍在当前可见性下复核，但关系、标签、地址、负责人不会变成 CSV 单元格。输出固定为 `lead_id ASC`，在 HTTP body 前生成最多 5,000 行、50 字段和 16 MiB 的 UTF-8 BOM CSV，并与已有 CSV 共用同一副本资源门。详见 [B33 规划](batch-B33.md) 与 [B33a 验收记录](docs/acceptance/B33a.md)。

B32a 已实现 tenant 级 Opportunity 销售管线与阶段目录：管线和阶段用 immutable、大小写敏感的 exact code 标识并独立版本化，受非数据范围的 `opportunity-pipelines:read/manage`、tenant 目录守卫、乐观锁和同 tenant 复合外键治理。V85 原样 backfill 历史 pair，新增 tenant 默认获得已启用的 `direct/qualification` 与 `direct/negotiation`；目录停用只阻止新建或实际迁入，既有商机保持可读、可筛选并可在 pair 二进制精确不变时编辑其他字段。B05 仍拥有两个 `TEXT` 字段的访问策略，B11 workflow stage 继续是独立命名空间且不会与销售阶段隐式同步。详见 [B32 规划](batch-B32.md) 与 [B32a 验收记录](docs/acceptance/B32a.md)。

Redis 通过 `atom.redis.enabled` 启用，默认使用空缓存实现。Flyway 迁移脚本位于 `infra/persistence/src/main/resources/db/migration`；数据库变更应新增迁移文件，不要修改已执行的迁移。

完整的身份、Redis、数据库和密钥配置见[配置指南](docs/configuration.md)。

## 测试

```bash
sh ./mvnw test                                        # Docker 可用时自动包含集成测试
sh ./mvnw test -Datom.test.skip-database-tests=true   # 显式跳过集成测试
```

集成测试按 Docker 可用性自动启用；Docker 不可用时干净跳过，不会失败。`CI=true/false` 不控制此门禁，`make test` 与 `make integration-test` 当前使用同一机制。需要明确快跑时使用上面的 skip 参数；需要数据库验证时确保 Docker 可用，并确认 Surefire 报告中相关用例实际执行，不能仅以构建成功认定集成回归通过。

R1 发布门禁会同时检查版本一致性、残留 SNAPSHOT、差异格式、完整 Maven 安装和
MySQL Testcontainers 回归：

```bash
make release-verify
```

集成测试覆盖 Flyway、租户隔离、角色与字段权限失败关闭、客户、联系人、商机、合同和发票数据范围、B17a 核心报表、B17b 三来源趋势/排名的数据范围、九种指标粒度组合、零桶、排序、生命周期和日期索引、B17c 个人定义所有权、读写路由、软删除、字段依赖锁、501 行拒绝及真实 MySQL 聚合/EXPLAIN 索引回归、B18a 仪表盘偏好的类型、隔离与版本、B18b 固定卡片的偏好收窄、部分可用、字段失败关闭和默认读取不落库、B19a 营销表单字段依赖、严格公开输入、规范公开路径限流、单连接事务复用、6907 错误归一和公开 B09 线索创建，以及 B19b 成功台账原子回滚、趋势零桶、历史生命周期、当前线索数据范围和 SQL 内可见分页、B20a OA 字段 provision、表单依赖、当前字段访问、跨 tenant 失败关闭、并发启停、审批定义锁序和 200 条上限、B20b 权威申请事实、原子审批联动、并发幂等、失败回滚、SQL 参与者可见性和 B15 待审批复用、B20c 公告受众快照、历史稳定性、并发幂等回执、成员失活锁序、共享公告锁、未读统计与并发发布仲裁、B21 任务参与可见性、隐藏 CRM 关系安全清除、状态机、重新分派、复合外键、并发版本仲裁及责任人工作台投影、B22a connector 一次性凭据、主体隔离、tenant/ref 不可迁移、轮换/撤销/停用/退役即时失败关闭及 registration 锁序、审批路由快照与阶段审批门、活动/回访关系和版本、九来源工作台 `v2`/`v3` cursor 兼容与确认、商机关系与结果生命周期、产品目录及快照、合同累计开票金额和开票资料、并发唯一值/层级仲裁、公海领取限额/冷却/转池/并发领取、乐观锁、软删除、HTTP 认证和错误映射。当前测试目标为 JVM 部署，不包含 GraalVM native-image。

`.github/workflows/ci.yml` 会在每个 pull request 和 `main` 推送上运行同一 R1
发布门禁；MySQL Testcontainers 集成用例仍取决于运行环境的 Docker 可用性。

## 模板清理脚本

`clean.sh` / `make clean-sample` 是脚手架遗留的源码删除工具，不属于安装、重置数据或 CRM 精简步骤。当前首次安装、密码认证和组织成员管理均依赖 User 代码；删除这些文件会破坏应用。保留现有源码，仅在明确准备重新设计这些功能时单独评估脚本。

## 文档

- [前后端开源准备检查记录](docs/acceptance/open-source-readiness.md)
- [贡献与问题反馈](CONTRIBUTING.md)
- [安全问题报告](SECURITY.md)
- [变更记录](CHANGELOG.md)
- [Agent 执行约定](AGENTS.md)：按任务定位上下文、保留架构与安全不变量，并选择能证明改动的验证；[llms.txt](llms.txt) 提供当前指南与历史资料索引。
- [R1 / 1.0.0 发布范围与门禁](release-R1.md)
- [R1 主干发布验收记录](docs/acceptance/R1.md)
- [架构与不变量](docs/architecture.md)
- [HTTP API 参考](docs/api-reference.md)
- [开发流程](docs/usage-guide.md)
- [配置与安全](docs/configuration.md)
- [生产部署与容器运行](docs/deployment.md)
- [对象分层与命名](docs/object-layering.md)
- [测试指南](docs/test-guide.md)
- [B19a 复审验收记录](docs/acceptance/B19a.md)
- [B18b/B19b 合入后复审验收记录](docs/acceptance/B18b-B19b.md)
- [B20a 验收记录](docs/acceptance/B20a.md)
- [B20b 验收记录](docs/acceptance/B20b.md)
- [B20c 验收记录](docs/acceptance/B20c.md)
- [B23a 企业数据查询内核验收记录](docs/acceptance/B23a.md)
- [B23-O-L1 GLEIF 离线 mapper lane 验收记录](docs/acceptance/B23-O-L1.md)
- [B23-RA GLEIF RA/EntityID 离线映射验收记录](docs/acceptance/B23-RA.md)
- [B23-O-L2 quota/readiness durable simulator](docs/B23-O-L2-durable-simulator.md)
- [B23-O-L3 离线 HTTP/security simulator](docs/B23-O-L3-http-security-simulator.md)
- [B24-S 对象存储映射、readiness 与失败契约](docs/B24-S-storage-readiness.md)
- [B24-I 内容检查、类型识别与 quarantine 契约](docs/B24-I-content-inspection.md)
- [B24-A 业务附件授权、目标关系与父生命周期契约](docs/B24-A-attachment-access.md)
- [B24-X 安全文件与业务附件组合 fixture](docs/B24-X-composed-fixture.md)
- [B24-X/1 可执行独立模型 fixture](docs/B24-X-model-fixture.md)
- [B24a Work Task 附件内核验收记录](docs/acceptance/B24a.md)
- [B24b 八类 CRM 业务附件目标验收记录](docs/acceptance/B24b.md)
- [B24c 受治理 FILE 字段验收记录](docs/acceptance/B24c.md)
- [B24d secure-local / ClamAV 真实后端 canary 验收记录](docs/acceptance/B24d-real-backend-canary.md)
- [B25a 客户权限感知业务概览验收记录](docs/acceptance/B25a.md)
- [B25 剩余业务接缝与客户业务概览边界规划](batch-B25.md)
- [B26 统一调度与自动化内核边界规划](batch-B26.md)
- [B27 Work 周期任务、提醒升级与可靠通知边界规划](batch-B27.md)
- [B28 OA 公告定时发布与可靠通知接入边界规划](batch-B28.md)
- [B29 剩余文件业务覆盖扩展边界规划](batch-B29.md)
- [B30 剩余业务接缝与有界 Customer CSV 导出边界规划](batch-B30.md)
- [B31 B30a 后本地业务接缝与有界 Contact CSV 导出边界规划](batch-B31.md)
- [B32 Opportunity 销售管线与阶段目录治理边界规划](batch-B32.md)
- [B32a Opportunity 销售管线与阶段目录治理验收记录](docs/acceptance/B32a.md)

## CRM CSV 受控导入

线索、联系人和产品 CSV 导入使用私有草稿协议，而不是直接 multipart 创建：`POST /api/v1/crm-import-drafts` 以 `LEAD|CONTACT|PRODUCT` 创建草稿，`GET /templates/{objectType}` 返回固定列，产品列为 `name,code,categoryId,unit,listPrice,description,availability`；源文件只通过既有 `import-draft` 附件目标的 upload intent、原始内容上传与安全检查状态进入系统。草稿创建、预览、确认、撤回分别要求对象写入权限和 `files:write`；源文件只允许严格 UTF-8 CSV，最大 1 MiB、200 行，且一个活动草稿只能有一个源附件。`GET /{draftId}/preview` 只返回可导入计数及行号/稳定错误码，绝不返回单元格内容；若预览发现错误，`GET /{draftId}/error-report` 会重新解析同一私有源附件并下载仅含 `rowNumber,errCode` 的受控 CSV，响应禁止缓存且不使用原始文件名。`POST /{draftId}/confirm` 以草稿 version 在一个事务中复核当前写入范围、字段策略和关联可见性。线索和联系人要么全部创建要么全部回滚；产品以规范化编码去重，跳过已有记录和同一文件重复记录且绝不覆盖既有字段，其余创建仍在同一事务内完成。确认或取消都会撤回源附件；确认在响应丢失后按 version 只重放最小完成投影。CSV 原始字节、文件名和单元格不进入业务 DTO、数据库列、响应或日志。

## 许可证

MIT，详见 [LICENSE](LICENSE)。
