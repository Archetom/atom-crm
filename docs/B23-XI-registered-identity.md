# B23-XI 登记身份、Customer 关联与 MySQL 唯一仲裁

> 决策日期：2026-07-19
>
> 输入：[B23 企业数据规划](../batch-B23.md)、[B23-C 商业企业数据模型 spike](spikes/B23-commercial-enterprise-data.md)、[B23-O 官方/开放登记模型 spike](spikes/B23-official-open-registry.md) 与现有 B06 Customer/MySQL 约束
>
> 状态：身份作用域、法律辖区、主体状态、标识 standing、tenant 内关联基数和 MySQL 并发 oracle 已闭合；本文件不实现 API、Java 类型、迁移、provider adapter 或真实数据 fixture

## 1. 决策摘要

B23 首期采用专用、typed 的登记身份关联，不把登记号塞进 Customer `description`、`tags`、任意 JSON 或普通 B05 自定义字段。一个规范登记身份的稳定键是：

```text
(identityScope, registryScheme, canonicalValue)
```

其中 `identityScope` 表示号码唯一性成立的受治理命名空间，`registryScheme` 表示该命名空间内的格式和校验规则，`canonicalValue` 是按该规则验证后得到的完整规范值。`legalJurisdiction` 是独立的主体事实，不参与身份键。provider、connector、tenant 数据账号、source locator、名称、地址、主体状态、标识 standing、观测时间和来源修订也都不参与身份键。

tenant 内当前关系固定为：

- 一个未删除 Customer 可以关联零到多个规范登记身份，包括同一主体由不同登记系统提供的本地号码和 LEI；
- 同一规范登记身份最多关联一个未删除 Customer；`ACTIVE` 与 `IN_POOL` 都是未删除 Customer，不能各自占用同一身份；
- 相同身份可以分别被不同 tenant 的 Customer 采用，不能建立跨 tenant 全局占用、共享 profile 或共享查重索引；
- B06 仍允许名称、电话或缺少共同规范标识的重复 Customer。B23 唯一约束只证明“同一个精确登记身份没有被当前关联两次”，不证明现实世界主体全局去重；
- Customer 软删除必须在同一事务释放当前身份占用，但已提交的最小择取 provenance 保留。外部主体失活、标识 stale/retired/superseded、刷新无结果或来源不可用都不得自动释放、迁移或重绑身份。

MySQL 用 exact tenant-scoped 唯一键裁决并发，不以 JVM `exists`、缓存、名称匹配或先查后写承担正确性。同身份并发关联到不同 Customer 时只有一个事务能提交；失败事务的 Customer 字段、身份和 provenance 全部回滚。冲突不能向无权调用者暴露已占用 Customer 的 ID、名称、状态或 owner。

## 2. `RegisteredIdentifier` 的规范语义

### 2.1 Identity scope、scheme 与值

本文件冻结的规范结构是语义契约；最终 Java 名称可在实现批次按仓库命名规则落地，但不能折叠以下字段：

```text
RegisteredIdentifier
  identityScope
  registryScheme
  canonicalValue
  identifierStanding
```

`identityScope` 不是查询辖区，也不是数据供应商。它是一个核心受治理目录项，回答“这个值在哪个登记命名空间内被声明为唯一”。`registryScheme` 选择同一目录中明确的字符集、长度、大小写、前导零和校验规则。只有目录中允许的 `(identityScope, registryScheme)` 组合才能进入公共规范对象。

首期代表映射如下；代码文本只是冻结语义的示例，最终实现必须由一个版本化目录统一给出，不能由 adapter 自由拼接：

| 登记身份 | identity scope | scheme 语义 | 规范值要求 |
| --- | --- | --- | --- |
| 中国大陆统一社会信用代码 | 全国统一社会信用代码命名空间 | GB 32100 对应 USCC 方案 | 完整 18 位、受限字符集、大写并通过方案校验 |
| Companies House company number | Companies House 公共登记命名空间 | 完整 company number 方案 | 保留完整 8 位和前导零；字母前缀按受治理规则大写，不转成数字 |
| LEI | Global LEI System 全球命名空间 | ISO 17442 LEI 方案 | 完整 20 位大写字母数字值并通过方案校验 |

