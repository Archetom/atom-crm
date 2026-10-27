# B22-M 录音媒体传输契约设计

> 决策日期：2026-07-19
>
> 状态：实现前设计闭合；未新增 API、Java 类型、配置、迁移、适配器或测试代码
>
> 范围：只固定首期录音对象、可用性映射、人工媒体授权、HTTP 代理流、adapter 流端口、资源上限和契约 fixture。号码、录音引用加密、保留与确认删除继续由 B22-D 负责；可靠 journal/inbox、gap 与对账继续由 B22-R 负责；呼叫 lifecycle 与两腿拓扑继续由 B22-S 负责。

## 1. 决策摘要

- 首期每个 `CallInteraction` 最多公开一个完整的两方录音对象。provider/PBX 的分段、双轨、临时文件、录音名称和转码中间物都留在私有 adapter；不能稳定提供一个对象时不声明录音媒体能力。
- 录音状态继续只公开 `UNAVAILABLE`、`PROCESSING`、`AVAILABLE`、`REMOVED`。它描述录音对象观察，不改变 `PREPARING`、`RINGING`、`CONNECTED` 或 `TERMINATED`，也不从呼叫终态推断媒体已经就绪。
- 首期人工读取固定为**每个请求重新授权的同源服务端代理流**。客户端只携带核心本地 recording identity 和既有人工认证；不签发浏览器媒体 grant，不在 path/query/fragment/cookie 返回 token，不重定向到 provider，也不返回 `externalRecordingRef`。
- HTTP 只提供经过认证的 `GET`。完整响应使用 `200`；adapter 明确支持且对象长度稳定时可接受一个 `bytes` range 并返回 `206`。多 range、未知 query、`HEAD`、条件请求和格式选择不属于首期。
- 首期媒体格式只允许规范 `WAV` 或 `MP3`，分别输出 `audio/wav` 或 `audio/mpeg`。格式由 adapter 在对象可用时确定并保持不变，不转发 provider `Content-Type`、文件名、header、validator 或诊断文本。
- 核心 application 只依赖 typed、可取消、可关闭的媒体 byte-stream capability。端口不得返回 URL、URI、路径、文件、整块 `byte[]`、provider HTTP response 或 SDK 类型；私有 adapter 负责认证 provider、限制 egress、处理受治理的内部 redirect 和释放连接。
- 授权与访问审计在短数据库事务内完成；adapter/provider I/O 和向客户端写 body 全部在事务外。每次完整或 range 请求都有独立 access attempt，客户端断开会向 adapter 传播取消，不会在已写出 body 后透明重试。
- 录音到访问/删除截止或进入 removal 后不接受新 access attempt。已开始的流受绝对录音截止、部署最大流时长、最大字节数、启动/空闲超时和主动取消共同约束；`Cache-Control: no-store` 不能阻止已授权客户端另行保存或录制音频，B22-M 不把它描述成 DRM。

B22-D 曾保留“同请求代理流”与“最长 60 秒单次 grant 后再流式读取”两个选择。B22-M 选择前者。内部一次性授权结果只是同一服务端请求栈中的不可序列化值，不是可交给浏览器、跨请求复用或持久化为 bearer credential 的媒体 grant。

## 2. 首期录音对象与状态

### 2.1 一个 interaction、一个公开对象

首期 recording 是 `CallInteraction` 的正交受治理子对象，不是 provider Recording、Asterisk recording name、文件或 URL。核心生成本地 recording identity，并将首次接受的加密 `externalRecordingRef` 永久绑定到原 tenant、connector 和 interaction。

首期约束如下：

