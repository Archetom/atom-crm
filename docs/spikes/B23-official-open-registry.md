# B23-O/1 官方/开放登记模型只读 spike

> 调研日期：2026-07-19
>
> 代表模型：英国 Companies House Public Data API、GLEIF API / LEI-CDF 3.1
>
> 状态：只读调研完成；后续 B23-XI/B23-XP 已闭合 identity、profile 与 policy oracle，B23a 已实现公共端口，B23-XF 已执行开放 native shape 的合成查询 fixture，[B23-O/2](../B23-O-official-source-readiness.md) 又冻结组织资格、RA 映射、许可、quota、TTL 与真实 conformance oracle；仍未选择生产数据源、调用真实企业数据或实现真实 SDK/adapter

## 1. 结论

Companies House 与 GLEIF 都能提供 B23 所需的基础主体事实，但两者不是可互换的“企业库”：

- Companies House company number 是登记机关范围内的登记身份；LEI 是 ISO 17442 下的全球法律实体标识。LEI 记录可以携带本地登记机关和登记号，但 LEI 不能替代本地登记号，Companies House number 也不能跨登记机关使用。
- `legalJurisdiction` 不是身份命名空间。Companies House 还覆盖境外公司或 UK establishment；GLEIF 的 LEI 本身是全球方案并另带法律辖区。因此 B23 原候选 `(jurisdiction, registryScheme, registryValue)` 必须明确为“签发登记机关/方案/值”，把法律成立辖区作为独立事实。
- GLEIF 的 `EntityStatus` 与 `RegistrationStatus` 是两条正交状态轴。`LAPSED` 表示 LEI 未按期续期，不表示主体停止经营；`DUPLICATE`、`ANNULLED` 说明该 LEI 记录不应继续作为当前标识，也不直接证明主体失活。公共模型不能只保留一个无来源语义的 `status`。
- Companies House 自称 API 数据 live / real-time，但 company profile 没有统一的源更新时间；`ETag` 只能作为资源修订标记。GLEIF API 基于每日三次发布的 Golden Copy，记录另有 `LastUpdateDate` 与 `NextRenewalDate`。`observedAt`、源更新时间、源修订和本地可择取截止时间必须分别表达。
- Companies House Public Data API 需要应用 API key，默认 5 分钟 600 请求；GLEIF API 无业务账号或 API key，当前按每用户每分钟 60 请求限流。两者的外部额度都不能成为 B23 的公共分页契约。
- Companies House 公共登记数据可供公众访问并且 Companies House 不对公共登记信息的使用附加规则，但使用者仍负责数据保护、版权等适用法律；这不是对仓库代码或所有下游用途授予 MIT/OGL 数据许可。GLEIF LEI 数据明确按 CC0 提供，但条款仍禁止规避技术限制或造成 GLEIF 背书的误解。
- 两方错误体都不能进入核心。Companies House 搜索端点还会用 `404` 表达“无候选”，GLEIF 使用 JSON:API 且官方文档没有冻结一个可供核心依赖的完整错误枚举。B23 只能把 HTTP 和解析结果缩减为稳定、安全的本地错误类别。

B23-O 因此支持继续使用 provider-neutral adapter，但要求先修正身份、状态和时效模型；不能直接把任一方 DTO 当作规范 profile。

## 2. 六维对比

