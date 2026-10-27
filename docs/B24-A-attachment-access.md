# B24-A 业务附件授权、目标关系与父生命周期契约

> 基线：`f30240b docs(B24): 规划下一批能力边界与任务拆分`
>
> 对账日期：2026-07-23
>
> 状态：B24-A 设计契约已闭合；本文不实现 API、Java 类型、能力目录、迁移或存储。

## 1. 闭合结论

B24 首期不建立一个能看见所有 `BusinessObjectType` 的通用文件库，也不把附件权限塞进现有业务域权限中。每次操作必须对一个明确目标取两类权限的交集：

1. 当前活动 human caller 拥有独立、tenant-assignable 的 `files:read` 或 `files:write`；
2. 目标所属业务域使用自己的当前能力、数据范围、参与人关系和生命周期判断记录是否可读或可协作。

`files:*` 不携带数据范围，不会将 `SELF` 提升为 `TENANT`，也不会把 Work 任务参与权反向变成 CRM 记录权限。B29a Product 仅以 active membership 和 `products:read/write` 交集授权，绝不从 owner/category 推导范围；ReturnVisit 继续重用当前 read/write scope。tenant owner 按现有代码目录动态拥有能力，仍必须通过目标域守卫。

本节的 B24 首期目标白名单固定为 `WORK_TASK`、`CUSTOMER`、`CONTACT`、`LEAD`、`OPPORTUNITY`、`CONTRACT`、`INVOICE`、`RECEIVABLE` 和 `RECEIVABLE_PLAN`。B29a 已在同一显式目录中追加 `PRODUCT` 与 `RETURN_VISIT`；它们不再是 unsupported，仍不得通过默认分支或反射仓储自动纳入。`WORK_TASK` 不是 B05/B11 的 `BusinessObjectType`，因此实现应使用 B24 自己的稳定 `AttachmentTargetType` 目录，不为了复用一个 `switch` 就扩大 B05 对象枚举。

`OA_REQUEST`、公告、Activity、审批实例、阶段运行记录、营销表单、公海、connector 和 B22 recording 不在当前实现白名单。Product 与 ReturnVisit 已由 B29a 用独立授权、锁序、删除和 fixture 接入；其余新目标类型也必须具备这些证明，不能由 `default` 分支或一个反射仓储自动纳入。

## 2. 能力与操作交集

### 2.1 首期文件能力

| 能力 | 语义 | 明确不授予 |
| --- | --- | --- |
| `files:read` | 在目标域当前读守卫通过后，列出一个明确目标的可用附件、读取受控元数据并下载字节 | tenant 文件库、隐藏目标发现、待检查/被拒绝对象、provider 定位器 |
| `files:write` | 在目标域当前协作守卫通过后，创建/消费自己的上传意图，以及撤回该目标的附件 | 业务字段写权、业务记录删除权、跨记录移动/复制、安全取证下载 |

首期不增加 `files:manage`、`files:delete` 或文件所有者超级权限。撤回是目标协作内容管理，与上传使用同一 `files:write` 能力；有当前目标协作权的记录参与者可撤回任何当前附件，不以历史 uploader 身份建立无法治理的个人文件所有权。高风险取证、保全或被拒绝对象处理若以后需要，必须使用独立能力和隔离主体。

`files:write` 不隐式包含 `files:read`。创建意图和上传接收可返回该操作的最小安全状态；对已发布附件的列表、详情和下载仍需要 `files:read`。

### 2.2 操作矩阵

| 操作 | 文件能力 | 目标域判断 | 额外绑定 |
| --- | --- | --- | --- |
| 创建上传意图 | `files:write` | 当前协作/写能力与范围 | 在目标根锁内生成文件 ID，固定 tenant、target type/ID 和 uploader member |
| 打开上传流 | `files:write` | 再次应用当前目标协作判断 | caller 必须是意图中 exact uploader；意图状态、版本、deadline 与对象身份仍匹配 |
| 读取上传/检查安全状态 | `files:write` | 当前目标协作判断 | 仅 exact uploader；不返回 scanner/provider 细节 |
| 列表与可读详情 | `files:read` | 当前目标读判断 | 必须先授权目标再查有界 `AVAILABLE` 关系 |
| 下载或未来的单 range | `files:read` | 每次请求重新应用当前目标读判断 | exact target/file 关系、附件版本、`AVAILABLE`、storage readiness 和删除截止时间均通过 |
| 撤回附件 | `files:write` | 当前目标协作判断 | 目标根锁先于 file/link 锁；同一本地事务关闭下载并创建 durable deletion work |
| 父记录软删除联动 | 不额外要求 `files:write` | 父域本身的删除能力、范围和聚合规则 | 父域事务撤回所有当前附件并产生删除任务；不等外部存储 I/O |