- 一个 interaction 最多绑定一个公开 recording；不同 external ref 不能用“最后写入获胜”替换；
- 一个 recording 表示该 interaction 首期 `AGENT` 与 `REMOTE_PARTY` 两方通信的一个可播放对象，不公开 native leg、channel、track 或 speaker 文件；
- provider 产生多个 segment、双轨文件或重试产物时，私有 adapter 必须在自己的 registry 中确定一个稳定对象，或在私有边界合成后再提供一个 opaque ref；核心不拼接、不混音、不选择 track；
- 只有实时/live recording 时保持 `PROCESSING`，不能把仍在写入的文件作为 `AVAILABLE` 流出；
- recording 没有 CRM record authority。它的可见性永远受 interaction 参与/管理范围以及关联 CUSTOMER、CONTACT 或 LEAD 当前可见性的交集约束。

首期不支持一个 interaction 的录音列表、分段下载、双轨选择、波形、字幕、转写、摘要、分享、导出、离线包或公开下载链接。

### 2.2 Provider-neutral 可用性

adapter 向规范 ingress 提交固定 recording facts；事实仍走 B22-R durable journal/inbox，并遵守 B22-D 的 encrypted reference 规则：

| 规范事实 | public projection | 必要证据 |
| --- | --- | --- |
| recording expected/started | `PROCESSING` | 已按本 interaction 的受治理策略发起录音，或 provider 报告同一对象正在处理 |
| recording available | `AVAILABLE` | 同一 external ref 已完成，adapter 能以声明的规范格式打开一个稳定 stored object |
| recording absent/failed | `UNAVAILABLE` | provider/PBX 对同一录音尝试给出权威 absent、canceled 或 failed 结果；只保存安全分类 |
| removal confirmed | `REMOVED` | B22-D removal command 已由 adapter 权威确认对象不存在；发送删除请求或普通读取 `404` 不足以确认 |

`UNAVAILABLE` 同时覆盖未启用录音、没有对象和已确认失败；普通响应不公开 provider 错误、法律政策、内部 failure class 或“是否本应录音”。内部安全分类可以区分 `NOT_REQUESTED`、`ABSENT`、`FAILED`、`UNSUPPORTED`，但不能携带原始错误码或消息。

应用规则固定为：

- `PROCESSING` 可以在 interaction 终态前或后出现；`AVAILABLE` 也可以晚于 `TERMINATED`；
- `AVAILABLE` 只由 recording fact 建立，不能由通话时长、`CONNECTED`、Call `completed`、channel 销毁、文件名存在或 URL 非空推断；
- 已有 `AVAILABLE` 后，晚到 processing/failed 不能把对象降级；相反终局证据进入 reconciliation；
- 已确认 absent/failed 后，只有带更晚权威 revision/sequence 或重新查询证明的 available 事实才可提升为 `AVAILABLE`；没有顺序证据时保持不可访问并进入 reconciliation；
- `REMOVED` 是终态。任何晚到 available、processing 或 provider callback 都不能恢复引用、访问或保留期限；
- 到期与 removal pending 是内部访问/删除状态，不扩张普通四态。对象可能在确认删除前仍显示 `AVAILABLE`，但从截止起媒体读取已经拒绝；public availability 不是 authorization。

### 2.3 媒体表示

available fact 还需归一化一个内部、不可由 provider 任意字符串填充的 representation descriptor：

| kind | HTTP `Content-Type` | 固定扩展名 | 规则 |
| --- | --- | --- | --- |
| `WAV` | `audio/wav` | `.wav` | adapter 把 `audio/x-wav` 等 provider 别名归一为该值并验证对象确为 WAV |
| `MP3` | `audio/mpeg` | `.mp3` | adapter 验证对象为 MPEG audio，不接受仅由文件名或上游 header 声明的类型 |

同一 recording 的 kind 在首次 `AVAILABLE` 后不可改变。首期不做客户端内容协商，不接受 format、codec、channel 或 quality query。adapter 可以在私有边界选择 provider 已存格式或受治理转码，但核心不拥有转码任务、临时文件或 codec 配置。其他格式只有在后续设计增加有限 typed kind 和两个适配器 fixture 后才能进入公共契约。

对象总字节数可以作为仅内部使用的有界非负 descriptor 保存或在打开时返回；它不进入普通详情。range capability 要求 adapter 能给出稳定总长度，并保证同一 recording 的字节表示在可用期内不变。duration、声道数、sample rate、provider ETag 和原始文件名不属于首期传输契约。

