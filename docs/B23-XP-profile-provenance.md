# B23-XP 规范 profile、provenance 与数据使用矩阵

> 决策日期：2026-07-19
>
> 状态：实现前规范已闭合；本文件冻结首期 provider-neutral profile、日期/source kind、B05 落点、最小 durable provenance、许可/attribution/TTL/保留矩阵和失败关闭 fixture，不实现 API、Java 类型、数据源 adapter、配置、表、迁移或真实数据调用
>
> 输入：[B23 总规划](../batch-B23.md)、[B23-C 商业企业数据 spike](spikes/B23-commercial-enterprise-data.md)、[B23-O 官方/开放登记 spike](spikes/B23-official-open-registry.md)、现有 B05 字段治理和 B06 Customer 实现

## 1. 决策摘要

B23-XP 把外部来源的一次主体观测缩减为一个固定、无扩展 map 的首期 profile，并把“可以显示”“可以择取”“可以保存多久”从数据可访问性中分离：

- 首期可采用事实只有登记名称、可选法律辖区、粗粒度主体状态、带明确 kind 的一个成立相关日期和一个注册地址；规范登记身份及其 standing 由 B23-XI 定义并作为 profile 前置，不在 B05 中复制；
- `organizationType`/legal form 暂不进入首期规范 profile。Companies House `company_type`、GLEIF entity legal form/category 与商业来源 `EconKind` 不共享一个经过验证的值域，不能用自由文本或猜测性的粗分类伪造交集；
- 日期必须携带 `INCORPORATED`、`REGISTERED`、`FORMED`、`ESTABLISHED` 或 `OPENED` 之一。来源无法证明 kind 时整个日期缺失，不能只保存一个含义不明的“成立日期”；
- source kind 固定区分 `OFFICIAL_REGISTRY`、`OPEN_REFERENCE_INDEX`、`COMMERCIAL_AGGREGATOR` 与仅限 dev/test 的 `SYNTHETIC_FIXTURE`。kind 只说明来源形态，不证明准确性、许可、权威性或 CRM 权限；
- B05 落点采用显式、tenant-scoped、版本化 mapping。登记名称只落 `customer_name`，主体状态绝不写 B06 `status`，注册地址只落内置 `addresses` 集合，身份、standing、来源和时效只进入专用关联/provenance；
- 一次成功择取原子追加最小 provenance header 和有界 field-selection lines。它记录本地关联、来源 binding、策略/attribution/mapping 版本、观测/采用时间、操作者、Customer 前后版本和目标字段，不复制外部值、raw profile、人员、locator、URL 或响应 digest；
- capability、部署政策、attribution 渲染能力与核心硬边界取最严交集。缺少任一必需政策材料时为 `NOT_READY`；只允许临时展示时为 `EPHEMERAL_ONLY`；只有展示、逐字段持久化、最小 provenance、attribution 和保留都可执行时才允许 `ADOPT_SELECTED_FACTS`；
- 每个可返回 profile 的 observation 都有正的 `expiresAt`，且不晚于 `observedAt + 5 分钟`。搜索候选和未择取 profile 默认不缓存、不进 MySQL；Customer 中已人工采用的值不因 observation 过期自动回滚或删除。

B23-XP 冻结的是产品和开源 contract 的最严边界，不替部署方给任何真实来源作法律判断。特别是商业来源仍默认 `NOT_READY`；公开 API 页面和公开用户协议不足以填入 attribution、TTL、持久化、导出或终止处置。

## 2. 与 B23-XI 的所有权边界

B23-XP 不重复决定登记身份的现实世界唯一性。它消费 B23-XI 后续冻结的以下语义：

- 每个详细 profile 至少有一个通过 scheme/authority 规则验证的规范登记身份；名称搜索中无法形成该身份的候选不是可择取 profile；
- `legalJurisdiction` 与 identity scope 分开；主体 `entityStatus` 与每个 identifier 的 standing 分开；
- tenant 内 Customer 与登记身份的一对一/一对多、历史身份和 MySQL 仲裁由 B23-XI 决定；
- 成功择取时，Customer、身份关联和 provenance 在同一个本地事务中提交，provenance 引用稳定的本地身份关联，不再复制一份可漂移的登记值。

若 B23-XI 尚未就某 scheme、standing 或关联动作给出可执行结论，该 profile 最多用于临时展示，不能因为 B23-XP 已闭合字段和政策设计就持久化。

## 3. 首期规范 profile

### 3.1 Profile 与搜索候选分离

