# B24 安全文件对象与业务附件开源边界规划

> 基线：`aee60c3 fix(B22a): 修复 connector 安全复审问题`
>
> 对账日期：2026-07-21
>
> 状态：B24-S/I/A/X 契约、B24-X/1 模型与 [B24-SPLIT](docs/B24-SPLIT-implementation-boundaries.md) 已闭合；B24a Work Task 附件内核、B24b 八类 CRM target 以及 B24c 受治理 FILE/OA 草稿与快照均已实现，迁移完成至 V64。B24d 已增加 Unix-socket ClamAV `INSTREAM` runtime adapter、owner-only external report source 和 secure-local/ClamAV 合成 canary runner；实际 backend subset 已记录，但完整 S/I/X 三层证据仍缺失。剩余业务覆盖见 [B29](batch-B29.md)；production 继续默认 disabled，启用仍需当前 build/binding/policy 的完整真实 conformance 证据。

> 第 1–12 节保留 B24 实现前的决策语境；当前交付状态以第 13 节和 B24a/b/c 验收记录为准。

## 1. 范围结论

B24 中的“文件”特指：已认证 CRM 成员为一个当前有权写入的业务记录上传有界二进制对象，经完整性校验和恶意内容检查后，以附件形式供仍有权读取该业务记录的成员下载。它不指文件盘、网盘、网站静态资源、数据导入导出、在线文档、电子签章、企业数据报告、呼叫录音或一个任意对象存储代理。

B24 选择文件与附件作为下一批能力，是因为仓库已经存在三个明确但尚未闭合的接缝：

- B05 已声明 `CustomFieldType.FILE`，但 Customer、Contact、Lead、Opportunity、审批事实、OA 表单和保存视图均明确拒绝该类型；枚举存在不等于文件值已经可安全保存；
- B10 的商机文件、B20 的 OA/公告附件和 B21 的任务附件都被后置到独立文件批次；
- 当前 `BusinessRecordAccessGuard` 已能重新应用多个业务域的当前能力与数据范围，但还没有文件字节、上传隔离、检查、下载授权和确认删除契约。

首期采用“受治理文件对象 + 单一业务附件关系”而不是“通用文件库”：每次上传意图在创建时就绑定一个明确 tenant、上传成员和业务目标，一个完成的文件对象最多形成一个当前附件关系。客户端不能先建立一个脱离业务授权的私人网盘对象，再把它任意分享、复制或跨记录挂载。

MySQL 保存身份、目标、状态、版本、大小、类型、完整性、检查结论和最小审计；对象字节只进入部署提供的隔离对象存储。对象存储、恶意内容检查、密钥、bucket、endpoint、provider locator、签名 URL 和原始扫描器响应都不是 domain 或公共 API 类型。

B22 录音继续使用自己更严格的 connector、号码、媒体代理、retention 和确认销毁契约。B24 不把 B22 recording 改造成普通附件，也不允许普通附件能力绕过 B22 媒体授权。

## 2. 开源与部署边界矩阵

| 能力 | 决策 | 所属边界 | 说明 |
| --- | --- | --- | --- |
| 文件上传意图、生命周期和版本规则 | 开源 | domain/application | provider-neutral，显式 tenant、上传成员和唯一业务目标 |
| 业务附件关系与当前授权复核 | 开源 | application/domain/persistence | 上传、列出、下载、删除分别复核独立文件能力与目标业务域权限 |
| 有界流式上传和下载用例 | 开源 | application output ports / `infra/rest` | 不把完整文件放入 JVM heap、MySQL、Redis、事件或日志 |
| 对象存在、元数据、完整性、读取和删除端口 | 开源契约 | application output ports | typed request/result，不暴露 bucket、path、URL、header map 或 SDK 对象 |
| 恶意内容检查与类型识别端口 | 开源契约 | application output ports | 只返回受控结论、引擎目录版本和检查时间 |
| 禁用实现和确定性合成适配器 | 开源 | `infra/external` / tests | 默认生产不可用；测试不依赖网络、真实恶意样本或供应商账号 |
| 元数据、任务租约和删除确认持久化 | 开源 | `infra/persistence` | MySQL 是状态和调度正确性来源，Redis 不是 |
| S3、兼容对象存储或本地文件系统 SDK | B24-S 已冻结 reference profile，具体 SDK/产品未选择 | 可选 `infra/external` artifact | 不能进入 domain/application；本地适配器仅用于开发测试时也必须隔离 webroot |
| bucket、region、endpoint、access key、KMS key 和网络策略 | 部署私有 | secret / platform configuration | 不能由普通请求选择或进入公共响应、日志和 metric 标签 |
| ClamAV、商业沙箱、CDR 或内容识别实现 | 私有或可选模块 | 独立检查适配器 | 核心只消费固定结论；签名库更新与引擎运行不是 CRM 事务 |
| 预签名直传/直下 | 首期不纳入 | 后续传输 profile | URL 是限时 bearer，需独立绑定、CORS、覆盖、撤销和泄漏模型 |
| 缩略图、在线预览、转码、OCR 和全文索引 | 不纳入 B24 首期 | 后续内容处理批次 | 会引入解析器隔离、衍生对象授权和删除传播 |
| 公共分享、外部协作者和匿名下载 | 不纳入 | 外部身份/分享批次 | 当前没有外部主体、撤销传播和防枚举边界 |
| CSV/Excel 导入导出和数据迁移 | 不纳入 | 独立数据治理批次 | 附件字节流不能成为批量读写 CRM 数据的旁路 |
| 远程 URL 抓取、网页归档和云盘同步 | 不纳入 | 禁止作为首期输入 | 避免 SSRF、重定向、内网探测、凭据转发和来源许可问题 |

