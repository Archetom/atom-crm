# B24-X 安全文件与业务附件组合 fixture

> 决策日期：2026-07-20
>
> 状态：跨 B24-S/I/A 的实现前组合契约已闭合，`B24-X/1` 的第一层 `MODEL` 已在 test source 可执行；本文冻结双 tenant、MySQL/storage crash、内容检查、发布、full/single-range、失权、父生命周期与确认删除的共同 driver、oracle、检查点和通过条件，不实现 API、生产 Java 类型、表、worker、对象存储、scanner 或生产 readiness
>
> 输入：[B24 总规划](../batch-B24.md)、[B24-S 存储契约](B24-S-storage-readiness.md)、[B24-I 内容检查契约](B24-I-content-inspection.md)、[B24-A 附件授权契约](B24-A-attachment-access.md)与现有 Work Task、Customer/Contact 及八类 CRM 目标的权限和生命周期边界

## 1. 闭合结论

`B24-X/1` 固定一套确定性的安全附件组合 fixture。它把此前分别冻结的 storage、inspection 和 target authorization 置于同一条 durable trace 中，同时证明：

- 上传意图先在 MySQL 中固定 exact tenant、目标、uploader、file identity、planned locator 和 deadline，之后才允许外部 storage I/O；请求中的文件名、tenant、target、checksum、locator、provider 或状态都不能改变该绑定；
- MySQL 与 storage 不组成分布式事务。storage 已成功而本地 finalize 未提交、inspection 已完成而结论未提交、`CLEAN` 已提交而发布未提交、delete 已生效而 confirmation 未提交，分别由稳定 identity、job/attempt、lease/fence 和只读 reconcile 收敛；
- `STORED`、`CLEAN` 与 active private link 只是发布的必要条件。publication worker 必须在目标根锁下重新检查 exact parent、file/link、content version、inspection attempt 和生命周期，才能把 publication 改为 `AVAILABLE`；
- full 与每一个 single-range 请求都重新执行 human、活动成员、`files:read`、目标域当前读谓词、exact target/file、`AVAILABLE`、storage readiness 和删除截止检查。授权事务结束后才打开 storage handle；无效、multi、不可满足或不受支持的 range 不能打开 storage，也不能 fallback full；
- uploader、任务参与者、角色或 CRM 数据范围失效后，后续列表、详情、full 和 range 在 storage 前失败。已经授权并开始的单次流不是 bearer grant，只能在原有 hard limits 内结束；任何恢复、续传或新 range 都重新授权；
- 撤回或 CRM 父删除的本地事务先关闭新访问并只建立一个 durable deletion work。storage delete response、delete marker、普通 `404`、本地路径缺失或进程重启都不是 `REMOVED`；只有 exact generation 在同一 binding 中得到 authoritative absence 且活动 handle/lease 已 drain，MySQL 才提交最终删除；
- 两个 tenant 即使使用相同展示名、bytes、声明媒体类型和 SHA-256，也不能共享 planned locator、stored generation、inspection result、quota、target visibility、download handle、deletion attempt 或 existence hint；
- adapter 只报告 storage/inspection native observation。期望状态由独立 oracle 根据已提交的 MySQL、typed evidence 和 driver history 计算；storage、scanner、target router 或 fixture driver 都不能直接提交 expected `AVAILABLE`/`REMOVED`。

本文件闭合的是组合设计门槛，不是可执行 runtime 或真实 adapter 通过报告。第一层独立模型现由 [`SafeFileComposedFixtureTest`](B24-X-model-fixture.md) 执行 `XS/XM/XL` 与 `X-01..X-08`；[B24-SPLIT](B24-SPLIT-implementation-boundaries.md) 已确定正式 B24a/b/c 的类型、API、schema、迁移、worker 和验收，仍须由实现批次建立真实 MySQL/Testcontainers 与 adapter conformance runner。

## 2. 固定双 tenant 世界与 profile 矩阵

### 2.1 Fixture identities

每个场景从新的 MySQL schema、空 storage/inspection stores、空 handle/admission counters 和固定虚拟时钟开始：

| 维度 | Tenant A | Tenant B |
| --- | --- | --- |
| tenant | `tenant-a` | `tenant-b` |
| human | `member-a`，同时具有 fixture 所需文件与目标能力 | `member-b`，独立能力和成员关系 |
| 首要目标 | `work-task-a`，含一个对 A 不可见的可选 CRM link | `customer-b`，可在 `ACTIVE/IN_POOL/DELETED` 间驱动 |
| file/intent | `file-a` / `intent-a` | `file-b` / `intent-b` |
| storage binding | `storage-binding-a`, revision `sa-17` | `storage-binding-b`, revision `sb-23` |
| inspection binding | `inspection-binding-a`, revision `ia-31` | `inspection-binding-b`, revision `ib-47` |
| jobs | `inspection-a-*` / `publication-a-1` / `deletion-a-1` | 完全独立 namespace |

A/B 使用相同的合成展示名、声明类型和生成式 benign bytes；两份 bytes 的 canonical SHA-256 也相同。展示名包含不会被输出到普通可观测性的 Unicode/path/header canary，但 storage locator 不从展示名派生。fixture 另提供：