搜索候选不是可直接保存的 profile。候选可以只有受控显示名和短期选择引用；只有 scheme-specific exact lookup 重新取得、通过主体资格与 identity 一致性校验的详情才能形成下表的 profile。客户端返回的候选名称、地址、状态、日期、来源或 locator 都不具有权威性。

首期 profile 固定为：

| 规范事实 | 必需性 | 语义与约束 |
| --- | --- | --- |
| `identifiers` | 必需，至少一项 | B23-XI 管理的 typed authority/scheme/value/standing；不得由名称、地址、URL 或 provider ID 猜测 |
| `registeredName` | 必需 | 登记/legal name；裁剪后非空且满足 B06 `customer_name` 上限，不用 trading/previous/transliterated name 回填 |
| `legalJurisdiction` | 可选 | B23-XI 定义的受治理辖区 code；不是 identity namespace，不从地址或电话推断 |
| `entityStatus` | 必需 | B23-XI 的 `ACTIVE`、`INACTIVE`、`UNKNOWN`；未知来源值稳定变为 `UNKNOWN`，不由风险或程序详情推断 |
| `formationDate` | 可选 | 一个 ISO local date 加一个第 3.2 节 date kind；二者全有或全无 |
| `registeredAddress` | 可选 | 单一登记/legal/registered-office 地址；结构见第 3.3 节，不宣称营业、邮寄、总部或唯一主地址 |
| observation metadata | 必需 | 第 4 节受控来源、`observedAt`、`expiresAt`、使用模式与 attribution；source update/revision 可选 |
| `sourceDeclaredPartial` | 必需布尔值 | 只表达来源明确声明 partial；`false` 不承诺内容完整、正确或覆盖全部登记事实 |

缺失的可选事实保持缺失。profile 不使用空字符串、`0`、默认日期、另一地址、辖区推断或 `UNKNOWN` 文本填洞。任何必需字段缺失、超界、identity 不一致或结构冲突都会使整次详情观测不可用，而不是产生可部分择取的畸形对象。

### 3.2 日期 kind

日期 kind 的语义值冻结如下；最终 Java 名称可以调整，但不得合并语义：

| Date kind | 只允许表达 | 代表来源映射 |
| --- | --- | --- |
| `INCORPORATED` | 法人/公司依法完成 incorporation 的日期 | Companies House 明确把该 company type 的 `date_of_creation` 定义为 incorporated |
| `REGISTERED` | 主体或登记记录完成 registration 的日期 | Companies House 明确为 registered 的类型 |
| `FORMED` | 组织依法 formed 的日期 | Companies House 明确为 formed 的类型 |
| `ESTABLISHED` | 来源明确称主体建立/成立的日期 | GLEIF `EntityCreationDate`；商业 `StartDate` 仅在合同/fixture 确认“成立日期”语义后 |
| `OPENED` | establishment/branch/open entity 被打开或设立的日期 | Companies House 明确为 opened 的类型 |

以下值不能进入 `formationDate`：source update、核准、filing、cessation、dissolution、renewal、next renewal、provider receive、HTTP `Date` 或 Customer 创建时间。adapter 必须根据受治理的主体类型映射 date kind；同一原字段可能随主体类型改变含义时，无法证明映射就省略整个日期。不存在 `OTHER`、自由文本 kind 或“先存日期以后再解释”的分支。

### 3.3 注册地址

规范注册地址只包含有界的组织地址分量：一个必需的 `addressLine`，以及可选的受治理 country code、region、city/locality 和 postal code。它不包含人员姓名、care-of 联系人、电话、邮箱、经纬度、provider 格式模板、原始地址对象或任意扩展 map。

- Companies House 只使用 registered office；GLEIF 只使用 legal address；商业模型只使用经审定为注册地址的 `Address`；
- 不用 headquarters、service、mailing、business、other 或人员地址回填；
- 不从 `legalJurisdiction` 自动生成国家，也不把邮编/地区解析结果提升为来源事实；
- 来源只有一条格式化地址时可以只填 `addressLine`，其余分量保持缺失；
- 任何超出 B06 地址分量上限的值使该地址缺失或整次观测失败关闭，不能截断后造成另一地址。

### 3.4 明确排除的 profile 字段

首期 profile 不包含：organization/legal-form 类型、raw status、历史名称、trading name、经营范围、注销原因、人员/代表/股东/受益人、联系方式、网站、Logo、资本、风险、诉讼、处罚、关系、filing、文档、报告、BIC/ISIN、provider score/rank/total、provider company ID、URL、订单号、错误消息或任意扩展 map。

