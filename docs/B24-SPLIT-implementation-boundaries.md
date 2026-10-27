# B24-SPLIT 正式实现拆批与冻结边界

> 决策日期：2026-07-20
>
> 输入：[B24 总规划](../batch-B24.md)、[B24-S 存储契约](B24-S-storage-readiness.md)、[B24-I 内容检查契约](B24-I-content-inspection.md)、[B24-A 附件授权契约](B24-A-attachment-access.md)与 [B24-X 组合 fixture](B24-X-composed-fixture.md)
>
> 状态：B24a 的 Work Task 内核、B24b 的八类 CRM target/V60 与 pool pause durable job/V61，以及 B24c 的 FILE 字段、CRM/OA 草稿、受控候选上传、OA 提交绑定、approval/stage 快照、重授权下载与 V62/V63/V64 均已实现。剩余业务覆盖已进入 [B29](../batch-B29.md) 规划；本文不表示生产 adapter readiness 已通过。

## 1. 冻结结论

B24 的正式实现只按下列顺序交付，不允许把公共文件库、CRM target、B05 `FILE` 和 OA 文件同时塞进一个无法独立验收的批次：

| 子批次 | 唯一交付 | Flyway | 完成后仍不包含 |
| --- | --- | --- | --- |
| `B24a` | provider-neutral 文件内核、Work Task 单目标纵切、代理上传/下载、正交状态、storage/inspection ports、durable job/lease/fence、secure-local + streamed-engine 首个可部署 profile 和 `B24-X/1` Work lane | `V59__create_attachment_foundation.sql` | 八类 CRM target、B05 `FILE` 值、OA 文件、S3/托管 scanner 生产 bean、预签名和 multipart |
| `B24b` | 在同一内核上一次性补齐 Customer、Contact、Lead、Opportunity、Contract、Invoice、Receivable、ReceivablePlan 的 typed target adapter、父锁与父删除联动，以及 Customer/Contact pool pause durable defer | `V60__add_crm_attachment_targets.sql`、`V61__add_attachment_paused_job_state.sql` | 新的文件状态/API/worker、Product/ReturnVisit/Announcement/Activity、B05 `FILE` 值、OA 文件 |
| `B24c` | 八类 CRM 的结构化 FILE 字段值、创建前 file-field draft、字段访问/生命周期、B11/stage 文件快照，以及 OA 表单提交前文件和同事务冻结 | `V62__create_file_field_and_oa_file_binding.sql`、`V63__repair_file_field_draft_withdrawal_and_snapshot_headers.sql`、`V64__cascade_file_snapshot_headers_with_parent_cleanup.sql` | [B29](../batch-B29.md) 承接 OA 提交后附件、Product/ReturnVisit FILE、营销表单 FILE 和公告附件；外部分享、预览/解析/OCR 继续后置 |

依赖固定为 `B24a -> B24b -> B24c`。B24b 不能复制或分叉 B24a 的 file root、job、storage 或 HTTP transport；B24c 不能把 file ID 塞入现有 `Map<String,List<String>> customFields/values`。单个实现 PR 可以提前落下一部分内部代码，但只有本表整行验收全部通过时才可宣称对应子批次完成。

本轮同时选择第一条可部署组合为：

```text
LOCAL_SECURE_NIO_V1
  + BOUNDED_MAGIC_V1
  + STREAMING_ENGINE_V1
```

`infra/external` 在 B24a 提供 secure-local 和受控 streamed scanner adapter；默认组合仍为 disabled。S3-like 与 managed exact-version event 在 B24a 只需要 model/durable conformance driver，不安装生产 bean。它们成为生产选项时仍须提交当前 build、binding、政策和真实环境的 `B24-S/1 + B24-I/1 + B24-X/1` 报告。

首个公开媒体目录固定为 `application/pdf`、`image/png` 和 `image/jpeg`。它们只以 `Content-Disposition: attachment` 下载，不允许浏览器内联预览。B24a 的公开有效单文件硬上限固定为 `16,777,216` bytes（16 MiB）；B24-S 的 adapter contract 仍必须独立证明 `67,108,864` bytes storage ceiling，二者不得混写。新增媒体类型、container detector 或提高公开上限都属于新的契约版本和 conformance 变更。

## 2. 共同 Java 边界

### 2.1 Domain 所有权

以下名称和包归属在 B24a 固定：

| Java 类型 | 包 | 所有权 |
| --- | --- | --- |
| `AttachmentFile` | `domain.entity` | 文件聚合根；拥有声明、content version、三轴状态、controlled reason、版本与生命周期行为 |
| `AttachmentFileId` | `domain.valueobject` | 正 `Long` 本地身份；公共语义 opaque，不能从顺序或数值获得授权 |
| `AttachmentTargetType` | `domain.model` | `WORK_TASK`、B24b 八类 CRM 与 B29a `PRODUCT`/`RETURN_VISIT` 的闭集；不复用 `BusinessObjectType` |
| `AttachmentTarget` | `domain.model` | exact `(type,id)`；不包含 tenant，tenant 始终由 caller/持久化关系证明 |
| `AttachmentByteState` | `domain.model` | `ABSENT/UPLOADING/STORED/REMOVING/REMOVED/UNKNOWN` |
| `AttachmentInspectionState` | `domain.model` | `NOT_RUN/PENDING/CLEAN/REJECTED/INDETERMINATE` |
| `AttachmentPublicationState` | `domain.model` | `PRIVATE/AVAILABLE/WITHDRAWN/DELETED` |
| `AttachmentRelationState` | `domain.model` | `ACTIVE/WITHDRAWN/DELETED`；与 publication 轴分离 |
| `AttachmentContentEvidence` | `domain.model` | actual bytes、固定 SHA-256 与 content version；不含 ETag、path、provider metadata |
| `AttachmentFileRepository` | `domain.repository` | tenant-scoped aggregate、目标页、根锁和历史存在性端口 |

