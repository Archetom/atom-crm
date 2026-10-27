# B29 剩余文件业务覆盖扩展边界规划

> 基线：`683f4c7a feat(B28b): 实现 OA 公告到可靠 EMAIL delivery`
>
> 对账日期：2026-07-24
>
> 状态：B29a、B29b、B29c、B29d 已全部实现并完成独立验收记录。B24 的 production 默认禁用和真实 adapter readiness 门槛保持不变。

## 1. 结论

B24a、B24b 和 B24c 已经提供一个可复用的安全文件内核：不可改绑的 `AttachmentFile`、有界代理上传/下载、内容检查、target-first 授权、durable worker、确认删除，以及八类 CRM 的普通附件和结构化 FILE 值。B29 不复制 storage、scanner、job 或下载协议，也不把剩余业务类型一次性加入一个带默认分支的“任意对象附件”枚举。

B29 只闭合仓库已经明确后置的四条业务接缝：

1. **B29a Product/ReturnVisit 文件覆盖**：把两个已存在的 B05 对象接入普通附件和结构化 FILE 值；
2. **B29b MarketingForm FILE**：允许活动公开营销表单接收受治理的 LEAD FILE 字段，但使用独立匿名草稿凭据和更严的容量门槛；
3. **B29c OA 公告附件**：让草稿公告拥有一个在排期/发布前完整就绪、之后不可变的附件集合；
4. **B29d OA 申请协作附件**：在不改写提交事实和 B11 FILE 快照的前提下，为待审批申请增加独立的提交后协作附件。

四类文件关系不等价。Product/ReturnVisit FILE 是当前业务字段值；MarketingForm FILE 是在目标 Lead 尚不存在时由匿名提交者暂存的候选；公告附件是发布内容的一部分；OA 协作附件则是提交事实之外的参与者协作内容。B29 必须分别定义主体、权限、父锁、冻结时点和删除规则，不能只扩大 `BusinessObjectType`、`AttachmentTargetType` 或数据库 CHECK。

## 2. 当前事实与决策总账

| 接缝 | 当前事实 | B29 决策 |
| --- | --- | --- |
| Product 普通附件 | B24 target catalog 不含 Product；Product 是 tenant-wide 目录，无数据范围 | B29a 增加显式 Product target adapter，取 `files:*` 与 `products:read/write` 的交集 |
| Product FILE | PRODUCT 已有 B05 字段、字段策略和标量自定义值，但 draft/binding 明确拒绝 FILE | B29a 扩展结构化 FILE draft/binding；不把文件复制进商机/合同产品快照 |
| ReturnVisit 普通附件 | ReturnVisit 已有独立能力、数据范围、父锁和软删除，B24 未注册 | B29a 增加显式 ReturnVisit target adapter并复用当前 READ/WRITE scope |
| ReturnVisit FILE | RETURN_VISIT 已有 B05 字段，但 B24c 明确失败关闭 | B29a 增加创建前草稿、更新替换、字段权限和父删除联动 |
| MarketingForm FILE | B19 公开表单只接受标量 public-safe LEAD 字段；匿名主体不是成员 | B29b 使用独立 public draft credential，不把表单 owner 伪装成 uploader/caller |
| 公告附件 | B20/B28 只支持纯文本；排期冻结标题、正文和 selectors | B29c 只允许 DRAFT 管理附件，并在 schedule/publish 前要求 exact 集合全部 AVAILABLE |
| OA 提交 FILE | B24c 已在提交事务中冻结不可变 OA FILE 事实和 B11 快照 | 保持不变；B29d 不替换、不追加、不重拍这些事实 |
| OA 提交后附件 | OA Request 可见性已覆盖申请人、历史审批人、管理人、抄送人及 OA 管理入口，但没有写语义 | B29d 新增待审批协作能力和更窄的当前参与者谓词，使用普通附件而不是 FILE |
| Activity、B22 recording、导入导出 | 各自需要追加审计、敏感媒体或数据集治理语义 | 继续排除，不因 B29 扩大 |

## 3. 子批次与迁移

| 子批次 | 交付物 | 依赖 | 计划迁移 |
| --- | --- | --- | --- |
| B29a | Product/ReturnVisit target adapters、普通附件、结构化 FILE 请求/输出/绑定和父删除（已实现） | B24a/b/c | `V77__add_product_and_return_visit_file_targets.sql` |
| B29b | 匿名 MarketingForm FILE draft credential、公开候选上传/状态、同事务 Lead 绑定和 abuse admission（已实现） | B29a 只为顺序依赖；复用 B24/B19 | `V78__create_marketing_file_draft_and_upload_origin.sql` |
| B29c | OA Announcement target、DRAFT 附件管理、排期/发布冻结和 recipient 下载授权（已实现） | B28a/B24a | `V83__add_oa_announcement_attachment_target.sql` |
| B29d | OA Request collaboration target、独立能力、当前参与者写谓词和终态冻结（已实现） | B20b/B11/B24a | `V84__add_oa_request_collaboration_attachment.sql` |