`organizationType` 从原 B23 候选中移除不是信息丢失缺陷，而是跨 Companies House、GLEIF 与商业模型复审后的失败关闭结论。将来只有在 scheme-qualified legal form 或经过独立治理的跨源分类完成隐私、许可、映射和 B05 落点复审后才能新增。

## 4. 来源、观测时间与 attribution

### 4.1 Source kind 与受控来源目录

| Source kind | 语义 | 首期代表 | 限制 |
| --- | --- | --- | --- |
| `OFFICIAL_REGISTRY` | 法定登记机关直接运营的登记读取来源 | Companies House | 不等于内容已认证、无错误或自动允许任意再利用 |
| `OPEN_REFERENCE_INDEX` | 汇集/标准化多个登记机关引用的开放索引 | GLEIF / Global LEI Index | 不把索引标识替代本地登记身份，不暗示来源背书 |
| `COMMERCIAL_AGGREGATOR` | 受账号/合同约束的商业聚合数据服务 | B23-C 代表模型 | 公开可购买不证明展示、持久化、缓存或导出权利 |
| `SYNTHETIC_FIXTURE` | 无真实主体/人员/供应商数据的确定性测试来源 | 开源 dev/test adapter | 生产 profile 拒绝；不能作为真实 readiness 或许可证据 |

未知 kind 失败关闭。source kind 不足以形成 provenance：每个 tenant 的受治理 source binding 还要关联一个内部稳定目录项、adapter contract/build、部署政策版本和 attribution 版本。普通请求不能选择 binding，普通输出也不暴露账号、套餐、endpoint、credential 或内部路由。

来源展示由版本化受控目录生成，而不是复制 provider response。目录最多提供有界纯文本来源名、可选经 allowlist 的 HTTPS 信息链接、可选固定免责声明以及必须展示的 context。任意 HTML、脚本、Logo blob、tracking query、provider 返回 URL 或响应内 attribution 文本都不进入核心契约。

### 4.2 观测时间

- `observedAt`：adapter 成功完成白名单化和结构校验时使用核心提供的 UTC clock 取得，必需；
- `expiresAt`：部署策略产生，必需，满足 `observedAt < expiresAt <= observedAt + 5 分钟`；来源/合同要求更短时取更短值，缺少正 TTL 时 source `NOT_READY`；
- `sourceUpdatedAt`：可选来源业务更新时间，不能替代 `observedAt`、`expiresAt` 或 source revision；
- `sourceRevision`：可选 opaque revision。Companies House ETag 可作为一次表示修订，GLEIF/QCC 当前没有可证明的同类值；它不得被解释为更新时间或真实性；
- renewal、filing、核准、成立、HTTP `Date`、adapter build 时间和本地缓存写入时间都不能生成上述另一个时间。

TTL 只限制 profile/reference 是否还能显示、比较或被选择。择取在外部重取后使用新观测，并在本地事务开始前验证其仍未过期；一次已提交 Customer 值不会在旧 `expiresAt` 到达时自动删除、恢复旧值或改变 B06 状态。

### 4.3 Attribution context

受控 attribution policy 可以要求以下 context 的任意子集：`SEARCH_RESULT`、`DETAIL`、`PREVIEW`、`ADOPTION_CONFIRMATION`、`CUSTOMER_READ` 和 `EXPORT`。所有需要显示外部事实的响应都必须带服务端解析后的受控 attribution，而不是让客户端凭 source kind 猜文案。

- 若 `SEARCH_RESULT`、`DETAIL` 或 `PREVIEW` 的必需 attribution 不能由当前 transport/UI 完整呈现，整个显示操作 `NOT_READY`；
- 若来源要求 `CUSTOMER_READ` 持续展示 attribution，而普通 B06 输出尚不能按已择取字段呈现，最多允许 `EPHEMERAL_ONLY`，不得采用后再丢失署名；
- 若来源要求随 Customer 导出 attribution，B23 首期因为不提供来源数据集/带 provenance 导出而不声明 export ready；
- 来源未强制 attribution 时，B23 产品仍在搜索、详情、预览和确认页显示受控来源名，作为透明度而非法律许可推断；
- `sourceKind`、浏览器页脚、“来源：互联网”或 MIT license 都不能替代具体 source policy 的 attribution。

## 5. B05 字段 mapping

### 5.1 映射原则

