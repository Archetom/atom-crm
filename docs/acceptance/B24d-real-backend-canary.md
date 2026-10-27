# B24d secure-local / ClamAV 真实后端 canary 验收记录

> 执行日期：2026-07-23
>
> 证据等级：`REAL_BACKEND_CONFORMANT`（仅列出的 backend 子场景）；整体 B24：`DURABLE_SIMULATED`

## 交付

- `ClamdInstreamStreamingInspectionEngine` 使用 ClamAV `INSTREAM` length-prefixed protocol，运行时只接受 Unix-domain socket；native TCP 永不作为生产 scanner transport。
- `FileSecureLocalAttachmentConformanceReportSource` 只读取 owner-only JSON 文件；缺失、符号链接、宽权限、解析失败或任意 drift 都返回 no report，进而维持 `NOT_READY`。
- `B24SecureLocalCanaryRunner` 是独立进程入口，执行真实 secure-local adapter 和 ClamAV 的合成 canary，并仅写入 digest、definition basis/expiry 和 scenario ID。它不接受客户文件、文件名或 raw scanner reply。
- loopback TCP 只存在于 runner 的 isolate mode，报告的 `adapterBuildDigest` 与 `engineBuildDigest` 固定为 `CANARY_LOOPBACK_ONLY`，不能满足 runtime manifest 的 current-build 条件。
- runner 现也接受**恰好一个** `--clamd-unix-socket`，该 Linux 模式实例化生产同一 `ClamdInstreamStreamingInspectionEngine` 并写入当前 runtime build digest；它仍必须执行完整 scenario 集合才可能被 manifest 接受。`--clamd-loopback-port` 与 Unix socket 不能同时使用。

## 实际 canary

输入仅为合成 PDF、PNG、JPEG 和 EICAR 测试字符串；未使用客户文件、真实恶意样本或真实登记/CRM 数据。

本次已在一次性 Linux Docker 拓扑中重跑 Unix-domain socket 路径：ClamAV 和 JDK runner 均以不可变 OCI digest 启动；初始 ClamAV 容器只为下载公开 definition database 而短暂接入默认网络；同一数据库卷随后只挂给内部 Docker 网络中的 `clamd`。Java runner 位于 `--network none` 容器，存储根和 report 为独立 Linux volumes，Unix socket 为只读挂载。scanner 本身不具备外网，runner 无网络或 TCP scanner path。报告目录实际为 `0700`，报告文件实际为 `0600`。该 Linux 容器实测使用同一生产 `ClamdInstreamStreamingInspectionEngine`，不是 loopback-TCP isolate mode；它仍不是经批准的生产 Linux 主机或完整部署清单。

本次 runner 还在同一 secure-local volume 上只用生成的零字节流写入并删除 1 byte 与 64 MiB 对象，记录 adapter 的最大 read request 不超过 64 KiB；空对象、64 MiB+1、store 端超限、unknown/short/long length、延迟流和中断流都必须失败，并经 exact `abortIncomplete` 收敛为 absence。secure-local adapter 现在在 byte-store 边界重复这一 64 MiB ceiling 与 transport-proven length 条件，因此直接 port 调用不能绕过 application admission 扩大写入面。

同一次 Unix-socket runner 也构造了完整的 opt-in profile，但只提供空的独立 report source。profile policy/storage 均保持 disabled，fallback `AttachmentStorage` 在 `allocate` 与 `open` 前固定失败；真实 root 或可用 scanner 不能替代 manifest。这为 `S-01` 增加当前 build 的 Linux/runtime composition 证据，且不允许该负向检查产生 readiness。

同一次 runner 还为 `STORE_CONTENT_FORCED`、`STORE_DESCRIPTOR_FORCED`、`STORE_DIRECTORY_FORCED`、`DELETE_CONTENT_REMOVED` 与 `DELETE_DESCRIPTOR_REMOVED` 各启动一个隔离子 JVM。子进程在真实 local force/remove 边界以 `halt` 终止；父进程只经私有管道取得一次性 opaque state，随后只用原 planned locator 或 stored identity 确认 partial state、同一 receipt 或 exact cleanup。该状态不会写入报告或控制台。这是进程崩溃收敛证据，不是主机断电或副本/备份销毁证明。

