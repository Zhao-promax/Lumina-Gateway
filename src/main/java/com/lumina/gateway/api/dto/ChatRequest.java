package com.lumina.gateway.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入参请求（OpenAI 兼容）。
 * <p>
 * model / messages / stream / temperature 为显式字段；其余厂商私有参数（max_tokens、top_p、stop 等）
 * 通过 {@link #putExtra} 透传，保证对 OpenAI 协议参数的前向兼容。
 */
public class ChatRequest {

    private String model;

    @NotEmpty(message = "messages must not be empty")
    private List<@Valid ChatMessage> messages;

    private Double temperature;

    private Boolean stream;

    private final Map<String, Object> extra = new LinkedHashMap<>();

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Boolean getStream() {
        return stream;
    }

    public void setStream(Boolean stream) {
        this.stream = stream;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    @JsonAnySetter
    public void putExtra(String key, Object value) {
        this.extra.put(key, value);
    }
}
