package com.lumina.gateway.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;

/**
 * RediSearch 向量存储（需 Redis Stack，含 RediSearch 模块）。
 * <p>
 * 使用 FT.CREATE 建 HNSW 向量索引，FT.SEARCH 做 KNN 检索。
 * 检索为阻塞调用，offload 到 boundedElastic 线程，避免阻塞 Netty 事件循环。
 * 注意：需 Redis Stack（redis/redis-stack-server 镜像）才能运行。
 */
public class RedisSearchVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSearchVectorStore.class);

    private final StringRedisTemplate redis;
    private final String index;
    private final int dimension;
    private final String keyPrefix;

    public RedisSearchVectorStore(StringRedisTemplate redis, String index, int dimension, String keyPrefix) {
        this.redis = redis;
        this.index = index;
        this.dimension = dimension;
        this.keyPrefix = keyPrefix;
        createIndex();
    }

    private void createIndex() {
        // FT.CREATE idx ON HASH PREFIX 1 "sem:" SCHEMA vector VECTOR HNSW 6 TYPE FLOAT32 DIM 1024 DISTANCE_METRIC COSINE
        String command = "FT.CREATE";
        byte[][] args = new byte[][]{
                index.getBytes(),
                "ON".getBytes(), "HASH".getBytes(),
                "PREFIX".getBytes(), "1".getBytes(), keyPrefix.getBytes(),
                "SCHEMA".getBytes(),
                "vector".getBytes(), "VECTOR".getBytes(), "HNSW".getBytes(), "6".getBytes(),
                "TYPE".getBytes(), "FLOAT32".getBytes(),
                "DIM".getBytes(), String.valueOf(dimension).getBytes(),
                "DISTANCE_METRIC".getBytes(), "COSINE".getBytes()
        };
        try {
            redis.execute((RedisCallback<Object>) connection -> connection.execute(command, args));
            log.info("redisearch index {} created", index);
        } catch (Exception e) {
            // 索引已存在等错误可忽略
            log.debug("redisearch index {} create: {}", index, e.getMessage());
        }
    }

    @Override
    public Mono<Void> put(String key, float[] vector, String value) {
        return Mono.fromCallable(() -> {
            redis.opsForHash().put(key, "vector", floatsToBytes(vector));
            redis.opsForHash().put(key, "value", value);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Optional<String>> find(String model, float[] queryVector, double threshold) {
        return Mono.fromCallable(() -> {
            // FT.SEARCH idx "*=>[KNN 1 @vector $vec AS score]" PARAMS 2 vec <binary> DIALECT 2 RETURN 1 value
            String query = "*=>[KNN 1 @vector $vec AS score]";
            String command = "FT.SEARCH";
            byte[][] args = new byte[][]{
                    index.getBytes(),
                    query.getBytes(),
                    "PARAMS".getBytes(), "2".getBytes(), "vec".getBytes(), floatsToBytes(queryVector),
                    "DIALECT".getBytes(), "2".getBytes(),
                    "RETURN".getBytes(), "1".getBytes(), "value".getBytes()
            };
            Object raw = redis.execute((RedisCallback<Object>) connection -> connection.execute(command, args));
            List<Object> result = (raw instanceof List<?>) ? (List<Object>) raw : List.of();
            return parseSearchResult(result, threshold);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * FT.SEARCH 返回扁平数组：[count, key1, [field, value, ...], key2, [...], ...]
     * 此处取第一个结果的 value 字段；score 通过 AS score 附加，需在结果中识别。
     */
    private Optional<String> parseSearchResult(List<Object> result, double threshold) {
        if (result == null || result.size() < 3) {
            return Optional.empty();
        }
        Object first = result.get(0);
        long count = first instanceof Number n ? n.longValue() : Long.parseLong(first.toString());
        if (count == 0) {
            return Optional.empty();
        }
        // 简化解析：结果[1] 为 key，结果[2] 为 [field, value, ...] 或 [value, json]
        try {
            Object fieldsObj = result.get(2);
            if (fieldsObj instanceof List<?> fields) {
                for (int i = 0; i + 1 < fields.size(); i += 2) {
                    if ("value".equals(String.valueOf(fields.get(i)))) {
                        return Optional.of(String.valueOf(fields.get(i + 1)));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("redisearch result parse failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private byte[] floatsToBytes(float[] floats) {
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }
}