最终迁移编号为 V77、V78、V83 和 V84；集成主线在 B29a/b 与 B29c/d 之间加入 V79–V82 后，后两批按规则顺延。不得编辑 V37、V42、V48、V54、V55、V59–V64 或 V74–V84 的任何已应用迁移。

依赖顺序固定为 `B29a -> B29b -> B29c -> B29d`，但每个子批次必须有独立验收记录。前一批只落下 schema 不能让后一类 target 在 registry、HTTP 或 worker 中自动可用。

## 4. 共同文件内核与不变量

### 4.1 继续复用 B24

B29 的所有字节仍使用同一个 `AttachmentFile`、content、job、read lease、tenant/global usage 和 storage/inspection port。以下边界不变：

- 一个 file 只有一个 exact tenant、upload origin、content version 和最终业务关系，不能复制、移动或跨 target 重绑；
- `STORED`、`CLEAN` 和 `AVAILABLE` 继续是不同事实，业务绑定只接受 exact content version 和 evidence digest；
- storage/scanner/HTTP body I/O 不持有 MySQL 业务事务；
- 下载每次重新授权 exact target/file，未知 target、隐藏 target、跨 tenant 和 mismatch 使用相同安全失败；
- 撤回先在本地事务关闭新读取，再由 durable deletion work 执行 exact delete 和 absence confirmation；
- PDF/PNG/JPEG、16 MiB 首个公共上限、单流、full/single-range 和默认 production disabled 均不因业务扩展而改变。

B29 不建立第二套 `MarketingFile`、`AnnouncementBlob` 或 `OaRequestDocument` 字节状态机。业务专属类型只拥有草稿凭据、目标授权、关系和冻结规则。

### 4.2 普通附件、FILE 与快照继续分离

| 概念 | 业务含义 | 是否进入 B05 字段 | 可变边界 |
| --- | --- | --- | --- |
| 普通附件 | 对一个已存在 target 的协作内容 | 否 | 由 target 当前协作规则决定 |
| CRM FILE | Product/ReturnVisit 当前结构化字段值 | 是 | whole-list replace，当前值可更新 |
| Marketing FILE candidate | 尚未创建 Lead 的匿名一次性候选 | 最终绑定后才是 LEAD FILE | exact form/version draft 只能消费一次 |
| OA submission FILE | 申请提交时冻结的权威输入事实 | 是 | 提交后不可变，B29 不改写 |
| OA request collaboration attachment | 提交后的补充协作内容 | 否 | 仅待审批期间可新增/撤回 |
| Announcement attachment set | 公告发布内容的文件集合 | 否 | 仅 DRAFT 可变，schedule/publish 后冻结 |

普通附件不能满足必填 FILE 字段，FILE 也不能自动出现在通用附件列表。MarketingForm candidate 只有在同一事务成功创建 Lead 并绑定字段后才成为内部 FILE 值；成功提交台账不保存文件 ID、文件名或匿名凭据。

### 4.3 Closed catalogs

`BusinessObjectType` 继续只描述 B05/B11 支持的业务对象；`AttachmentTargetType` 继续描述普通附件目标；公开 Marketing draft credential 是第三种专用入口，不属于两者。

每个新增 target 必须显式注册 `AttachmentTargetAccess`。registry 对重复、缺失或未知注册启动失败，不允许反射、表名路由、`Map<BusinessObjectType, Repository>`、`default -> tenant wide` 或仅凭同名枚举自动加入。

## 5. B29a：Product/ReturnVisit 普通附件与 FILE

### 5.1 用户故事

产品管理员需要为当前产品保存说明附件和受治理 FILE 字段；具有当前 ReturnVisit 记录读写范围的成员需要以相同方式保存回访附件和 FILE。看得到相关 Customer、Contract、Product category 或 owner 不能代替目标自身权限。

### 5.2 授权矩阵

