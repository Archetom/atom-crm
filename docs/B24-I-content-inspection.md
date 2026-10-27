# B24-I 文件内容检查、类型识别与 quarantine 契约

> 决策日期：2026-07-20
>
> 对账 profiles：ClamAV `clamd INSTREAM` 类受控流式引擎、Amazon GuardDuty Malware Protection for S3 类 exact-version 对象事件引擎，以及 Apache Tika bounded-magic / isolated-container 类型识别模型
>
> 状态：检查语义与双 profile conformance oracle 已闭合；没有 Java 类型、API、表、worker、对象存储、scanner、Tika 依赖、云服务绑定、样本文件或生产 readiness
>
> 输入：[B24 总规划](../batch-B24.md)。B24-S 已冻结 exact storage identity、不可变版本与读取能力；B24-A 已冻结业务目标授权；[B24-X](B24-X-composed-fixture.md) 已组合持久化、崩溃、发布、失权与删除 fixture

## 1. 闭合结论

B24-I 把“文件检查”固定为同一不可变对象上的四份独立证据，而不是一个 scanner 返回的布尔值：

1. storage 已证明的 exact object identity、实际 bytes、完整性值和不可覆盖版本；
2. 受治理 detector 对同一 bytes 给出的规范媒体类型；
3. 恶意内容检查器对同一 bytes 给出的受控结论；
4. 核心 policy 对 detector、scanner、定义新鲜度、媒体 allowlist、资源上限和隔离 readiness 的判定。

只有四者同时成立才产生 `CLEAN`。`CLEAN` 仅表示“在已记录的检查基础与 policy 下未发现威胁并通过类型政策”，不表示文件无害、可内联执行、可预览、可解析或永远安全。

本轮冻结以下决定：

- `CLEAN`、`REJECTED` 与 `INDETERMINATE` 是核心结论，provider 原生 `OK`、tag、finding、HTTP status、退出码或异常都不能直接成为 domain 状态；
- 已知恶意命中、确定的声明/扩展/检测类型冲突或确定不在 allowlist 中是 `REJECTED`；timeout、超限、未知格式、password protection、定义过期、扫描器不可用、协议错误或 identity 不匹配是 `INDETERMINATE`；
- 恶意内容 verdict 与媒体类型 verdict 分开保存。一个 scanner 声称 clean 不能把 `application/octet-stream`、未知容器或类型冲突发布出去；
- 定义证据分成 `PINNED_DEFINITIONS` 与 `MANAGED_DEFINITIONS`。前者保存精确 engine/catalog revision，后者诚实保存托管通道、schema、结果时间和受审更新承诺；不得给后者伪造 `latest`、hash 或虚构签名版本；
- ClamAV 类 profile 使用受限 stream 或 file descriptor，不允许核心传 path；GuardDuty 类 profile 只接受与预分配 exact version 完全一致的受治理事件，object tag 只能辅助 reconcile，不能单独发布；
- prefix/magic 类型识别可以在有界流上执行；需要打开 ZIP/OLE/XML 等容器的深度识别必须进入独立、最小权限、硬资源限制的检查进程。解析器不是安全边界；
- quarantine 是存储/IAM/进程访问隔离，不是数据库里叫 `QUARANTINE` 的字符串。普通应用读取主体、web server、静态目录、业务 API、Redis 和搜索索引都不能读取其 bytes；
- scanner、detector 或部署 policy 未配置时，B24 文件能力明确 `NOT_READY`，现有 CRM API 继续可用；
- fixture 只用生成式 benign bytes、完全合成的威胁响应和禁止值 canary。仓库、CI artifact 和测试日志不保存恶意二进制、真实客户文件或完整 EICAR 内容。

这两种检查 profile 能运行同一核心 oracle，但不因此被宣称为等价产品或已经可用于生产。`OBJECT_EVENT_MANAGED_V1` 的最终可用性还依赖 B24-S 证明 versioned exact object、事件恢复和标签/version 行为。

## 2. 正交词汇与最小证据

### 2.1 检查 subject

一次 attempt 从 durable job 取得以下不可改绑 subject：

| 事实 | 语义 |
| --- | --- |
| tenant/file/content version | 由核心持久化并参与 lease、版本仲裁和结果提交；不能来自 callback body |
| exact storage identity | 由 B24-S typed storage contract 产生；provider bucket/key/path/version 在 adapter 私有边界展开 |
| actual length | storage 完成证明的实际 bytes，不采用客户端声明值 |
| integrity evidence | 同一对象的受支持完整性算法和值；ETag 不自动等于完整内容 digest |
| declared presentation | 已规范化展示名、声明媒体类型和扩展；它们只是冲突检查输入 |
| attempt identity | 同一文件版本、inspection generation 和 job attempt 的稳定 identity |
| deadlines and limits | queue、open、idle、stream、总检查、bytes、container 与并发上限 |
| policy/readiness revision | allowlist、detector、scanner、定义基础、隔离和部署约束的不可变 revision |

