# B24-X/1 safe-file composed model fixture

> 实现日期：2026-07-20
>
> 状态：`X-01..X-08` 的独立模型层已可执行；没有生产文件类型、API、迁移、MySQL durable composition、真实 HTTP streaming、对象存储、文件系统、scanner 或 current-build readiness evidence

## 1. 范围

本层位于 `infra/external` 的 test source，只使用生成式 benign bytes 和合成 scanner observation。它把 target、core、storage-private 与 inspection-private store 分开，重启 core/storage/inspector process object 时只保留对应 durable model store，进程内 handle 和 worker 对象不能充当权威状态。

fixture 固定执行：

- `XS`：versioned-object storage + streamed inspection；
- `XM`：versioned-object storage + managed exact-version event；
- `XL`：secure-local generation + streamed inspection；
- full-only 与 native single-range operation mode；
- tenant A Work Task 与 tenant B Customer 主目标，以及 target-first 授权、Work history 和 Customer pool/parent lifecycle。

`XL + managed event` 被显式报告为 `UNSUPPORTED`，不会静默跳过或伪造托管 exact-version capability。所有类型均只存在于 test source，不新增 Spring Bean、配置、端口、公共 API 或生产 classpath。

## 2. 已执行的组合 oracle

### X-01：意图、readiness 与双 tenant

- 相同声明与相同 bytes 在 A/B 中生成不同的 planned identity digest、file namespace 和 binding；
- cross-tenant target 在 file/storage 读取前返回相同安全失败；
- storage/inspection readiness 缺失只阻止对应 tenant 的新意图，另一 tenant 不受影响；
- planned locator 先进入 core store，storage effect 在意图提交前为零。

### X-02：store/finalize crash

- storage effect 后 receipt 丢失会留下 `UPLOADING`，重启只能按 planned locator probe；
- receipt 与 MySQL-model finalize 之间保持不可发布；
- finalize commit acknowledgment 丢失后，core restart 重读 `STORED + PENDING` 与唯一 inspection job；
- duplicate finalize 不增加 store effect、job 或 file version。

### X-03：inspection lease/fence

- streamed lane 对 exact bytes 做 storage inspection read，managed lane 对 exact version 做 probe；
- native result 后 inspector crash 可由同 attempt 的 durable observation replay；
- type/scanner evidence 由 core 独立归一为 `CLEAN/REJECTED/INDETERMINATE`；
- old fence、wrong inspection revision、wrong tenant/binding 不能提交；
- `CLEAN` 仍为 `PRIVATE`，只建立唯一 publication work。

### X-04：发布全序

- publisher 不接收 human authority，只在 target/core transaction 中复核 parent、relation、stored identity 与 clean state；
- clean publication 至多写一次；
- publish 前 withdraw 或 parent delete 不会形成短暂 `AVAILABLE`；
- available 后到达同 exact subject 的 threat evidence 会先关闭 publication，再进入唯一 deletion work。

### X-05：full/range

- `4096` bytes 对象覆盖 `512-1535`、open-ended、suffix 与 end-clamping；
- unsatisfied、multi、non-decimal、reverse、zero suffix 与 numeric overflow 都在 storage open 前返回 `416`；
- full-only profile 不 fallback full；
- upstream bytes 精确等于已选择区间，handle 只 close 一次，transparent reopen 为零；
- 每次 full/range 都重新执行 membership、`files:read`、target predicate 与 exact target/file 授权。

### X-06：授权与父生命周期

- Work Task attachment 只使用 task participant/manager projection，不从隐藏 CRM link 获权；
- target read loss、file capability loss 与 Customer `IN_POOL` 立即阻止新 list/full/range，但不触发 storage delete；
- 恢复后只重新开放仍为 `AVAILABLE` 的对象；
- 任一 file intent/history 使 Work Task delete 保持 conflict；
- Customer parent delete 在本地状态中先写 `DELETED/REMOVING` 与唯一 deletion work，之后所有 public read 在 storage 前失败；
- inspection 已 durable 接受后，uploader 后续失去 write 不会让 worker 依赖 human grant 或阻止安全 publication。

### X-07：exact delete 与 authoritative absence

- withdraw 先关闭新 read，并 best-effort cancel 活动 handle；
- delete effect 后 storage process crash 只重投同一 logical deletion/exact generation，effect count 仍为一；
- core 在独立 absence confirmation 前保持 `REMOVING`；
- confirmation 同时要求 exact generation absent、同 binding revision 与 file-local handle drain；
- wrong-tenant evidence、late duplicate commit 和 commit acknowledgment loss 都不能复活对象或第二次写 `REMOVED`。

### X-08：capacity、cleanup 与 safe report

- record cap 与 download concurrency 分别作为 target 和 global operation 的显式 fixture limit；
- 普通 download slot 饱和时，另一 file 的 deletion control trace 仍能 effect/confirm/commit；
- expired incomplete intent 只按 planned locator probe 并建立 exact cleanup，不调用 storage list；
- machine-safe report version 固定为 `B24-X/1` 并标记 execution layer=`MODEL`，只包含状态 digest、枚举 lane 和计数；locator/name/scanner canary、bytes 与 checksum 原值不进入报告。

每个 checkpoint 还断言 external I/O during transaction、cross-tenant mutation、storage list、invalid-range open、transparent reopen、double release、second logical publication/deletion 与 forbidden-sink hit 全为零。

## 3. 文件与运行方式

- `SafeFileComposedFixture`：分离 store、可重建 process、driver、native storage/inspection observation、range normalizer、resource counter 与 safe report；
- `SafeFileComposedFixtureTest`：`X-01..X-08` 的 11 个执行用例，其中 `X-03` 参数化运行 `XS/XM/XL`。

聚焦执行：

```bash
sh ./mvnw -pl infra/external -am \
  -Dtest=SafeFileComposedFixtureTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## 4. 未完成边界

本 fixture 是 [B24-X](B24-X-composed-fixture.md) 的第一层独立模型，不是其中要求的第二层 MySQL/Testcontainers/application transaction/真实 HTTP stream composition，也不是第三层 current storage/scanner build conformance。它不证明 MySQL unique/FK/lock/lease、secure-local filesystem primitives、S3 version/delete behavior、scanner/container sandbox、跨 replica admission、真实进程终止、backup/replica deletion 或生产 policy。

因此 production 仍没有文件 API、attachment schema、storage/scanner Bean 或正向 readiness。B24-SPLIT 和后续 B24a/b 实现必须复用同一 `X-01..X-08` oracle 建立 durable composition；任何 real lane 还必须单独提供 current build/config/binding evidence。