| 维度 | Companies House | GLEIF / LEI | B23 结论 |
| --- | --- | --- | --- |
| 身份 | 完整 company number；在 Companies House 公共登记范围内唯一，名称和地址变化不改变号码 | 全球唯一的 20 位字母数字 LEI；记录可选带本地 `RegistrationAuthorityID` 和 `RegistrationAuthorityEntityID` | `GB-CH` 与 `LEI` 是两个独立 scheme；允许同一候选同时携带多个有来源的登记身份，不做字符串猜测合并 |
| 状态 | `company_status` 同时包含 active、dissolved、administration、liquidation、open、registered 等登记或程序状态 | `EntityStatus` 描述主体；`RegistrationStatus` 描述 LEI 记录生命周期 | 规范主体状态与标识记录 standing 分开；未知枚举一律 `UNKNOWN` |
| 时效 | API 声明 live / real-time；profile 返回 `ETag`，但无统一 `lastUpdatedAt` | API 基于 Golden Copy；每日 02:00、10:00、18:00 UTC 更新；记录含最后更新和下次续期时间 | 核心生成 `observedAt`；源更新时间可选；`expiresAt` 是本地策略，不由宣传语或续期日代替 |
| 限额 | 默认 600 请求/5 分钟；alphabetical search 单页 1..100，advanced search 可请求 1..5000 | 当前 60 请求/分钟/用户；普通分页 1..200，默认分页提示为 15；模糊查询词最多 255 字符 | 核心固定名称查询最多 200 Unicode 码点、最多 20 个候选且首期不开放任意翻页、过滤或批量遍历 |
| 许可/权限 | API 必须使用应用 API key；公共登记可免费访问，使用者承担下游合规责任 | 无 API key 的免费公共访问；LEI/LE-RD 按 CC0 提供；不得规避技术限制或暗示背书 | 外部可访问性不授予 CRM authority；仍按 tenant 绑定 source policy，并只允许显式择取的组织字段 |
| 错误 | profile 明示 200/401/404；搜索端点可能以 404 表示无结果，参数错误可能是 400/422，超额为 429 | JSON:API；当前文档明确 60/分钟，但未冻结完整错误状态/错误体枚举 | 本地预校验后只公开 `NO_RESULT`、`LIMITED`、`UNAVAILABLE`、`UNSUPPORTED`、`EXPIRED`、`CONFLICT`；不透传 body、URL、账号或厂商消息 |

表中的外部上限只是 2026-07-19 官方文档所述能力，不是 B23 对调用者的承诺。真实适配器必须把额度、端点行为和许可作为可变 readiness / policy 输入。

## 3. Companies House 模型

### 3.1 查询与身份

首期只需要两条 Public Data API 读取能力：

- `GET /company/{companyNumber}`：按完整 company number 精确取得 company profile；
- `GET /search/companies`：只搜索公司，不使用会混入 officer 的 `/search`，也不暴露 advanced search 的任意筛选面。

company number 必须作为完整值保存。纯数字早期号码需要前置零补足至 8 位，其他类型存在字母前缀；不能先转数字、去前导零、统一小写或仅靠一个覆盖不全的通用正则“修复”。规范值使用 Companies House 返回的完整大写号码，scheme 暂记为 `GB-CH`。

该号码标识 Companies House 登记记录，不必然标识境外母体本身。profile 可以出现 overseas company、UK establishment、外部注册号和 originating registry；这些字段不能被拼成第二个权威身份，除非将来存在该登记机关的受治理 scheme 映射与验证规则。

Companies House 的 `jurisdiction` 值包含 England/Wales、Scotland、Northern Ireland、United Kingdom、EU 和 non-EU 等。它是被登记主体的辖区事实，不是 company number 的命名空间。因此公共身份至少要分开：

| 事实 | Companies House 映射 |
| --- | --- |
| `registryAuthority` | 固定的 Companies House 来源目录项 |
| `registryScheme` | `GB-CH`，最终代码名留给实现批次 |
| `registryValue` | 完整规范 company number |
| `legalJurisdiction` | 仅从已知 `jurisdiction` 枚举显式映射；未知或缺失保持缺失 |

搜索排名、`links.self`、URI、结果位置和 API key 都不是主体身份或 source revision。

### 3.2 状态

Companies House 当前 profile 枚举包括：`active`、`dissolved`、`liquidation`、`receivership`、`administration`、`voluntary-arrangement`、`converted-closed`、`insolvency-proceedings`、`registered`、`removed`、`closed`、`open`。

首期保守映射为：

| 原始值 | 规范主体状态 | 原因 |
| --- | --- | --- |
| `active` | `ACTIVE` | 源明确声明 active |
| `dissolved`、`converted-closed`、`removed`、`closed` | `INACTIVE` | 源明确表达已解散、转换/关闭或移除 |
| `liquidation`、`receivership`、`administration`、`voluntary-arrangement`、`insolvency-proceedings` | `UNKNOWN` | 属于法律或破产程序，不能安全推断主体已经停止登记或经营 |
| `registered`、`open` | `UNKNOWN` | 枚举只给出 Registered/Open，未提供足够语义证明等同于 B23 `ACTIVE` |
| 缺失或未来新增值 | `UNKNOWN` | 失败关闭，禁止默认 active |