GLEIF 的 `RegistrationAuthorityID` 只有在受治理目录把它精确映射到已知 identity scope 和 scheme，且 `RegistrationAuthorityEntityID` 通过该方案验证时，才能产生第二个登记身份。`OtherRegistrationAuthorityID`、validation authority、来源自由文本或未知 RA code 都不能生成身份。

以下值明确不是 identity scope：

- Companies House、GLEIF 或商业 provider 的 API endpoint、账号、key、套餐、connector、source locator、URL 或搜索排名；
- CRM tenant、Customer ID、owner、数据范围、角色或调用者；
- `legalJurisdiction`、登记地址国家、provider 查询 filter 或 UI 所选地区；
- provider 内部 company ID、`KeyNo`、`QccCode`、LOU、validation authority 或响应 resource ID。

同一规范身份被多个 adapter 观察时必须落到相同身份键；来源差异进入观测/provenance，而不能通过增加 provider scope 绕开 tenant 唯一约束。

### 2.2 方案规范化与失败关闭

登记身份不复用当前用于一般 identifier 搜索的通用 NFKC/lowercase 规则。规范化必须由 scheme-specific 目录执行，并同时完成语法和校验：

1. 在 adapter 返回核心前和公共 exact selector 的可信边界分别校验；
2. 只执行该方案明确定义的裁剪、大小写和格式处理；
3. 保留具有身份意义的前导零、前缀和完整长度；
4. 未知 scope/scheme、未允许的组合、非法字符、错误长度、校验失败或响应值与 selector 不一致均失败关闭；
5. 不通过名称、地址、电话、provider ID、相似字符串或 Unicode 猜测生成或合并身份。

当前首期方案的规范值都是有界 ASCII。持久化必须使用 exact binary equality 和可完整索引的有界列；不能使用大小写不敏感 collation、索引前缀、`TEXT`、未经碰撞复核的 hash 或 provider 原始值承担唯一性。以后增加非 ASCII 或超出当前索引边界的方案时，必须新增目录和迁移设计，不能静默套用现有列语义。

### 2.3 Legal jurisdiction 独立建模

`legalJurisdiction` 是规范 profile 中可选的受治理事实，表达主体依法成立或登记所归属的法律辖区。它只能来自已知 ISO country/subdivision 或经复审的 registry 枚举映射，未知值保持缺失，不能保留 provider 自由文本。

它与身份作用域保持正交：

- 境外主体的 UK establishment 可以拥有 Companies House identity scope，而 legal jurisdiction 在英国之外；
- LEI 使用全球 identity scope，同时携带独立的国家或 subdivision legal jurisdiction；
- 注册地址、营业地址、查询地区和数据存储地域都不等于 legal jurisdiction；
- legal jurisdiction 变化不产生新身份、不释放唯一占用，也不自动改变 Customer hierarchy。

名称搜索可把受治理辖区作为有界 query selector，但 selector 只是 adapter 能力输入，不能被回写为权威 legal jurisdiction，更不能进入身份唯一键。

## 3. 主体状态与标识 standing

### 3.1 主体状态

规范主体状态只保留：

| 状态 | 含义 |
| --- | --- |
| `ACTIVE` | 来源明确把主体本身声明为 active |
| `INACTIVE` | 来源明确把主体本身声明为 dissolved、closed、removed 或等价终止状态 |
| `UNKNOWN` | 缺失、未知、新增、程序性、冲突或不足以证明 active/inactive 的状态 |

主体状态不是 Customer `status`、公海状态、交易状态、风险等级、认证结论或写权限。`INACTIVE` Customer 资料仍可被人工择取或保留；B23 不因外部状态删除、锁定、移池或禁止 Customer。未知枚举必须映射为 `UNKNOWN`，不能默认 active。

### 3.2 Identifier standing

标识 standing 描述登记记录或 identifier 自身的生命周期，与主体状态正交：