subject 不含 `AuthenticatedCaller`、业务目标名称、provider URL、credential、任意 metadata map 或 scanner 原始命令。检查 worker 不能借 job body 切换 tenant、对象、scanner、definition channel 或 policy。

### 2.2 类型识别证据

类型识别结果独立使用：

| 结果 | 含义 |
| --- | --- |
| `IDENTIFIED` | detector 在受治理 catalog 中得到一个规范媒体类型，并说明 `BOUNDED_MAGIC` 或 `ISOLATED_CONTAINER` 证据级别 |
| `UNSUPPORTED` | bytes 被稳定识别，但类型不属于当前允许目录，或所需证据级别高于当前 profile |
| `INDETERMINATE` | 未知、歧义、空证据、parser crash/timeout/limit、catalog drift 或读取未完成 |

证据至少绑定 detector contract/build、catalog revision、evidence level、规范媒体类型、实际 length/integrity、attempt 和完成时间。客户端声明类型、展示名扩展和对象 metadata 可以参加一致性比较，但不能覆盖 detector 输出。

`application/octet-stream` 是未知的安全占位，不是可发布类型。prefix magic 不能区分的容器格式也不能仅凭 `.docx`、`.xlsx`、`.pptx` 或声明 header 通过。B24-SPLIT 只有在媒体 allowlist 明确需要它们时，才可接入已通过隔离 fixture 的 container-aware detector。

### 2.3 恶意内容证据

scanner adapter 只向核心返回以下受控 shape：

| 字段 | 语义 |
| --- | --- |
| verdict | `NO_THREAT_DETECTED`、`THREAT_DETECTED`、`UNSUPPORTED` 或 `FAILED` |
| definition basis | `PINNED_DEFINITIONS` 或 `MANAGED_DEFINITIONS`，见第 5 节 |
| subject evidence | 同一 length、integrity、content version 和 attempt |
| timing | adapter 接收、检查完成/结果产生、核心接收时间；不伪造 provider 未给出的 scan start/end |
| controlled reason | 固定的安全分类；无 threat name、nested path、native error 或 response text |

`NO_THREAT_DETECTED` 只是 clean candidate。只有 subject、类型、定义 freshness、allowlist、limits 和 readiness 全部再次通过核心校验后才成为 `CLEAN`。

### 2.4 核心三态归一

| 核心结论 | 必要条件 | 生命周期语义 |
| --- | --- | --- |
| `CLEAN` | exact immutable subject 一致；类型 `IDENTIFIED` 且允许；scanner `NO_THREAT_DETECTED`；定义与 readiness 未过期；所有流到达规范 EOF 且无 limit/协议异常 | 可与 `byte=STORED`、active link 一起候选发布 |
| `REJECTED` | 确定 threat；或确定的声明/扩展/检测冲突；或稳定识别为不允许类型 | 当前 content version 终态，不因普通重试变 clean；bytes 保持隔离并进入有界清理 |
| `INDETERMINATE` | unknown/unsupported scan、未知/歧义类型、timeout、cancel、limit、stale definitions、读取不完整、identity/config/schema/protocol drift、基础设施错误 | 始终不可发布；可在 deadline 和 attempt 上限内用新 attempt 重试 |

正向 threat evidence 采取 strongest-wins：同一 immutable content version 的后到 clean、重复结果或旧 policy 不能覆盖 `REJECTED`。已提交 `CLEAN` 后若收到同一受治理 generation 的有效 threat 证据，必须先撤销/阻止发布，再进入 `REJECTED` 安全处置；不能继续下载等人工判断。

## 3. Profile 一：`STREAMING_ENGINE_V1`

### 3.1 能力映射

该 profile 代表由核心 worker 主动读取 quarantine bytes，再通过受限协议交给检查引擎的模型。ClamAV `clamd INSTREAM` 是本轮事实依据，不是产品选择。

ClamAV 官方协议把 `INSTREAM` 定义为同一 socket 上的 length-prefixed chunks，以零长度 chunk 结束；总量超过 `StreamMaxLength` 会返回明确错误。`VERSION` 返回程序和数据库版本，`SELFCHECK` 可检查已加载数据库是否 current。`clamd` 原生 TCP 没有加密或认证，因此生产 readiness 不能把一个可被任意网络主体访问的 TCP socket 视为受控 scanner。

固定映射为：

| Native observation | Adapter verdict | 核心候选 |
| --- | --- | --- |
| 完整 `stream: OK`，规范 EOF，actual bytes 与 subject 相等 | `NO_THREAT_DETECTED` | 仅在类型/定义/policy 同时通过后 `CLEAN` |
| 任一 `FOUND` | `THREAT_DETECTED` | `REJECTED`；丢弃 signature/threat name |
| `INSTREAM size limit exceeded` | `UNSUPPORTED` | `INDETERMINATE/SCANNER_LIMIT`，绝不 clean |
| `ERROR`、unknown reply、malformed framing、socket close、timeout、cancel、short/long read | `FAILED` | `INDETERMINATE` |

