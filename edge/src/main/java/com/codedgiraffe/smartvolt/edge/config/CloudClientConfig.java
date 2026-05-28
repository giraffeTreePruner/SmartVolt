package com.codedgiraffe.smartvolt.edge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CloudClientConfig {

    @Bean
    public RestClient cloudRestClient(
            @Value("${smartvolt.cloud.url}") String cloudUrl,
            @Value("${smartvolt.cloud.api-key}") String apiKey) {
        return RestClient.builder()
                .baseUrl(cloudUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }
}
