# B24-S 对象存储映射、readiness 与失败契约

> 基线：`f30240b docs(B24): 规划下一批能力边界与任务拆分`
>
> 对账日期：2026-07-20
>
> 状态：设计契约已冻结；未实现 Java 端口、S3/本地适配器、配置、迁移、网络调用或真实 storage conformance。

## 1. 结论

B24 首期可以让一个 versioned S3-like 模型与一个隔离本地文件系统模型实现同一组 provider-neutral typed storage operations。共同合同不要求所有后端都有原生 version API，而是要求每个成功对象都有不可覆盖的 exact generation identity；S3 profile 用 `(key, versionId)`，本地 profile 用受治理目录中的一次性 locator 与 descriptor generation。二者都不能只用可复用路径、当前 key、ETag、文件名或“存在=true”表示对象身份。

共同合同固定以下决策：

- 首期只提供核心代理的单流上传，不提供浏览器预签名直传、分片 API、断点续传或 provider multipart identity；
- canonical content evidence 固定为 core 与 adapter 同时核对的 full-object `SHA-256 + actualBytes`，ETag、mtime、inode/file key、客户端声明或 SDK request ID 都不能替代它；
- `HEAD/probe`、完整读取、single range、删除请求与 authoritative absence confirmation 是分离的 typed operation；delete success 不能直接产生 `REMOVED`；
- single range 是独立 capability。两个本轮 profile 都能实现 native positional read，但任一真实 build 未通过该能力 conformance 时只能声明 `UNSUPPORTED`，收到 `Range` 时在 storage open 前失败，不能读取完整对象再丢弃前缀；
- core storage absolute ceiling 固定为 `67,108,864` bytes（64 MiB），有效单文件上限取它与 B24-I 检查器、媒体 allowlist、tenant policy 和部署 adapter ceiling 的最小值；首期每个业务记录最多 `100` 个 active attachments；
- ready/quarantine bytes、未完成意图、上传/下载并发和磁盘/对象存储 reserve 必须由部署提供正的 tenant/global 数值并进入 readiness evidence。B24-S 不用 S3 理论容量或本地 `usableSpace` 猜一个通用 tenant quota；缺少可执行 quota policy 时 storage `NOT_READY`；
- S3 multipart 与本地“分片文件”在 `B24-S/1` 都是 `UNSUPPORTED`。S3 SDK 自动 multipart 必须关闭；未来若启用，必须另增 upload ID/part checksum/complete/abort/reconcile 合同版本，不能在 adapter 内静默改变 crash surface。

这份结论只解除“两个 storage 模型能否共用 typed contract”的设计阻断。B24-A 决定目标域授权和父生命周期，B24-I 决定内容类型、检查器 ceiling 和最终有效文件上限；[B24-X](B24-X-composed-fixture.md) 已进一步把 storage、inspection、授权与 MySQL crash oracle 组合冻结。

## 2. 信任边界与非目标

### 2.1 核心拥有的事实

MySQL 后续保存 tenant、file/intent/attempt identity、目标关系、byte/inspection/publication state、expected/actual bytes、canonical SHA-256、opaque storage locator/generation、版本、deadline、lease 和最小审计。应用层决定某个对象是否可检查、发布、下载或删除；storage adapter 不得从对象 metadata、bucket、路径或调用凭据反推 CRM authority。

storage 后端只负责：在一个已由核心选择的 binding 中不可覆盖地保存 exact bytes，按 exact identity 探测和打开它，并按 stable deletion attempt 删除与确认同一 identity。bucket、root path、endpoint、region、account、credential、KMS key、provider version、filesystem name、SDK error 和 native request/response 都留在 adapter/部署边界。

### 2.2 明确不由 storage 端口提供

- caller、member、tenant 选择、目标域授权、附件关系、文件名、媒体 allowlist、扫描结论或发布决定；
- 任意 bucket/path/key/URL/header/metadata map、provider list/search/copy/rename 或跨 binding 操作；
- provider redirect、预签名 URL、浏览器 bearer、公开对象、静态网站或 webroot 路径；
- 全文件 `byte[]`、`Path`、`File`、Servlet multipart、SDK request/response 或 provider exception；
- 秒传、跨 tenant dedup、checksum lookup、对象列表、prefix cleanup 或“我的文件”目录；
- 物理介质擦除、replica/backup 全局销毁、legal hold 解除或虚假的 `physicallyDestroyed=true`。

### 2.3 不可绕过的隔离

普通请求永远不能提交或选择 storage binding。应用从已经验证的 human caller 派生 tenant，再由部署 policy 解析当前 tenant 的 opaque binding revision。每个 locator、generation、attempt、probe、stream、quota 和 deletion evidence 都绑定该 tenant/binding revision；随机 key 只是防碰撞材料，不是 tenant 隔离控制。

runtime adapter 不允许发出 `ListObjects`/`ListObjectVersions` 或遍历 storage root 猜测 owner。崩溃恢复只读取 MySQL 中某个 intent/deletion job 预先保存的 exact locator；部署 inventory 可以做独立只读审计，但不能直接创建、发布或删除 CRM file state。S3 为区分 missing 与无权访问而要求的 `s3:ListBucket` IAM 判定是第 6 节单独约束的 confirmation-lane 权限，不授权 application 调用列表 API，也不把列表结果带入核心。

## 3. Provider-neutral typed contract

