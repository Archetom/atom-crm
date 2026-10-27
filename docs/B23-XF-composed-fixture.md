# B23-XF 企业数据合成组合 fixture

> 闭合日期：2026-07-20
>
> 状态：`B23-XF/1` 已闭合并以应用层可执行 fixture 固化；它联合复核 B23-XI 的查询身份/状态语义、B23-XP 的只读 profile/政策边界和 B23a 的 tenant 绑定查询实现，不实现 Customer 身份占用、采用事务、provenance、真实 provider adapter 或生产 readiness
>
> 输入：[B23 总规划](../batch-B23.md)、[B23-C 商业模型 spike](spikes/B23-commercial-enterprise-data.md)、[B23-O 官方/开放模型 spike](spikes/B23-official-open-registry.md)、[B23-XI 登记身份设计](B23-XI-registered-identity.md)、[B23-XP profile/policy 设计](B23-XP-profile-provenance.md)与已实现的 B23a 查询内核

## 1. 闭合结论

B23-XF 固定一套同进程、双 tenant、双 native source shape 的确定性组合 fixture。它不连接真实网络，不包含真实企业、自然人、登记号码、provider 响应或凭证。`B23-XF/1` 同时证明：

- 官方/开放形态的 LEI 与商业形态的 USCC 都先经过 scheme-specific 校验，公共身份固定为 `(identityScope, registryScheme, canonicalValue)`；法律辖区、主体状态、标识 standing、来源和 tenant 都不进入身份键；
- 主体状态与标识 standing 保持正交：合成开放记录可以是 `entity=ACTIVE + identifier=STALE`，商业 native 新枚举稳定缩减为 `entity=UNKNOWN + identifier=UNKNOWN`，不会猜成 active；
- 人员、联系方式、关系、provider locator 和 raw error canary 在 adapter 白名单化时丢弃；公共观测仍只有 B23-XP 固定字段，不存在扩展 map 或 organization/legal-form 回流；
- adapter capability、tenant 部署政策和 B23a 核心上限取最严交集。即使 fixture policy 表示 `ADOPT_SELECTED_FACTS`，B23a 也只返回 `EPHEMERAL_ONLY`；缺 policy 不进入 source query；
- 名称查询使用 adapter 声明且不超过核心 20 条的上限；合成来源额度按 tenant 分区，A tenant 的 `LIMITED` 不消耗、淘汰或禁用 B tenant；fixture 数值只用于测试，不代表任一真实来源的 QPS 或合同额度；
- `NO_RESULT`、`LIMITED`、`UNAVAILABLE`、`UNSUPPORTED` 与未知 runtime failure 只形成 B23 稳定安全错误，provider message/body/status/account 不进入公共结果或日志；
- 同一 adapter 进程中的 capability、policy、attribution、TTL、quota、call count 和 observation 都由派生 `TenantId` 选择。客户端不能提交 tenant、provider、binding、account 或套餐 selector，跨 binding 的标识在 source I/O 前拒绝。

本 fixture 对 XI 的执行范围止于 B23a 已存在的登记身份类型、规范值和状态正交。XI 的 Customer 一对多关联、tenant 内反向唯一键、删除释放和 MySQL 竞态仍是采用链路的实现门槛；不能把本次通过描述为“Customer 身份唯一仲裁已实现”。

## 2. 固定双 tenant 世界

每个测试从新的 adapter 实例、空额度计数和固定时钟 `2026-07-20T08:00:00Z` 开始：

| 维度 | Tenant O：官方/开放形态 | Tenant C：商业形态 |
| --- | --- | --- |
| fixture tenant | `101` | `202` |
| source kind | `OPEN_REFERENCE_INDEX` | `COMMERCIAL_AGGREGATOR` |
| legal jurisdiction | `GB` | `CN` |
| primary identity | `GLOBAL_LEI_SYSTEM / LEI / 0000SYNTHETICFIXTU42` | `CN_UNIFIED_SOCIAL_CREDIT_CODE / CN_USCC / 91110000F1XTURE1AN` |
| native entity status | `ACTIVE` | 未知未来枚举 `NATIVE_FUTURE_STATE` |
| native identifier status | `LAPSED` | 来源没有独立 standing |
| canonical state | `ACTIVE / STALE` | `UNKNOWN / UNKNOWN` |
| formation mapping | entity creation → `FORMED` | StartDate → `ESTABLISHED` |
| registered address | 合成 legal address | 合成 registered address |
| adapter max candidates | `2` | `1` |
| fixture-only policy TTL | `2 minutes` | `45 seconds` |
| fixture-only policy maximum | `ADOPT_SELECTED_FACTS`，由 B23a 下压 | `EPHEMERAL_ONLY` |
| B23a public usage | `EPHEMERAL_ONLY` | `EPHEMERAL_ONLY` |