| standing | 规范含义 | 首期新关联规则 |
| --- | --- | --- |
| `CURRENT` | 来源明确表示该标识当前签发/有效 | 可关联 |
| `STALE` | 标识仍指向主体，但维护或续期已逾期 | 可关联；必须保留并展示 stale 语义 |
| `TRANSITIONING` | 标识正在受治理转移或归档过程中 | 可关联当前完整值；不能因 LOU/provider 变化创建新身份 |
| `SUPERSEDED` | 来源明确表示该记录重复或已由另一标识接替 | 不允许作为新关联；successor 只作重新查询提示 |
| `INVALID` | 来源明确撤销、废止或判定该标识无效 | 不允许作为新关联 |
| `RETIRED` | 标识记录不再当前，但可用于历史识别 | 首期不允许新关联 |
| `UNKNOWN` | 缺失、未知、新增或无法安全映射 | 不能仅凭 standing 允许关联 |

不同 scheme 对 standing 的提供能力不同。LEI 目录要求可靠的 registration standing，因此 LEI 的 `UNKNOWN` 必须拒绝新关联；Companies House number 和经验证的 CN USCC 当前没有独立 identifier-standing 字段，只要 exact lookup、身份一致性、组织资格和来源使用政策都通过，目录可以声明“来源不发布独立 standing”，以 `UNKNOWN` 返回并仍允许该 scheme 关联。这个例外由固定 scheme/source contract 决定，不能由 adapter 对单条记录临时放宽。

standing 不进入身份键。刷新后从 `CURRENT` 变为 `STALE`、`SUPERSEDED`、`INVALID` 或 `RETIRED` 时：

- 保留现有 Customer 身份占用和先前 provenance；
- 不自动把 successor 加到 Customer，不自动删除旧值，也不把占用移动到另一个 Customer；
- 允许用户重新取得 successor 的 exact observation，再显式把该新身份追加到同一 Customer；
- successor 若已被另一个 Customer 占用，仍由同一个 MySQL 唯一约束冲突；
- standing 变化可以产生新的预览/审计事实，但不能伪装成 Customer 乐观锁之外的隐式写授权。

## 4. Customer 关联基数与生命周期

### 4.1 当前关系

当前关系是 `Customer 1 -> 0..N registered identities`，反向是 `registered identity 0..1 -> Customer`，唯一作用域为 tenant。

不增加 `(tenant, customer, identityScope, registryScheme)` 的“每方案最多一个”约束。现实登记中可能同时存在迁移前后的本地号码、重复/接替的 LEI 或同一命名空间中的多个受治理登记记录；只要每个完整身份键不同且选择有效，一个 Customer 可以保留多个身份。UI 可以标记主查询身份，但“primary”不是唯一键，也不能删除其他身份。

同一次观测携带 LEI 与一个已验证本地登记号时，调用者可以显式选择一个或两个；每个身份独立经过 scheme 校验和唯一仲裁。名称、地址相等或 provider 搜索结果相邻不能证明两个身份属于同一主体。来自不同观测的身份也只有在用户明确选择目标 Customer、服务端重新 exact 取数并通过全部权限/政策后才能追加，不能后台自动合并。

### 4.2 Customer 重复、分支和层级

B06 的名称/电话重复提示是调用者可见范围内的辅助判断，B05 unique field reservation 也是 tenant/field/value 规则；两者都不能替代 B23 身份占用。

同一法人可能在 CRM 中有多个站点、部门或业务账户，但共享同一个登记号不使该登记号可以复制关联到多个 Customer。首期规则是：

- 有独立受治理登记身份的分支或 establishment 可以关联自己的身份；
- 没有独立登记身份的站点/部门不能复制母体身份来绕开唯一约束；
- 可以使用 B06 parent Customer、owner、协作者或其他 CRM 事实表达业务组织，但 Customer hierarchy 不自动声明法定母子公司、分支或控制关系；
- 如果 tenant 选择保留多个代表同一现实主体的 Customer，只能有一个持有该精确登记身份，其他记录保持未关联，直到未来显式合并/修复流程处理。