- 合法 `4096`-byte 生成式对象，用于 full/range 与成功检查；
- `1` byte、fixture effective limit 边界、边界加一、short/long/slow/cancelled streams；
- declared/type mismatch、unknown container 和 disallowed type 的生成式 benign bytes；
- 完全合成的 `NO_THREAT_DETECTED`、`THREAT_DETECTED`、`UNSUPPORTED` 与 `FAILED` native observations。

组合 trace 的小尺寸只用于加速 crash/interleaving；`S-03` 仍独立证明 `1..67,108,864` bytes 和 64 KiB buffer，fixture 数字不得成为生产默认。仓库、CI artifact 和报告不保存恶意二进制、真实客户文件、完整 EICAR 内容、真实 locator 或 checksum 原值。

### 2.2 Storage 与 inspection lanes

同一核心 oracle 至少运行下列兼容 lanes：

| Lane | Storage profile | Inspection profile | Range 变体 |
| --- | --- | --- | --- |
| `XS` | versioned S3-like exact object | `STREAMING_ENGINE_V1` | full-only 与 native single-range |
| `XM` | versioned S3-like exact object | `OBJECT_EVENT_MANAGED_V1` | full-only 与 native single-range |
| `XL` | secure-local once-only content + descriptor generation | `STREAMING_ENGINE_V1` | full-only 与 native single-range |

secure-local storage 不能仅因拥有 generation 就宣称存在托管 exact-version object-event scanner；没有独立 managed service binding 时，`XL + OBJECT_EVENT_MANAGED_V1` 必须明确 `UNSUPPORTED/NOT_READY` 并在任何 object-event/scan open 前停止，不能被测试框架静默跳过。所有 lanes 都执行 unsupported/invalid range 的 open-count-zero 分支；只有当前 storage build/binding 证明 native exact offset read 时才执行成功 `206` 分支。

真实 adapter conformance 可以增加兼容 lane，但不能删除 `XS/XM/XL` 的 provider-neutral semantics，也不能把 S3-like simulator、临时目录、合成 scanner 或 managed tag 通过升级成 production readiness。

### 2.3 Target lanes

`WORK_TASK` 与 `CUSTOMER` 是组合主 trace 的两个固定目标：前者证明参与关系和 CRM link 不互授权限，后者证明 `IN_POOL` 暂停、恢复与父软删除。共享 target-driver 还必须能为 `CONTACT`、`LEAD`、`OPPORTUNITY`、`CONTRACT`、`INVOICE`、`RECEIVABLE` 和 `RECEIVABLE_PLAN` 执行 B24-A 对应的 read/collaborate、非删除生命周期、关闭状态和父删除规则。

B24a 实现只能先声明 Work Task target lane；B24b 接入某个 CRM target 时必须运行该 target 的完整授权/父锁/删除子矩阵。不能用 Work Task 通过报告代替八个 CRM resolver、typed parent proof 和生命周期的各自证据。

## 3. 独立 oracle、可观察 stores 与 checkpoint

### 3.1 Store 所有权

组合 harness 必须把下列 stores 分开，进程重启会丢弃所有未持久化 future、handle、mock history 和 dedup cache：

| Store / projection | 只读 oracle 必须观察 |
| --- | --- |
| target store | tenant、typed target、生命周期、版本、当前 read/collaborate 可见谓词与锁顺序；不暴露业务敏感字段 |
| core MySQL | intent/file/link/tombstone、三轴状态、planned/stored opaque identity、actual bytes、SHA-256 evidence digest、job/attempt/lease/fence、deadline、optimistic version、publication/deletion work |
| storage private store | binding/locator/generation、descriptor/content/version、actual bytes/digest、open handles、store/probe/range/delete/confirm/abort counters、partial artifacts；provider key/path 只作泄漏 canary |
| inspection private store | stream chunks/EOF/native reply，或 exact-version event/dedup/tag reconcile；definition/binding generation、open/close/cancel/admission counters |
| HTTP/stream boundary | public status/header allowlist、body length/digest、first byte、client cancel、upstream offset/bytes、buffer high-water；不保存完整 body 到报告 |
| authorization | human/member/file capability、target resolver outcome、target/file mismatch 分类、authorization transaction end 与 storage open ordering |
| resource policy | tenant/global bytes、record cap、upload/scan/download concurrency、temporary space、control reserve、unknown-input partitions 和最终释放计数 |
| forbidden sinks | API/ordinary metadata、log、trace、metric、event、Activity、Redis、cache、report、exception、test output 与对象 `toString` 的 canary 命中数 |

oracle 只能读取已提交投影和安全计数，不能调用 adapter 的 expected-state calculator。native store 中的 key、path、version、scanner message 和 bytes 只能参与等值/计数断言，失败输出只显示固定 reason 与 evidence digest。

### 3.2 正交状态与派生可用性

oracle 独立跟踪：

