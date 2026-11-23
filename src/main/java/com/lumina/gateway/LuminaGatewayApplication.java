package com.lumina.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LuminaGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LuminaGatewayApplication.class, args);
    }
}
