# B22 云平台 Webhook 适配器只读 spike

> 调研日期：2026-07-19
>
> 代表模型：Twilio Programmable Voice Call、Status Callback、Recording Callback
>
> 状态：只读调研完成并经复审修正；未选择生产厂商，未实现 SDK、Controller、配置、迁移或测试代码

## 1. 结论

Twilio 模型可以映射到 B22 的 provider-neutral 呼叫交互内核，但不能把 Webhook DTO、`CallSid` 或厂商状态枚举直接提升为公共契约。当前 B22 的开源/私有边界方向成立，稳定契约仍需补以下缺口：

- 呼叫命令投递状态与呼叫生命周期必须分离；Create Call 超时不能被解释为“未拨号”，也不能盲目重试；
- 一个 CRM interaction 不能等同于一条外部 call leg；B22-S 首期把相关 Call 归一为一个 `AGENT` 和一个 `REMOTE_PARTY` 参与腿，`CallSid` 只是 leg 引用，`ParentCallSid` 也不能替代本地聚合 ID；
- Webhook 的传输投递标识、语义事实标识和厂商对象标识必须分离，核心不能要求所有厂商都提供原生 event ID；
- 呼入首事件还没有本地 `interactionRef`，需要单独的 connector-scoped 幂等发现入口，不能要求所有事件先绑定本地交互；
- 默认 callback 配置、有限重试和排队期取消都会形成观测缺口，云 Webhook 也需要显式 gap/对账语义；
- 完整外呼目标和呼入号码需要独立的敏感地址通道，不能既从公共事实排除又不给 adapter 提供受控数据路径。

因此本 spike 验证了“核心只接收已验签、已归一化事实”的边界，但没有消除正式实现前置条件。

## 2. 原始入口与验签边界

Twilio 对不同 content type 使用不同验签输入：表单请求需要外部可见的完整 URL 和全部 form 参数；JSON 请求需要保留原始 body，并把 URL 中的 `bodySHA256` 纳入校验。URL 的编码形式也必须保持不变。这个步骤必须在任何通用 DTO 映射之前完成。

私有 Webhook 适配器应按以下顺序处理：

1. 从受治理的路由或部署配置选择候选 connector 和对应 secret；不能从 `AccountSid`、tenant、actor 或请求体选择租户。
2. 依据 allowlist 后的代理配置重建厂商实际访问的 scheme、host、port、path 和原始 query；不信任任意 `Forwarded` 或 `X-Forwarded-*`。
3. 使用厂商官方校验器验证 `X-Twilio-Signature`。表单传入全部参数，JSON 传入原始 body；验签前不做 decode/re-encode、字段白名单化或 JSON 重排。
4. 验签成功后才解析允许的事件并映射为有界规范事实。原始 body、签名、secret 和任意厂商错误不得写日志或进入通用 lifecycle inbox；完整号码只允许进入下文定义的专用敏感地址通道。
5. 核心至少持久化规范 inbox 后才返回成功；状态推进可以在同一短事务完成，也可以由 inbox worker 后续执行。重复事实也返回成功；可重试基础设施失败返回适配器配置的失败响应，永久无效输入不反复消耗核心资源。

Twilio 官方说明其 Webhook 没有固定来源 IP 范围，因此来源 IP allowlist 不能替代签名校验。标准签名也不提供可由核心依赖的通用 freshness nonce，合法旧请求仍需由持久化 inbox 幂等吸收。Call 类 Webhook 还有 15 秒硬超时；入口不能在应答前下载录音、调用 CRM 查询或执行其他外部网络请求。

Connection Overrides 的默认 retry 主要覆盖连接/TLS 失败。若部署希望核心数据库暂时失败时的 5xx 得到厂商重投，必须在私有 callback URL 配置 5xx/read-timeout retry；核心不能把“厂商必然重试所有失败”写成公共保证。

## 3. 命令、对象和事件事实

### 3.1 外呼命令

Create Call 是异步边界。请求使用表单参数提交 `To`、`From` 和受控的 `Url`、`TwiML` 或 `ApplicationSid` 等执行配置；这些号码与执行入口属于专用命令/私有配置，不进入通用事实。成功响应返回 `CallSid`。适配器必须显式设置 `StatusCallback`，并按首期拓扑显式请求所需的 `initiated`、`ringing`、`answered`、`completed` `StatusCallbackEvent`，录音另设 `RecordingStatusCallback`。使用 `ApplicationSid` 时 per-call `StatusCallback`/`StatusCallbackEvent` 会被忽略，因此该执行模式必须证明有文档化的等价观测能力，否则 readiness 失败关闭。未配置事件列表时默认只发送 `completed`；呼叫若仍在 queued 状态就被取消，则不会产生状态 callback。因此核心不能假定完整的进度序列自然到达。