| 轴 | 状态 | 允许推进的证据 |
| --- | --- | --- |
| bytes | `ABSENT/UPLOADING/STORED/REMOVING/REMOVED/UNKNOWN` | exact store/probe receipt；exact delete 与 authoritative absence 分开 |
| inspection | `NOT_RUN/PENDING/CLEAN/REJECTED/INDETERMINATE` | exact content version、attempt/fence、detector/scanner/policy 的核心归一 |
| publication | `PRIVATE/AVAILABLE/WITHDRAWN/DELETED` | 目标根锁内的本地事务；storage/scanner 不能直接改变 |
| relation | immutable target binding + current private/active/tombstone relation | typed same-tenant parent proof 与一 file 一 target 的 MySQL 不变量 |

唯一普通可读条件为：

```text
bytes=STORED
AND inspection=CLEAN
AND publication=AVAILABLE
AND active exact target/file relation
AND current human/target authorization
AND storage read operation ready
AND before withdrawal/deletion deadline
```

任一条件缺失都不产生列表项或 storage open。`CLEAN` 不是发布，storage 可读也不是授权，隐藏目标也不是空附件页，delete effect 也不是 `REMOVED`。

### 3.3 每步共同 checkpoint

每个 driver 动作后至少比较：

- A/B target/file/intent/content version、三轴状态、link/tombstone 与 optimistic version；
- planned locator、stored identity、actual bytes/SHA-256 evidence 的 same-attempt equality；不输出 opaque 原值；
- inspection/publication/deletion job generation、attempt、lease owner/fence、not-before、deadline、result digest 与 effect count；
- MySQL transaction begin/commit/rollback、持锁 target/file/job 集合和外部 I/O count；任一 storage/scanner/HTTP body I/O 期间活动业务事务数必须为零；
- storage allocate/store/probe/full/range/delete/confirm/abort、inspection open/read/event/close/cancel 与 handle/admission counters；
- authorization-before-file/storage ordering、列表 total/page、full/range public outcome、first offset、upstream/body byte count 和 header allowlist；
- B tenant 的所有 durable state/counters 未被 A 场景改变，除非该 step 明确只驱动 B；
- forbidden sink hit、cross-tenant hit、transparent reopen、double release、second logical publication/deletion 均为零。

## 4. Driver 动作词汇与禁止捷径

共享 harness 至少提供：

- `createIntent(caller, target, declaration)`：经过真实 human/member/file/target collaborate guard，在目标根锁内保存不可改绑 intent 与 planned locator；
- `openUpload(caller, target, file)` / `streamUpload(chunks)`：重新授权 exact uploader/target/intent，在事务外执行有界单流 store；
- `finalizeStored(receipt)` / `reconcileStore(intent)` / `abortIncomplete(intent)`：以 planned locator 和 stable attempt 提交或对账 exact receipt；禁止 listing；
- `claimInspection(file)` / `driveStreamedInspection(...)` / `deliverManagedEvent(...)`：取得 durable lease/fence 后，在事务外驱动对应 native profile；
- `commitInspection(evidence)` / `runPublisher(file)`：先提交核心三态，再以独立目标根事务尝试 publication；
- `listAttachments(caller, target)` / `requestFull(...)` / `requestRange(...)`：经过真实 target-first authorization、HTTP range parser 和 typed storage handle；
- `revokeFilesCapability(...)` / `changeTargetScope(...)` / `changeTaskParticipant(...)` / `enterPool(...)` / `restoreFromPool(...)`：只修改权威权限或父状态，不直接编辑 file；
- `withdraw(caller, target, file)` / `deleteParent(...)` / `deleteTask(...)`：按 B24-A 锁序提交关系关闭或任务历史拒绝；
- `claimDeletion(file)` / `requestExactDelete()` / `confirmExactAbsence()`：复用唯一 deletion attempt，分别驱动 effect 与只读 confirmation；
- `cutAt(boundary)` / `restartCore()` / `restartStorage()` / `restartInspector()` / `advanceClock(...)` / `abortClient(...)`：注入 commit acknowledgment 丢失、进程终止、deadline 与 cancellation，并只从 durable stores 恢复。

driver 不得直接设置 `STORED/CLEAN/AVAILABLE/REMOVED`，不得生成业务授权结论，不得把 adapter private key/path/version/tag 当作 core identity，不得伪造 absence，不得在测试中调用 repository 绕过 use-case/worker transaction，也不得以 mock invocation count 代替 MySQL unique/FK/lock/lease 证据。

## 5. `X-01` readiness、意图绑定与双 tenant

固定顺序为：

1. 验证 human、活动成员、`files:write`、目标域入口能力和 collaborate 可见性；任一失败都使后续 file/link/storage/inspection read count 为零，目标域只执行到判定该失败所需的受控查询；
2. 在目标根锁内检查 lifecycle、record active-attachment cap 与 tenant/global admission，创建 `file/intent/planned locator`；
3. A/B 用相同 declaration 和 bytes 分别创建意图，得到不同的 tenant binding、file、planned locator、quota reservation 和 job namespace；
4. 请求体增加 tenant、uploader、目标改绑、locator、provider、status、scanner result、object key/path 或任意 metadata 时，在外部 I/O 前拒绝；
5. storage、inspection 或 deployment policy 缺失/过期/漂移时只使 B24 对应 operation `NOT_READY`，普通 Work/Customer API 与既有数据库状态不变。

