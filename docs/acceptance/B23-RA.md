# B23-RA GLEIF RA/EntityID 离线映射验收记录

> 完成日期：2026-07-20
>
> 状态：test-scope 离线 mapper 与负向 fixture 已完成；生产仍为 `NOT_READY`

## 范围与交付

B23-RA 只扩展 `infra/external/src/test` 的 B23-O-L1 GLEIF lane：

- `GleifRegistrationAuthorityIdentifierMapper` 固定目录版本 `GLEIF-RA-1.8.1/B23-CH-2026-07-20`，不读取网络、文件、环境变量或自由文本 authority；
- `RA000585` 只接受 England and Wales 的纯数字及 pinned 前缀类别，`RA000586` 只接受 Northern Ireland 类别，`RA000587` 只接受 Scotland 类别；
- RA 类别通过后仍构造既有 `RegisteredIdentifierKey(UK_COMPANIES_HOUSE, GB_CH_NUMBER, EntityID)`，因此八位 ASCII、大小写、前导零与显式前缀规则不会在 adapter 复制或放宽；
- 生成的 Companies House identity standing 固定为 `UNKNOWN`，不会把 GLEIF LEI `RegistrationStatus` 误用于本地登记号；
- `GleifLeiRecordMapper` 继续以 LEI 为 primary identity，并按 primary、secondary 的稳定顺序输出去重 identity 列表。

实现没有修改 `src/main`、公共 API、domain scheme、配置、组合根或数据库，也没有增加 HTTP client、endpoint、credential、Spring Bean、真实数据或生产 source policy。

## 失败关闭

- 未知或大小写不匹配的 RA、缺失/空白 EntityID 不生成第二身份；不会尝试 `OtherRegistrationAuthorityID`、validation authority、URL、名称或地址回填。
- 一个全局合法的 Companies House number 若落入其他 RA 的号码类别，也不生成第二身份；例如 `RA000585 + SC...`、`RA000586 + 8 位纯数字`、`RA000587 + NI...` 均被拒绝。
- 未知前缀、七位值和 Unicode lookalike 继续由 B23-CH validator 拒绝。
- 以上 RA 失败不改变已经通过资格和 LEI 校验的主 profile；精确查询若 primary LEI 与 selector 不一致，则即使 RA/EntityID 有效也整体缩减为安全 `UNAVAILABLE`。

## 验证

```bash
sh ./mvnw -pl infra/external -am \
  -Dtest=GleifRegistrationAuthorityIdentifierMapperTest,GleifEnterpriseDataContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：19 个测试通过，其中 RA mapper 4 个、GLEIF/B23-OC 组合 15 个；覆盖三条正向 RA、未知 RA、缺失 EntityID、错误号码类别、scheme validator 失败及 primary identity mismatch。

## 剩余门槛

B23-RA 是 mapper 层离线证据，不是 GLEIF RA artifact 的 runtime loader，也不验证真实 payload、HTTP/TLS/redirect/content-type/schema、当前 build manifest、rights/attribution、共享 admission 或 live source。真实 adapter 必须把相同目录版本与 digest 纳入五分钟 readiness 交集，并重跑三层 conformance；否则生产继续使用 disabled source/policy。