核心先提交 interaction 与 outbox，再由适配器发起外部请求；用户侧成功只表示本地意图已接受，不能表示平台已拨号、对端已振铃或双方已接通。callback 订阅配置错误、厂商有限重试和无 callback 的排队期取消都必须进入 observation gap/对账路径，不能自动翻译为业务失败。

本 spike 在 Twilio Call Resource 的 Create Call 契约中未发现可供调用方提交的、官方承诺的请求幂等键。该结论只表示当前官方 Call 创建契约没有给出此保证，不外推到 Twilio 的其他产品 API。若 HTTP 超时或进程在平台接受请求后、保存 `CallSid` 前崩溃，适配器不能用相同 `commandId` 盲目再次 Create Call。

provider-neutral dispatch 结果至少需要区分：

| 结果 | 含义 | 后续动作 |
| --- | --- | --- |
| `CONFIRMED` | 平台明确接受，已取得外部引用 | 保存绑定，等待规范事件 |
| `REJECTED` | 平台明确未接受，安全分类可持久化 | 终结该命令，不自动泄露原始错误 |
| `AMBIGUOUS` | 超时、断线或崩溃窗口导致是否拨号未知 | 禁止盲重试，等待 callback、查询或人工对账 |

transactional outbox 可以保证本地命令不会丢失，但不能凭自身跨越外部 HTTP 的原子性缺口。正式契约必须说明每个适配器提供 `IDEMPOTENT_START`、`AT_MOST_ONCE_START` 或其他何种保证。

### 3.2 Call 与 call leg

Twilio 把 `CallSid` 定义为一条 Call resource；两腿场景还提供 `ParentCallSid`。核心保存 adapter-scoped 的不透明外部引用；B22-S 首期只绑定一个 `AGENT` 和一个 `REMOTE_PARTY` 规范参与腿，其他 provider 执行节点留在 adapter。普通 API 不返回这些值，核心也不以它们作为 tenant、actor 或业务记录授权事实。

adapter 可以把本地 `interactionRef` 或 `commandId` 放入受签名保护的 callback 路径或 query，用于在 Create Call 响应和 callback 竞态时关联本地意图。该值只是相关性信息，不是认证凭据；tenant 始终由已认证 connector 的核心注册关系派生。

作为另一种云模型的交叉校验，Telnyx 官方契约分别暴露 call session、call leg 和实时 control handle，并为命令去重给出有限时间窗口。这进一步说明公共能力必须表达“是否支持命令去重以及有效窗口”，不能把某个厂商的 ID 结构或无限期 exactly-once 当作默认。

### 3.3 Status Callback

Twilio 为进度 callback 提供 `Timestamp` 和从 0 开始的 `SequenceNumber`，但官方明确说明各事件是独立 HTTP 请求，不能保证按发送顺序到达。推荐映射如下：

| Twilio 事实 | 核心处理 | 限制 |
| --- | --- | --- |
| Create Call 成功响应 | dispatch `CONFIRMED` + 外部 leg 绑定 | 不是呼叫里程碑 |
| `initiated` | leg discovered/拨号已开始候选 | 不推进 interaction lifecycle，也不等于振铃 |
| `ringing` | leg `ALERTING` 候选事实 | B22-S 外呼只有已识别的对端腿可推进 interaction `RINGING`；呼入则只有最终坐席腿可推进，provider 原始状态本身不选择角色 |
| `answered` / `in-progress` | `LEG_MEDIA_READY` 候选 | 只是该 Call/leg 与平台媒体就绪；另需两条正确角色腿的显式两方媒体关系证明才能产生 interaction `CONNECTED` |
| `busy` | `TERMINATED(BUSY)` | 保留安全分类，不保存 SIP 原始错误文本 |
| `no-answer` | `TERMINATED(NO_ANSWER)` | 终态不能被晚到的 ringing 倒退 |
| `canceled` | `TERMINATED(CANCELED)` | 不推断由谁挂断 |
| `failed` | `TERMINATED(PROVIDER_FAILURE)` | 原始 SIP code 和厂商诊断留在私有适配器 |
| `completed` | 正常结束候选事实 | 不能仅凭该值推断远端或坐席主动挂断 |

