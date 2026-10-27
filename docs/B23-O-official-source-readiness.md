# B23-O/2 官方/开放企业源 readiness 与 conformance 契约

> 决策日期：2026-07-20
>
> 来源 profile：Companies House Public Data API、GLEIF API / LEI-CDF 3.1
>
> 状态：设计与 conformance oracle 已闭合；B23-O-L1 与 B23-RA 已实现 test-scope GLEIF 合成 mapper 及三条 RA/EntityID 离线映射，B23-O-L2 已执行第二层 `O2-06/07/08/10` 合成 durable fixture，B23-O-L3 已执行第二层 `O2-09/11` 离线 HTTP/security fixture；但没有真实 adapter、credential、生产 source binding 或 live `O2-12` evidence，因此仓库内生产 readiness 仍为 `NOT_READY`
>
> 输入：[B23 总规划](../batch-B23.md)、[B23-O/1 只读模型调研](spikes/B23-official-open-registry.md)、[B23-XI 登记身份设计](B23-XI-registered-identity.md)、[B23-XP profile/policy 设计](B23-XP-profile-provenance.md)、[B23-XF 合成组合 fixture](B23-XF-composed-fixture.md)与已实现 B23a 只读内核

## 1. 闭合结论

B23-O/1 已证明 Companies House 与 GLEIF 不能被压成同一个“企业库”，但把 GLEIF 非自然人资格、GLEIF RA code 到本地登记 scheme 的映射，以及真实来源 readiness/conformance 留作阻断项。本轮固定这些缺口，不改变 B23a 只读、不缓存、不持久化、不写 Customer 的实现边界。

冻结结论如下：

- readiness 是 tenant source binding、部署政策、来源权利、组织资格目录、登记机关目录、额度 admission、短期 snapshot 和当前 adapter build conformance 的最严交集；“API 能连通”不是 readiness；
- Companies House 与 GLEIF 分别使用 `CH_PUBLIC_DATA_V1` 和 `GLEIF_LEI_API_V1` source profile，不能在失败时静默互相 fallback，也不能由请求选择来源、账号或 credential；
- Companies House 只有命中版本化 `company_type` 组织 allowlist 的 company-only 记录才合格；缺失、`other`、未来未知 type 或非 company endpoint 输入失败关闭；
- GLEIF 只有 `GENERAL`、`FUND`、`BRANCH`、`RESIDENT_GOVERNMENT_ENTITY` 或 `INTERNATIONAL_ORGANIZATION` 才通过组织资格。`SOLE_PROPRIETOR`、category 缺失或未来未知值均在名称、地址和其他 profile 值离开 adapter 前拒绝；
- GLEIF RA List 只是“哪个登记机关提供 EntityID”的目录，不自动赋予本地号码的 scheme 语义。首批只冻结 Companies House 的 `RA000585/RA000586/RA000587` 映射；未知 RA、历史自由文本 authority、validation authority 或未通过 scheme validator 的 EntityID 不生成第二身份；
- B23-CH 已把 `UK_COMPANIES_HOUSE/GB_CH_NUMBER` 加入 domain catalog、公共请求解析和 VO 投影，并以显式八位号码类别目录取代宽泛正则；B23-RA 已在 test scope 证明独立 RA-scoped mapper，真实 GLEIF adapter 仍必须提供同 build 的 artifact digest、schema/HTTP 与 live conformance，Companies House profile 在 adapter/policy/conformance 缺失时继续保持 `NOT_READY`；
- Companies House 的公开可访问说明与 GLEIF 的 CC0 都只形成“可配置资格”，仍要求版本化部署 policy。当前 B23a 的核心硬上限始终是 `EPHEMERAL_ONLY`；
- Companies House 官方上限 `600 requests / 5 minutes` 与 GLEIF `60 requests / minute / user` 固定为 provider ceiling 证据，不是 public API 承诺。每次 B23 查询最多一次 provider request，429 不内联重试；
- 正向 readiness snapshot 与 observation reference 都最多有效 5 分钟。任一正 TTL、权利证据、quota scope、当前 build evidence 或 attribution 缺失/过期/漂移时，在 source I/O 前降为 `NOT_READY`；
- 只有同一不可变 adapter build 同时通过合成 mapper、双 tenant/双 replica durable simulator 和私有 live source conformance，且报告与当前 source/policy/catalog digest 完全匹配时，才可产生生产正向 snapshot。