共同负向矩阵覆盖 `A-01..A-04`、`A-12..A-14`：只拥有文件或目标一侧能力、目标隐藏/跨 tenant、target/file mismatch、未知 target、同 file 并发绑两个 target 和不存在 typed parent。隐藏 target 分别拥有 0、1 和上限个附件时，安全结果类型、storage call count 和普通可观测性必须一致；真实无附件空页只在 target 已授权后返回。

MySQL 必须最终仲裁一 file 一 target、same-tenant typed parent、record cap 与 optimistic version；JVM pre-check 只能改善错误，不能让两个并发 intent/link 都提交。

## 6. `X-02` 单流上传、storage/MySQL crash 与 finalize

### 6.1 成功主 trace

| Step | Driver / failure point | 必须收敛的结果 |
| --- | --- | --- |
| 1 | `createIntent` 提交 | planned locator、target/uploader/deadline 已 durable；storage object 尚不存在 |
| 2 | `openUpload` 后流式 store 成功，adapter exact object 已 durable | storage 返回同 attempt 的 immutable generation、actual bytes 与 canonical SHA-256；core 仍不可推断 `STORED` |
| 3 | finalize 短事务锁 file/version，核对 receipt 与 declaration | `bytes=STORED`、stored identity/evidence 与 inspection job 原子提交；没有 publication |
| 4 | HTTP response 丢失后客户端重试 | 重用同 intent/attempt/receipt，no-op 或返回同一安全状态；不 store 第二对象、不创建第二 inspection job |

### 6.2 必注入 crash cuts

- planned locator commit 前/后、storage I/O 前：未提交意图永远不能产生对象；已提交意图只使用其 planned locator；
- storage conditional/create-new effect 后、receipt 返回前：重启只可按 exact planned locator probe，收敛为 same receipt、exact incomplete cleanup 或 `UNKNOWN`；不能 list、换 key 或重传不同 bytes；
- receipt 返回后、finalize commit 前：MySQL 仍不是 `STORED`；reconciler 核对同 generation/bytes/digest 后重复 finalize；
- finalize commit acknowledgment 丢失：重启读取已提交 file/job，duplicate finalize 不增加版本以外的 effect，不重复 reserve bytes；
- MySQL rollback 与 storage 已成功：对象保持 quarantine/incomplete，不发布；deadline 后由 durable exact cleanup 处理；
- parent withdraw/delete 在 finalize 前提交：receipt 可被记录为待清理，但不能创建可发布 active link；只建立一个 deletion work。

空流、未知或不一致长度、fixture limit+1、慢流、提前断开、同 intent 不同 bytes、duplicate store、覆盖已有 generation、client checksum/ETag/mtime/fileKey 单独证明和 multipart native call 全部失败关闭。buffer high-water `<=64 KiB`，所有 cancel/close/admission 只释放一次，storage success 不能把上传 HTTP 响应改成 `AVAILABLE`。

## 7. `X-03` 内容检查、lease/fence 与结果提交

从 exact `STORED + inspection=PENDING + publication=PRIVATE` 开始，每个兼容 lane 执行：

1. worker 在短事务中取得 file/content version、inspection attempt、lease/fence 和 readiness revision；事务提交后才打开 exact storage stream 或等待 exact-version event；
2. streamed lane 从 offset 0 读到规范 EOF 并执行 bounded detector/scanner；managed lane 只接受当前 binding/version/schema 的 durable native event，并另行 probe length/integrity；
3. adapter 只产生 typed type/scanner evidence；核心在结果提交事务中重查 file version、lease/fence、definition basis/freshness、policy allowlist 和 stronger result；
4. allowed identified type + complete no-threat + fresh evidence 只提交一次 `CLEAN` 和一个 publication work；仍为 `PRIVATE`；
5. threat 或确定 type conflict/disallowed type 提交 `REJECTED`；unknown/container/password/limit/timeout/failed/stale/mismatch 提交 `INDETERMINATE`；两者均没有 publication effect。

scanner/native result 完成后、MySQL result commit 前必须 crash。恢复时 streamed lane 可以用新 attempt 重扫同一 immutable object，managed lane可以重消费同一 durable event；旧 lease worker 的迟到结果因 fence/version mismatch no-op。duplicate/out-of-order/wrong-version/wrong-binding/tag-only 结果不能推进当前 file。

已提交 `CLEAN` 后到达同 generation 的有效 threat evidence 时，新的目标根事务必须先使 publication 不可用/阻止发布，再提交 strongest `REJECTED` 与唯一 cleanup work。迟到 clean、旧 definitions 和普通 worker retry 永远不能覆盖 `REJECTED`。

A/B 的 queue、temporary bytes、definition generation、event dedup、storage open 和 result state 全部分区。用 B event/lease/native token 或相同 checksum 完成 A inspection 必须在 byte read/核心提交前失败。

## 8. `X-04` 发布、撤回与父删除总序

publication worker 不携带 `AuthenticatedCaller`，也不继承 uploader 当前权限。它只能在 B24-A 的目标锁顺序中执行一个短事务：