`SequenceNumber` 可帮助 adapter 为同一 `CallSid` 构造稳定事实键和处理乱序，但它不是跨平台公共排序保证。核心只单调补充里程碑；终态先到时也只能在时间/顺序证明兼容时补录更早的历史里程碑或更具体但不冲突的终止分类。

### 3.4 录音

Call completed callback 中的 `RecordingUrl` 可能尚不可访问；Twilio 明确要求用独立 Recording Status Callback 判断录音何时真正可用。录音应独立映射为 `PROCESSING`、`AVAILABLE` 或 `UNAVAILABLE` 一类事实：

- `RecordingSid` 可以成为 adapter-scoped 的不透明 recording 引用；
- `RecordingUrl`、账号凭据、音频下载、签名 URL 和保留策略全部留在私有媒体适配器；
- Call `completed` 不说明录音媒体已经可访问，也不把 URL 暴露给普通呼叫详情；
- `absent` 或明确失败只收敛为安全的不可用状态，不把厂商错误原文写入核心。

## 4. 已验证事实输入与核心持久化 envelope

名称仍为 spike 临时命名，不预占 Java 类型。适配器提交的已验证事实只包含它能够证明的值：

| 字段 | 要求 |
| --- | --- |
| `deliveryRef` | connector 内必填；adapter 必须保证同一事实重投时稳定。云 callback 可按有界语义键确定性合成，易失事件流则需要 journal，用于核心 inbox 唯一性 |
| `sourceEventRef` | 可选；存在厂商原生 event ID 时保存，不把它设为跨适配器必填 |
| `interactionRef` | 已绑定外呼事实必填；呼入发现首事件为空，由核心生成后返回 |
| `commandId` | 可选的本地命令相关性，不承担认证 |
| `externalInteractionRef` | adapter 生成的不透明会话分组引用 |
| `externalLegRef` | leg 事实必填；B22-S 首期每个 interaction 最多绑定一个 `AGENT` 和一个 `REMOTE_PARTY` 规范腿，Twilio `CallSid` 由 adapter 映射为相应 opaque 引用 |
| `externalRecordingRef` | 仅录音事实可选；例如映射 `RecordingSid`，不包含媒体 URL 或凭据 |
| `factKind` | 使用 [B22-S](B22-state-and-topology.md) 固定的 typed lifecycle facts；不接受通用 `factKind + Map` |
| `occurredAt` | 可选的厂商时间；核心另存数据库接收时间，不能只依赖它排序 |
| `direction` / `terminationCategory` | 有界规范方向与安全终止分类；不能携带原始厂商错误 |
| `partyAddress` | 仅呼入发现等明确场景可选的专用敏感值；与普通事实字段隔离并受长度、格式、加密、tenant 分区和保留策略约束 |

核心在验证 connector principal 后构造自己的持久化 envelope：

| 核心派生字段 | 规则 |
| --- | --- |
| `tenantId` | 只从启用的 opaque connector 注册派生，不接受 adapter 或请求体声明 |
| `receivedAt` | 使用数据库/核心接收时间生成，不信任外部时间 |
| `normalizedDigest` | 核心对不含直接敏感值的规范字段计算，或使用核心密钥 HMAC；同一 `deliveryRef` 内容不同必须失败关闭并告警 |
| `interactionRef` | 呼入发现时按 `(connector, externalInteractionRef)` 幂等创建；后续事实只能使用已绑定值 |

不进入普通生命周期事实或客户端公共契约的字段包括 `AccountSid`、原始 `CallStatus`、`SipResponseCode`、地理参数、STIR/SHAKEN 细节、完整号码、原始 callback、`RecordingUrl`、价格和厂商异常。完整外呼目标只由应用层把授权时的服务端字段快照通过受控内部命令值对象交给 adapter；它不得由客户端任意提交，也不得写入普通 API 输出、日志或通用事实。

## 5. 呼入发现和 integration principal

Twilio 呼入首先要求 Webhook 返回 TwiML 指令；路由、IVR、排队和设备选择属于私有电话适配器，不进入 CRM 核心。核心只接收一个已验签的 `INBOUND_DISCOVERED` 规范事实，按 `(connector, externalInteractionRef)` 幂等创建本地 interaction，然后把 `interactionRef` 返回给适配器用于后续事实。