本文件是技术边界和验收合同，不替部署方给出法律意见，也不宣称 Companies House 或 GLEIF 已在任何实际 tenant 中启用。

## 2. 固定 source profile

| Profile | Source kind | 允许的 provider surface | 当前开源内核可达状态 |
| --- | --- | --- | --- |
| `CH_PUBLIC_DATA_V1` | `OFFICIAL_REGISTRY` | `GET /company/{companyNumber}` 与 company-only `GET /search/companies`；禁止 officer、PSC、filing、document、advanced search 和 bulk product | `NOT_READY`：B23-CH 与 B23-RA 只补齐 domain scheme 和离线 RA mapper，仍缺真实 adapter/policy/conformance |
| `GLEIF_LEI_API_V1` | `OPEN_REFERENCE_INDEX` | `GET /api/v1/lei-records/{lei}` 与一个固定 legal-name/legal-jurisdiction 名称查询；禁止 Level 2、ownership、mapping、history、任意 filter/page 和 bulk file | 可作为后续私有 adapter 的 `EPHEMERAL_ONLY` 候选；仓库当前仍 `NOT_READY` |

source profile 必须绑定：核心 adapter contract version、不可变 build identity、`PRODUCTION/TEST_ONLY` deployment class、provider host/transport profile、响应 schema profile、组织资格目录、RA/scheme 目录、source-policy/attribution 版本、quota profile 和 conformance manifest。任一 profile 值不能来自普通请求或 provider response。

同一 tenant 一次只解析一个明确 source binding。另一个来源可被配置为独立替代 binding，但切换必须提高 binding revision、撤销旧 snapshot 并重新执行 conformance/policy 交集；超时、404、429、schema drift 或 quota exhaustion 不能触发按请求 fallback。

## 3. 组织资格冻结

### 3.1 资格不是主体状态或 identifier standing

组织资格只回答“该 profile 是否属于 B23 首期允许处理的法人或组织登记记录”。它不回答主体是否活跃、LEI 是否 current、是否可采用、是否值得交易或是否通过 KYC/KYB。

资格检查必须发生在 adapter native mapper 内，并早于：

1. 构造 `EnterpriseDataObservation`；
2. 把名称、地址、日期、source revision 或 locator 写入普通对象、异常、日志、trace、metric、cache 或 conformance 报告；
3. 候选去重、排序或分页投影。

`entityStatus`、`identifierStanding`、资格和 usage mode 四条轴独立。组织可以 inactive，组织的 LEI 可以 stale/retired；反之一个 current LEI 也可能属于首期明确排除的 sole proprietor。

### 3.2 Companies House

`CH_PUBLIC_DATA_V1` 的资格证据同时要求：

- 输入来自固定 company profile 或 company-only search surface，而不是 general search、officer、PSC、filing、document、insolvency 或网页解析；
- 完整 company number、registered name 与 `type`/`company_type` 存在且响应 identity 与 selector 一致；
- `company_type` 精确命中 conformance manifest 固定的官方 `company_type` key 集合；该集合以本轮核对的 Companies House enumeration 为基线，允许所有已明确命名的 company、corporate body、partnership、fund、society、charity、establishment 和 overseas-entity 类别，但明确排除 `other`；
- `uk-establishment`、`oversea-company` 和 `registered-overseas-entity` 只证明当前 Companies House 登记记录是组织记录，不把 foreign parent、external registration number 或 beneficial owner 提升为第二身份；
- `limited-partnership` 或其他不一定具有独立 legal personality 的已知类别仍可作为“组织”候选，但不得在输出或 UI 中宣称为 legal person。

缺失/空白 type、`other`、未来新增 key、manifest 未包含的 key 或 catalog digest 漂移都不默认通过。名称搜索只丢弃不合格候选且不输出其值；精确查询返回稳定 `UNSUPPORTED`。若 adapter 无法在 mapper 边界逐条隔离，整页 `UNAVAILABLE`，不能先返回部分后异步发现不合格值。

### 3.3 GLEIF

LEI-CDF 3.1 把 `EntityCategory` 定义为可选字段，并明确 `SOLE_PROPRIETOR` 是“以经营身份行动的个人”。因此“记录存在”“拥有 LEI”“LegalName 非空”“ValidationSources=FULLY_CORROBORATED”或“EntityStatus=ACTIVE”均不能单独证明组织资格。

资格 allowlist 固定为：