mapping 是 tenant-scoped、版本化、显式启停的配置，只能引用当前 CUSTOMER 字段目录中的一个精确 `fieldCode`。每个 canonical fact 首期最多一个目标，不能 fan-out 到多个字段，也不能以脚本、表达式、JSON path、provider field name 或自由选项映射动态改写值。

mapping 的创建、替换、停用、B05 引用字段生命周期变更和 B23 择取都共享 `(tenant, CUSTOMER)` customization guard。活动 mapping 是字段依赖：必须先停用 mapping 才能停用其目标字段或造成类型不兼容。预览后 mapping 版本、字段 definition、选项或角色字段策略变化，择取返回 conflict，不使用旧快照。

预览既有 Customer 值要求目标字段当前 `READ`、无 mask 且 `WRITE`；否则该映射完全不进入 diff，不能只隐藏 current value 却仍暴露“存在差异”。创建场景没有 current value，但仍要求目标字段当前 `WRITE`。本地事务内重新解析同一权限；外部数据查询能力不会授予字段访问。

### 5.2 固定落点矩阵

| Canonical fact | 允许的 B05/B06 落点 | 映射约束 | 明确禁止 |
| --- | --- | --- | --- |
| registered identity | B23-XI 专用 Customer identity 关联 | 与 Customer/provenance 原子保存；由 XI 做 tenant/MySQL 仲裁 | custom `TEXT`、`description`、`tags`、Customer ID |
| `registeredName` | 内置 `customer_name` (`TEXT`) | 固定落点；创建时可满足 B06 必填名称，更新时逐字段显式选择 | 任意自定义字段、历史/别名拼接 |
| `legalJurisdiction` | 可选活动自定义 `TEXT`，或选项值精确等于 canonical code 的 `SELECT` | 无默认 mapping；普通 B06 写仍使用相同类型策略 | 地址国家、owner、tag、自由显示名 |
| `entityStatus` | 可选活动自定义 `SELECT` | 目标必须保留精确 `ACTIVE`、`INACTIVE`、`UNKNOWN` 选项值；无默认 mapping | 内置 `status`、`deal_status`、`locked`、文本猜测 |
| `formationDate` | 可选活动自定义 `DATE` | mapping 固定一个非空 allowed-date-kind 集；profile kind 不在集合中时只显示、不生成 diff | `created_time`、`updated_time`、无 kind 的 DATE/DATETIME |
| `registeredAddress` | 内置 `addresses` (`ADDRESS` 集合) | 使用第 5.3 节 component-aware merge；无默认主地址提升 | 字符串型 custom `ADDRESS`、`description`、全量替换地址集合 |
| identifier standing | B23-XI identity 关联 | 与对应 identifier 同生命周期 | Customer lifecycle/status 或普通自定义字段 |
| source kind/name、observed/source-updated/expires time、revision、attribution | B23 provenance/source policy | 不属于 Customer 业务字段 | `description`、`tags`、自定义 JSON/文本 |

现有 B05 custom `ADDRESS` 在 Customer 值策略中是一个字符串值，不是 `CustomerAddress` 的结构化集合。为了避免把地址语义、分量、主地址和后续刷新关系压扁，B23 首期明确不把注册地址写入 custom `ADDRESS`。

### 5.3 地址槽位合并

注册地址采用 B06 地址集合中的一个本地槽位，而不是替换全部 `addresses`：

1. 第一次采用时追加一个服务端生成 key 的非 primary 地址；来源不提供 label、primary 或 coordinates；
2. provenance selection line 记录该本地 address key 作为 target locator，但不记录地址值；
3. 后续刷新只能更新仍存在且由前次 B23 关联的同一槽位，保留 key 和本地 primary/label 决策，替换被明确选择的来源地址分量；
4. 地址分量变化时，旧 coordinates 不能被当作新地址的坐标继续保留；清空坐标必须作为整行 diff 的可见结果并由成员显式选择；
5. 槽位已被人工删除或失去可见/可写资格时，刷新不猜测另一个相似地址；只能明确追加新槽位或跳过；
6. 任何操作都保留其他地址，仍满足最多 20 条、最多一个 primary、分量长度和坐标成对等 B06 不变量。

### 5.4 本地值不变成 source-owned

成功采用后的值仍是 B06 Customer 业务值。成员可按普通 B06 权限修改或清空它，外部来源没有写锁、自动覆盖权或更高优先级。provenance 只说明某次值由人工从一次观测中采用，不宣称当前 Customer 字段仍与来源一致。刷新必须重新查询、重新预览并逐字段确认。

