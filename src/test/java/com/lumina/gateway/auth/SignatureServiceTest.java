package com.lumina.gateway.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureServiceTest {

    private final SignatureService service = new SignatureService();

    @Test
    void signIsDeterministicAndVerifiable() {
        String sig1 = service.sign("POST", "/v1/chat/completions", "1700000000", "abc123", "secret");
        String sig2 = service.sign("POST", "/v1/chat/completions", "1700000000", "abc123", "secret");

        assertThat(sig1).isEqualTo(sig2);
        assertThat(service.verify(sig1, sig2)).isTrue();
    }

    @Test
    void verifyRejectsMismatch() {
        String sig = service.sign("POST", "/v1/chat/completions", "1700000000", "abc123", "secret");
        assertThat(service.verify(sig, "deadbeef")).isFalse();
    }

    @Test
    void differentInputsProduceDifferentSignatures() {
        String sig1 = service.sign("POST", "/v1/chat/completions", "1700000000", "abc123", "secret");
        String sig2 = service.sign("POST", "/v1/chat/completions", "1700000000", "abc124", "secret");
        assertThat(sig1).isNotEqualTo(sig2);
    }
}