| `EntityCategory` | 资格 | 说明 |
| --- | --- | --- |
| `GENERAL` | 允许 | 一般法律实体 |
| `FUND` | 允许 | 受 GLEIS 专门规则治理的基金组织记录 |
| `BRANCH` | 允许 | 分支组织记录；不得复制母体 LEI 或推断独立 legal personality |
| `RESIDENT_GOVERNMENT_ENTITY` | 允许 | 政府部门、社会保障基金或受政府控制的 NPI 类记录 |
| `INTERNATIONAL_ORGANIZATION` | 允许 | 国际协议或安排建立的组织记录 |
| `SOLE_PROPRIETOR` | 拒绝 | 明确为自然人以经营身份行动 |
| 缺失、空白或未来未知值 | 拒绝 | 无法证明不是自然人，失败关闭 |

名称搜索对拒绝项静默丢弃，且搜索结果数量按通过资格后的集合计算；不得为了“补满 20 条”自动翻页。精确 LEI 查询对拒绝项返回 `UNSUPPORTED`，不返回名称、地址、category 或“sole proprietor”诊断。未知 category 不是 `UNKNOWN` 主体状态，而是资格失败；两者不能混用。

## 4. GLEIF 登记机关到本地 scheme 的映射

### 4.1 三层目录

RA 映射固定分为三层，不能合并：

1. GLEIF RA List 证明 `RegistrationAuthorityID` 指向哪个 authoritative source；
2. B23 受治理目录决定该 RA 是否获准映射到一个已实现的 `(identityScope, registryScheme)`；
3. scheme validator 决定 `RegistrationAuthorityEntityID` 的完整规范值是否合法。

GLEIF RA List 明确允许有 RA code 但没有 EntityID。因此缺少 EntityID 只是不产生本地第二身份，不用名称、地址、legal-form、OtherRegistrationAuthorityID 或 provider URL 补齐。

首批映射冻结为：

| GLEIF RA code | Authority jurisdiction | B23 identity type | 附加条件 |
| --- | --- | --- | --- |
| `RA000585` | England and Wales | `UK_COMPANIES_HOUSE / GB_CH_NUMBER` | entity ID 必须命中该 RA 的版本化 Companies House prefix/number class |
| `RA000586` | Northern Ireland | `UK_COMPANIES_HOUSE / GB_CH_NUMBER` | entity ID 必须命中该 RA 的版本化 Companies House prefix/number class |
| `RA000587` | Scotland | `UK_COMPANIES_HOUSE / GB_CH_NUMBER` | entity ID 必须命中该 RA 的版本化 Companies House prefix/number class |

三个 RA code 映射到同一个 Companies House identity namespace；RA code 和 legal jurisdiction 不进入 identity key。它们只约束哪些号码类别可由该 RA 生成，避免把同一 Companies House number 因地区标签不同创建多个身份。

### 4.2 `GB_CH_NUMBER` 规则

scheme 与未来 adapter 必须共同保证：

- 只裁剪边界空白并转 ASCII 大写；规范值恰好 8 个 ASCII 字母/数字；
- 保留全部字母前缀和前导零，不转整数、不做通用 NFKC/lowercase、不删除内部空格或标点后“修复”；
- 以版本化、RA-scoped 的完整前缀/纯数字类别目录验证；不能用一个宽泛 `[A-Z0-9]{8}` 正则代替登记类别目录；
- exact Companies House adapter 还必须要求响应 company number 与 selector byte-for-byte 相同；
- 新 prefix、RA 迁移或目录冲突先使对应 identity type `NOT_READY`，经目录更新、负向 fixture 与 conformance 后再启用。

B23-CH 已加入这两个 enum、validator 和 contract test：输入只做边界空白裁剪和逐字符 ASCII 大写，必须恰好八位，并命中纯数字类别或 pinned 显式前缀目录；所有字母前缀和前导零都保留。B23-RA 又在 `infra/external` test scope 固定 `GLEIF-RA-1.8.1/B23-CH-2026-07-20`，证明 `RA000585/586/587` 与各自号码类别匹配后才生成 standing 为 `UNKNOWN` 的第二身份；未知 RA、缺失 EntityID 或错误归属只省略第二身份，主 LEI identity mismatch 仍失败关闭。两者都不授权 runtime adapter 宣告生产 capability。

### 4.3 目录版本与漂移