| Target | 列表/详情/下载 | 创建/上传/撤回 | 生命周期 |
| --- | --- | --- | --- |
| `PRODUCT` | active membership + `files:read` + `products:read` + active Product | active membership + `files:write` + `products:write` + active Product | unavailable 仍可读写文件；`DELETED` 关闭并清理 |
| `RETURN_VISIT` | `files:read` + `return-visits:read` + 当前 ReturnVisit READ scope + active root | `files:write` + `return-visits:write` + 当前 ReturnVisit WRITE scope + active root | `DELETED` 关闭并清理 |

Product owner、category、availability 和被商机/合同引用都不增加文件权限。ReturnVisit 的 Customer、Contact、Contract 关系分别保持当前输出复核，但它们的可见性不能替代 ReturnVisit scope。

FILE 还要在上述目标交集上增加：

```text
current active FILE definition
  ∩ field READ/WRITE level for the exact member
  ∩ mask NONE for output/download
  ∩ maximum 10 ordered active values
```

`files:write` 不包含 `files:read`。创建/更新成功可以返回该命令的最小候选状态；以后读取 FILE 元数据或字节仍要求 `files:read`、目标 read 和字段 unmasked READ。

### 5.3 API 与聚合接入

普通附件复用：

```text
/api/v1/attachment-targets/product/{productId}/attachments
/api/v1/attachment-targets/return-visit/{returnVisitId}/attachments
```

FILE 创建前草稿继续使用 `/api/v1/file-field-drafts`，请求 `objectType` 增加 `PRODUCT` 和 `RETURN_VISIT`。已存在目标的候选上传与下载继续使用 target-first `/file-fields` 路径。

Product/ReturnVisit create/update Request 增加独立 `fileFieldDraftId` 和 `fileFields`；Response 增加结构化 `fileFields`。标量 `customFields` 仍拒绝 FILE，keyword、saved view、report、unique reservation 和普通 facts 仍不接收 file ID。

Product FILE 不复制到 `OpportunityProductSnapshot` 或 `ContractProductSnapshot`，ReturnVisit FILE 不进入 B25a 数量卡、Activity 或 worklist fingerprint。B29a 也不因此让 Product/ReturnVisit 成为 B11 approval 或 stage runtime 类型。

### 5.4 父锁与删除

- Product 普通附件、FILE replace、availability/update 和 delete 共享 Product root；FILE 定义依赖继续先取得 `(tenant, PRODUCT)` customization guard；
- ReturnVisit 普通附件、FILE replace、owner/update 和 delete 共享 ReturnVisit root；字段写入继续受 `(tenant, RETURN_VISIT)` customization guard；
- create-before-ID 按 customization guard → draft → new root → binding/file 的既有 B24c 顺序提交；
- parent delete 在一个本地事务中关闭普通 relation 和所有 active FILE binding，并创建有界 deletion work，不等待 storage；
- worker 发布从 exact target root 开始，确认父仍 active、relation/draft 未撤回和 content evidence 精确匹配。

每个 target 继续受普通附件最多 100 个和每 FILE 字段最多 10 个的独立上限约束，不能通过在两类关系间搬移同一个 file 绕过。

## 6. B29b：MarketingForm 匿名 FILE

### 6.1 为什么不能复用 human draft

公开营销表单提交者没有 `AuthenticatedCaller`、tenant membership 或 `files:write`。把表单 owner 保存为 `AttachmentFile.uploaderMemberId` 会错误记录上传主体，并让匿名字节看似由受信成员上传；构造 owner caller 后直接调用 human draft 也会绕过公开路由限流和严格字段白名单。

B29b 因此新增一个最小、一次返回且只能成功消费一次的 `MarketingFileDraftCredential`：

- 创建时由 canonical `publicId` 精确解析 active form 和 tenant；
- 只返回一次至少 256 bit 随机 secret，数据库只保存 purpose-separated digest；
- credential 绑定 exact `(tenant, form, formVersion, draftId)`，不包含 actor、role、authority、tenant claim 或 Lead ID；
- 后续请求同时提供 canonical public form path、exact draft ID 和专用 Authorization credential，任一不匹配均使用相同安全失败；
- credential 只允许在该 draft 的已配置 FILE 字段中创建候选、上传、查询受控状态、撤回 draft 和消费提交，不能列出 tenant 文件、下载字节或访问其他表单。

这不是 `AuthenticatedCaller`、`AuthenticatedConnector`、API key 或公开分享链接。应用通过专用 guard 解析它，且永远不把它映射成成员。

### 6.2 公开 API

计划新增：