该策略不会用外部数据强制合并 B06 Customer，也不会把隐藏 Customer 暴露为“重复企业”。它只防止 B23 自己为同一精确身份创建多个当前权威锚点。

### 4.3 删除、历史与纠错

`ACTIVE` 和 `IN_POOL` 都保有身份占用。Customer 进入 `DELETED` 时，当前占用必须作为 Customer 删除命令的一部分在同一数据库事务释放；不能在 after-commit、缓存任务或异步事件中释放。删除事务回滚时占用仍在，提交后另一个 Customer 才能竞争该身份。

释放当前占用不删除已提交的最小 provenance。历史记录可以证明某身份曾被某个已删除 Customer 采用，但它不参与当前 Customer 查询、授权或唯一仲裁，也不能成为恢复 Customer 数据的旁路。这样同一身份在不同时期可以出现在多个历史 provenance 中，但任一时刻最多有一个未删除 Customer 占用。

首期不因下列外部变化释放身份：

- 主体更名、地址变化、legal jurisdiction 修正或组织类型变化；
- 主体 `INACTIVE`，或标识 `STALE`、`TRANSITIONING`、`SUPERSEDED`、`INVALID`、`RETIRED`；
- exact lookup 暂时 `NO_RESULT`、来源超时、额度耗尽、合同停用或 adapter 不可用；
- provider 改名、换账号、换 connector、换 source locator 或 source revision 变化。

对错误选择的显式解绑、两个未删除 Customer 的合并和身份转移需要独立的高权限、双 Customer 锁、expected-version 与不可变审计设计；不属于 B23 首期择取/刷新。实现不能通过直接更新关联行的 `customer_id`、删除 provenance 或先释放再异步占用来偷渡该能力。

## 5. 专用持久化边界

登记身份使用 Customer 专用 typed 关联，不使用通用自定义值表，原因是：

- 一个 Customer 必须支持多个 scheme 和多个身份；普通单值 unique 字段不能表达跨 scheme 的命名空间；
- 规范化必须由 scheme-specific 目录执行，不是 B05 通用字符串 normalizer；
- 关联唯一性对所有 B23 写入固定，不能由 tenant 管理员关闭、改字段类型或改 `is_unique`；
- source、standing、观测和采用字段属于 provenance，不应拼成一个展示字段或 JSON；
- 同一事务必须同时保存 Customer、当前身份占用和 provenance，并由约束级错误映射区分身份冲突。

逻辑持久化至少具有以下约束形状；本文件不预占最终表名、PO 名或 Flyway 版本：

```text
current_customer_registered_identity
  tenant_id
  customer_id
  identity_scope_code
  registry_scheme_code
  canonical_value
  created_time

UNIQUE (tenant_id, identity_scope_code, registry_scheme_code, canonical_value)
FOREIGN KEY (tenant_id, customer_id) -> customer (tenant_id, id)
INDEX (tenant_id, customer_id, identity_scope_code, registry_scheme_code, canonical_value)
```

唯一键本身就是当前占用的数据库 authority。`customer_id` 不在唯一身份键中，否则会退化成同一身份可以关联多个 Customer。`legalJurisdiction`、entity status、identifier standing、source kind、provider、observed time 和 provenance ID 不在唯一键中，否则同一身份可通过改变观测属性重复占用。

当前关系只保存关联正确性需要的有界规范值。standing 与主体状态是一次外部观测的事实，不作为可被最后写覆盖的关联列；其 durable snapshot、来源、操作者和字段选择由 B23-XP 的 provenance 设计决定。原始 profile、provider ID、响应、URL、错误、人员字段和未采用字段始终不进入这张关系。

## 6. MySQL 唯一仲裁协议

### 6.1 命令顺序

B23 创建/更新 Customer 时，外部查询仍在 `validate/prepare` 阶段完成，不能持有 MySQL 锁等待 provider。进入 `CommandServiceTemplate` 的本地事务后固定执行：