## 3. 人工授权与事务边界

### 3.1 每个请求重新授权

完整读取和每次 seek/resume range 都是新的人工用例调用，并显式接收 `AuthenticatedCaller`。短事务必须重新验证：

1. caller 当前仍是该 tenant 的活动成员，并具有呼叫读取和独立 recording-media 读取能力；
2. caller 是 interaction 的当前受治理参与者，或拥有明确的 tenant call-management 范围；
3. interaction 关联 CRM record 时，caller 当前仍能通过 owning domain 的 read capability 与 data scope 读取该 CUSTOMER、CONTACT 或 LEAD；
4. recording 属于同 tenant、connector 和 interaction，状态为 `AVAILABLE`，引用尚未清除，tenant policy 允许访问，数据库时间早于访问/删除截止，且未进入 removal；
5. connector registration 当前 active，持久化 capability snapshot 仍声明媒体读取、格式和删除确认能力；请求 range 时还必须声明 byte-range capability；
6. 本次请求通过成员、tenant、connector 和进程级有界 admission；容量不足是临时拒绝，不能绕过前述 authorization 或暴露对象存在性。

不存在、跨 tenant、隐藏 interaction、无参与/管理关系和隐藏关联 CRM record 在公共边界使用不可区分的安全拒绝。只有已经通过 interaction 可见性的人才可以看到 `PROCESSING`、过期或 adapter 暂不可用等安全状态；响应和时序不能成为跨 tenant 或隐藏记录 oracle。

### 3.2 Access attempt 与删除竞态

authorization 事务锁定/验证当前 recording access state，并为成功授权写一个最小 access attempt：tenant、本地 interaction/recording、member、请求种类（full/range）、策略版本、服务端时间、绝对最晚结束时间和安全 outcome。它不保存 range 之外的 request header、IP、user agent、external ref、URL、credential、媒体 bytes 或异常消息。

预期的授权拒绝还要通过独立最小安全审计追加 `DENIED` 和固定原因分类；它不能为不存在或隐藏对象保存 provider/recording 细节，也不能因审计失败把拒绝变成允许。基础设施异常记录为独立 internal failure，不伪装成 caller 越权。授权事务回滚时不能留下可消费的 access attempt，拒绝审计和内部失败审计也不能被客户端用来判断对象是否存在。

retention/removal worker 与 authorization 对同一 recording 使用相同数据库锁顺序。因此：

- removal 先提交时，新 access attempt 失败；
- authorization 先提交时，该次 attempt 已被允许，但只能在同一服务端调用栈内立即消费；removal 会阻止后续请求并尽力取消活动 handle；
- access attempt 不是 bearer grant。它不能由客户端提交，不能在另一节点恢复成读取权限，也不能在日志或响应中作为 token；
- adapter open 前再次检查 attempt 的短启动期限和 recording 的绝对截止。内部授权结果最长不超过 B22-D 的 60 秒上限，实际实现应只覆盖当前 open 调用；
- 录音截止是流的 hard deadline。成员、角色或 CRM scope 在 body 已开始后被撤销无法通过持续数据库轮询做到即时切断；部署最大流时长和下一次 range 重新授权共同限制该残余窗口，审计必须明确记录它。

请求进入 adapter 前 outcome 至少记录 `AUTHORIZED`；打开失败、首字节、正常结束、客户端断开、超过截止/上限和内部失败以 `OPEN_FAILED`、`STREAMED`、`COMPLETED`、`CLIENT_ABORTED`、`LIMIT_ABORTED` 或 `INTERNAL_FAILED` 一类安全结果补充。进程在补充前崩溃时保留可识别的 incomplete attempt，不能把 `AUTHORIZED` 猜成成功播放。

### 3.3 长 I/O 不占数据库事务