这些 identity 字符串通过当前 LEI/USCC checksum 目录，但使用显眼的合成词根。名称、地址、revision 与 attribution 也全部是 fixture 值。商业形态的 TTL、候选上限和 attribution 只用于证明交集与隔离算法，不是对企查查或任何商业来源合同、并发额度、持久化许可或 production readiness 的推断。

native profile 另外携带以下禁止值类别：

- 人员/法定代表人 canary；
- 电话或联系方式 canary；
- Level 2/股权关系 canary；
- provider locator/account token canary；
- 任意 provider error body/message canary。

这些值只存在于测试私有 native record 或异常中。adapter mapper 不提供通用属性 map，因此 core port、VO、公共错误、`toString` 与测试报告都不能容纳它们。

## 3. 独立 oracle 与 checkpoint

每一步同时观察下列面，不能只断言最终 HTTP/Result code：

| Oracle | 必须观察 |
| --- | --- |
| native shape | 原始状态、人员/联系/关系/locator/error canary；只在 fixture adapter 私有对象中存在 |
| adapter port | tenant、typed query、声明的 identity type/jurisdiction/query mode/max candidates、规范观测或固定 `EnterpriseDataSourceError` |
| deployment policy | tenant 对应的 source kind、usage ceiling、正 TTL、`SEARCH_RESULT`/`DETAIL` attribution；与 native profile 分离 |
| B23a core | active member/capability 在 source 前校验、core usage cap、source-kind/identity/jurisdiction/候选数复核、core clock 生成 observation lifetime |
| public projection | B23-XP allowlist、规范 identity/status/date/address/source、受控 attribution、稳定错误；无 provider selector 或扩展 map |
| partition counters | 每 tenant 的 search/lookup call、last limit、remaining admission 和 policy block；一个分区的失败不能改变另一个分区 |
| mutation sentinel | Customer、identity link、provenance、Activity、event、report 和 cache 写次数均为零 |

fixture adapter 可以把 native 枚举映射为规范类型，但不能生成权限、tenant、attribution、`observedAt`、`expiresAt` 或更宽 usage mode。后五项由活动成员解析、独立 policy 目录和核心时钟决定。

## 4. `XF-01` 身份、状态与 profile 缩减

### 4.1 开放形态

Tenant O 以小写 selector 查询合成 LEI。必须得到完整大写规范值、`GLOBAL_LEI_SYSTEM/LEI` 和 `IdentifierStanding.STALE`。native `LAPSED` 只改变 identifier standing；native entity `ACTIVE` 仍独立映射为 `RegisteredEntityStatus.ACTIVE`。

entity creation date 只映射为 `FORMED`，source update 与 revision 不得替代 formation date。legal address 缩减为一个 `RegisteredAddressObservation`；管理人员、关系、provider locator 和额外 native category 被丢弃。

### 4.2 商业形态

Tenant C 以小写 selector 查询合成 USCC。scheme catalog 必须校验 registration department/category、行政区段、字符集和 checksum，再输出完整大写 canonical value。来源没有独立 standing 时固定为 `UNKNOWN`；未知 native entity 枚举固定为 `UNKNOWN`，不能落入 active 的 default 分支。

StartDate 只映射为 `ESTABLISHED`，商业注册地址缩减为一个规范地址。法定代表人、contact、关系、评分、provider ID/URL 和任意新字段都不进入公共观测。

### 4.3 共同负向分支

- scope/scheme 不匹配、无效 checksum、超长或空 identifier 在 source lookup 前失败；
- name search 必须带显式受支持 jurisdiction，名称为 1..200 Unicode 码点，不能携带 filter map、分页 token 或 provider selector；
- lookup 返回的 primary identity 必须精确等于 selector；额外 identity 必须属于 capability 声明的受治理类型；
- duplicate primary identity、错误 jurisdiction、错误 source kind、超过 adapter/core 候选上限或非法 observation 都归一为安全不可用；
- `legalJurisdiction`、entity status、standing、source kind、tenant 和 provider locator 改变时，不能改变 canonical identity key。

## 5. `XF-02` 政策、attribution、TTL 与使用模式

固定顺序为：

1. 从 caller 的活动成员关系派生 `TenantId` 并验证 `enterprise-data:read`；
2. 用该 tenant 读取 adapter capability；
3. 独立解析同 tenant deployment policy；
4. 取 query mode、jurisdiction、identity type、候选上限、source kind、usage、TTL 与 attribution 的最严有效交集；
5. 只有 ready 才执行名称/标识 source query；
6. source 返回后由核心固定时钟产生 `observedAt`，以 policy TTL 产生 `expiresAt`；
7. 进入只读数据库 snapshot 只投影已经验证的结果。