生产 manifest 必须固定 RA List version、取得时间、官方 artifact digest、三条受支持 row 的 canonical digest、Companies House prefix catalog version/digest 和 mapper version。规则如下：

- RA List 新版本不会自动热更新 domain identity；先下载到私有受控配置、校验 artifact，再比较受支持 row；
- 三条 row 未变化时，旧映射可在显式 evidence `validUntil` 内继续；row 改名、换辖区、删除、重复或 digest 冲突会立即撤销对应附加 identity capability；
- RA artifact 暂时不可下载时不得改用 provider response 自带的自由文本 authority。未过期的 pinned evidence 可继续，过期后失败关闭；
- 未识别 RA、`RA999999`、`OtherRegistrationAuthorityID`、`ValidationAuthority` 或任意 URL 都不生成 identity；
- RA 映射失败不改变一个已经通过资格和 LEI 校验的 GLEIF 主 profile，但必须完全省略本地第二身份；若 capability 曾声明该第二 identity type，则本次 capability drift 使请求 `UNAVAILABLE`，不能返回与声明不一致的 shape。

## 5. 来源权利、展示与 attribution

### 5.1 Companies House

Companies House 说明公共登记信息可免费访问且不额外规定如何使用，同时明确下游使用者自行承担数据保护、版权和其他适用法律责任。该说明不是 MIT/OGL 数据许可，也不自动证明任何 tenant 的营销、批量复制、人员处理、Customer 持久化或再分发用途。

`CH_PUBLIC_DATA_V1` 默认 `NOT_READY`。只有部署方提供版本化、有限期的审查记录，明确允许当前 tenant/user group 的人工基础组织事实查询、`SEARCH_RESULT`/`DETAIL` 展示、数据地域、禁止用途和终止处置后，才可达到 `EPHEMERAL_ONLY`。人员、PSC、filing、document、insolvency detail、历史名称和 service address 永不因该政策进入 B23 profile。

受控 attribution 使用来源名 `Companies House` 和受治理 information URL；不使用 logo，不返回任意 record URL，不声称数据已验证、官方认证 Customer 或 Companies House 支持本产品。

### 5.2 GLEIF

GLEIF 的 LEI/LE-RD Access Service 免费并以 CC0 提供数据，但 Terms of Use 仍要求通过其提供的 access service 访问、不得规避技术限制、不得使用 GLEIF 商标/logo 制造授权或背书印象，并明确服务和数据按现状提供。

`GLEIF_LEI_API_V1` 同样默认 `NOT_READY`。显式部署 policy 可在当前 B23a 达到 `EPHEMERAL_ONLY`；CC0 不跳过 tenant、组织资格、字段 allowlist、attribution、quota、TTL 或 conformance。受控 attribution 使用来源名 `GLEIF` 和受治理 information URL；不把 LOU、RA、provider link 或 response attribution 文本交给客户端。

### 5.3 权利证据门

每个 source binding 的权利证据至少固定：source profile、用途、tenant/user group、允许字段、展示 context、持久化模式、地域、attribution 版本、禁止用途、终止处置、reviewedAt、有限 `validUntil`、官方 terms/data-use URL 集合及内容 digest。

缺项、过期、URL 内容改变、review scope 与当前 tenant/use mode 不匹配时立即 `NOT_READY`。自动抓取到“页面没有变化”不能替代人工审查；人工审查也不能替代 adapter conformance。当前 B23a 没有采用事务，因此即使审查记录写了更宽权利，核心仍下压为 `EPHEMERAL_ONLY`。

## 6. 配额、并发与重试冻结

### 6.1 Provider ceiling 与 scope

| Profile | 官方公开 ceiling | 保守 quota scope | B23 解释 |
| --- | --- | --- | --- |
| `CH_PUBLIC_DATA_V1` | 600 requests / 5 minutes | application API key / source binding | 同一 key 被多个 tenant/replica 共享时先受一个共享 provider bucket 约束 |
| `GLEIF_LEI_API_V1` | 60 requests / minute / user | 共享 outbound egress identity，除非真实 provider evidence 证明更窄 scope | 无 API key 不等于每 tenant 独立 60/min；NAT、代理或其他服务可能共享外部额度 |

GLEIF 的 quota scope 是根据官方“per user”在无业务账号/API key 条件下作出的保守部署推断；真实 adapter conformance 必须记录实际网络拓扑，不能把该推断宣传成 provider 对 tenant 的保证。