adapter 不得使用 `SCAN path`、`CONTSCAN path`、shell、glob、URL 或展示文件名。优先使用 Unix domain socket；若部署选择 TCP，必须位于独立受控网络并由外层认证加密代理提供双向身份，scanner 端口仍不能暴露给用户、公共网络或普通应用。

### 3.2 定义版本与 reload fence

单独在扫描前后调用 `VERSION` 不能证明中间没有 database reload。`STREAMING_ENGINE_V1` 的正向 readiness 必须把一个 scanner worker pool 绑定到一个不可变 generation：

1. 构建/启动 generation 时加载已验证 definition bundle；
2. 记录 engine version、database/catalog revision、bundle evidence digest、loaded time 和 policy max age；
3. generation 接受新 attempt 时持有 fence；更新通过启动新 generation、停止旧 generation 接单并等待旧 attempt drain 完成实现；
4. scan result 只引用实际执行它的 generation；generation 过期或 evidence drift 后不能提交 clean；
5. reload、VERSION 不可用、版本格式未知、definition update 失败或 `SELFCHECK` 不可证明时，撤销该 generation 的正向 readiness。

实现可以用等价的进程/容器镜像或 libclamav engine instance 隔离，但必须证明同样的 generation fence。禁止把 wall-clock 附近的两次 health probe 当作逐 attempt 的 catalog 绑定。

### 3.3 流与资源

- core 文件上限、scanner `StreamMaxLength`、scanner 解包/扫描上限和部署 policy 取最严交集；scanner 上限小于已允许文件上限时，该媒体/大小组合 `NOT_READY`，不是在运行时“跳过并假定 clean”；
- 每次 attempt 只读 exact object，从 offset 0 到规范 EOF。detector 若需要第二次读取，必须重新按同一 immutable identity 打开并再次核对 descriptor；不能缓存或复用另一个对象的流；
- worker 自己执行 open/idle/stream/total deadline、byte counter、backpressure 和 cancellation；provider `OK` 不能覆盖本地 short read、over-read 或 deadline；
- connection pool、scanner threads、tenant/process 并发、队列和临时空间都有硬上限。队列满在读取 bytes 前失败关闭，不把文件内容放入内存待处理；
- scanner 断开或 client cancellation 后迟到 reply 被丢弃，handle/slot 只能释放一次，不透明 reopen；durable job 另起 attempt 才能重试。

## 4. Profile 二：`OBJECT_EVENT_MANAGED_V1`

### 4.1 能力映射

该 profile 代表受治理对象版本由托管扫描服务异步检查，核心消费 exact-version 结果事件的模型。Amazon GuardDuty Malware Protection for S3 是本轮事实依据，不是 B24 的对象存储或 scanner 选择。

GuardDuty 对新对象/新版本触发检查，结果事件包含 bucket、object key、ETag、`versionId` 与 `NO_THREATS_FOUND`、`THREATS_FOUND`、`UNSUPPORTED`、`ACCESS_DENIED` 或 `FAILED`。官方同时说明 EventBridge 为至少一次投递；S3 versioning 下不同版本会分别扫描，开始顺序不保证与上传顺序一致。

固定映射为：

| Native result | Adapter verdict | 核心候选 |
| --- | --- | --- |
| `COMPLETED + NO_THREATS_FOUND` | `NO_THREAT_DETECTED` | subject、类型、managed definitions policy 全部通过后才可 `CLEAN` |
| `COMPLETED + THREATS_FOUND` | `THREAT_DETECTED` | `REJECTED`；threat name、nested item path/hash 不进入核心普通状态 |
| `UNSUPPORTED` | `UNSUPPORTED` | `INDETERMINATE` |
| `ACCESS_DENIED` | `FAILED` | `INDETERMINATE` 并撤销相关 operation readiness |
| `FAILED`、unknown status/schema | `FAILED` | `INDETERMINATE`；unknown schema 另触发 drift |

password protection、对象/解包 bytes 超限、文件数/层级/压缩比超限和不支持 storage class 都是 `INDETERMINATE`，不是“未发现威胁”。provider 的 100 GB object ceiling 只是当前服务 ceiling；B24-S 冻结的首期文件上限必须更小或相等，不能把该数字直接变成公共承诺。

### 4.2 exact version 与事件可靠性

正向结果必须同时满足：