```text
POST   /api/v1/open/marketing-forms/{publicId}/file-drafts
POST   /api/v1/open/marketing-forms/{publicId}/file-drafts/{draftId}/fields/{fieldCode}/upload-intents
PUT    /api/v1/open/marketing-forms/{publicId}/file-drafts/{draftId}/fields/{fieldCode}/files/{fileId}/content
GET    /api/v1/open/marketing-forms/{publicId}/file-drafts/{draftId}/fields/{fieldCode}/files/{fileId}/upload-status
DELETE /api/v1/open/marketing-forms/{publicId}/file-drafts/{draftId}
```

credential 使用专用 Authorization scheme 传递，不放进 URL、JSON、cookie、日志、metric 或响应后的任何持久化字段。带 FILE 的既有 submission 请求增加独立 `fileDraftId` 与 `fileFields`；同一次提交必须继续携带 exact credential。无 FILE 的旧客户端请求形状和行为保持不变。

公开状态只返回 `UPLOADING/PROCESSING/READY/REJECTED/EXPIRED` 等受控结果，不返回 scanner 原因、storage locator、checksum、tenant、owner、Lead、内部 file version 或下载内容。匿名提交者没有 preview/download API。

建议为新 public-draft operations 使用 `6910..6914` 稳定 scene；最终提交仍属于 `MARKETING_FORM_SUBMIT`，以保持 Lead 创建和 attribution 的同一公共错误边界。

### 6.3 表单与字段规则

MarketingForm 只能配置当前 active、自定义、`CustomFieldType.FILE` 的 LEAD 字段；不存在 public-safe FILE 内置字段。每次管理读取、公开渲染、draft 创建、候选创建和最终提交都重新验证：

```text
active exact form/version
  ∩ active owner membership
  ∩ owner's current leads:write scope
  ∩ owner's WRITE access to lead_name, owner_actor and exact FILE field
  ∩ current FILE field definition and form required/order configuration
  ∩ B24 runtime/storage/inspection admission
```

匿名提交者不获得 owner 的其他字段或记录能力。FILE 仍不进入 `values`；未知顶层属性、把 file ID 放入标量值、未配置 field、跨 field file、重复 file、非 READY 候选或缺失 required FILE 都使整次提交失败。

表单更新、停用或删除会使旧 formVersion draft 立即不可消费。它不在管理事务中无界扫描并删除全部候选；过期/失配 draft 由有界 cleanup 关闭关系并复用 B24 deletion work。

### 6.4 原子提交

最终 submission 事务固定执行：

1. 锁 exact active MarketingForm；
2. 取得 `(tenant, LEAD)` customization guard并重新验证 owner/field policy；
3. 锁 exact public draft 和有序 candidate rows，验证 credential、form version、deadline、READY、evidence 和请求 file IDs；
4. 通过现有 `LeadCreationCoordinator` 创建一个普通 Lead；
5. 把候选作为该 Lead 的结构化 FILE binding，并消费 draft；
6. 追加 B19b 最小成功 attribution row。

任一步失败都回滚 Lead、custom unique reservation、FILE binding、draft consumption 和 attribution。storage I/O 已在此前请求完成，不进入该事务。成功响应仍只含 `accepted` 与表单成功提示，不返回 Lead/file/tenant/owner ID。

### 6.5 匿名 abuse 与容量

Public FILE 比标量提交更昂贵，必须同时满足：

- 现有 canonical public-ID/source admission 扩展到 closed route kinds，编码别名不能拆分 draft、upload 和 submit 配额；
- 每 draft 最多 10 个文件、全部 expected/reserved bytes 与结算后的 actual bytes 都不得超过 32 MiB、有效期默认 30 分钟；
- 每 form 最多 100 个活动 public drafts，由 MySQL 在多副本下最终仲裁；
- 每个 candidate 仍受 B24 单文件、tenant/global bytes、pending intent、active upload/inspection 和 control reserve；
- source bucket 只使用正确解析的 direct peer 或 trusted proxy 地址，不保存到业务表；
- 多副本全局 source quota 若是部署要求，仍由网关或共享 admission 服务执行；MySQL form/tenant/global 容量不能被进程内 bucket 代替。

`atom.files.public-marketing.enabled` 默认 `false`。只有普通 B24 upload/inspection 均 READY、public-file 配置合法且表单入口 admission 可用时才接受新 draft；关闭后仍要执行既有 draft expiry、deletion 和容量释放。

## 7. B29c：OA 公告附件

### 7.1 内容集合与 API

B29c 增加 `OA_ANNOUNCEMENT` target，复用：

```text
/api/v1/attachment-targets/oa-announcement/{announcementId}/attachments
```

