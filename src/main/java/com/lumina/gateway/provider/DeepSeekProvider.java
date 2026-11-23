package com.lumina.gateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DeepSeek 适配器（OpenAI 兼容协议）。
 */
public class DeepSeekProvider extends OpenAiCompatibleProvider {

    public DeepSeekProvider(String baseUrl, String apiKey, ObjectMapper objectMapper) {
        super("deepseek", baseUrl, apiKey, objectMapper);
    }
}