### 6.2 两层 admission

每次调用依次消费：

1. 同一 provider quota identity 的共享 binding bucket；
2. 当前 tenant/source binding 的独立公平 bucket；
3. 当前 operation 的并发 slot。

共享 bucket 的全部 replica 预算之和必须严格小于 provider ceiling，并显式保留正数 control/probe reserve；同时启用名称查询和 exact lookup 时还必须为 exact lookup 保留正数 reserve。具体安全余量、tenant 权重、并发数与 deadline 属于部署 policy，但没有这些明确数值就 `NOT_READY`，不能默认等于 provider ceiling。

多副本部署必须使用共享原子 admission，或在 manifest 中固定 replica 数和互不重叠的静态预算且证明总和不超 ceiling。自动扩容、egress 改变或未知 replica 数会撤销 readiness。单 JVM counter 只能声明单实例 conformance，不能宣称 tenant/global quota。

### 6.3 调用成本与失败

- 一次 capability 请求只读取本地正向 snapshot，不调用 provider；
- 一次名称搜索最多一个 provider request，不为补足候选做翻页或 N+1 exact lookup；
- 一次 exact lookup 最多一个 provider request；
- B23 核心仍限制名称 1..200 Unicode 码点、单页最多 20 个已通过资格的候选；provider 更大 page/total/filter 永不暴露；
- 429 或本地 bucket exhausted 返回 `LIMITED`，不在同一人工请求内 retry；
- 可信、格式正确且有上限的 `Retry-After` 只用于私有 cooldown，不向普通输出透传。缺失、负值、溢出或超界值按完整 provider window cooldown；
- timeout、TLS、DNS、5xx、parse/schema 失败同样不自动改换来源。若以后允许一次 transport retry，必须作为新 contract version 重新证明“首次请求确定未到 provider”、重复调用成本和 quota reservation，本版本固定为零 retry；
- admission store 不可用是 `UNAVAILABLE`，不是伪造 `LIMITED`；任何情况下都先拒绝再 source I/O，不能在调用后补记额度。

quota window/cooldown 状态不得在窗口结束前被 TTL/LRU 淘汰。容量不足时拒绝新 partition，不能淘汰另一个 tenant 的仍有效 bucket 或借用 exact/control reserve。

## 7. Readiness snapshot 与 TTL

### 7.1 最小 snapshot

每个 tenant source binding 的正向 snapshot 至少包含：

| 字段 | 冻结语义 |
| --- | --- |
| contract/build | B23 adapter contract、不可变 build identity、deployment class |
| binding | tenant 派生的 source binding revision；不含 credential/account 原值 |
| source profile | `CH_PUBLIC_DATA_V1` 或 `GLEIF_LEI_API_V1`、provider schema/profile digest |
| operation readiness | capability、registered-name search、exact lookup 分别为 `READY/NOT_READY/UNSUPPORTED` |
| qualification | Companies House type allowlist 或 GLEIF category allowlist version/digest |
| registrar mapping | RA/prefix/scheme catalog version/digest 及当前允许的 identifier types |
| rights/policy | use-mode、field/context、attribution、地域、validUntil 的安全 evidence digest |
| quota | provider scope、window/ceiling、安全预算、reserve、replica/admission profile digest |
| conformance | `B23-O/2` fixture version、通过场景、live evidence digest/validUntil |
| time | adapter checkedAt、core receivedAt、expiresAt 与单调 binding revision/probe sequence |

普通能力输出仍只返回 provider-neutral query modes、jurisdictions、identifier types、candidate ceiling、usage mode 和 ready boolean；上表的 build、profile、catalog、quota、terms 和 evidence 细节只属于部署运维审计。

### 7.2 TTL 交集

正向 snapshot 的有效期固定为：

`min(adapterExpiresAt, coreReceivedAt + 5 minutes, rightsValidUntil, conformanceValidUntil, catalogValidUntil)`。

任何时间缺失、非正 duration、明显未来 checkedAt、算术溢出、超过五分钟的 adapter TTL 或已过期 evidence 都拒绝正向 snapshot。新的 `NOT_READY`、credential 撤销、terms/catalog/schema digest 漂移、admission 故障或 build/binding 变化可以立即提前失效，不能等待 TTL 到期。

observation TTL 继续使用 B23-XP/B23a 规则：

`effectiveObservationTtl = min(core 5 minutes, adapter declared maximum, tenant source policy maximum)`。