authorization transaction 提交后才即时解密原 connector/recording 的 external ref，并构造不可 JSON 序列化、`toString` 固定脱敏、短生命周期的内部 open command。adapter open、provider HTTP/文件 I/O、backpressure 和客户端 body 写入都在事务外；任何读取都不得持有 interaction、recording、membership 或 connector 数据库锁。

密钥不可用、AAD/tag 失败、unknown key version 或 connector scope 不匹配时不调用 adapter，并按 B22-D 产生不含敏感值的安全告警。解密值不得进入通用 executor queue、MDC、trace baggage、retry payload、cache 或 access audit。

## 4. HTTP 代理流契约

### 4.1 Request

最终路径名在实现批次确定，但 wire shape 固定为：

```text
GET <same-origin core API>/<local interaction or recording identity>/media
Authorization/Cookie: existing human authentication
Range: bytes=<single-range>        # optional
```

- target 只含核心生成的本地 identity；不含 external ref、provider URL、文件名、grant、签名、expiry 或 credential；
- 不接收 query parameter。未知 query、format/channel/download 选择和媒体 token 均失败关闭；
- 使用和其他人工 API 相同的认证映射到 `AuthenticatedCaller`，不存在媒体专用 bearer；
- 若部署的人类认证依赖必须显式附加的 `Authorization` header，前端应使用能附加该凭据的同源 fetch/media client；B22-M 不为兼容裸 `<audio src>` 而降级成 query token。具体播放器集成不属于本设计；
- media route 不返回 `Access-Control-Allow-Origin`，也不继承允许跨域 credential 的通用 CORS。使用 ambient cookie 的部署还必须采用受治理的 SameSite/CSRF 策略，并在可信代理重建后拒绝明确的 cross-site `Origin`/Fetch Metadata；这些检查发生在 adapter open 前；
- 首期只接受 `GET`。`HEAD` 不作为绕过媒体审计的 metadata API；`POST` grant、cookie grant、signed path 和跨域播放均不支持；
- 只支持 `bytes` 的一个 range。multi-range 不生成 `multipart/byteranges`，不把 range 直接拼接进 provider URL；
- 不支持 `If-Range`、`If-Match`、`If-None-Match`、`If-Modified-Since` 或客户端 provider validator。每个请求都重新授权并取得当前同一 immutable representation。

### 4.2 Success response

完整响应使用 `200 OK`；合法且可满足的单 range 使用 `206 Partial Content`。只从规范 descriptor 生成以下 header：

```text
Content-Type: audio/wav | audio/mpeg
Content-Disposition: inline; filename="call-recording.wav"
Content-Length: <selected bytes>              # 已知时；206 必填
Accept-Ranges: bytes | none
Content-Range: bytes <start>-<end>/<total>    # 仅 206
Cache-Control: private, no-store, max-age=0
Pragma: no-cache
Expires: 0
X-Content-Type-Options: nosniff
Cross-Origin-Resource-Policy: same-origin
Referrer-Policy: no-referrer
```

MP3 使用同样固定的 `filename="call-recording.mp3"`。filename 不含 tenant、member、号码、客户名、interaction、external ref、provider 或时间。响应不包含 `Location`、provider `ETag`/`Last-Modified`、`Set-Cookie`、`WWW-Authenticate` challenge、Server/header passthrough、checksum、duration 或自定义诊断 header；不使用通用 CDN、反向代理缓存或 service-worker cache。传输使用 `identity` content coding，不对已经编码的音频再做动态压缩。

`no-store` 是对合规缓存的指令，不是客户端数据销毁保证。已经获准读取 bytes 的终端可以保存、录屏或通过音频设备重新捕获；若产品需要阻止离线保存、watermark 或受治理终端，必须另行设计，不能由响应 header 虚假承诺。

### 4.3 Range 与重试