- 事件来自部署固定的 account、region、protection plan、bucket/prefix 和 schema；这些 native 值只在私有 adapter 中解析；
- `versionId` 精确等于 B24-S 为该 content version 保存的 immutable storage version。未开启 versioning、version 缺失、`null`、错版本或只凭 key/ETag 关联都不具备 clean 能力；
- 事件 ETag 只作 provider identity corroboration，不冒充 B24 完整性 digest；adapter 还要按 B24-S exact descriptor 核对实际 length/integrity；
- 乱序与重复事件按 `(binding revision, exact object version, inspection generation, safe result digest)` 幂等。另一个版本、旧 protection plan 或旧 binding 的事件不能推进当前文件；
- object tag 是可选 reconcile evidence，不是权威结果。标签写入可能独立失败，tag 也不能绕过核心 durable attempt、版本和 policy；
- result deadline 到达仍无可核对事件时提交 `INDETERMINATE/RESULT_TIMEOUT`。B24-X 已冻结进程崩溃、事件重复和 tag failure 后的恢复路径；在真实 durable/adapter lanes 运行以前，此 profile 仍只有 inspection mapping readiness，没有 B24 端到端 production readiness。

对象创建自动扫描与 on-demand exact-version 扫描不能在同一 profile 中含混混用。若未来选择 on-demand API，它的空 `200` 只表示请求被接受；缺少 provider idempotency/result identity 的 crash 边界必须由独立 contract version 和 fixture 闭合。

### 4.3 托管定义证据

GuardDuty 文档说明其托管 engine 的签名列表持续按 15 分钟更新，但结果事件不返回逐次扫描的精确 engine/catalog revision。B24 不伪造该值，而使用 `MANAGED_DEFINITIONS`：

- 固定 service/profile、region、result schema、保护计划/binding revision 的安全 digest；
- 固定官方声明的最大更新 cadence、部署审查时间、有限 `validUntil` 与文档/contract digest；
- 保存 provider result time、core received time 和 inspection attempt；不把 EventBridge envelope time 重命名为 provider 未声明的 scan start/end；
- tenant/deployment policy 必须显式接受 managed definitions 证据等级。需要精确 signature revision 的部署将该 profile 设为 `NOT_READY`；
- 更新 cadence、schema、service behavior、region/binding、数据处理政策或 conformance evidence 缺失/过期/漂移时，新的 clean candidate 失败关闭。

`MANAGED_DEFINITIONS` 与 `PINNED_DEFINITIONS` 是不同审计能力，但在 policy 明确接受时都可进入同一核心三态。公共 API 不返回 service 名、region、plan、定义 cadence 或 evidence digest。

## 5. Readiness、定义 freshness 与结果提交

### 5.1 最小 readiness snapshot

每个 deployment inspection binding 的正向 snapshot 至少固定：

| 维度 | 必需证据 |
| --- | --- |
| contract/build | B24 inspection contract version、不可变 adapter build、deployment class |
| scanner profile | `STREAMING_ENGINE_V1` 或 `OBJECT_EVENT_MANAGED_V1` 及 native schema/protocol capability |
| detector profile | detector build/catalog、`BOUNDED_MAGIC`/`ISOLATED_CONTAINER` 能力和支持类型集合 |
| definitions | pinned engine/catalog generation，或 managed channel/cadence/policy evidence；二者不可混填 |
| subject binding | exact version、length、integrity 和 immutable-read 能力；由 B24-S 提供 |
| limits | bytes、queue/open/idle/stream/total、container、temp、memory、CPU、并发、lease、attempt 与 deadline 的正值 |
| isolation | network、process/container、filesystem/IAM、definition updater、temporary storage、egress、operator access 与 cleanup evidence |
| policy | allowlist、声明/扩展/检测一致性、managed-basis acceptance、freshness 与 retention revision |
| conformance | 本文件 scenario set、通过的 profile/build/config digest 与有限有效期 |

任一值缺失、非正、互相矛盾、过期或来自普通请求时，inspection operation `NOT_READY`。一个 scanner `PING`、bucket protection plan `ACTIVE` 或一次合成 clean 不能单独生成 readiness。

### 5.2 attempt-time 与 commit-time 双检

worker 在打开 storage stream/触发对象扫描前检查一次 snapshot；提交结果的短事务还必须按文件版本和 attempt fence 再检查：

- subject 仍是同一 content version，未被 withdraw/delete，job lease/attempt 仍有效；
- detector/scanner result 都绑定同一 length、integrity 和 inspection generation；
- pinned generation 未过 max age，或 managed evidence/policy 仍有效；
- allowlist、detector catalog、scanner binding 和 isolation revision 未改变；
- 该文件尚未有 stronger terminal result。

commit-time evidence 过期时结果是 `INDETERMINATE/READINESS_EXPIRED`，不是沿用 attempt 开始时的 clean。确定的 threat 可以在 freshness 后到时继续收窄为 `REJECTED`，因为过期定义不使正向命中变安全；它仍不能泄露 native threat detail。

## 6. 类型 allowlist 与 detector 隔离