`company_status_detail`、insolvency flags、accounts overdue、registered office dispute、annotation 等不能覆盖粗粒度主体状态，也不进入首期公共 profile。尤其 `active-proposal-to-strike-off` 不能预先映射为 inactive。

### 3.3 时效与修订

Companies House 将 API 描述为 live / real-time，但这只描述访问登记数据的更新方式，不证明内容已被全面核实。官方服务说明仍提示：查询是登记簿摘录，可能有偶发不准确，且 Companies House 不对错误或遗漏后果负责。

profile 的 `ETag` 可以作为一次资源表示的 source revision：

- `observedAt` 由适配器成功解析响应时使用核心时钟生成；
- `sourceUpdatedAt` 缺失，不能用 HTTP `Date`、查询时间、成立日期或 filing 日期冒充；
- `sourceRevision` 可在短期观测中保存 `ETag`，但不得向普通客户端暴露或写日志；
- durable provenance 是否保存 `ETag` 由后续最小审计设计决定，不能把它解释为内容真实时间；
- UI 预览引用的本地 `expiresAt` 建议最多为 `observedAt + 5 分钟`，择取仍按 B23 总规划重新精确取数和比较规范事实。

若响应带 `partial_data_available`，适配器必须标记观测不完整。缺失字段保持缺失，不自动跟随第三方来源、网页或文件补齐。

### 3.4 认证、额度与许可

Public Data API 读取要求应用级 API key，并使用 HTTP Basic username 传输，password 留空。该 key 属于私有适配器配置：普通请求不能提交、选择、探测或看到它；API `401` 也不能变成“当前 CRM 用户未登录”。

默认限额为 5 分钟 600 请求，持续超限或绕过限额可能被禁用。适配器必须在 provider 调用前执行本地 admission，给重试保留预算；429 只影响 B23 数据源，不阻塞 B06 普通客户操作。

Companies House 明确公共登记数据免费可访问，并声明不对公共登记信息的使用附加规则；同时要求使用者自行遵守数据保护、版权和其他法律。该边界带来以下部署结论：

- 不能把网页页脚的 Crown copyright 或一般 GOV.UK OGL 声明自动当作全部 register data 的许可证；
- B23 可将经审查的基础组织事实配置为 `ADOPT_SELECTED_FACTS`，但不得把同一结论扩展到 officer、PSC、文件、签名、个人地址或搜索历史；
- 首期只调用 company profile/company search，丢弃人员、filing 文档、风险和程序详情；
- 来源展示使用受控文本 `Companies House` 即可，不返回任意 provider URL 或造成官方认证/背书印象。

### 3.5 错误缩减

| 外部结果 | 私有适配器解释 | B23 公共错误 |
| --- | --- | --- |
| 精确 profile `404` | 完整号码当前无资源 | `NO_RESULT` |
| company search 空列表或该搜索端点的 `404` | 无候选，不是基础设施故障 | 成功的空候选列表 |
| `400` / `422` | 若本地 typed 校验本应阻止，则是 adapter 契约漂移或实现错误 | `UNAVAILABLE`；不能透传参数或 body |
| `401` | key 无效、缺失或账号配置不可用 | `UNAVAILABLE`，并记录无敏感值的内部配置类别 |
| `429` | provider 配额耗尽 | `LIMITED`；遵守窗口，不在一次人工请求内循环重试 |
| `5xx`、超时、TLS/解析失败 | 数据源或适配器不可用 | `UNAVAILABLE` |
| 200 但 identity 不匹配、必填名称缺失或 body 超界 | 不可信/不兼容响应 | `UNAVAILABLE`，不得产生观测或 Customer 写入 |

## 4. GLEIF / LEI 模型

### 4.1 查询与身份

GLEIF API 是基于 JSON:API 的开放读取接口。首期只需要：