### 3.1 最小类型

| 类型 | 冻结语义 | 禁止内容 |
| --- | --- | --- |
| `StorageBindingRef` | tenant policy 已解析的 opaque binding 与不可变 revision；只在 application/persistence 内部流转 | bucket、endpoint、account、root path、credential、KMS key |
| `PlannedObjectLocator` | intent 事务前后可稳定保存的一次性 locator；只允许该 binding 下一个 upload attempt 使用 | 文件名、tenant/actor/target 原值、可拼接 provider key/path |
| `StoredObjectIdentity` | binding revision + planned locator + adapter generation；成功 store 后不可变 | 裸 key、裸 versionId、inode、ETag 或当前路径 |
| `ContentEvidence` | exact `actualBytes` 与 full-object SHA-256；算法和值有固定编码和长度 | ETag、客户端任意算法、mtime、provider checksum map |
| `StorageReadSelection` | `FULL` 或 core 已按 RFC 9110 规范化的单一 `(offset,length,total)` | 原始 `Range` header、multi-range、suffix 文本 |
| `StorageReadHandle` | metadata 已核对的 closeable/cancelable/backpressured byte source | URL、Path、File、byte array、SDK stream/response/header map |
| `StorageProbeResult` | `PRESENT`、`ABSENT`、`MISMATCH` 或 `UNKNOWN` 加受控 evidence | provider status/message、路径、对象 metadata |
| `StorageDeleteResult` | `ACCEPTED`、`ALREADY_ABSENT`、`BLOCKED` 或 `UNKNOWN`；不代表最终确认 | provider 2xx、delete marker 或错误原文 |
| `StorageReadinessSnapshot` | build/binding/profile/operation/limit/conformance/time 的短期正向证据 | secret、bucket/path、账号、原始 probe response |

`PlannedObjectLocator` 与 `StoredObjectIdentity` 可以持久化为专用、长度受限、非结构可猜的 adapter-owned encoding；它们不进入 domain、公共 VO/Response、日志、trace、metric、事件或 Activity。application 只能比较 typed identity 是否 exact equal，不能解析其中的 provider 字段。

### 3.2 固定操作

| Operation | 输入 | 成功结果 | 失败关闭要求 |
| --- | --- | --- | --- |
| `allocate` | tenant-bound binding、local file identity、stable upload attempt | 一次性 planned locator | 不访问业务 repository；不能从展示名/目标 ID 生成 key |
| `store` | exact locator、expected bytes、64 MiB ceiling、deadline、bounded byte source、canonical checksum policy | stored identity + actual bytes + SHA-256 + safe completion receipt | 不覆盖、不 append；未知 outcome 先 probe，不能换 locator 重传 |
| `probe` | exact stored identity 或 exact planned locator | 四态存在/完整性结果 | permission/timeout/5xx/ambiguous missing 一律 `UNKNOWN`，不能当 absent |
| `open` | exact stored identity、expected evidence、full 或 normalized single range、deadline/cancellation | typed descriptor + one new read handle | 先核对 generation/size/checksum；不复用旧 handle，不跟 redirect |
| `requestDelete` | exact stored identity、stable deletion attempt | safe delete result | 只删 exact generation；2xx/void/delete marker 不完成本地 tombstone |
| `confirmAbsent` | same stored identity、same deletion attempt、current binding/build | authoritative `ABSENT` 或非终态 | 只接受 profile 声明的 exact procedure；普通 404/permission error 不升级 |
| `abortIncomplete` | exact planned locator、same upload attempt | partial artifact absent evidence | 不能 prefix/list 清理；若 outcome unknown 保持补偿 job |

`allocate` 产生 locator 后，核心先把它与 intent/attempt 原子保存，再允许 storage I/O。这样 provider 成功而本地 finalize 前崩溃时，reconciler 仍能定位唯一候选。任何 adapter 都不能在成功 `store` 后才临时生成一个无法从 durable intent 恢复的 key。

### 3.3 Exact identity 与幂等

同一 `store` attempt 的重复投递只允许以下收敛：

1. exact locator authoritative absent：可在原 deadline/retry policy 下重新执行同一 attempt；
2. exact locator present 且 generation、bytes、SHA-256 都与该 attempt 一致：返回同一 stored identity/receipt；
3. locator present 但 identity/evidence 不同：`CONFLICT`，隔离对象并停止自动重试；
4. provider/local outcome unknown：保持 `UNKNOWN` 并重试 exact probe，不能创建第二 locator、覆盖、copy 或把客户端重发当成新内容。

完整性证据永远绑定 stored generation。对旧 generation 的 clean inspection、read descriptor 或 delete confirmation 不能应用到同 key 的另一版本。adapter 即使知道 provider version，也不能把“当前版本”作为 shortcut。

## 4. 大小、完整性、配额与 multipart

### 4.1 首期硬边界

| 项目 | `B24-S/1` 决策 |
| --- | --- |
| 实际对象大小 | `1..67,108,864` bytes；空流和第 `67,108,865` byte 都失败 |
| 客户端 expected bytes | 必填且同一范围；actual 必须 exact equal，不能只当 hint |
| canonical checksum | full-object SHA-256，32 raw bytes；持久化/传输使用一种固定 canonical encoding |
| 客户端 checksum | 可缺失；若提供首期只接受 SHA-256，并与 core/adapter 结果同时相等 |
| stream buffer high-water | 每条 storage 读/写链路 `<= 64 KiB`，不含固定协议/TLS buffers 的部署证明 |
| active attachment cardinality | 每个已授权业务记录最多 `100`；数据库并发仲裁，不能只先查后写 |
| range 数量 | 每请求 `0` 或 `1`；multi-range 固定拒绝 |
| multipart/断点续传 | `UNSUPPORTED`；native create/upload-part/complete 调用次数必须为 `0` |