1. 重新锁定并校验 `(tenant, CUSTOMER)` customization guard、目标 Customer 根和 B06/B05 当前规则；新建时先按 B06 创建 Customer 根以取得本地 ID；
2. 对服务端新鲜观测中被显式选择的身份去重，并按 `(identityScope, registryScheme, canonicalValue)` 的 binary 顺序排序；
3. 以普通 `INSERT` 逐项建立当前身份占用；不使用 `INSERT IGNORE`、`REPLACE`、`ON DUPLICATE KEY UPDATE customer_id` 或应用锁模拟唯一性；
4. 保存选中 Customer 字段和最小 provenance；任一身份、字段唯一值、版本、外键或审计失败都回滚整个事务；
5. 提交后才执行缓存失效和普通安全事件。

多身份按规范键排序，保证两个事务以相同顺序竞争唯一索引，减少 A/B 与 B/A 交叉插入死锁。发生 MySQL deadlock 时整次本地事务回滚并返回可安全重试的本地冲突；持久化适配器不能在事务内部继续写或自行重新调用 provider。任何重试都重新进入用例校验，并按 observation expiry/policy 决定复用仍有效的服务端观测或重新 exact 取数，不能放宽选择集合。

### 6.2 Duplicate-key 结果

应用层可以在预览时做 tenant-scoped 可见提示，但它只是 UX，不能代替 `INSERT`。唯一键冲突后使用 exact tenant/key 的 locking current read 判定占用者：

- 已由同一 Customer 占用：关系层可按幂等成功处理，不删除重建关联；provenance 是否已有同一操作由独立本地操作键仲裁；
- 已由另一个 Customer 占用：整次命令返回不区分具体原因的通用 `CONFLICT` 并回滚；
- 找不到可解释行或冲突来自其他约束：作为基础设施/契约错误失败关闭，不能统一吞成“已存在”继续提交。

在 MySQL 默认 `REPEATABLE READ` 下，冲突后的归属确认必须是锁定 current read，不能依赖可能早于并发提交建立的普通一致性快照。持久化适配器应按约束来源映射身份冲突，不能让 Customer repository 当前宽泛的 `DuplicateKeyException -> CustomerAlreadyExists` 吞掉身份、字段、外键或 schema 漂移差异。

### 6.3 删除与并发释放

Customer 删除与身份释放持有同一 Customer 根锁并在一个事务执行。新关联在确认目标 Customer 未删除后插入唯一占用。若“删除旧 Customer”与“新 Customer 占用相同身份”并发：

- 新事务在唯一索引上等待旧占用的删除提交或回滚；
- 删除提交后新事务可插入，删除回滚则新事务稳定冲突；
- provenance 的历史保留不重新占用唯一键；
- 禁止先提交 Customer 删除、再通过 after-commit 清理占用形成无界假冲突窗口。

### 6.4 可见性与错误保密

按登记身份查找已关联 Customer 的任何提示都必须显式 tenant scoped，并在 SQL limit/page 之前应用 B06 当前 Customer 可见性。不存在、已删除、隐藏或无权读取的 Customer 对普通调用者保持不可区分。

数据库冲突只收敛为与版本或观测漂移同级的通用 B23 `CONFLICT`，不公开“身份已被占用”这一具体原因，也不包含占用 Customer ID、名称、owner、状态、scope 原值、完整登记值或来源。只有调用者通过普通 B06 可见性读到目标 Customer 后，独立查询才可返回安全关联提示。日志、trace、metric 和普通事件也不得记录完整身份键。

## 7. 并发与生命周期 oracle

B23-XF 已覆盖其中可由 B23a 查询链路到达的 scheme canonicalization、`ACTIVE + STALE`、未知状态、source/policy 和双 tenant 分区；实现级 Customer/MySQL Testcontainers fixture 仍须覆盖以下完整采用矩阵：