## 6. 最小 durable provenance

### 6.1 原子写入模型

每次成功 `ADOPT_SELECTED_FACTS` 在 Customer 本地命令事务中追加一个 adoption header 和有界 selection lines。除保留 deadline 只能按后续收紧政策提前外，adoption facts 不可改写。header、lines、B23-XI identity 关联变化与同一个 Customer aggregate save 全有或全无；provenance 失败会回滚 Customer。预览、搜索、无结果、拒绝、冲突和外部失败不创建 provenance。

Header 最小语义固定为：

| 类别 | 必需字段 |
| --- | --- |
| 本地归属 | tenant、Customer、本地 adoption operation identity |
| 操作者 | 当前 verified member/actor 的稳定本地引用；不接受请求覆盖 |
| 来源 | tenant-scoped source binding 目录引用、source kind、adapter contract/build evidence reference |
| 策略 | source policy version、attribution policy version、B05 mapping version |
| 主体 | B23-XI 创建或确认的稳定本地 identity-association reference |
| 时效 | 本次重新取得事实的 `observedAt`、`expiresAt`，可选 `sourceUpdatedAt` |
| 本地提交 | 数据库 UTC `adoptedAt`、Customer expected/before version 与 committed/after version |
| 使用边界 | 提交时有效的 `ADOPT_SELECTED_FACTS` 与只可提前、不可延后的 `deleteAfter` |

`sourceRevision` 只有在 source policy 同时证明审计必要性、持久化权利和保留期限时才可加入 header；缺省不保存。它必须有严格长度上限且不向普通 Customer 输出。provider locator、search token、URL、订单号、account、套餐、余额、credential 和 error 不属于 provenance。

每条 selection line 只记录：canonical fact kind、可选 date kind、目标 `fieldCode` 或 address-slot locator，以及固定的 applied/no-op 分类。它不保存 before/after 值、profile JSON、地址、名称、状态原文、隐藏 Customer 值、provider field path 或普通响应。

一个 adoption 的 line 数由首期事实 allowlist 严格限制；重复 canonical fact、重复目标、未选择事实或 mapping 外字段使整个命令失败。仅确认 identity 关联而没有任何可写字段变化时是否允许由 B23-SPLIT 的用例设计决定，但同样必须有明确人工动作和 provenance，不能由搜索自动建立。

### 6.2 为什么默认不保存值或 digest

Customer 中保存选中业务值，provenance 保存“谁、何时、从哪个受治理来源、按哪个政策把哪些事实写到哪里”。再复制 adopted value 会形成第二份绕过 B05 的历史 profile；普通 hash 对名称、状态、日期和地址等低熵值还可能被离线枚举，不构成匿名化。

因此首期核心不保存值快照、明文/密文副本或 value digest。若某真实合同要求保留原始证据、签名 response 或可验证 digest，该 adapter 对首期开源 provenance 为 `NOT_READY`；不能私自在通用 audit、Activity、event 或日志中补存。

### 6.3 读取边界

provenance 不是独立企业资料查询入口：

- 只能在 caller 当前可读 Customer 的上下文中读取，先应用 B06 record visibility；
- selection target 还应用当前 B05 field visibility。隐藏字段不返回 target code 或“曾采用”提示，避免形成字段存在 oracle；
- 普通输出最多返回受控来源名/kind、采用时间和当前可见目标的来源提示，不返回 source binding、policy/build/revision 内部标识；
- tenant 管理审计也不返回 raw profile、未采用字段、locator、provider error 或隐藏 Customer 值；
- provenance 不进入 B17/B18、B15、Activity、全文搜索、保存视图、普通事件或跨 tenant 缓存。

## 7. 许可与使用模式

### 7.1 最严交集

有效使用模式由四层取最严交集：核心硬 allowlist、adapter 技术 capability、tenant/source deployment policy、当前 transport 的 attribution/保留执行能力。不能把 provider 可访问、数据公开、CC0、CRM Customer 可写或 tenant 管理员身份中的任一个单独解释为采用许可。

| Effective mode | 可做 | 不可做 |
| --- | --- | --- |
| `NOT_READY` | 只返回稳定安全不可用/不支持；可做无数据的 readiness 运维检查 | 外部查询、候选/profile 展示、择取、缓存、持久化 |
| `EPHEMERAL_ONLY` | 人工有界查询和在 attribution/TTL 内临时展示 | 创建/更新 Customer、identity 关联、durable provenance、搜索历史、未择取缓存、导出 |
| `ADOPT_SELECTED_FACTS` | 在新鲜重取、B05/B06 权限和事务规则下逐字段采用，并保存最小 provenance | raw profile、未选择字段、批量复制、数据集导出、自动刷新/覆盖 |

