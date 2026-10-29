# 部署与发布

本文针对当前 `atom-crm` 后端与配套 `atom-crm-web` 前端。首次本地体验见 [README](../README.md)；生产能力及配置边界见 [configuration](configuration.md)。两个仓库应作为经过联调的版本对交付，记录各自的 commit/tag，不能仅凭版本号假定接口兼容。

## 构建可发布产物

在后端根目录使用 JDK 27、Maven Wrapper 3.10.0 和可访问的 Docker：

```bash
make release-verify
docker build -t atom-crm:local .
```

没有 Make 时用 `sh scripts/release-verify.sh`。发布门禁执行完整 `clean install`，并检查每个数据库测试类均实际运行。可执行 JAR 为 `start/target/atom-crm-start.jar`。Dockerfile 只复制该 JAR，运行用户为 `10001:10001`；镜像默认激活 `prod`，不会将源码、`.env`、测试配置或本地配置目录复制进去。镜像构建不会替代测试。

门禁同时检查发布版本一致且不含 `SNAPSHOT`、工作区差异无空白错误，并要求 Docker 可用。任何已发现的数据库测试缺少报告、未执行、跳过、失败或出错都会使发布失败。成功执行跳过数据库的快速测试不能替代发布门禁；完整说明见 [测试指南](test-guide.md#commands)。

前端在其仓库内使用 `package.json` 指定的 Node/pnpm、`pnpm install --frozen-lockfile`、`pnpm build` 和 `pnpm bundle:check`，再按该仓库 Dockerfile 构建静态站点镜像。前端 Nginx 的 `ATOM_CRM_API_UPSTREAM` 必须指向同一容器网络中的后端，例如 `http://atom-crm:8080`。同源 `/api/` 代理与 SPA 路由回退由前端配置提供。

## 生产配置

复制 [`.env.example`](../.env.example) 到未跟踪的 `.env` 并填充空值。文件仅是配置模板；不要将生产值、真实账号或密钥提交 Git，也不要把 `.env` 复制进镜像。部署平台可直接注入相同变量。

- 配置生产 MySQL 的地址、最小必要权限账号、密码和 TLS 信任链。示例 JDBC URL 使用 `sslMode=VERIFY_IDENTITY`，自签 CA 需安装受信任证书。根目录 Compose 的固定密码、回环端口和数据卷仅用于本地开发。
- 生成并保存至少 32 个随机字节的无填充 Base64URL 签名密钥。所有副本使用同一密钥，重启保持稳定；轮换会使既有会话失效。可以用 `openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n'` 生成，直接保存到秘密存储，勿附在日志或 Issue 中。
- 配置 `ATOM_AUTHENTICATION_PASSWORD_RESET_URL=https://你的域名/password-reset/confirm`。这对应前端真实路由；使用 HTTPS 对外提供整个站点，生产会话 Cookie 固定为 Secure。
- 配置 SMTP 欢迎通知：`ATOM_USER_NOTIFICATION_MAIL_ENABLED=true`、合法的 `ATOM_USER_NOTIFICATION_MAIL_FROM` 和示例中的 `SPRING_MAIL_*`。该适配器只在 `prod` 激活。自定义部署也可禁用该 flag 并提供自己的 `UserNotificationPort`；缺少生产实现仍拒绝启动。
- 密码重置邮件单独通过 `ATOM_AUTHENTICATION_PASSWORD_RESET_MAIL_ENABLED` 和 `ATOM_AUTHENTICATION_PASSWORD_RESET_MAIL_FROM` 启用。欢迎邮件不提供持久重试保证，也不启用工作通知的 EMAIL 通道。

在已有的私有 `crm` 容器网络中，可用如下命令启动后端；`.env` 中的数据库地址必须从容器可达：

```bash
docker run -d --name atom-crm --network crm \
  --env-file .env -p 127.0.0.1:8080:8080 atom-crm:local
curl --fail http://127.0.0.1:8080/actuator/health
```

网络不存在时先执行 `docker network create crm`，并将数据库与前端加入该网络或使用可达的受保护服务地址。示例只发布回环后端端口；在前端之前配置 HTTPS 入口。`prod` 不允许开发身份头。代理部署按 [来源地址契约](configuration.md#public-lead-intake) 配置信任代理或网关限流；不要用通配信任客户端转发头。

首次启动后先限制安装入口仅供部署人员访问，再在前端 `/setup` 创建 owner。系统没有默认账号。确认安装流程关闭、密码登录与退出可用、租户权限正确，再开放站点。SMTP 真实投递需在你控制的测试邮箱验证；本仓库的自动化测试不向外发信。

## 数据与升级

工程基线升级到 Atom Archetype 2.3.0 后，构建与部署运行时均需要 Java 27，atom-common 为 1.2.0。应用版本仍独立维护；升级时合并经过验证的工程配置，不重新生成或覆盖业务源码、环境配置和已应用迁移。Java 25 运行时无法运行新的构建产物，应先在隔离环境验证 Java 27 镜像与前后端版本对。

Flyway 是唯一建表和升级机制，保留迁移校验与禁用 clean；不要修改已应用迁移。发布前备份 MySQL，并在隔离环境验证恢复与迁移。先对备份副本升级和联调前后端，再安排生产变更。

Java 升级也可能改变 JDK 的时区数据库证据。Work 周期任务的 `atom.work.recurrence.tzdb-fingerprint=AUTO` 会与已持久化的控制行核对；相同策略版本下的 tzdb 不匹配会使该执行通道保持未就绪，阻止激活和物化，普通 Work 请求仍可用。部署前核对各副本的运行时与现有策略/tzdb 控制状态，按已有[周期任务配置契约](configuration.md#scheduled-automation)评估和验证协调过程，并检查 `automationScheduling` readiness。不要假定换 JDK 会自动接受新的指纹，也不要直接改控制表或迁移来绕过检查。

回退应用版本不等于回退数据库。迁移不向后兼容时，需要停写并按已演练的数据库恢复方案回退，同时保留升级后的数据用于核对。文件能力若启用，还需备份并恢复其实际私有存储和数据库关联；仅备份 MySQL 不足以恢复附件。

Redis、电话、企业数据源、文件扫描/存储、后台调度和工作通知适配器继续遵守各自默认关闭及 readiness 契约。SMTP 欢迎邮件的可用性不代表这些能力已配置完成。

## 能力与兼容性边界

当前交付包含组织与租户授权、字段与视图定制、客户/联系人/线索/商机、产品、合同/发票/回款、工作任务、报表、营销表单和 OA，以及各接口明确支持的受限 CSV 导入导出。每项能力仍受当前成员、业务权限、记录范围和字段策略限制；导航可见性不能代替授权。

电话连接器已提供注册、认证和持久化接入基础，尚未提供生产外呼或录音播放服务。企业数据已提供受控查询与显式采纳边界，但真实数据源、权利策略和当前证据必须单独满足准入。附件已提供安全本地存储和扫描链路，生产默认关闭；S3 生产适配器、公共分享、预览和 OCR 不属于当前交付。详见 [连接器](connector-integration.md)、[企业数据](enterprise-data.md) 和 [附件](file-storage.md)。

受限 CSV 接口不代表支持任意 Excel 或无限量异步导出。调度仅接受实现中列出的固定工作种类，不执行租户提供的 cron、脚本或通用 Webhook。商机阶段与审批工作流保留各自的语义，不会隐式同步。可选适配器不可用时，应保持相关能力关闭，不能降低其他 CRM 功能的租户或授权检查。

`/api/v1` 是当前 1.x 公共接口命名空间。会破坏现有请求、响应或持久化语义的变更必须设计明确的版本边界与迁移路径，并验证前后端版本对；不能仅保留 URL 就声称兼容。

## 发布仓库

源码仓库为 [atom-crm 后端](https://github.com/Archetom/atom-crm) 和 [atom-crm-web 前端](https://github.com/Archetom/atom-crm-web)。变更通过各自的 `main` 分支与经过验证的前后端版本对交付。

后续发布前由维护者确认版本对、启用私人漏洞报告入口，检查完整 Git 历史中的凭据与内部资料，并确认第三方依赖和素材的分发要求。发生过泄漏的密钥需要撤销或轮换；删除当前文件不能清除历史。

创建 tag/Release 时附上前后端 commit、运行环境、发布门禁结果与已知受限能力，保留 MIT 文件及分发依赖所需的许可声明。维护者应启用 main 分支保护、要求 CI 通过，并限制绕过者。有关协作和漏洞报告见 [CONTRIBUTING](../CONTRIBUTING.md) 与 [SECURITY](../SECURITY.md)。