64 MiB 远低于 S3 单次 PUT 的 5 GB 上限，也低于 AWS 文档建议开始考虑 multipart 的 100 MB 区间，从而让两个 profile 使用同一个一次性流/crash surface。这个数只是 storage absolute ceiling；B24-I 如果只能对更小对象给出有界检查证明，公共上限必须取更小值，不能为了保留 64 MiB 而绕过 scanner。

### 4.2 Tenant quota 不是 backend free-space 推断

每个 production binding policy 必须显式给出正值：`maxReadyBytes`、`maxQuarantineBytes`、`maxPendingIntents`、`maxActiveUploads`、`maxActiveDownloads`、process/global reserve、deletion control reserve 与最小 free-capacity floor。核心只在 MySQL/共享 admission 中对 tenant 用量和并发做权威仲裁；S3 account quota、bucket metrics 或本地 `FileStore.getUsableSpace()` 只能是 adapter readiness 输入。

Java 明确把 `getUsableSpace()` 描述为 hint 而不是保证，外部 I/O 会立即使它过时。因此本地 adapter 必须同时有部署 reserve 和实际写入失败模型，不能用一次 free-space 查询承诺 tenant quota。S3 的“几乎无限容量”也不能替代预算、成本、KMS/request quota、tenant 公平性和 quarantine reserve。

缺少任何上述数值、算术溢出、quota 大于部署 capacity、共享存储却只有单 JVM counter、或 readiness 无法为 deletion 保留 control capacity 时，相关写/read operation 为 `NOT_READY`。一个 tenant 满额不能淘汰另一个 tenant 的活动 reservation，也不能阻止已到期对象删除。

### 4.3 multipart 结论

S3 multipart 在 complete 前只保存 parts，不产生完整对象；未 complete/abort 的 parts 继续占用存储，abort 也需要 exact upload ID，且在途 part 可能与 abort 竞态。这个状态面与本地单文件没有共同最小语义，所以不藏在 `store` 内。

`B24-S/1` 要求 S3 client 关闭 transfer-manager/SDK 自动 multipart，conformance spy 断言 `CreateMultipartUpload/UploadPart/CompleteMultipartUpload=0`。部署仍应配置 incomplete multipart lifecycle 作为防御性治理，但该 lifecycle 不能成为 B24 attempt completion/cleanup evidence。未来 multipart profile 至少要新增 durable upload ID、part number/checksum/size、complete digest、abort lease、在途 part drain、orphan cost 与 crash oracle，并提升 contract version。

## 5. Full 与 single-range 读取

### 5.1 共同规则

core 在打开 storage 前解析 RFC 9110 `bytes` range，并用已经持久化且本次 probe 重验的 total length 规范化为一个 `(offset,length,total)`：

- `bytes=start-end`、`bytes=start-` 与 `bytes=-suffixLength` 可在无溢出且可满足时生成一个 selection；
- multi-range、非 ASCII 十进制、负数、逆序、零 suffix、start 不小于 total、算术溢出或超过 per-request hard limit 都没有 typed selection；
- capability `READ_SINGLE_RANGE=UNSUPPORTED/NOT_READY` 时，任何 `Range` 在 adapter open 前失败，不能降级成 `200` full；
- 每个 full/range 请求都建立新 handle，并重新应用 B24 human/record/file/publication/storage authorization；handle 不可序列化、缓存或跨请求复用；
- adapter 返回的 kind、filename、ETag、mtime、provider `Content-Range`、headers 或 validator 不参与 HTTP response；核心只根据规范 selection、B24-I canonical media type 和安全展示名生成 header；
- client abort、idle/stream deadline、byte mismatch、删除/撤回或 output failure 都 cancel+close 当前 handle，首字节后不透明 reopen、redirect 或补写 JSON error。

### 5.2 Profile 映射

S3 `GetObject` 原生接受一个 HTTP Range，官方 API 明确不支持一次 GET 的 multiple ranges；adapter 同时传 exact `versionId`，只读取选择区间。隔离本地 profile 使用 `SeekableByteChannel/FileChannel` 的 absolute position 和 bounded remaining counter，不读取前缀后丢弃。两个 profile 因而都可声明 `READ_SINGLE_RANGE=READY`，但 capability 是 build/binding 级证据，不是看到 S3-compatible 名称或 `SeekableByteChannel` 类型就自动获得。

## 6. `S3_VERSIONED_GENERAL_V1` 映射

### 6.1 适用范围

本 profile 以 AWS S3 general purpose、versioning-enabled bucket 的公开语义作为参考，不包含 directory bucket、S3 Object Lambda、Outposts、任意“兼容”endpoint 或 SDK 默认行为。其他 S3-like 产品只有在当前 adapter build 对同一操作、错误与 crash oracle 逐项通过后才能声明该 profile；API 名称相同不构成证据。

### 6.2 操作映射