父删除联动是父聚合生命周期的完整性效果，不是一个隐式文件管理员权限。它只能关闭该 exact parent 的关系，不能操作其他记录或 tenant 的文件。

## 3. 目标域权限与生命周期矩阵

“目标协作判断”不表示可以调用该聚合的任意业务方法。附件关系是独立协作内容；只要下表明确允许，关闭商机、作废合同、已开票申请或已完成任务仍可有附件协作，但这不会改写它们的业务事实或版本。

| B24 目标 | 列表/下载交集 | 创建/上传/撤回交集 | 可保留附件的非删除状态 | 关闭或删除规则 |
| --- | --- | --- | --- | --- |
| `WORK_TASK` | `files:read` + `work-tasks:read` + creator/当前 assignee/follower，或 `work-tasks:manage` 扩大的任务可见谓词 | `files:write` + `work-tasks:write` + 同一参与/管理可见谓词 | `TODO`、`IN_PROGRESS`、`DONE`、`CANCELLED` | `DELETED` 不可读写；任务存在任何附件/上传历史时禁止软删除，与现有 comment-history 限制一致 |
| `CUSTOMER` | `files:read` + `customers:read` + B06 READ 数据范围 | `files:write` + `customers:write` + B06 WRITE 数据范围 | `ACTIVE` | `IN_POOL` 期间普通附件全部不可读写但不删除，恢复 `ACTIVE` 后重新按当前范围可见；`DELETED` 联动撤回与清理 |
| `CONTACT` | `files:read` + `contacts:read` + B08 READ 数据范围 | `files:write` + `contacts:write` + B08 WRITE 数据范围 | `ACTIVE` | `IN_POOL`/`DELETED` 与 Customer 同语义；获得 Customer 权限不会授予 Contact 附件 |
| `LEAD` | `files:read` + `leads:read` + B09 READ 数据范围 | `files:write` + `leads:write` + B09 WRITE 数据范围 | `ACTIVE`、`CONVERTED` | conversion 不复制、转移或暴露附件，也不更改已保存的转换事实；`DELETED` 联动撤回与清理 |
| `OPPORTUNITY` | `files:read` + `opportunities:read` + B10 READ 数据范围 | `files:write` + `opportunities:write` + B10 WRITE 数据范围 | root `ACTIVE`，包括业务结果 `OPEN/WON/LOST/INVALID` | close/reopen 不改写附件；root `DELETED` 联动撤回与清理 |
| `CONTRACT` | `files:read` + `contracts:read` + B13 READ 数据范围 | `files:write` + `contracts:write` + B13 WRITE 数据范围 | `ACTIVE`、`VOIDED` | void 不撤回附件；`DELETED` 联动撤回与清理，仍受现有发票/回款历史删除约束 |
| `INVOICE` | `files:read` + `invoices:read` + B13 READ 数据范围 | `files:write` + `invoices:write` + B13 WRITE 数据范围 | `PENDING`、`ISSUED` | issue/reset 不改写附件；`DELETED` 联动撤回与清理 |
| `RECEIVABLE` | `files:read` + `receivables:read` + B14 READ 数据范围 | `files:write` + `receivables:write` + B14 WRITE 数据范围 | `ACTIVE` | `DELETED` 联动撤回与清理 |
| `RECEIVABLE_PLAN` | `files:read` + `receivable-plans:read` + B14 READ 数据范围 | `files:write` + `receivable-plans:write` + B14 WRITE 数据范围 | `ACTIVE` | `DELETED` 联动撤回与清理，仍受现有回款历史不可变约束 |

上表的 CRM 读写交集复用现有 access resolver 生成的 exact `TenantId` 和数据范围；它不复用某个关系 Customer、Contract 或 Opportunity 的权限代替目标自身权限。附件不是 B05 字段值，所以通用附件不额外要求某个业务字段的 `READ/WRITE`，也不会因某个字段被遮罩就提升附件权限。

## 4. 专用目标路由，不扩大通用 guard