`AttachmentFile` 不包含 bucket、path、provider key/version、scanner message、HTTP header、`InputStream`、`Path`、SDK 类型或任意 metadata map。它以 named `Snapshot` 重建并恢复乐观版本，不在 reconstitution 中产生事件。storage identity、job lease 和 read handle 不是聚合字段。

### 2.2 Application 类型与端口

`application.port.out.attachment` 固定承载以下 provider-neutral 合同：

- `AttachmentStorage`：`allocate/store/probe/open/requestDelete/confirmAbsent/abortIncomplete`；
- `AttachmentStorageReadinessResolver`：按 tenant binding 和 operation 返回短期 snapshot；
- `AttachmentContentDetector`：只返回规范媒体类型与 `BOUNDED_MAGIC/ISOLATED_CONTAINER` evidence；
- `AttachmentContentScanner`：只返回四态 scanner evidence 与 pinned/managed definition basis；
- `AttachmentInspectionReadinessResolver`：返回 detector/scanner/policy 的当前 snapshot；
- `AttachmentByteSource` 与 `AttachmentReadHandle`：不可序列化、closeable/cancelable、单调用栈 bounded stream；
- `StorageBindingRef`、`PlannedObjectLocator`、`StoredObjectIdentity`：opaque application/persistence 值，禁止默认 `toString` 暴露值；
- `AttachmentWorkStore`：保存 content plan、stored identity、inspection/publication/deletion job、lease/fence、read lease、quota reservation 与安全 evidence digest。

`AttachmentWorkStore` 是 application output port，不下沉到 domain，因为 planned/stored locator 和 adapter binding 不是领域词汇。`AttachmentFileRepository` 与 `AttachmentWorkStore` 的多次写调用仍在同一个 `CommandServiceTemplate` 事务内；infra/persistence 不得自行开启嵌套事务。

`application.security.attachment` 固定提供：

- `AttachmentTargetAccess`：单个 target type 的 `authorizeRead`、`authorizeCollaborate`、`lockForAttachmentMutation` 与 lifecycle projection；
- `AttachmentTargetAccessRegistry`：显式注册十一个 adapter；未知值、缺 bean 或重复注册启动失败，运行时不走 `default` tenant fallback；
- `WorkTaskAttachmentTargetAccess` 在 B24a 实现；B24b 增加八个以 owning aggregate 命名的 adapter，B29a 另增 Product 与 ReturnVisit adapter。

人类用例固定拆成 `AttachmentCommandService`、`AttachmentQueryService` 与 `AttachmentTransferService`。worker 用例固定放在 `application.service.attachment.worker`，不接受或构造 `AuthenticatedCaller`。`AttachmentParentLifecycle` 是父业务命令在已有事务和父锁下调用的内部协调接口；它只关闭关系和创建 durable deletion work，不做外部 I/O。

B24a 同时在 `AuthorityCatalog` 增加 tenant-assignable、无 data scope 的 `FILES_READ = "files:read"` 与 `FILES_WRITE = "files:write"`。它们互不包含，也不自动写入既有自定义角色；tenant owner 仍由动态目录获得。`SecurityConfig` 对 metadata/full/range 路由要求 read，对 intent/upload/status/withdraw 路由要求 write；application guard 必须独立重复能力、活动成员和 target 交集，HTTP matcher 不能成为唯一授权。

### 2.3 API、Facade 与基础设施所有权

- `api` 新增 `AttachmentFacade`、`Attachment*Request` 与 `Attachment*Response`；公共类型中没有 provider、scanner、locator、checksum 原值或三轴内部 evidence；
- `infra/facade` 只做 Request/VO/Response 映射；raw body 不经过 facade 缓冲；
- `infra/rest` 的 `AttachmentController` 负责 JSON 路由，`AttachmentContentController` 负责 raw upload/download、Range 和 cancellation；两者都把 `AuthenticatedCaller` 显式传入应用；
- `infra/persistence` 使用 `Attachment*PO`、`Attachment*POConverter`、mapper XML 和两个 repository adapter；PO 不向 facade/controller 暴露；
- `infra/external` 拥有 secure-local、Clamd 类 streamed adapter、disabled adapter 以及 test-scope synthetic drivers；
- `start` 只组合配置、worker scheduler 和集成 fixture，不拥有 file lifecycle 决策。