- 只有 capability 声明 range、对象总长度已知且表示 immutable 时返回 `Accept-Ranges: bytes`；否则返回 `Accept-Ranges: none`；
- range 数字只按 ASCII 十进制解析，先检查溢出、负值、逆序、suffix/open-ended 归一化和部署单请求 byte 上限，再构造 typed range；任何值都不能直接拼到 provider 请求或文件 seek；
- 对不支持、语法无效、超上限、multi-range 或不可满足的 Range，在 adapter open 前返回 `416 Range Not Satisfiable`；已知总长度时按 HTTP 语义给出 `Content-Range: bytes */<total>`；
- range 端点不允许核心通过读取并丢弃前 N bytes 模拟 seek；adapter 必须原生、安全地打开精确区间，否则 connector 不声明该 capability；
- 每个 range 请求重新执行第 3 节的全部授权并产生独立 audit。客户端不能凭第一次 `200/206` 的 response header 构造免授权 continuation；
- adapter 在首字节前可以按自己声明的只读幂等语义处理瞬时 open 失败，但核心不创建第二个 access identity。首字节之后任何 provider timeout、断线、长度不符或 client write error 都只关闭当前流并记录安全结果，不透明续传或拼接另一对象；
- body 已提交后不能再写 JSON error 或 provider message。连接关闭是该次传输失败的唯一 wire 结果，客户端如需恢复必须发起新的已授权 range。

## 5. Application 到私有 adapter 的流端口

### 5.1 Typed open command

规划端口语义至少包含：

- 核心生成的 media access operation identity；
- 已解析的 tenant 与唯一 connector identity；
- 原 recording 的一次性解密 opaque external ref；
- 固定 `WAV` 或 `MP3` representation kind；
- full 或一个规范化 byte range；
- 绝对 open deadline、stream deadline 和最大允许 bytes；
- 取消信号。

返回值只能是一个受控 handle：规范 representation kind、可选稳定总长度、实际区间、可背压读取的 byte source 和幂等 close/cancel。handle 不得携带 URL、URI、Path、File、provider response、任意 header map、credential、SDK exception 或完整内存 buffer；`toString` 只显示本地 operation identity 和安全分类。

application 选择 connector 对应的唯一 adapter 并把 scope 固定在 command 中。adapter 不能从 external ref、provider account、文件名或 URL 重新决定 tenant/connector，也不能访问核心 repository、mapper、PO 或数据库表。

### 5.2 Adapter 责任

私有 adapter 必须：

- 只用部署受治理的 endpoint、账号和 secret 打开媒体；普通请求值不能改变 scheme、host、port、path base、认证方式或 storage root；
- 若 provider SDK/API 返回临时 redirect，只能在私有 egress allowlist 内校验 scheme、host、端口、DNS/IP 和跳转次数，并按 provider 规则限制 credential forwarding；`Location` 永不跨 adapter 返回核心或浏览器；
- 把 provider content type、文件名和长度映射/验证为规范 descriptor，不转发响应 header；format、magic prefix、长度或 range 不一致时失败关闭；
- 使用流式 backpressure，不把完整 recording 读入 JVM heap、临时文件、Redis、MySQL、日志、trace 或 crash dump；确需私有受治理转码时，其临时数据遵守比 recording 更早的绝对删除期限；
- 在 client disconnect、deadline、max bytes、idle timeout 或 removal cancel 时立即停止上游读取并关闭 socket/file/SDK resource；close/cancel 重复调用安全；
- 不在 read path 自动复制到对象存储、生成 signed URL、触发转写或延长 provider retention；
- 将 provider 401/403/404/429/5xx、TLS、解密和文件错误映射为有限安全分类，原始 response/body/path/exception 只留在受治理私有诊断边界且不得进入核心。

云 provider 的媒体 HTTP、PBX 本地文件或 ARI `stored recording` 都必须通过同一端口语义，不能为 PBX 暴露一个 filesystem shortcut，也不能为云厂商暴露 redirect shortcut。

## 6. Capability、readiness 与资源上限

B22-M 固定以下媒体侧 typed capability 语义；最终 Java 命名可以调整，但不能退化成字符串 map：

