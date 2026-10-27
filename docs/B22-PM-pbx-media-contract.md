# B22-PM PBX 录音媒体组合契约 fixture

> 决策日期：2026-07-19
>
> 状态：PBX 录音媒体侧跨 B22-R/D/S/M/A/CP 的实现前组合契约已闭合；本文件冻结可由 durable 模拟 adapter 与真实私有 PBX adapter 共同执行的场景、检查点和通过条件，不实现 adapter、核心运行时、HTTP API、表、配置或真实录音
>
> 输入：[B22 总规划](../batch-B22.md)、[B22-R 可靠性设计](B22-R-reliability.md)、[B22-D 敏感数据设计](../batch-B22.md#14-b22-d-敏感数据录音访问与保留设计闭合)、[B22-S 状态与拓扑设计](spikes/B22-state-and-topology.md)、[B22-M 媒体传输设计](spikes/B22-media-transfer.md)、[B22-A readiness/隔离设计](B22-A-adapter-readiness.md)、[B22-CP PBX 核心组合契约](B22-CP-pbx-composed-contract.md)、[B22-PD PBX 敏感数据组合契约](B22-PD-pbx-sensitive-data-contract.md)和 [PBX 事件流 spike](spikes/B22-pbx-event-stream.md)

## 1. 决策摘要

B22-PM 固定一套确定性的双 connector PBX stored-recording/media fixture。它复用 B22-CP 的 tenant、connector、interaction 和 fenced stream 语义，但每个场景从独立空 store 开始，不能依赖 B22-CP 测试进程遗留的内存对象。`B22-PM/1` 必须组合证明：

- live recording、呼叫终态、recording finished/stop 事件和 stored object 可读是不同证据；录音可以晚于 interaction 终态到达，只有受治理 scope 中稳定、完整、格式合法且未超限的 stored object 才产生 `AVAILABLE`；
- recording name、spool root、路径、symlink、ARI/ESL endpoint、credential 和原始事件只存在于私有 adapter。核心保存的只是 tenant/connector/purpose 绑定的受保护 opaque ref；同名对象不能跨 connector 命中；
- 完整媒体读取与每一个 single-range 读取都重新执行 B22-D/M 人工授权并提交独立 access attempt。完整读取返回规范 `200`；只有 profile 证明 immutable representation、稳定总长度和原生精确区间读取时才返回 `206`；
- 客户端断开、open/idle/stream deadline、最大字节、录音截止和 removal 都能取消上游 handle。首字节后不透明 reopen、续传、拼接和 JSON/provider error 全部禁止；
- 对象大小、单请求字节、缓冲、来源连接、member、tenant、connector 和 process 活动流都有 hard limit。达到上限不排队保存 open command、不跨 tenant 淘汰、不阻塞 deletion control reserve；
- 到期先停止访问并 durable 投递唯一 removal command。delete 调用成功、普通媒体 `404`、PBX 断线或暂时不可读都不是 `REMOVED`；只有同一受治理 storage scope 的权威不存在确认提交后，核心才进入 `REMOVED`；
- 每一步同时检查 adapter registry/journal/ledger、核心 inbox/recording/access/removal 投影、活动资源计数、B connector 未变化和禁止值扫描，而不是只检查最终 HTTP status。

本文件不把 Asterisk `GET .../file` 的存在推断成 range capability，也不把 FreeSWITCH `RECORD_STOP` 推断成文件已经完成、稳定且可安全读取。通过设计 fixture 只表示 oracle 已冻结，不表示任何真实 PBX build、storage backend、线路或 B22 runtime 已通过。

## 2. Fixture 边界、profiles 与 driver

### 2.1 固定双 connector 世界

一轮 fixture 使用下列无敏感值别名；真实 provider identity 不得进入核心或报告：

| Fixture identity | Connector A | Connector B |
| --- | --- | --- |
| tenant/connector | `tenant-a` / `connector-a` | `tenant-b` / `connector-b` |
| governed PBX binding | `pbx-binding-a`, revision `r51` | `pbx-binding-b`, revision `r73` |
| stream/app | `stream-a` / `app-a` | `stream-b` / `app-b` |
| interaction | `interaction-a`, 已有 `AGENT`/`REMOTE_PARTY` 两腿 | 独立 `interaction-b` |
| recording | `recording-a` | 独立 `recording-b` |
| 私有 native 对象 | `native-recording-a` | 可合法使用同名 token，但属于 B 分区 |
| 核心 access/removal | `access-a-*` / `removal-a-1` | 独立 identity namespace |

主媒体对象是合成 `WAV`，长度固定为 `4096` bytes，包含合法固定 header 和无真人语音的确定性 payload；另有等价小型合成 `MP3` 变体用于 representation kind/header 检查。fixture 还提供 `8193` bytes 的明确超限对象、声明长度与实际流长度不符的 contract-failure 对象和不会把完整 payload 物化在 heap 的生成式慢流。任何样本都不能使用真实号码、真实通话或真实录音。

所有场景使用虚拟时钟。provider occurred time、adapter received time、核心数据库时间、policy deadline、open/idle/stream deadline 和 readiness expiry 分开推进，不用 wall-clock sleep 证明竞态。

### 2.2 两个读取 profiles

同一 build/profile 只能声明已经由本 fixture 证明的能力：

| Profile | Full | Single range | 必须证明 |
| --- | --- | --- | --- |
| `STORED_FULL_ONLY` | 支持 | 不支持；任何 `Range` 在 adapter open 前以 `416` 拒绝 | 一个完成且稳定的 WAV/MP3 stored object、受控顺序流、准确或安全未知的 full length、取消与上限 |
| `STORED_IMMUTABLE_NATIVE_RANGE` | 支持 | 支持 | 上述全部，加 immutable representation、稳定总长度、原生精确 offset/length 读取且 upstream read bytes 等于选择区间 |

ARI stored-file HTTP、受治理本地文件或对象存储私有 client 都只是 profile 的实现选择。上游支持任意 HTTP `Range`、SDK seek 或文件 seek 只有在相同 build/backend fixture 实际证明后才可声明 range；核心读取并丢弃前缀永远不能补出该能力。FreeSWITCH/Asterisk 暴露的原始 path/name 不属于 profile 输入。

### 2.3 可观察 store 与逐步 checkpoint

独立 oracle 必须只读观察已提交状态，不能调用被测 adapter 的“expected result”方法。每步 checkpoint 至少包含：

| 维度 | 检查内容 |
| --- | --- |
| native registry | connector-partitioned live/stored identity、ownership、format、稳定长度/immutability evidence、状态和 tombstone；path/name 仅以私有 canary 扫描，不输出原值 |
| reliability | adapter recording journal、core inbox outcome/digest、delivery ack、gap/reconciliation 与稳定 external ref binding |
| core recording | 四态、interaction/lifecycle/version、AEAD/HMAC envelope identity、policy/deadline、removal state 和 optimistic version |
| access | authorization/admission outcome、access attempt、数据库事务是否结束、open/first-byte/completion 分类 |
| stream | adapter open/read/cancel/close count、requested offset/length、实际 upstream bytes、sent bytes、buffer high-water、active handle |
| HTTP | status、固定 header allowlist、body digest/length、是否曾提交 response、redirect/provider header 数 |
| removal | core deletion outbox、adapter delete ledger/effect、confirmation journal/core inbox、重试、确认与 ref purge |
| readiness/capacity | build/profile/revision、read/range/delete operation gate、各层 hard limit、control reserve 和 A/B 分区计数 |
| leakage | 核心 persistence/非媒体 JSON API、HTTP target/query/cookie/header、日志、trace、metric、audit、exception、report 和对象 `toString` 的禁止值命中数；授权媒体 body 只由上一行单独校验 |

禁止值 canary 至少同时放入 native recording name、绝对/相对路径、spool root、symlink target、ARI/ESL URL、credential、external ref 明文、provider header/body/异常和媒体 chunk。媒体 chunk 只允许出现在该次已授权 byte-stream body 与私有 source；其余 canary 不得进入核心。测试失败输出只报告固定 reason 与 evidence digest，不能把命中的原值再次打印出来。

### 2.4 Driver 动作词汇

共享 harness 至少提供：

- `startLiveRecording(connector, interaction)`：在私有 registry 建立 live object，并经 journal 投递 `PROCESSING` candidate；
- `finishNativeRecording(connector)`：产生 recording finished/stop 事件，但不直接编辑 stored/核心状态；
- `publishStoredObject(connector, descriptor)`：让受治理 backend 出现完整对象，可独立控制可读、格式、长度、immutability、symlink 和 scope；
- `probeStoredObject(connector)`：执行 adapter 声明的只读 stored-object 完成证明；
- `requestFull(caller, recording)` / `requestRange(caller, recording, rangeHeader)`：经过真实 HTTP parser、人工授权、admission 和 adapter stream port；
- `abortClient(access)` / `advanceClock(deadline)` / `stallUpstream(access)`：触发 disconnect、deadline 或 idle path；
- `saturate(limitKind, partition)`：占满指定来源/member/tenant/connector/process admission，不制造虚假 recording identity；
- `beginRemoval(recording)` / `deliverRemovalCommand()` / `confirmAbsent()`：驱动真实 outbox/ledger/delete/confirmation 链；
- `restartAdapter()` / `restartCore()` / `cutAt(boundary)`：丢弃进程内 handle、future 和 dedup cache，只从 durable store 恢复。

driver 不能直接把 recording 改成 `AVAILABLE`/`REMOVED`，不能伪造 confirmation，不能绕过 HTTP/Range parser，也不能把 native path 作为核心 open command。

## 3. 初始不变量与固定 hard limits

每个场景产生第一条 recording fact 或媒体 request 前必须证明：

- A/B registration 活动且不可改绑；受治理 stream、native registry、journal、media admission、delete ledger、storage root/account 和 observability 均按 connector 分区；
- `interaction-a` 已由 B22-CP oracle 建立恰好两条规范腿；场景可从 `CONNECTED` 或 `TERMINATED` checkpoint 开始，但 recording 事实不能修改 lifecycle、dispatch 或 observation；
- tenant policy 已明确启用 recording，保存不可延长的 policy version、访问/删除绝对截止和 deployment ceiling；B22-D key provider 可用；
- `RECORDING_STATUS_OBSERVATION`、`RECORDING_MEDIA_READ` 与 `RECORDING_MEDIA_DELETE_CONFIRMATION` 绑定相同 adapter build、binding revision 和 storage scope；range profile 另有独立 capability；
- adapter registry 只允许由该 connector 的受治理 recording operation 创建的对象；任意 name/path lookup、目录枚举、glob、symlink follow、`..`、绝对路径、URL 或跨 connector lookup 在 open/delete 前失败；
- durable journal/inbox、access attempt、removal outbox/delete ledger/confirmation 和 capacity control reserve 均可提交；未完成 removal 不受 TTL/LRU 淘汰；
- 固定测试上限为 `maxObjectBytes=8192`、`maxBytesPerRequest=4096`、`openTimeout=100ms`、`idleTimeout=200ms`、`streamDuration=2s`、固定 buffer high-water `64KiB`、source connections=`4`、active/member=`1`、active/tenant=`2`、active/connector=`2`、active/process=`3`。真实部署可以采用更小正值，但缺失、非正、溢出或互相矛盾的值使 read/not-yet-recording admission 失败关闭；
- source admission 在解析 recording identity 前有界执行；人工授权后才应用 member/tenant/connector/process admission。任一 admission 都不授予权限，也不向另一个分区借用未声明容量。

`64KiB` 是 fixture 对 buffering 的上界而不是一次性读取大小要求；实现可以更小。单进程计数不被报告成多副本全局配额，需要全局上限时由共享 admission service 或 gateway 另行证明。

## 4. `PM-01` 录音晚到与 stored-object 证明

从 `interaction-a=CONNECTED` 开始执行：

| Step | PBX/driver 动作 | Adapter/core 必须提交 |
| --- | --- | --- |
| 1 | A 的受治理 bridge/two-party recording 启动 | 私有 registry 建立一个 live object 和稳定随机 opaque ref；journal/core 最多投影 `PROCESSING`；native name/path 不进入 typed fact |
| 2 | interaction 的 required leg 终止 | lifecycle 单调变为 `TERMINATED`；recording 仍为 `PROCESSING`，不存在 `AVAILABLE`、stored length 或媒体 read |
| 3 | 晚到 `RecordingFinished`/`RECORD_STOP`，但 stored file 尚不可读或仍在 finalize | adapter 只进入私有 `FINALIZING`/reconciliation；事件、Call/channel/bridge 终态和路径存在都不能产生 available fact |
| 4 | `restartAdapter()`，随后同一 A scope 出现完成对象 | registry 从 durable mapping 恢复同一 opaque ref；不能因 generation/restart 生成第二个 recording 或改绑 interaction |
| 5 | 只读 probe 证明 live writer 已结束、exact governed object 存在且可打开、格式 magic 与声明 WAV/MP3 一致、长度稳定且 `<=8192`、对象在读取期 immutable | adapter journal 一个 `RECORDING_AVAILABLE` typed fact；核心 inbox 以 encrypted ref 绑定 `recording-a`，只写一次 `AVAILABLE`，interaction 仍 `TERMINATED` |
| 6 | finished/available 重投、不同 delivery 的等价 available 和更晚 processing 到达 | 分别 `DUPLICATE`/`NO_CHANGE`；format、length、ref、policy deadline 和 lifecycle 不改变 |

必须执行以下失败分支：

1. finished/stop 后 stored lookup 暂时 `404`、文件打开 `403`、PBX 断线或 format/length 尚不稳定：保持 `PROCESSING`/reconciliation，不产生 `UNAVAILABLE`、`AVAILABLE` 或 `REMOVED`；
2. 对象为 `8193` bytes、未知 format、错误 magic、仍增长、已有 `AVAILABLE` 后 bytes/length 改变：不打开给核心；撤销该 build/backend 的 read/range readiness 并记录安全 contract failure，不能切换到另一对象；
3. registry 指向 `../`、绝对路径、URL、非受治理 root、symlink 或任意普通文件：在 typed fact/open/delete 前拒绝；禁止“规范化后碰巧落在 root”或 follow-then-check；
4. 从 `stream-b`、B registry、同名 native object 或 body 中伪造 A interaction/connector：不能命中 A mapping，A/B 均无跨分区 mutation；
5. 已有 `AVAILABLE` 后无更晚权威顺序的 failed/absent 与普通 read `404` 只打开 recording reconciliation，不降级；已 `REMOVED` 后的晚到 finished/available 永不恢复 ref 或 access。

stored-object probe 证明的是同一私有 registry 对象当前满足 declared profile，不是把 name/path 发给核心。Asterisk `RecordingFinished` 携带 live control object，FreeSWITCH `RECORD_STOP` 携带原始 file path/cause；两者都只是 adapter 私有候选输入。

## 5. `PM-02` 完整流与 HTTP 边界

前置为 `recording-a=AVAILABLE`、caller 通过 B22-D/M 全部可见性矩阵、policy 未到期且 full-read operation ready。一次完整请求固定执行：

1. 不含 recording identity 的 source admission 成功；HTTP target 只含核心本地 identity，无 query、grant、format、path、external ref 或 provider 值；same-origin/Fetch Metadata 检查在 adapter open 前完成；
2. 短事务重新验证 active membership、call read、独立 recording read、参与/管理关系、关联 CRM 当前可见性、recording/connector/policy/deadline/readiness 和分层 capacity，并提交唯一 `access-a-full`=`AUTHORIZED`；
3. 事务提交后才即时解密 external ref、构造不可序列化 typed open command；数据库 spy 证明 adapter open/read/body write 期间没有 interaction、recording、membership 或 connector 事务/锁；
4. adapter 只按 A registry 打开 stored object，返回规范 kind、稳定长度和可背压 handle；core 返回 `200`、固定 WAV/MP3 header allowlist 与 exact `4096` bytes；
5. first byte、正常 EOF 和审计补充分开提交为 `STREAMED`/`COMPLETED`；handle close 可重复调用但底层资源只释放一次，所有 admission counter 归零。

HTTP oracle 必须逐项比较 B22-M 固定的 `Content-Type`、`Content-Disposition`、`Content-Length`、`Accept-Ranges`、`Cache-Control`、`Pragma`、`Expires`、`X-Content-Type-Options`、`Cross-Origin-Resource-Policy` 和 `Referrer-Policy`。`STORED_FULL_ONLY` 的 full response 使用 `Accept-Ranges: none`，range profile 使用 `Accept-Ranges: bytes`。不得出现 `Location`、provider validator/header、Server passthrough、诊断 header、媒体 cookie、CORS credential header 或动态压缩；body digest 必须等于 fixture object，不得拼接另一个 open。

同一 fixture 还要发送未知 query、`HEAD`、条件请求、cross-site `Origin`/Fetch Metadata 和 format/download/media-token 输入；它们都在 adapter open 前失败关闭，不返回 grant、3xx、provider challenge 或 CORS credential response。

逐项撤销 membership、call read、recording read、参与/管理、CRM visibility、policy、deadline、connector active、read capability、delete-confirm capability、key/AAD 和 capacity 时，都在 adapter open 前安全拒绝。隐藏/跨 tenant/不存在对象不可区分；已经可见的 `PROCESSING`、过期或 readiness 暂不可用只得到有限安全状态。任何拒绝分支的 adapter open/read count 都为 `0`。

## 6. `PM-03` 原生 single range

`STORED_IMMUTABLE_NATIVE_RANGE` 对同一 `recording-a` 运行以下矩阵；每一行都是新的 HTTP 请求、完整人工授权、独立 access identity 和独立 audit：

| `Range` | 规范选择 | HTTP 结果 | Adapter oracle |
| --- | --- | --- | --- |
| `bytes=512-1535` | offset `512`, length `1024` | `206`, `Content-Range: bytes 512-1535/4096`, `Content-Length: 1024` | 原生 open `(512,1024)`；只读这 `1024` bytes |
| `bytes=3584-` | offset `3584`, length `512` | `206`, `Content-Range: bytes 3584-4095/4096` | 不读取前 `3584` bytes |
| `bytes=-256` | offset `3840`, length `256` | `206`, `Content-Range: bytes 3840-4095/4096` | suffix 在 open 前规范化 |
| `bytes=0-8191` | 截断为当前 representation `0-4095`，且未超过单请求上限 | `206`, exact total/length | 只打开实际选择区间 |
| `bytes=4096-` | 不可满足 | `416`, `Content-Range: bytes */4096` | open/read=`0` |
| multi-range、非 ASCII 十进制、负 start、逆序、溢出或超过部署请求上限 | 无 typed range | `416` | open/read=`0`，不回退 full |

fixture 还必须证明：

- `STORED_FULL_ONLY` 收到任何 `Range` 都在 adapter open 前返回 `416`、`Accept-Ranges: none`，且在已知总长度时返回 `Content-Range: bytes */4096`；它不读取整个对象后丢弃前缀；
- 每个成功 `206` 都返回所选区间的精确 `Content-Length` 与 `Content-Range`，并使用 `Accept-Ranges: bytes`；
- 成功 `206` 的 response header 只从核心 descriptor 产生；upstream `Content-Range`、ETag、filename 或任意 header 不透传；
- adapter spy 的 first offset、总 upstream bytes 和 body bytes 与规范区间完全一致。full open、range open 或 client retry 都不能复用前一 handle；
- 同一 recording 的 kind、length 或 representation digest 改变时当前请求失败并使 range readiness 失效；不能把旧 total 与新 bytes 混成 `206`；
- 首字节后失败只关闭连接。客户端恢复必须发起新的 range、重新授权；核心不自行发起第二次 range 或跨对象拼接。

## 7. `PM-04` 取消、截止与失败

对慢速 handle 分别运行下表；cancel 与 close 可以由多个竞态路径调用，但底层资源 release count 必须为 `1`，transparent reopen count 必须为 `0`：

| 场景 | 必须结果 |
| --- | --- |
| 客户端收到首个 chunk 后断开 | 立即传播 cancellation，停止 PBX/file read，access=`CLIENT_ABORTED`，active handle/counter 最终归零；已提交 body 后不写 JSON/provider error |
| `openTimeout` 前未取得 handle | 取消 pending open，未发送首字节，access=`OPEN_FAILED`/安全 limit 分类；迟到 handle 立即 close，不能交给下一请求 |
| 上游超过 `idleTimeout` | 取消并关闭，access=`LIMIT_ABORTED`；不发 keepalive bytes 掩盖 idle |
| 达到 `streamDuration`、recording absolute deadline 或 `maxBytesPerRequest` | 在边界停止读取/写出并取消，access=`LIMIT_ABORTED`；后续 full/range 重新授权并按当前截止拒绝 |
| 首字节前 provider/file/format/length 失败 | 普通安全错误、`OPEN_FAILED`，不透传原始消息/header；允许的 adapter 内 open retry 仍属于同一 operation 且首字节前完成 |
| 首字节后 provider 断线、长度不符或 client write error | 关闭当前连接/handle，记录安全 failure；不透明 retry、redirect、另开对象或补写错误 body |

removal/open 竞态使用同一 recording 数据库锁形成全序：removal 先提交时不建立 access attempt，也不调用 adapter；authorization 先提交时只允许当前调用栈在短启动期限内消费，removal 阻止所有后续请求并尽力取消已登记 handle。cancel、deadline 和 removal 同时发生仍只释放一次。

在 authorization commit 后/open 前、open 后/首字节前、首字节后/audit completion 前分别终止核心进程。恢复只能把已提交 attempt 标成 incomplete/安全终态，不能重建 open command、重新解密 ref 或再次读取媒体；OS/adapter harness 必须证明旧 socket/file descriptor 已关闭。

## 8. `PM-05` 大小、时间与并发资源上限

### 8.1 对象、请求与缓冲

- `8193` bytes stored object 在 available proof 阶段因超过 `maxObjectBytes=8192` 失败，核心不进入 `AVAILABLE`；不能通过 full/range 分片把超限对象带入首期；
- 对 length 未知但 profile 允许 full stream 的生成式 source，读取最多到本次 hard byte limit；下一 byte 到来前主动取消。若 descriptor 声明 `4096` 却产生第 `4097` byte，按 adapter contract/length mismatch 失败且不能把截断 body 记为 `COMPLETED`；
- range parser 在 open 前应用 `maxBytesPerRequest`；full handle 在首字节前对已知超限长度失败。所有路径的 buffer high-water `<=64KiB`，生成式大流不随 source 长度增长；
- open/idle/stream deadline 使用绝对 monotonic deadline 传入 adapter，不因重试、backpressure、core/adapter restart 或 readiness refresh 重新起算。

### 8.2 分层 admission 与隔离

固定按以下顺序执行容量矩阵：

1. 占满四个 source connections 后发送未知、隐藏和合法 recording target；三者在 identity/authorization 前得到相同临时拒绝，adapter/core 均不保存敏感 open command；
2. A 的同一 member 已有一个活动流时，第二个已授权请求因 member limit 拒绝；不排队、不取消第一条，也不把 attempt 交给另一 member；
3. A tenant/connector 分别达到两个活动流后，A 的新请求拒绝；在 process 仍有一个 slot 时，B 的合法流必须成功，证明 A 不能占用/淘汰 B 的分区；
4. process 达到三个活动流后，A/B 新媒体请求都临时拒绝，但已经活动的 handle 不被抢占；释放任一 handle 后只由一个新请求原子取得 slot；
5. 在 media slots 全满、read readiness 失效和 connector 停用三种状态下，`removal-a-1` 仍能使用独立 control reserve 进入 delete ledger；媒体容量不能取消到期义务；
6. 对 admission acquire 后在 authorization rollback、open failure、client abort、limit abort 和进程 crash 的每个 cut 检查 counter 最终回收且不出现负数/双释放。

内置单进程计数只证明本实例 hard limit；conformance 报告必须明确是否另有共享 admission。没有共享服务时不能写“tenant 全局并发上限已证明”。

## 9. `PM-06` durable 删除与权威确认

### 9.1 删除主 trace

从 `recording-a=AVAILABLE`、一个活动慢流和绝对删除截止将到开始：

| Step | 动作 | 必须提交和断言 |
| --- | --- | --- |
| 1 | 数据库时间到达截止，retention worker 锁定 recording | 同一事务阻止新 access、把 removal 置为 pending 并建立唯一 `removal-a-1` outbox；活动 handle 收到 best-effort cancel，普通四态不能因此伪造 `REMOVED` |
| 2 | core worker 重投同一 command/digest | adapter 先提交 connector A 的 durable delete ledger/receipt，再执行 delete；重投不创建新 command、不从 payload 接受 name/path/connector selector |
| 3 | PBX delete 返回 2xx/void，或对象在调用后不可读 | ledger 记录 effect/result candidate，但核心仍不可访问且不进入 `REMOVED`；read `404`、delete response 和 timeout 不是 confirmation |
| 4 | adapter 按该 build 声明的 confirmation procedure，在同一 A binding/storage namespace 对 exact registry object 执行权威 absence probe | 必须同时排除 live writer、stored descriptor/文件和同名替代 format；确认依赖原 delete ledger/registry identity 并 durable 提交 confirmation delivery |
| 5 | 核心经认证 connector inbox 应用 confirmation | removal/outbox settle 与 recording=`REMOVED` 在一个短事务中完成；晚到 available/processing/finished 只能 conflict/no-change，不能恢复访问或延长期限 |
| 6 | B22-D 引用宽限期到达 | 有界 sweep 清除 encrypted ref、HMAC、key metadata 和可恢复 registry correlation，只保留必要 deletion tombstone；删除证明期限不复制原 path/name/ref |

对受治理本地 backend，权威确认可以是 exact registry descriptor 上不跟随 symlink 的原子删除与同 scope absence 检查；对 ARI profile，可以是 capability 声明的 delete 加 post-delete stored/live exact-object 检查。具体程序必须绑定 build/backend 并由真实 conformance 执行，普通 `GET file 404` 不能追溯升级成确认。

### 9.2 重试、漂移和攻击分支

- delete 超时、PBX 断线、权限撤销、暂时 `404/403`、storage 不一致或 confirmation store 不可写时保持 removal pending、媒体不可访问并有界退避；不得恢复 read，也不得把告警关闭解释成已删除；
- delete effect 后/ledger outcome 前、confirmation probe 后/delivery commit 前和 core inbox commit 前崩溃时，恢复复用同一 removal identity。delete 可幂等重试，但只能作用于 registry 中 exact A object；confirmation delivery/digest 稳定；
- `RECORDING_MEDIA_READ`、range 或 start readiness 漂移立即阻止新媒体读取，却不能阻止已有 deletion。`RECORDING_MEDIA_DELETE_CONFIRMATION` 漂移使 removal 保持 pending/告警；connector disable/retire 必须保留最小 decommission delete-confirm lane，或在退役完成前证明所有对象已确认不存在；
- 用 B credential、B storage、同名 B native recording、伪造 A path/ref 或已清除 tombstone 尝试完成 A removal，必须在 delete effect/typed confirmation 前失败。A 的 delete 也不能枚举或删除 B object；
- 先出现普通 media `404`、随后对象重新可读时，状态保持 `AVAILABLE + reconciliation` 而不是 `REMOVED`；先有权威 confirmation、随后晚到同名新对象时，视为 registry/ownership 严重冲突，不能把旧 recording 复活。

## 10. 统一 crash/竞态矩阵

`PM-01` 至 `PM-06` 至少在以下相邻 durable 边界前后注入进程终止，并在每个 cut 重复 connector B 隔离、hard-limit 回收和禁止值扫描：

| Boundary | 恢复断言 |
| --- | --- |
| live registry / opaque ref binding commit | 重投只建立一个 connector-scoped mapping；未提交 ref 不进入事实或核心 |
| finished candidate / stored probe evidence commit | finished 不能替 stored proof；恢复后重新只读 probe，不从内存宣布 available |
| available journal / core inbox commit | 使用相同 delivery/digest，核心最多绑定一个 recording/ref/format |
| access authorization/admission commit | 全有或全无；提交的 attempt 只供当前栈短期消费，崩溃后不 reopen |
| adapter open / first byte / body completion | 首字节前可安全失败；首字节后只关闭，任何恢复都不透明续传 |
| counter acquire / handle register / release | rollback、cancel、limit 和 crash 后无泄漏、负数或跨分区 slot |
| core removal outbox / adapter delete-ledger receipt | 重投相同 command/digest；无 name/path body override 或第二逻辑 removal |
| delete effect / confirmation evidence / confirmation delivery | delete 结果不冒充 confirmation；恢复可重查 exact absence，不重建 media read |
| core `REMOVED` / ref purge | confirmation 至多应用一次；purge 不能早于宽限期，也不能删除 deletion tombstone 所需最小证据 |

同一 cut 还要参数化 client disconnect、recording deadline、removal cancel 与 readiness expiry 的排列。允许多个原因竞争调用幂等 cancel/close，也允许 durable delete ledger 对 exact object 执行声明为幂等的重试；不允许多次底层 handle release、透明 media reopen、第二个逻辑删除、对另一对象执行 delete、过期后新 access 或 deletion control reserve 被媒体占满。

## 11. 组合覆盖矩阵

| 场景 | B22-R | B22-D | B22-S/CP | B22-M | B22-A |
| --- | --- | --- | --- | --- | --- |
| `PM-01` late/stored | journal/inbox、restart、冲突 | encrypted ref、单对象、late-after-removal | lifecycle/recording 正交、connector ownership | live/stored 四态、格式/长度 | status/read/delete 同 build/scope |
| `PM-02` full | access outcome、completion failure | 人工权限矩阵、policy/deadline、AAD | interaction/CRM visibility | 200/header allowlist、事务外 backpressure | read operation gate |
| `PM-03` range | 独立 operation identity | 每次重新授权/审计 | 本地 recording identity | 206/416、原生单 range、immutable bytes | 独立 range capability/profile |
| `PM-04` cancel | crash/incomplete attempt | 截止/removal 全序 | 不修改 lifecycle | client/deadline/idle/limit cancel | drift 后不 reopen |
| `PM-05` limits | control reserve、durable counter | 超限不延长保留 | A/B 分区不交叉 | constant buffer/bytes/time/concurrency | capacity/readiness fail closed |
| `PM-06` delete | outbox/ledger/delivery 重投 | 到期停访、确认后 purge | late fact 不复活 | ordinary 404 不等于 removed | delete/decommission 独立 operation gate |

任一行只能由同一场景的联合 checkpoint 通过，不能把六份孤立的 mock unit test 拼成组合通过。

## 12. 执行层次与 conformance 输出

同一 `PM-01` 至 `PM-06` ID 必须运行在三层：

1. 纯规范模型，用属性化/穷举调度检查 recording/removal 状态、range 规范化、admission counter 和 crash 全序；
2. 真实 MySQL 核心加真实 HTTP server、durable PBX/media simulator 和虚拟时钟，使用真实事务、锁、outbox/inbox/ledger、断开连接及进程重建；
3. 隔离测试环境中的每个真实 PBX adapter build/backend/profile，使用相同 driver 语义、合成录音、独立 PBX/storage oracle 和安全 egress/path spy。

模型通过不能替代 durable 测试，模拟器通过不能替代真实 adapter conformance。真实 backend 若不能证明 native range，只运行并声明 `STORED_FULL_ONLY`；它仍必须执行 range 负向分支，不能跳过。真实环境不得使用客户录音来满足 fixture。

机器可读报告使用 fixture version `B22-PM/1`，至少包含：

- adapter contract/build evidence digest、PBX/storage profile、binding revision、isolation level、规范 format 和 full/range capability；
- hard-limit 配置与证明层级、是否存在共享 global admission、buffer high-water 和 control reserve 结果；
- `PM-01` 至 `PM-06` 每个 step/cut/profile 的 pass/fail 与固定安全分类；
- 每 connector 的 registry/journal/inbox/recording/access/removal/delete/confirmation 最终计数和 stable identity digest；
- available 写次数=`1`、full/range adapter open 次数与请求匹配、invalid/unsupported range open=`0`、transparent reopen=`0`、底层 handle 双释放=`0`；
- 每个成功 range 的规范 offset/length 与 upstream-read/body byte count；所有 200/206/416 header allowlist diff；
- 超限对象 available=`0`、所有活动 handle/counter 最终归零、A saturation 时 B 在保留 process slot 下成功、media saturation 时 deletion receipt 成功；
- `REMOVED` 只在 authoritative confirmation delivery 后写一次，delete response/read 404 直接导致 removed 的次数=`0`，late fact 复活=`0`；
- 除已授权媒体 response body 和私有 source/registry 外，path/name/symlink target/spool root、URL、credential、external ref、媒体 bytes、raw event/provider header/body/异常和敏感日志扫描命中数=`0`。

报告不能包含 tenant/recording 的真实标识、PBX node/app/account、recording name、路径、URL、credential、external ref、range source token、媒体 digest 的可外部关联原值或异常 message。build、backend、storage root policy、binding revision、format/range behavior、hard limit、delete procedure 或 isolation evidence 变化后必须重跑；旧报告不能自动迁移为新 readiness。

## 13. 闭合与剩余门槛

B22-PM 闭合以下 PBX 媒体组合设计问题：

- 呼叫/bridge/channel 终态和 recording finished/stop 只形成候选；晚到录音必须经过同 scope stored-object 完成证明才可用；
- 完整与单 range 共享逐请求授权和 typed stream，range 只能由 immutable、稳定总长度和原生读取能力支持；
- client、deadline、idle、byte、removal 与 crash 都不会触发透明 reopen，handle/counter 最终释放；
- 大小、时间、来源/member/tenant/connector/process hard limit 与 deletion control reserve 同时受测，不能靠跨 tenant 淘汰或无界排队维持可用；
- durable removal、adapter delete 和权威不存在确认是三个独立阶段；普通读取失败或 delete response 不会伪造 `REMOVED`；
- recording name/path/URL/credential 始终留在 connector-partitioned 私有 registry，重启、同名对象和攻击输入不能跨 connector 命中。

仍未由本 fixture 闭合的项目包括：

- B22-PD 已独立闭合的 PBX 号码、密钥、完整 command/journal/quarantine 敏感值、backup restore sweep 和确认销毁组合；本文件只复核与 recording ref/access/removal 相交的断言，不重复声明该 oracle；
- 真实 Asterisk/FreeSWITCH/SIP trunk 版本、线路、PBX/storage 权限、Docker/电话环境和 production adapter conformance/canary；
- 最终 URI、Controller/Facade/port/PO、数据库表、Flyway、错误码、配置键、共享 admission 服务和 fixture harness 实现；
- 司法辖区录音告知/同意、legal hold、暂停录音、转写、导出、分享、watermark/DRM、多个 recording/track 或 live media。

因此 B22-PM 只把 B22 总门槛中的“PBX stored-recording/media 组合 oracle”标为设计闭合。B22-PD 已独立闭合敏感数据/确认销毁设计，但两份 oracle 都不表示 runtime 已实现、真实对象已删除、真实 adapter 已 ready 或整个 B22 可以拆分；真实 canary 与最终汇总仍是后置门槛。

## 14. 官方依据

- [Asterisk ARI Recording API](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/Introduction-to-ARI-and-Media-Manipulation/ARI-and-Media-Part-1-Recording/) 明确区分正在写入的 live recording 与已经完成并保存到 Asterisk 文件系统的 stored recording；B22-PM 因此不从 channel/bridge/finished 事件直接推出 stored bytes 可读。
- [Asterisk ARI Recordings REST API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Recordings_REST_API/) 分别提供 live/stored 查询、stored binary read 和 stored delete；该接口没有被 B22-PM 自动解释为 immutable native range 或 delete confirmation，能力仍需 build/backend fixture 证明。
- [Asterisk ARI Event/Data Models](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Asterisk_REST_Data_Models/) 的 `RecordingFinished` 仍携带 live recording control object，而 `StoredRecording` 是另一模型；组合 oracle 因此另做 exact stored-object probe。
- [FreeSWITCH Audio Files and Streaming Sources](https://developer.signalwire.com/freeswitch/media-and-codecs/audio-files/) 说明 `record_session`/`uuid_record` 写入由 path 和 format 控制的文件，并有明确停止操作；这些 path 与控制命令必须留在私有 adapter。
- [FreeSWITCH Events Catalog](https://developer.signalwire.com/freeswitch/programming/events-catalog/) 将 `RECORD_START`/`RECORD_STOP` 作为带文件路径的 channel 事件；B22-PM 只把它们作为私有完成候选，不把路径或 event 本身当作核心 stored-object authority。
- [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html) 定义 byte ranges、`Accept-Ranges`、single-part `206` 和 `416`/`Content-Range`；B22-PM 在该通用语义之上有意只允许一个 range，并为每次请求重新授权。
