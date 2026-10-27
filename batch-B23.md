# B23 企业数据查询与客户资料择取开源边界规划

> 基线：`f31fcbc docs(B22-S): 闭合呼叫状态与双腿拓扑设计`
>
> 对账日期：2026-07-20
>
> 状态：B23-C、B23-O/1、B23-O/2、B23-XI、B23-XP、B23-XF、B23a 与 B23-CH 已完成；B23-OC/L1/RA/L2/L3 继续提供离线契约、mapper、durable simulator 与 HTTP/security simulator。B23d 新增默认关闭的 GLEIF Level-1 HTTPS runtime、版本化 RA catalog/digest、MySQL 跨实例共享额度和 owner-only conformance report gate；私有 live runner 已只用内存中的公开记录完成 `O2-12` 子项，报告不含真实值且被 runtime 刻意拒绝。B23e 已实现 Customer 身份占用、preview/re-exact adoption、B05/B06 写入、值无关 provenance、耐久 retention lease 与 MySQL 并发/回滚/软删除/双 tenant oracle；Companies House 也已有默认关闭的 HTTP Basic adapter/policy gate。完整 `O2-01..12`、`DURABLE_SHARED` 证明、经审查的真实来源 adoption 权利、Companies House API key/隐私/版权审查和独立 live evidence 仍待闭合。

## 1. 术语与范围结论

B23 中的“企业数据”特指：CRM 成员在人工作业中，从受治理的外部法人或组织登记数据源查询候选主体，查看有界的基础登记事实，并把明确选择的事实用于创建或更新当前 tenant 的 B06 客户。它不指 tenant CRM 全量业务数据、通用导入导出、BI 数仓、征信、KYC、供应链风控或一套可本地复制的全国企业数据库。

B23 不在当前 MIT 仓库内抓取、镜像或再分发国家公示网站、商业企业数据库和人员关系图谱。开源部分定位为“企业数据查询与择取内核”：定义 provider-neutral 的查询、基础主体观测、来源与时效语义，执行 caller、tenant、能力、客户数据范围、字段权限和并发版本校验，并通过稳定输出端口调用部署提供的数据适配器。

真实数据源 API、SDK、账号、密钥、原始响应、供应商枚举、搜索排序、额度、缓存和许可执行属于私有外部适配器。私有模块只能实现开源 application 端口，不得访问 Customer repository、MyBatis mapper、PO 或数据库表，也不得要求 domain/application 引入供应商 SDK、响应 DTO、风险标签或计费模型。

开源仓库只保留一个确定性的合成 dev/test 适配器和一个显式禁用适配器。合成 fixture 不含真实企业、自然人或供应商数据；生产没有合规且已启用的真实适配器时，企业数据能力明确不可用，但普通 B06 客户创建、更新和查询继续独立工作。

因此 B23 开源的是可审计、可替换的查询与安全择取边界，不承诺随 MIT 代码附送任何企业数据、实时性、覆盖率、法律结论或商业风险判断。

## 2. 开源与私有矩阵

| 能力 | 决策 | 所属边界 | 说明 |
| --- | --- | --- | --- |
| 有界名称搜索与登记标识精确查询用例 | 开源 | api/application | 显式 `AuthenticatedCaller`；只接受 typed、长度受限的查询 |
| provider-neutral 主体候选与基础登记观测 | 开源 | application contract | 固定字段、source kind、观测和到期时间；不接受任意属性 map |
| 数据源能力与使用约束探测 | 开源 | application output port | 表达支持的辖区、标识方案、查询方式和允许的使用模式 |
| 客户创建/更新前的字段差异预览 | 开源 | application | 当前 B05 字段策略与 B06 可见性下生成，不写业务数据 |
| 明确选择事实后的客户创建或更新编排 | 开源 | application/domain/persistence | 复用 B06 聚合、锁、字段校验、唯一值和乐观锁，不复制客户表 |
| 最小来源凭据与择取审计 | 开源 | application/persistence | 只保存证明来源和操作所需的最小事实，不保存原始 profile |
| 禁用实现、合成模拟器与适配器契约测试 | 开源 | `infra/external`/tests | 普通构建和测试不依赖外部网络、账号或真实数据 |
| 厂商 SDK、HTTP API、签名、响应 DTO 和状态枚举 | 私有 | 独立外部适配器 | 不进入当前仓库依赖树或公共错误 |
| 国家或地区登记机关的真实 API 接入 | 私有或后置可选模块 | 独立外部适配器 | 是否开源取决于稳定性、使用条款、许可和维护责任 |
| 商业企业数据、风险、关系和知识产权 API | 私有 | 独立外部适配器 | 数据维度和授权差异不能抬升为核心通用模型 |
| tenant 到数据账号、套餐和 source binding 的映射 | 私有部署配置 | 适配器/secret 管理 | 普通请求不能选择账号、source binding、套餐或计费主体 |
| API key、secret、证书和供应商 access token | 私有 | 部署 secret 管理 | 不进入请求、响应、MySQL 明文、样例、日志或指标标签 |
| 原始响应、文书、报告、图片和附件 | 私有或不纳入 | 供应商授权存储 | 开源核心不持久化、不代理下载、不建立通用 blob 字段 |
| 批量抓取、验证码绕过、反爬对抗和页面解析 | 不纳入 B23 | 禁止作为参考实现 | 只允许经授权、文档化的数据接口或部署方合法数据集 |
| 自然人联系方式、法定代表人、董监高、股东和受益所有人 | 首期不纳入 | 后续隐私专项 | 公开可见不等于可以无边界复制、画像或营销使用 |
| 司法诉讼、被执行、失信、舆情和供应商风险分 | 不纳入 B23 首期 | 独立风控批次 | 来源、异议、时效、解释和决策影响需要独立治理 |
| 自动监控、变更订阅和风险告警 | 不纳入 B23 首期 | 调度/集成批次 | 需要 outbox、任务租约、配额、去重、退订和可靠投递 |
| 企业库导出、销售名单生成和跨 tenant 共享 | 不纳入 | 数据治理/营销批次 | 查询能力不得变成数据转售、批量获客或越权导出通道 |

## 3. 与现有能力的承接关系

### 3.1 B06 Customer 仍是 CRM 业务真相

B23 不新增一个与 `Customer` 平行、长期复制客户资料的 `Enterprise` 聚合。外部数据是某个来源在某个时间的观测，不是对 CRM 客户名称、负责人、协作者、层级、标签、交易状态和自定义字段的持续所有权。

创建客户必须复用 B06 的创建协调边界，更新必须保存同一个已加载的 Customer 聚合实例。B23 不直接写客户表或自定义值表，也不绕过以下现有规则：

- `customers:write`、必要时的 `customers:assign`、当前数据范围和活动成员关系；
- B05 当前活动字段定义、调用成员的 `WRITE` 权限、字段类型、唯一性与遮罩策略；
- `(tenant, CUSTOMER)` customization guard、客户层级/公海边界、tenant 复合约束和乐观锁；
- 创建或更新响应继续按当前 `READ` 与遮罩策略组装，外部数据权限不能扩大 Customer 输出。

外部来源不能指定 owner、collaborator、parent、tag、状态、tenant 或管理员身份。创建时这些 CRM 事实仍由人工请求和已验证 caller 按 B06 规则提供；数据适配器只提供允许的登记事实。

### 3.2 B05 字段治理负责最终落点

