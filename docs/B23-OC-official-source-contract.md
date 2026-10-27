# B23-OC 官方/开放源适配器离线契约骨架

> 启动日期：2026-07-20
>
> 状态：离线契约骨架、B23-O-L1 GLEIF mapper lane 与 B23-RA RA/EntityID 映射已建立；没有真实 HTTP adapter、凭证、endpoint、生产 Bean 或生产流量

## 1. 本轮范围

B23-OC 先把真实官方/开放源进入 `EnterpriseDataSource` 前必须满足的最小可执行契约固定下来，不选择或启用生产数据源。骨架位于 `infra/external` 的 test source：

- `OfficialSourceEnterpriseDataContract` 是可继承的契约；
- `OfficialSourceEnterpriseDataContractSkeletonTest` 用纯内存、显眼合成的 native-shape driver 执行它，证明契约自身可运行；
- `GleifEnterpriseDataContractTest` 已作为 B23-O-L1 首条继承者，用 stub transport、合成 JSON:API-like LEI payload 和 test-scope mapper 执行同一契约；
- `GleifRegistrationAuthorityIdentifierMapper` 由 B23-RA 在同一 test scope 中交叉校验三条 pinned RA、RA-scoped Companies House 号码类别与既有 scheme validator；
- driver 没有 HTTP client、DNS、endpoint、credential、Spring stereotype 或配置开关；
- `src/main`、`conf/application-prod.yml` 与组合根不新增真实来源，生产仍由 `DisabledEnterpriseDataSource` 和 disabled policy 失败关闭。

公共登记身份目录已由 B23-CH 加入 `UK_COMPANIES_HOUSE/GB_CH_NUMBER` 和显式八位号码类别校验。B23-RA 只在 GLEIF test lane 中补齐离线 RA mapper：`RA000585/586/587` 必须命中各自 pinned 号码类别，EntityID 再由既有 scheme validator 规范化；未知 RA、缺失或错误号码类别不会猜测第二身份。该变化只解除 offline mapper 证据缺口；真实 Companies House/GLEIF lane 仍缺少 HTTP adapter、组织资格或 schema transport 的当前 build evidence、来源政策、额度 admission 和 live conformance，不能借 scheme、测试骨架、L1 或 B23-RA 宣称 source ready。

## 2. 契约 checkpoint

未来 adapter test 必须用 stub transport 和纯合成 provider-native payload 为每个场景创建一个 `EnterpriseDataSource`，继承同一契约并通过以下检查：

1. capability 只发布有界名称搜索与精确登记身份查询，候选上限不超过核心的 20；
2. 精确查询保持完整 scheme-qualified identity，主体状态与 identifier standing 正交；
3. 名称搜索遵守调用方 limit 和 legal jurisdiction，不开放 provider filter、分页或任意参数 map；
4. 未知 native 状态保守映射为 `UNKNOWN`，不能默认 active/current；
5. partial 明确保留，人员、联系、关系、locator、raw error 与未来额外字段在 adapter 边界丢弃；
6. 无结果不携带 provider 诊断，限额、不支持、网络/解析/schema/identity mismatch 只缩减为稳定的 `EnterpriseDataSourceError`；
7. 异常消息不包含请求、响应、账号、endpoint、credential 或 fixture canary。

这些 checkpoint 验证 adapter 到公共输出端口的缩减，不证明真实数据准确性、许可、attribution、地域、额度、组织资格或生产 readiness。真实 source policy 仍由独立 `EnterpriseDataReadPolicyResolver` 提供，不能从 payload 或本契约测试推断。

## 3. 接入真实 adapter test 的方式

真实 adapter 出现后，在它自己的测试范围中：

1. 继承 `OfficialSourceEnterpriseDataContract`；
2. 实现 `ContractDriver.fixture()`，只使用通过 LEI/USCC 校验但明显虚构的标识与组织事实；
3. 实现 `ContractDriver.sourceFor(Scenario)`，由 stub transport 固定返回对应 native payload 或 transport failure；
4. 对 `MALFORMED_RESPONSE` 和 `IDENTITY_MISMATCH` 在 adapter 内收敛为 `UNAVAILABLE`；
5. 保持契约测试离线。任何 live sandbox/canary 检查都必须属于独立、显式 opt-in、凭证隔离且默认不执行的 conformance 工作流，不能加入普通 Maven 测试。

## 4. 明确未完成

- 没有 GLEIF、Companies House 或其他官方源 HTTP 实现；
- 没有真实响应 fixture、真实主体标识或供应商错误正文；
- 没有生产 source binding、policy、attribution、secret 或 quota 配置；
- 没有 Customer 择取、XI identity reservation、provenance、retention 或 MySQL 竞态实现；
- 没有生产 RA catalog loader、artifact digest 校验或 runtime mapper registration。

因此，B23-OC 把 adapter mapping 的验收形状变成可执行契约，B23-O-L1 证明 GLEIF synthetic mapper 可继承它，B23-RA 证明三条 Companies House RA 的离线缩减；三者都不改变 B23a 的 runtime 和生产流量边界。验收见 [B23-O-L1](acceptance/B23-O-L1.md) 与 [B23-RA](acceptance/B23-RA.md)。