- `GET /api/v1/lei-records/{lei}`：按 LEI 精确读取；
- `GET /api/v1/lei-records` 的固定 legal-name / legal-jurisdiction filter，或受控 fuzzy completion：名称候选；
- 不开放 API 的任意 field、operator、ownership、ISIN/BIC 映射、Level 2 关系或 modification history。

LEI 是 ISO 17442 下全球唯一的 20 位字母数字标识，scheme 暂记为 `LEI`。LEI 记录另可带：

- `LegalJurisdiction`：ISO 3166-1/3166-2 法律成立辖区；
- `RegistrationAuthorityID`：GLEIF 维护的登记机关代码；
- `RegistrationAuthorityEntityID`：该登记机关中的本地实体标识，可缺失；
- `ValidationAuthority`：LEI issuer 用于验证资料的登记机关，不能与主体身份自动互换。

当且仅当 B23 的受治理映射目录认识 `RegistrationAuthorityID`，并能按对应 scheme 验证 `RegistrationAuthorityEntityID`，适配器才可把它作为额外 `RegisteredIdentifier` 返回。例如一个 LEI 观测可同时携带 LEI 和本地登记身份，但两者仍各自保留 scheme、authority 和来源。未知 RA code、自由文本 `OtherRegistrationAuthorityID` 或 validation authority 不生成规范身份。

GLEIF 的目标设计是一个 LEI 只代表一个实体，但 CDF 明确定义 `DUPLICATE`、`ANNULLED` 和 successor，使用条款也不保证某实体只有一个已发布 LEI。B23 因此不能在应用层假定数据永远满足现实世界一对一；标识 standing 与最终数据库仲裁仍必需。

### 4.2 两条状态轴

`EntityStatus` 才能映射 B23 主体状态：

| `EntityStatus` | 规范主体状态 |
| --- | --- |
| `ACTIVE` | `ACTIVE` |
| `INACTIVE` | `INACTIVE` |
| `NULL`、缺失、未来新增值 | `UNKNOWN` |

`RegistrationStatus` 描述 LEI registration，必须进入独立的标识/记录 standing，而不是覆盖主体状态：

| `RegistrationStatus` | 建议 standing | 允许作为当前 LEI 精确身份择取 |
| --- | --- | --- |
| `ISSUED` | `CURRENT` | 是 |
| `LAPSED` | `STALE` | 是，但必须展示/审计 stale；主体状态仍单独读取 |
| `PENDING_TRANSFER`、`PENDING_ARCHIVAL` | `TRANSITIONING` | 是，保留当前 LEI，不能按 LOU 变化创建新身份 |
| `DUPLICATE` | `SUPERSEDED` | 否；如有 successor 只用于提示重新选择，不自动替换 Customer |
| `ANNULLED` | `INVALID` | 否 |
| `RETIRED` | `RETIRED` | 仅历史显示，首期不允许新关联 |
| `PENDING_VALIDATION`、`CANCELLED`、`TRANSFERRED`、已废弃 `MERGED` | `UNKNOWN` 或非当前 | 首期失败关闭，不允许新关联 |
| 缺失或未来新增值 | `UNKNOWN` | 否 |

standing 的最终 Java 名称和值留给实现规划，但“主体状态”和“LEI 记录状态”分离是 B23-O 的冻结语义。`LAPSED + EntityStatus.ACTIVE` 必须成为契约 fixture，确保不会把未续期 LEI 误映射为已注销企业。

### 4.3 时效与质量

GLEIF API 基于 Golden Copy。GLEIF 当前每日在 02:00、10:00、18:00 UTC 发布三组 Golden Copy；LEI issuer 的源资料和实体续期仍可能早于 API 可见时间。

规范时效映射为：

| 规范事实 | GLEIF 映射 |
| --- | --- |
| `observedAt` | 适配器成功解析时的核心时钟 |
| `sourceUpdatedAt` | `Registration.LastUpdateDate` |
| `sourceRevision` | 首期缺失；`LastUpdateDate` 是业务更新时间，不冒充官方 revision token |
| `renewalDueAt` / standing 输入 | `NextRenewalDate` 与 `RegistrationStatus`；不能直接当 `expiresAt` |
| `expiresAt` | 本地策略，建议最多 `observedAt + 5 分钟`；择取前重新取数 |

