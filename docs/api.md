# Lumina Gateway 接口文档

## 1. 概述

网关对外暴露 OpenAI 兼容接口，业务侧可沿用 OpenAI SDK 直连。基地址 `http://<host>:8080`。

## 2. 鉴权（AppKey 签名）

所有 `/v1/**` 请求需携带以下请求头：

| 请求头 | 必填 | 说明 |
|---|---|---|
| `X-App-Key` | 是 | 应用 Key |
| `X-Timestamp` | 是 | Unix 秒级时间戳（±5 分钟内） |
| `X-Nonce` | 是 | 随机字符串，防重放 |
| `X-Signature` | 是 | HMAC-SHA256 签名（小写十六进制） |

**签名算法**：

```
signature = HMAC_SHA256(appSecret, METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + NONCE)
```

Python 示例：

```python
import hmac, hashlib, time, uuid
ts = str(int(time.time())); nonce = uuid.uuid4().hex
sig = hmac.new(secret.encode(),
    f"POST\n/v1/chat/completions\n{ts}\n{nonce}".encode(), hashlib.sha256).hexdigest()
```

## 3. 对话补全接口

`POST /v1/chat/completions`

### 请求体

```json
{
  "model": "deepseek-chat",
  "messages": [{"role": "user", "content": "你好"}],
  "temperature": 0,
  "max_tokens": 100,
  "stream": false
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| model | string | 逻辑模型名（可省略，由路由标签决定） |
| messages | array | role ∈ {system, user, assistant} |
| temperature | number | 0–2；**精确缓存仅在 temperature=0 时命中** |
| max_tokens | number | 完成 Token 上限 |
| stream | boolean | true 时返回 SSE |

可选请求头 `X-Route-Tag` 指定路由标签（缺省 `default`），用于主备/降级/多模型路由。

### 非流式响应

```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "created": 1700000000,
  "model": "deepseek-chat",
  "choices": [{"index": 0, "message": {"role": "assistant", "content": "..."}, "finish_reason": "stop"}],
  "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
}
```

### 流式响应（SSE）

```
data: {"id":"...","object":"chat.completion.chunk","model":"...","choices":[{"index":0,"delta":{"role":"assistant","content":"..."},"finish_reason":null}]}

data: [DONE]
```

## 4. 错误码

| HTTP | type | 说明 |
|---|---|---|
| 400 | invalid_request_error | 参数校验失败 |
| 400 | content_policy_violation | 命中内容风控 |
| 400 | no_route / provider_not_found | 路由/厂商不存在 |
| 401 | invalid_signature 等 | 鉴权失败（签名/时间戳/nonce） |
| 402 | insufficient_quota | 预算配额耗尽 |
| 429 | rate_limit_error | 限流 |
| 502 | upstream_error | 全部候选模型失败 |
| 503 | provider_not_configured | 厂商未配置密钥 |

错误响应体：

```json
{"error": {"message": "...", "type": "invalid_request_error", "param": null, "code": null}}
```

## 5. 可观测端点

| 端点 | 说明 |
|---|---|
| `/actuator/health` | 健康检查（liveness/readiness） |
| `/actuator/prometheus` | Prometheus 指标（请求量、分位延迟、Token、缓存、错误） |