| Contract | S3 映射 | 必须保存/验证 |
| --- | --- | --- |
| allocate | adapter 生成不可猜且不含业务原值的 unique key，绑定 expected bucket owner/binding revision | opaque planned locator；key 不进入普通核心输出/日志 |
| store | one `PutObject`，`If-None-Match: *`，exact Content-Length，full-object SHA-256/trailing checksum，禁止 ACL/redirect metadata | success `VersionId` 必须非空且非 `null`；actual bytes、SHA-256 与 request/response evidence 一致 |
| probe | `HeadObject`/必要时 bounded `GetObject`，始终指定 exact key + versionId + expected owner/checksum mode | size、stored checksum、version identity；403/400/timeout/5xx=`UNKNOWN` |
| open full | `GetObject` exact version | response version/length/checksum 与 stored identity/evidence 一致；不透传 response headers |
| open range | `GetObject` exact version + one normalized Range | upstream/body bytes exact 等于 selection；provider multi-range 不使用 |
| requestDelete | `DeleteObject` exact key + versionId + expected owner | stable deletion attempt；不允许遗漏 versionId 的 simple DELETE |
| confirmAbsent | 同 binding/owner 下用专用 confirmation lane 对 exact version 执行已证明的 HEAD/GET absence probe | 只有具有 missing-disambiguation 权限的明确 exact-version `404/NoSuchVersion` 才确认；403、delete marker/current-key 404 不够 |
| abortIncomplete | 对 planned unique key 做 exact probe；若发现本 attempt 的 stored version，只按该 exact version 删除；首期没有 upload ID/parts | 不做 key-only delete，不 list prefix，不创建 delete marker 代替 exact cleanup |

S3 `PutObject` 成功不会产生 partial object，但连接断开可能让客户端不知道请求是否成功，因此 unknown write outcome 仍必须按 planned key probe。`If-None-Match: *` 是覆盖防线而不是幂等 receipt：`409/412` 后先核对 exact identity/evidence，不能盲目用新 key 重发或把任意现有对象当成本 attempt。

### 6.3 Version、删除与复制边界

profile readiness 要求 bucket versioning 当前为 `Enabled`，首次启用已超过官方建议的 15 分钟传播窗口，并由 canary 证明 PUT 返回非空 versionId、exact-version HEAD/GET/range/delete 可用。`Suspended`、未知、directory bucket 的 `null` version 或 binding 在运行期间漂移都会立即撤销 write/read/delete readiness。

普通 versioned DELETE 只添加 delete marker，旧 bytes 仍作为 noncurrent version 保存；所以 B24 删除只能携带 persisted exact versionId。Object Lock retention/legal hold 若可能阻止 B24 deadline 内 exact-version delete，则 `DELETE_EXACT/CONFIRM_ABSENCE=NOT_READY`；首期不能用 governance bypass 或删除 marker 假装完成。permission-denied、KMS unavailable、delete 2xx、current key 404 或 replication 状态都不能直接成为 local `REMOVED`。

AWS 对不存在对象的 HEAD/GET 会根据调用者是否拥有 `s3:ListBucket` 返回 `404` 或 `403`，而 HEAD 错误本身又不携带可依赖的具体异常。因此 S3 profile 必须有与普通 read/write role 分离的 confirmation lane：它固定 expected owner/bucket/binding，具有让 exact-version missing 明确返回 `404/NoSuchVersion` 的最小 `s3:ListBucket` 判定权限和 `s3:GetObjectVersion`，但 adapter 代码仍没有 list operation，conformance 断言 `ListObjects/ListObjectVersions=0`。该权限不存在、被 condition/Deny 抵消、返回 `403`，或当前 backend 不能证明删除后 exact version 的响应语义时，`CONFIRM_ABSENCE=NOT_READY`；不能为了保留“无 ListBucket 权限”把 403 猜成 absent。

readiness/manifest 必须声明 replication、backup、Object Lock 和 lifecycle policy。B24 首期确认只代表受控 primary binding 的 exact version 对新读取已 authoritative absent；replica、backup、不可变保留与物理介质不在这个布尔值中。若部署要求它们也删除，应由独立 retention/deletion evidence 追踪，公共 API 仍不声称物理销毁。

### 6.4 安全 readiness

至少证明：

- all four Block Public Access 生效，Object Ownership 为 bucket-owner-enforced/ACL disabled，bucket/access-point policy 没有 public、anonymous 或浏览器直读；
- TLS endpoint、expected bucket owner、region/binding revision 和允许 host 固定；redirect、任意 endpoint、request payer 或 caller metadata 不能由业务请求选择；
- at-rest encryption policy 当前生效；若用 SSE-KMS，writer/reader/probe/delete 所需 KMS 权限和 key 状态分别通过，key ARN 不进入 snapshot/public output；
- IAM/bucket policy 只允许受治理 prefix 下需要的 PUT、exact-version GET/HEAD/range、exact-version DELETE 与 readiness probe；普通 data lane 没有 `s3:ListBucket`/`s3:ListBucketVersions`，独立 confirmation lane 只额外持有第 6.3 节的 missing-disambiguation `s3:ListBucket` 判定权限；所有 runtime lane 的 `ListObjects/ListObjectVersions/CopyObject/ACL/tagging/public URL` 调用能力都不存在；
- conditional write、version ID、SHA-256、size、range、unknown outcome、exact delete 和 absence procedure 由当前 build 的隔离 canary 证明；兼容实现不得用 AWS 文档替代自己的测试；
- quota、request/KMS limits、open/idle/stream deadlines、retry/backoff、control reserve 和最大 response/request bytes 都有正值，且 snapshot 不包含 account/bucket/key/credential/provider error。