### 6.1 `BOUNDED_MAGIC`

Apache Tika `MimeTypes` 文档说明其 stream 检测只读取 `getMinLength()` 所需的有界前缀，并结合 magic 与可选 metadata hints。B24 使用这一模型时：

- detector catalog 由部署固定，客户端不能上传 `custom-mimetypes.xml`、parser 列表、override type 或 detector 参数；
- 展示文件名和声明 `Content-Type` 只用于一致性检查。实现必须保留纯 magic 结果，不能让 user override 把未知 bytes 改成允许类型；
- 读取前缀仍受 exact identity、byte counter、timeout 与 close 约束；prefix read 失败或过短是 `INDETERMINATE`；
- 只把 B24-SPLIT 明确 allowlist 且可由该 evidence level 稳定识别的类型标为 supported；未知 text、polyglot 或公共 container 不猜测。

### 6.2 `ISOLATED_CONTAINER`

Tika 官方安全模型明确指出解析不可信文件可能 crash、hang 或接管解析进程，mime detection 也容易出错，Tika 不是安全边界。任何需要 container-aware detector/parser 的 profile 必须：

- 与 CRM web/start 进程和业务数据库 credential 分离，以无特权 uid、只读 exact-object handle、独立临时目录运行；
- 默认无外网、无任意 URL/file fetch、无请求驱动 parser/config/plugin、无 shell/office/renderer；定义更新走独立受控通道；
- 对 CPU、memory、pids、open files、temp bytes、container entries、nesting、expanded bytes/ratio、XML depth、wall/idle time 和 output metadata 设硬上限；
- limit 达到、parser exception、unsupported encryption、ambiguous/polyglot 或进程被 OOM/kill 时返回 `INDETERMINATE`；不能返回部分识别为成功；
- 只返回规范 media type 与受控 evidence，丢弃文本、宏、embedded names、作者、路径、URL、parser warning 和原始 metadata。

B24 首期不因接入 container detector 自动允许 archive、宏文档、HTML 或 SVG。类型识别能力和媒体发布 allowlist 是两道独立门。

## 7. Quarantine 与职责隔离

### 7.1 访问面

quarantine 至少满足：

- 不在 webroot、classpath、应用可执行目录、共享临时目录或公共 bucket/prefix；关闭 public ACL/policy、目录 listing 和匿名/浏览器直接访问；
- 上传主体只写入预分配 exact identity，不能读取、覆盖、列举或自行标记 clean；
- detector/scanner 主体只读当前 lease 授予的 exact object，不能列 tenant、创建业务 link、发布、修改文件元数据或读取 MySQL 业务表；
- publication worker 只提交核心状态/关系，不能伪造 scanner result；若 B24-S 选择 copy-to-ready，copy 仍按 exact immutable source 和新 identity 核对，不能由 tag/rename 偷渡；
- cleanup/delete 主体只消费 durable exact-object command，并保留独立 deletion confirmation；不能因 scan rejection 直接递归删除目录或另一个版本；
- 普通下载主体只读取核心已授权 `AVAILABLE` 对象，永远没有 quarantine-wide read/list 权限。

storage provider 的 object tag、prefix 或 filesystem directory 可以参与防御，但数据库核心状态仍是发布授权源。任何一个单独控制失效都不应让待检查 bytes 进入普通下载。

### 7.2 检查器数据边界

本地引擎的 socket、definition bundle、temporary file、engine cache 和 crash dump；托管引擎的 account、region、bucket/prefix、IAM/KMS、plan、event bus、finding 和 object tag；detector 的 catalog、parser warning 和 temporary artifacts 均属私有部署边界。它们不能成为 public Request/Response、domain enum、普通 event、Activity、Redis value、日志/trace/metric label 或业务自定义值。

部署使用托管 profile 时，还必须单独审查数据地域、临时副本、加密、operator access、service terms、retention 和删除说明。B24-I 只冻结技术映射，不给出法律或合规结论。

## 8. Job、lease、retry 与 crash 语义

- finalize bytes 与创建 inspection job 在一个短本地事务中完成；scanner I/O、detector I/O 和 provider 等待均在 MySQL 事务外；
- job 保存稳定 file/content version、inspection generation、attempt number、not-before、deadline、lease owner/fence、policy/readiness revision 和安全结果摘要；不保存 provider raw response 或内容；
- 一个 attempt 被 lease 后才可打开 exact object。lease expiry 允许新 worker 用新 fence 接管，旧 worker 的迟到结果因 fence/version mismatch 无法提交；
- provider/result 完成后、本地提交前 crash 可以重做同一 immutable content 的检查。streamed profile 使用新 attempt；object-event profile 可消费同一 durable native event。两者都不能修改 bytes；
- `INDETERMINATE` 只有在 retryable 分类、文件 deadline、quarantine retention 和显式 max attempts 内重试；使用有界 backoff 与 tenant/process admission，不在一次上传 HTTP 请求内等待或循环；
- `REJECTED` 不自动重试；`CLEAN` 不因普通定时任务重复扫描。未来 definition update 后的主动 re-scan、false-positive appeal 或已发布对象持续监控属于独立策略批次；
- attempt 上限耗尽、deadline 到达或 readiness 长期缺失保持 `INDETERMINATE` 并进入有界补偿/人工安全诊断，不改成 rejected 或 clean；
- 多副本全局并发/队列若无共享 admission 或静态不重叠预算，只能声明单 replica readiness。不能用本机 semaphore 冒充 tenant/global 上限。

