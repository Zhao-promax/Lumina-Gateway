package com.lumina.gateway.moderation;

import com.lumina.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContentModerationServiceTest {

    @Test
    void detectsProhibitedWord() {
        GatewayProperties props = new GatewayProperties();
        props.getModeration().setEnabled(true);
        props.getModeration().setWords(List.of("暴力", "色情"));
        ContentModerationService service = new ContentModerationService(props);

        assertThat(service.detect("这是一段包含暴力的内容")).isPresent();
    }

    @Test
    void allowsNormalContent() {
        GatewayProperties props = new GatewayProperties();
        props.getModeration().setEnabled(true);
        props.getModeration().setWords(List.of("暴力"));
        ContentModerationService service = new ContentModerationService(props);

        assertThat(service.detect("你好，请介绍一下你自己")).isEmpty();
    }

    @Test
    void disabledReturnsEmpty() {
        GatewayProperties props = new GatewayProperties();
        props.getModeration().setEnabled(false);
        props.getModeration().setWords(List.of("暴力"));
        ContentModerationService service = new ContentModerationService(props);

        assertThat(service.detect("暴力内容")).isEmpty();
    }
}