外部字段不能因为供应商返回就自动获得写权限，也不能动态创建 B05 字段、选项或布局。字段差异预览只展示同时满足以下条件的映射：稳定规范事实、tenant 已配置的允许落点、当前活动兼容字段、调用成员当前可写。

实现前必须决定基础登记身份是使用专用 tenant/customer 关联，还是新增受治理的 CUSTOMER 内置字段；不能把统一社会信用代码、公司编号、登记状态或来源引用拼入 `description`、`tags` 或一个无类型 JSON 自定义字段。供应商专属维度默认没有落点，缺少明确映射时只展示或直接丢弃，不能猜测字段 code。

### 3.3 B07 公海、B09 线索与 B17 报表不被扩大

- 企业数据查询不授予普通客户、公海客户或隐藏客户的读取、更新和领取资格。把候选关联到既有客户前，仍按目标记录当前所属边界执行 B06/B07 的原有可见与写入规则。
- 首期只对 B06 Customer 创建或更新，不自动更新 Contact/Lead，不把搜索候选批量创建为线索，也不触发 B09 公开接入。
- 外部搜索结果和未择取 profile 不进入 B17 报表、B18 仪表盘、B15 工作台、Activity、审批事实、缓存键或全文搜索。后续若把规范登记事实用于报表，仍必须经过 B05 字段与来源对象的独立权限复核。

## 4. 首期最小产品边界

### 4.1 纳入

1. **精确登记身份查询**：客户端提交受支持的辖区、登记标识方案和值；标识只是查找 selector，服务端必须通过当前 tenant 的受治理适配器重新取得事实。
2. **有界名称搜索**：使用裁剪后的非空名称和显式辖区，返回固定上限的候选，不提供任意 offset、供应商排序、批量遍历或“导出全部”；响应不承诺供应商总数。
3. **基础主体详情**：只返回两个独立适配器都能稳定表达且首期允许使用的规范事实，以及来源、观测时间、到期时间和必要 attribution。
4. **客户字段差异预览**：选择一个候选后，针对一个当前可写 Customer 或待创建 Customer 计算 typed diff；预览不修改客户。
5. **显式择取**：成员逐字段确认后，服务端重新解析选择、取得新鲜观测，并在本地命令事务内创建或更新 Customer，同时保存最小来源与审计事实。
6. **手工刷新**：对已关联客户重新查询并预览差异；刷新永不自动覆盖本地值，也不因为外部主体失活而删除、锁定或移入公海。

名称搜索首期应使用小而固定的服务端上限，建议不超过 20 个候选；具体字符上限和页形态在实现批次确定，但不得接受任意过滤表达式、字段名、SQL、供应商 DSL、排序字段或透传参数 map。

### 4.2 基础规范事实候选

最终类型名留到实现批次，但首期稳定语义只考虑：

- 辖区和登记标识方案；
- 规范登记标识值；
- 登记名称；
- 粗粒度登记状态，至少能安全区分 `ACTIVE`、`INACTIVE` 与 `UNKNOWN`，不能把供应商风险结论伪装成登记状态；
- 可选法律辖区、带明确 date kind 的一个成立相关日期和单一登记地址；
- 固定 source kind、来源展示归属、`observedAt`、可选 source update/revision 与 `expiresAt`。

任何可选值缺失都保持缺失，不能用空字符串、推断值或另一字段补齐。地址首期只是登记地址观测，不宣称它是营业地址、邮寄地址或 B06 的唯一主地址。状态映射必须保留 `UNKNOWN`，不能因供应商新增枚举而把主体误判为 active；日期无法证明 kind 时整体缺失，不设置通用 `UNKNOWN` kind。

[B23-XP](docs/B23-XP-profile-provenance.md) 复审后把 `organizationType`/legal form 从首期 profile 移除：Companies House company type、GLEIF legal form/category 和商业来源 `EconKind` 没有可证明的共同值域，不能以供应商自由文本或猜测性粗分类补齐。日期只允许 `INCORPORATED`、`REGISTERED`、`FORMED`、`ESTABLISHED` 或 `OPENED`；无法证明 kind 时整个日期缺失。

首期不把下列内容纳入公共规范 profile：法定代表人、人员姓名、证件、电话、邮箱、股东、董监高、受益所有人、实际控制人、资本穿透、分支关系、银行账户、税票资料、诉讼、执行、处罚详情、知识产权、新闻舆情、经营异常、供应商评分和联系方式。适配器取得的多余字段必须在私有边界丢弃，不能塞入扩展 map。

### 4.3 不纳入

- 克隆一个全量企业搜索网站、关系图谱、批量名单库或企业详情门户；
- 自动建客、自动合并、自动覆盖本地字段、自动改变负责人或自动判重删除；
- 企业认证、电子营业执照验真、开户/KYB/KYC、反洗钱、授信、制裁筛查或风险准入；
- 供应商报告购买、PDF/图片下载、工商档案、历史文书和原始证照存储；
- 联系人挖掘、号码/邮箱补全、营销触达、群呼、群发和广告受众构建；
- 跨辖区法律状态推理、集团/母子公司自动建树或以供应商关系覆盖 B06 人工客户层级；
- 定时刷新、全租户扫描、变更 webhook、消息通知、自动任务和 SLA。

## 5. 稳定查询与观测契约

### 5.1 登记身份不等于供应商 ID

规范主体身份按 [B23-XI](docs/B23-XI-registered-identity.md) 固定为 `(identityScope, registryScheme, canonicalValue)`。`identityScope` 是号码唯一性成立的受治理命名空间，`legalJurisdiction` 是独立可选主体事实，不参与身份键。供应商内部 company ID、URL、搜索排名和 account-scoped token 只是不透明 source locator，不能成为 Customer ID、tenant、授权事实或跨供应商永久主键。

登记值按方案执行明确的字符集、大小写、长度与校验规则；不能对所有国家统一做 NFKC 加小写，也不能假定所有主体都有中国统一社会信用代码或 LEI。中国统一社会信用代码的方案可依据现行 GB 32100 单独实现，其他辖区使用各自 scheme adapter；未知 scheme 失败关闭，不以“看起来相似”匹配。

一个未删除 Customer 可以关联多个规范登记身份；反向同一 tenant 的同一规范身份最多关联一个未删除 Customer，`ACTIVE` 与 `IN_POOL` 都保有占用。B06 仍允许没有共同规范身份的重复 Customer，这不是现实世界主体全局唯一约束。Customer 软删除在同一事务释放当前占用并保留 provenance；外部主体/standing 变化、无结果或来源不可用都不自动释放、换绑或改变 Customer hierarchy。

### 5.2 外部结果是有时效的观测

公共契约表达“某来源在 `observedAt` 返回这些事实”，不表达永久真相或官方认证。每个可用于择取的观测必须有：

- 核心生成的本地查询/观测关联标识，或可由适配器安全解析的短期不透明引用；
- 规范主体身份和固定 allowlist 事实；
- 固定 source kind、受控来源目录与显示所需 attribution，不泄露凭证、账号或内部路由；
- `observedAt`、明确的过期语义和按政策可选的 source revision；不保存通用 value/response digest；
- 使用模式，例如只可临时显示、允许择取选中事实，或当前 tenant 不允许持久化。

搜索候选列表和未择取详情默认不进入开源 MySQL。客户端回传的名称、地址、状态或日期都只是不可信展示值，择取时服务端必须用不透明引用或规范登记 selector 重新从已选定适配器取数；不能把预览响应原样当作可信写请求。

### 5.3 不允许无界通用 DTO