ArchUnit 必须新增边界：domain/application/api 不得引用 `MultipartFile`、Servlet response/request、`Path`、`File`、`URL/URI`、AWS/Tika/ClamAV/native DTO 或 provider SDK；controller 不得引用 repository/PO；external adapter 不得读取业务 repository 或 datasource。

### 2.4 Use-case scene 预留

`UseCaseOperation` 为 B24 固定预留：

| Code | Operation |
| --- | --- |
| `7330` | `ATTACHMENT_UPLOAD_INTENT_CREATE` |
| `7331` | `ATTACHMENT_CONTENT_UPLOAD` |
| `7332` | `ATTACHMENT_UPLOAD_STATUS_GET` |
| `7333` | `ATTACHMENT_QUERY` |
| `7334` | `ATTACHMENT_GET` |
| `7335` | `ATTACHMENT_CONTENT_DOWNLOAD` |
| `7336` | `ATTACHMENT_WITHDRAW` |
| `7310` | `FILE_FIELD_DRAFT_CREATE` |
| `7311` | `FILE_FIELD_DRAFT_WITHDRAW` |
| `7312` | `FILE_FIELD_UPLOAD_INTENT_CREATE` |

worker 不通过 public `Result` 暴露 scene；内部日志只记录固定 worker kind、safe reason 和 evidence digest，不记录 file/locator/checksum 原值。

## 3. B24a API 冻结

### 3.1 Canonical target 路由

公共根路径固定为：

```text
/api/v1/attachment-targets/{targetType}/{targetId}/attachments
```

path token 固定为小写 kebab：`work-task`、`customer`、`contact`、`lead`、`opportunity`、`contract`、`invoice`、`receivable`、`receivable-plan`、`product`、`return-visit`。B24a 只注册 `work-task`；B24b 原子注册其余八个；B29a 注册最后两个。大小写、下划线、复数、未知 token 或超出白名单的 `BusinessObjectType` 返回输入错误，不能用宽松 enum 转换自动接入。

所有 file 操作同时携带 target 和 file ID；不存在 `/api/v1/files/{fileId}`、tenant 文件列表、最近文件或 filename 搜索。

| Method | Path suffix | 权限/结果 |
| --- | --- | --- |
| `POST` | `/upload-intents` | `files:write` + target collaborate；`201` 返回 uploader-safe intent |
| `PUT` | `/{fileId}/content?version={version}` | exact uploader + 再次 collaborate；raw single stream；成功 `202`，只表示 `ACCEPTED_FOR_INSPECTION` |
| `GET` | `/{fileId}/upload-status` | exact uploader + `files:write` + target collaborate；安全状态，不返回 native reason |
| `GET` | `?page=1&size=20` | `files:read` + target read；只分页 `AVAILABLE`，`size=1..100` |
| `GET` | `/{fileId}` | 与列表相同；target/file mismatch 使用安全 `404` |
| `GET` | `/{fileId}/content` | 每次重新授权并在事务外 full/range stream；`200/206/416` |
| `DELETE` | `/{fileId}?version={version}` | `files:write` + target collaborate；本地撤回后 `204`，不等待 storage delete |

### 3.2 Request/Response shape

`AttachmentUploadIntentCreateRequest` 只含：

```json
{
  "displayName": "quote.pdf",
  "declaredMediaType": "application/pdf",
  "expectedBytes": 4096,
  "expectedSha256": "optional-unpadded-base64url"
}
```

服务端对展示名先 trim 再 NFC 规范化，要求 `1..200` Unicode code points 且 UTF-8 `<=512` bytes；拒绝 C0/C1/DEL、NUL、Unicode bidi control、`/`、`\\`、`:`、`.`/`..`、尾随点/空格和任何 CR/LF/header 注入。允许扩展名固定为 `.pdf/.png/.jpg/.jpeg` 并必须与声明类型和最终 detector 类型一致。`expectedSha256` 若存在必须是 canonical 32-byte SHA-256 的无填充 Base64URL；它只参加三方相等校验，不选择 locator 或提供秒传。

未知 JSON property 必须拒绝。tenant、actor、target、file ID、status、provider、locator、object metadata、scanner result、retention 或 headers 出现在 body 时也必须拒绝。

`AttachmentUploadIntentResponse` 固定返回 `fileId/displayName/declaredMediaType/expectedBytes/status/version/expiresAt`。`AttachmentUploadStatusResponse` 只允许：

```text
UPLOADING
ACCEPTED_FOR_INSPECTION
AVAILABLE
REJECTED
INDETERMINATE
WITHDRAWN
REMOVING
REMOVED
EXPIRED
```

它可返回固定 `failureCategory`，但不能返回 threat、signature、parser/provider error、checksum、binding 或 locator。`AttachmentResponse` 只含 `fileId/displayName/mediaType/actualBytes/uploaderActorId/createdTime/availableTime/version`。

### 3.3 Raw upload 与 download wire contract

raw upload 必须：

- `Content-Type: application/octet-stream`；声明业务媒体类型只来自 intent JSON；
- 有唯一 `Content-Length`，且与 intent 完全相等；chunked、multipart、form-data、unknown length 和 content-encoding 全部在 storage open 前拒绝；
- 从 servlet request 到 storage 使用最多 64 KiB application buffer，不读取完整 `byte[]`，不在上传 HTTP 内等待 inspection；
- duplicate PUT 只对相同 intent/version/content evidence 返回相同安全状态；不同 bytes 或已消费 intent 为 conflict，不分配第二 locator。

