# Lumina Gateway 测试报告

> 测试日期：2026-11-24 · 环境：Windows 11 / JDK 17 / 单实例 · 厂商：Mock（压测）/ DeepSeek+通义（功能，无真实 key）

## 一、单元测试

**30 / 30 通过**（`mvn test`），覆盖：

| 模块 | 用例数 | 覆盖点 |
|---|---|---|
| auth / SignatureService | 3 | HMAC 签名确定性、校验、防篡改 |
| routing / RouteResolver | 3 | 主备顺序、降级按价格排序、未知标签报错 |
| resilience / RetryablePredicate | 6 | 仅 5xx/连接异常重试，4xx/429/401 不重试 |
| ratelimit / InMemoryRateLimiter | 2 | 令牌桶容量、突发拒绝、多维度隔离 |
| budget / InMemoryBudgetService | 2 | 超日限额拒绝、结算退款 |
| cache / ExactCacheService | 3 | 仅 temperature=0 命中、存取、键稳定性 |
| moderation / ContentModeration | 3 | 敏感词命中/放行/开关关闭 |
| security / SecretCipher | 3 | AES-GCM 加解密往返、密钥不匹配失败、轮换重加密 |
| usage / TokenEstimator | 2 | 默认/指定 max_tokens 估算 |
| service / ChatService | 2 | 主备故障转移、流式首字节前故障转移 |

## 二、端到端冒烟测试

| 场景 | 预期 | 实测 |
|---|---|---|
| 健康检查 | UP | ✅ `{"status":"UP"}` |
| 无鉴权头 | 401 | ✅ 401 |
| 错误签名 | 401 | ✅ 401 |
| 预算超限（日限 100，估算 519） | 402 | ✅ 402 `insufficient_quota` |
| 含敏感词 | 400 | ✅ 400 `content_policy_violation` |
| 正常内容（无 api-key） | 502 | ✅ 502（故障转移耗尽） |
| 连发 15 次（限流 10/s） | 出现 429 | ✅ 前 11 次 503、后 4 次 429 |
| 客户端中途断连 | 下游请求取消 | ✅ `gateway_disconnects=1` + 下游 provider `doOnCancel` 日志 |
| Prometheus 指标 | 有 gateway_* | ✅ P50/P95/P99、requests、errors、circuitbreaker.opens、budget.warnings 均暴露 |

## 三、并发压测（Mock 厂商，`scripts/loadtest.py`，keep-alive 连接复用）

> 说明：压测脚本每个 worker 复用同一条 HTTP 连接（keep-alive）。此前用 urllib 每请求新建连接，
> 混入 TCP 握手 + Nagle/延迟 ACK 约 50ms 开销，导致测得的 56ms 远高于网关真实开销。

### 网关真实单请求开销（keep-alive 顺序）

| 指标 | 值 |
|---|---|
| 平均 | 3.8 ms |
| P50 | 3.7 ms |
| P99 | 7.5 ms |

### 并发非流式：500 请求 / 50 并发（keep-alive）

| 指标 | 值 |
|---|---|
| 成功率 | 500 / 500（0 错误） |
| 吞吐 | ~2200 req/s |
| 平均延迟 | ~10 ms |
| P50 | ~5 ms |
| P95 | ~60–80 ms |
| P99 | ~80–170 ms |

### 并发流式：100 SSE / 20 并发

| 指标 | 值 |
|---|---|
| 成功率 | 100 / 100（0 错误） |
| 平均流时长 | ~1011 ms（受 Mock 50 chunk × 20ms 节奏主导） |
| 平均分片数 | 51 帧（50 chunk + 1 `[DONE]`） |

### 压测结论与优化

- 网关异步非阻塞链路在 50 并发下零错误，**真实单请求开销约 5ms（P50）**，对秒级 LLM 调用可忽略；
- SSE 长连接可承载 20+ 并发流式链路，分片与 `[DONE]` 结束符完整；
- 尾延迟主要来自 JVM GC 与 Resilience4j 超时调度，后续可做 GC 调优进一步压降；
- 审计日志已改为**异步独立线程池**（有界队列 + 溢出丢弃），不阻塞响应主链路，吞吐提升约 14%（1931→2200 req/s）；
- 压测脚本支持采集 JVM 线程数/堆内存（`jvm.threads.live` / `jvm.memory.used`），压测前后输出快照，用于观察资源是否稳定、有无内存泄漏；
- 压测中曾现 `nonce_replayed` 401，系脚本 `time.time_ns()` 在 Windows 分辨率不足产生重复 nonce，改 `uuid4` 后消除——反向验证防重放有效。

## 四、已知限制（未覆盖）

- Redis 限流/预算、RabbitMQ 审计、Nacos 热更、SkyWalking 追踪、语义缓存依赖外部中间件，本报告未做联调验证（代码已就绪，见部署配置）。