Tenant O 的 policy ceiling 即使为 `ADOPT_SELECTED_FACTS`，B23a 输出也必须是 `EPHEMERAL_ONLY`。Tenant C 的 fixture policy 本身就是 `EPHEMERAL_ONLY`。policy 缺失、not ready、source kind 不匹配、TTL 非正/超过五分钟或任一必需 attribution context 缺失时，capability 安全降为 `NOT_READY`，查询返回 `ENTERPRISE_DATA_UNAVAILABLE` 且 source search/lookup count 不增加。

`SEARCH_RESULT` 与 `DETAIL` 使用各自受控 attribution。adapter raw response 不能提交 attribution 字符串，也不能把 account、endpoint、套餐或 locator 伪装为 source name。商业 fixture attribution 明确写成 synthetic contract fixture，不能出现在真实 conformance 报告中冒充生产合同证明。

## 6. `XF-03` 候选额度与 source admission

名称搜索没有 offset、cursor、total 或“下一页”。B23a 把 adapter capability 的 `maxCandidates` 原样作为 typed query limit，但该值必须为 `1..20`；fixture 分别断言 O=`2`、C=`1`。adapter 返回超过声明值、超过核心值、重复 primary identity 或与查询 jurisdiction 不符的列表时，整页失败关闭，不截断后继续。

fixture admission 是按 tenant 独立的确定性计数：

1. 把 Tenant C remaining 设为 `0`，C 查询得到稳定 `LIMITED`；
2. 同一进程中 Tenant O 仍使用自己的 remaining slot 成功查询；
3. C quota 不会借用 O slot，不会淘汰 O policy/cache/binding，也不会把 C identity 交给 O source；
4. capability 只公开安全候选上限，不公开余额、provider QPS、套餐、reset time 或账号状态；
5. quota 的 fixture 数字不得进入生产配置默认值或真实 adapter readiness 声明。

真实 adapter 可使用自己的本地或共享 admission，但若声称 tenant 全局额度，必须另有跨副本共享实现与 conformance。单 JVM counter 只能证明本进程分区。

## 7. `XF-04` 稳定错误与 source-call ordering

| Adapter/core 输入 | B23 稳定错误 | 额外断言 |
| --- | --- | --- |
| exact lookup 无结果 | `ENTERPRISE_DATA_NO_RESULT` (`304`) | 不暴露 native 404/body |
| provider quota/rate limit | `ENTERPRISE_DATA_LIMITED` (`305`) | 不暴露余额、reset header 或账号 |
| timeout、5xx、runtime、非法 adapter output、policy/source mismatch | `ENTERPRISE_DATA_UNAVAILABLE` (`306`) | raw exception message/body 命中数为零 |
| 未声明 query mode/jurisdiction/identity type | `ENTERPRISE_DATA_UNSUPPORTED` (`307`) | 在对应 source search/lookup 前拒绝 |
| malformed public selector | `PARAMETER_INVALID` (`101`) | 在 capability/source 前拒绝 |
| caller 无效、成员失活或缺少能力 | 原有 authentication/access error | 在任一 adapter 调用前拒绝 |

provider 401/403 也是 source `UNAVAILABLE`，不能伪装成 CRM caller 的 102/103。error message、URL、header、body、credential 与 account 状态不能进入 `ApplicationException`、日志、trace、metric label、event 或 Activity。

## 8. `XF-05` 双 tenant 隔离

同一 adapter 对象服务 O/C 两个 tenant，但以下状态全部以派生 `TenantId` 为首要键：

- source binding 与 source kind；
- capability 的 jurisdiction、identity type、query mode 和 candidate limit；
- policy、usage ceiling、TTL 与 contextual attribution；
- quota/admission、search/lookup call count 和 failure injection；
- native profile、canonical observation 与 source revision。

固定负向矩阵包括：

1. C caller 提交 O 的 LEI type：C capability 未声明该 type，在 C source lookup 前返回 unsupported；
2. C quota exhausted：O 查询仍成功；
3. C policy 改为 unavailable：C source query count 不变，O policy与结果不变；
4. adapter 返回与当前 tenant policy 不同的 source kind：只使当前请求 unavailable，不能回退到另一 binding；
5. request body 中出现 tenant/provider/account/binding 字段：REST 严格 DTO 解析拒绝，不能改变 caller 或 adapter 分区；
6. 结果、错误与 attribution 不包含另一个 tenant 的名称、identity、source label、remaining quota 或 failure state。

公开登记数据可能在现实中被两个 tenant 查询到同一 identity，这不允许共享 query history、observation reference、policy 或 quota。未来采用链路中两个 tenant 可以各自占用同一规范 identity；唯一约束只在 `(tenant, identityScope, registryScheme, canonicalValue)` 内生效。

## 9. `XF-06` 可执行覆盖与通过条件