## 7. `LOCAL_SECURE_NIO_V1` 映射

### 7.1 适用范围与部署等级

本 profile 面向一个绝对、专用、不可由 HTTP server 直接服务的 POSIX-like local filesystem root。它不是任意 `Path` adapter：必须支持 Java 25 `SecureDirectoryStream`、`NOFOLLOW_LINKS`、atomic `CREATE_NEW`、owner-only POSIX permissions、seekable positional read、content/metadata force，以及当前 filesystem/build 的 crash conformance。

node-local root 默认只可声明 single-node dev/test readiness。多 replica、容器漂移、ephemeral volume、NFS/SMB/FUSE/同步盘或共享挂载若没有同一 filesystem 的 locking、force、rename/delete、open-handle、failover 和 fencing 证据，production operation 全部 `NOT_READY`；不能仅因为每个 replica 上存在同名目录就认为共享对象可用。

### 7.2 On-disk layout 与 store

root 下的固定 shard directory 只在部署/provisioning 阶段创建并验证，runtime 不接受或创建任意目录层级。每个 planned locator 映射到一个固定 shard 中随机、一次性的 basename，并派生两个固定 suffix entry：content 与受控二进制 descriptor；basename 不含 tenant、actor、目标或展示文件名，文件 mode `0600`。普通请求不能提供 shard、basename、suffix 或任何 path segment。

store 顺序固定为：

1. 从 open root 的 `SecureDirectoryStream` 以 `NOFOLLOW_LINKS` 相对打开预配置 shard，再相对操作两个 entry；拒绝 symlink、非目录、owner/mode/device mismatch；
2. content entry 使用 `CREATE_NEW + WRITE` 创建，边读边执行 byte/deadline/idle/cancellation 上限并计算 SHA-256；永不使用 `CREATE`、`TRUNCATE_EXISTING`、append 或 replace；
3. actual bytes 与 expected bytes/checksum 不一致时关闭并进入 exact abort，不创建 descriptor；
4. 对 content 执行当前 platform conformance 要求的 data+metadata force 并关闭；
5. 最后以 `CREATE_NEW` 写入固定 schema descriptor entry，包含随机 generation、actual bytes、SHA-256 和 schema version，force 并关闭，再执行已证明的 shard-directory durability procedure；
6. 只有 descriptor、content 和 durable directory evidence 都完成才返回 stored receipt。任何前置 crash 留下的 content entry 都只是 incomplete artifact，不能被 probe 映射为 `PRESENT/STORED`。

不采用“先写 temp 再 `ATOMIC_MOVE` 到已知 target”作为共同正确性基础：Java 对 atomic move 目标已经存在时是 replace 还是失败保留 implementation-specific。随机一次性 basename + 两个 `CREATE_NEW` entry 避免把该差异变成覆盖窗口；如果具体实现仍选择 rename，必须在自己的 profile/build conformance 证明 no-replace 和 crash durability，不能改变 core contract。

### 7.3 Probe、read 与 delete

probe 通过 `SecureDirectoryStream` 相对读取 descriptor 与 content attributes，使用 `NOFOLLOW_LINKS`，要求普通文件、owner/mode、same approved FileStore、generation 和 size 全部匹配，并在有界 full read 中重新计算 content SHA-256 与 descriptor 比较。descriptor 缺失而 content 存在、类型/权限变化、symlink、额外可替换 entry 或任何 I/O 异常均为 `MISMATCH/UNKNOWN`，不是 `ABSENT`。

open 每次重新核对 descriptor 后，用 secure relative `READ + NOFOLLOW_LINKS` 打开 content；full 从 offset 0 开始，range 使用 seekable absolute position 和 remaining counter。read 完成还核对 exact byte count；pre-open/descriptor 与 post-read evidence 发生变化时 adapter contract failure，不能把流标为 completed。

delete 先核对 exact descriptor generation，再删除 content，随后删除 descriptor，并执行当前 platform 已证明的 shard-directory durability procedure。crash 后重复同一 deletion attempt：content 已 absent 而 exact descriptor 仍在时继续清理；descriptor identity 不同则 conflict；两者状态无法确定则保持 pending。authoritative absence 还要求两个 object entries 都 absent、没有该 identity 的 adapter-managed active handle/lease，并且 root/device/binding 未漂移。`Files.delete` 成功本身不是跨 crash deletion confirmation。

### 7.4 本地 readiness

至少证明：

- root 是 canonical absolute path，启动时与每次 snapshot 都不为 symlink，不位于 webroot、应用 classpath/可执行目录、通用 temp、用户下载目录或其他服务共享目录；
- root/子目录/文件 owner、group 和实际 permissions 不宽于 `0700/0600`，只有受治理 adapter workload 能创建 hard link、rename、write 或 delete；volume at-rest encryption 和 backup policy 有部署 evidence；
- `DirectoryStream` 实际实现为 `SecureDirectoryStream`；root/device/FileStore identity、POSIX view、CREATE_NEW、NOFOLLOW、seek、force、delete 与 free-capacity floor 都通过，不支持任一项即 `NOT_READY`；
- storage root 与 staging/content/descriptor 位于同一 approved FileStore；`FileStore.getUsableSpace` 只作短期 readiness hint，部署 reserve 与 MySQL quota 仍为权威 admission；
- power/process crash cuts 覆盖 content/descriptor create、每次 force、receipt、read open、content delete、descriptor delete 和 confirmation；恢复不发布 partial bytes、不覆盖、不跨 object 清理；
- multi-replica 或 failover 环境还有共享 active-handle admission/fencing 与同一 root 可见性证据；否则只声明本机 dev/test profile。

