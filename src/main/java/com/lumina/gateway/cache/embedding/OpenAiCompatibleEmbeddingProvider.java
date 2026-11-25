package com.lumina.gateway.cache.embedding;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embedding 实现（调用 /embeddings 接口）。
 * <p>
 * 可对接任意提供 OpenAI 兼容 embedding 接口的服务（含 OpenAI / 本地 embedding 服务等）。
 * 配置见 gateway.cache.embedding.*。
 */
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    private final WebClient webClient;
    private final String model;
    private final String apiKey;
    private final int dimension;

    public OpenAiCompatibleEmbeddingProvider(String baseUrl, String apiKey, String model, int dimension) {
        this.webClient = WebClient.create(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<float[]> embed(String text) {
        return webClient.post()
                .uri("/embeddings")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", model, "input", text))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .map(resp -> {
                    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
                    List<Number> embedding = (List<Number>) data.get(0).get("embedding");
                    float[] result = new float[embedding.size()];
                    for (int i = 0; i < result.length; i++) {
                        result[i] = embedding.get(i).floatValue();
                    }
                    return result;
                });
    }
}