附件是公告内容集合的一部分，但正文继续是纯文本；不支持 inline image、HTML、Markdown、任意 URL、远程抓取或 EMAIL attachment。

管理者只有在公告为 `DRAFT` 时才能创建、上传或撤回附件。`SCHEDULED`、`PUBLISHED`、`WITHDRAWN` 和 `DELETED` 均拒绝附件 mutation。取消排期回到 DRAFT 后可以重新编辑，下一次 schedule 使用增加后的 publication generation 和当时的 exact 附件集合。

### 7.2 授权

| 操作 | 交集 |
| --- | --- |
| DRAFT 管理列表/下载 | active membership + `files:read` + `oa-announcements:write` + exact non-deleted root |
| DRAFT 创建/上传/撤回 | active membership + `files:write` + `oa-announcements:write` + exact DRAFT root |
| recipient 列表/下载 | `files:read` + `oa-announcements:read` + active membership + exact immutable recipient snapshot + current PUBLISHED |
| withdraw/delete 联动 | 公告原有写授权；不额外要求执行者拥有 `files:write` |

管理权限不创建 recipient 身份，recipient 身份也不授予公告管理。没有 `files:read` 的接收人仍可读取正文和写 read receipt，但不能通过附件计数、文件名或错误判断附件是否存在。

### 7.3 排期、发布与冻结

schedule 和立即 publish 必须在公告根锁下读取最多 101 个 current relations，并要求：

- active attachment 数量不超过普通 target 的 100 上限；
- 每个 relation exact 关联本公告；
- 每个 file 已 `AVAILABLE`，没有 UPLOADING、PENDING、REJECTED、INDETERMINATE、WITHDRAWN 或删除中的候选；
- attachment set 与公告 root 在同一事务快照中，检查后到状态变为 SCHEDULED/PUBLISHED 之间不能被修改。

任一文件未就绪时，schedule/publish 整体失败，不忽略、不截断，也不先发布无附件正文。到期 publication 重新验证同一冻结集合仍可用；失败按新的固定 `ATTACHMENT_UNAVAILABLE` safe reason 结算 occurrence，并把公告安全返回 DRAFT，不写 recipient 或 EMAIL outbox。

公告不为每个 recipient 复制 file relation。一个冻结的 announcement-level attachment set 与不可变 recipient snapshot 共同决定下载授权，避免 `10,000 × 100` 关系膨胀。

B28b EMAIL template、payload digest 和 exact command 保持 bit-for-bit 兼容：EMAIL 不携带文件、文件名、附件数、下载 URL 或 bearer token。接收人只能通过登录后的同源 API 重新授权下载。

### 7.4 生命周期和锁序

相对锁序扩展为：

```text
organization_tenant
  -> oa_announcement
  -> publication_occurrence（存在时）
  -> announcement attachment relation/file
  -> recipient snapshot
  -> notification delivery
```

附件 worker 对公告 target 也从 organization tenant → announcement → relation/file 开始；不能先锁 generic file 再反向锁公告。

withdraw 或 delete 在公告事务中关闭最多 100 个 current relations并创建 deletion work。`WITHDRAWN` 公告不再发布，因此附件无需为未来重新发布保留可写关系；provider/storage 删除仍在事务外完成。已经打开的读取遵守 B24 read-lease/cancel 语义。

## 8. B29d：OA 申请提交后协作附件

### 8.1 与不可变申请事实分离

B24c 的 `crm_oa_request_file_value` 和 approval FILE snapshot 是提交时的权威事实，继续不可修改。B29d 的普通附件：

- 不带 `fieldCode`；
- 不进入 `OaRequest.facts`、submission digest、approval route 或 FILE snapshot；
- 不能补齐一个提交时缺失的 required FILE；
- 不改变 request/version、approval task、worklist acknowledgement 或审批历史；
- 在 OA Request 输出中作为独立 attachment capability 使用，不混入 `fileFields`。

### 8.2 新的协作能力与参与者

计划新增 tenant-assignable `oa-requests:collaborate`。普通上传/撤回要求：

```text
active membership
  ∩ files:write
  ∩ oa-requests:collaborate
  ∩ exact visible OA request
  ∩ authoritative approval status PENDING
  ∩ one current collaboration role
```

首期 current collaboration role 只含：

- exact applicant；
- 当前未完成 approval task 的 assignee；
- submission-time snapshotted instance manager。

历史 assignee、watcher/抄送人、同部门成员和只有 `oa-requests:manage` 的 tenant reader不能写附件。`oa-requests:manage` 保持只扩大读取，不隐式获得 collaborate。