## 8. Readiness snapshot

### 8.1 operation 维度

每个 tenant binding 的 snapshot 对以下 operation 分别返回 `READY/NOT_READY/UNSUPPORTED`：

- `ALLOCATE_WRITE`
- `STORE_SINGLE_STREAM`
- `PROBE_EXACT`
- `READ_FULL`
- `READ_SINGLE_RANGE`
- `DELETE_EXACT`
- `CONFIRM_ABSENCE`
- `ABORT_INCOMPLETE`
- `MULTIPART_UPLOAD`（本版本固定 `UNSUPPORTED`）

上传 admission 至少要求 allocate/store/probe/abort ready；检查读取要求 full read ready；普通 full download 要求 full read ready；range 额外要求 single-range ready；删除 worker 要求 delete 与 confirmation ready。read 不 ready 不能阻止已经 due 的 deletion control lane，write 不 ready 也不能让普通 CRM API 失败。

### 8.2 最小字段与 TTL

| Evidence | 最小语义 |
| --- | --- |
| contract/build | `B24-S/1`、不可变 adapter build、deployment class |
| binding | opaque binding revision、profile、isolation class；不含 provider/root 原值 |
| operation | 每项 status、safe reason、max bytes、checksum、range/multipart mode |
| immutability | conditional/create-new procedure、generation kind、no-overwrite conformance digest |
| integrity | SHA-256 upload/probe/read procedure 与 current build fixture digest |
| deletion | exact delete、absence procedure、active-handle drain、Object Lock/replica/backup scope 的安全摘要 |
| capacity | tenant/global quota policy digest、free/reserve bucket、concurrency/control reserve；不含可枚举 tenant 用量 |
| security | public/webroot denial、ownership/permission、encryption、allowed endpoint/root policy digest |
| conformance | `S-01..S-14` 通过集合、environment/profile digest、validUntil |
| time | adapter checkedAt、core receivedAt、expiresAt、monotonic probe sequence |

正向 snapshot 有效期固定为：

`min(adapterExpiresAt, coreReceivedAt + 5 minutes, conformanceValidUntil, bindingPolicyValidUntil)`。

任一时间缺失、非正 duration、future checkedAt、算术溢出、旧 sequence/build/binding、超过五分钟的 adapter TTL 或 evidence 到期都拒绝正向结果。credential/KMS/root/bucket/versioning/permission/quota/security drift、local free floor 跌破、S3 policy/lock 改变或新的 conformance mismatch 可以立即撤销对应 operation，不等待 TTL。

snapshot 是 admission evidence，不是 I/O success guarantee。每次 store/open/delete 仍执行 deadline、byte/cancellation 和 exact identity 检查；每次 human download 仍重新授权目标业务记录。readiness 不能携带 CRM authority，也不能缓存为浏览器 token。

## 9. 失败模型与重试

### 9.1 稳定分类

adapter 只向 application 返回以下受控类别：

| 分类 | 核心处理 |
| --- | --- |
| `CONFLICT` | exact locator/generation/evidence 不同；停止自动重试并隔离诊断 |
| `NOT_FOUND` | 只表示当前 operation 的受控 missing；删除完成仍需 authoritative confirmation |
| `INTEGRITY_MISMATCH` | size/SHA-256/generation/descriptor 不同；永不检查或发布 |
| `LIMIT_EXCEEDED` | byte/capacity/concurrency/local free floor；不驱逐其他 tenant |
| `DEADLINE_EXCEEDED` | cancel/close；unknown write/delete outcome 进入 exact reconcile |
| `PERMISSION_DENIED` | operation readiness 撤销或安全不可用；不能伪装 not found/absent |
| `RETENTION_BLOCKED` | Object Lock/volume policy 阻止 exact delete；保持不可下载和 pending |
| `UNSUPPORTED` | profile 明确不提供 range/multipart 等能力；不 fallback |
| `UNAVAILABLE` | transport/KMS/filesystem/provider/service 暂不可用；普通 CRM 不受影响 |
| `UNKNOWN_OUTCOME` | effect 可能成功也可能失败；只按 stable attempt/identity 对账 |

reason/message 只能是固定安全枚举，例如 `BINDING_STALE`、`VERSIONING_DISABLED`、`CHECKSUM_UNPROVEN`、`ROOT_UNSAFE`、`NO_SECURE_DIRECTORY_STREAM`、`CAPACITY_FLOOR`、`DELETE_CONFIRMATION_UNPROVEN`。不得包含 bucket、key、versionId、path、FileStore name、credential、KMS key、provider request ID、SDK class、exception message 或 object/checksum 原值。

### 9.2 Retry 规则