现有 `BusinessRecordAccessGuard` 是 Activity、审批、阶段和 Work 任务 CRM 关系的共享读/写数据范围路由，它对 `OA_REQUEST` 明确只允许读，也不理解 Work 任务参与语义。B24 不修改它的 boolean `write` 参数去同时表示数据范围、附件协作、生命周期和父锁。

后续实现应有一个 B24 专用 application 路由；[B24-SPLIT](B24-SPLIT-implementation-boundaries.md) 已将其正式命名为 `AttachmentTargetAccess` 与显式 `AttachmentTargetAccessRegistry`：

- 只接受 `AuthenticatedCaller`、显式 `AttachmentTargetType`、opaque local target ID 和固定的 `READ`/`COLLABORATE` 操作；
- CRM 分支可复用现有 resolver/repository 判断，但必须有能在命令事务内锁定 exact root 并重读生命周期的 typed 变体；
- Work 分支必须调用 `WorkTaskRepository` 的 creator/assignee/follower/manager 可见谓词，不能通过任务上可选 CRM link 授权；
- 目标域返回的 tenant 必须与附件根的 tenant 精确一致，普通请求中的 target/file ID 不能切换 tenant；
- 任何未知枚举、未注册分支、仓储异常或无法证明的生命周期都失败关闭，不回退到 tenant 级读写。

列表可用目标开始，所以可在一个已授权目标下做有界分页。批量输出若以后需要，必须先用目标域自己的有界 SQL 可见性批次过滤，再读取最终页的附件；不能先分页所有 file rows 再在 JVM 中删除隐藏目标。

## 5. 关系基数与持久化证明

### 5.1 不可改绑的单一目标

每个上传意图从创建时就有且只有一个 exact `(tenant, targetType, targetId)`。uploader 也在该时固定，但不成为附件的永久个人 owner。

- 一个 file identity 在全生命周期最多对应一个业务目标；
- 一个目标可有 `0..N` 个当前附件，`N` 由 B24-S 的部署可执行上限冻结；
- 撤回保留当前 tenant-scoped tombstone 和删除证据，不允许将原 file ID 改绑到另一目标；
- 要将相同本地字节放到另一条记录，必须创建新文件身份，重新授权、上传、检查和计费；
- checksum、文件名、字节相等或 uploader 相同都不产生共享关系。

公共操作的语义定位必须同时携带目标和 file identity。最终 HTTP 路由尚未冻结，但一个只接受 file ID 并在成功响应前暴露目标类型、目标 ID 或文件元数据的设计不符合本契约。target/file 不匹配与 target/file 不可见使用同一安全 not-found 语义。

### 5.2 数据库不能只靠多态字符串

一个只含 `(tenant_id, target_type, target_id)` 而没有 typed parent 完整性证明的通用表，不足以阻止悬空 owner。实现迁移必须同时证明：

1. file root 和附件关系同 tenant，且一个 file 只有一个关系；
2. 关系指向的 exact typed parent 在同 tenant 真实存在；
3. 父记录软删除不物理删除审计所需的关系 tombstone；
4. 未来每增加一个 target type，都有自己的 composite foreign-key 或等价的 MySQL 可执行不变量，而不是只加一个 enum 值。

[B24-SPLIT](B24-SPLIT-implementation-boundaries.md) 已选择 `crm_attachment_relation` 中央关系表：V59 先提供 Work typed parent，V60 以 nullable typed parent columns、tenant-composite foreign keys 和 exact-one CHECK 扩到八类 CRM。不允许以 application “先查 parent 再 insert generic row”作为唯一完整性保障。

## 6. 父生命周期、锁序与异步发布

### 6.1 与父删除建立总序

创建意图、撤回附件、worker 发布 `CLEAN` 对象以及父记录软删除，必须使用同一个目标根作为本地事务序列点。锁序固定为：

```text
owner domain's existing tenant/object guard when required
  -> exact target root
  -> attachment/file roots ordered by file ID
  -> durable inspection/deletion work rows
```

任何路径不能先锁 file 再反向锁 parent。Work Task 分支保留 B21 的 tenant root -> task root 顺序。CRM 分支保留各自现有对象/结构/合同根锁，B24 只在它们之后增加 file/link 锁。

上传意图在目标根锁内保存不可改绑的 private link。字节上传和检查发生在数据库事务外。检查 worker 提交 `CLEAN` 时不伪造 human caller：它在目标根锁内只重新确认 exact parent 仍存在、生命周期容许、private link 未撤回，且 object identity/version/digest 与检查 attempt 一致。上传已由 human command 接受后，uploader 在扫描期间失权不会把 worker 变成授权主体；后续列表和下载仍每次重新应用当前读权。