具体 production tenant/global 配额、并发、deadline、attempt、definition max age 与 quarantine retention 数值不由合成 fixture 猜测。B24-SPLIT 已冻结 `atom.files.*` 正值配置面、16 MiB public ceiling 和 prod 无 readiness 默认；每个部署仍必须从 B24-S 文件边界、所选 scanner/detector build、容量和安全政策取最严值，缺失时保持 `NOT_READY`。

## 9. 安全失败矩阵

| 场景 | 核心结果 | 禁止行为 |
| --- | --- | --- |
| 完整 clean scan + allowed identified type + fresh evidence | `CLEAN` | 不能跳过 commit-time fence 或自动生成下载 URL |
| threat 命中 | `REJECTED` | 不返回 threat name、hash、nested path、rule 或原始 message |
| 已识别 disallowed type | `REJECTED` | 不因 scanner clean 发布 |
| 声明/扩展/检测冲突 | `REJECTED` | 不选其中“最宽松”一个，不改名后复用旧 scan |
| unknown/ambiguous type、container evidence 不足 | `INDETERMINATE` | 不把 octet-stream 当 allowlisted binary |
| scanner bytes/解包/层级/ratio limit | `INDETERMINATE` | 不映射 clean 或 harmless |
| password-protected/encrypted content 无法检查 | `INDETERMINATE` | 不因业务用户知道密码而旁路；B24 无密码提交面 |
| timeout、cancel、queue full、OOM、parser crash | `INDETERMINATE` | 不吞异常后发布，不在事务内等待 |
| definition stale/unknown/reloaded mid-attempt | `INDETERMINATE` | 不写 `latest`，不沿用旧 generation clean |
| managed exact version 缺失/错配、old binding/event | `INDETERMINATE` 或丢弃未知事件 | 不只按 key、filename、ETag 或 tag 关联 |
| duplicate equivalent result | no-op | 不增加 publication、attachment 或 cleanup effect |
| clean 后有效 threat 冲突 | 先 withdraw，再 `REJECTED` | 不维持旧下载，不让 clean last-write-wins |
| scanner/detector/policy 未配置 | operation `NOT_READY` | 不影响非 B24 CRM API，不安装 fail-open adapter |
| cross-tenant file/locator/event/attempt | 在任何 byte read 前拒绝 | 不创建另一 tenant 的 queue/quota partition 或 existence hint |

未知 runtime failure 统一收敛到安全 `INDETERMINATE`/operation unavailable。内部 reason 只能使用固定枚举，例如 `TYPE_UNKNOWN`、`TYPE_MISMATCH`、`TYPE_NOT_ALLOWED`、`SCANNER_LIMIT`、`CONTAINER_LIMIT`、`PASSWORD_PROTECTED`、`RESULT_TIMEOUT`、`DEFINITIONS_STALE`、`READINESS_EXPIRED`、`SUBJECT_MISMATCH`、`SCHEMA_DRIFT` 和 `INSPECTION_UNAVAILABLE`。

## 10. `B24-I/1` 双 profile conformance oracle

### 10.1 独立 driver 与 oracle

同一 scenario set 至少运行：

1. `STREAMING_ENGINE_V1` 合成协议 driver：真实执行 length-framed、backpressured stream state machine，但 scanner native result 完全合成；
2. `OBJECT_EVENT_MANAGED_V1` 合成事件 driver：真实执行 exact version、重复/乱序/event/tag state machine，但不访问 AWS；
3. 未来私有 adapter conformance：用部署批准的无害 canary/benign files 在隔离环境运行，只输出安全计数和 digest。

oracle 读取核心 projection、durable job/lease、adapter-private safe counters、storage descriptor、resource counters 和 sink-scan 结果。它不能调用 adapter 的 expected-state calculator，也不能让 driver 直接把 inspection 改成 clean/rejected。

固定 fixture 使用虚拟时钟和生成式 bytes：`benign-pdf-like`、`benign-image-like`、`unknown-container`、`declared-mismatch`、`8192-byte-limit`、`8193-byte-over-limit`、生成式 slow/short/long streams，以及完全合成 `threat-detected` native result。fixture 名仅为测试 identity；媒体 allowlist 与数字不是生产默认。