## 3. 与现有能力的承接关系

### 3.1 B05 `FILE` 只是待实现类型标记

`CustomFieldType.FILE` 当前只属于字段元数据目录；各业务值策略拒绝它，OA 表单也不能配置它。B24 不能把文件名、对象 key、URL、base64、JSON 或逗号分隔 ID 当成普通字符串自定义值。

首期通用“附件”与 B05 字段值保持分离：附件绑定业务记录，但不宣称占据某个自定义字段。B05 `FILE` 真正启用时必须另行冻结以下语义后再作为 B24c 实施：

- 单文件还是有序多文件、最大基数、必填和替换语义；
- 字段 `READ/WRITE/HIDDEN/MASKED` 如何与文件能力及业务记录数据范围取交集；
- 字段停用、删除、布局、保存视图、唯一性、审批事实和历史快照如何处理文件引用；
- 文件被拒绝、待检查、删除中或确认删除时，字段值是否成立以及命令如何原子失败。

在 B24c 之前，不修改现有业务聚合的自定义值结构，也不把 `FILE` 从当前失败关闭名单中移除。

### 3.2 业务聚合仍拥有记录授权

附件不能成为读取 Customer、Contact、Lead、Opportunity、Contract、Invoice、Receivable、ReceivablePlan 或 Work Task 的新入口。[B24-A](docs/B24-A-attachment-access.md) 已固定独立 `AttachmentTargetType` 白名单：Work Task 使用 B21 专用 creator/assignee/follower/manager 参与谓词，8 类 CRM 记录复用各自 access resolver/repository 的当前读/写数据范围；它不扩大 `BusinessRecordAccessGuard` 去承载 Work、锁序和父生命周期。知道附件 ID 不能读取目标名称、文件元数据或字节。

创建上传意图、重新打开 uploader 流、读取 private 检查状态和撤回附件要求 `files:write` 与目标记录当前协作/写范围的交集；列出和下载要求 `files:read` 与目标记录当前读范围的交集。两个文件能力都不带数据范围且互不包含，没有首期 `files:manage/delete`。失去业务记录权限后，历史上传者也立即失去附件访问；重新获得权限后只恢复仍处于可读状态的附件。

首期不提供跨业务记录的“最近文件”“全部文件”“按文件名搜索”或 tenant 管理员文件库。所有列表都从一个已经过授权的明确目标开始，因此隐藏记录、无附件记录和不存在记录不会形成跨域枚举旁路。

### 3.3 B20、B21 和 B22 的边界不被偷渡

- OA Request 不纳入 B24a/b 通用目标：它在提交前没有 exact request ID，提交后事实又不可变，现有 `oa-requests:submit/read/manage` 也不表示提交后协作写权。OA 文件只能在 B24c 作为提交前已 `CLEAN` 并与请求同事务冻结的 FILE 事实，或经过新的 OA 协作设计后接入；提交后新增字节永远不改写 B11 已保存的权威路由/事实快照；
- Work Task 附件只扩大任务协作内容，不改变责任人、关注人、评论、状态或工作台投影版本；任务参与权也不能反向授予关联 CRM 记录的文件访问；
- 公告发布后的不可变正文和受众快照不能被一个可替换附件绕过。公告附件需要独立的发布时冻结和接收人授权设计，不属于首期通用记录附件；
- B22 recording 的 `externalRecordingRef`、媒体 bytes 和删除证据不进入 B24 文件表、下载路由或对象存储端口。

## 4. 首期最小产品边界

### 4.1 纳入

1. **创建上传意图**：对一个明确、当前可写的业务目标创建短期、一次性、tenant-scoped 意图；服务端生成文件身份和内部存储身份。
2. **有界代理上传**：客户端把一个文件流交给同源核心入口；核心在数据库事务外流式写入隔离 quarantine，强制总字节、耗时、空闲和并发上限。
3. **完成与完整性核对**：上传完成后按 exact object identity 核对存在性、实际大小和受支持的完整性值；客户端声明值不能单独证明对象正确。
4. **恶意内容与类型检查**：文件在 quarantine 中由受控 worker 检查；只有确定 `CLEAN` 且类型符合核心 allowlist 的不可变对象才能发布为可下载附件。
5. **目标附件列表与详情**：只从一个已经重新授权的业务目标读取有界元数据；待上传、待检查、被拒绝和删除中的对象不向普通读者显示。
6. **受控流式下载**：每次请求重新验证 human caller、活动成员、文件能力、目标记录当前可见性、对象状态和存储 readiness；核心同源代理字节，不重定向到 provider URL。
7. **软删除与确认清理**：删除先立即关闭新下载，再由 durable worker 删除 exact stored object；只有适配器确认同一对象身份不再可读后才进入最终删除状态。
8. **过期与补偿**：未完成上传、长期 quarantine、扫描失败、孤儿存储对象和删除重试均有有界 lease、deadline、重试和人工可诊断状态。