三者都必须显式为正。observation 到期后返回 `EXPIRED` 或要求重新查询，不读取旧 profile、延长旧 reference 或把 provider source-update/renewal 时间当成本地 expiry。readiness snapshot 到期也不允许已有 observation 发起新 source 操作；当前 B23a 没有采用命令，未来采用仍须同时重验 snapshot 和重新 exact lookup。

## 8. Fail-closed 矩阵

| 缺失/漂移 | 对外安全结果 | Source-call 与数据规则 |
| --- | --- | --- |
| source policy、rights、attribution、正 TTL 或 quota profile 缺失 | `UNAVAILABLE` / capability `NOT_READY` | source call 为 0 |
| snapshot 缺失、过期、旧 binding/build 或 conformance mismatch | `UNAVAILABLE` / `NOT_READY` | source call 为 0；不使用历史正向结果 |
| Companies House type 缺失、`other` 或未知 | exact=`UNSUPPORTED`；search 丢弃或整页 `UNAVAILABLE` | 不输出该记录任何 profile 值 |
| GLEIF `SOLE_PROPRIETOR`、category 缺失或未知 | exact=`UNSUPPORTED`；search 丢弃或整页 `UNAVAILABLE` | 名称/地址/人员值不得越过 native mapper |
| RA 未识别、EntityID 缺失或本地 validator 失败 | 主 LEI 可继续；第二 identity 缺失 | 不猜测、不用 Other/Validation authority；若 capability 声明冲突则整次 `UNAVAILABLE` |
| provider 401/403 | `UNAVAILABLE` 并撤销相关 operation snapshot | 不是 CRM caller 401/403；不透传账号/key 信息 |
| exact 404 | `NO_RESULT` | 不区分不存在、移除或覆盖缺失 |
| search 空列表或 CH search 404 | 成功空页 | 不伪装 source outage |
| provider 400/422 在本地 typed request 后出现 | `UNAVAILABLE` 并标记 contract drift | 不透传参数/body，不降级为另一个查询面 |
| provider/local 429 或 bucket exhausted | `LIMITED` | 零 retry，进入私有 cooldown，不暴露余额/reset/account |
| redirect、非允许 host、TLS/timeout、5xx、content-type/body/schema/identity mismatch | `UNAVAILABLE` | 不跟随 redirect，不产生 observation/cache |
| 新 entity/standing 枚举 | 按 B23-O/1 固定映射为安全 `UNKNOWN` 或非当前 standing；不扩大采用资格 | 新 qualification/type 枚举仍拒绝，不能混为 status unknown |
| RA/terms/schema/catalog 下载失败 | 未过期 pinned evidence 可继续；过期后 `NOT_READY` | 不抓网页/第三方镜像补齐，不使用 response 自由文本 |
| admission、clock 或 evidence store 故障 | `UNAVAILABLE` | 不能 fail-open 调 source，不能把 unknown 当 remaining capacity |

未知 runtime failure 只能收敛为稳定 `UNAVAILABLE`。安全 reason 可使用固定内部枚举，例如 `RIGHTS_EVIDENCE_MISSING`、`QUALIFICATION_UNPROVEN`、`RA_CATALOG_DRIFT`、`QUOTA_SCOPE_UNPROVEN`、`SNAPSHOT_EXPIRED`、`CONFORMANCE_MISMATCH`、`SOURCE_AUTH_FAILED`、`SCHEMA_DRIFT` 和 `ADMISSION_UNAVAILABLE`；不得包含 provider message、URL、credential、query、名称、登记号、地址或 response 摘要。

## 9. `B23-O/2` conformance oracle

### 9.1 执行层次

同一 scenario ID 必须运行三层：

1. **纯 mapper 层**：只用显著合成 Companies House/GLEIF native shapes，证明资格、status/standing、RA 和 allowlist 缩减；
2. **durable HTTP/admission simulator 层**：两个 tenant、两个 adapter replica、独立 provider simulator 与共享 admission store，使用虚拟时钟验证 quota、TTL、retry=0、漂移和失败隔离；[B23-O-L2](B23-O-L2-durable-simulator.md) 已实现其中 quota/readiness 机械层并执行 `O2-06/07/08/10`，真实 HTTP/DNS/TLS/schema lane 仍未实现；
3. **私有 live source 层**：当前真实 endpoint/auth/network profile，使用经审查的 allowlisted 组织 selector 与私有 ephemeral response；报告只保留安全计数/digest，不把真实名称、号码、地址、response、credential 或 query 写入仓库、CI artifact、日志或快照。

