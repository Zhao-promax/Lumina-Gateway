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
| Prometheus 指标 | 有 gateway_* | ✅ P50/P95/P99、requests、errors 均暴露 |

## 三、并发压测（Mock 厂商，`scripts/loadtest.py`）

### 非流式：500 请求 / 50 并发

| 指标 | 值 |
|---|---|
| 成功率 | 500 / 500（0 错误） |
| P50 延迟 | 57.6 ms |
| P95 延迟 | 75.2 ms |
| P99 延迟 | 77.5 ms |
| 平均延迟 | 56.2 ms |

### 流式：100 SSE 长连接 / 20 并发

| 指标 | 值 |
|---|---|
| 成功率 | 100 / 100（0 错误） |
| 平均流时长 | 1011 ms（受 Mock 50 chunk × 20ms 节奏主导） |
| 平均分片数 | 51 帧（50 chunk + 1 `[DONE]`） |

### 压测结论

- 网关异步非阻塞链路在 50 并发下零错误，非流式 P99 < 80ms，开销稳定；
- SSE 长连接可承载 20+ 并发流式链路，分片与 `[DONE]` 结束符完整；
- 压测中曾出现 `nonce_replayed` 401，系压测脚本 `time.time_ns()` 在 Windows 下分辨率不足产生重复 nonce，改用 `uuid4` 后消除——反向验证了**防重放机制有效**。

## 四、已知限制（未覆盖）

- Redis 限流/预算、RabbitMQ 审计、Nacos 热更、SkyWalking 追踪、语义缓存依赖外部中间件，本报告未做联调验证（代码已就绪，见部署配置）。