download 固定发送 `Content-Disposition: attachment`、`X-Content-Type-Options: nosniff`、`Cache-Control: private, no-store`、`Pragma: no-cache` 与 `Content-Length`。不发送 `ETag`、provider `Last-Modified`、URL、checksum 或 provider headers。range ready 时发送 `Accept-Ranges: bytes`；否则发送 `Accept-Ranges: none`。只接受一个 RFC 9110 bytes range；invalid、multi、overflow、unsatisfied 或 capability unavailable 统一 `416` 且 storage open count 为零。`If-Range`、条件 GET 和 transparent resume 首期不支持。

authentication/authority 失败保持现有 `401/403`；通过文件入口能力后，target hidden/not-found/deleted、file hidden/not-found、target/file mismatch 与 cross-tenant 统一安全 `404`；B24 operation readiness 缺失为安全 `503`；请求/大小/range/type validation 为 `400/416/422` 的固定公共消息。任何错误正文不带文件存在性、名称、大小或内部 reason。

## 4. SQL 与 Flyway 冻结

### 4.1 `V59__create_attachment_foundation.sql`

V59 由 B24a 独占并一次创建：

| Table | 责任与必须约束 |
| --- | --- |
| `crm_attachment_file` | aggregate root；tenant/uploader FK、declaration、三轴状态、canonical output、content version、controlled reason、optimistic version；`UNIQUE(tenant_id,id)`，状态/bytes/version/时间 CHECK |
| `crm_attachment_relation` | 一 file 一当前 owner 的中心证明；V59 只有非空 `work_task_id`，`PRIMARY KEY(tenant_id,file_id)`，FK file 与 `(tenant_id,work_task_id)`，relation state/version，CHECK target type=`WORK_TASK` |
| `crm_attachment_content` | 一对一 upload plan 与 exact stored evidence；binding ref、planned locator、stored identity 都是 binary-exact bounded opaque columns；attempt、actual bytes、SHA-256、deadline；不保存 provider JSON |
| `crm_attachment_job` | durable work；kind/state/generation/attempt/not-before/deadline/lease-owner/lease-fence/result digest/version；`UNIQUE(tenant_id,file_id,content_version,kind,generation)` |
| `crm_attachment_read_lease` | same-call-stack open authorization、worker/replica fence、hard expiry 与 release state；不能成为 browser bearer |
| `crm_attachment_tenant_usage` | 每 tenant ready/quarantine/pending bytes、intent/upload/scan/download counters、版本；所有加减在锁行后做 checked arithmetic |
| `crm_attachment_global_usage` | deployment 级 ready/quarantine/pending byte singleton；每次 reserve/release 先锁此行再锁 tenant usage，保证 global reserve 跨 tenant 共享；不是另一套 file/relation/job 数据模型 |

V59 的 job kind 固定为 `UPLOAD_RECONCILIATION`、`INCOMPLETE_ABORT`、`INSPECTION`、`PUBLICATION`、`DELETION` 与 `RETENTION_SWEEP`。初始状态为 `PENDING/LEASED/EFFECT_RECORDED/SETTLED/DEAD`；B24b 的 V61 再加入仅用于 Customer/Contact pool defer 的 `PAUSED`，它不消耗 failure attempt、不能被 deadline terminal recovery 选中，只能在恢复 owner 时重排为 `PENDING`。`DEAD` 仍不可发布或下载，不物理移除正确性证据。

SHA-256 使用 `BINARY(32)`；opaque binding/locator/identity 使用 binary collation 的有界 `VARBINARY`，禁止 JSON、TEXT provider blob 和明文 path。tenant、uploader、file、target、job 的 FK 全部 `ON DELETE RESTRICT`。file/link/job/tombstone 不做 cascade delete。所有时间为 MySQL UTC `DATETIME(6)`，关键 deadline 由持久化 UTC 时间和 worker clock port 对账，不信任请求时间。

每 target 最多 100 个 active/private relation 通过“先锁 exact target root，再以 `(tenant,target,state)` 覆盖索引计数，再 insert”的 MySQL 串行化最终仲裁；不能用 JVM counter。V59 为 Work 建立 `(tenant_id,work_task_id,relation_state,file_id)` 索引。Work Task 删除前使用 `existsHistoryByTargetForUpdate` 检查任何 intent/relation/tombstone，并把结果传入扩展后的 `WorkTask.delete(hasComments,hasAttachmentHistory,now)`。

### 4.2 `V60__add_crm_attachment_targets.sql`

V60 只能扩 `crm_attachment_relation`：增加八个 nullable typed parent ID、八组 tenant-composite FK 和八个 `(tenant,target,state,file)` 索引；随后 drop/recreate V59 的 relation CHECK，使九个 parent 列 exact one non-null 且与 `target_type` 一致。禁止创建 `crm_customer_attachment` 等九套复制 file/job 表，也禁止 generic `(target_type,target_id)` 无 FK 字符串。