呼入发现可以携带一个经过 adapter 初步规范化、长度受限的专用 `partyAddress`，但不能放入通用事实 map。核心仍按 B22-D 独立执行 E.164 校验，以 tenant/purpose key 生成 HMAC 指纹并保存加密值、固定脱敏展示和绝对截止。integration principal 只证明 connector 来源，不能取得 CRM 候选或凭号码自动关联；候选建议与关联只能在显式人工 caller 下重新验证当前记录、数据范围和未脱敏字段权限。

外部事件不能伪装成人工坐席。B22 已固定 integration principal 只包含经过入口认证的 `connectorRef`；核心再从开源侧启用的 opaque connector 注册中解析 tenant。它不包含 actor、role 或 authority，也不复用 `AuthenticatedCaller`。若事件需要关联坐席，适配器提交 connector-scoped 的外部坐席引用，经 B22-S 不可改绑的受治理目录解析为当前 tenant 成员并再次校验活动成员关系。

这个模型要求仓库在正式实现前显式扩展“每个用例都接收 `AuthenticatedCaller`”的架构规则：人工用例继续接收 `AuthenticatedCaller`，integration ingress 改为接收独立、不可由普通 HTTP 请求构造的 connector principal。

## 6. inbox、应答与对账

- Twilio 官方只说明 `I-Twilio-Idempotency-Token` 可用于区分 retry attempts，并未给出同一逻辑事件各次重试是否复用、唯一性范围或保留期；它只能留作私有传输诊断，不能提升为核心 event ID。核心以 adapter 保证稳定的 `deliveryRef` 去重；Twilio 适配器可用 `CallSid + CallbackSource + SequenceNumber` 或 `RecordingSid + status + callback family` 确定性合成。
- 核心至少在 inbox 提交后才允许 Webhook 返回 2xx；状态推进可在同一短事务完成，也可由 inbox worker 后续执行。重复 delivery 返回相同成功；相同 delivery 内容不一致失败关闭。
- 状态推进不能依赖到达顺序。终态不会被旧事件倒退，录音事实可以在呼叫终态后独立到达。
- Retrieve Call 官方标注为最终一致，只能用于适配器对账，不能在 callback 同一请求里当作强一致补丁。
- 适配器 readiness 必须验证所需 status/recording callback 已配置；默认只回 `completed`、queued 状态取消无 callback、厂商有限重试或 callback 配置漂移都要形成可观察的 gap，而不是假装事件完整。
- 核心必须保留 `AMBIGUOUS` dispatch 和可对账状态，不能把“callback 暂未到达”自动翻译为失败或重新拨号。对账能力应声明可查询的活动/终态范围、最终一致窗口和无法恢复的观测缺口。

## 7. 正式实现阻断项