### 10.2 固定场景

| ID | 两个 profiles 必须共同证明 |
| --- | --- |
| `I-01` | 同一 exact bytes 的 allowed magic type、完整 clean result 和 fresh definitions 产生一次 `CLEAN`；未形成 B24 publication/API |
| `I-02` | threat native result 产生 `REJECTED`，丢弃 threat name/path/hash；迟到 clean 不能覆盖 |
| `I-03` | 已识别 disallowed type 与声明/扩展/检测冲突稳定 `REJECTED`，scanner clean 不改变结果 |
| `I-04` | unknown/container-ambiguous、password protected、scanner/container limit 都是 `INDETERMINATE`，不产生 available |
| `I-05` | short/long read、timeout、idle、cancel、queue full、parser crash 和 malformed native reply/event 释放全部 handle/slot 且失败关闭 |
| `I-06` | pinned generation reload/expiry 与 managed evidence/policy expiry 在 commit-time 阻止 clean；新 attempt 不能复用旧证据 |
| `I-07` | streamed profile 的 bytes/chunk/EOF 与 managed profile 的 versionId/binding/schema 完全绑定 subject；path/key/ETag/tag 单独均不足 |
| `I-08` | 重复、乱序、旧 attempt、旧 file version、旧 binding 和迟到 worker result 都由 durable version/fence 幂等拒绝 |
| `I-09` | scanner 完成后、结果提交前 crash 可收敛；旧 lease worker 不能覆盖新 attempt；effect/count 不重复 |
| `I-10` | tenant A/B 使用相同展示名、bytes、checksum 或 native token 时，storage read、queue、quota、result、cleanup 和存在性完全隔离 |
| `I-11` | quarantine、scanner、detector、publication 与 cleanup 主体的权限矩阵失败关闭；普通 app/browser 无法读取待检查/rejected/indeterminate bytes |
| `I-12` | file bytes、展示名、checksum、locator、provider identity、threat detail、parser metadata、native error 和 canary 不进入日志、trace、metric、普通事件、Activity、Redis、报告或失败输出 |
| `I-13` | scanner/detector/policy 缺失或 conformance digest 漂移使 B24 inspection `NOT_READY`，既有 CRM API 与数据库状态保持不变 |
| `I-14` | fixture policy 分别接受 pinned 与 managed definitions 时，两个 profile 的同一核心输入得到相同三态；拒绝 managed evidence 的 policy 在任何 object scan/open 前 `NOT_READY` |

### 10.3 逐场景 checkpoint

每一步至少比较：

- exact file/content version、byte/inspection/publication 三轴状态和 optimistic version；
- job generation、attempt、lease/fence、deadline、retry classification 与下一个 not-before；
- detector/scanner readiness revision、definition basis、safe result digest 和 commit-time validation；
- storage open/read/close/cancel counts、first offset、实际 bytes、buffer high-water 和 active handles；
- streamed native chunk/EOF/reply counters，或 managed event version/binding/dedup/tag-reconcile counters；
- A/B tenant queue/admission/temporary-space counters与未知输入 partition 数；
- publication/download/list effect 必须为零；只有 B24-X 在相同 oracle 上取得目标根锁并显式驱动的组合场景可产生发布；
- 所有禁止 sink 的 canary 命中数为零。失败报告只显示 scenario、固定 reason 和 evidence digest。

通过 `B24-I/1` 只表示归一 contract 和 isolation oracle 可执行，不表示真实 scanner、Tika parser、AWS account、对象存储、数据政策、吞吐或定义新鲜度已经通过。

## 11. 分层与后续接口约束

B24-SPLIT 已把本文件的 semantic shapes 固化为 `application.port.out.attachment` contracts 与 domain value objects 的未来所有权，实现必须遵守：

- domain 只拥有文件内容 identity、正交状态、受控结论与生命周期；不 import Tika、ClamAV、AWS SDK、socket、filesystem、HTTP/event DTO 或 provider enum；
- application 编排 typed detector/scanner/readiness/storage ports、durable job 和结果仲裁；不接收 `InputStream` 之外的 Servlet/SDK/provider request，不保存 full bytes；
- `infra/external` 实现 stream/event/native mapper、进程隔离和 readiness；provider path/key/version/threat message 在该边界终止；
- `infra/persistence` 只保存 tenant-scoped file/job/evidence 的受控字段、版本、lease、deadline 和 safe reason；不保存 raw response、任意 metadata JSON、parser output 或 object URL；
- `infra/rest` 只负责后续代理上传/下载 transport；检查 worker 不是公共 HTTP scanner endpoint；
- `start` 默认组合 disabled inspection。dev/test synthetic adapter 必须由非 prod 条件保护，且不能接受任意本地 path、URL 或真实文件目录。