V60 不回填数据、不修改 V59 file/content/job 状态、不新增 public route 形态。已有 Work 行在新 CHECK 下仍合法。每个父 FK 都 `ON DELETE RESTRICT`，因为业务聚合使用 status soft delete。

### 4.3 `V61__add_attachment_paused_job_state.sql`

V61 只重建 `crm_attachment_job` 的 state CHECK，加入 `PAUSED`。它不增加 worker kind、public API、file state 或第二张 job 表。pool pause 发生在已取得 `parent → relation → file → job` 当前事务锁序后：leased `INSPECTION`/`PUBLICATION` job 进入 `PAUSED` 时清 lease 并归还 claim 加上的 attempt；`PAUSED` 不在 claim/deadline terminal selection 中。Customer/Contact leave pool 后，在同一 parent-root transaction 内将本 target 的 paused inspection/publication job 重排为 `PENDING`，刷新 deadline。

### 4.4 `V62__create_file_field_and_oa_file_binding.sql`

V62 创建：

| Table | 责任 |
| --- | --- |
| `crm_file_field_draft` | tenant/uploader/object type、可选 OA form+version、purpose digest、deadline、state/version；一次消费，不能换 object/form/uploader |
| `crm_file_field_draft_file` | draft + field definition + file + ordinal；一 file 只能属于一个 draft slot，保留 staging history |
| `crm_attachment_field_binding` | 八类 CRM target 的 active FILE value；file、field definition、target relation、ordinal/content version/evidence digest；`UNIQUE(tenant,file_id)` 与 `UNIQUE(tenant,target_type,target_id,field_id,ordinal)` |
| `crm_oa_request_file_value` | OA request 的不可变有序 FILE fact；tenant/request/field/file/content version/evidence digest；tenant-composite OA/file/field FK |
| `crm_approval_instance_file_fact` | B11 immutable ordered file snapshot；不放入 `facts_json` 字符串 |
| `crm_stage_workflow_file_fact` | stage/attempt 的 ordered FILE snapshot；不放入普通 scalar facts JSON |

V62 不编辑 V6/V35/V52/V54；所有新关系通过新表和新 FK 加入。draft 到 CRM create 或 OA request 的消费在 owning record 创建事务内以 draft/file version 更新和 final typed row insert完成；draft history不作为第二个业务 target。一个 file 只能从 `STAGED` 一次转成 exact `BOUND` destination，失败或过期只能进入 cleanup，不能换到另一对象、field 或 request。

`FILE` 值不写入现有 `*_field_value.value_text/value_json`，不创建 unique reservation，不进入 saved-view/report/keyword index。field lifecycle dependency 查询必须覆盖 draft candidate、active binding、OA value，以及非终态 approval/stage snapshot；停用或删除仍被 current/private dependency 阻止。终态历史 snapshot 按既有 retention 留存但不永久阻止字段停用，也不恢复字段可写性或文件下载。

V59/V60/V61 必须通过 empty-schema 和从 V58 升级测试；V62 实施时也必须追加相同证明。实现中若发现缺陷，只能追加新版本修复，不能修改已经发布的 V59/V60/V61 checksum。

## 5. Durable worker 冻结

### 5.1 Worker 集合

B24a 一次实现全部正确性 worker；B24b 不新增 worker kind，B24c 只让 retention worker理解 draft/field candidate：

1. `AttachmentUploadReconciliationWorker`：对 storage effect 后未 finalize 的 exact planned locator 做 probe/finalize 或创建 incomplete-abort work；
2. `AttachmentIncompleteAbortWorker`：只清理同 attempt 的 exact planned locator；
3. `AttachmentInspectionWorker`：claim 后在事务外 drive detector/scanner，结果事务重查 subject/fence/readiness；
4. `AttachmentPublicationWorker`：无 human caller、无外部 I/O；在 target root 锁内把 eligible general attachment 从 PRIVATE 变 AVAILABLE；
5. `AttachmentDeletionWorker`：effect 与 authoritative confirmation 分两个 durable phase；只有 confirmation commit 写 REMOVED；
6. `AttachmentRetentionWorker`：有界分页发现 expired intent、draft、rejected/indeterminate quarantine 和 tombstone，并只创建/推进 exact work。

FILE field candidate 不由 publication worker自动绑定；CRM update/create 或 OA submit transaction 才能把已 `STORED+CLEAN` 的 exact candidate 绑定并发布。worker 不选择 field、target 或 OA request。

### 5.2 Claim/I/O/commit 协议

- claim 是短 `REQUIRES_NEW` transaction，按 `(not_before,id)` 取最多 25 行，使用 `FOR UPDATE SKIP LOCKED`，递增 attempt 与 lease fence；
- 外部 storage/scanner/HTTP byte I/O 时活动业务 transaction count 必须为零；lease heartbeat 如需要只更新 exact job/fence，不加载业务聚合；
- commit 是新的短 transaction，要求 tenant/file/content version/job generation/lease owner/fence 全相等；旧 worker 结果 no-op；
- publication 取得 owning domain guard -> exact target -> file/relation -> job；deletion 取得 file/relation -> deletion job，父命令仍先持有 parent；任何 worker 不反向先锁 file 再锁 parent；
- job lease、heartbeat、claim batch、backoff、attempt、open/idle/stream/total deadline 与 retention 都来自 `AttachmentRuntimePolicy` 的正值。prod 不提供回退默认；缺一项只使对应 operation/worker `NOT_READY`，不阻止普通 CRM 启动；
- attachment pump 由 `start` 的统一 `BackgroundWorkScheduler` 唤醒，仍由 `atom.files.workers.enabled` 独立控制，默认 `false`；它不共享 B26 automation run，且多副本仍只依靠各自 MySQL job lease/fence，不用 `@Async` future 或本机 map 冒充 durable work；
- worker shutdown 只停止新 claim并取消本进程 handle；shutdown hook 不是 completion evidence。