| capability | 首期语义 |
| --- | --- |
| `RECORDING_STATUS_OBSERVATION` | 能把 expected/processing/available/absent/failed 映射为第 2 节事实，并证明 stored object 的可用时点 |
| `RECORDING_MEDIA_READ` | 能按 encrypted opaque ref 受控打开 `WAV` 或 `MP3` 流，且不向核心返回 URL/path/credential |
| `RECORDING_MEDIA_DELETE_CONFIRMATION` | 能执行 B22-D durable removal 并权威确认对象不存在；启用新录音的必需能力 |
| `RECORDING_MEDIA_BYTE_RANGE` | 可选；能对 immutable representation 返回稳定总长度并原生读取精确单 range |

媒体 readiness 不是“provider 凭据可登录”或“曾经成功下载一次”。生产启用新 recording 前必须同时验证：

- status callback/event/subscription 或 stored-object 完成证据已经配置；
- read 与 delete 使用同一受治理 connector/account/storage scope，且 decommission 后仍有最小 delete-confirm 路径；
- 至少一个规范 format 可用，最大对象长度、open timeout、idle timeout、stream duration、每次读取 bytes 和活动流数量都有部署 hard limit；
- private egress/storage root、TLS、redirect policy、credential 权限、API 版本和 range 行为已通过 probe；
- adapter 对 unavailable、deleted、transient error 和 permission drift 能给出安全且互不混淆的结果；
- dev/test 确定性 adapter、无界 URL fetcher、通用 filesystem reader 或返回 `byte[]` 的实现无法装入 `prod`。

readiness 漂移立即阻止新媒体读取和新录音启用，但不把既有 `AVAILABLE` 猜成 `REMOVED`，也不取消已到期对象的删除义务。调用能力和 recording availability 仍是两件事。[B22-A](../B22-A-adapter-readiness.md) 进一步固定 `RECORDING_READ`/`RECORDING_DELETE` 的独立操作门、最长有效期、connector 隔离、build/revision evidence 和漂移失败关闭语义。

入口先有一个不含 recording identity 的有界来源/连接 admission，再在授权后应用 per-member、per-tenant、per-connector 和 per-process active-stream 限制。单进程内置限制不声称多副本全局配额；需要全局并发或带宽仲裁时由共享 admission service 或 gateway 提供，但它不能替代应用层权限复核。达到上限不排队保存敏感 open command，也不淘汰其他 tenant 的活动流。

## 7. 失败语义

| 场景 | 规划结果 |
| --- | --- |
| 缺少成员、录音能力、参与/管理范围或关联 CRM 当前可见性 | 在 adapter 调用前安全拒绝；隐藏对象与不存在对象不可区分 |
| `PROCESSING`、`UNAVAILABLE`、已过期或 removal pending | 不打开媒体；只有已可见 interaction 的 caller 得到有限安全状态 |
| connector/readiness/capability 暂时不可用 | 本次 open 失败且可稍后新请求；不降级 redirect，不改 recording availability |
| external ref 解密/AAD/tag/key version 失败 | 安全失败并告警；不调用 adapter，不公开底层原因 |
| provider 返回 URL、Path、未知 format、活动 live recording 或超限长度 | adapter contract failure；不发送首字节，不把值保存到核心 |
| `AVAILABLE` 后 provider 普通读取返回 404 | 形成 media inconsistency/reconciliation；不自动声明 `REMOVED` |
| Range 不支持、multi-range 或不可满足 | `416`，不回退为整个对象下载，不读取并丢弃前缀 |
| 首字节前 provider 瞬时失败 | 返回安全普通错误并完成 `OPEN_FAILED` audit；不返回 provider body/header |
| 首字节后 provider/client 失败 | 取消并关闭两侧流，记录安全结果；不拼接、重定向或写 JSON error |
| stream 期间达到录音截止、字节/时长/空闲上限 | 主动取消并记录 `LIMIT_ABORTED`；后续 range 重新授权时继续拒绝 |
| removal 与 open 并发 | 由同一 recording lock 决定新 attempt 是否成立；removal 阻止后续请求并尽力取消已打开 handle |
| audit completion 写失败 | 不把失败隐藏为成功审计；保留初始 attempt 供恢复标为 incomplete，但不为补审计重复读取媒体 |

