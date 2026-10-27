# B23-O-L2 quota/readiness durable simulator

> 实现日期：2026-07-20
>
> 状态：`O2-06`、`O2-07`、`O2-08`、`O2-10` 的第二层合成 durable fixture 已可执行；没有真实 HTTP source、credential、生产 binding、生产 admission service 或 live conformance evidence

## 1. 范围

B23-O-L2 落在 `infra/external` 的 test source，不新增生产类、Spring Bean、配置、迁移或网络调用。它补上 [B23-O/2](B23-O-official-source-readiness.md) 三层 conformance 中的第二层 quota/readiness 机械证明：

- 固定两个 tenant 与两个 adapter replica；
- 两个 replica 各自打开同一份文件持久化 admission/readiness store，不持有权威 JVM 计数；
- replica restart 会丢弃 replica 对象并重新打开 store，已消费窗口、cooldown、并发槽、binding revision 和 readiness evidence 不会重置；
- provider simulator 独立记录 source call，并能注入 429、timeout、5xx 和阻塞调用；它不提供 expected-state calculator；
- readiness、quota window、cooldown、evidence expiry 和 observation expiry 全部使用可推进 UTC 虚拟时钟，不用 wall-clock sleep 判断时间语义。

持久化 store 使用独立 lock file、进程内同路径互斥、文件锁、临时 commit 文件、`force` 和同目录原子替换。这个实现只用于可重启合成 fixture；它不是生产共享 admission 适配器，也不替代数据库/分布式存储选型和故障证明。

## 2. 已执行的 oracle

### `O2-06` quota 与共享 admission

- 合成 GLEIF profile 固定 provider ceiling `60/min`，测试 admission budget 明确小于 ceiling；这些数字只是 fixture 配置，不是生产默认；
- 12 个交错的 Alpha tenant 请求经两个 replica 竞争同一 tenant bucket，只有 6 个进入 provider；Beta tenant 仍能使用自己的公平额度；
- 名称搜索停止后仍保留两个 exact request 和一个 control probe 的正数 reserve；总 provider call 不超过共享 budget；
- 两个 replica 共用一个 operation concurrency slot，第二个并发调用在 source I/O 前被拒绝；slot 只释放一次；
- 两个 replica 重建后仍看到已耗尽窗口，不能用 restart 重置额度。

### `O2-07` 调用成本、失败与零重试

- capability 只读 durable readiness snapshot，provider call 和 admission consumption 都为零；
- search/exact 每次成功 admission 最多调用 provider 一次；
- 429 按共享 provider quota identity 写入 cooldown，同窗口后续 tenant/replica
  都在 source I/O 前返回 `LIMITED`；
- timeout 与 5xx 各自只有一次 provider call，不 retry、不切换 source profile；
- admission store 不可用返回 `UNAVAILABLE`，provider call count 不变；一个 tenant
  的 timeout/5xx 不改变另一个 tenant 的调用能力，但共享 provider 429 会关闭整个
  quota identity 的当前窗口。

### `O2-08` readiness/observation 时间边界

- readiness expiry 取 adapter、核心五分钟、rights、conformance 和 catalog 的最早时间；在边界时刻立即失效；
- observation expiry 取核心五分钟、adapter maximum 和 tenant policy maximum 的最严值，fixture 证明较短的 45 秒 policy 生效；
- checkedAt 位于未来、adapter TTL 超过五分钟或核心时间加法溢出都在 source I/O 前失败关闭；
- durable revoke 对另一个 replica 立即可见，且不撤销另一个 tenant 的 snapshot；
  已进入 source I/O 的调用在返回 observation 前复验同一 snapshot 证据，调用期间的
  revoke 或 binding/snapshot 漂移不能返回 `SUCCESS`。

### `O2-10` 双 tenant/双 replica 隔离

- source profile、build、binding revision、policy/catalog/conformance digest 必须同时匹配 durable current binding、snapshot 和 replica manifest；
- Alpha binding/policy/catalog revision 漂移后，两个旧 replica 都不能继续使用旧正向证据，Beta 保持 ready；
- 新 conformance snapshot 发布后，旧 build/binding replica 仍失败关闭；重建为当前 manifest 的 replica 才恢复；
- quota/readiness 文件在重建后保留，source call、tenant partition 和 provider profile projection 可由独立测试断言读取。

## 3. 文件与运行方式

- `OfficialSourceReadinessDurableSimulator`：虚拟时钟、文件持久化 admission/readiness store、双 replica 和独立 provider simulator；
- `OfficialSourceReadinessDurableSimulatorTest`：7 个 `O2-06/07/08/10` 场景测试。

聚焦执行：

```bash
sh ./mvnw -pl infra/external -am \
  -Dtest=OfficialSourceReadinessDurableSimulatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## 4. 未完成边界

B23-O-L2 自身没有实现 HTTP/DNS/TLS/redirect/content-type/schema/identity mismatch 注入，也不单独宣称 `O2-09/11`；这些第二层合成证据现由 [B23-O-L3](B23-O-L3-http-security-simulator.md) 的离线 scripted transport、完整 sink inventory 和安全 manifest 补齐。组织资格、status/standing 和 RA mapper 仍由 L1/后续真实 adapter lane 承担，`O2-12` 必须由私有 live runner 完成。

本 fixture 不实现或消费 Companies House scheme、GLEIF/Companies House adapter、credential、egress、真实 quota header、rights review 或 production policy。B23-CH 已在独立 domain/API 变更中补齐 Companies House scheme，但不改变本 simulator 的范围。它不能产生 production `READY`，不能把合成数值写成真实来源额度证据，也不改变 B23a 的 `EPHEMERAL_ONLY` 硬上限和默认生产 `NOT_READY`。