### 4.2 首期输入与输出边界

客户端可声明的值仅包括有界展示文件名、声明媒体类型、预期字节数和受支持的完整性算法/值。它不能提交 tenant、actor、owner、bucket、object key、version ID、endpoint、provider、扫描结论、存储状态、任意 metadata map 或下载 header。

展示文件名按 Unicode 码点与 UTF-8 bytes 双重限长，拒绝控制字符、双向控制符、NUL、路径分隔语义、`.`/`..` 路径段和仅空白值。原始名称只用于受控显示；存储 key 永远由服务端生成，下载 `Content-Disposition` 由服务端安全编码，不能把原始名称直接拼入路径或响应头。

声明 `Content-Type`、扩展名和文件签名都不是单独真相。核心只发布固定 allowlist 中、由类型识别和内容检查共同支持的类型；声明、扩展和检测结果冲突时失败关闭。B24-S 已把 storage absolute ceiling 固定为 64 MiB、每记录 active attachments 固定为 100，并要求 production tenant/global quota 以正的部署 policy 进入 readiness；最终有效单文件上限取 storage ceiling、B24-I scanner/type profile、tenant policy 和 adapter ceiling 的最小值。媒体目录、检查侧 ceiling 和 retention 仍由 B24-I 与后续组合证明，不能由 S3 理论容量或本地 free-space 猜测。

公共元数据只考虑：opaque file ID、受控展示名、规范媒体类型、实际大小、上传成员、创建/就绪时间和版本。对象 key、bucket、provider version、checksum、扫描器、签名版本、拒绝原因细节和删除证据仅供内部正确性与安全审计，不进入普通附件响应。

### 4.3 明确不纳入

- 文件夹、标签、收藏、共享空间、文件复制、跨记录复用和个人文件库；
- 匿名或外部链接、密码分享、邮件附件、IM 附件和跨 tenant 发送；
- 覆盖写、原地编辑、协同编辑、版本对比、签入签出和 Office 在线预览；
- ZIP/RAR/7z 等通用归档上传与服务端解压，或者客户端指定解压路径；
- HTML/SVG 的浏览器内联执行、脚本、可执行文件、安装包和宏启用文档；
- 任意 `Content-Type`、任意扩展名、任意对象 metadata 或租户自定义扫描脚本；
- 秒传、跨 tenant 内容寻址去重、hash 搜索或以 checksum 暴露“系统是否已有此文件”；
- 远程 URL 导入、FTP、WebDAV、S3 兼容凭据代理、桌面同步和离线缓存；
- OCR、转写、缩略图、PDF 页面渲染、内容摘要、AI 解析和全文搜索；
- 法律保全、电子签章、WORM 合规、归档期限自动推断和不可撤销物理销毁声明。

## 5. 文件对象与附件关系契约

### 5.1 身份、不可变字节与单一目标

文件对象使用服务端生成的 opaque identity；内部 storage locator 由受控 adapter 从 tenant 分区、文件身份和随机材料生成。展示文件名、业务 ID、tenant ID、actor ID、邮箱、手机号或客户名称都不能成为对象 key 的原始组成部分。

一个上传意图从创建时起绑定 exact `(tenant, targetType, targetId, uploaderMember)`，不能改绑。首期一个完成文件对象最多有一个当前附件目标，不能通过添加第二条 link 达到共享或绕过另一个对象的数据范围。要把相同本地文件附到第二条记录，必须重新创建意图并独立授权、上传、检查和保留。

完成后的字节不可覆盖。显示名称变化如果允许，只更新版本化元数据；内容变化创建新的文件对象，并按目标域明确的替换操作切换关系。对象存储若允许同 key 覆盖，B24 adapter 仍必须用条件写、版本 identity 或随机 immutable key 防止旧 grant/重试改写已经检查过的对象。

### 5.2 正交状态

实现前契约不能用一个含混的 `status` 同时表示对象是否存在、是否安全和是否可访问。至少保留三条正交事实：

| 维度 | 最小语义 | 失败关闭规则 |
| --- | --- | --- |
| byte state | `ABSENT`、`UPLOADING`、`STORED`、`REMOVING`、`REMOVED`、`UNKNOWN` | 只有 exact object 已完整核对才是 `STORED`；超时或未知不推断不存在 |
| inspection state | `NOT_RUN`、`PENDING`、`CLEAN`、`REJECTED`、`INDETERMINATE` | 引擎不可用、超限、未知格式或结果丢失都不是 `CLEAN` |
| publication state | `PRIVATE`、`AVAILABLE`、`WITHDRAWN`、`DELETED` | 只有 `STORED + CLEAN + active target link` 可进入 `AVAILABLE` |

普通列表和下载只接受 `AVAILABLE`。创建、上传和检查状态只对上传者或具有后续专用诊断能力的管理员显示安全摘要；拒绝原因不能泄露扫描器规则、内部路径、签名、provider error 或文件内容。

### 5.3 关系与生命周期

附件关系必须保存 tenant、文件身份、目标类型/ID、创建者、创建时间和版本，并由数据库保证同一文件只有一个当前目标。删除关系与关闭下载在一个本地事务中完成；对象字节的外部删除是后续 durable effect，不能把一个普通 after-commit listener 描述为可靠删除。

