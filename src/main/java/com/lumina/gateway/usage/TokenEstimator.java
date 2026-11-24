package com.lumina.gateway.usage;

import com.lumina.gateway.api.dto.ChatMessage;
import com.lumina.gateway.api.dto.ChatRequest;

/**
 * Token 用量估算（预算预扣用，结算时以厂商返回 usage 为准多退少补）。
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    /**
     * 估算本次请求的 Token 总量：提示词 Token + 完成 Token。
     * 提示词按字符数/4 粗略估算；完成按 max_tokens，未指定用默认值。
     */
    public static long estimate(ChatRequest request, long defaultCompletionTokens) {
        long prompt = 0;
        if (request.getMessages() != null) {
            for (ChatMessage message : request.getMessages()) {
                if (message.role() != null) {
                    prompt += 4;
                }
                if (message.content() != null) {
                    prompt += message.content().length() / 4 + 1;
                }
            }
        }

        long completion = defaultCompletionTokens;
        Object maxTokens = request.getExtra().get("max_tokens");
        if (maxTokens instanceof Number number) {
            completion = number.longValue();
        }
        return prompt + completion;
    }
}