## 8. 云与 PBX 组合 fixture

云 callback 的跨可靠性、敏感值、两腿、媒体和 readiness 统一检查点后来由 [B22-CW](../B22-CW-cloud-callback-fixture.md) 闭合；本节的单项矩阵继续作为其媒体 oracle，并保留 PBX 组合 fixture 的输入。

### 8.1 云 Webhook / Recording API fixture

- `in-progress` 映射 `PROCESSING`；只有明确 `completed` 且按 capability 可读取时映射 `AVAILABLE`；`absent`/`failed` 映射安全 `UNAVAILABLE`；Call `completed` 本身不改变 recording；
- callback 中的 `RecordingUrl` 永不进入规范事实、核心 persistence、日志或响应。adapter 只以稳定 recording object identity 建立自己的 opaque ref；
- provider basic/OAuth credential、account identity、temporary URL 和 redirect 完全留在 adapter。核心代理响应只出现规范 WAV/MP3 headers；
- provider media 尚未就绪、返回 404、限流、权限漂移、encrypted-media format 限制和 delete confirmation 分别产生正确安全结果，不混成 `REMOVED`；
- callback-before-call-terminal、call-terminal-before-recording、重复/乱序 recording callback、adapter/core restart 和 late callback after removal 都保持四态与 lifecycle 正交；
- full stream、支持的 single range、客户端中断、首字节前/后故障和最大字节终止都证明 provider connection 被关闭且没有透明第二次下载。

### 8.2 PBX stored-recording fixture

- live recording 只能是 `PROCESSING`；只有 bridge/two-party recording 已停止并成为可读 stored object 后才是 `AVAILABLE`；channel/bridge 终态不能替代该证据；
- recording name、spool root、绝对/相对路径、symlink、ARI base URL 和认证不进入核心。adapter registry 用 opaque ref 精确映射受治理 stored object；路径穿越、任意文件读取和跨 connector 名称复用失败关闭；
- ARI `GET .../file` 或受治理本地 file stream 都实现相同 close/cancel/size/format 契约；没有原生 range 时声明 `Accept-Ranges: none`，核心不模拟 seek；
- ARI/PBX delete 只有权威确认 stored object 不存在后才映射 `REMOVED`；文件暂时不可读、PBX 断线或普通 404 先进入 inconsistency/对账；
- PBX stream owner/fencing、journal、session registry 与 media handle 相互独立；连接世代变化不改变 tenant、recording identity 或授权。

### 8.3 开源模拟 adapter 与核心 HTTP fixture

- 模拟 adapter 提供固定小 WAV/MP3 流、可选稳定 range、慢流、首字节前后失败、长度不符、取消和删除确认；测试数据不冒充真实通话；
- 权限矩阵逐项撤销 active membership、call read、recording read、参与/管理范围、CRM visibility、policy、deadline、connector 和 capability，任一项都在 adapter open 前拒绝；
- 两 tenant、两 connector 和相同 external ref/recording bytes 不会交叉授权、复用 handle 或共享容量分区；
- HTTP fixture 证明 URL/query/cookie/response 均无媒体 grant、external ref 或 provider 值，cross-site/CORS credential 请求在 adapter open 前拒绝，绝不返回 3xx；200/206/416、single range 和 header allowlist 精确；
- 大于 JVM heap fixture 通过生成式慢流证明常量级 buffering/backpressure；client abort、timeout、limit 和 removal 都调用一次可重复 close/cancel；
- 日志、trace、metric、audit、error capture、heap-friendly object `toString` 和异常映射不含媒体 bytes、URL、path、credential、external ref、provider header/body 或完整异常消息；
- access-attempt crash fixture 覆盖 authorization commit 后进程退出、open 前失败、首字节后退出和 completion audit 失败；恢复只标记 incomplete，不重新打开媒体；
- 到期/open 并发由真实 MySQL 行锁形成全序，截止后没有新 attempt；活动流最晚在自己的 hard deadline 终止，range resume 重新授权失败。