目标业务记录软删除或 caller 失权时，附件立即不可读，但这不等于对象字节已经删除。每个接入域必须提供明确的生命周期策略：跟随目标删除、保留到固定 deadline、还是由高权限流程先解除引用。不能靠“列表查不到”长期遗留无 owner 的对象，也不能在尚有活动关系时由通用清理器删除字节。

## 6. 上传、检查与下载流程

### 6.1 上传顺序

推荐的首期代理上传顺序固定为：

1. 创建意图的短事务验证 `AuthenticatedCaller`、活动成员、独立文件写能力、目标记录当前写权限、类型/大小声明和 tenant 配额，保存不可改绑的意图；
2. 上传请求在短事务内重新授权并取得仅限本次调用栈消费的内部写句柄，随后退出事务；
3. `infra/rest` 把受限请求流经 application typed stream 传给 storage port，执行 deadline、idle、byte 和并发限制；不把 `MultipartFile`、Servlet、SDK request 或完整 byte array 传入 domain；
4. 流成功结束后，在新的短事务中按意图版本记录 adapter 返回的 exact object identity、实际大小和完整性证据，并把检查任务与 quarantine 状态原子保存；
5. 若进程在存储成功和本地确认之间崩溃，reconciler 只按该意图预先分配的 exact locator 核对或清理，不能 list 整个 bucket 猜 owner；
6. 检查 worker 在无数据库事务的情况下读取 quarantine bytes 并调用检查器，再用文件版本和 inspection attempt 原子提交受控结论；并发重试只能确认同一不可变对象；
7. 只有提交 `CLEAN` 后才发布关系。任何 mismatch、超限、取消、扫描未知或过期都保持不可下载并进入拒绝/补偿路径。

上传 HTTP 成功只表示字节接收完成，不代表附件已经可用。公共结果必须明确区分 `ACCEPTED_FOR_INSPECTION` 与最终 `AVAILABLE`；客户端轮询只读取安全状态，不返回内部工作队列或 provider 信息。

### 6.2 检查规则

- 文件类型检测、恶意内容检查和必要的容器限制都在核心 allowlist 下执行；客户端 header、扩展名和对象存储 metadata 只作声明；
- 检查器必须支持流式、有界、可取消读取。`clean` 结论绑定文件 identity、实际大小、完整性值、引擎/规则目录版本和 attempt；不能把对旧 bytes 的结果应用到被覆盖对象；
- scanner 超时、签名过旧、内容过大、递归/解压上限、协议异常或未知返回都产生 `INDETERMINATE`，不会 fail open；
- 命中结论只保存受控分类和必要审计，普通 API 不返回恶意软件名称、原始 scanner 文本或命中的内部规则；
- 被拒绝对象永不进入 ready 区，也不能由管理员普通下载“检查一下”。安全取证若未来需要，必须使用独立隔离主体和审计边界；
- 测试只使用无害 EICAR 类协议 canary 或完全合成的检查响应时，必须避免把真实恶意二进制放入仓库历史。

B24-I 已在 [内容检查、类型识别与 quarantine 契约](docs/B24-I-content-inspection.md)中闭合两个原生模型：受控 `INSTREAM` 类流式引擎使用可 fenced 的精确 engine/catalog generation；exact-version 对象事件类托管引擎使用显式较弱的 managed-definition channel evidence，不能伪造逐次签名版本。两者都先把 native 结果映射成受控 scanner/type evidence，再由核心归一 `CLEAN/REJECTED/INDETERMINATE`；object tag、provider `OK`、声明类型、扩展名或 `application/octet-stream` 均不能单独发布。

prefix/magic 类型检测只读取有界前缀；任何需要打开 ZIP/OLE/XML 等容器的深度 detector 都必须在独立最小权限进程中执行，并对 CPU、memory、临时 bytes、条目数、嵌套、展开比例和 deadline 失败关闭。quarantine 同时要求存储/IAM/进程访问隔离，不能仅靠数据库状态命名。B24-SPLIT 已把首个 allowlist 固定为 PDF/PNG/JPEG、public ceiling 固定为 16 MiB；具体生产 tenant/global 配额与时间限制仍须在所选 build 和部署上用正值 manifest 证明。

### 6.3 下载规则

每次下载先在短只读事务中重新验证：human caller、当前活动成员、文件读取能力、目标业务记录当前可见性、附件版本、`AVAILABLE`、storage readiness 和删除 deadline。实际 storage I/O 在事务外执行；授权结果不能跨请求缓存为 bearer。

首期下载由核心同源代理，并至少设置安全的 `Content-Disposition: attachment`、`X-Content-Type-Options: nosniff` 和私有/no-store 缓存策略。对象 key、bucket、provider URL、签名 query、认证 header、storage version 和 checksum 不返回浏览器。完整流不能进入内存聚合或临时 webroot；client abort、deadline、byte limit 和上游异常必须取消并关闭 storage handle。

B24-S 已把 single byte range 冻结为独立 storage capability：S3 versioned 与隔离本地 profile 都能通过 exact version/seekable positional read 实现，但每个真实 adapter build 必须单独证明。`UNSUPPORTED/NOT_READY`、multi-range 或非法 range 都在 adapter open 前失败，不能回退为完整下载；每个合法 range 仍按 RFC 语义重新授权。

## 7. 安全、tenant 与可观测性边界

