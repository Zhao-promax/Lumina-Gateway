# Lumina Gateway

面向业务系统的异构大模型统一接入网关（OpenAI 兼容）。

> 配套文档：[plan.md](plan.md)（计划书）、[architecture.md](architecture.md)（架构设计书）。

## 技术栈

Java 17 · Spring Boot 3.2 · Spring WebFlux（Reactor）· Resilience4j · Redis（可选）· Nacos（可选）· Maven

## 当前进度

- **阶段 1（底座 MVP）**：OpenAI 兼容接口、SPI 适配器、SSE 流式透传、断连取消、标签路由、AppKey 签名鉴权
- **阶段 2（可靠性治理）**：Resilience4j 熔断/超时/重试、主备故障转移、高价→廉价降级、多维限流、Nacos 动态热更（可选）
- **阶段 3（成本管控）**：Token 用量统计、Redis 原子预算配额（预扣+结算）、超额拦截（402）、精确 Prompt 哈希缓存、Embedding 语义缓存（可选）

## 构建与运行

```bash
export JAVA_HOME=/c/Program\ Files/Java/jdk-17
mvn -DskipTests package
java -jar target/lumina-gateway-0.1.0.jar
```

启动前配置厂商密钥（可选，未配置时对应厂商返回 503）：

```bash
export DEEPSEEK_API_KEY=sk-xxxx
export QWEN_API_KEY=sk-xxxx
```

## 核心配置（application.yml）

| 配置项 | 说明 |
|---|---|
| `gateway.routing.routes[].models` | 有序候选模型列表：primary 在前、backup 在后，`price` 用于降级排序 |
| `gateway.routing.downgrade-enabled` | 降级开关，开启后按价格升序（廉价优先） |
| `gateway.resilience.*` | 熔断（失败率阈值/滑动窗口/最小调用数）、超时、重试 |
| `gateway.rate-limit.backend` | `memory`（默认，单实例）或 `redis`（分布式） |
| `gateway.rate-limit.permits-per-second` | 每租户/App/模型维度的令牌速率 |
| `gateway.budget.*` | 预算配额（日/总限额、后端、默认完成 Token 估算），超额返回 402 |
| `gateway.cache.exact-*` | 精确 Prompt 哈希缓存（仅 temperature=0 命中） |
| `gateway.cache.semantic-*` | Embedding 语义缓存（需提供 EmbeddingProvider Bean） |
| `gateway.nacos.enabled` | 启用后路由规则从 Nacos 热更（默认 false） |

## 调用方式

### 签名鉴权

所有 `/v1/**` 请求需携带以下请求头：

| 请求头 | 说明 |
|---|---|
| `X-App-Key` | AppKey（见 application.yml `gateway.auth.apps`） |
| `X-Timestamp` | Unix 秒级时间戳 |
| `X-Nonce` | 随机字符串（防重放） |
| `X-Signature` | HMAC-SHA256 签名（十六进制） |

签名串（小写十六进制）：

```
signature = HMAC_SHA256(appSecret, METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE)
```

### 非流式

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "X-App-Key: demo-app" \
  -H "X-Timestamp: $TS" -H "X-Nonce: $NONCE" -H "X-Signature: $SIG" \
  -d '{"model":"deepseek-chat","messages":[{"role":"user","content":"你好"}]}'
```

### 流式

请求体加入 `"stream": true`，通过 `X-Route-Tag` 选择路由标签（缺省 `default`）。

## 可靠性行为

- **重试**：仅对连接异常与上游 5xx 重试，4xx/429/401 不重试
- **熔断**：按 `provider:model` 维度，失败率达到阈值后打开，进入半开探测
- **主备故障转移**：primary 熔断/失败时自动切换到下一个候选模型
- **流式故障转移**：仅当「首字节产出前」失败才切换，已产出则保持原流
- **降级**：开启 `downgrade-enabled` 后按价格升序选择候选

## 已知边界

- 厂商密钥与 AppKey 暂存于配置，未加密（阶段 4）
- Nacos 热更需部署 Nacos Server（`gateway.nacos.enabled=true`）
- 限流使用令牌桶；Redis 后端异常时「失败放行」
