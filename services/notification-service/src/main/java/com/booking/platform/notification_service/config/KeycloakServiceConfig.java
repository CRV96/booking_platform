package com.booking.platform.notification_service.config;

import com.booking.platform.notification_service.properties.KeycloakServiceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(KeycloakServiceProperties.class)
public class KeycloakServiceConfig {

    @Bean
    public RestClient keycloakRestClient() {
        return RestClient.create();
    }
}