`ValidationSources` 和 policy conformity flag 可以作为 adapter 的质量/资格输入，但不把 `FULLY_CORROBORATED` 翻译为 B23 的“认证通过”，也不允许自动生成风险或准入结论。GLEIF 条款明确数据按现状提供，且不保证准确、正确、最新或某实体只有一个 LEI。

### 4.4 额度、许可与组织边界

GLEIF API 当前不要求 API key，文档示例只要求 JSON:API `Accept`；服务免费，限额为每用户每分钟 60 请求。普通分页大小为 1..200，名称模糊查询词最多 255 字符。这些能力不能被上推为批量遍历接口。

LEI 和 LE-RD 明确按 CC0 提供，允许查找、下载和再利用。Terms of Use 同时要求：

- 只通过 GLEIF 提供的 access service 访问，不规避技术限制；
- 不声称 GLEIF 支持、授权或与下游产品存在特殊关系；
- 接受 access service 可暂停、修改或不可用；
- 接受数据和服务按现状提供。

CC0 允许的范围大于 B23 首期需要，不能反向扩大核心 allowlist。Level 2 关系、历史修改、BIC/ISIN 映射、全部数据文件和任意导出仍不纳入。

LEI-CDF 的 `EntityCategory` 包含 `SOLE_PROPRIETOR`，而该类 legal name/address 可能直接指向自然人；`EntityCategory` 又是可选字段。为满足 B23 首期不处理人员姓名和自然人资料的边界：

- 明确 `SOLE_PROPRIETOR` 必须拒绝；
- category 缺失或未知时，不能仅凭“拥有 LEI”断言是非自然人；[B23-O/2](../B23-O-official-source-readiness.md) 后来把规则冻结为直接拒绝，真实 GLEIF adapter 仍须通过对应 conformance；
- 资格不能证明时只返回不支持，不把名称或地址带入普通 API、日志或缓存。

### 4.5 错误缩减

GLEIF 文档规定 JSON:API response、查询/分页规则和额度，但没有冻结一个覆盖所有 endpoint 的稳定错误枚举或错误 body。B23 不依赖 `errors[].detail`、代理 HTML、内部 code 或消息文本。

| 外部结果 | 私有适配器解释 | B23 公共错误 |
| --- | --- | --- |
| 精确 LEI 无资源 | 无结果 | `NO_RESULT` |
| 搜索 200 + 空 `data` | 无候选 | 成功的空候选列表，与 Companies House 统一 |
| 本地不支持的 scheme、辖区、filter 或 standing | 调用前拒绝 | `UNSUPPORTED` |
| `400` / `422` | typed request 漂移或 adapter 错误 | `UNAVAILABLE` |
| `429` | provider 配额耗尽 | `LIMITED` |
| `5xx`、超时、服务中断 | 数据源不可用 | `UNAVAILABLE` |
| 非 JSON:API、超界集合、identity 不匹配、未知必填结构 | 不兼容响应 | `UNAVAILABLE`，不保存观测 |

如果未来出现认证/禁止类 `401`/`403`，它代表 access service 或部署网络政策变化，不代表当前 CRM caller 无权；公共结果仍是 `UNAVAILABLE`，内部 readiness 则进入安全配置类别。

## 5. 规范 profile 修正

B23 原规划的基础 profile 需要按本 spike 修正为以下语义，类型名仍留给实现批次：