source policy 必须逐项固定 tenant、用户群、用途、辖区/主体、显示 context、采用、Customer 持久化、provenance、缓存、attribution、地域/访问、纠错/删除、终止处置、调用预算和禁止用途。任一当前操作所需项缺失都失败关闭；不从较宽操作推导较窄或相邻操作。

### 7.2 代表来源许可/attribution 矩阵

| 来源模型 | 默认/可达模式 | 搜索/详情 attribution | 逐字段采用 | 未择取缓存 | Customer 值与 provenance | raw profile / 数据集导出 |
| --- | --- | --- | --- | --- | --- | --- |
| Companies House Public Data API | 经部署方数据保护/版权评审和显式 policy 后可到 `ADOPT_SELECTED_FACTS`；未配置时 `NOT_READY` | 受控 `Companies House` 来源名；不返回任意 provider URL，不暗示认证/背书 | 只限本文件基础组织事实 | 默认关闭；若 policy 允许仍 tenant/binding 隔离且不超过 observation TTL | selected fact 按 B06 生命周期；最小 provenance 按显式有限期限 | raw 永久禁止；数据集导出不属于 B23 |
| GLEIF API / LEI data | CC0 与 Terms 允许基础事实再利用，但仍需 tenant policy；可到 `ADOPT_SELECTED_FACTS` | 受控 `GLEIF` 来源名，并避免暗示 GLEIF 背书 | 只限通过非自然人资格门的基础事实 | 许可可能允许，但核心默认关闭 | selected fact 按 B06 生命周期；最小 provenance 按显式有限期限 | raw 永久禁止；bulk/file export 是独立批次 |
| B23-C 商业聚合模型 | 默认 `NOT_READY`；合同只允许显示时最多 `EPHEMERAL_ONLY` | 不能从公开页面猜文案、Logo、链接或位置 | 只有合同明确允许 CRM 逐字段保存、地域、删除和 attribution 才可能开启 | 默认关闭；合同未给 TTL/范围时禁止 | 未证明 Customer 持久化和 provenance 权利，默认禁止 | 永久禁止进入开源核心；再导出不纳入 |
| 合成 dev/test adapter | 仅 dev/test 可到 `ADOPT_SELECTED_FACTS`，prod 永远拒绝 | 固定 `Synthetic registry fixture` | 允许测试全部规范事实与负向分支 | 关闭 | 只写测试数据库和确定性 provenance | 不包含也不生成真实数据 |

Companies House/GLEIF 行记录的是 B23-O 已核对公开材料后的“可配置资格”，不是部署即生效的法律结论。商业来源即使未来合同允许采用，也仍使用 `COMMERCIAL_AGGREGATOR` kind，不能冒充 official/open；反之 CC0 也不扩大 B23 首期字段 allowlist 或 CRM authority。

## 8. TTL、缓存、保留与删除矩阵

生产不使用代码猜测的通用合规期限。source policy 和部署 ceiling 都必须显式存在，保存时计算 deadline；policy 缩短需要有界重算并提前 deadline，deadline 单调不增，policy 延长只影响新观测/采用，不静默延长既有记录。负值、零值、溢出、缺失或超过部署 ceiling 使对应模式不 ready。