端口不得使用 `Map<String, Object>`、原始 JSON/XML/HTML、供应商 SDK 类型、HTTP request、动态字段路径、可执行映射表达式或任意 error body。供应商新增字段默认被忽略；只有通过跨适配器语义、隐私、许可、存储和字段映射复审后才能进入公共 allowlist。

查询能力应是 typed capability：支持的辖区、登记方案、精确查找、名称搜索、手工刷新、最大候选数、观测有效期与允许的使用模式。它不公开账号余额、套餐名称、供应商 endpoint 或凭证状态。

## 6. 预览、择取与本地事务

### 6.1 预览不授权写入

能查询外部企业数据，不等于能读取或修改任意 Customer。针对既有客户的预览必须先验证目标 Customer 当前可见且可写；若页面需要提示“tenant 内已有相同主体”，只能返回调用者当前可读的客户，隐藏客户与不存在客户必须保持不可区分。

预览只列出当前 B05 允许的目标字段、当前值、外部建议值、来源和观测时间。被隐藏、只读、脱敏、不兼容或未配置映射的字段不进入 diff；不能为了预览而返回调用者原本看不到的当前值。

### 6.2 择取事务顺序

企业数据调用是外部网络读取，不能在持有 Customer、组织或 customization 数据库锁时等待供应商。计划中的命令顺序固定为：

1. 从 `AuthenticatedCaller` 解析当前活动成员和 tenant，验证企业数据入口与目标 Customer/B06 创建资格；
2. 在 `validate/prepare` 阶段通过 tenant 绑定的适配器取得新鲜、固定 allowlist 的观测，执行来源、方案、长度和时效校验；
3. 客户端只提交待采用的规范字段选择、目标 ID 和 expected version，不提交权威外部值、tenant、source binding、owner 或权限；
4. 进入 `CommandServiceTemplate` 的独立本地事务后，按 B06 既有顺序取得 `(tenant, CUSTOMER)` 和目标根所需锁，重新读取当前 Customer、活动字段定义与有效字段访问；
5. 对所选值复用 B06 类型规范化、字段写权限、唯一值、层级和 tenant 约束，保存同一个 Customer 实例；
6. 在同一事务保存最小来源/择取审计；提交后才允许普通缓存失效和安全事件发布。

若 Customer 版本、字段定义、角色字段权限、映射或外部观测绑定在预览后变化，整次择取稳定冲突或失败关闭，不能部分写入。供应商在本地事务提交后继续变化不构成分布式回滚；已写值必须明确表示一次带时间的人工择取，后续刷新再次预览。

### 6.3 创建与更新边界

- 新建 Customer 时，名称等外部建议值仍逐字段经过当前 `WRITE`；owner 和可选 CRM 关系由人工请求按 B06 提供，适配器不能决定。
- 更新 Customer 时必须提交 expected version，并明确列出允许覆盖的字段。默认不把空本地字段以外的值视为可覆盖授权；UI 可以提示差异，但服务端以显式字段选择为准。
- 同一次择取的 Customer 写入、规范登记身份关联、来源凭据和审计必须原子提交；任何唯一冲突、版本冲突或审计失败都全部回滚。
- 不创建第二套 Customer snapshot、标签、地址、owner、协作者或自定义值表；来源记录也不成为绕过 B06 查询 Customer 的入口。

## 7. 安全、tenant 与数据权利边界

- 每个查询、预览、创建、更新和刷新用例显式接收 `AuthenticatedCaller`，经 `CallerGuard` 和当前活动成员关系派生 tenant。普通请求不能提供或切换 tenant、数据账号、source binding、供应商、套餐、actor、role 或 administrator。
- 即使底层登记数据面向公众，应用调用仍 tenant scoped：tenant 决定启用的数据源、许可模式、额度和政策。开源核心不建立跨 tenant 搜索历史、共享 profile 缓存或可被另一个 tenant 命中的择取记录。
- B23 首期是人工触发的 outbound read，不需要把私有适配器伪装成 `AuthenticatedCaller`，也不复用 B22 的 connector ingress。若后续增加 webhook/订阅，必须另行设计非人工 integration principal 和 tenant 解析，不能让 callback 直接写 Customer repository。
- 完整查询词、企业名称、登记标识、地址、候选内容、人员信息、原始响应、source locator、token、账号、供应商错误和字段差异不得进入日志、trace、metric 标签、普通事件、Activity 或缓存键。可观测性只记录本地操作 ID、受控 source kind、安全结果计数桶、耗时和错误类别。
- 供应商响应中的自然人信息在私有适配器首期即丢弃。数据已公开不自动消除合法、正当、必要、合理范围、准确性、告知、安全和异议处理责任。
- 外部数据权限不能扩大 B05/B06 权限；反向也不能把 Customer 可见性解释为可以查看供应商返回的全部人员、风险或商业数据。
- 公共错误只区分安全且稳定的不可用、限额、无结果、过期、冲突和不支持类别；不得透传 HTTP body、供应商错误码、请求 URL、账号状态或异常消息。

## 8. 许可、来源、保留与删除

MIT 许可证只覆盖仓库代码和合成 fixture，不授予任何登记数据库、商业数据、供应商响应、商标、报告或人员信息的使用权。部署方必须为每个真实适配器提供经过审查的来源政策，至少固定：

- 允许的 tenant、用途、辖区和用户群；
- 是否允许展示、择取、缓存、持久化、再导出和生成衍生值；
- 必需 attribution、来源链接、免责声明和刷新频率；
- 观测有效期、缓存和审计保留上限、纠错/异议与删除要求；
- API 额度、并发、批量限制、禁止用途和账号终止后的处理；
- 对自然人字段、跨境传输和供应商分包的独立合规结论。

适配器 capability 与部署政策共同取最严约束。若来源只允许临时展示，核心必须禁止择取持久化；若来源要求 attribution 或到期停用，缺少必要展示/执行能力时失败关闭。不能用“公开数据”默认推导可缓存、可批量复制、可营销或可永久保存。

开源持久化只保存已择取进入 Customer 的业务值、B23-XI 身份关联和 B23-XP 冻结的最小 provenance：受治理 source binding/kind、策略/attribution/mapping 版本、观测与数据库采用时间、操作者、Customer 前后版本和实际目标 field/address slot。provenance 不复制 adopted value 或普通 digest；source revision 只在政策证明必要且允许时保存，短期 locator 永不保存。原始 profile、搜索候选、供应商报告、HTTP 元数据和未采用字段不落库。

任何真实数据抓取都必须使用文档化且经授权的接口或合法提供的数据文件。B23 不提供页面爬虫、验证码识别、代理池、频控规避、签名绕过或反爬对抗；供应商接口不可用时返回不可用，不回退到未经许可的网页抓取。

## 9. 可靠性、配额与缓存

- 名称搜索和精确查询是只读外部调用，可在适配器规则允许时做有界重试；重试必须沿用同一个本地操作关联，遵守 deadline、退避和供应商配额。
- HTTP 成功只说明一次响应被取得，不证明事实完整、最新、官方认证或适合决策。空结果、部分字段、未知枚举和供应商降级必须显式表达，不能使用旧值假装新鲜成功。
- 择取的正确性以本地 Customer 事务为界。外部读取发生在事务前；外部超时不会产生部分 Customer 写入，本地提交失败也不会要求回滚供应商读取。
- 首期没有外部写副作用、定时刷新或订阅，因此不引入通用 outbox/inbox。后续监控、付费报告购买或 callback 若有可靠投递要求，必须在独立批次设计命令账本、inbox、对账、配额和取消语义。
- Redis 不能成为查询授权、来源绑定、观测完整性或择取事务的唯一正确性来源。任何 adapter cache 都必须满足许可、tenant/账号隔离、TTL、容量和删除政策；禁用 Redis 后业务边界仍正确。
- 数据源限额和熔断失败不能拖垮普通 Customer API。禁用或不可用适配器只影响 B23 用例，并以安全错误失败关闭。