## 6. 配置与首个 profile

正式 property namespace 固定为 `atom.files.*`：

- `atom.files.enabled`、`atom.files.workers.enabled`；prod 默认均为 `false`；
- `atom.files.policy.max-file-bytes`，不得高于 16 MiB；
- `atom.files.policy.max-ready-bytes`、`max-quarantine-bytes`、`max-pending-intents`、`max-active-uploads`、`max-active-inspections`、`max-active-downloads`、`global-reserve-bytes`、`deletion-control-reserve`；
- `atom.files.policy.local-free-floor-bytes`，必须为正数；secure-local 在构造、allocation 和新 namespace 写入前复核它，任一 free-space probe 故障或低于 floor 均失败关闭；
- `atom.files.policy.intent-ttl`、`upload-open-timeout`、`upload-idle-timeout`、`upload-total-timeout`、`inspection-total-timeout`、`download-open-timeout`、`download-idle-timeout`、`download-total-timeout`、`job-lease`、`job-heartbeat`、`max-attempts`、`quarantine-retention`、`tombstone-retention`；
- `atom.files.storage.profile` 与 private binding reference；
- `atom.files.inspection.detector-profile`、`scanner-profile`、definition basis/max-age 与 allowlist revision；
- `atom.files.worker.claim-size`，取值 `1..25`。

除两个 enable flag 外，prod profile 没有可使文件 READY 的数值默认。配置类在 `infra/external`/`start`，application 只接收 validated `AttachmentRuntimePolicy` 和 readiness snapshot。root、socket、endpoint、bucket、credential、KMS、provider account 和 definition bundle path 不写入公开文档示例、API、metric label 或普通日志。

`LOCAL_SECURE_NIO_V1` 只有在真实 root/mount/owner/mode/`SecureDirectoryStream`/force/delete/crash conformance 全部通过时才可 ready；普通临时目录只能用于 durable simulator。`STREAMING_ENGINE_V1` 只接受 fenced definition generation，不能用裸公开 TCP `clamd`。生产部署仍必须提交与同一 build/config 对应的 positive manifest；配置存在本身不等于 ready。

实现中的 positive evidence 必须经私有 `SecureLocalAttachmentConformanceReportSource` 提供，而不是把 build、binding digest、有效期或“通过”标志放进 `atom.files.*` properties。report 要匹配运行中 adapter class digest、binding、完整 policy/catalog/allowlist revision、完整 S/I/X scenario set、engine build 和 definition generation/basis/freshness；source 缺失、报告撤销/过期或任一 drift 都立即 `NOT_READY`。

## 7. B24b 目标接入边界

B24b 对每个 CRM domain 只做四件事：

1. 注册一个显式 `*AttachmentTargetAccess`，复用该域现有 access resolver 的 READ/WRITE scope；
2. 在 command transaction 中提供 exact root lock 与 lifecycle projection；
3. 在父 soft-delete 的现有锁序内调用 `AttachmentParentLifecycle.withdrawAll`；
4. 运行该 target 的完整 A/X 子矩阵和 typed FK 竞态。

Customer/Contact 的 `IN_POOL` 只暂停全部附件操作，不建 deletion work。Lead conversion、Opportunity close、Contract void、Invoice issue、Work completion/cancel 不改变附件。父删除联动必须发生在同一本地 transaction 中；storage 不可用不能让父删除后的附件重新可读。

B24b 完成要求八个 target 同时交付。不能先把 `CUSTOMER` enum/API 打开，再让 registry 中缺少的 target 返回运行时 500；未完整实现的 token 必须根本不注册。B24b 交付时 Product、ReturnVisit、OA Request、Announcement、Activity、Approval、Stage、MarketingForm、pool、connector 和 B22 recording 均为 unsupported；B29a 已随后显式注册 Product 与 ReturnVisit，其余类型仍为 unsupported。

## 8. B24c FILE 与 OA API/语义

### 8.1 结构化 FILE 值

首期 FILE 字段语义固定为有序多文件，基数 `0..10`，更新为全量替换。`unique=false`、`searchable=false`、options 为空；FILE 不能进入 saved view、keyword、report group/measure、审批 condition 或营销表单。required FILE 在 owning create/submit transaction 要求非空，在后续写入继续按当前字段策略校验。

八类 B24b CRM Request 和 B29a Product/ReturnVisit Request 增加独立字段：

```java
Long fileFieldDraftId; // only create requests
Map<String, List<Long>> fileFields;
```