本文本身不冻结最终 Java 类型、包名、API path、migration、配置键、媒体 allowlist 或生产数值；后续 [B24-SPLIT](B24-SPLIT-implementation-boundaries.md) 已选择首个实现边界，且不改变本文的检查语义与 conformance 要求。

## 12. 对后续任务的交付

### 12.1 B24-S

B24-S 已为两个 profiles 冻结相同的 exact subject：不可覆盖 content version、实际 length、受支持 integrity、从 offset 0 的有界流和关闭/取消；对象事件 profile 另需 versioning、versionId、事件/tag reconcile 与旧版本删除语义。若实际 storage profile 无法提供 exact version，`OBJECT_EVENT_MANAGED_V1` 仍不可 ready。

### 12.2 B24-A

业务 target/caller 授权不进入 scanner。B24-A 只决定谁能创建意图、观察安全状态、列出、下载和删除；scanner service account、callback 或 event 都不能合成为 `AuthenticatedCaller` 或获得业务目标权限。

### 12.3 B24-X

[B24-X](B24-X-composed-fixture.md) 已用 `X-01..X-08` 复用 `I-01..I-14`，并加入 MySQL transaction、storage crash、发布/删除并发、业务失权与双 tenant。核心 expected state 仍由独立 oracle 计算；synthetic scanner/storage 只报告 native observations。

### 12.4 B24-SPLIT

[B24-SPLIT](B24-SPLIT-implementation-boundaries.md) 已选择 secure-local once-only + bounded magic + streamed engine 作为 B24a 第一组合，并冻结 PDF/PNG/JPEG、16 MiB public ceiling 与正值配置面。具体部署仍须填入全部 queue/stream/container/attempt/retention limits，并在相同 build/config 上运行三层 conformance；只通过合成 profile不得启用生产文件能力。

## 13. 官方事实依据

- [ClamAV ClamD Protocol](https://docs.clamav.net/manual/Usage/ClamdProtocol.html)定义 `INSTREAM` length-prefixed chunk、`StreamMaxLength` 超限错误、`VERSION` 的程序/数据库版本、`SELFCHECK`、有界 queue/thread/timeout 约束，并明确原生 TCP socket 未加密且未认证。
- [ClamAV scanning documentation](https://docs.clamav.net/manual/Usage/Scanning.html)区分 daemon path、file-descriptor 与 streamed input，并说明 `clamd` 依赖由 `freshclam` 管理的签名数据库；这支持 B24 只用 stream/FD profile 且独立治理 definition generation。
- [Amazon GuardDuty S3 scan result documentation](https://docs.aws.amazon.com/guardduty/latest/ug/monitoring-malware-protection-s3-scans-gdu.html)定义 `NO_THREATS_FOUND`、`THREATS_FOUND`、`UNSUPPORTED`、`ACCESS_DENIED` 与 `FAILED`，并把 password、对象/解包/层级/ratio limits 列为 skipped/unsupported，而不是 clean。
- [GuardDuty EventBridge schema](https://docs.aws.amazon.com/guardduty/latest/ug/monitor-with-eventbridge-s3-malware-protection.html)提供 bucket/key/ETag/versionId、重复/乱序可关联的对象结果结构；[S3 feature support](https://docs.aws.amazon.com/guardduty/latest/ug/supported-s3-features-malware-protection-s3.html)说明 versioned object 分别扫描且开始顺序不保证；[工作原理](https://docs.aws.amazon.com/guardduty/latest/ug/how-malware-protection-for-s3-gdu-works.html)说明至少一次结果投递、临时扫描环境与清理边界。
- [GuardDuty malware scan engine](https://docs.aws.amazon.com/guardduty/latest/ug/guardduty-malware-detection-scan-engine.html)说明托管引擎持续每 15 分钟更新签名但不为逐次结果提供精确 catalog revision，支持 `MANAGED_DEFINITIONS` 与 `PINNED_DEFINITIONS` 分级而不是伪造版本。
- [GuardDuty S3 quotas](https://docs.aws.amazon.com/guardduty/latest/ug/malware-protection-s3-quotas-guardduty.html)给出当前 100 GB object、100,000 extracted files 与 100 nesting levels 等 provider ceilings；这些值只用于能力对账，不成为 B24 公共默认。
- [Apache Tika content detection](https://tika.apache.org/3.2.1/detection.html)区分 magic/name/declared-type hints 与 container-aware detection；[MimeTypes API](https://tika.apache.org/3.0.0/api/org/apache/tika/mime/MimeTypes.html)说明 stream magic detection 读取有界前缀；[Tika security model](https://tika.apache.org/security-model.html)明确解析与 mime detection 不是安全边界，支持将深度 detector 放入受限隔离进程。

这些资料证明两种 native scanning model 可以归一到同一失败关闭核心，但不能替部署方选择产品、接受托管定义证据、配置 IAM/network、审查数据处理或证明真实 build readiness。