列表、详情和下载继续要求 `files:read + oa-requests:read`，并复用申请当前参与可见性；具有 `oa-requests:manage` 的成员可以在同时拥有 `files:read` 时读取 tenant 内可见申请附件，但仍不能写。

### 8.3 PENDING 截止与审批总序

OA Request 根不保存审批状态，B11 `ApprovalInstance` 仍是唯一真相。target adapter 必须锁 exact approval instance，再锁 OA request 和 relation/file：

```text
approval_instance
  -> oa_request
  -> attachment relation/file
```

审批 action、batch action、withdraw 和 cancel 对 OA_REQUEST subject 使用相同相对顺序。任何可能把 instance 变成终态的事务都必须在这个锁序内执行有界 attachment cutoff：关闭全部非 `AVAILABLE` current relation、按 B24 撤回协议登记 deletion work，再冻结剩余全部 `AVAILABLE` 的 exact attachment set，最后才提交审批终态。cutoff 是父生命周期动作，只要求原审批 action 的授权，不额外要求执行者拥有 `files:write`；否则失权、失活或恶意上传者可以用一个永不就绪的 candidate 永久阻塞审批。

终态之后：

- 不接受新 intent、content upload 或 withdraw；
- 现有 AVAILABLE 附件按当前申请可见性继续读取；
- 后台 worker 不能用旧 intent 绕过终态；被 cutoff 的 in-flight store/inspection 即使稍后返回，也只能进入 B24 撤回/确认删除收敛；
- OA Request 没有 soft-delete API，因此首期没有 parent-delete cascade；文件仍受部署 retention、人工终态前撤回和确认删除协议约束。

这条冻结只保证本地附件集合与审批终态的数据库总序，不声明审批人已经实际下载、阅读或理解文件。

## 9. HTTP、输出与安全失败

- 所有人类内部路由继续由 Spring Security 建立 `AuthenticatedCaller`，tenant 只从验证上下文派生；
- Marketing public credential 只在专用公开路由解析，不能调用任何内部 attachment/file-field API；
- target/file mismatch、跨 tenant、隐藏父记录、错误 field、错误 form version、错误 draft credential和真实不存在使用不泄露业务存在性的安全结果；
- 只有在 exact target 已授权后，真实空附件页或空 FILE 值才可以返回；
- 文件名、标题、表单字段 label 和 OA request title 不进入日志、metric label、health、safe reason 或 job controlled reason；
- credential、checksum、locator、storage identity、scanner detail、request body、email、IP、User-Agent、Referer 和异常原文不进入 domain event、Activity、outbox 或普通审计；
- public credential、human auth token、connector bearer 和 storage/provider credential 使用不同 scheme、guard 和 purpose domain，不能互换。

## 10. 持久化计划

### V77 — Product/ReturnVisit targets and FILE

- 为 `crm_attachment_relation` 增加 `product_id`、`return_visit_id`、typed tenant FK、覆盖索引和扩展 exact-one CHECK；
- 扩展 `crm_file_field_draft` object CHECK；
- 为 `crm_attachment_field_binding` 增加 Product/ReturnVisit typed columns、FK、索引、generated target identity 和 exact-one CHECK；
- 不改标量 Product/ReturnVisit custom-value 表，不把 FILE 存成 JSON/string。

### V78 — Marketing public draft and upload origin

- 建立 `crm_marketing_file_draft`，保存 tenant/form/formVersion、credential digest、deadline、state/version 和有界查询索引；
- 建立有序 candidate relation，使用 exact field/file FK、ordinal、state、content version 和 evidence digest；
- 把 `crm_attachment_file` 的 upload origin 从必填 member 扩展为 closed exact-one `MEMBER` 或 `PUBLIC_MARKETING_FORM`；全部历史行回填为 MEMBER；
- PUBLIC 行必须关联 exact public draft且 `uploader_actor_id` 为空，MEMBER 行保持现有 membership FK；
- 不保存 plaintext credential、source address、cookie、request metadata 或 Lead ID 到 draft root。

### V83 — Announcement target

- 为 `crm_attachment_relation` 增加 `oa_announcement_id`、typed tenant FK、root/status 查询索引和扩展 exact-one CHECK；
- 不在 recipient 表复制 file ID，不在 delivery/outbox 增加 attachment、URL 或 payload 列；
- 若 safe reason CHECK 需要新值，使用同一新增迁移更新，不能编辑 V75。

### V84 — OA Request collaboration target