| 规范项 | 规则 |
| --- | --- |
| `identifiers` | 至少一个有 scheme 的登记标识；每项包含受治理 registry authority/scheme/value。主查询标识与额外映射标识有明确来源，不用名称/地址自动合并 |
| `legalJurisdiction` | 独立可选事实，不参与决定标识命名空间；只接收已知 ISO/受治理枚举映射 |
| `registeredName` | Companies House `company_name` 或 GLEIF `LegalName`；不使用 trading name、previous name、transliteration 或 alias 补齐 |
| `entityStatus` | 只表示主体粗粒度 `ACTIVE`、`INACTIVE`、`UNKNOWN` |
| `identifierStanding` | 可选的标识记录质量/生命周期；GLEIF 必需，Companies House 首期可缺失。不能用于风险评分 |
| `organizationType` | 可选 typed source mapping；未知枚举缺失，不返回供应商自由文本 |
| 日期 | Companies House `date_of_creation` 随 company type 可能表示 incorporated/registered/formed/opened；GLEIF `EntityCreationDate` 表示首次建立。最终 profile 必须带 `dateKind`，否则首期省略跨源成立日期 |
| `registeredAddress` | Companies House registered office 或 GLEIF legal address；不使用 service/headquarters/other address 回填，不宣称营业或邮寄地址 |
| 时效 | 核心 `observedAt` 必填，`sourceUpdatedAt`、`sourceRevision` 可选，`expiresAt` 必填且由 source policy 产生 |
| 来源 | 至少区分 `OFFICIAL_REGISTRY` 与 `OPEN_REFERENCE_INDEX`；显示归属是受控目录值，不是 provider response 文本 |

规范 profile 不携带 `rawStatus`、任意扩展 map、provider URL、搜索分数、LOU、API key、error body、officer/PSC、sole proprietor、ownership、filing、insolvency detail、annotation、BIC/ISIN 或历史修改。

## 6. 查询、额度与重试契约

B23 首期共同能力固定为：

1. 一个 scheme-specific exact identifier lookup；
2. 一个登记名称查询，可选一个受治理 `legalJurisdiction`；
3. 查询词裁剪后 1..200 Unicode 码点；
4. 每次最多返回 20 个候选，不返回 provider total count，不开放 provider page token；
5. 禁止任意 filter map、advanced search、全量下载、关系遍历和跨页枚举；
6. exact lookup 最多产生一个候选；如果 source 返回 identity 不一致则响应无效；
7. 429 不重试；超时/连接失败最多进行 source policy 明确允许且仍在 deadline/额度内的一次退避重试，名称查询默认不重试；
8. 适配器并发和窗口预算始终小于 provider 上限，并为择取时的精确重取保留容量。

本地 20 候选与 200 码点限制是 B23 安全契约；Companies House 的 100/5000 和 GLEIF 的 200/255 只是外部最大值，不能动态扩大公共 API。

## 7. 许可与使用模式矩阵

| 来源 | source kind | 展示基础组织事实 | 择取选中事实 | 缓存搜索结果 | 持久化 raw profile | 再导出数据集 |
| --- | --- | --- | --- | --- | --- | --- |
| Companies House Public Data API | `OFFICIAL_REGISTRY` | 经部署政策允许 | 经数据保护/版权评审后可配置允许 | 默认禁止；若允许必须 tenant/account 隔离且短 TTL | 禁止 | 不属于 B23 |
| GLEIF API | `OPEN_REFERENCE_INDEX` | CC0 下允许，但仍显示来源且不暗示背书 | 允许选中基础事实 | 许可上可行，核心仍默认不缓存未择取 profile | 禁止 | 不属于 B23；使用官方文件服务是独立批次 |

两方都不能因为“public/open”跳过 `AuthenticatedCaller`、tenant source policy、B05 字段访问、B06 数据范围、expected version 或 provenance。`ADOPT_SELECTED_FACTS` 是部署政策与 adapter capability 的交集，不是来源自动授予的 CRM 权限。

## 8. 统一错误语义

最终错误 code 留给实现批次，但稳定场景冻结如下：

| 规范场景 | 触发条件 | 是否可安全重试 | 对外信息 |
| --- | --- | --- | --- |
| `NO_RESULT` | 精确标识不存在 | 修改 selector 后可重试 | 不区分从未存在、已移除或 source 未覆盖；名称搜索无候选返回成功空列表 |
| `LIMITED` | provider 429 或本地 adapter 配额 admission 拒绝 | 到安全重试时间后可重试 | 不返回剩余额度、账号或原始 header；可选统一 `retryAfter` 上限值 |
| `UNAVAILABLE` | credential/readiness、网络、5xx、解析、schema、identity mismatch、policy material 缺失 | 仅安全类别可重试 | 不区分 key、账号、endpoint、代理或 body |
| `UNSUPPORTED` | scheme、辖区、查询模式、entity category、record standing 或 use mode 不被 adapter/policy 支持 | 相同请求不可重试 | 返回固定安全消息 |
| `EXPIRED` | 本地 observation reference 超过 `expiresAt` 或绑定版本不再有效 | 重新查询/预览 | 不透露缓存或 source revision |
| `CONFLICT` | 重新取数后规范事实改变，或 Customer/字段/策略/映射版本变化 | 重新预览 | 不返回隐藏当前值或 raw diff |