| 数据类别 | 开源核心存储 | TTL/保留规则 | 到期动作 |
| --- | --- | --- | --- |
| provider raw response、人员/关系/联系方式 | 不存 | adapter 白名单化后立即丢弃；不得进入日志/quarantine 默认路径 | 当前调用缓冲释放；无核心 tombstone |
| 搜索候选、未择取详情、查询词 | 不进 MySQL | 默认不缓存；policy 明确允许的 adapter cache 仍不超过 5 分钟 observation TTL，并按 tenant/source binding/selector 分区 | 物理移除缓存；不转存历史 |
| observation/profile reference | 不进业务 MySQL | 正 TTL，硬上限 5 分钟；来源更短则取更短 | 变为 `EXPIRED`，必须重新查询；不能读取旧 profile 假装新鲜 |
| source locator、provider URL/order/account token | 不存 | 只允许私有 adapter 当前调用栈或受 policy 约束的短期 opaque resolution；不进入 provenance | 调用/引用到期即销毁 |
| 已采用 B06 Customer 值 | Customer aggregate | 只在 `ADOPT_SELECTED_FACTS` 下保存；遵循 B06 生命周期和更严 source policy | observation 到期不删除；人工覆盖/清空或 Customer 受治理删除。若来源要求自动物理到期而 B06 不能执行，则来源不 ready |
| B23-XI identity 关联 | 专用 tenant/customer 关联 | 只在允许持久化身份时保存；具体历史/删除由 XI 冻结 | 不因 profile TTL 解除；按 Customer/identity policy 处理 |
| 最小 provenance header/lines | 专用 append-only store | 每条 adoption 必有显式有限 `deleteAfter`，取 source policy 与 deployment ceiling 较早者；未解决的纠错/争议不能靠延长普通 TTL 伪装 legal hold | 有界 worker 先去除可识别关系，再按审计/完整性窗口物理删除；不保留 adopted value tombstone |
| 可选 source revision | 默认不存 | 只有 policy 证明必要且允许时与 provenance 同 deadline或更早 | 与 provenance 一起或更早删除 |
| attribution/policy 历史 | 受控配置历史 | 至少覆盖仍存 provenance 的全部引用；不得借历史配置保存外部 profile | 最后引用到期后按配置审计 policy 删除 |
| 安全运维审计/metrics | 只含本地 operation、source kind、安全分类、计数桶和耗时 | 使用普通运维期限；不得含名称、登记号、地址、field diff、source revision 或错误原文 | 按运维 policy 删除 |

合成 fixture 使用确定性基线：observation/reference 5 分钟、未择取 cache 关闭、provenance 365 天；这些只用于断言 deadline 与 worker 行为，不是生产来源的默认合规期限。

Customer 当前只有 `status=DELETED` 的软删除边界。任何真实来源若要求合同终止、异议或固定期限后物理清除已采用字段，而首期没有相应 Customer/backup sweep，就不能宣告 `ADOPT_SELECTED_FACTS` ready。备份恢复到可服务环境前必须先运行 B23 provenance/cache policy sweep；不能只清主库后宣称所有副本已删除。

## 9. 预览到择取的 policy/mapping 绑定

预览引用至少绑定 tenant、受治理 source binding/version、source policy version、attribution version、canonical identity、profile facts、observation expiry、B05 mapping version、目标 Customer/expected version和当前可选择 fact kinds。它可以是本地有界 opaque reference，不把这些值作为客户端可改写字段。

择取时：

1. 在数据库事务外重新解析同一 tenant source binding，重新 exact lookup 并取得新 observation；
2. 重新验证 identity、主体资格、usage mode、attribution 和 TTL；
3. 比较客户端选择所对应的 canonical facts。名称、状态、日期/kind、地址或 identity/standing 改变时返回 conflict，要求重新预览；
4. 进入本地事务后取得 `(tenant, CUSTOMER)` guard，重载 Customer、mapping、field definitions、role access 与 XI identity 约束；
5. 任一 policy/mapping/Customer/字段版本漂移、字段隐藏/脱敏/只读、地址槽位漂移或 identity 仲裁失败都整体回滚；
6. 保存同一 Customer 实例、XI 关联和 provenance，再提交后执行普通 cache invalidation/event；外部 adapter 不进入 Customer repository。

policy 在预览后收紧时不能用旧 observation/grant 完成择取。policy 变宽也不自动扩大既有预览，必须重新查询，避免旧响应获得后来才出现的用途。

## 10. 合成契约 fixture

B23-XP 为 B23-XF 固定以下 oracle；所有主体、名称、地址、identifier、revision 与 attribution 都必须是合成值：