```text
owner-domain guard when required
  -> exact target root
  -> file/link root
  -> publication/deletion work
```

事务内必须重新确认 exact parent 存在且 lifecycle 允许、private link 未撤回、`bytes=STORED`、`inspection=CLEAN`、content/storage/attempt evidence 全部相同且未过 deadline。满足时 publication 从 `PRIVATE` 到 `AVAILABLE` 一次；worker crash 或 commit acknowledgment 丢失后只读取 MySQL 判定是否已经发布，不重复建 link/事件或改变业务 target 版本。

固定竞态结果为：

| 竞态 | 唯一允许收敛 |
| --- | --- |
| `CLEAN` commit / publisher crash | 保持 `PRIVATE + durable publication work`，恢复后重试；不能从 scanner 内存宣布 available |
| publisher / human withdraw | 锁序决定先后；publish 先则随后撤回，withdraw 先则永不 publish；最终不可下载且一个 deletion work |
| publisher / CRM parent soft delete | publish 后由父事务撤回，或父先删除使 publisher no-op；最终父 deleted、附件不可下载且一个 deletion work |
| publisher / Work Task delete | task 因任何 intent/link/tombstone history 整体拒绝；file 状态不因失败删除改变 |
| publisher / Customer or Contact `IN_POOL` | 不删除、不改 inspection/publication；pool 期间所有通用附件操作暂停，恢复后按新范围重新判定 |
| publisher / uploader 失权 | 若 parent/private link 仍有效可发布；失权 member 不能列表或下载，worker 不被伪造成 human |

Lead conversion、Opportunity close、Contract void、Invoice issue、Work Task finish/cancel 不自动发布、复制、转移或撤回附件；当前 target guard 仍决定后续 human 操作。publication 不能生成 Activity、业务自定义 `FILE` 值、业务 target 版本或 provider URL。

## 9. `X-05` 列表、full、single range 与取消

### 9.1 Target-first 列表与 full

普通列表在一个已授权 exact target 下做有界 `AVAILABLE` 分页；`PRIVATE/REJECTED/INDETERMINATE/WITHDRAWN/DELETED/REMOVING` 不占页、不计 total。每个 full 请求：

1. 验证 human、active member、`files:read` 与 target read predicate；
2. 在短只读事务内核对 exact target/file、active relation、`AVAILABLE`、stored identity、deadline 和当前 storage operation readiness，并登记仅供本调用栈消费的 open authorization；
3. 事务结束后才打开一个新的 typed handle，按 exact generation 从 offset 0 流式返回；
4. public headers 只由核心安全展示名、canonical media type 和 exact size 生成；不透传 provider headers、validator、URL、key/path/version 或 checksum；
5. EOF/cancel/deadline/length mismatch 后 handle/admission 最终归零，底层 release count=`1`，transparent reopen=`0`。

授权已经提交并开始的流只可在原 request 的 open/idle/stream/byte deadline 内结束。能力或目标范围随后失效会阻止所有新请求，但不把原 handle 变成可缓存或可恢复 grant；新 full/range/resume 必须重新授权。withdraw、parent delete、retention cutoff 会关闭所有新请求并 best-effort cancel 已登记 handle；authoritative absence 前必须等待 adapter-managed active handle/lease drain。

### 9.2 Native single-range 矩阵

对 `4096`-byte fixture object，每一行都是新的 human authorization、audit 和 storage handle：

| Range | 规范选择 | Public result | Storage oracle |
| --- | --- | --- | --- |
| `bytes=512-1535` | offset `512`, length `1024` | `206`, exact `Content-Range/Length` | native open `(512,1024)`，upstream bytes=`1024` |
| `bytes=3584-` | offset `3584`, length `512` | `206` | 不读取前 `3584` bytes |
| `bytes=-256` | offset `3840`, length `256` | `206` | suffix 在 open 前规范化 |
| `bytes=0-8191` | 截到 `0-4095` | `206` | 只打开实际选择区间 |
| `bytes=4096-` | 不可满足 | `416` | open/read=`0` |
| multi、非十进制、逆序、负值、溢出或超过请求上限 | 无 typed range | `416` | open/read=`0`，不 fallback full |

full-only profile 收到任何 Range 都在 storage open 前拒绝，并明确不声称 native range。成功 range 的 upstream byte count 必须等于选择区间，seek-by-reading-and-discarding 前缀、完整对象读取后裁剪、复用旧 handle 或首字节后透明续传都使该 build 的 range conformance 失败。

client abort、open/idle/stream deadline、max bytes、withdraw/removal cancel、首字节前后 storage failure 与 core/storage process crash 都必须参数化。首字节后失败只关闭响应；恢复不能重新建立 open command或继续传输。任何拒绝不得改写 file/publication 状态。

## 10. `X-06` 失权与目标生命周期

对 A/B 和每个 target lane 执行 B24-A `A-01..A-13`，其中固定证明：