调用者认证、tenant membership、入口 capability 和 B05/B06 字段权限继续使用已有安全错误；provider `401` 绝不能伪装为 caller `UNAUTHENTICATED`，provider `403` 也不能伪装为 caller `FORBIDDEN`。

## 9. 合成契约 fixture

B23-O 为后续官方/开放 adapter fixture 固定以下最小案例；所有名称、号码、LEI、地址和 ETag 必须是合成值：

1. Companies House exact lookup 保留完整 8 位数字/字母前缀，拒绝截零、大小写漂移和响应 identity 不一致；
2. overseas/UK establishment 记录不把 external registration number 自动提升为当前 scheme 身份；
3. `active`、四个明确终止值、五个程序状态、`registered/open`、未知新增状态分别按上表映射；
4. `active + active-proposal-to-strike-off` 仍不伪造成 inactive；
5. `ETag` 进入短期 revision，缺少源更新时间保持缺失，`partial_data_available` 不回填字段；
6. Companies House 401/404-search/404-exact/422/429/5xx/超时/HTML body 映射到稳定安全场景；
7. GLEIF LEI 与本地 registration authority identity 同时存在但不互相替代；未知 RA code 不生成第二身份；
8. `EntityStatus.ACTIVE + RegistrationStatus.LAPSED` 映射为主体 active、标识 stale；
9. `INACTIVE + RETIRED`、`DUPLICATE + successor`、`ANNULLED`、pending/未知枚举分别执行 standing 与择取门；
10. GLEIF `LastUpdateDate` 是 source update，`NextRenewalDate` 不成为 observation expiry，未知额外 JSON 字段被丢弃；
11. `SOLE_PROPRIETOR` 和无法证明组织资格的记录在返回名称/地址前失败关闭；
12. GLEIF 空 data、429、5xx、超时、非 JSON:API、超界分页和 identity mismatch 映射为稳定场景；
13. 两方名称查询都只发出固定 200 码点、20 候选的一页请求，不暴露任意 filter/page/total；
14. 日志、trace、metric、事件、Activity 和错误不含查询词、名称、登记号、LEI、地址、ETag、URL、key、raw body 或 provider message；
15. 预览引用 5 分钟后过期；有效引用择取时重新取数，状态、名称、地址或 identity standing 改变时返回 conflict，不部分写 Customer。

## 10. 对 B23 的闭合与剩余阻断项

B23-O/1 已闭合：

- 官方本地登记身份与全球 LEI 身份不能统一成单一“企业 ID”；
- identity scope 与 legal jurisdiction 必须分开；
- 主体状态与标识记录 standing 必须分开；
- Companies House/GLEIF 的时效、额度、许可、认证和错误映射；
- 官方/开放 adapter 的首期查询面、字段 allowlist 与合成 fixture。

B23-XI 后来依据本 spike 与 B23-C 完成[登记身份、Customer 关联与 MySQL 唯一仲裁](../B23-XI-registered-identity.md)：一个未删除 Customer 可关联多个身份，同一 tenant 的精确身份键反向最多占用一个未删除 Customer，Customer 删除在同一事务释放当前占用并保留 provenance。B23-XP 又冻结 profile/policy/provenance，B23-XF 已执行查询侧跨模型合成组合。[B23-O/2](../B23-O-official-source-readiness.md) 进一步关闭本文件遗留的 GLEIF category 资格门、Companies House RA mapping、许可、quota、TTL 和真实 conformance 设计。B23 采用链路仍不能拆实现子批次，直到以下其余问题共同闭合：