- 为 `crm_attachment_relation` 增加 `oa_request_id`、typed tenant FK、申请列表/终态检查索引和最终 exact-one CHECK；
- 不修改 `crm_oa_request_file_value`、approval/stage FILE fact 或 `oa_request.facts_json`；
- authority 目录变化由代码提供，不在请求或数据库角色行中自动授予普通角色；tenant owner 继续按现有动态目录规则解析。

每次迁移都必须提供 empty-schema、V82 upgrade、历史行 backfill/CHECK、跨 tenant FK 和 rollback-on-failure fixture。不能依赖 Java pre-check 代替 exact-one、same-tenant 或 active-slot 数据库仲裁。

## 11. 配置、readiness 与运维

B29a/c/d 不增加 storage/scanner profile。它们只在既有 `atom.files.*` 对相应 upload/inspection/download operation READY 时工作，普通业务 API 在文件 lane disabled/not-ready 时继续可用。

B29b 计划增加独立 public gate：

| 配置 | 默认 | 代码绝对边界 |
| --- | --- | --- |
| `atom.files.public-marketing.enabled` | `false` | 只控制新 public draft/candidate admission |
| `draft-ttl` | `PT30M` | 5 分钟至 2 小时 |
| `max-files-per-draft` | `10` | 1..10 |
| `max-bytes-per-draft` | `33554432` | 正数且不超过 64 MiB |
| `max-active-drafts-per-form` | `100` | 1..100 |
| `max-requests-per-form-per-minute` | `120` | 1..600 |
| `max-requests-per-source-per-minute` | `30` | 1..120 |

非法 public 配置只关闭新匿名文件 admission；已有 public drafts 继续 expiry/cleanup，内部 B24 文件和标量 MarketingForm submission 不受影响。开启 MarketingForm FILE 不能使 B24 storage/scanner READY，B24 READY 也不能自动打开匿名入口。

health 只报告 fixed `DISABLED/CONFIG_INVALID/FILES_NOT_READY/RUNTIME_FAILURE/READY`，不包含 tenant、form、draft、file、field、source 或异常。metrics 只使用 fixed route-kind/outcome/state 标签。

## 12. B29a 实现任务卡

1. 扩展 target/file-field closed catalogs，但保持 registry 完整性和未知 target 失败关闭；
2. 实现 Product/ReturnVisit read/collaborate/lock/lifecycle adapters；
3. 实现 V77、PO/mapper/converter 和父删除普通 relation + FILE binding 清理；
4. 给 Product/ReturnVisit Request/VO/Response、command/query 和 file-field download 接入结构化 FILE；
5. 覆盖普通附件、创建前草稿、whole-list replace、字段生命周期、数据范围和删除竞态；
6. 更新 API、架构、对象分层、测试指南和 `docs/acceptance/B29a.md`。

## 13. B29b 实现任务卡

1. 增加匿名 upload-origin domain vocabulary 和专用 public credential guard，不构造 caller/member；
2. 实现 V78、hash-only credential、public draft/candidate store 和有界 expiry；
3. 扩展 MarketingFormPolicy/public render，只允许配置的 custom LEAD FILE；
4. 实现 closed public routes、canonical admission、流式 upload/status 和无下载边界；
5. 在现有 Marketing submit transaction 中原子消费 draft、创建 Lead、绑定 FILE 并追加 attribution；
6. 增加 abuse、token leak、版本漂移、停用、重复消费、MySQL crash 和完整 B24 worker fixture；
7. 更新配置/API/测试文档和 `docs/acceptance/B29b.md`。

## 14. B29c 实现任务卡

1. 增加 OA Announcement target adapter和 V83 typed parent；
2. 只在 DRAFT 开放管理 mutation，并为 manager/recipient 实现互不替代的 read predicates；
3. 将 attachment readiness/set freeze 接入 schedule、publish-now 和 due coordinator；
4. 保持 B28 Work/OA EMAIL command compatibility，证明零 attachment/URL进入 outbox；
5. 接入 withdraw/delete 有界关闭与 target-first worker锁序；
6. 增加 schedule/cancel/reschedule/publish/withdraw/download 并发 fixture 和 `docs/acceptance/B29c.md`。

## 15. B29d 实现任务卡

1. 增加 `oa-requests:collaborate`、current collaboration projection和 OA Request target adapter；
2. 实现 V84 typed parent，不触碰 immutable submission FILE 表；
3. 让 OA approval mutation 与 attachment mutation 共享 instance → request → file 总序；
4. 在终态事务内有界 cutoff 未就绪 collaboration candidate，冻结其余 AVAILABLE 集合，并在终态后拒绝 mutation；
5. 覆盖 applicant/current assignee/manager 与 historical assignee/watcher/OA manager负向矩阵；
6. 增加申请列表/详情/下载、B11 action/batch/withdraw/cancel 竞态 fixture 和 `docs/acceptance/B29d.md`。