- 所有首期用例只接受 `AuthenticatedCaller`。B22 `AuthenticatedConnector`、公开表单提交者、匿名公告读者或对象存储 callback 都不能被合成为 human caller；
- tenant 只从 caller 与目标业务记录的当前守卫派生。普通 body/path 不能切换 tenant、storage binding、bucket、provider、扫描器、保留策略或 KMS key；
- 每个 repository、job、locator、quota 和 storage operation 在 tenant 内分区；对象 key 即使随机也不能成为唯一 tenant 隔离控制；
- 文件能力与目标域能力取交集。tenant owner/管理员不会仅凭身份自动获得隐藏业务记录的附件读取；
- 文件名、媒体内容、checksum、object locator、下载 URL、扫描文本、业务目标名称和原始请求不得进入日志、trace、metric 标签、普通事件或 Activity；可观测性只记录本地操作 ID、受控状态/大小桶、耗时和安全错误类别；
- checksum 只用于同一 tenant、同一对象的完整性绑定，不用于跨 tenant 去重、查重接口或存在性提示；
- quarantine 与 ready 对象均禁止公共读和目录 listing，运行身份按写入、读取、删除和检查职责最小授权；生产对象存储不得使用 webroot 或应用可执行目录；
- 存储/检查不可用只影响 B24。普通 CRM 记录的创建、更新和读取继续工作，且不能因 Redis 或文件 adapter 缺失改变既有正确性。

## 8. 可靠性、配额、保留与确认删除

### 8.1 外部存储不是本地事务参与者

对象 PUT、GET、scan 和 DELETE 都发生在 MySQL 事务之外。B24 使用版本化状态、durable job/lease、稳定 attempt identity 和 exact locator 对账，不使用分布式事务，也不把一次 HTTP 2xx 当作整个用例原子成功。

检查是只读、可重复 effect：进程在检查完成后、本地结果提交前崩溃，可以对同一不可变对象重试。删除是有外部副作用的 effect：必须保存稳定 deletion attempt，幂等调用 exact object identity，并区分已请求、provider 接受、确认不可读和最终本地 tombstone。

对象存储原生事件若被使用，只是至少一次的唤醒信号，不能直接宣布上传、检查或删除完成。核心仍按 exact intent/object/version 验证；重复、乱序、未知和跨 tenant 事件失败关闭。若使用 callback，必须另行设计独立 workload authentication，不能信任 body 中的 tenant 或 actor。

### 8.2 有界资源

实现批次必须同时执行以下已冻结或待交集边界：

- B24-S 已固定声明/实际 bytes 为 `1..67,108,864`、storage buffer high-water 为 64 KiB、每记录 active attachments 为 100；上传总时长、idle timeout 和每 caller/tenant 并发流必须有正的部署数值；
- 每 tenant 未完成意图数、quarantine bytes、ready bytes、global/process reserve 和 deletion control reserve 必须由部署 policy 给出正值并进入 storage readiness；本地 `usableSpace` 或 provider 理论容量不能替代；
- scanner 全局/tenant 并发、单次读取上限、排队 deadline、lease 时长和 retry 次数；
- 下载全局/tenant 并发、单流时长和 backpressure；single-range capability/负向语义已由 B24-S 冻结；
- 未完成上传、检查异常、被拒绝对象、已解除关系对象和删除 tombstone 的最长保留。

所有配额由服务端目录和部署政策取最严值。未知 tenant、伪造 file ID 或失败上传不能创建无界缓存/队列分区，也不能驱逐仍活动 tenant 的保护状态。

### 8.3 删除不是 `DELETE 200`

关系删除先使附件不可下载，再调度 exact stored object 删除。对于启用 versioning、复制、对象锁、生命周期或备份的存储，简单 delete 可能只创建 delete marker，不能宣称 bytes 已永久消失。adapter readiness 必须说明能否按 exact version 删除、确认在线不可读、处理非当前版本、过期 multipart、复制品和备份保留。

B24 首期最多宣称“核心在线访问已关闭并且受控主存储对象已确认不可读”。物理介质擦除、跨区域副本、不可变备份和法律保全需要部署策略与独立证据；公共 API 不返回虚假的 `physicallyDestroyed=true`。

## 9. 模块与发布边界

当前 MIT 仓库后续可以包含：

- `api` 中受控上传意图、状态、附件元数据和下载请求契约；不包含 URL、path、bucket、header map、scanner DTO 或任意 metadata；
- `domain` 中 file identity、不可变内容证据、正交状态、附件目标和生命周期行为；
- `application` 中 caller/target 复核、上传/检查/下载/删除编排、typed storage/inspection ports、job lease 和稳定错误；
- `infra/persistence` 中 tenant-scoped 元数据、唯一关系、任务租约、版本、deadline 和 Flyway；
- `infra/rest` 中流式 body/range/response header 边界，不把 Servlet multipart 类型传入 application/domain；
- `infra/external` 中禁用适配器、确定性合成 storage/scanner，以及经 B24-S 选择后的可选参考 adapter；
- `start` 中默认禁用生产文件能力，普通 CRM 在未配置 storage/scanner 时仍可启动和测试。

私有部署负责真实 bucket、endpoint、TLS、IAM、KMS、扫描引擎、签名更新、网络隔离、容量、备份和删除政策。私有 adapter 只能实现 application 端口，不得读取业务 mapper/PO、伪造 caller、直接创建 attachment row，或把 provider callback 当成业务授权。