纯 mock 只统计方法调用、adapter 自己回写 expected output、只跑公开 API `200`、只测一个 tenant/replica，或把 B23-XF 的合成 quota/TTL 当作真实 evidence 都不能通过生产 conformance。

### 9.2 固定场景

| ID | 必须证明 |
| --- | --- |
| `O2-01` | Companies House 已知 type 通过；`other`、缺失和未来 type 不输出；establishment/overseas record 不提升 parent/external identity |
| `O2-02` | GLEIF 五个允许 category 通过；`SOLE_PROPRIETOR`、缺失和未来 category 在名称/地址离开 mapper 前拒绝 |
| `O2-03` | LEI entity status 与 registration standing 正交；unknown status 不变 active，duplicate/annulled/successor 不自动迁移 identity |
| `O2-04` | 三个 Companies House RA code 精确映射；未知/Other/Validation RA 和错误 prefix/value 不生成第二 identity；仅 scheme 或 test-scope mapper 已实现而 runtime catalog/conformance 未实现时 capability 仍不得声明生产 CH identity |
| `O2-05` | rights/attribution/region/use-mode evidence 缺失、过期或 digest 漂移在 source call 前 `NOT_READY`；B23a 始终下压 `EPHEMERAL_ONLY` |
| `O2-06` | CH 600/5m 与 GLEIF 60/min profile、共享 binding bucket、tenant 公平 bucket、exact/control reserve 和两 replica 总预算；一个 tenant exhausted 不跨分区 |
| `O2-07` | capability 零 source-call，search/exact 每次最多一 call，429/timeout/5xx 零 retry 且不 fallback；admission 故障先于 source I/O |
| `O2-08` | readiness 与 observation 的五分钟边界、较短 policy、evidence validUntil、clock skew/overflow、立即 revoke 和虚拟时钟过期 |
| `O2-09` | 401/403/404/400/422/429/5xx/redirect/TLS/content-type/body/schema/identity mismatch 的稳定错误及 snapshot 影响 |
| `O2-10` | 同进程/双 replica 的 binding、policy、quota、catalog、snapshot、failure 隔离；请求不能选择 tenant/provider/account |
| `O2-11` | native DTO、普通对象、HTTP 输出、exception、log、trace、metric、event、Activity、cache、report、test artifact 和 conformance manifest 的敏感值泄漏命中数均为零 |
| `O2-12` | live endpoint 的当前 auth、host、schema/category/status/RA shape、quota headers/429 policy 与 source docs digest 同 manifest 匹配；不在报告保存数据值 |

### 9.3 机器可读 manifest

生产 conformance 报告至少包含：

- fixture `B23-O/2`、adapter contract、不可变 build、deployment class、source profile、binding-policy schema version；
- provider documentation/schema、Companies House enum 或 GLEIF CDF/RA List 的 version/digest；
- qualification/RA/scheme mapper version，支持的 operation/jurisdiction/identifier type 和 candidate limit；
- quota scope/window/ceiling、本地预算/reserve、replica/admission profile，只记录配置 digest 与安全数值，不记录 key/IP/account；
- rights/attribution/TTL evidence digest、reviewedAt/validUntil 和最终 usage mode；
- 每个 scenario 的 pass/fail、安全 reason、provider call count、retry count、partition counter 与 leak-hit count；
- live run 的 checkedAt/validUntil、runner/environment identity digest 和“真实值未持久化”的 sink inventory 结果。

报告不得包含真实 company number、LEI、名称、地址、query、API key、egress IP、provider body/header、record URL、ETag/revision、错误原文或其可离线枚举的普通 hash。source schema、terms、RA/type catalog、quota scope、network topology、adapter build 或 policy 变化后必须重跑；历史 manifest 不自动迁移 readiness。

## 10. 闭合与剩余门槛

B23-O/2 已冻结：

- Companies House 与 GLEIF 的可证明组织资格及 missing/unknown fail-closed 规则；
- GLEIF 三个 Companies House RA code 到 `UK_COMPANIES_HOUSE/GB_CH_NUMBER` 的受治理映射，以及 scheme 或 RA mapper/conformance 任一缺失时必须省略的边界；
- 两个来源的可配置权利/attribution 证据门，且不把 public/CC0 误写成 CRM authority；
- provider quota scope、共享/tenant 两层 admission、单调用成本、零 retry、multi-replica 证明和 429 cooldown；
- readiness/observation 五分钟 TTL、evidence 有效期交集、漂移撤销和稳定 fail-closed 分类；
- 三层 `O2-01` 至 `O2-12` conformance 与无真实值 manifest。