- `store` 在未得到 authoritative absent/present evidence 前不自动重发；确认 absent 后只可复用同一 attempt/locator/deadline，确认 present+same evidence 时复用同一 receipt；
- `probe/open` 可以按部署 policy 对明确未触达 provider 的 pre-open failure 做有限 retry；任何 retry 共用原 monotonic deadline，首字节后不 reopen；
- `requestDelete` 可用同一 deletion attempt 对 exact identity 幂等重投；不能生成第二逻辑 deletion、删除 current key 或扩大到 prefix/list；
- `confirmAbsent` 是只读、可重复 effect，但 permission/timeout/5xx/ambiguous 404 始终非终态；
- local partial 与 future multipart part 的清理都必须由 durable job/lease 驱动；after-commit callback、JVM shutdown hook、`DELETE_ON_CLOSE` 或 lifecycle event 只能辅助，不能承诺完成。

## 10. `B24-S/1` conformance oracle

### 10.1 执行层次

同一 scenario ID 必须运行：

1. provider-neutral model：属性化执行 byte/generation/attempt/range/delete 状态，oracle 独立于 adapter；
2. durable simulator：真实 streaming boundary、虚拟时钟、两个 tenant、两个 binding、crash injection；S3-like simulator 与真实 local filesystem runner 运行同一断言；
3. 当前真实 adapter build：隔离的 S3/general-purpose 或具体兼容 backend，以及明确的 local filesystem/OS/mount profile。报告只保留安全计数/digest，不保存对象值、locator、path、bucket 或凭据。

纯 mock、只统计方法调用、adapter 自己计算 expected state、只跑一个 tenant、内存 byte array、临时目录在同进程通过、或引用 AWS/Java 文档都不能产生 production readiness。local runner 必须在声明的真实 filesystem 上执行 crash/durability/symlink 负向场景；S3-like 实现必须证明自己的 semantics，不能继承 AWS 的品牌结论。

### 10.2 固定场景

| ID | 必须证明 |
| --- | --- |
| `S-01` | storage/policy/snapshot 缺失或 operation not ready 时，在 allocate/open 前失败；既有 CRM API 不受影响 |
| `S-02` | 两 tenant 相同文件名/bytes/SHA-256 仍有不同 binding/locator/generation/quota；cross-binding probe/read/delete 全拒绝 |
| `S-03` | `1` byte 与 64 MiB 成功；空流、expected mismatch、第 64 MiB+1 byte、慢流、abort、unknown length 全失败且 buffer `<=64 KiB` |
| `S-04` | canonical SHA-256、provider/local evidence、actual bytes exact 相等；ETag/mtime/fileKey/client checksum 不能单独完成 store |
| `S-05` | duplicate store、同 locator 不同 bytes、conditional/create-new conflict 与覆盖攻击不改写已存 generation |
| `S-06` | provider/local success 后 receipt 前每个 crash cut 只按 planned locator 收敛为 same receipt、exact cleanup 或 unknown，不 list/换 key |
| `S-07` | full 与三类 single range 读取 exact bytes；multi/invalid/overflow/unsatisfied/unsupported range open count=`0` 且不 fallback full |
| `S-08` | client abort、idle/stream deadline、length mismatch、首字节前后失败都只释放一次，transparent reopen=`0`，active counter 归零 |
| `S-09` | S3 exact version read/delete；simple delete marker、current-key 404、403/KMS/timeout、Object Lock 都不会直接产生 confirmed absent；confirmation lane 缺少有效 `s3:ListBucket` 判定权限时 `NOT_READY`，具备权限时 exact-version 404 才确认，且 list API call count=`0` |
| `S-10` | local symlink、unsafe root、mode/owner/device drift、descriptor/content partial、rename/replace、free floor 和 crash durability 失败关闭 |
| `S-11` | stable deletion attempt、effect/confirmation 分离、active handle drain、每个 delete crash cut；late store/read receipt 不复活对象 |
| `S-12` | multipart native call count=`0`；SDK threshold/config drift 立即撤销 write readiness；防御 lifecycle 不修改 core expected state |
| `S-13` | tenant/global quota、record cap 100、concurrency、control reserve、算术溢出与双 replica admission；一个 tenant 不能淘汰另一个 |
| `S-14` | body/name/checksum/locator/version/path/bucket/account/KMS/SDK error 不进入 API、普通 metadata、log/trace/metric/event/Activity/cache/report/conformance artifact |

### 10.3 Machine-readable manifest

报告至少包含 contract/fixture version、adapter build、deployment class、profile、binding-policy digest、operation statuses、max bytes、checksum/range/multipart mode、immutability/delete procedure digest、security/isolation class、quota/reserve/concurrency 数值、filesystem/S3 semantic evidence digest、每个 scenario/cut 的 pass/fail 与固定 safe reason、provider/local operation count、high-water bytes、double-close/reopen/cross-tenant/leak hits 和 checkedAt/validUntil。

报告不得包含真实 tenant/file/object ID、文件名或 bytes、SHA-256 原值、locator/key/version/path/root/bucket/endpoint/region/account、IAM/KMS/credential、request/response header/body、provider request ID、FileStore/device/host 可关联原值或 exception message。build、SDK、S3-like backend、bucket policy/versioning/lock/replication、local OS/filesystem/mount/root、encryption、quota、range、checksum、delete procedure 或 binding revision 变化后必须重跑；旧 manifest 不能自动迁移为新 readiness。

## 11. 闭合与剩余门槛

B24-S 已冻结：

