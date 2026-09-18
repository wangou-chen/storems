package com.storems.gatewayservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AuthClientConfig {
    @Bean
    public WebClient.Builder authWebClientBuilder(LoadBalancerClient loadBalancerClient) {
        // Finchley customizes @LoadBalanced builders after singleton creation.
        // Install the filter before AuthFilter builds its WebClient.
        return WebClient.builder().filter(
                new LoadBalancerExchangeFilterFunction(loadBalancerClient));
    }
}
