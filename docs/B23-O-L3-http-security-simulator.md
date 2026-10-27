# B23-O-L3 离线 HTTP/security conformance simulator

> 实现日期：2026-07-20
>
> 状态：`O2-09`、`O2-11` 的第二层合成 evidence 已可执行；没有真实 socket、endpoint、credential、生产 adapter/source binding、真实数据或私有 live `O2-12` evidence

## 1. 范围

B23-O-L3 位于 `infra/external` test source，继续补齐 [B23-O/2](B23-O-official-source-readiness.md) 的 durable simulator 层，但不修改 `src/main`、Spring 组合、配置、迁移或生产 readiness。它和 [B23-O-L2](B23-O-L2-durable-simulator.md) 分工如下：

- L2 证明双 tenant/双 replica 共享 admission、quota/reserve、TTL、撤销、漂移与重启语义；
- L3 用一个不打开 socket 的 scripted transport 证明 HTTP/DNS/TLS/redirect/content-type/body/schema/identity failure 的安全归一、零 retry、零 redirect follow、零 observation 和 operation snapshot 影响；
- L3 同时枚举所有 O2-11 sink，使用显眼合成 canary 扫描诊断投影和测试 artifact，并输出可以由 JDK `Properties` 重新解析的安全 manifest。

原始 request、header、body 和 native value 只在单次测试调用内短暂存在。它们的对象诊断输出固定 redacted，file-backed operation snapshot 与 manifest 都不保存这些值或它们的普通 hash。

## 2. `O2-09` 错误矩阵

fixture 执行 19 个固定场景：

| 输入 | 安全结果 | operation snapshot |
| --- | --- | --- |
| HTTP 401/403 | `UNAVAILABLE / SOURCE_AUTH_FAILED` | 撤销当前 operation |
| exact 404 | `NO_RESULT` | 保留 |
| search 404 | 成功空页 | 保留 |
| HTTP 400/422 | `UNAVAILABLE / CONTRACT_DRIFT` | 撤销当前 operation |
| HTTP 429 | `LIMITED` | 保留；cooldown/quota 由 L2 证明 |
| HTTP 500/503 | `UNAVAILABLE / SOURCE_SERVER_ERROR` | 保留 |
| DNS/TLS failure | `UNAVAILABLE` | 保留 |
| redirect | `UNAVAILABLE / REDIRECT_REJECTED` | 撤销当前 operation；follow count 为 0 |
| content-type mismatch | `UNAVAILABLE / CONTENT_TYPE_DRIFT` | 撤销当前 operation |
| 空、超限、malformed 或非法 UTF-8 body | `UNAVAILABLE` | 撤销当前 operation |
| schema mismatch | `UNAVAILABLE / SCHEMA_DRIFT` | 撤销当前 operation |
| exact identity mismatch | `UNAVAILABLE / IDENTITY_MISMATCH` | 撤销当前 operation |

每个场景恰好发生一次 synthetic provider call、零 retry、零 redirect follow、零 observation。撤销只作用于当前 tenant/current operation；同 tenant 的另一 operation 和另一 tenant 保持 ready。新建 replica 会从 file-backed store 看到撤销，已撤销 operation 的后续调用在 source I/O 前失败关闭。

## 3. `O2-11` sink inventory

inventory 必须且只能包含以下 13 类面：

- native DTO diagnostic、普通对象 diagnostic、对外 HTTP error/empty output 和 exception；
- log、trace、metric；
- event、Activity、cache、report；
- test artifact 和 conformance manifest。

native request/response 的 `toString` 只输出 redacted 形状；普通结果、HTTP 错误和 telemetry 只保留稳定枚举。event、Activity、cache 与 report 明确记录为 `NO_WRITE`。每个场景结束后，独立 leak oracle 扫描完整 canary 集；任一 sink 缺失、重复、场景失败或 leak hit 非零都会阻止报告构造。最终测试还递归扫描临时目录中的 operation snapshot 和 manifest，要求命中数为零。

## 4. 安全 manifest

`SafeManifestWriter` 生成 UTF-8 properties manifest，并立即用 `Properties.load` 重新解析。报告包含：

- fixture/lane/adapter contract、不可变 test build、`TEST_ONLY` deployment class、source profile 和 binding-policy schema；
- provider documentation/schema、qualification/registrar catalog 的安全配置 digest；
- 支持的 operation/jurisdiction/identity type、candidate limit；
- quota scope/window/ceiling、本地 budget、exact/control reserve、replica 数与 admission profile digest；
- rights evidence digest、reviewedAt/validUntil、`EPHEMERAL_ONLY` usage mode；
- 每个 scenario 的 pass、安全 reason/outcome、snapshot effect、provider/retry/redirect/partition/leak 计数；
- 每个 sink 的 disposition、检查次数与 leak-hit count；
- checkedAt/validUntil、runner/environment identity digest，以及显式 `production.ready=false`、`live.evidence.present=false`、`real.values.persisted=false`。

digest 只对固定的安全配置 identity 计算，不对 LEI、query、名称、地址、credential、header/body、URL、ETag 或错误原文计算。manifest 写入前和写入后都执行 canary 扫描。

## 5. 文件与运行方式

- `OfficialSourceHttpSecuritySimulator`：scripted transport、per-operation durable snapshot、sink inventory/leak oracle 与 safe manifest writer；
- `OfficialSourceHttpSecuritySimulatorTest`：`O2-09/O2-11` 的三个 executable tests。

聚焦执行：

```bash
sh ./mvnw -pl infra/external -am \
  -Dtest=OfficialSourceHttpSecuritySimulatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## 6. 未完成边界

B23-O-L3 不解析真实 GLEIF/Companies House response，不验证真实 host/auth/TLS chain、quota header、schema/category/status/RA shape、network topology 或 source documentation drift；这些仍属于私有 live `O2-12` runner。L3 的 test build、合成 quota 数值、synthetic rights digest 和安全 manifest 不能产生 production `READY`，也不能替代 L1 mapper、L2 admission/readiness、RA mapper、部署 policy 或 Customer/MySQL 采用 fixture。