- S3 exact `(key, versionId)` 与本地 once-only locator/descriptor generation 到同一 `StoredObjectIdentity` 的映射；
- 单流 immutable store、exact probe/full read、可选 native single range、stable delete 与 authoritative absence 的 typed operation 分离；
- full-object SHA-256、64 MiB storage ceiling、64 KiB buffer high-water、每记录 100 active attachments 与 deployment-owned tenant quota readiness；
- 首期 multipart/断点续传 `UNSUPPORTED`，S3 SDK 不得静默启用；
- versioned delete marker、Object Lock、replica/backup、本地 symlink/permission/FileStore/crash/active-handle 的失败关闭边界；
- 五分钟以内按 operation readiness 与 `S-01..S-14` 三层 conformance/安全 manifest。

仍未实现或证明：

- application Java 端口/类型、disabled/synthetic adapter、S3 SDK、本地 adapter、Spring bean、配置或真实 conformance runner；
- 任何 production bucket/root、credential、KMS/volume encryption、quota/admission、backup/deletion policy 或 current build manifest；
- application Java 端口对应的 B24-I MIME/signature/scanner 与 quarantine/inspection runtime；
- application/persistence 对应的 B24-A 目标路由、能力、父锁、typed parent 与 Work/OA/B05 `FILE` runtime；
- B24-X 的真实 MySQL/storage/scanner/authorization durable runner、current adapter conformance，以及已由 B24-SPLIT 冻结的正式 B24a/b/c 实现。

因此 B24-S 完成不表示文件功能、S3 或 local storage 已生产可用。B24-X 已消费本文件的 exact identity、range、quota、readiness 和 delete-confirm oracle，并禁止 synthetic storage 声明 expected state；B24-SPLIT 已闭合，下一步仍须 B24a 及后续实现批次实际运行 durable/real lanes。

## 12. 官方依据

### Amazon S3

- [PutObject API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html)：成功不产生 partial object、相同 key 默认 last-write-wins、`If-None-Match: *` 条件写、version ID 与 checksum header，支持本契约的完整单流与覆盖防护。
- [S3 conditional writes](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html)：`If-None-Match` 只允许 key 不存在时写入；它是防覆盖条件，不是 CRM 幂等或完整性证据。
- [S3 object integrity](https://docs.aws.amazon.com/AmazonS3/latest/userguide/checking-object-integrity-upload.html)：PUT 支持 full-object checksum，multipart ETag/合成 checksum 不能当通用完整 digest；单次 PUT 最大 5 GB。
- [GetObject API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html)、[HeadObject API](https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadObject.html)与 [S3 error responses](https://docs.aws.amazon.com/AmazonS3/latest/API/API_Error.html)：exact version、checksum 与 byte Range；S3 不支持单 GET 的 multiple ranges，不存在对象在有/无 `s3:ListBucket` 时分别返回 404/403，`NoSuchVersion` 是 404，因此 confirmation lane 必须证明权限与 exact-version 语义，不能把普通错误当 absent。
- [S3 consistency model](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html#ConsistencyModel)：PUT/DELETE 后 GET/HEAD 强一致；B24 仍要求 exact generation 和 unknown-outcome reconcile，而不依赖 LIST。
- [Enabling S3 Versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/manage-versioning-examples.html)：首次启用可能需要最多 15 分钟传播，readiness 必须等待并用 canary 证明。
- [Deleting object versions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/DeletingObjectVersions.html)：普通 versioned DELETE 只增加 delete marker；永久删除特定对象版本必须携带 versionId。
- [S3 Object Lock](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html)：retention/legal hold 可阻止 exact version deletion，simple DELETE 仍可能返回 200 并只加 marker。
- [Multipart upload](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html)与 [abort incomplete multipart lifecycle](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpu-abort-incomplete-mpu-lifecycle-config.html)：parts 在 complete/abort 前独立占用空间，支持首期显式禁用与未来另立 contract。
- [Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html)、[Object Ownership](https://docs.aws.amazon.com/AmazonS3/latest/userguide/about-object-ownership.html)与[encryption](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingEncryption.html)：支持 private bucket、ACL disabled、policy-only authorization 与 at-rest encryption readiness。

### Java NIO

- [SecureDirectoryStream](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/SecureDirectoryStream.html)：相对 open directory 的 race-free、`NOFOLLOW_LINKS` read/create/delete/move 操作；底层不支持时不能假定安全语义。
- [Files](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html)与 [StandardOpenOption](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/StandardOpenOption.html)：`CREATE_NEW` 的原子不存在检查、`NOFOLLOW_LINKS`、SYNC/DSYNC，以及 copy/move/delete 的 implementation-specific 边界。
- [FileChannel](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/channels/FileChannel.html)：seekable/absolute-position read 与 `force`，支持 native single range 和 crash durability procedure，但具体 filesystem 仍需 conformance。
- [POSIX file attributes](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/attribute/PosixFileAttributeView.html)：owner/group/permission 与创建时 permission evidence；实际 filesystem/build 必须复核。
- [FileStore](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/FileStore.html)：`getUsableSpace` 只是可能很快过时的 hint，不能替代 tenant quota 或 capacity guarantee。

### HTTP

- [RFC 9110 Range Requests](https://www.rfc-editor.org/rfc/rfc9110.html#name-range-requests)：single range 规范化、可满足性与 `206/416` 的协议依据；public header 仍由 B24 REST 边界生成，不透传 storage response。
