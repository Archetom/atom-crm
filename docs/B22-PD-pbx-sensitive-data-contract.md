# B22-PD PBX 敏感数据与确认销毁组合契约 fixture

> 决策日期：2026-07-19
>
> 状态：PBX 号码、密钥、retention、quarantine allowlist、日志泄漏哨兵与确认销毁的实现前组合契约已闭合；本文件冻结确定性 fixture、独立 oracle、crash matrix 和失败关闭条件，不实现 adapter、核心 runtime、API、表、配置、KMS、备份系统、Docker PBX 或真实电话调用
>
> 输入：[B22 总规划](../batch-B22.md)、[B22-R 可靠性设计](B22-R-reliability.md)、[B22-D 敏感数据设计](../batch-B22.md#14-b22-d-敏感数据录音访问与保留设计闭合)、[B22-A readiness 与隔离设计](B22-A-adapter-readiness.md)、[B22-CP PBX 核心组合契约](B22-CP-pbx-composed-contract.md)、[B22-PC PBX command 组合契约](B22-PC-pbx-command-contract.md)和 [PBX 事件流 spike](spikes/B22-pbx-event-stream.md)

## 1. 决策摘要

B22-PD 把 B22-D 已冻结但尚未在 PBX 路径共同验证的安全不变量放进一套双 tenant、双 connector、可轮换密钥、可推进虚拟时间、可崩溃恢复的组合 trace：

- 外呼仍从当前可见且未脱敏的权威 CRM 字段形成一次不可变 E.164 快照；普通 outbox、adapter ledger、session、journal、quarantine metadata、PBX correlation 和 conformance 报告都不能保存完整或脱敏号码；
- 完整号码只存在于 tenant/purpose/AAD 绑定的受保护 payload，且只在规范化、AEAD/HMAC 或 B22-PC 允许的 provider invocation 调用栈内短时解封；重试不能改读新 CRM 值、改 key purpose、改 connector 或生成明文恢复副本；
- PBX frame 在受治理 stream 已确定 connector 后按固定字段目录抽取。Caller ID、destination、extension、channel variable、SIP header、command argument、raw frame/body 和 native error 默认在 journal 前丢弃；确需短期重放的原始材料只能进入独立加密 payload store，不能进入普通 quarantine；
- active/retiring key、加密与 HMAC purpose、core 与 adapter at-rest key、PBX credential 和通用幂等 digest key 相互分离。key provider 不可用、tag/AAD 错误、未知 key version 或轮换库存不完整时失败关闭，不回退明文；
- policy deadline 是数据属性，不是 worker 建议。gap、`AMBIGUOUS`、未完成 journal、容量压力、connector 停用或 KMS 故障都不能延长号码或 raw payload；到期后保留最小可靠性 tombstone，但删除全部可恢复敏感材料；
- 泄漏哨兵同时覆盖应用 sink 与 PBX 控制范围内的 CDR/CEL、事件导出、控制台、syslog、旋转日志、spool 和异常采集。只证明应用日志干净、只扫描最终字符串或只执行 log rotation 都不算通过；
- “确认销毁”区分在线副本已清除、adapter/PBX 受控副本已核验不存在、key 最终销毁和备份到期/恢复前 sweep。删除请求、逻辑 tombstone、KMS scheduled deletion、文件 rotation 或普通 `NOT_FOUND` 都不能被报告成所有副本已销毁。

本 fixture 只闭合 PBX 号码及 adapter 敏感 payload 的组合设计。B22-PM 仍独立负责 stored recording、媒体流和媒体对象删除确认；B22-CANARY 仍负责真实 PBX、SIP trunk/运营商、部署日志链和真实 KMS/备份环境的私有证据。因此 B22-PD/1 通过不表示真实环境已完成数据删除，也不触发正式实现拆批。

## 2. Fixture 边界与独立 oracle

### 2.1 固定双 connector 部署

fixture 复用 B22-CP/PC 的同进程双 connector 结构，并为每个分区放入不可与真实值混淆的 sealed canary：

| Fixture identity | Connector A | Connector B |
| --- | --- | --- |
| tenant | `tenant-a` | `tenant-b` |
| connector | `connector-a` | `connector-b` |
| command/session | `command-23` / `session-7` | 允许相同 token，但属于 B 分区 |
| governed stream | `stream-a` | `stream-b` |
| core data-key generation | active `a-k2`、retiring `a-k1` | 独立 active `b-k4` |
| adapter payload-key generation | active `a-p3` | 独立 active `b-p8` |
| retention policy | dev/test 确定性策略 `policy-a-3` | 独立 `policy-b-5` |

sealed registry 生成并持有以下类别，但测试报告和普通失败输出只能引用类别与安全本地 ID，不能读取值：

- 两个完整 E.164 地址、它们的 digits-only/末四位/固定 mask、URL/form/JSON/base64/hex 表示；
- 外呼 dial target、呼入 Caller ID、destination/extension、SIP header、channel variable、ARI `appArgs`、ESL `Job-Command-Arg`/body；
- credential、key bytes、wrapped key、key reference、HMAC、ciphertext、nonce、tag、native channel/session ID、provider error 和异常 message；
- 原始 ARI/ESL frame、CDR/CEL 行、日志行、spool record、媒体路径和 recording reference。

真实号码从不写在 fixture 源码、参数化测试名或 snapshot 中。driver 只能通过受监控的窄操作使用 sealed 值；任何普通 getter、序列化、字符串拼接或报告渲染都使场景失败。

### 2.2 五个相互独立的观察面

独立 oracle 只读观察提交后的状态，不复用被测 adapter 的 expected-state 方法：

1. **核心 store**：interaction/outbox/inbox 的普通元数据、独立号码 envelope/HMAC、policy snapshot/deadline、purge marker、retention cursor 和最小 tombstone；
2. **adapter store**：command ledger、journal、session/ownership、普通 quarantine metadata、可选独立加密敏感 payload、payload deadline、访问审计和删除证明；
3. **PBX surface oracle**：模拟或隔离 PBX 的活动对象、CDR/CEL backend、event/CDR exporter、queue log、console/file/syslog、旋转文件、failure spool 和声明纳管的历史投影；
4. **key/backup oracle**：独立 key provider 的 active/retiring/destroy-scheduled/destroyed 状态、purpose 调用计数、wrapped-material inventory，以及不可由被测 store 回写的备份 snapshot/restore gate；
5. **sink oracle**：HTTP/transport/access log、结构化日志、MDC、trace/baggage/event、metric label/exemplar、audit、exception/crash capture、object serialization/`toString`、CLI/debug export 和 conformance artifact。

oracle 可以在隔离测试进程内比较 ciphertext/HMAC 与 canary，最终报告只能输出固定分类、数量和不含秘密的 manifest/evidence digest。PBX surface 不在 manifest 中的副本使 readiness 和场景失败；测试不能通过“oracle 没查那个目录/后端”宣称无泄漏。

### 2.3 数据面 manifest

每个 build/profile/binding revision 在测试前冻结一个有界 data-surface manifest。每个可能接触号码或 raw PBX 数据的面恰好声明一种处置：

| 分类 | 允许内容 | 退出条件 |
| --- | --- | --- |
| `EPHEMERAL_ONLY` | 规范化、AEAD/HMAC 或 provider invocation 当前调用栈中的 sealed plaintext | 调用返回/取消后不再可枚举；不宣称 Java/PBX 内存物理擦除 |
| `GOVERNED_ENCRYPTED` | 独立 payload store 中的 AEAD envelope、key version、绝对 deadline 和本地 payload ID | retention 删除与独立不存在证明；普通 store/sink 不得读取 envelope |
| `SAFE_ALLOWLIST` | 固定本地 ID、安全分类、服务端时间、版本和计数 | 按自己的可靠性/审计期限删除；永不含直接或派生号码 |
| `FORBIDDEN` | 不得创建的明文列、raw dump、dial string/CDR/CEL/日志副本、URL/path、任意属性 map | 发现一份即 conformance 失败，不能靠后置清洗或较短 TTL 合法化 |

manifest 至少枚举核心 DB/cache/outbox、adapter ledger/journal/session/query/quarantine、PBX CDR/CEL/queue/event/log/spool、sidecar/agent/syslog、备份和 crash dump。配置文件中的真实 endpoint、路径、KMS reference 和 credential 仍是私有部署信息；报告只包含 manifest 的规范 digest。

### 2.4 Driver 动作词汇

共享 harness 至少提供以下语义动作：

- `commitAuthoritativeSnapshot(caller, record, field)`：在核心事务内重新授权、规范化并保存独立号码 envelope；
- `deliverSealedCommand(connector, command)`：只按原 command/payload identity 投递，不暴露号码 getter；
- `invokePbx(connector, sealedTarget)`：B22-PC 唯一受监控 provider invocation 边界；
- `acceptSensitiveFrame(owner, fence, frame)`：由 governed stream 先选 connector，再按固定目录抽取专用地址输入；
- `quarantine(reason, safeEvidence, optionalSealedPayload)`：普通 metadata 与可选敏感 payload 分仓提交；
- `rotateKey(tenant, purpose)`、`rewrapPage(cursor)`、`rehmacPage(cursor)`、`requestKeyDestruction(version)`：驱动轮换与销毁门；
- `shortenPolicy(tenant, version)`、`runRetentionPage(tenant, cursor)`：使用虚拟时间执行有界、幂等 sweep；
- `snapshotBackup()`、`restoreOffline()`、`openServiceGate()`：证明恢复环境先 sweep 后服务；
- `crashCore()`、`crashAdapter()`、`restartFromDurableState()`：清空内存，只从提交状态恢复；
- `scanAllSinks()`、`scanPbxSurfaces()`：执行结构白名单与 canary byte/text 扫描。

driver 不能直接编辑 deadline、purge marker、key inventory、quarantine state 或销毁证明，也不能让被测 adapter 自己宣布某个 PBX 文件/后端不存在。

## 3. 初始门与 PBX profile

产生第一条敏感值前必须同时证明：

- production-like profile 有显式可用的 key provider、deployment retention ceiling、版本化 tenant policy、backup policy、时钟边界和 data-surface manifest；dev/test key provider 不能被装入 production profile；
- core 与 adapter 的 at-rest key、PBX transport/control credential、connector credential、HMAC purpose 和幂等 digest key 分离；任一 key reference 不能作为 connector、tenant、session 或 command identity；
- A/B 的 key inventory、payload store、quarantine、retention cursor、capacity、backup namespace 和 sink route 分区；A 的高水位、轮换或销毁不能读取、删除或阻塞 B；
- 普通 journal/ledger/session/quarantine 只接受 `SAFE_ALLOWLIST`；可选 `GOVERNED_ENCRYPTED` raw replay profile 必须声明业务必要性、固定最大 bytes/rows、独立访问审计和不晚于 adapter sensitive-journal policy 的 deadline；
- 所有到期删除都可以在 key provider不可用时按本地 ID 删除 ciphertext/envelope；sweep 不能以“无法解密确认内容”为由延期；
- B22-CP stream owner/fence 与 B22-PC command ledger/start guarantee 已有效。旧 fence 在 key lookup、payload append、quarantine 和 purge confirmation 上同样不能写入；
- PBX 控制范围内的 CDR/CEL、event exporter、console/file/syslog、queue log、debug/trace、rotated log、spool 和备份已被显式关闭、最小化为安全字段，或纳入受治理加密/保留/删除面。

Asterisk profile 必须特别枚举 CDR、CEL 及其全部已加载 backend、queue log、logger channel/syslog 和动态 debug channel。CEL/CDR 可包含 Caller ID、extension、application data、user field、peer/native identity；`logger rotate` 只改文件世代，不证明旧文件删除。

FreeSWITCH profile 必须特别枚举 channel event 的 `Caller-*`、`Other-Leg-*`、`variable_*`、`Channel-Name`、`BACKGROUND_JOB` command/argument/body，以及所有 CDR、event handler、console/logfile/syslog sink 和错误落盘 spool。event subscription/filter 只减少事件集合，不把已收到 event 的公共 header 变成安全字段；`rotate-on-hup` 或 `maximum-rotate` 也不替代 policy deadline 与确认删除。

若真实部署保留含号码的电信计费/法定 CDR，必须把它声明为 B22 边界外的独立受治理处理活动并从 B22-PD 删除声明中排除；该部署不能把 B22-PD 报告描述成 PBX/运营商所有号码副本已销毁。未登记的 CDR/CEL 副本则直接使 readiness 失败，不能用“可能是运营要求”事后豁免。

## 4. `PD-01` 外呼快照、dispatch 与失败关闭

主 trace 在 A 的 CRM 字段保存 sealed address `A1`，并按以下顺序执行：

1. 人工请求只含业务类型、记录 ID、field code 和 request id。核心在同一事务重新验证 caller、数据范围和 `READ + mask=NONE`，以 `E164_V1` 规范化 `A1`，保存 tenant/purpose/AAD 绑定的 AEAD envelope、固定末四位展示、policy version/deadline 与唯一 outbox；普通 outbox 不含地址；
2. 事务提交后把 CRM 字段改为另一 sealed address `A2`，再以相同 request id 重试。返回首次 interaction，envelope identity/digest 和 deadline 不变，绝不重新读取或改写为 `A2`；
3. core worker 与 adapter 分别按 B22-PC gate 处理同一 command。adapter ordinary ledger 只保存本地 identity/digest/state；若 start guarantee 的崩溃恢复确需地址副本，只能原子写入独立 `GOVERNED_ENCRYPTED` command payload，不能把明文/dial string 放入 ledger、session 或 provider key；
4. provider invocation 按声明的 `AT_MOST_ONCE_START` 或 `IDEMPOTENT_START(window)` 次数即时解封同一 `A1` identity。每次调用后 provider argument 对测试代码不可枚举；attempt 可以符合 B22-PC 上限，但不能产生任何额外持久明文副本；
5. PBX response/exception 同时携带 `A1`、dial string、native ID 和 credential canary。adapter 只提交固定 outcome/reason，不能记录 throwable/message、response dump 或命令文本；
6. 交换 A/B 重跑，并尝试用 B connector、B key、错误 purpose/AAD、未知 version、篡改 tag 和不可用 key provider处理 A payload。所有分支在 PBX I/O 前失败，B 的 store/key invocation 不变。

公开请求直接提交号码、dial string、connector、tenant、外显号码或未知属性时，必须在创建 interaction/outbox/envelope 前稳定拒绝。key provider 在 snapshot 事务中暂时不可用时整个事务回滚；已经提交但 dispatch 时不可用则 outbox 保持安全待处理或进入明确不可派发状态，不返回空号码、不调用 PBX、不创建明文 fallback。

## 5. `PD-02` 呼入地址、journal 与 tenant HMAC

A 的合法 frame 同时携带一个 `PRESENT` sealed Caller ID 和 destination、extension、ANI、其他腿号码、SIP header、任意 variables、native error/body 等攻击 canary：

1. `stream-a` 与当前 fence 在解析 frame body 前选定 `connector-a`。adapter 只能从 profile 固定的唯一 party-address source 构造专用 `PRESENT` 值；其他字段立即丢弃，不进入通用 map、日志或普通 journal；
2. adapter 在 core ack 前需要重投该地址时，把已选地址放入独立 tenant/connector/purpose/AAD 绑定的 AEAD payload；journal 只持有本地 payload ID、稳定 delivery/digest、状态、deadline 和安全分类。raw frame 本身不落盘；
3. 核心重新执行 `E164_V1`，保存自己的独立 AEAD envelope、完整 32-byte tenant `PHONE_MATCH_HMAC`、key version、固定末四位和 absolute deadline。adapter ciphertext 不能直接复制成核心 ciphertext；
4. core inbox commit/ack 丢失后，adapter 用相同 delivery 与同一 sealed payload 重投。核心 duplicate/no-change 后 adapter 删除可删 payload；journal tombstone继续按 B22-R 窗口保留但没有号码；
5. 同 tenant/同 active HMAC key/同号码产生相同 token；不同 tenant、purpose、key version或号码产生不同 token。查询始终带 tenant 和 key version，普通输出、日志、cache key 和 report 不能读取 token；
6. `WITHHELD`、`UNAVAILABLE`、`INVALID` 不得携带任何占位/raw address；无法规范化的 `PRESENT` 只能安全拒绝或转换为不含原值的固定分类，不能把失败值放入 error/quarantine metadata；
7. 若地址 payload 在 core ack 前到期，retention 删除 ciphertext 并留下 `SENSITIVE_PAYLOAD_EXPIRED` 安全事实，相关 observation 打开 gap/对账；pending journal 不得保住号码，也不能从原 frame、PBX CDR 或日志重建它。

把 B ownership frame 发到 A stream、让 body 伪造 tenant/connector 或让旧 fence重投敏感 payload，继续执行 B22-CP 隔离分支。攻击只能在已选 A 分区留下安全 allowlist 记录；A/B 的 envelope、HMAC、key invocation、deadline 和 purge marker 都不能交叉。

## 6. `PD-03` quarantine allowlist 与容量

### 6.1 普通 quarantine 固定目录

普通 quarantine 记录只允许以下逻辑字段；最终名称和表结构留给实现批次：

| 允许 | 条件 |
| --- | --- |
| tenant/connector 的内部绑定与安全展示、local quarantine/delivery ID | tenant 只来自已验证 connector；普通报告不得输出可反查 provider 的 native ID |
| 固定 reason/category、source kind、owner fence、服务端 received/expiry time | reason 是服务端枚举，不含 provider text |
| policy/key schema version、payload presence/size bucket、attempt/state/count | key version 可审计，但 KMS reference/wrapped key 不可见 |
| keyed evidence digest、独立 payload ID | 只供相等性/完整性；不得进入日志、metric label、API 或跨 connector 查询 |

禁止字段包括完整/脱敏号码、HMAC、ciphertext/nonce/tag、raw frame/body/header、dial string、channel variable、SIP/SDP、native ID/path、credential、provider endpoint/response/error、exception message、任意属性 map 和序列化请求对象。

### 6.2 可选敏感 replay payload

profile 默认是 `ALLOWLIST_ONLY`。只有协议重放确有必要并在 readiness evidence 中显式声明时，才允许把有界原始材料写到独立 `GOVERNED_ENCRYPTED` store：

- 写入前 connector/tenant 已由 stream/route 确定；使用与 core address、phone HMAC、credential 和普通 digest 不同的 adapter at-rest key/purpose；
- payload bytes、单 connector rows/bytes、单项 deadline 和总 quarantine limit 都有硬上限；超大输入不做“先完整落盘再截断”，只留下安全 `OVERSIZED` 分类并按 PBX 语义开 gap/断流；
- ordinary quarantine transaction 只引用 payload ID；envelope 不能出现在查询投影、错误、日志、trace、metric、审计详情或报告；
- 首期应用没有普通 raw 查看 API。部署若提供 break-glass 运维读取，必须是独立身份、理由、短会话、逐次审计且不改变 deadline；B22-PD 只验证该读取不会写入新 sink，不把它当成 CRM 权限；
- 到期、处置完成或 connector cleanup 删除 payload，不等待普通 quarantine/tombstone 到期。key provider 不可用不阻止按本地 ID删除 envelope。

A 的 sensitive/high-water 或 quarantine hard limit 不能淘汰 A 的 unresolved metadata，更不能占用 B 的分区。到达 hard limit 时 A 撤销受影响 ingress readiness，PBX stream按协议断开或施加有界 flow control，并使用独立 control capacity 先写 gap；B 仍可正常 ingress。容量恢复不会恢复已到期 payload，也不会自动清除 gap。

## 7. `PD-04` key 轮换与销毁门

轮换场景从 A 的 retiring `a-k1`/active `a-k2` 和 adapter active `a-p3` 开始，B 的 `b-k4`/`b-p8` 全程不变：

1. 新 core 地址写入只使用 `a-k2`；HMAC 查询只在 active 与唯一 retiring version 上 dual-read，不能扫描任意历史 key。adapter payload 新写只使用自身 active version；
2. 有界 re-encrypt/rewrap 与 re-HMAC worker 按 tenant、purpose、本地 ID 游标处理。每项在写入新 envelope/token 前重新检查 deadline；已到期项直接 purge，不为轮换解密或延寿；
3. worker 在读取旧 envelope、新 key调用、新 envelope提交、旧 envelope清除和 inventory checkpoint 的相邻点崩溃。恢复后每项要么仍由旧 version 完整可读，要么由新 version 完整可读，不出现明文中间列、双 active 写入、丢失 purge 或跨 tenant副本；
4. 篡改 tag/AAD、把 interaction/connector/purpose交换、key version未知或 provider返回错误 key material 时失败关闭并发出安全分类；底层异常和 key reference 不进入 sink；
5. retiring key 只有在 core、adapter、quarantine、索引/token、恢复清单和受治理副本的 inventory 均为零或已选择可验证 crypto-erasure 后才可请求销毁。抽样验证不能替代完整计数与 store约束；
6. KMS `DISABLE_REQUESTED`、`DESTROY_SCHEDULED` 或暂时不可用不是销毁确认。只有 provider 的最终不可用/已销毁证据与本地 inventory gate 同时成立才记录 `KEY_DESTROYED_CONFIRMED`；相同命令可重试但不重建 key；
7. PBX/connector credential 轮换与 data-key 轮换正交。credential 变化不能重包号码、改变 HMAC、session/command identity 或重置 retention；data-key 变化也不能恢复已撤销 credential。

普通单条号码删除不得通过销毁共享 tenant key冒充即时 crypto-shred。tenant cleanup 才能在停用 connector、阻止新 command/ingress、清空全部在线 payload/HMAC/wrapped material 后销毁 tenant key；如果备份仍可能与 key共同恢复，则最终报告保持 `BACKUP_EXPIRY_PENDING`，不能声称所有副本已销毁。

## 8. `PD-05` retention、gap 与 restore-before-service

dev/test 使用 B22-D 已冻结的确定性基线和虚拟时钟。A 的地址在 `t0` capture、`t1` terminal 时，fixture 显式计算 `min(t1 + 30 days, t0 + 7 days)`；本组向量令后者更早，因此地址在 `t0 + 7 days` purge。这个较早 absolute cap 不因终态、gap、`AMBIGUOUS`、人工处置或 worker停机延长。

场景同时覆盖：

- `t0 + 3 days` 把 policy 缩短到 4 天，有界重算只把未到期 deadline提前；随后延长到 60 天只作用于新 capture，旧 deadline 不后移；
- adapter sensitive journal/quarantine deadline 不晚于 7 天；core ack、quarantine 处置或 reliability tombstone需要保留更久时，只保留无地址的 identity/digest/outcome；
- open gap、unresolved `AMBIGUOUS`、reconciliation case 和未完成 journal 都不被 TTL/LRU 删除，但其号码、raw payload 和可恢复 crypto metadata仍按各自 deadline purge；后续只能如实报告证据不完整，不能从 CDR/CEL/log重建地址；
- retention worker使用 tenant cursor、固定小页、按本地 ID稳定排序和行锁。两 worker并发、cursor commit前后崩溃、重复 page和 connector停用都只产生一次逻辑 purge；B 分区不变；
- core purge 原子清除 ciphertext、nonce/tag、HMAC、key version和所有可恢复材料并写固定 `PURGED`；adapter purge 清除独立 payload。末四位只保留到 interaction metadata deadline，且从不进入日志/metrics；
- purge 不需要解密。KMS outage、tag损坏或 retiring key丢失不能阻止删除 ciphertext/HMAC；删除失败保持到期不可访问并安全告警，不能延后 deadline。

随后在 `t0 + 8 days` 恢复一个 `t0 + 2 days` 的 core、adapter 与 PBX 受控面备份。恢复环境默认网络/HTTP/worker/provider credential关闭，先恢复 policy/key inventory，再运行全量有界 sweep和 PBX surface purge验证；只有所有已到期在线副本不可读取、未到期副本通过当前 tag/AAD且 readiness重新探测后，`openServiceGate()` 才允许服务。先开放查询/dispatch/ingress、因 key缺失跳过 sweep或只扫核心不扫 adapter/PBX面都使 fixture失败。

## 9. `PD-06` 泄漏哨兵与 PBX 数据面

### 9.1 双层扫描

每个 scenario/checkpoint 同时执行：

1. **结构白名单**：在序列化/格式化前检查 DTO/VO/event、ordinary PO、outbox/ledger/journal/session/quarantine projection、log arguments、MDC、trace attributes/baggage/events、metric labels/exemplars、audit字段和exception capture对象的类型与字段目录；
2. **最终 sink 扫描**：扫描数据库允许列、缓存、HTTP body/header、日志文件/console/syslog、trace/metric export、audit、dead-letter、CLI/debug bundle、test failure、PBX CDR/CEL/event/log/spool、旋转文件、备份清单与conformance artifact。

byte/text scanner至少注册 raw canary及 digits-only、末四位/固定 mask、常见 URL/form/JSON escaping、base64与hex表示。它不是生产后置正则清洗方案；主要控制仍是 typed sealed value、字段 allowlist和sink前阻断。仅在最终日志上搜索完整 E.164 会漏掉结构化 exporter、部分号码、编码、exception和持久化副本，不满足 B22-PD。

允许值按 sink区分：固定末四位只允许在授权的 interaction response/受控数据库列，禁止出现在日志、trace、metric、普通audit和报告；policy/key schema version可以进入安全运维记录，KMS key reference、wrapped material和ciphertext metadata不可以。

### 9.2 PBX 负向场景

fixture 分别让 Asterisk/FreeSWITCH 模拟 profile 尝试：

- 把 Caller ID/destination、dial string或extension写入 CDR/CEL、queue log、FreeSWITCH CDR template或failure spool；
- 打开 Asterisk debug/verbose/dynamic log channel、FreeSWITCH debug/CDR `debug`、ESL `log`订阅、SIP trace或第三方event/CDR exporter；
- 把号码放入 channel name、app args、variable、account code、job argument/body、native error、recording/file name、syslog、APM异常或crash dump；
- rotate、compress或重命名含canary的日志/CDR，再只删除当前文件。

每个动作都必须在受治理配置门被禁止，或由 independent surface oracle发现并使本轮 conformance失败。rotation不减少副本数量；关闭主logger不代表console、syslog、CDR/CEL和event exporter已关闭。若对账必须使用PBX历史面，只允许安全opaque correlation和规范时间/状态，或把该历史面纳入独立加密、deadline和确认删除契约；不能以“reconciliation需要”为由永久保存号码。

provider/PBX SDK 抛出的异常对象本身也视为可能敏感。普通日志和error tracker只接收服务端稳定分类与本地ID，不接收原Throwable、message、request/response或SDK model；stack trace若会包含message同样禁止。安全告警可记录异常类别映射，但不能借独立安全日志复制canary。

## 10. `PD-07` 确认销毁与 crash matrix

### 10.1 证据状态而非单一布尔值

fixture 以以下正交证据描述清理进度；它们是设计状态，不预占最终枚举或表：

| 证据 | 含义 | 不能推出 |
| --- | --- | --- |
| `ACCESS_CLOSED` | deadline后新dispatch/match/raw-read已阻止 | ciphertext或PBX副本已删除 |
| `CORE_ONLINE_PURGED` | 核心所有受治理在线envelope/HMAC/index/cache已核验不存在 | adapter、PBX、backup已清理 |
| `ADAPTER_ONLINE_PURGED` | adapter payload/quarantine受治理在线副本已核验不存在 | PBX CDR/log或物理介质已清理 |
| `PBX_CONTROLLED_PURGED` | manifest内PBX CDR/CEL/log/spool副本已由独立oracle核验不存在 | 运营商或未受控系统副本不存在 |
| `KEY_DESTROYED_CONFIRMED` | tenant cleanup所选key已最终不可用且inventory gate为零 | 单行backup已物理覆写 |
| `BACKUP_EXPIRED_OR_CRYPTO_ERASED` | 声明的backup窗口已过且恢复测试无可服务明文，或相关key已确认销毁 | 底层介质已按任意标准物理销毁 |
| `EVIDENCE_COMPLETE` | 本报告声明范围内所需证据全部成立 | 全球、运营商、终端或内存中不存在任何副本 |

在线确认至少要求按本地 ID 的 authoritative get/list 返回不存在、所有二级索引/payload pointer/cache计数为零，并由与被测删除实现独立的store/PBX surface oracle验证。普通 provider/PBX `NOT_FOUND` 只有在当前 manifest声明它覆盖完整对象/历史范围时才可作为该面的一项证据；超时、第一页为空、删除命令已发送、HTTP 2xx、SQL tombstone或本地文件路径不存在都不自动满足全部面。

### 10.2 删除顺序与崩溃点

同一 trace 在以下相邻边界前后终止进程并恢复：

| Boundary | 恢复断言 |
| --- | --- |
| deadline/access-close commit | commit后任何新dispatch/match/raw-read为零；commit前重跑按同一deadline关闭 |
| core payload清除事务 | envelope/HMAC/pointer/purge marker全有或按设计全清，不留下可恢复孤儿 |
| adapter payload delete前后 | 相同purge command/idempotency key重试；删除完成不从journal/raw frame重建payload |
| PBX surface delete/rotate/checkpoint | rotation不算delete；manifest内所有世代/后端核验后才确认 |
| online confirmation写入前后 | 重复确认是no-op；确认记录不含敏感值且不能早于独立absence证据 |
| retiring key inventory checkpoint | crash后重新全量核对；不能因cursor完成但旧副本仍在而销毁key |
| KMS destroy request与最终确认之间 | scheduled保持pending；恢复不能创建替代同version key或提前报告confirmed |
| backup restore sweep与service gate之间 | service始终关闭直到core、adapter、PBX面全部sweep并重新验证 |

tenant cleanup固定顺序为：停止新command/ingress并撤销connector/PBX credential，关闭敏感访问，清理core与adapter在线payload/HMAC，清理manifest内PBX受控副本，完成backup策略选择，最后销毁tenant data-key version。安全tombstone只保留本地ID、固定分类、policy/key schema version、服务端时间与evidence digest；不能为了“证明删除”继续保存号码、HMAC、ciphertext或PBX路径。

Java对象、PBX进程内存、SSD/虚拟磁盘底层块和运营商记录不由一次应用delete证明物理擦除。需要介质级清理的部署必须另按存储/介质能力执行sanitization并保留外部证据；B22-PD不把数组清零、文件unlink或数据库DELETE夸大为介质级销毁。

## 11. 组合覆盖矩阵

| Fixture | B22-D | B22-R/PC | B22-CP/A | 独立安全 oracle |
| --- | --- | --- | --- | --- |
| `PD-01` outbound snapshot | E.164、AEAD、AAD、最小明文生命周期 | 同command/digest/start guarantee | connector/readiness/pre-I/O隔离 | key invocation、ledger/store、PBX sink scan |
| `PD-02` inbound address | AEAD、tenant HMAC、固定mask/deadline | journal/inbox重投、payload/tombstone分离 | stream先选connector、stale fence拒绝 | HMAC等值/隔离、raw frame absence |
| `PD-03` quarantine | encrypted raw exception、短期限、访问审计 | unresolved metadata不被TTL淘汰、control capacity | connector分区、high-water gap | allowlist schema、payload store与容量oracle |
| `PD-04` key rotation | active/retiring、purpose separation、destroy gate | crash重试不改identity | A/B key/credential正交 | independent KMS/inventory/backup oracle |
| `PD-05` retention | shorten-only、absolute cap、purge | gap/AMBIGUOUS不延寿、cursor幂等 |停用仍可安全清理 | virtual clock、restore-before-service |
| `PD-06` leak sentinel | 全部禁止值与mask边界 | error/tombstone不复制payload | PBX event/CDR/log surface inventory | pre-format allowlist + final byte scan |
| `PD-07` confirmed destruction | online/backup/key语义分离 | durable delete/confirm重试 | PBX controlled surface coverage | independent absence与KMS final evidence |

任一场景若只检查最终核心数据库、只扫描adapter日志、只验证key删除或只等待TTL，都不能宣称通过B22-PD。每个checkpoint必须同时比较A/B两个分区、ordinary与sensitive store、key/backup state、PBX surface和全部sink。

## 12. 执行层次与 conformance 输出

相同 `PD-01` 至 `PD-07` ID 必须运行在三层：

1. **纯规范模型层**：属性化生成deadline、rotation、crash、duplicate和tenant/purpose交换，证明允许状态与更早deadline；
2. **durable组合层**：真实MySQL核心、独立可重启adapter store、独立fake KMS/backup和Asterisk/FreeSWITCH surface simulator，使用真实事务/唯一键/文件世代与虚拟时钟；
3. **真实PBX adapter conformance层**：隔离Asterisk/FreeSWITCH节点、真实配置/module/sink inventory、真实KMS/secret-backedprovider与backup/restore流程，仍使用合成号码且不连接真实线路。会触发真实SIP trunk/运营商数据面的canary留给B22-CANARY。

机器可读报告使用fixture version `B22-PD/1`，至少包含：

- adapter contract/build evidence digest、PBX profile、binding revision、isolation level、data-surface manifest digest、key-provider class和retention-policy schema version；
- `PD-01`至`PD-07`每个scenario/cut point的pass/fail与固定安全分类；
- 每connector的ordinary/sensitive payload、quarantine、purge、rotation、key invocation/inventory、backup sweep和confirmation计数；
- cross-tenant decrypt/HMAC/query/purge、stale-fence sensitive write、plaintext fallback、deadline extension、expired re-encryption、unregistered PBX surface和automatic key recreation均为`0`；
- public/application/PBX sink的forbidden canary hit为`0`，并分别给出结构allowlist与最终byte scan是否完成；
- online core/adapter/PBX controlled purge、key final destruction和backup expiry/crypto-erasure各自的证据状态，不用一个`deleted=true`合并；
- 报告生成器未读取sealed value、HMAC、ciphertext metadata、raw frame、native ID、provider path、credential、异常message或真实配置path。

报告不能包含完整或脱敏号码、末四位、HMAC/ciphertext/nonce/tag、KMS key reference、wrapped material、PBX endpoint/node/app、channel/Job UUID、CDR/CEL行、dial string、SIP header、raw frame、quarantine payload、credential、provider error、文件路径或异常message。build、profile、binding revision、key-provider class、retention schema、PBX module/sink inventory、backup策略或data-surface manifest变化后必须重跑；旧报告不能自动迁移。

## 13. 闭合与剩余门槛

B22-PD 闭合以下PBX敏感数据组合问题：

- 外呼权威快照到PBX invocation、呼入frame到core AEAD/HMAC的两条路径都没有ordinary明文恢复副本；
- core/adapter/PBX credential、加密、HMAC、digest purpose及A/B tenant key彼此分离，轮换/崩溃/销毁不跨域；
- ordinary quarantine固定allowlist，可选raw replay只进入独立加密、有界、有deadline且独立审计的payload store；
- policy shorten、absolute cap、gap/AMBIGUOUS、KMS outage、connector停用与backup restore共同执行时，敏感payload仍按deadline删除；
- application sink与PBX CDR/CEL/event/log/spool由同一canary oracle覆盖，rotation、后置regex和安全日志不成为泄漏旁路；
- 确认销毁按core、adapter、PBX controlled surface、key和backup分别取证，不把请求、scheduled deletion、tombstone或普通not-found夸大为全副本销毁。

仍未由本fixture闭合的项目包括：

- B22-PM的PBX stored recording晚到、stored-object证明、完整/单range、取消、大小/时间/并发上限、媒体对象删除确认及recording ref宽限；
- 真实Asterisk/FreeSWITCH/SIP trunk/运营商版本、PBX外部日志/APM/备份链、真实KMS/HSM和生产介质sanitization的私有B22-CANARY证据；
- 最终Java/HTTP/SQL类型、表、迁移、配置、KMS/provider选择、fixture harness与运维API实现；
- 任何司法辖区的法定计费记录、录音同意、法律保全、终端侧复制或运营商数据删除结论。

因此本次只把B22总门槛中的“PBX号码、密钥、retention、quarantine、泄漏与确认销毁组合设计”标为闭合。B22仍由B22-PM和真实provider canary阻断，不选择标准adapter，也不拆B22a/B22b。

## 14. 官方依据

- [Asterisk CEL Specification](https://docs.asterisk.org/Configuration/Reporting/Channel-Event-Logging-CEL/CEL-Specification/)：CEL可包含Caller ID、extension、application data、channel variable及event-specific数据，并可输出到多个backend；因此启用CEL不是安全allowlist或删除证明。
- [Asterisk CDR configuration](https://docs.asterisk.org/Asterisk_23_Documentation/API_Documentation/Module_Configuration/cdr/)：CDR可写入文件、数据库等backend，debug还会增加日志；PBX surface manifest必须枚举实际backend。
- [Asterisk Logging Configuration](https://docs.asterisk.org/Configuration/Core-Configuration/Logging-Configuration/)：日志可同时进入文件、console与syslog，rotation会重命名/压缩旧文件；它不等于按数据deadline确认删除。
- [FreeSWITCH Event Model](https://developer.signalwire.com/freeswitch/programming/event-model/)：channel事件携带Caller、Other-Leg和`variable_*` header，`BACKGROUND_JOB`还携带command、argument和text body；adapter必须在普通持久化前固定抽取而不是保存任意header map。
- [FreeSWITCH CDR](https://developer.signalwire.com/freeswitch/integration/cdr/)：CDR模板可以记录caller/destination，debug可把完整channel variable集合写入日志，rotation只切换文件。
- [FreeSWITCH core/log settings](https://developer.signalwire.com/freeswitch/configuration/core-settings/)：console、logfile、syslog可并行接收记录，`maximum-rotate=0`可形成无上限的日期文件；真实conformance不能只检查一个logger。
- [NIST SP 800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final)、[FIPS 198-1](https://csrc.nist.gov/pubs/fips/198-1/final)与[RFC 5869](https://www.rfc-editor.org/info/rfc5869/)分别支持B22-D冻结的AEAD、HMAC和purpose separation语义。
- [NIST SP 800-57 Part 1 Rev. 5](https://csrc.nist.gov/pubs/sp/800/57/pt1/r5/final)要求治理key生命周期；scheduled deletion与最终destroy证据必须区分。
- [NIST SP 800-88 Rev. 2](https://csrc.nist.gov/pubs/sp/800/88/r2/final)把media sanitization定义为使目标数据在给定努力水平下不可访问的治理过程；应用层row/file删除不自动证明底层介质已sanitized。

本文件是技术安全与conformance边界，不是法律意见。真实部署仍需分别治理PBX、SIP proxy/trunk、运营商、集中日志、APM、备份、终端和介质生命周期。