## 10. 实现前契约测试门槛

至少用一个 S3-like immutable/versioned 对象存储模型和一个隔离本地/文件流模型，再组合一个 clean/rejected/indeterminate 检查器 fixture，证明：

1. storage、scanner 或部署政策缺失时，文件能力明确不可用且既有 CRM API 不受影响；
2. caller 无效、成员失活、缺少文件能力、目标业务记录不可写/不可读或跨 tenant 时，在创建句柄或打开对象流前拒绝；
3. 客户端文件名、路径、媒体类型、大小、checksum、tenant、target、object key 和状态篡改不能改变服务端绑定；
4. 空流、超限、慢流、提前断开、重复上传、complete-before-bytes、同意图不同内容和覆盖现有 key 都失败关闭；
5. provider 成功但本地确认前崩溃、检查后提交前崩溃、发布并发删除和删除确认前崩溃都可按同一 identity 收敛；
6. 声明类型、扩展和检测类型不一致，scanner timeout/limit/signature stale/unknown，均不能产生 `AVAILABLE`；
7. 待检查、被拒绝、indeterminate、withdrawn 和 deleting 对象无法通过列表、详情、下载、range、旧句柄或 provider URL 访问；
8. 下载每次重新应用目标域当前数据范围；失权、目标软删除、任务参与变化和附件删除不让隐藏文件占页或泄露文件名/大小；
9. 两个 tenant 即使使用相同展示名、bytes 或 checksum，也不能共享 locator、检查状态、quota、下载、删除或存在性提示；
10. MySQL 唯一键和版本仲裁同一文件的单一目标、重复 finalize、发布/删除和 job lease 竞态，不依赖 JVM 先查后写；
11. 文件 bytes、展示名、checksum、object locator、scanner raw message、目标名称和禁止 canary 不进入日志、trace、metric、普通事件、Activity、业务自定义值或 Redis；展示名、checksum 与 locator 只允许进入各自专用、tenant-scoped metadata/audit 字段；
12. 删除先关闭授权，versioned store 的 delete marker 不被误报为永久删除，只有 exact online absence 证据才完成受控主存储删除；
13. 过期上传、quarantine、拒绝对象和解除关系对象被有界清理，清理器不会越过活动 link 或另一个 tenant；
14. local、S3-like 与 synthetic scanner 运行同一 provider-neutral oracle，adapter 不能修改预期状态或跳过负向场景。

一旦实现数据库、业务记录守卫、对象存储、扫描 worker、下载流或清理任务，必须运行 Docker/MySQL 集成套件；真实 adapter conformance 还必须在不使用真实客户文件、恶意样本、生产 bucket 或长期凭据的隔离环境执行。

## 11. 任务拆分与依赖

### 11.1 设计与 fixture 前置任务

| 任务 | 状态 | 交付 | 依赖 | 完成门槛 |
| --- | --- | --- | --- | --- |
| B24-S | 已完成 | [S3-like/versioned 与隔离本地存储的 typed contract、readiness、失败和确认删除模型](docs/B24-S-storage-readiness.md) `B24-S/1` | 本文 | 两个模型映射到同一 exact-generation contract；single range 独立 capability，multipart 首期 `UNSUPPORTED` |
| B24-A | 已完成 | [业务附件授权、目标关系与父生命周期契约](docs/B24-A-attachment-access.md)：`files:read/write`、Work + 8 CRM target、不可改绑单一关系、typed parent 证明、锁序、OA/B05 边界与 `A-01..A-14` oracle | B24-S/I | 没有通用 guard 绕过目标域、隐藏记录探测或悬空 owner |
| B24-I | 已完成 | [对账流式恶意内容检查、类型识别、定义证据、超限/未知、quarantine 和检查器隔离](docs/B24-I-content-inspection.md) | 本文 | `STREAMING_ENGINE_V1` 与 `OBJECT_EVENT_MANAGED_V1` 可运行同一 `I-01..I-14` 三态 oracle，且 pinned/managed definitions 不伪装成同一证据等级 |
| B24-X | 已完成 | [双 tenant、MySQL/storage crash、扫描、发布、range、失权和确认删除组合 fixture](docs/B24-X-composed-fixture.md) `B24-X/1` | B24-S/A/I | `X-01..X-08` 覆盖第 10 节 oracle，adapter 与核心判定独立 |
| B24-X-M | 已完成 | [可重启的双 tenant storage/inspection/authorization 独立模型](docs/B24-X-model-fixture.md) | B24-X | `XS/XM/XL` 执行 `X-01..X-08`，report version 为 `B24-X/1`、execution layer 为 `MODEL` |
| B24-SPLIT | 已完成 | [正式冻结 `B24a/b/c` 的 Java、HTTP API、SQL/Flyway、worker 和验收边界](docs/B24-SPLIT-implementation-boundaries.md) | B24-X-M | `V59/V60/V61`、Work/八 CRM/FILE+OA 顺序与每批 gate 已确定；不以模拟器通过补齐真实部署 readiness |