runner 还在同一 Linux volume 实测拒绝宽权限 root、symlink root、运行中 mode drift、root rebind 与低于 `local-free-floor-bytes` 的容量配置。报告把它们列为不含路径或容量值的 `observedChecks`，而非 `passedScenarios`：尚未覆盖设备/mount drift、entry rename/replace、partial-entry 全矩阵与跨进程容量仲裁，故 `S-10` 仍不能标记通过。

可复现实测入口为：

```bash
scripts/b24-linux-unix-socket-canary.sh --report /absolute/path/b24-linux-report.json
```

脚本拒绝覆盖报告，运行前构建当前 runtime classpath，使用随机且明确命名的容器/volume/network，并在退出时删除它们。导出的 owner-only JSON 不在仓库中；它只有 digest、definition basis/expiry 和 scenario ID，不含 root path、binding 原值、文件 bytes、checksum、scanner reply、EICAR 内容或凭据。

`scripts/b24-conformance-matrix.sh` 把独立 backend report 与每次重新运行的 contract/model/MySQL lanes 合成为一份矩阵。必须先生成新的 backend report、再立刻运行 matrix，避免其五分钟 definition TTL 在长 MySQL lane 中失效：

```bash
scripts/b24-linux-unix-socket-canary.sh --report /absolute/path/b24-backend.json
scripts/b24-conformance-matrix.sh \
  --backend-report /absolute/path/b24-backend.json \
  --report /absolute/path/b24-matrix.json
```

matrix 会重新运行 `AttachmentStorageConformanceTest`、`AttachmentInspectionConformanceTest`、`SafeFileComposedFixtureTest` 以及 B24a/b/c 的 MySQL/Testcontainers suite。它的 `layers` 明确区分 `CONTRACT_CLOSED`、`DURABLE_SIMULATED` 和真实 Linux/ClamAV backend；为防止模拟结果越级，顶层 `passedScenarios` 仍只有实际 backend 的十四项，`missingRealBackendScenarios` 逐项列出未证明场景，且 `productionReady` 永远为 `false`。

机器报告通过 JSON 解析与 owner-only `0600` 权限检查，实际通过的 subset：

- `S-01`, `S-02`, `S-03`, `S-04`, `S-05`, `S-06`, `S-07`, `S-08`, `S-11`
- `I-01`, `I-02`, `I-07`, `I-12`, `I-13`

该报告为临时部署证据，未提交到仓库；不含 locator、root path、binding 原值、文件 bytes、checksum、scanner version/reply、EICAR 内容或任何凭据。Linux Unix-socket 子集的证据等级是 `REAL_BACKEND_CONFORMANT`；此前 loopback-TCP isolate mode 只能证明 `CANARY_LOOPBACK_ONLY`，不能替代这一结论。

同日重新执行的 MySQL/Testcontainers durable lane 也通过：`AttachmentPersistenceIntegrationTest`（9 tests）、`B24aWorkTaskAttachmentIntegrationTest`（2）、`B24bCrmAttachmentTargetIntegrationTest`（14）、`B24cFileFieldIntegrationTest`（2）以及 `AttachmentComposedConformanceTest`（3）。这证明当前实现的持久化、target 隔离、文件字段与组合 runtime 在受控测试 profile 可回归；它们使用合成 scanner/fixture，故证据等级最多为 `DURABLE_SIMULATED`，不能把真实 ClamAV subset 扩写成全量真实 backend 或生产 readiness。

执行命令：

```bash
CI=true sh ./mvnw -q -pl start -am test \
  -Dtest=AttachmentPersistenceIntegrationTest,B24aWorkTaskAttachmentIntegrationTest,\
B24bCrmAttachmentTargetIntegrationTest,B24cFileFieldIntegrationTest,\
AttachmentComposedConformanceTest -Dsurefire.failIfNoSpecifiedTests=false
```

## 未闭合门槛

`S-01..S-14`、`I-01..I-14`、`X-01..X-08` 的完整 current-build 三层集合尚未完成，尤其 MySQL/Testcontainers durable crash composition、真实生产 Unix socket/scanner isolation、policy/binding/current build 的完整 manifest、跨 replica fencing 和 X lane。因此：

- `PROD_READY`：未达到；
- production `atom.files.enabled` 与 workers 继续为 `false`；
- 当前 partial manifest 不能使任何附件 storage/inspection operation ready；
- 需要在批准的 Linux secure-local volume 与 Unix-socket `clamd` 上重跑全量 runner，并把完整 owner-only report 交给 runtime，才可评估生产启用；本次临时 Docker Linux 子集不是该批准环境。