Response/VO 增加 `Map<String,List<AttachmentResponse>> fileFields`。现有 `customFields` 保持 scalar/string contract，FILE key 出现在 `customFields` 或普通 `values` 时拒绝。只有同时具备 `files:read`、target read 和当前 field READ/unmasked 才组装该 key；command caller 只有 write 时也省略。HIDDEN 或任意 MASKED FILE 字段整个 key 省略，不返回数量、占位符、文件名或大小。下载 field-bound file 同样重新证明这三个条件。

existing target 的 candidate 路由固定为（响应携带内部 draft 的 `draftId`）：

```text
POST /api/v1/attachment-targets/{targetType}/{targetId}/file-fields/{fieldCode}/upload-intents
```

它要求 `files:write + target collaborate + field WRITE`，从创建起固定 field/target/uploader。candidate 在被 owning aggregate update 选入前保持 PRIVATE，不出现在普通 attachments 列表；未选中、替换掉或过期时进入同一 deletion worker。未绑定 candidate 可由 exact uploader 在重复 field WRITE 后撤回；已经绑定的 FILE 不能通过通用 attachment DELETE 单独撤回，必须由 owning aggregate 的 whole-list replace/clear 在 required/field/version 校验后原子关闭。field-bound metadata/detail 与 content 都重复 field READ/unmasked，普通 attachments page 永远排除它们。

### 8.2 创建前 draft

为解决 required FILE 与“record 尚无 ID”，B24c 增加：

```text
POST   /api/v1/file-field-drafts
POST   /api/v1/file-field-drafts/{draftId}/fields/{fieldCode}/upload-intents
PUT    /api/v1/file-field-drafts/{draftId}/fields/{fieldCode}/files/{fileId}/content?version={version}
GET    /api/v1/file-field-drafts/{draftId}/fields/{fieldCode}/files/{fileId}/upload-status
DELETE /api/v1/file-field-drafts/{draftId}?version={version}
```

draft 创建 body 只允许 `objectType`；OA draft 另由 OA form 路由创建并由服务端绑定 form ID/version，普通 body 不能伪造。CRM draft 只允许 Customer/Contact/Lead/Opportunity/Contract/Invoice/Receivable/ReceivablePlan，并在创建时验证对应 create entry capability、当前 field WRITE 与 uploader membership。最终 create transaction 重做完整 owning-domain create policy和字段策略，再锁 draft/files，要求 exact `STORED+CLEAN+PRIVATE`，创建业务 root 后一次绑定；任一失败全部不消费 draft。

draft 只能被 exact uploader、object type 和一次 create/idempotency operation 消费；不能用作个人文件库、跨 target 移动或重复创建多个记录。过期/撤回立即关闭状态读取并建立 cleanup。

### 8.3 OA 提交前文件

OA 使用：

```text
POST /api/v1/oa/forms/{formId}/file-field-drafts
```

服务端锁定 form/version、当前 submit eligibility、FILE field 配置与申请人，返回 draft。`OaRequestSubmitRequest` 新增 `fileFieldDraftId` 和结构化 `fileFields`；preview 可引用 draft 但不消费。submit 继续使用 B20b 的单一 outer transaction：tenant serialization -> OA customization guard -> form root -> draft/files -> OA request -> approval。它创建 `crm_oa_request_file_value` 与 B11 file facts后才提交；request、approval、file facts 全有或全无。idempotency digest 必须包含有序 `(fieldCode,fileId,contentVersion,evidenceDigest)`，同 key 更换任何文件为 conflict。

OA FILE 下载只通过：

```text
GET /api/v1/oa/requests/{requestId}/file-fields/{fieldCode}/{fileId}/content
```

它要求 `files:read`、当前 OA request participant/manage 可见性和当前 field READ/unmasked。不存在通用 `OA_REQUEST` attachment target，也没有提交后追加、替换或删除 OA file value。OA request soft lifecycle 不改写已保存 B11 route facts；retention 到期先关闭下载，再按 immutable request policy清理 bytes并保留最小 snapshot evidence。

### 8.4 B11 与 stage 快照

FILE 永远不参与条件比较。审批/阶段若捕获一个可读、unmasked FILE field，只保存专用表中的 ordered `fileId/contentVersion/evidenceDigest`；不把 ID 或 JSON 串放进 scalar `facts_json`。路由计算不能根据文件名、数量、媒体类型或 scanner result 分支。

输出 snapshot 时仍重新应用业务记录、file capability 和 field policy；隐藏后省略。approval snapshot 的 content 路由为 `/api/v1/approval-instances/{instanceId}/file-fields/{fieldCode}/{fileId}/content`；stage snapshot 在 `StageWorkflowRecordResponse.fileFields[stageCode]` 输出，content 路由为 `/api/v1/stage-workflow-records/{recordId}/stages/{stageCode}/file-fields/{fieldCode}/{fileId}/content`。两者都重新验证 workflow/record 可见性、`files:read` 和当前未遮罩 FILE READ。历史 snapshot 不恢复已 withdrawn/deleted file 的下载，也不延长 bytes retention。stage required FILE 必须在 capture transaction 中至少有一个 exact active bound file；否则整体 advance 失败。

## 9. 验收冻结

### 9.1 B24a gate

B24a 必须同时通过：