B24-S、B24-I、B24-A 与 B24-X 均已闭合。B24-X 以 `XS/XM/XL` 兼容 lanes、Work/CRM target driver、独立 core oracle 和统一 crash 矩阵组合执行 `S-01..S-14`、`I-01..I-14` 与 `A-01..A-14`；synthetic adapter 只能报告 native observation，不能声明 expected state。

### 11.2 正式实现切片

以下切片已经由 [B24-SPLIT](docs/B24-SPLIT-implementation-boundaries.md) 正式冻结：

1. **B24a 文件对象内核与 Work Task 纵切**：固定 Java/domain/application/port 名称、target-first `/api/v1/attachment-targets/...` API、`V59__create_attachment_foundation.sql`、六类 durable worker、secure-local + bounded-magic + streamed-engine 首个 profile，以及 Work lane 的 MySQL/crash/conformance gate；继续不启用 B05 `FILE`。
2. **B24b CRM 业务附件扩展**：不改变 B24a 的 file/API/job 内核，只通过 `V60__add_crm_attachment_targets.sql` 与八个显式 target adapter 一次性接入 Customer、Contact、Lead、Opportunity、Contract、Invoice、Receivable 与 ReceivablePlan 的当前授权、typed parent lock、软删除联动和独立 target report。
3. **B24c 受治理 FILE 字段与 OA 提交文件**：通过结构化 `fileFields`、创建前 file-field draft、后续 `V62__create_file_field_and_oa_file_binding.sql`、专用 B11/stage snapshot 表和 OA 单事务冻结启用八类 CRM/OA 的 FILE；仍不把 file ID 写入 scalar custom values，也不把 OA Request 变成通用提交后附件目标。

B24-A 已确认 Work Task 可作为第一个真实 vertical slice：附件只继承任务自身的参与/管理可见性，不继承任务可选 CRM link；它不修改任务版本或工作台投影，且任何上传/附件历史都像 comment history 一样阻止任务软删除。因此 B24-SPLIT 不得再退回无目标“我的文件”库。

### 11.3 B24-S 存储契约结论

[B24-S 对象存储映射、readiness 与失败契约](docs/B24-S-storage-readiness.md)把 S3 `(key, versionId)` 和隔离本地 once-only locator/descriptor generation 映射为同一不可变 `StoredObjectIdentity`；共同 operation 分离 allocate、单流 store、exact probe、full/single-range read、stable delete、authoritative absence 与 incomplete cleanup。canonical evidence 固定为 full-object SHA-256 与 actual bytes，ETag、mtime、文件 key、客户端声明、delete 2xx、current-key 404 和 delete marker 都不能代替。

首期 storage absolute ceiling 为 64 MiB、每流 buffer high-water 为 64 KiB、每记录最多 100 个 active attachments；production tenant/global quota 与 deletion reserve 必须有部署数值，否则 operation `NOT_READY`。single range 是 build/binding 级独立 capability；两个 profile 均可实现，但不通过 conformance 时必须在 open 前拒绝。multipart/断点续传固定 `UNSUPPORTED`，S3 SDK 自动 multipart 调用必须为零，未来需新增 durable upload ID/part/complete/abort/crash contract version。

S3 profile 要求 general-purpose bucket versioning steady enabled、conditional no-overwrite、非空 exact versionId、SHA-256、exact-version read/range/delete、public/ACL denial、encryption、Object Lock/replica/backup inventory 与受控 absence procedure；普通 versioned DELETE 只加 marker，不能完成 B24 删除。本地 profile 要求专用非 webroot POSIX-like root、预配置 shard、`SecureDirectoryStream`、`NOFOLLOW_LINKS`、`CREATE_NEW`、owner-only permissions、content-last descriptor evidence、seekable range、force/delete crash proof；node-local 默认只具 single-node dev/test 等级。`S-01` 至 `S-14` 必须在独立 model、双 tenant durable simulator 和当前真实 backend/build 三层运行，设计文档或兼容品牌不能产生 production readiness。

### 11.4 B24-X 组合 fixture 结论

[B24-X 安全文件与业务附件组合 fixture](docs/B24-X-composed-fixture.md)固定 `X-01..X-08`：先保存不可改绑 intent/planned locator，再组合单流 store/finalize、双 inspection profile、目标根锁发布、target-first full/range、当前失权、父生命周期、durable exact delete 和 authoritative absence。`CLEAN`、storage 可读或 delete effect 都不能单独推进公共状态；MySQL commit/rollback/ack-loss 与每个 storage/inspection effect 之间都有显式 crash cut。

fixture 用 Work Task 与 Customer 作为双 tenant 主 trace，并要求 B24b 的其余 CRM target 复用完整授权/父锁/删除子矩阵。versioned S3-like storage 分别组合 streamed 与 managed-event inspection，secure-local 组合 streamed inspection；没有真实 managed exact-version service 的 local lane 必须报告 `UNSUPPORTED/NOT_READY`，不能静默跳过。full-only 与 native single-range 都执行负向 range，成功 range 还断言 exact offset 与 upstream byte count，禁止读全对象后裁剪。

`B24-X/1` 已在 `infra/external` test source 实现第一层独立 model：分离 target/core/storage/inspection stores，以可重建 process、虚拟时钟、native observation 和 safe digest/counter report 执行 `X-01..X-08`。它仍未实现 durable MySQL/Testcontainers/HTTP composition、任何 B24 runtime、schema 或真实 adapter，也不能把合成 lane 通过升级成 production readiness；B24-SPLIT 只完成实现边界选择，runtime 与证据仍由 B24a/b/c 实现批次承担。实现与验证明细见 [B24-X/1 model fixture](docs/B24-X-model-fixture.md)。