若父记录先删除或附件先撤回，worker 不发布，而是保持不可读并进入清理。若 worker 先发布，随后的父删除在同一个本地事务中撤回该关系。不存在“扫描完成所以绕过父删除”的竞态结果。

### 6.2 删除与保留

- CRM 父记录软删除的本地事务必须对有界活动/private 关系做批量撤回，并为已有字节的 exact object 创建 durable deletion work；
- 这个事务不调用对象存储，不因 storage 不可用而把已删除 CRM 记录的附件重新打开；
- 文件实际 bytes 在确认主存储不可读前仍是内部待清理状态，但公共访问在本地撤回提交后立即关闭；
- Customer/Contact 进入公海、Lead 转换、Opportunity 关闭、Contract 作废、Invoice 开票、Work Task 完成/取消都不是文件删除事件；
- Work Task 采取更严的历史保护：只要存在任何上传意图、附件关系或其 tombstone，就与现有评论历史一样阻止任务软删除，避免把已参与协作的任务伪装成从未使用的 TODO。

## 7. 隐藏记录与探测失败语义

1. 路由先验证 human authentication、活动成员和所需 `files:*` 与目标域入口能力，才进入目标/file 查询；
2. 通过入口能力后，target 不存在、已软删除、数据范围隐藏、参与关系失效、跨 tenant、file 不存在、file 属于另一目标或状态不可公开，都使用同一安全 not-found 语义；
3. 只有在目标已授权后，一个真实“无附件”的目标才返回空页。隐藏目标不返回空页、数量 `0` 或文件名提示；
4. 未授权时不调用 storage HEAD/GET，不打开上传/下载流，不发放句柄，不增加命中可观测标签；
5. 待上传、待检查、被拒绝、不确定、已撤回或删除中的对象不占用普通列表的分页位置，也不进入 total；
6. 公开 file ID 仍是 opaque identity，不把编码格式或随机性当成授权边界。

失去目标权限只关闭当前访问，不自动删除关系或字节。重新获得目标权限后，只会重新看见仍为 `AVAILABLE` 且未过保留/删除截止时间的附件。

## 8. B05 `FILE`、OA Request 与 Work Task 特例

### 8.1 B05 `FILE` 继续失败关闭

通用附件不保存 `fieldCode`，不进入 Customer/Contact/Lead/Opportunity 等聚合的 custom value collection，不参与唯一值预留、布局、保存视图、keyword、报表、阶段必填字段或审批事实。

`CustomerFieldValuePolicy`、`ContactFieldValuePolicy`、`LeadFieldValuePolicy`、`OpportunityFieldValuePolicy`、`ApprovalFactPolicy`、`SavedViewPolicy`、`MarketingFormPolicy`、`OaFormServiceImpl` 和 `OaRequestApplicationSupport` 对 `FILE` 的现有拒绝或空操作不修改。B24c 必须另行冻结字段基数、必填/替换、`READ/WRITE/HIDDEN/MASKED` 交集、字段生命周期和快照语义，不能只将 file ID 放进字符串值。

### 8.2 OA Request 不纳入 B24a/b 通用附件

`OaRequest` 在当前模型中是提交时即不可变的权威事实，`BusinessRecordAccessGuard` 对它也明确拒绝 write mode。一个 OA Request 在创建前又没有可绑定的 exact request ID。因此 B24-A 不在下列两个不等价方案中猜一个：

- 不把提交后新上传的 bytes 当成已保存 B11 路由/事实快照的一部分；
- 不把 `oa-requests:submit`、`oa-requests:read` 或 `oa-requests:manage` 偷换成尚未定义的提交后协作写权。

OA 文件只能在 B24c 或独立 OA 附件任务中回归：要么作为表单提交前已 `CLEAN` 且在同一提交事务中冻结的 FILE 事实，并解决“目标尚未存在”的受控绑定；要么新建明确的 OA 协作内容能力、参与人写语义、快照隔离和删除规则。在那之前 `OA_REQUEST` 不是 `AttachmentTargetType`。

### 8.3 Work Task 是第一个纵切，但不继承 CRM link

Work Task 附件是任务协作内容，与评论同样可在 `DONE`/`CANCELLED` 任务上继续保留和访问，但它不修改任务根、不增加任务乐观锁版本、不改变 `worklistProjectionVersion`、不生成 Activity，也不改变责任人/关注人/评论。