- 删除 `files:read`、停用 membership、缩小目标 read data scope、移除 Work creator/assignee/follower/manager 参与关系后，下一次 list/detail/full/range 在 file/storage 前返回同一安全拒绝；恢复权限只恢复仍为 `AVAILABLE` 的附件；
- uploader 在 inspection 期间失去 `files:write` 或 target collaborate 权，不取消已经接受的 durable inspection/publication；但其 private status、重新上传和 withdraw 都立即失败；
- Work Task 参与者即使看不到 task 的可选 CRM link 仍可访问 task 自身附件，输出不暴露 link；只看得到 CRM target 而不是 task 的 caller 不能访问 task 附件；
- Customer/Contact `IN_POOL` 期间 list/download/upload/private status/withdraw 全部不可用且 storage delete count 不增加；恢复 `ACTIVE` 后按新的 owner/collaborator 范围判定；
- Lead conversion、Opportunity close/reopen、Contract void、Invoice issue/reset、Work Task finish/cancel 保留附件但不扩大当前权限；
- CRM parent soft delete 的同一本地事务撤回有界 current/private links并为已存在 bytes 建立 deletion work，不等待 storage；之后隐藏 target、无 target 与 target/file mismatch 的 public 行为一致；
- Work Task 有任一 intent、current link 或 tombstone history 时，task delete 与 comment-history 相同整体拒绝；不能先清 file history 后把任务伪装成未协作。

失权本身不是字节删除事件，也不产生 tenant 管理员文件库。系统不承诺扫描所有业务权限变化并实时终止已经开始的 response；其安全边界是每次请求重新授权、open command 不可复用、单流严格有界，以及 withdrawal/parent delete/deadline 对 file-local handle 的取消与 drain。

## 11. `X-07` durable 删除与 authoritative absence

### 11.1 删除主 trace

从 `AVAILABLE` 和一个活动慢流开始：

| Step | Driver 动作 | MySQL/storage 必须提交 |
| --- | --- | --- |
| 1 | human withdraw、父删除或 retention cleanup 取得目标/file 锁 | 同一本地事务把 publication 关闭、bytes 置为 `REMOVING` 并创建唯一 stable `deletion-a-1`；所有新 read 拒绝，活动 handle best-effort cancel |
| 2 | deletion worker claim | durable lease/fence 与 exact stored identity/deletion attempt；不接收请求提供的 tenant/key/path/version |
| 3 | `requestExactDelete` | 只删除该 binding 的 exact generation；effect/receipt 可 durable，但 core 仍不是 `REMOVED` |
| 4 | `confirmExactAbsence` | 独立只读 procedure 证明 exact descriptor/version/content absent、binding/current procedure 未漂移且 active handles/leases 已 drain |
| 5 | confirmation commit | MySQL 在一个短事务中以 attempt/fence/version 提交 `bytes=REMOVED`、settle deletion work并保留最小 tombstone；late store/scan/publish/read result 不复活 |

### 11.2 Crash、漂移与攻击分支

- deletion work commit 前/后、adapter delete ledger/effect 前/后、delete response 前/后、absence probe 后/confirmation commit 前、MySQL confirmation commit acknowledgment 丢失都必须终止进程并恢复；每次只复用同一 logical deletion identity；
- delete 2xx/void、S3 simple delete marker、current-key 或 ordinary read `404`、local `Files.delete` success、descriptor/path 暂时 missing、permission/KMS/timeout、Object Lock/retention blocked 都不能直接写 `REMOVED`；
- confirmation readiness 漂移、binding revision 改变、active handle 未 drain、local root/device/mode 变化或无法区分 403/404 时保持 `REMOVING/PENDING`、不可下载并有界退避；
- exact absence 后晚到同名/同 key 新 generation 是严重 ownership conflict，不复活旧 file，也不能把新对象当作旧 deletion 已完成；
- B credential/binding、相同 B bytes/checksum、B delete result、伪造 locator/version 或跨 target tombstone 不能完成 A deletion，A cleanup 也不能枚举或删除 B；
- media/download slots 饱和、read readiness 失效和 target 权限全部撤销时，deletion control reserve 仍能执行；未确认删除不得被 TTL/LRU 淘汰。

fixture 最多证明受控主存储 exact generation 对新读取已 authoritative absent。replica、backup、Object Lock、法律保全和物理介质销毁仍按 B24-S 的部署证据单独报告，公共 API 不产生 `physicallyDestroyed` 声明。

## 12. `X-08` 配额、bounded cleanup 与禁止值扫描

固定容量矩阵覆盖 tenant/global ready/quarantine bytes、未完成 intents、每 target 100 active attachments、upload/inspection/download 并发、scanner queue/temp、process reserve 和 deletion control reserve：

1. A 占满 upload 或 inspection quota 时，B 在自己的 partition 与剩余 global slot 中仍可推进；不能驱逐 A/B 的 live reservation 或把未知 tenant 变成新分区；
2. record cap、bytes addition 和 counter arithmetic 在 MySQL/共享 admission 最终仲裁，overflow、负数、double release 与跨副本超发失败关闭；
3. queue full 在 storage/scanner open 前拒绝，stream/scan/download failure、rollback、crash 和 cancellation 后 reservation 最终回收；
4. media/download slots 全满时 deletion control lane 仍可 claim/effect/confirm；普通工作不能借用该 reserve；
5. bounded sweeper 分页处理 expired intent、incomplete artifact、长期 `INDETERMINATE`、`REJECTED`、withdrawn/private link 和 deletion tombstone；每个 candidate 重新核对 tenant、exact identity、active link、lease 与 deadline；
6. sweeper 不删除 `AVAILABLE` 或仍有 active/private relation 的 bytes，不越过另一个 tenant，不以 bucket/list/path scan 猜 owner，不因到达页上限写“全部完成”；
7. storage、inspection 或 policy unavailable 只积累有界 pending/安全告警，不影响普通 CRM API，也不安装 clean-by-default 或 delete-by-not-found fallback。