## 16. 验收门槛

B29 全部完成必须同时证明：

1. B29a Product tenant-wide 能力不被 owner/category扩大或缩小，ReturnVisit 四类 data scope 在普通附件和 FILE 两条路径独立生效；
2. Product/ReturnVisit FILE 只存在于结构化 binding，标量、saved view、keyword、report和产品快照都没有 file ID；
3. 两类父删除与 worker publication 并发只有“父 active + exact current relation”可以 AVAILABLE，删除提交后新读取为零；
4. B29b 匿名请求从未构造 `AuthenticatedCaller`/MemberId，数据库也不把 owner 记录成 uploader；
5. public credential 跨 form/version/draft/tenant、猜测 ID、重放、过期和泄漏 sink 全部失败关闭；
6. public form 停用/更新与 upload/submit 并发按 form root 总序，旧 draft 不能创建 Lead；
7. Lead、FILE binding、draft consumption 和 attribution 在提交成功/回滚/ack-loss 下保持一个原子事实；
8. public form/source/draft/tenant/global 容量在并发和多副本下不超限，storage/scanner 调用均受当前 readiness 和预算；
9. B29c schedule/publish 只接受全部 AVAILABLE 的冻结集合，pending/rejected/第 101 个文件不会产生部分 recipient/outbox；
10. announcement recipient 只有当前 PUBLISHED + snapshot + 两项读能力交集才能下载，manager 与 recipient 权限不互相替代；
11. B28 EMAIL exact command 与 V76 历史行保持兼容，附件绝不进入 EMAIL；
12. B29d immutable OA FILE facts/submission digest/approval route 在协作附件增删前后完全相同；
13. OA collaborate 只有 applicant、current task assignee 或 instance manager，历史参与、watcher、同部门和普通 OA manager 均不能写；
14. approval terminal 与文件 mutation/worker 竞态形成一个总序，终态事务原子关闭未就绪 candidate、冻结 AVAILABLE 集合，且之后不能新增/撤回或被旧 worker 复活；
15. 所有新 relation/check/FK/index、跨 tenant、隐藏 target、range、撤回、delete 和 retention 在真实 MySQL/Testcontainers 通过；
16. `sh ./mvnw test` 与 `CI=true sh ./mvnw test` 全部通过。B29 涉及 schema、权限、事务、mapper、文件 worker和匿名入口，Docker/MySQL 套件是每个子批次的必需门槛。

## 17. 明确非目标

B29 不包含：

- tenant 文件库、文件夹、跨记录移动/复制、共享链接、匿名下载、外部协作者或预签名 URL；
- Product 图片画廊、缩略图、在线预览、转码、OCR、全文索引、电子签名或文档协同编辑；
- 把 Product FILE 复制到 Opportunity/Contract 产品快照，或把 ReturnVisit FILE 复制到 Activity/worklist；
- MarketingForm 品牌图片、表单附件下载、公开提交历史、幂等提交升级、captcha选型或保存匿名请求原文；
- 公告 inline image、EMAIL attachment、动态下载 URL、发布后增删附件或向新成员补投；
- 修改 OA 提交 FILE、用协作附件参与 approval condition、自动通知审批人或声称审批人已阅读；
- Activity、ApprovalInstance、StageWorkflowRecord、CustomerPool、ContactPool、MarketingForm 配置根本身的普通附件；
- B22 recording、外部企业报告、CSV/Excel 导入导出、批量迁移、备份恢复下载或 data lake；
- 新 storage/provider/scanner 选型、S3 生产 bean、managed scanner readiness或把合成测试描述为生产证据。

## 18. 实施顺序

1. 先完成 B29a 两个纯 human target，证明 B24 target/FILE 扩展不需要分叉内核；
2. 再实施 B29b 的 upload-origin schema 和独立 public credential，先用 disabled storage 证明零匿名旁路，再接入 synthetic READY lane；
3. 在 B28 回归全绿后实施 B29c，把附件冻结接到同一个 publication coordinator，而不是另建公告发布流程；
4. 最后实施 B29d，因为它需要同时修改 OA target access 和 B11 terminal lock oracle；
5. 每批完成后回写实际迁移、operation、测试命令和 production readiness 结论；未实现的后续批次继续失败关闭，不因前一批 schema 存在而提前暴露。