开源可执行证据位于 `EnterpriseDataComposedFixtureTest`，通过 Maven application fast suite 运行。它使用两个 test-private native record mapper、同一 tenant-routed source/policy 进程和真实 `EnterpriseDataQueryServiceImpl`/`QueryServiceTemplate`，覆盖：

- LEI/USCC canonical identity 和小写 selector；
- `ACTIVE + STALE` 与 `UNKNOWN + UNKNOWN` 状态正交；
- 人员/联系/关系/locator allowlist 丢弃及固定 VO record components；
- source kind、usage cap、context attribution、两种 TTL 和不同 candidate limits；
- quota/policy 分区、cross-binding selector 拒绝和 source call count；
- `304`、`305`、`306`、`307` 以及 raw exception canary 清除。

现有 `EnterpriseDataIntegrationTest` 继续用 MockMvc、Spring Security、真实 organization/authorization 数据和 MySQL 证明未认证/未授权拒绝、tenant bootstrap、严格请求 DTO 与公开 JSON allowlist。B23-XF 不为只读内核新增数据库表，因此组合 fast fixture 与已有 B23a Docker integration fixture 是互补证据。

通过 `B23-XF/1` 必须同时满足：

- 所有 fixture 数据显著合成，禁止值扫描为零；
- 每个 scenario 在一次真实 application service 调用链内联合观察 port、policy、core、projection 和 partition counter；
- 测试不能直接编辑 B23a result、伪造 caller 派生 tenant 或以 mock 返回值代替 source/policy 交集；
- fast suite 与 `CI=true` Docker suite 均通过；
- 文档和测试明确区分“合成 source shape conformance”与“真实来源 policy/readiness”。

## 10. 私有 adapter conformance 输出

真实官方/开放或商业 adapter 必须复用 `XF-01` 至 `XF-05` 的语义，并输出不含真实查询值的机器可读证据，至少包含：

- fixture version `B23-XF/1`、adapter contract version、不可变 build/profile evidence digest 和执行时间；
- source kind、受支持 identity type/jurisdiction/query mode、声明 candidate limit 和 quota scope；
- native status → entity status/standing/date/address 的映射版本与 unknown/default 分支结果；
- policy/attribution/TTL 的独立部署证据摘要，以及最终 `NOT_READY`/`EPHEMERAL_ONLY`/`ADOPT_SELECTED_FACTS`；
- 每 scenario 的 pass/fail、安全错误类别、source call count 和双 tenant partition counters；
- raw field/provider error allowlist diff 与人员、联系、关系、locator、credential、URL、response、query、identifier、address、revision 泄漏扫描命中数，全部只报告计数；
- 明确声明测试使用 synthetic values，未读取真实人员或客户数据。

商业 adapter 没有经过审查的合同、用途、地域、展示/持久化、attribution、TTL、retention、删除和并发额度证据时，conformance 只能报告 `NOT_READY`，不能用本文件的 `45 seconds` 或 `1 candidate` fixture 数字补齐。真实 source schema、合同或 build 改变后必须重跑；历史报告不能自动迁移 readiness。

## 11. 已闭合与剩余门槛

B23-XF 已闭合：

- 官方/开放与商业 native shape 到 B23-XI identity/standing 和 B23-XP read profile 的共同合成映射；
- B23a 的 policy/capability/core 三方交集、候选上限、稳定错误与外部调用顺序；
- 同一 adapter 进程中的双 tenant binding、policy、quota、observation 和失败隔离；
- 测试私有人员/联系/关系/locator/raw-error canary 的边界丢弃；
- 可执行 fast fixture 与已有 Docker HTTP fixture 的证据分工。

仍未闭合：

- XI Customer 身份关联、exact binary MySQL 唯一键、软删除释放、隐藏冲突与并发事务；
- XP preview/diff、B05 落点/address slot、field-policy drift、采用事务、最小 provenance、retention/restore sweep；
- [B23-O/2](B23-O-official-source-readiness.md) 已冻结 Companies House/GLEIF 组织资格、RA/CH-number、许可、quota、TTL 与 conformance 设计；B23-CH 已实现 domain scheme，B23-RA 已实现 test-scope mapper，但 runtime catalog/digest、真实 adapter、共享 admission 和 live evidence 仍未实现；
- 任一真实商业合同、production attribution/TTL/额度/地域/删除能力，以及真实官方、开放或商业 adapter conformance；
- B23-SPLIT 对采用链路的正式子批次、API/Java/SQL/Flyway 与验收拆分。

因此 B23-XF 解除的是“跨模型查询组合 fixture 未定义/未执行”的门槛，不解除真实数据权利和 Customer 采用链路门槛。普通 B06 继续独立可用，B23a 生产默认继续 `NOT_READY`。