## 10. 模块与发布边界

当前 MIT 仓库后续可包含：

- api/application 中 provider-neutral 的 typed 查询、观测、预览和择取用例契约；
- application 输出端口、适配器能力/使用模式和安全错误语义；
- domain/application 中与 B05/B06 组合的登记身份、来源与字段选择规则；
- infra/persistence 中最小 tenant/customer provenance 和并发约束；
- infra/rest/facade 中面向已认证 CRM 成员的 API，不包含供应商 callback、账号管理或任意代理路由；
- infra/external 中禁用实现、合成 dev/test 模拟器和契约测试工具；
- start 中不依赖私有 artifact 即可构建、测试并运行普通 CRM，B23 默认为不可用。

私有适配器仓库负责：

- 每个登记机关或商业供应商一个独立 artifact，依赖开源 application contract；
- 账号、密钥、签名、endpoint、SDK、配额、重试、响应解析、许可和 attribution；
- 把原始响应缩减为固定 allowlist，首期在返回核心前删除人员、风险、联系方式和供应商扩展字段；
- 显式、确定地按已验证 tenant 解析受治理 source binding，不通过客户端选择或 classpath 偶然顺序决定；
- 复用开源契约测试，证明边界、时效、错误、字段丢弃和 tenant 隔离。

私有模块不得通过 Spring 扫描取得 Customer/Field repository，不得直接更新 Customer、自定义字段、Activity 或审计表，也不得用供应商 DTO 作为 Facade/REST Response。所有 CRM 状态变化经开源 application 用例完成。

## 11. 实现前契约测试门槛

至少用一个官方/开放登记模型和一个商业企业数据模型的只读 fixture 共同证明：

1. 未安装、未启用、政策缺失或 capability 不匹配时，B23 安全不可用且普通 B06 不受影响；
2. caller 无效、成员失活、tenant 不匹配或缺少入口能力时，在外部调用前拒绝；普通请求不能选择另一个 source binding/账号；
3. 名称和登记查询 typed、有界，未知辖区/方案、超长值、空值、任意 filter map 和批量遍历失败关闭；
4. 两个适配器的原始枚举、额外字段和错误都被缩减为同一规范 allowlist，未知状态映射为 `UNKNOWN` 而不是 active；
5. 搜索、详情、预览和错误路径的日志、trace、metric、事件与 Activity 不含名称、登记标识、地址、人员、原始响应、locator 或凭证；
6. 搜索结果不会泄露隐藏 Customer 是否存在；关联提示只返回 caller 当前可读记录且在 SQL 截页前应用 B06 可见性；
7. 预览只包含当前可读且可写的兼容字段，隐藏、脱敏、只读和未映射字段不返回当前值或可写建议；
8. 客户端篡改预览值、来源、观测时间、tenant、source binding 或 adapter 字段不能改变服务端重新取得的权威观测；过期引用失败关闭；
9. 创建和更新完整复用 B06 owner/assign、字段、唯一值、层级、公海、tenant 与 expected-version 规则；适配器不能直接写 repository；
10. Customer、字段定义、角色策略或映射在预览与择取间变化时整次冲突或拒绝，不留下部分字段、身份关联或 provenance；
11. 同一 tenant 的 `(identityScope, registryScheme, canonicalValue)` 由 exact binary MySQL 唯一键仲裁到最多一个未删除 Customer；同身份并发只允许一个事务完整提交，另一事务不留下 Customer、字段、身份或 provenance，且不依赖 JVM 先查后写；
12. `EPHEMERAL_ONLY` 一类来源不能持久化，允许择取的来源只保存选中值和最小 provenance，原始 profile 与未采用字段始终不落库；
13. 供应商超时、429、5xx、未知 error body、部分响应和 license/attribution 失败映射为安全稳定错误，不透传异常或写 Customer；
14. 合成 fixture、测试快照和仓库历史不包含真实企业、自然人、API key、供应商付费响应或受限数据。

数据库、字段策略、Customer 写入、身份关联、审计或缓存边界一旦进入实现，必须运行 Docker/MySQL 集成测试；只用 mock 验证适配器 DTO 映射不足以解除门槛。

## 12. 外部事实依据