1. opaque connector 注册、启停/退役、tenant 唯一不可改绑和独立进程参考 ingress 认证拓扑已由 [B22 第 12 节](../../batch-B22.md#12-integration-principal-与-connector-tenant-解析设计闭合)闭合；实现仍需用真实传输 fixture 验证 credential 轮换、停用竞态和安全错误映射。
2. 用户侧 `requestId`、interaction/request binding/outbox 原子提交以及 `commandId` 的独立作用域已由 [B22-R](../B22-R-reliability.md)闭合；实现不得用一个标识同时近似两层幂等。
3. 外呼 `AMBIGUOUS` 和 adapter start guarantee capability 已由 B22-R 闭合；实现禁止默认承诺 exactly-once 拨号或自动补拨。
4. interaction 与 leg、接通判定、终止分类和 observation 正交状态已由 [B22-S](B22-state-and-topology.md) 闭合；实现仍需按两个规范参与腿和显式两方媒体证明执行 fixtures。
5. 不预带本地 `interactionRef` 的幂等呼入发现已由 B22-R 闭合；实现仍须按 connector/session 约束生成本地引用。
6. 号码与敏感值边界已由 [B22-D 第 14 节](../../batch-B22.md#14-b22-d-敏感数据录音访问与保留设计闭合)闭合：外呼只选择当前可见未脱敏的权威字段并保守规范化为 E.164；`telephone` 只能显式选择且整体通过规则，不能自动拨号。外呼快照、呼入 AEAD/HMAC/固定脱敏、tenant/key 分区、轮换、保留和销毁不再是阻断项，实现仍需按第 14.10 节完成 fixture。
7. CRM member、connector agent binding、私有 endpoint/device 和按成员 configuration/presence 投影已由 [B22-S](B22-state-and-topology.md) 分层；真实云 adapter 的 capability/readiness、按 route/account/resource 隔离和漂移处理已由 [B22-A](../B22-A-adapter-readiness.md) 固定，私有映射及真实 fixture 仍待实现。
8. `externalRecordingRef` 的加密/HMAC、独立录音授权、禁止厂商 URL、到期停访和确认删除已由 B22-D 闭合；[B22-M](B22-media-transfer.md) 已进一步固定每次请求重新授权的同源代理流、单录音四态、WAV/MP3、可选 single byte-range、取消/上限和云媒体 fixture，不签发浏览器 grant。
9. 点击外呼已由 [B22-S](B22-state-and-topology.md) 固定为 `OUTBOUND_AGENT_FIRST`：坐席腿 `MEDIA_READY` 前禁止创建或呼叫客户腿，只有两条正确角色腿的双向媒体关系证明可以产生 `CONNECTED`。
10. capability/readiness probe 已由 B22-A 固定为最长 5 分钟、按操作的持久化快照：认证与账号归属、命令权限、callback route/事件集合、活动/终态查询与 replay/cursor、adapter build，以及 B22-M 的 stored-object 时点、规范格式、媒体读/确认删除、内部 redirect/egress、range、上限和厂商 API 版本都必须可审计；周期探测不得产生真实电话或媒体副作用。
11. [B22-CW 云回调组合契约 fixture](../B22-CW-cloud-callback-fixture.md) 已固定重复、乱序、callback-before-command-response、多腿、录音晚到、首字节后媒体取消和 readiness 漂移的统一 checkpoint，并同时执行 B22-R/D/S/M/A 断言；默认/漏配 callback、queued 取消、有限重试和最终一致查询继续作为 readiness/gap 前置输入。

B22-A 已闭合第 7、10 项的设计语义，[B22-CW](../B22-CW-cloud-callback-fixture.md) 已闭合云 callback 组合契约，[B22-CP](../B22-CP-pbx-composed-contract.md) 后来闭合 PBX reconnect/session/topology/subscription/gap 的核心组合，[B22-PC](../B22-PC-pbx-command-contract.md) 再闭合 PBX command crash/`AMBIGUOUS` 组合，[B22-PD](../B22-PD-pbx-sensitive-data-contract.md) 又闭合 PBX 敏感数据组合，[B22-PM](../B22-PM-pbx-media-contract.md) 进一步闭合 PBX stored-recording/media 组合；这些设计都不代表真实 adapter 已通过 fixture。真实 provider conformance 仍未闭合，因此暂不拆 B22a/B22b 实现子批次。

## 8. 官方依据

- [Twilio Call Resource](https://www.twilio.com/docs/voice/api/call-resource)：Call 创建、`CallSid`/`ParentCallSid`、进度事件、`Timestamp`、`SequenceNumber`、乱序说明和 Recording callback。
- [Twilio Webhook Security](https://www.twilio.com/docs/usage/webhooks/webhooks-security)：完整 URL、form 参数、JSON 原始 body、`bodySHA256`、签名校验和非固定来源 IP。
- [Twilio Webhook Connection Overrides](https://www.twilio.com/docs/usage/webhooks/webhooks-connection-overrides)：retry 策略、`I-Twilio-Idempotency-Token` 和呼叫请求 15 秒硬超时。
- [Twilio Voice Webhooks](https://www.twilio.com/docs/usage/webhooks/voice-webhooks)：呼入请求/响应和 Call Status Callback 边界。
- [Twilio Record](https://www.twilio.com/docs/voice/twiml/record)：录音独立状态 callback 与可访问时点。
- [Twilio Recordings Resource](https://www.twilio.com/docs/voice/api/recording)：completed recording 的 WAV/MP3 媒体读取、provider 认证和对象缺失/删除边界。
- [Telnyx Voice Webhooks](https://developers.telnyx.com/docs/voice/programmable-voice/voice-api-webhooks)：另一云模型对 session、leg 和 control handle 的区分。
- [Telnyx Command Retries](https://developers.telnyx.com/docs/voice/programmable-voice/command-retries)：厂商命令去重能力具有明确时间窗口的交叉样本。
