# 部署与发布

本文针对当前 `atom-crm` 后端与配套 `atom-crm-web` 前端。首次本地体验见 [README](../README.md)；生产能力及配置边界见 [configuration](configuration.md)。两个仓库应作为经过联调的版本对交付，记录各自的 commit/tag，不能仅凭版本号假定接口兼容。

## 构建可发布产物

在后端根目录使用 JDK 25、Maven Wrapper 和可访问的 Docker：

```bash
make release-verify
docker build -t atom-crm:local .
```

没有 Make 时用 `sh scripts/release-r1-verify.sh`。发布门禁执行完整 `clean install`，并检查每个数据库测试类均实际运行。可执行 JAR 为 `start/target/atom-crm-start.jar`。Dockerfile 只复制该 JAR，运行用户为 `10001:10001`；镜像默认激活 `prod`，不会将源码、`.env`、测试配置或本地配置目录复制进去。镜像构建不会替代测试。

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

Flyway 是唯一建表和升级机制，保留迁移校验与禁用 clean；不要修改已应用迁移。发布前备份 MySQL，并在隔离环境验证恢复与迁移。先对备份副本升级和联调前后端，再安排生产变更。

回退应用版本不等于回退数据库。迁移不向后兼容时，需要停写并按已演练的数据库恢复方案回退，同时保留升级后的数据用于核对。文件能力若启用，还需备份并恢复其实际私有存储和数据库关联；仅备份 MySQL 不足以恢复附件。

Redis、电话、企业数据源、文件扫描/存储、后台调度和工作通知适配器继续遵守各自默认关闭及 readiness 契约。SMTP 欢迎邮件的可用性不代表这些能力已配置完成。

## 发布仓库

源码仓库为 [atom-crm 后端](https://github.com/Archetom/atom-crm) 和 [atom-crm-web 前端](https://github.com/Archetom/atom-crm-web)。公开历史从本次发布快照的新初始提交开始，不包含此前开发过程中的 Git 提交；旧 Git 元数据仅保留在本地备份。后续变更通过各自的 `main` 分支与经过验证的前后端版本对交付。

后续发布前由维护者确认版本对、启用私人漏洞报告入口，检查完整 Git 历史中的凭据与内部资料，并确认第三方依赖和素材的分发要求。发生过泄漏的密钥需要撤销或轮换；删除当前文件不能清除历史。

创建 tag/Release 时附上前后端 commit、运行环境、发布门禁结果与已知受限能力，保留 MIT 文件及分发依赖所需的许可声明。维护者应启用 main 分支保护、要求 CI 通过，并限制绕过者。有关协作和漏洞报告见 [CONTRIBUTING](../CONTRIBUTING.md) 与 [SECURITY](../SECURITY.md)。