每个场景结束运行统一 sink scan。除已授权 full/range response body 和 adapter 私有生成式 source 外，bytes、展示名、checksum、locator/key/version/path/root/bucket/account、target name、scanner/threat/parser detail、provider header/body/error、credential 和禁止 canary 的命中数必须为零。专用 tenant-scoped metadata 可以保存展示名、canonical integrity evidence 和 opaque stored identity，但 conformance 报告只能保存字段存在性、计数和 digest，不能输出原值。

## 13. 统一 MySQL/storage/inspection crash 矩阵

`X-01..X-08` 至少在下列相邻 durable 边界前后注入 core、storage 或 inspector 进程终止，并重复 B tenant 隔离、resource release、ordinary CRM sentinel 与 sink scan：

| Boundary | 恢复断言 |
| --- | --- |
| target lock / intent + planned locator commit | 未提交无 I/O；已提交只能使用 exact immutable target/uploader/locator |
| storage effect / receipt | 只按 planned locator probe；same receipt、exact cleanup 或 unknown，不 list/换 key/覆盖 |
| receipt / MySQL finalize + inspection job | `STORED` 与 job 全有或全无；commit-ack loss 幂等读取，不重复 quota/effect |
| inspection lease / storage open or managed event wait | 外部 I/O 无业务事务；旧 fence 不能提交，handle/admission 可恢复回收 |
| native inspection result / core conclusion commit | 重扫或重消费同 exact subject；结果不从 adapter 内存直接变三态 |
| `CLEAN` conclusion / publication target-root commit | clean 不等于 available；重启只由 durable publication work推进 |
| publication / withdraw or parent delete | 同一目标根全序；最终不可出现 deleted parent + downloadable link |
| download authorization / storage open / first byte / EOF | 事务外 I/O；崩溃后不 reopen/续传，handle/counter 最终释放 |
| withdraw/parent-delete commit / deletion claim | 新 read 立即关闭；一个 logical deletion work，不等待 storage |
| delete ledger/effect / absence probe | delete success 不冒充 absence；恢复只作用 exact generation |
| absence evidence / MySQL `REMOVED` commit | confirmation fence/version 幂等；late receipt/event/publisher 不复活 |
| counter acquire / durable work / release | rollback、cancel、lease takeover 和 crash 后无负数、双释放、跨 tenant 借位 |

调度器必须参数化 commit 成功但 acknowledgment 丢失、transaction rollback、worker lease expiry/takeover、readiness expiry、client cancel、父状态变化和两个 replica 竞争。允许 exact immutable read/scan 重做和 exact deletion attempt 的声明幂等重投；不允许第二 storage generation、第二 active link、第二 logical publication/deletion、旧 fence 覆盖、transparent stream reopen 或适配器自报 expected state。

## 14. 跨契约覆盖矩阵

| 场景 | B24-S | B24-I | B24-A / B24 总门槛 |
| --- | --- | --- | --- |
| `X-01` readiness/intent/tenant | `S-01/02/13/14` | `I-10/12/13/14` | `A-01..04/12..14`，总门槛 1..3/9..11 |
| `X-02` store/finalize crash | `S-03..06/10/12/13` | exact subject 前置 | 单一目标、父先于 file；总门槛 4/5/10 |
| `X-03` inspection | exact probe/full read | `I-01..I-14` | worker 无 human authority；总门槛 5/6/11/14 |
| `X-04` publication races | same generation/late receipt | strongest result、commit-time fence | `A-07..11/14`；总门槛 5/7/10 |
| `X-05` full/range/cancel | `S-07/08/11/14` | only published clean bytes | `A-01..06/10/13`；总门槛 2/7/8 |
| `X-06` auth/parent lifecycle | no delete on mere auth loss/pool | scanner 不授予 access | `A-01..13`；总门槛 2/7/8 |
| `X-07` exact delete/confirm | `S-09..11/13/14` | rejected/withdrawn cleanup subject | `A-09..11/14`；总门槛 5/7/12/13 |
| `X-08` limits/cleanup/leaks | `S-03/08/12..14` | `I-05/10..13` | hidden-target/cap/tenant isolation；总门槛 1/9/11/13/14 |

每一行必须由同一 scenario 的联合 checkpoint 通过。不能把 storage、scanner、authorization 的三个孤立 mock 测试相加后声称组合通过，也不能因为 `S/I/A` 单项报告都存在就省略 MySQL 与外部 effect 之间的 crash cuts。

## 15. 执行层次与 machine-readable report

同一 `X-01..X-08` ID 至少运行在三层：