1. 三类真实来源形态和一个 synthetic kind 只产生固定 source kind；未知 kind、客户端 source selector 和生产 synthetic profile 失败关闭；
2. 详情缺少 identity、registered name、entity status、observed/expiry 或 attribution readiness 时不生成 profile；可选值缺失不被推断；
3. Companies House 四类 creation 语义、GLEIF entity creation 与商业 StartDate 只产生五个 date kind；未知/冲突 kind 使日期缺失，source update/renewal 永不进入 formation date；
4. Companies House registered office、GLEIF legal address和商业注册地址缩减为单一规范地址；service/headquarters/person address、坐标和多余分量被丢弃；
5. company type、GLEIF legal form/category 和商业 `EconKind` 不进入首期 profile，provider 新字段不会通过 map 泄漏；
6. registered name 只能落 `customer_name`；entity status 写内置 `status`、日期写 `created_time`、identity/source 写 `description/tags/custom TEXT` 都在 Customer mutation 前拒绝；
7. custom status target 缺少精确三选项、date target 类型错误或 kind 不在 mapping allowlist、legal jurisdiction target 不兼容时不生成 diff；
8. 地址首次采用追加非主槽位，刷新只更新绑定槽位并保留其他地址；槽位删除、20 条上限、primary 冲突或坐标清理未显式展示时整体拒绝；
9. HIDDEN、masked、READ-only、inactive、mapping version/definition/option/access drift 的字段既不显示 diff，也不能在择取事务中写入；
10. provenance 与 Customer/XI 关联全有或全无；header/lines 只有 allowlist metadata，数据库、日志、event、Activity 与 report 中没有 adopted value、raw profile、source locator/revision 默认值或 hidden current value；
11. `NOT_READY` 不调用真实 source，`EPHEMERAL_ONLY` 不能写任何 Customer/identity/provenance，`ADOPT_SELECTED_FACTS` 在 attribution/retention 任一执行能力缺失时降级或失败关闭；
12. observation 在 5 分钟或更短 policy TTL 后稳定 `EXPIRED`，不读取旧 cache；到期不改写已采用 Customer 值；
13. provenance deadline 取 source/deployment 较早者，policy 缩短提前清理、延长不改变既有 deadline，恢复备份先 sweep；
14. attribution context 缺失使对应 display/adopt operation 不 ready；普通 B06 不支持持续 attribution 时，要求 `CUSTOMER_READ` attribution 的来源不能持久化；
15. 同一 adapter 进程中的两个 tenant 不能共享 binding、policy、mapping、observation reference、cache、attribution 或 provenance；请求伪造 tenant/provider/account 不改变分区；
16. fixture 扫描确认名称、登记号、地址、人员、revision、URL、query、credential、provider body/error 在日志、trace、metric、event、Activity、测试快照和仓库历史中的命中数为零。

这些 fixture 证明规范缩减、权限、policy 和持久化边界，不证明真实商业合同、实际来源正确性、生产地域合规、provider schema/QPS 或真实 adapter readiness。

## 11. 闭合与剩余门槛

B23-XP 已闭合：

- 首期规范 profile 的必需/可选事实与明确排除项；
- 五种 date kind、四种 source kind 以及 observation/source-update/revision/expiry 分离；
- source 目录、attribution context 和 `NOT_READY`/`EPHEMERAL_ONLY`/`ADOPT_SELECTED_FACTS` 最严交集；
- 对现有 B05/B06 的逐事实落点，尤其 entity status 与 Customer lifecycle 分离、地址结构化槽位合并；
- 不复制 adopted value 的最小 durable provenance、读取边界、TTL、缓存、保留与删除矩阵；
- B23-XF 的 profile/policy/mapping/provenance 组合 oracle；其中与 B23a 只读边界相交的部分已由 `B23-XF/1` 执行。
- B23-XI 已冻结且 B23a 已消费的 identifier authority/scheme/value/standing 类型与 legal jurisdiction 边界。
- B23-XF 已闭合跨官方/开放/商业合成 source shape 的只读组合 fixture。

仍未闭合：

- tenant 内 Customer/XI 关联和 MySQL 仲裁；
- [B23-O/2](B23-O-official-source-readiness.md) 已冻结 GLEIF category 缺失时拒绝、Companies House 组织 type allowlist、三条 RA code 映射和 `GB_CH_NUMBER` 规则；B23-CH 已实现 domain scheme，B23-RA 已实现 test-scope mapper，真实 adapter、runtime RA catalog/digest、共享 admission 与 live conformance 仍未实现；
- 任一商业来源的真实合同、attribution、TTL、持久化、地域、删除、并发/额度和私有 conformance evidence；
- B05 diff、address slot、provenance、retention 和 MySQL 采用分支的实现级 fixture；
- 最终 API/Java/SQL 名称、能力和错误码，以及 retention worker、Customer 物理清除/导出、真实 adapter 和生产配置实现。

因此 B23-XP 与 B23-XF 完成都不单独触发采用实现拆批。B23-XF 已使用本文件与 XI 的 identity/standing oracle 组合复核 B23a；下一步仍要求目标真实来源具备可证明 policy/readiness，并为 Customer/XI/provenance 本地事务补齐实现级 MySQL fixture。