仍未实现或证明：

- Companies House/GLEIF 的生产 RA-scoped mapper 装配、真实 adapter、runtime 号码目录 digest 及当前 build conformance；B23-CH 已完成 domain scheme、显式 prefix 数据和 API/VO 支持，B23-RA 仅完成 test-scope 离线 mapper；
- GLEIF 或 Companies House 私有 adapter、credential/binding、共享 admission、生产 policy 和当前 build 的 live conformance report；
- B23-XI Customer identity 表/MySQL 仲裁、B23-XP preview/diff/address slot/adoption provenance/retention；
- 任一商业来源合同与真实 conformance。

因此本轮解除的是“官方/开放来源 readiness/conformance 规则未定义”的设计阻断，B23-CH 又解除 CH typed identity 阻断；两者都不产生生产 `READY`，也不单独触发 Customer 采用实现。B23-O-L1 已选择 `GLEIF_LEI_API_V1` 完成第一层纯 mapper conformance，并继承 B23-OC；B23-O-L2 已执行第二层的 `O2-06/07/08/10` quota/readiness 机械证据。后续仍须补齐目标来源的私有 adapter、RA mapper（若生成 CH 第二身份）、生产 policy/admission、真实 HTTP/DNS/TLS/schema lane、`O2-09/11` 与私有 live source `O2-12` conformance。B23-SPLIT 仍要求至少一个目标来源的真实 policy/readiness evidence 与 Customer/MySQL 采用 fixture。见 [B23-O-L1 验收](acceptance/B23-O-L1.md)和 [B23-O-L2 fixture](B23-O-L2-durable-simulator.md)。

## 11. 官方依据

### Companies House

- [Developer guidelines](https://developer.company-information.service.gov.uk/developer-guidelines/)：600 请求/5 分钟、429 与禁止绕过限额。
- [Company profile endpoint](https://developer-specs.company-information.service.gov.uk/companies-house-public-data-api/reference/company-profile/company-profile)与[resource schema](https://developer-specs.company-information.service.gov.uk/companies-house-public-data-api/resources/companyprofile?v=latest)：精确 profile、company number/name/type/status、ETag、地址和 partial-data shape。
- [Company-only search](https://developer-specs.company-information.service.gov.uk/companies-house-public-data-api/reference/search/search-companies)：本轮唯一允许的名称查询面。
- [Official API enumeration catalog](https://github.com/companieshouse/api-enumerations/blob/master/constants.yml)：`company_type`、status、jurisdiction 与 formation label 的当前受治理 key 集合。
- [Companies House data products](https://www.gov.uk/guidance/companies-house-data-products)：公共登记数据访问方式、使用者的数据保护/版权等责任。
- [Company number lookup guidance](https://find-and-update.company-information.service.gov.uk/company-lookup/search)：8 字符与前导零要求。

### GLEIF

- [GLEIF API documentation](https://documenter.getpostman.com/view/7679680/SVYrrxuU)：JSON:API、Golden Copy、1..200 分页、60 请求/分钟/用户及 endpoint/filter shape。
- [LEI-CDF 3.1](https://www.gleif.org/en/lei-data/access-and-use-lei-data/level-1-data-lei-cdf-3-1-format)：EntityCategory、EntityStatus、RegistrationStatus、RegistrationAuthority 与字段基数语义。
- [GLEIF Registration Authorities List](https://www.gleif.org/en/lei-data/code-lists/gleif-registration-authorities-list)：RA code、EntityID、目录版本与演进说明。
- [LEI linking to Registration Authorities](https://www.gleif.org/en/lei-data/lei-linking-to-registration-authorities/)：Companies House 的 `RA000585`、`RA000586`、`RA000587` 官方映射。
- [LEI Data Terms of Use](https://www.gleif.org/en/meta/lei-data-terms-of-use)：CC0、Access Service、技术限制、非背书与按现状提供。
- [GLEIF API overview](https://www.gleif.org/en/lei-data/gleif-api/)：API production service 与 Golden Copy 数据来源。
