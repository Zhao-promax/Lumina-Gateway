# Lumina Gateway

面向业务系统的异构大模型统一接入网关（OpenAI 兼容）。

> 配套文档：[plan.md](plan.md)（计划书）、[architecture.md](architecture.md)（架构设计书）、[docs/api.md](docs/api.md)（接口文档）、[docs/test-report.md](docs/test-report.md)（测试报告）。

## 技术栈

Java 17 · Spring Boot 3.2 · Spring WebFlux（Reactor）· Resilience4j · Redis（可选）· Nacos（可选）· Maven

## 当前进度

- **阶段 1（底座 MVP）**：OpenAI 兼容接口、SPI 适配器、SSE 流式透传、断连取消、标签路由、AppKey 签名鉴权
- **阶段 2（可靠性治理）**：Resilience4j 熔断/超时/重试、主备故障转移、高价→廉价降级、多维限流、Nacos 动态热更（可选）
- **阶段 3（成本管控）**：Token 用量统计、Redis 原子预算配额（预扣+结算）、超额拦截（402）、精确 Prompt 哈希缓存、Embedding 语义缓存（可选）
- **阶段 4（可观测与安全审计）**：Micrometer 指标（Prometheus）、异步审计（RabbitMQ 可选）、密钥加密与轮换、内容风控、Docker/K8s 交付

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
| `gateway.moderation.*` | 前置内容风控（敏感词过滤，命中返回 400） |
| `gateway.audit.mq-enabled` | 异步审计投递 RabbitMQ（默认 false，仅结构化日志） |
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

## 可观测性

- **指标**：`/actuator/prometheus`（Prometheus 抓取），含请求量、P50/P95/P99 延迟、Token、缓存命中、断连取消、错误率
- **审计**：结构化日志（logger 名 `AUDIT`）+ 可选 RabbitMQ 异步投递（`gateway.audit.mq-enabled=true`）
- **告警**：见 `deploy/prometheus-alerts.yml`
- **链路追踪**：SkyWalking 通过 `-javaagent` 注入（见 Dockerfile 注释）；WebFlux 场景可优先 OTel/Micrometer Tracing

## 部署

- `Dockerfile`：多阶段构建
- `deploy/k8s.yaml`：Deployment + Service + HPA（含健康/就绪探针、密钥注入）
- `deploy/application-prod.yml`：生产配置（Redis/RabbitMQ/Nacos 后端）
- `deploy/schema.sql`：审计日志表结构

## 已知边界

- 配置中的厂商密钥/AppSecret 仍为明文；已提供 `SecretCipher`（AES-GCM）与 `KeyRotationService`，生产建议接 KMS 或将密文写入配置
- Nacos 热更需部署 Nacos Server（`gateway.nacos.enabled=true`）
- RabbitMQ 审计需部署 RabbitMQ（`gateway.audit.mq-enabled=true`）
- 限流使用令牌桶；Redis 后端异常时「失败放行」