1. **独立规范模型**：`B24-X/1` 的 `MODEL` execution layer 已在 test source 以分离 store、process reconstruction、`XS/XM/XL` 和 safe report 执行 `X-01..X-08` 的确定性主 trace；后续仍可扩展属性化/穷举调度，model 不依赖实现 adapter；
2. **durable composition**：真实 MySQL/Testcontainers、真实 application transaction/target guard、真实 HTTP streaming/range parser、durable versioned-object/secure-local simulator、两个 inspection drivers、虚拟时钟和可终止/重建 worker；使用 MySQL unique/FK/lock/lease 而不是内存 map 证明；
3. **current real adapter build**：在隔离、无真实客户数据的批准环境中，对当前 S3-like 或 secure-local storage、detector/scanner profile 和部署 policy 运行兼容 lane；报告仍由独立 core oracle 计算，adapter 只能提供 safe observation/counter。

纯模型不能替代 durable MySQL，durable simulator 不能替代当前 backend/build conformance，真实 storage 通过也不能替代 target authorization 与 inspection policy。任何 real lane 缺少 range、managed definitions、exact delete/confirmation、quota、isolation 或 current fixture evidence 时，只能把对应 operation 报告为 `UNSUPPORTED/NOT_READY`。

报告版本固定为 `B24-X/1`，至少包含：

- core/storage/inspection contract 与 immutable build/config/binding evidence digest、target lane、storage/inspection profile、definition basis、range mode 和 isolation class；
- MySQL schema/fixture digest、执行层级、虚拟时钟、正的 fixture limits、shared admission 声明和 conformance validUntil；
- `X-01..X-08` 每个 step/cut/lane 的 pass/fail 与固定 safe reason；
- 每 tenant intent/store/finalize/inspection/publication/list/full/range/withdraw/deletion/confirmation 最终计数和 stable identity digest；
- storage/inspection/HTTP 的 first offset、upstream/body byte count、buffer high-water、open/reopen/cancel/close/double-release、invalid-range-open 和 active-handle counters；
- publication 写入至多一次、active link 至多一个、logical deletion 至多一个、`REMOVED` 只在 authoritative confirmation 后写一次、late resurrection 和 cross-tenant mutation 均为零；
- ordinary CRM sentinel、quota/control reserve、bounded sweeper、forbidden sink 和 raw-canary 扫描结果。

报告不得包含真实 tenant/member/target/file ID、展示名、bytes、checksum 原值、locator/key/version/path/root/bucket/endpoint/region/account、IAM/KMS/credential、scanner/threat/parser detail、request/response body/header、provider request ID、host/device/FileStore 可关联原值或异常 message。schema、build、backend、filesystem/mount、binding、policy、limits、allowlist、definition basis、range/delete/confirmation procedure 或 target resolver 改变后必须重跑；旧报告不能自动迁移 readiness。

## 16. 闭合与剩余门槛

B24-X 已冻结：

- 双 tenant 与 `XS/XM/XL` 兼容 profile lanes、Work/CRM target-driver 和 test-private 合成值边界；
- 独立 core oracle、四类可观察 store、driver 禁止捷径与每步联合 checkpoint；
- intent → store/finalize → inspection → publication → full/range → withdrawal/deletion/confirmation 的完整 durable trace；
- MySQL commit/rollback/ack-loss、storage/inspection effect、worker lease takeover、target lifecycle 与 HTTP stream 的统一 crash/竞态矩阵；
- uploader/reader 失权、Work/CRM 互不授予、pool 暂停、父删除和任务历史保护；
- exact-generation delete 与 authoritative absence、handle drain、control reserve 和 late-result 不复活；
- `X-01..X-08` 到 `S-01..S-14`、`I-01..I-14`、`A-01..A-14` 与 B24 总门槛 14 项的覆盖映射；
- 三层执行证据与不含敏感原值的 `B24-X/1` report schema。

仍未实现或证明：

- 任何生产 file/attachment domain/application/API 类型、能力目录项、Controller/Facade、storage/inspection port、PO/mapper/Flyway、job worker、HTTP streaming、Spring bean 或配置；test-only model 类型不属于 runtime；
- 真实 MySQL B24 schema、typed parent foreign key、version/lease query、Testcontainers composition harness 与进程 crash runner；
- 真实 S3-like SDK、本地 secure filesystem、scanner/detector、managed event authentication、tenant/global admission、encryption、backup/deletion policy 与 current build report；
- B24a Work Task 纵切、B24b 八类 CRM 扩展、B24c B05 `FILE`/OA 提交前文件的正式 API、迁移和验收；
- 预签名传输、multipart、在线预览、OCR/解析、公共分享、法律保全、物理介质销毁或 B22 recording 复用。

因此 B24-X 解除的是“跨 storage/inspection/authorization/MySQL 的组合 oracle 未定义”这一实现拆批前置门槛。B24-SPLIT 已闭合，下一步只从 B24a 开始；任何 runtime 或 production enablement 仍必须在后续实现批次实际运行本 fixture、S/I/A 单项 conformance、Docker/MySQL 集成套件和真实 adapter lane。
