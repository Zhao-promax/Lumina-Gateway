package com.lumina.gateway.moderation;

import com.lumina.gateway.config.GatewayProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 前置内容风控：在调用厂商前对用户输入做敏感词过滤。
 * <p>
 * 命中则拒绝请求（content_policy_violation），避免敏感内容下发到模型厂商。
 */
@Component
public class ContentModerationService {

    private final GatewayProperties properties;
    private volatile List<Pattern> patterns = new ArrayList<>();

    public ContentModerationService(GatewayProperties properties) {
        this.properties = properties;
        refresh();
    }

    public void refresh() {
        this.patterns = properties.getModeration().getWords().stream()
                .map(w -> Pattern.compile(Pattern.quote(w), Pattern.CASE_INSENSITIVE))
                .toList();
    }

    /**
     * 检测内容，命中返回提示，否则 empty。
     */
    public Optional<String> detect(String content) {
        if (!properties.getModeration().isEnabled() || content == null) {
            return Optional.empty();
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(content).find()) {
                return Optional.of("content contains prohibited terms");
            }
        }
        return Optional.empty();
    }
}