## 12. 外部事实依据

- [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html)要求扩展 allowlist、不要信任 `Content-Type`、服务端生成存储名、限制大小、隔离 webroot、重新授权并执行恶意内容检查，支持 B24 的 quarantine 与多重验证边界。
- [Amazon S3 multipart upload 与 checksum 文档](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html)说明 multipart 完成和完整性是独立步骤；[S3 integrity 文档](https://docs.aws.amazon.com/AmazonS3/latest/userguide/checking-object-integrity-upload.html)还明确 multipart `ETag` 不是完整对象 MD5，支持 typed checksum 而不是把 ETag 当通用 digest。
- [Amazon S3 presigned URL 文档](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)说明 URL 以签发主体权限提供限时访问，并且上传到既有 key 会覆盖对象，支持把直传、覆盖防护和 URL 泄漏留给独立 profile。
- [Amazon S3 Event Notifications](https://docs.aws.amazon.com/AmazonS3/latest/userguide/EventNotifications.html)明确通知至少投递一次，支持只把事件当唤醒信号并由核心按 intent/object/version 幂等核对。
- [Amazon S3 versioned object deletion](https://docs.aws.amazon.com/AmazonS3/latest/userguide/DeletingObjectVersions.html)说明普通 DELETE 可只增加 delete marker，保留非当前版本，支持 B24 区分访问关闭、在线对象删除和永久销毁声明。
- [ClamAV `INSTREAM` protocol](https://docs.clamav.net/manual/Usage/ClamdProtocol.html#instream)要求分块流并受 `StreamMaxLength` 约束，说明“扫描器返回超限”不能映射成 clean，也支持 typed、流式、可取消检查端口。
- [Amazon GuardDuty S3 scan result](https://docs.aws.amazon.com/guardduty/latest/ug/monitoring-malware-protection-s3-scans-gdu.html)与 [EventBridge schema](https://docs.aws.amazon.com/guardduty/latest/ug/monitor-with-eventbridge-s3-malware-protection.html)提供 exact-version 对象事件的第二个检查模型：clean/threat 与 unsupported/access-denied/failed 明确分离，结果包含 `versionId`，但不暴露逐次 engine/catalog revision；B24-I 因此把 pinned 与 managed definitions 证据等级显式拆开。
- [Apache Tika content detection](https://tika.apache.org/3.2.1/detection.html)说明 magic、文件名/声明 hint 与 container-aware detection 的差异；[Tika security model](https://tika.apache.org/security-model.html)明确解析不可信文件不是安全边界，支持 B24-I 的有界 prefix detector 与隔离 container detector 分层。
- [RFC 9110 Range Requests](https://www.rfc-editor.org/rfc/rfc9110.html#name-range-requests)与 [RFC 6266 Content-Disposition](https://www.rfc-editor.org/rfc/rfc6266)为 single-range、416 和安全下载文件名提供标准语义；B24-S 已要求每个具体 adapter build 独立证明 native range，未证明时固定失败关闭。

这些资料证明对象存储和扫描器的原生语义并不等于 CRM 附件事务、授权或确认删除；它们不构成 AWS、ClamAV 或任一产品的首期选择，也不替部署方完成数据地域、加密、备份、恶意样本处理或合规评估。

## 13. B24 实现闭合后的剩余边界

- B24-S 选择 AWS S3 general-purpose/versioned 公开语义与 Java 25 secure NIO/POSIX-like 隔离文件系统作为两个 reference profile，但不选择具体 S3-compatible 产品、Java SDK、生产 bucket/root、ClamAV、商业沙箱或 CDR 产品；
- B24a 以 V59 实现 Work Task 纵切、六类 durable worker 与 secure-local + bounded-magic + streamed-engine profile；B24b 以 V60/V61 接入八类 CRM typed parent 和 pool pause；B24c 以 V62/V63/V64 实现八类 CRM 与 OA 的有序 FILE 值、创建前草稿、字段生命周期和 approval/stage 快照；
- target-first API、PDF/PNG/JPEG allowlist、16 MiB public cap、`files:read/write` 交集授权、不可改绑关系与确认删除保持有效；
- tenant/global 容量、并发、worker 时限、检查重试和 retention 使用已冻结的 `atom.files.*` 正值配置面，但 prod 没有可使文件 READY 的回退默认；具体部署值仍必须由同一 storage/detector/scanner build、部署容量和 current conformance manifest 证明；
- Product/ReturnVisit FILE、MarketingForm FILE、公告附件和 OA 提交后协作附件仍在 B24 完成边界之外，后续主体、权限、冻结与迁移边界已由 [B29](batch-B29.md) 规划；外部分享、预览/解析/OCR、S3-like 生产 bean 与 managed scanner 继续后置；
- B24 的默认禁用与真实 adapter readiness 不阻止普通 CRM 独立运行，也不能由合成 fixture 或本验收记录自证生产已 READY。

后续业务实现不再从 B24 内继续编号；剩余业务接缝的选择依据见 [B25 规划](batch-B25.md)，文件覆盖扩展见 [B29 规划](batch-B29.md)。
