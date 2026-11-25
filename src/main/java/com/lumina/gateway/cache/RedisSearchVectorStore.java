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
 * 索引在首次写入时按实际向量维度创建（无需预知 embedding 维度）。
 * 检索为阻塞调用，offload 到 boundedElastic 线程，避免阻塞 Netty 事件循环。
 */
public class RedisSearchVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSearchVectorStore.class);

    private final StringRedisTemplate redis;
    private final String index;
    private final String keyPrefix;

    private volatile boolean indexCreated = false;

    public RedisSearchVectorStore(StringRedisTemplate redis, String index, String keyPrefix) {
        this.redis = redis;
        this.index = index;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public Mono<Void> put(String key, float[] vector, String value) {
        return Mono.fromCallable(() -> {
            ensureIndex(vector.length);
            redis.opsForHash().put(key, "vector", floatsToBytes(vector));
            redis.opsForHash().put(key, "value", value);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Optional<String>> find(String model, float[] queryVector, double threshold) {
        return Mono.fromCallable(() -> {
            if (!indexCreated) {
                return Optional.<String>empty();
            }
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

    private void ensureIndex(int dimension) {
        if (indexCreated) {
            return;
        }
        synchronized (this) {
            if (indexCreated) {
                return;
            }
            createIndex(dimension);
            indexCreated = true;
        }
    }

    private void createIndex(int dimension) {
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
            log.info("redisearch index {} created with dim {}", index, dimension);
        } catch (Exception e) {
            // 索引已存在等错误可忽略
            log.debug("redisearch index {} create: {}", index, e.getMessage());
        }
    }

    /**
     * FT.SEARCH 返回扁平数组：[count, key1, [field, value, ...], key2, [...], ...]
     * 此处取第一个结果的 value 字段。
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