1. 为商业来源补齐公开资料无法证明的完整登记状态、持久化许可、attribution、TTL、地域、删除和数值并发上限；B23-XF 的测试数字不得替代合同证据；
2. 实现 B23-XP 已冻结的 B05 字段 diff/地址槽位、Customer/XI 采用事务、最小 durable provenance 与 retention；
3. 实现 B23-O/2 已冻结的 `UK_COMPANIES_HOUSE/GB_CH_NUMBER` 目录、真实官方/开放 adapter、共享 admission 与当前 build 的私有 live conformance；
4. B23-XF 已用开放与商业 native shape 合成 fixture 证明 B23a 查询映射；B23-XI Customer 唯一键和采用 rollback 仍需实现级 MySQL fixture，且不能使用真实企业响应。

## 11. 官方依据

### Companies House

- [Developer Hub](https://developer.company-information.service.gov.uk/)：机构职责、Public Data API 的 live / real-time 描述。
- [Company profile](https://developer-specs.company-information.service.gov.uk/companies-house-public-data-api/reference/company-profile/company-profile)：精确路径、API key、200/401/404 和 `ETag`。
- [Company profile resource](https://developer-specs.company-information.service.gov.uk/companies-house-public-data-api/resources/companyprofile?v=latest)：company number、name、status、jurisdiction、type、creation/cessation date、registered office 与 partial data 字段。
- [Search companies](https://developer-specs.company-information.service.gov.uk/companies-house-public-data-api/reference/search/search-companies)：company-only 名称查询与 API key。
- [Alphabetical search](https://developer-specs.company-information.service.gov.uk/companies-house-public-data-api/reference/search/search-companies-alphabetically)：1..100 单页边界及 404/422。
- [Advanced company search](https://developer-specs.company-information.service.gov.uk/companies-house-public-data-api/reference/search/advanced-company-search)：1..5000 外部能力；本 spike 明确不向核心开放。
- [Authentication](https://developer.company-information.service.gov.uk/authentication)：API key 的 HTTP Basic 传输方式。
- [Developer guidelines](https://developer.company-information.service.gov.uk/developer-guidelines/)：600 请求/5 分钟、429 与禁止绕过限额。
- [Companies House data products](https://www.gov.uk/guidance/companies-house-data-products)：公共登记信息使用与下游法律责任说明。
- [Searching the Companies House register](https://www.gov.uk/guidance/searching-the-companies-house-register)：实时更新、登记簿摘录、准确性与免责声明。
- [Company number lookup guidance](https://find-and-update.company-information.service.gov.uk/company-lookup/search)：完整 8 位和前导零要求。
- [HMRC company registration number guidance](https://www.gov.uk/hmrc-internal-manuals/cotax-manual/com40011)：company registration number 的唯一、不随名称或 registered office 变化语义。
- [Companies House API enumerations](https://github.com/companieshouse/api-enumerations/blob/master/constants.yml)：company status/type/jurisdiction 当前枚举目录。

### GLEIF

- [GLEIF API](https://www.gleif.org/en/lei-data/gleif-api/) 与[完整 API 文档](https://documenter.getpostman.com/view/7679680/SVYrrxuU)：JSON:API、查询/分页、1..200 page size、255 字符 fuzzy query、60 请求/分钟和 Golden Copy 数据源。
- [LEI-CDF 3.1](https://www.gleif.org/en/lei-data/access-and-use-lei-data/level-1-data-lei-cdf-3-1-format)：LEI、登记机关 identity、legal jurisdiction、EntityStatus、RegistrationStatus、更新时间、续期和 validation source 定义。
- [GLEIF Golden Copy](https://www.gleif.org/en/lei-data/gleif-golden-copy)：每日三次发布、delta 和完整性语义。
- [Level 1: Who is Who](https://www.gleif.org/en/lei-data/access-and-use-lei-data/level-1-data-who-is-who)：官方名称、登记地址、成立国家和时间等 LEI reference data 范围。
- [LEI Data Terms of Use](https://www.gleif.org/en/meta/lei-data-terms-of-use)：CC0、免费 access service、技术限制、非背书和按现状提供条款。
- [GLEIF Open Data](https://www.gleif.org/en/about/open-data)：Global LEI Index 与 CC0 开放数据定位。
- [The Legal Entity Identifier](https://www.gleif.org/en/organizational-identity/lei-vlei/the-legal-entity-identifier-lei)：ISO 17442 和 20 位全球 LEI 身份定位。