任务可选 CRM relation 与任务附件完全独立：

- 一个当前任务参与者即使看不到可选 CRM target，仍可按任务权限访问任务自身的附件；
- 一个只看得到该 CRM target 而不是任务参与者的成员，不能访问任务附件；
- 直接挂在 CRM target 上的另一个附件必须重新经过该 CRM 域权限，不能复用任务参与权。

## 9. B24-X 必须执行的授权 oracle

| 场景 | 输入/竞态 | 唯一允许结果 |
| --- | --- | --- |
| `A-01` | 有 `files:read`，无目标 read 能力 | 在 file/link/storage 读取前拒绝 |
| `A-02` | 有目标 read，无 `files:read` | 在 file/link/storage 读取前拒绝 |
| `A-03` | 能力齐全但目标在数据范围外、参与关系失效或跨 tenant | 安全 not-found，不泄露文件数、名称或存在性 |
| `A-04` | target 可见，file ID 属于另一 target/tenant | 安全 not-found，不打开 storage |
| `A-05` | Work Task 参与者看不到 task 的 CRM link | 仍可按 task 交集读任务附件，但输出不暴露 CRM link |
| `A-06` | 只有 CRM target 读权，不是 task 参与者 | 不可读 task 附件 |
| `A-07` | Customer/Contact 从 `ACTIVE` 进入 `IN_POOL` 再恢复 | 入池期间列表/下载/上传/撤回都不可用；不删除字节；恢复后按新范围重新判定 |
| `A-08` | Lead conversion、Opportunity close、Contract void、Invoice issue 或 Work Task finish | 不自动撤回/复制/发布附件；后续操作仍按当前交集授权 |
| `A-09` | 检查 worker 与父删除并发 | 按目标根锁总序收敛为 `AVAILABLE` 后立即撤回，或从未 `AVAILABLE`；最终不可下载且只有一个删除工作 |
| `A-10` | 撤回附件与下载并发 | 本地撤回提交后的新下载必须拒绝；旧句柄取消规则交由 B24-S/X 与 storage profile 组合证明 |
| `A-11` | 任务存在已撤回或删除完成的附件 tombstone 时请求删除 task | 与存在评论历史一样整体拒绝 |
| `A-12` | 尝试使用 OA Request、Announcement、Product、Return Visit、Approval 或 B22 recording 作为通用 target | 在创建意图前以不支持的 target 失败关闭 |
| `A-13` | 隐藏 target 分别拥有 0 个、1 个或上限数附件 | 响应类型、安全错误、storage 调用次数和普通可观测性完全一致 |
| `A-14` | 同 file 被并发链接到两个 target，或 generic row 指向不存在 parent | MySQL 唯一键/typed parent 完整性至少使一方回滚，不依赖 JVM 先查后写 |

[B24-X](B24-X-composed-fixture.md) 已把上表映射到 `X-01..X-08`；其 oracle 必须独立计算预期结果。storage/scanner adapter 只能报告自己的 typed 事实，不能把“目标应该可见”作为模拟响应传回核心。

## 10. 对实现拆分的约束

B24-A 闭合后，正式拆分必须遵守：

1. B24a 只以 `WORK_TASK` 完成第一个纵切，使用 B21 专用参与可见性，并把附件历史纳入 task delete 限制；
2. B24b 只扩展到本文的 8 个 CRM target，逐域接入 typed parent lock、读/协作范围与软删除联动；
3. B24c 处理 B05 `FILE` 与 OA 提交前文件快照，不把 OA Request 预先塞进 B24b 通用目标；
4. Product、Return Visit、Announcement、OA Request 和其他目标需要新的设计/验收任务，不从 B05 枚举自动继承；前四类后续边界已由 [B29](../batch-B29.md) 分批规划，Activity 继续排除；
5. B24-S 仍决定存储/range/readiness 能力，B24-I 仍决定检查/类型识别语义，B24-A 不用业务权限结论代替两者。

本文自身没有预占最终路由、Java 类名、表名、Flyway 版本、错误码或配额数值；后续 [B24-SPLIT](B24-SPLIT-implementation-boundaries.md) 已在不改变本文业务语义的前提下完成 B24 实现边界选择，[B29](../batch-B29.md) 又为剩余四类业务覆盖分别冻结主体、权限和生命周期。target 白名单、`files:read/write` 交集、专用守卫、单一不可改绑关系、typed parent 证明、父生命周期总序和隐藏记录安全语义继续有效。