- domain/application 单元测试：三轴转换、strongest threat、filename/type/size/checksum、target registry、transaction/I/O ordering、old fence no-op；
- `AttachmentPersistenceIntegrationTest`：V59 FK/CHECK、same-tenant one-target、record cap 100、quota checked arithmetic、optimistic version、job claim/takeover；
- `B24aWorkTaskAttachmentIntegrationTest`：完整 HTTP intent/upload/status/list/full/range/withdraw、Work participant/CRM-link independence、task history delete block；
- `B24aAttachmentCrashIntegrationTest`：X-01..X-05、X-07/X-08 的 Work lane及所有相邻 crash cuts；
- `AttachmentStorageConformanceTest`、`AttachmentInspectionConformanceTest` 与 `AttachmentComposedConformanceTest`：model + durable `XS/XM/XL`，其中 synthetic 不产生 production readiness；
- ArchUnit、forbidden sink scan、non-B24 CRM sentinel、default-prod-disabled test。

### 9.2 B24b gate

`B24bCrmAttachmentTargetIntegrationTest` 必须参数化运行八个 target 的：read/collaborate scope、target/file mismatch、same-tenant typed FK、non-delete lifecycle、父 root lock、soft-delete withdrawal、publication/delete race和 late worker no-resurrection。Customer 与 Contact 另跑 IN_POOL pause/restore；各自关闭状态按 B24-A 表执行。

V60 升级测试必须直接尝试九种错误 parent/type/null 组合、cross-tenant FK、同 file 并发两 target 和不存在 parent。`B24-X/1` target report 必须出现九个独立 lane，不能以 Work/Customer 代表其余 resolver。B24b 完成时全套 `A-01..A-14` 与 `X-01..X-08` durable composition 才首次完整。

### 9.3 B24c gate

B24c 固定新增 `C-01..C-12`：

| ID | 必须证明 |
| --- | --- |
| `C-01` | direct candidate 同时受 files、target、field WRITE，未绑定前不进普通列表 |
| `C-02` | ordered 0..10、required、whole-list replace、duplicate file 和同 file 多 field 失败关闭 |
| `C-03` | HIDDEN/MASKED 在记录、审批、stage、OA 和 download 全部省略且不泄露 count/name/size |
| `C-04` | field deactivate/delete 与 draft/current/snapshot dependency 共用 customization guard，总序无悬空引用 |
| `C-05` | CRM create draft 只被 exact uploader/object/command 消费一次，业务 root 与 bindings 全有或全无 |
| `C-06` | OA form/version/draft/files/request/approval 单事务；preview 不消费；idempotency file digest exact |
| `C-07` | FILE 不进入 scalar custom values、unique/search/saved view/keyword/report/approval condition |
| `C-08` | B11/stage 专用 file fact snapshot immutable，当前授权/field policy 仍控制输出和下载 |
| `C-09` | replace/clear/draft expiry/retention 立即关访问，只建立一个 exact deletion work |
| `C-10` | B24c 交付时 Product/ReturnVisit/MarketingForm/Announcement/Activity/B22 recording 均在 I/O 前 unsupported；B29a 后续仅接入 Product/ReturnVisit。 |
| `C-11` | 并发 bind/consume/field lifecycle/parent delete 由 MySQL lock/FK/version 仲裁，无双 target/双 field |
| `C-12` | 两 tenant 相同 field/file/bytes 不共享 draft、binding、snapshot、quota、download 或 leak sink |

### 9.4 验证命令与报告

每个实现子批次至少运行：

```bash
sh ./mvnw -q test

CI=true sh ./mvnw -q -pl start -am \
  -Dtest='B24*IntegrationTest,Attachment*IntegrationTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

CI=true sh ./mvnw -q test
```

storage、scanner、transaction、mapper、worker、parent delete 或 field/OA 变更全部要求 Docker/MySQL suite。真实 adapter readiness 另在隔离环境运行当前 build 的三层 conformance，并只保存 `B24-X/1` 安全报告。CI synthetic、临时目录、mock scanner 或设计文档永远不能把 `atom.files.enabled` 的生产状态升级为 ready。

每批完成后才创建 `docs/acceptance/B24a.md`、`B24b.md` 或 `B24c.md`，记录实际测试命令、退出码、schema/build/report digest 与未实现边界；本 SPLIT 文档是验收规范，不是通过记录。

## 10. 变更控制

以下任何变化都需要新的 B24 contract/fixture version，而不是静默实现差异：

- target catalog、公共 route、file ID 定位语义或 target-first 404 分类；
- 16 MiB public cap、64 KiB buffer、SHA-256、100/10 基数、媒体 allowlist、range/multipart 模式；
- typed relation schema、Flyway version ownership、job kind、claim/fence/absence procedure；
- storage/scanner profile、definition basis、container detector、binding或 readiness fields；
- FILE required/replace/mask/snapshot/OA draft 语义；
- 任何 provider URL、direct upload/download、preview、external share、dedup 或 B22 recording 复用。

B24-SPLIT 至此完成。B24a 与 B24b 的实现仍必须保持真实 MySQL、stream、worker 和 conformance 门槛；下一步只能按冻结边界实现和验收 B24c。