| 场景 | 必须结果 |
| --- | --- |
| 同 tenant、同身份、两个不同既有 Customer 并发关联 | 恰好一个提交；另一个稳定冲突且字段/身份/provenance 全回滚 |
| 同 tenant、同身份、两个新建 Customer 并发创建并择取 | 恰好创建一个完整 Customer；失败事务不留下 Customer 根或子表 |
| 同 tenant、同 Customer、同身份重复命令 | 当前关系恰好一行；操作幂等/provenance 由独立键确定，不重复占用 |
| 两个事务以相反输入顺序采用身份 A/B | 核心排序为相同 binary 顺序；无部分提交，死锁使整个本地尝试回滚，后续重试重新遵守 observation policy |
| 不同 tenant 采用相同规范身份 | 两边均可提交，且查询、锁、错误与缓存互不可见 |
| 同 tenant、相同值但不同 identity scope 或 scheme | 作为不同身份；只有受治理目录允许的组合可提交 |
| legal jurisdiction、名称、地址、entity status 或 standing 变化 | 身份键不变，不产生第二占用，不自动更新 Customer |
| LEI `ACTIVE + LAPSED` | 主体 `ACTIVE`、standing `STALE`，可显式关联但不得伪装为 fully current |
| LEI `DUPLICATE/ANNULLED/RETIRED/UNKNOWN` 新关联 | 在 MySQL 写入前失败关闭；successor 不自动迁移 |
| Companies House/CN 来源不发布独立 standing | 仅在固定 scheme/source contract、exact identity 和政策均通过时允许 `UNKNOWN` 关联 |
| 已关联 Customer 从 `ACTIVE` 进入 `IN_POOL` | 占用保留，另一个 Customer 仍冲突 |
| 已关联 Customer 软删除与新占用并发 | 删除提交后新占用才成功；删除回滚时新占用冲突；历史 provenance 保留 |
| 已关联身份刷新为 inactive、stale、invalid 或无结果 | 当前占用保留；不删 Customer、不释放、不换绑 |
| 冲突占用者对 caller 隐藏 | 不返回关联提示；提交只得到与版本/观测漂移相同的通用 `CONFLICT`，响应、日志和计数不泄露占用 Customer |
| provider/connector/source 不同但规范身份相同 | 命中同一 tenant 唯一键，不能按来源重复关联 |

fixture 还必须校验 ASCII binary equality、Companies House 前导零、LEI/USCC 大小写与校验位、未知 scheme、响应 identity mismatch、超界值和非法 Unicode 在写事务前失败。不能仅用 H2、mock repository 或 JVM set 证明 MySQL 唯一、锁等待、rollback 和 tenant 复合外键语义。

## 8. 已闭合与后续依赖

B23-XI 已闭合：

- identity scope、scheme、规范值和 legal jurisdiction 的边界；
- `ACTIVE/INACTIVE/UNKNOWN` 主体状态与七类 identifier standing；
- Customer 一对多身份、tenant 内身份对一个未删除 Customer 的当前唯一关系；
- Customer 删除释放当前占用、外部生命周期变化不自动释放、历史 provenance 保留；
- 专用 typed 关联与 exact MySQL 唯一键；
- 同/异 Customer、同/异 tenant、多身份、删除和隐藏冲突的并发 oracle。

关联进展与后续依赖：

- B23-XP 已冻结日期 kind、source kind、B05 业务字段落点、最小 durable provenance，以及许可、attribution、TTL 和保留矩阵；
- [B23-XF](B23-XF-composed-fixture.md) 已把 B23-C 与 B23-O 的合成 source shape 组合到 B23a，执行 standing/source/usage mode/额度/错误/tenant 隔离；由于 B23a 不写 Customer，它不替代本节的 MySQL 采用矩阵；
- 实现拆批：最终 API、Java 类型、目录存储方式、权限 code、错误码、表/PO/mapper、Flyway 版本和 Customer 删除协调方式；
- 真实 adapter readiness：[B23-O/2](B23-O-official-source-readiness.md) 已冻结 Companies House number、GLEIF RA/非自然人资格、来源权利、quota、TTL 与 conformance 规则；B23-CH 已实现 domain scheme，对应 RA mapper、真实 build/live evidence，以及商业状态/合同 fixture 仍待实现。

B23-XI 仍是规划契约，不表示已添加任何登记数据、生产 adapter、数据库表或 Customer 写入能力。
