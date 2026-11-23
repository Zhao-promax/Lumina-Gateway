package com.lumina.gateway.routing;

import com.lumina.gateway.config.GatewayProperties;
import com.lumina.gateway.exception.GatewayException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteResolverTest {

    private GatewayProperties propertiesWith(boolean downgradeEnabled) {
        GatewayProperties props = new GatewayProperties();
        GatewayProperties.Routing routing = new GatewayProperties.Routing();
        routing.setDefaultTag("default");
        routing.setDowngradeEnabled(downgradeEnabled);

        GatewayProperties.Route route = new GatewayProperties.Route();
        route.setTag("default");
        route.setModels(List.of(
                modelRef("deepseek", "deepseek-chat", 0.002),
                modelRef("qwen", "qwen-plus", 0.0008)));
        routing.setRoutes(List.of(route));
        props.setRouting(routing);
        return props;
    }

    @Test
    void keepsPrimaryOrderWhenDowngradeDisabled() {
        RouteResolver resolver = new RouteResolver(propertiesWith(false));
        List<RouteTarget> candidates = resolver.resolveCandidates(null);
        assertThat(candidates).extracting(RouteTarget::provider).containsExactly("deepseek", "qwen");
    }

    @Test
    void reordersByPriceWhenDowngradeEnabled() {
        RouteResolver resolver = new RouteResolver(propertiesWith(true));
        List<RouteTarget> candidates = resolver.resolveCandidates(null);
        assertThat(candidates).extracting(RouteTarget::provider).containsExactly("qwen", "deepseek");
    }

    @Test
    void throwsOnUnknownTag() {
        RouteResolver resolver = new RouteResolver(propertiesWith(false));
        assertThatThrownBy(() -> resolver.resolveCandidates("unknown"))
                .isInstanceOf(GatewayException.class);
    }

    private GatewayProperties.ModelRef modelRef(String provider, String model, double price) {
        GatewayProperties.ModelRef ref = new GatewayProperties.ModelRef();
        ref.setProvider(provider);
        ref.setModel(model);
        ref.setPrice(price);
        return ref;
    }
}
