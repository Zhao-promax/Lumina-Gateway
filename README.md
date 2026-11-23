# Lumina Gateway

面向业务系统的异构大模型统一接入网关（OpenAI 兼容）。

> 配套文档：[plan.md](plan.md)（计划书）、[architecture.md](architecture.md)（架构设计书）。

## 技术栈

Java 17 · Spring Boot 3.2 · Spring WebFlux（Reactor）· Maven

## 当前进度（阶段 1：底座 MVP）

- OpenAI 兼容 `/v1/chat/completions`（支持 stream=true/false）
- SPI 厂商适配器（DeepSeek、通义千问 compatible-mode）
- SSE 流式透传 + 客户端断连自动取消下游请求
- 基础标签路由（`X-Route-Tag` 请求头）
- AppKey HMAC-SHA256 签名鉴权

## 构建与运行

```bash
export JAVA_HOME=/c/Program\ Files/Java/jdk-17
mvn -DskipTests package
java -jar target/lumina-gateway-0.1.0.jar
```

启动前配置厂商密钥（环境变量，可选，未配置时对应厂商返回 503）：

```bash
export DEEPSEEK_API_KEY=sk-xxxx
export QWEN_API_KEY=sk-xxxx
```

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

在请求体中加入 `"stream": true`，并通过 `X-Route-Tag` 选择路由标签（缺省 `default`）。

## 已知边界（阶段 1）

- 厂商密钥与 AppKey 暂存于配置，未加密（阶段 4 引入加密存储与轮换）
- 流式请求若在下游返回前出错，暂以终止连接处理，未发送错误帧（阶段 2 完善）
- 鉴权未绑定请求体（阶段 2+ 可选增强）