- [GB 32100-2015 国家标准页](https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=24691C25985C1073D3A7C85629378AC0&refer=outter)显示“法人和其他组织统一社会信用代码编码规则”目前为现行标准，说明中国标识应作为一个明确 scheme 处理，而不是通用自由文本。
- [国家企业信用信息公示系统使用运行管理办法](https://www.samr.gov.cn/xyjgs/flfg/art/2023/art_f6972d9dbbf641168e71fab9de31cb7a.html)把公示系统定义为国家企业信息归集公示和监管平台；B23 不由“公众可查询”推导一个未经授权的批量 API、镜像或爬虫权利。
- [企业信息公示暂行条例（2024 修订）](https://policy.mofcom.gov.cn/claw/clawContent.shtml?id=99906)明确企业信息公示的监管目的，并单独处理商业秘密和个人隐私，支持把基础主体事实与人员/敏感维度分开治理。
- [企查查开放平台](https://openapi.qcc.com/)展示的商业 API 同时覆盖工商、人员、司法、知识产权、联系方式和风险等大量异构维度，证明不能把某一供应商完整 response 提升为 CRM 公共模型。
- [Companies House Developer Hub](https://developer.company-information.service.gov.uk/)提供实时公司信息 API；其[开发指南](https://developer.company-information.service.gov.uk/developer-guidelines/)另有认证、限额和响应演进约束，适合作为官方登记类只读 spike，而不是默认内置依赖。
- [GLEIF Open Data](https://www.gleif.org/en/about/open-data)说明 LEI 数据是开放、标准化的法律实体参考数据，但 LEI 与地区登记编号的覆盖和语义不同，支持以 typed jurisdiction/scheme 而不是单一“企业 ID”建模。
- [中华人民共和国个人信息保护法](https://www.samr.gov.cn/wljys/gzzd/art/2023/art_3ef1e889c1e644d4b65b5f5c7f432386.html)要求合法、正当、必要、最小范围和安全，并只允许在合理范围处理已合法公开的个人信息；公开人员数据不能自动进入首期企业 profile。
- [中华人民共和国反不正当竞争法（2025 修订）](https://www.npc.gov.cn/npc/c2/c30834/202506/t20250627_446247.html)禁止以避开或破坏技术管理措施等不正当方式获取、使用他人合法持有的数据，支持明确排除验证码绕过、反爬对抗和未授权抓取。

## 13. 初始规划轮次不做的决定与下一步

> 本节保留 B23a 实现前的历史决策。后来落地的只读例外以第 18 节为准；这里对真实 adapter、持久化和 Customer 写入的限制继续有效。

- 不选择首个中国商业供应商、官方登记 API、SDK、Java client 或数据采购方案；
- 不确定最终 API 路径、Java 类型、能力 code、Customer 字段 code、表、错误码、缓存实现或 Flyway 版本；
- 不实现已由 B23-XI 冻结的登记身份目录、Customer 一对多关联、删除释放和唯一仲裁；错误解绑、未删除 Customer 合并/转移仍留给独立高权限设计；
- 不实现代码、迁移、配置、网络测试、真实数据 fixture、爬虫、数据导入或私有 artifact；
- 不预占 B23a/B23b。只有 B23-XI 身份/Customer 关联、预览-择取事务和 B23-XF 至少两个适配器组合映射通过复审，并且目标真实来源具备可证明政策/readiness 后，才能拆实现任务。

B23-XP 已冻结 source kind、date kind、attribution context、最小 provenance 与 TTL/保留矩阵；source locator 永不持久化、adopted value/digest 默认不复制，商业来源仍必须以真实合同补足具体政策。

B23 原定的两份只读 spike 均已完成：中国商业模型由 [B23-C](docs/spikes/B23-commercial-enterprise-data.md) 覆盖，官方/开放登记模型由 [B23-O/1](docs/spikes/B23-official-open-registry.md) 覆盖。两份 spike 都只记录公开文档中的查询、标识、状态、时效、限额、许可、人员字段和错误差异，不提交供应商代码、凭证或真实响应；跨模型 identity scope、standing、Customer 基数与 MySQL 唯一仲裁由 [B23-XI](docs/B23-XI-registered-identity.md) 闭合，profile/provenance/字段/政策共同复审由 B23-XP 完成，[B23-XF](docs/B23-XF-composed-fixture.md) 已把两类合成 source shape 与 B23a 查询链路组合执行。[B23-O/2](docs/B23-O-official-source-readiness.md) 又冻结官方/开放来源的组织资格、RA/Companies House number、许可、quota、TTL、fail-closed 和三层真实 conformance oracle。

B23-C 证明了有界名称搜索、统一社会信用代码详情和基础字段的适配形态，也证明人员、联系、关系与画像字段必须在私有 adapter 内丢弃；但公开资料不足以冻结完整登记状态、持久化许可、attribution、TTL 和数值并发上限，因此仍不构成真实 adapter readiness。2026-07-20 漂移复核还发现公开协议已显示新的生效日期但不再呈现可审查正文，原请求状态码 URL 也已跳转；旧条款和错误目录只能保留为合成 fixture 输入，不能提升为当前生产政策。

## 14. B23-O 官方/开放登记模型与 readiness 结论

[B23-O/1 官方/开放登记模型只读 spike](docs/spikes/B23-official-open-registry.md) 以 Companies House Public Data API 与 GLEIF API / LEI-CDF 3.1 为代表模型，已固定以下约束：

- Companies House company number 是登记机关范围内身份，LEI 是全球法律实体标识；同一观测可以携带多个有 scheme 和 authority 的登记身份，但不能把名称、地址、provider URL 或未知登记机关字段猜成同一身份；
- 原候选 `(jurisdiction, registryScheme, registryValue)` 必须把 identity scope 与 `legalJurisdiction` 分开，否则境外登记、UK establishment 和全球 LEI 无法无歧义表达；
- 粗粒度主体 `ACTIVE/INACTIVE/UNKNOWN` 与标识记录 standing 正交。GLEIF `LAPSED` 不代表主体 inactive，`DUPLICATE/ANNULLED` 也不直接说明主体状态；Companies House 的破产程序、`registered/open` 和未来枚举保守映射为 unknown；
- `observedAt`、可选 source update、可选 source revision 与本地 `expiresAt` 分离。Companies House 的 `ETag` 不是更新时间；GLEIF 的 `LastUpdateDate`/`NextRenewalDate` 也不是本地观测有效期；
- 外部最大值不会上推为公共契约。首期名称查询固定 1..200 Unicode 码点、最多 20 个候选且不开放任意分页/filter；Companies House 600 请求/5 分钟和 GLEIF 60 请求/分钟只由 adapter admission/readiness 消费；
- Companies House 使用应用 API key，GLEIF 当前无需 API key；两者的数据可访问性都不授予 CRM authority。Companies House 下游使用者承担适用法律责任，GLEIF 数据为 CC0 但仍禁止规避限制或暗示背书；
- provider 401/403、400/422、404、429、5xx、超时和任意 error body 被缩减为 B23 稳定安全场景。尤其 provider 401/403 不能伪装为 CRM caller 认证/授权错误；
- Companies House 人员/PSC/filing 和 GLEIF Level 2/映射/历史均不进入首期。GLEIF `SOLE_PROPRIETOR` 明确拒绝，category 缺失时仍需非自然人资格规则才能上线真实 adapter。

B23-O/1 只完成只读模型验证，没有实现代码、配置、迁移、网络调用或真实 fixture。身份 scope、standing、tenant 内 Customer 基数和唯一仲裁后来由 B23-XI 闭合；B23-XP 也已闭合首期规范 profile、date/source kind、最小 provenance、B05 落点和许可/attribution/TTL/保留矩阵；B23-XF 又闭合跨模型合成 adapter 查询 fixture。

[B23-O/2 官方/开放企业源 readiness 与 conformance 契约](docs/B23-O-official-source-readiness.md) 进一步冻结：Companies House `company_type` 明确 allowlist 且拒绝 missing/`other`/unknown；GLEIF 只允许五类组织 category 并拒绝 `SOLE_PROPRIETOR`/missing/unknown；`RA000585/586/587` 映射到 `UK_COMPANIES_HOUSE/GB_CH_NUMBER`，且 scheme、RA mapper 和 conformance 任一缺失时不得生成第二身份；Companies House 600/5m 与 GLEIF 60/min 进入共享 binding 加 tenant 公平 admission，单次查询最多一次 provider call 且零 retry；正向 readiness 和 observation 均受五分钟硬上限、有限权利证据及当前 build conformance 交集约束。`O2-01` 至 `O2-12` 必须在合成 mapper、双 tenant/双 replica durable simulator 和私有 live source 三层运行，机器报告不得保存真实查询值。

B23-O/2 闭合设计、B23-CH scheme 与 B23-RA test-scope mapper 都不表示真实来源已 ready。当前仍没有 production RA artifact loader/digest 校验、真实 adapter、credential/binding、共享 admission 或 live conformance manifest；商业来源合同、Customer 身份/采用实现和 MySQL fixture 也继续阻断 B23-SPLIT。

## 15. 当前任务板与并行关系

> 更新日期：2026-07-20
>
> 本表保留实现前汇总任务，并补记后来独立落地的 B23a 只读内核。商业来源缺少合同或真实 conformance 证据时必须保持 `EPHEMERAL_ONLY` 或 `NOT_READY`，不能由公开文档推导持久化权利。

| 任务 | 状态 | 交付物 | 依赖 | 并行关系 |
| --- | --- | --- | --- | --- |
| B23-O/2 | 已完成 | [官方/开放来源组织资格、RA 映射、许可、quota、TTL、fail-closed 与三层 conformance oracle](docs/B23-O-official-source-readiness.md) | B23-O/1、B23-XI、B23-XP、B23-XF、B23a | 只解除设计缺口，不产生真实 readiness |
| B23-O-L2 | 已完成 | [双 tenant/双 replica 文件持久化共享 admission/readiness simulator](docs/B23-O-L2-durable-simulator.md)，执行 `O2-06/07/08/10` 的 quota、reserve、并发、零重试、TTL、撤销、漂移与重启 oracle | B23-O/2、B23-OC | 只完成第二层合成机械证据；不替代 L1 mapper、O2-09/11 或 L3 live conformance |
| B23-XI | 已完成 | `RegisteredIdentifier` identity scope、legal jurisdiction、主体状态/标识 standing、tenant 内 Customer 一对多/反向唯一规则与 MySQL 仲裁 oracle | B23-C、B23-O、现有 B06 | — |
| B23-XP | 已完成 | [首期规范 profile、日期/source kind、最小 durable provenance、B05 字段落点及许可/attribution/TTL/保留使用矩阵](docs/B23-XP-profile-provenance.md) | B23-C、B23-O、现有 B05/B06；消费 B23-XI oracle | 为 B23-XF 提供 profile/policy/mapping oracle |
| B23a | 已完成 | `enterprise-data:read`、类型化名称/USCC/LEI 查询、XP 规范观测、独立 policy/attribution 目录、稳定错误、禁用与合成 adapter；有效模式至多 `EPHEMERAL_ONLY` | B23-C、B23-O、B23-XI、B23-XP | 不声明采用链路或真实 adapter ready |
| B23-CH | 已完成 | `UK_COMPANIES_HOUSE/GB_CH_NUMBER` domain scheme、八位 ASCII/前导零规范化、pinned 显式号码类别前缀、请求解析及 capability/observation VO | B23-O/2、B23a | 不含 RA mapper、真实 adapter、policy/admission 或 readiness evidence |
| B23-RA | 已完成 | [GLEIF RA/EntityID 离线映射](docs/acceptance/B23-RA.md)、`RA000585/586/587` RA-scoped 号码类别、secondary standing 与负向 fixture | B23-O-L1、B23-CH、B23-O/2 | 只存在 test source；不含 runtime catalog loader、artifact digest、HTTP、policy/admission/build/live evidence |
| B23-XF | 已完成 | [官方/开放与商业模型的双 tenant 合成 adapter 组合 fixture](docs/B23-XF-composed-fixture.md)，可执行覆盖人员字段丢弃、状态/身份映射、政策交集、候选/来源额度、稳定错误和 tenant 隔离 | B23-XI、B23-XP、B23a | 不替代 Customer/MySQL 采用 fixture 或真实 adapter conformance |
| B23-OC | 骨架已完成 | [官方/开放源适配器离线契约](docs/B23-OC-official-source-contract.md)、可继承 JUnit contract 与纯内存合成 reference runner | B23-O、B23-XI、B23-XP、B23a | 不含 HTTP、credential、真实响应、生产 Bean/policy 或 readiness；CH scheme 已由 B23-CH 补齐，但来源证据仍阻断 |
| B23-O-L1 | 已完成 | [GLEIF 离线 mapper lane](docs/acceptance/B23-O-L1.md)、stub transport、合成 JSON:API-like LEI payload、B23-OC 继承测试 | B23-OC、B23-O/2、B23a | 只完成三层 conformance 的 mapper 层；不含 runtime source、真实 HTTP、admission、policy/build/live evidence |
| B23-SPLIT | 后置 | 汇总预览-择取事务、身份关联、provenance 和 fixture 结论，拆分正式实现子批次 | B23-XF、B23-O/2 以及至少一个目标来源的真实 policy/readiness evidence | 依赖完成后执行 |

B23-XF 已使用统一合成数据复核 XI 的查询 identity/standing、XP 的只读 profile/政策约束与 B23a 实现；Customer 唯一仲裁因 B23a 不含采用写链路，仍留给实现级 MySQL fixture。下一轮只有在目标真实来源具备可证明 policy/readiness 后才可进入 B23-SPLIT；商业来源不得用 B23-XF 的测试 attribution、TTL、候选数或 quota 补齐真实合同和持久化许可。

## 16. B23-XI 登记身份与 Customer 唯一关联闭合

> 决策日期：2026-07-19
>
> 完整模型、关系生命周期、并发矩阵与通过条件：[B23-XI 登记身份、Customer 关联与 MySQL 唯一仲裁](docs/B23-XI-registered-identity.md)
>
> 范围：只冻结跨 adapter 规范身份和本地关联正确性；不实现 API、Java 类型、目录、迁移、provider adapter 或真实数据 fixture。

B23-XI 把原候选 `(jurisdiction, scheme, value)` 修正为 `(identityScope, registryScheme, canonicalValue)`，把 `legalJurisdiction` 留作独立可选 profile 事实。identity scope 是 Companies House、Global LEI System、全国统一社会信用代码一类受治理命名空间，不是 provider、connector、tenant、查询地区或登记地址。规范值只按 scheme-specific 字符集、长度、前导零、大小写和校验规则生成；通用 NFKC/lowercase、未知 RA code、名称/地址相似或 provider ID 都不能产生身份。

主体状态固定为 `ACTIVE/INACTIVE/UNKNOWN`，identifier standing 固定为 `CURRENT/STALE/TRANSITIONING/SUPERSEDED/INVALID/RETIRED/UNKNOWN`。两条轴正交：LEI `LAPSED` 映射 stale 而不把 active 主体改成 inactive；未知 Companies House/CN identifier standing 只有在固定 scheme/source contract 明确“不发布独立 standing”且 exact identity、组织资格和使用政策均通过时才可关联。standing 不进入身份键，刷新为 stale、superseded、invalid、retired 或无结果都不自动释放或迁移既有关联。

tenant 内当前基数固定为 Customer 一对多身份、每个规范身份反向最多一个未删除 Customer。`ACTIVE` 与 `IN_POOL` 都占用；Customer 软删除在同一事务释放当前占用，但保留最小 provenance。不同 tenant 可独立采用同一身份，B06 仍允许没有共同规范身份的重复 Customer；分支、站点和人工 Customer hierarchy 不自动成为法定关系，也不能复制母体身份绕开唯一约束。

持久化使用专用 typed 当前关联和 exact binary 唯一键 `(tenant, identityScope, registryScheme, canonicalValue)`，不使用 B05 自定义字段、provider scope、hash-only reservation 或 JVM 先查后写。多身份按规范键排序后普通插入；禁止 `INSERT IGNORE`、`REPLACE` 或冲突时改写 `customer_id`。同身份并发关联到不同 Customer 时 MySQL 只允许一个事务提交，另一事务连同 Customer 字段和 provenance 整体回滚；冲突 owner 隐藏时不返回 Customer ID、名称或存在性细节。删除/新占用并发也由同一唯一索引等待和事务提交顺序仲裁。

B23-XI 只闭合 Customer 关系和 MySQL 仲裁的设计 oracle。B23-XP 已闭合 profile/provenance/许可矩阵，B23-XF 已执行跨 adapter 查询侧合成组合；B23-O/2 已冻结 Companies House number、GLEIF RA/组织资格和官方/开放 conformance 规则，B23-CH 已实现 domain scheme。真实 adapter/live evidence、RA mapper、商业来源合同/conformance 和实现级 Customer/MySQL fixture 仍是采用子批次的阻断项。

## 17. B23-XP profile、provenance 与数据使用矩阵闭合

> 决策日期：2026-07-19
>
> 完整 profile、字段落点、attribution、TTL/保留和 fixture：[B23-XP 规范 profile、provenance 与数据使用矩阵](docs/B23-XP-profile-provenance.md)
>
> 范围：只冻结实现前 provider-neutral 语义和 oracle；不实现 API、Java 类型、adapter、配置、表、迁移、retention worker 或真实数据调用。

B23-XP 把可采用的首期 profile 收窄为 B23-XI typed identifiers、登记名称、可选 legal jurisdiction、`ACTIVE/INACTIVE/UNKNOWN` 主体状态、带五种明确 kind 之一的可选成立相关日期、单一注册地址和受控 observation metadata。跨来源没有共同值域的 organization/legal-form 类型被移出首期；人员、联系方式、关系、风险、报告、provider ID/URL 和任意扩展 map 继续在私有 adapter 白名单化时丢弃。

source kind 固定为 official registry、open reference index、commercial aggregator 和 dev/test-only synthetic fixture 四种形态。每个 profile 必须由版本化 source binding/policy/attribution 目录产生正 TTL，`expiresAt` 硬上限为 `observedAt + 5 分钟`。有效模式由核心、adapter、部署政策和 attribution/保留执行能力取最严交集：缺材料为 `NOT_READY`，只可临时展示为 `EPHEMERAL_ONLY`，完整允许逐字段持久化才是 `ADOPT_SELECTED_FACTS`。商业来源缺少真实合同，因此保持默认 `NOT_READY`。

B05 落点不允许自由字段猜测：登记名称固定写 `customer_name`；legal jurisdiction 可显式映射到兼容 custom `TEXT/SELECT`；entity status 只能进入具有精确三选项的 custom `SELECT`，绝不能覆盖 B06 `status/deal_status/locked`；带 kind 的日期只进入声明接受该 kind 的 custom `DATE`；注册地址进入内置 `addresses` 并按绑定槽位合并，不能压成当前字符串型 custom `ADDRESS` 或替换全部地址。identity、standing、来源和时效只进入 XI 关联/provenance。

每次采用与 Customer/XI 关联同事务追加不可改写的 provenance facts，只记录 tenant/Customer/actor、受治理来源和策略版本、观测/采用/deletion deadline、Customer 前后版本、canonical fact 和目标字段/address slot；deadline 只可因更严政策提前，不可延后。provenance 不复制 adopted value、raw profile 或普通 digest。未择取候选/profile 默认不缓存、不落 MySQL，observation 到期只要求重新查询，不自动删除已经人工采用的 B06 值。若来源要求当前 B06 无法执行的持续 attribution、物理到期、终止清除或证据保存，则不能宣告持久化 ready。

B23-XP 为 B23-XF 冻结了 date/source 映射、B05 负向落点、地址槽位、field-policy drift、usage mode、attribution context、deadline/backup sweep、最小 provenance、双 tenant 隔离和敏感值泄漏哨兵。B23-XF 已执行其中与 B23a 只读边界相交的 profile、policy、quota、error、泄漏和 tenant oracle；B23-O/2 又关闭 GLEIF category 缺失时的资格设计并定义真实 adapter conformance。B05 落点、地址槽位、provenance、retention、Customer/MySQL 写事务、真实 build evidence 和商业合同仍没有实现，因此 Customer 采用链路继续处于实现前阶段。

## 18. B23a 企业数据查询内核实现

> 实现日期：2026-07-19
>
> XP 对齐日期：2026-07-20
>
> 范围：仅人工触发、tenant 绑定的 outbound read；不实现预览、择取、刷新写入、身份占用或 durable provenance。

B23a 新增独立 `enterprise-data:read` 能力与 `/api/v1/enterprise-data` 三个端点：安全 capability、按登记名称的一页查询、按规范登记标识的精确查询。名称裁剪后为 1..200 Unicode 码点并必须携带受治理 legal jurisdiction；核心候选上限固定 20，adapter 可以进一步收窄。精确目录首期只实现 `CN_UNIFIED_SOCIAL_CREDIT_CODE/CN_USCC` 与 `GLOBAL_LEI_SYSTEM/LEI`；USCC 除字符、长度和 checksum 外还校验登记管理部门/机构类别目录及第 3–8 位六位行政区划数字。登记标识对象的默认字符串输出固定脱敏，不会把完整值带入模板日志。

公共观测没有任意 map，只包含一个 primary identity、完整 standing 一致的去重 identity 集合、可选 legal jurisdiction、最多 200 Unicode 码点的登记名称、主体状态、五类可选 formation date、单一注册地址、`sourceDeclaredPartial`、四类 source kind、可选 source update/revision、最长五分钟 expiry 与使用模式。organization/legal-form 类型已删除。adapter 不再提供 attribution、usage permission、`observedAt` 或 `expiresAt`；独立 `EnterpriseDataReadPolicyResolver` 按 tenant 产生受控 source kind、正 TTL 和分 `SEARCH_RESULT`/`DETAIL` 上下文的 attribution，外部调用完成后由核心 clock 产生观测时间和到期时间。核心合并技术 capability、部署政策与自身硬边界，B23a 最高只发布 `EPHEMERAL_ONLY`，缺少政策或 attribution readiness 时为 `NOT_READY`。

活动成员与能力在任何 adapter 调用前校验；tenant 部署政策也先于 adapter capability 解析，政策缺失或禁用时不会触达数据源。Capability 和外部查询在 `ServiceOperation.prepare` 中完成，`QueryServiceTemplate.execute` 只投影已验证结果，因此不会持有数据库事务等待外部网络。端口只接收派生的 `TenantId` 与 typed selector，普通请求不能选择 provider、connector、账号、endpoint、套餐或凭证。`NO_RESULT`、`LIMITED`、`UNAVAILABLE`、`UNSUPPORTED` 映射为稳定应用错误；provider 原始状态、body 和消息不进入公共错误。

无私有实现时 `DisabledEnterpriseDataSource` 与 `DisabledEnterpriseDataReadPolicyResolver` 返回 `NOT_READY` 并失败关闭。`dev`/`test` 可通过 `atom.enterprise-data.synthetic.enabled=true` 使用成对的虚构 source/policy fixture；`prod` 明确关闭。B23a 不创建迁移，不缓存或持久化搜索/详情，不生成 Activity/事件/报表数据，不写 Customer，也不声称 Companies House、GLEIF 或商业来源已经具备生产许可、组织资格、attribution、额度或 schema conformance；B23-O/2 只定义未来证明方式，不改变该事实。

### B23-CH Companies House identity 扩展

B23-CH 在不增加 source I/O 或持久化的前提下把 `UK_COMPANIES_HOUSE/GB_CH_NUMBER` 加入同一 identity/API/VO 目录。规范化只裁剪边界空白并逐字符转换 ASCII 小写字母；结果必须恰好八个 ASCII 字母/数字，纯数字类别保留全部前导零，带前缀类别必须命中 v2026-07-20 pinned 显式目录。内部空白、标点、Unicode lookalike、未知前缀、七/九位值和 scope/scheme 混配均失败关闭。现有字符串型请求与 VO 继续只传稳定 catalog 名称，不引入 provider DTO。

该扩展仅让未来 adapter 可以安全声明 CH identifier type；后续 B23-RA 已在 test scope 补齐 GLEIF `RA000585/586/587` 到号码类别的离线映射。Companies House HTTP adapter、资格 mapper、runtime RA catalog/digest、共享/tenant admission、credential、部署 policy 和 current-build conformance 仍未实现；生产组合继续由 disabled source/policy 返回 `NOT_READY`。

## 19. B23-XF 合成组合 fixture 闭合

> 闭合日期：2026-07-20
>
> 完整双 tenant 世界、场景、checkpoint 与通过条件：[B23-XF 企业数据合成组合 fixture](docs/B23-XF-composed-fixture.md)
>
> 范围：联合执行 XI/XP 与 B23a 已实现的只读接缝；不实现或宣称 Customer 身份占用、采用事务、provenance、真实 provider adapter 或生产 readiness。

`B23-XF/1` 在同一 test-private adapter 进程内固定一个 GLEIF-like 开放形态 tenant 和一个 USCC commercial-like 形态 tenant。两边只使用显眼合成 identity、名称、地址、revision、attribution 与禁止值 canary。开放形态证明 `ACTIVE` 主体与 `STALE` LEI standing 正交；商业形态的未知 native status/无独立 standing 均保守映射 `UNKNOWN`。人员、联系、关系和 provider locator 在 native mapper 边界丢弃，公共 VO 继续是无扩展 map 的 XP allowlist。

fixture 通过真实 `EnterpriseDataQueryServiceImpl` 组合 adapter capability、独立 tenant policy、核心 `EPHEMERAL_ONLY` 上限与核心时钟。它分别断言两 tenant 的 source kind、context attribution、TTL 和候选上限，并证明 C tenant 的 quota exhaustion 或 policy disable 不改变 O tenant。跨 binding identity type 在 source lookup 前以 unsupported 拒绝；no-result、limited、runtime/unavailable 与 raw error canary 分别收敛为稳定 `304`、`305`、`306`、`307` 分类且不泄漏 provider 文本。

本次可执行 fixture 只覆盖 B23a 能到达的查询 identity/standing 和 XP read-policy oracle。XI 的 Customer 一对多/反向唯一、软删除释放和 exact binary MySQL 竞态，以及 XP 的字段 diff/address slot/采用 provenance/retention，仍须后续 command 与 Testcontainers fixture 证明。商业形态使用的测试 TTL、候选数与 attribution 不构成真实合同证据；真实商业 source 仍必须在材料缺失时报告 `NOT_READY`。

## 20. B23-OC 官方/开放源适配器离线契约骨架

> 启动日期：2026-07-20
>
> 完整 checkpoint 与接入方法：[B23-OC 官方/开放源适配器离线契约骨架](docs/B23-OC-official-source-contract.md)
>
> 范围：只建立 test-only adapter contract；不实现或启用真实 provider 网络调用。

B23-OC 在 `infra/external` test source 新增 `OfficialSourceEnterpriseDataContract`。未来真实 adapter test 通过 stub transport 为固定场景提供纯合成 native payload，继承同一套 capability、精确 identity、辖区名称搜索、候选上限、状态/standing 正交、未知状态失败关闭、partial/额外字段剥离、无结果和安全错误缩减检查。`LIMITED`、`UNSUPPORTED`、网络/解析/schema 与 identity mismatch 不得暴露 provider body、URL、账号、凭证或 native canary。

`OfficialSourceEnterpriseDataContractSkeletonTest` 使用纯内存 reference driver 执行全部 checkpoint。它没有 HTTP client、endpoint、credential、Spring annotation、生产配置或真实企业响应；`src/main` 和 `conf/application-prod.yml` 未新增任何 source，生产继续解析到 disabled adapter/policy。B23-CH 已补齐 Companies House identity scope/scheme 和号码类别校验，因此未来 offline lane 可使用该 typed identity；真实 lane 仍须闭合 RA mapper、legal-jurisdiction/source qualification、adapter、policy/admission 与当前 build conformance，不能因为 scheme 或测试骨架存在而宣称 source ready。

## 21. B23-O-L1 GLEIF 离线 mapper lane

> 完成日期：2026-07-20
>
> 验收记录：[B23-O-L1](docs/acceptance/B23-O-L1.md)

B23-O-L1 在 `infra/external` test source 为 GLEIF/LEI 增加固定 stub transport、显眼合成 JSON:API-like payload、native mapper 和 `EnterpriseDataSource` contract driver，并由 `GleifEnterpriseDataContractTest` 继承 B23-OC。mapper 先执行五类组织资格 allowlist，再映射完整校验 LEI、法律辖区、legal name/address、`EntityCreationDate=ESTABLISHED`、主体状态、独立 registration standing 和 `LastUpdateDate`；`SOLE_PROPRIETOR`、category 缺失/未知、malformed/schema/identity mismatch 均失败关闭。LOU、provider locator、人员、关系、renewal 和未来额外字段不进入公共 observation；该批次当时抑制 RA 第二身份，后续 B23-RA 已在同一 test lane 中补齐。

该 lane 对 capability 保持零 transport call，exact/search 各至多一次 stub call，无 retry/fallback，并证明 cross-tenant 在调用前失败。全部实现仍位于 test source；没有真实 JSON parser/HTTP transport、endpoint、credential、Spring Bean、source binding、readiness、admission、policy 或 live 数据。它只满足 B23-O/2 三层 conformance 的第一层，生产继续使用 disabled source/policy。

## 22. B23-RA GLEIF RA/EntityID 离线映射

> 完成日期：2026-07-20
>
> 验收记录：[B23-RA](docs/acceptance/B23-RA.md)

B23-RA 在 `infra/external` test source 新增独立 `GleifRegistrationAuthorityIdentifierMapper`，固定 `GLEIF-RA-1.8.1/B23-CH-2026-07-20`。`RA000585`、`RA000586`、`RA000587` 分别只接受 England and Wales、Northern Ireland、Scotland 的显式 Companies House 号码类别，之后仍复用 B23-CH `RegisteredIdentifierKey` validator；RA code 与辖区不进入最终 identity key，secondary standing 固定为 `UNKNOWN`。

未知 RA、缺失 EntityID、跨 RA 号码类别、未知前缀、错误长度和 Unicode lookalike 均不产生第二身份，也不借 authority 自由文本、URL、名称或地址猜测。已通过资格和 LEI 校验的主 profile 仍可返回；但 primary LEI 与 exact selector mismatch 即使伴随有效 RA/EntityID 也整体缩减为 `UNAVAILABLE`。所有实现与 fixture 只位于 test source，生产组合和 readiness 不变。

## 23. B23-O-L2 quota/readiness durable simulator

> 实现日期：2026-07-20
>
> 完整 fixture、场景与剩余门槛：[B23-O-L2 quota/readiness durable simulator](docs/B23-O-L2-durable-simulator.md)
>
> 范围：只执行 B23-O/2 第二层的 `O2-06/07/08/10`；不新增 runtime adapter、生产 admission 或 live evidence。

B23-O-L2 在 `infra/external` test source 固定两个 tenant、两个 adapter replica、一个独立 provider simulator、共享文件持久化 admission/readiness store 和可推进 UTC 时钟。两个 replica 的调用先交集当前 binding/build/policy/catalog/conformance snapshot，再原子消费共享 provider budget、tenant 公平 bucket 与 operation concurrency slot；名称搜索停止后仍保留 exact/control 正数额度。replica 重建会重新打开 store，无法重置窗口、cooldown 或 readiness evidence。

fixture 证明 capability 零 source-call，search/exact 每次最多一次 provider call；429 产生共享 cooldown，timeout/5xx 零 retry 且不 fallback，admission 故障先于 source I/O。readiness 取 adapter、核心五分钟、rights、conformance、catalog 的最早期限，observation 继续取核心、adapter 和 tenant policy 的最严 TTL；未来 checkedAt、超过五分钟的 adapter TTL、算术溢出、立即 revoke 和 binding/policy/catalog drift 均失败关闭且不跨 tenant。

本实现没有 HTTP/DNS/TLS/redirect/schema transport lane，不关闭 `O2-09`；没有 sink inventory/机器报告扩展，不单独关闭 `O2-11`；也没有私有 live runner，因此 `O2-12` 仍未完成。文件 store 与合成 quota 数字只属于测试，不构成生产存储、实际 GLEIF/Companies House ceiling、rights review 或正向 readiness evidence。