## 9. 本设计不重复或暂不定义的边界

- 最终 URI、Controller/Facade/port/PO 名称、authority code、稳定错误码、表名和 Flyway 版本；
- B22-D 已冻结的号码、external recording ref 加密/HMAC、policy version、删除截止、密钥轮换、retention sweep 和确认删除；
- B22-R 已冻结的 command/outbox、adapter journal、core inbox、delivery identity、gap、reconciliation、session registry、capacity tombstone 和 crash guarantee；
- B22-S 已冻结的 interaction、leg、connected proof、dispatch/observation/recording 正交性和 agent/device capability；
- 真实 provider/PBX adapter 实现及其凭据/订阅/版本 probe；readiness/隔离设计由 B22-A 冻结，B22-PM 已冻结 PBX 媒体组合 oracle，仍需执行真实 canary；
- media grant、signed URL、CDN、跨域播放、公开下载、对象存储转存、转写/字幕/摘要、分享/export、多个 recording/track、live media、WebRTC、DTMF、监听、质检、legal hold、watermark 或 DRM。

B22-M 只闭合媒体传输前置，不表示 B22 runtime 已实现，也不触发 B22a/B22b 拆批。真实 adapter readiness/隔离设计后来由 B22-A 闭合，云 callback 组合后来由 B22-CW 闭合；[B22-CP](../B22-CP-pbx-composed-contract.md) 与 [B22-PC](../B22-PC-pbx-command-contract.md) 分别闭合不含录音的 PBX 核心和 command 组合，[B22-PD](../B22-PD-pbx-sensitive-data-contract.md) 闭合 PBX 敏感数据/确认销毁组合，[B22-PM](../B22-PM-pbx-media-contract.md) 后来闭合 PBX 录音晚到、stored object、完整/单 range、取消、hard limit 和确认删除组合。真实 provider canary 仍是实现前阻断项。

## 10. 官方依据

- [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html) 定义 GET range、`206 Partial Content`、`416 Range Not Satisfiable`、`Accept-Ranges` 与 `Content-Range`；B22-M 只接受一个 `bytes` range，避免 multipart 和无界 range 放大。
- [RFC 9111 HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111.html) 定义 `no-store` 对合规缓存的存储限制，同时明确它不是充分的隐私机制；本设计因此同时禁止核心/CDN 缓存，并明确不能阻止已授权客户端另行保存。
- [Twilio Recordings Resource](https://www.twilio.com/docs/voice/api/recording) 区分 recording metadata 与完成后的 WAV/MP3 media，媒体访问需要 provider credential，媒体缺失或删除时返回 not found；这些 URL、credential、format alias 与错误都留在私有 adapter。
- [Twilio Voice Webhooks](https://www.twilio.com/docs/usage/webhooks/voice-webhooks) 与 [Twilio Record](https://www.twilio.com/docs/voice/twiml/record) 说明 recording 有独立 `in-progress`、`completed`、`absent`、`failed` callback，且完成回调才表示媒体可访问；B22-M 不从 Call terminal 推断录音就绪。
- [Asterisk ARI Recording API](https://docs.asterisk.org/Configuration/Interfaces/Asterisk-REST-Interface-ARI/Introduction-to-ARI-and-Media-Manipulation/ARI-and-Media-Part-1-Recording/) 区分 live 与 stored recording；stored recording 是 PBX 文件系统对象，并有独立读取与删除操作。
- [Asterisk ARI Recordings REST API](https://docs.asterisk.org/Latest_API/API_Documentation/Asterisk_REST_Interface/Recordings_REST_API/) 暴露按 recording name 的 stored-file GET 与 DELETE。这证明名称、路径和文件访问必须留在私有 adapter，核心只使用 opaque ref 和统一 byte-stream capability。
