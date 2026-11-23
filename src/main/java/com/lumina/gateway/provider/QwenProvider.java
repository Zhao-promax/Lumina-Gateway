package com.lumina.gateway.provider;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 通义千问适配器（DashScope compatible-mode，OpenAI 兼容协议）。
 */
public class QwenProvider extends OpenAiCompatibleProvider {

    public QwenProvider(String baseUrl, String apiKey, ObjectMapper objectMapper) {
        super("qwen", baseUrl, apiKey, objectMapper);
    }
}
