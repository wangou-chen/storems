package com.storems.gatewayservice.config;

import org.junit.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class AuthClientConfigTest {
    @Test
    public void immediatelyBuiltClientResolvesServiceNameAndPreservesAuthorization() {
        AtomicInteger choices = new AtomicInteger();
        ServiceInstance instance = (ServiceInstance) Proxy.newProxyInstance(
                ServiceInstance.class.getClassLoader(),
                new Class<?>[]{ServiceInstance.class}, (proxy, method, args) -> null);
        LoadBalancerClient balancer = (LoadBalancerClient) Proxy.newProxyInstance(
                LoadBalancerClient.class.getClassLoader(),
                new Class<?>[]{LoadBalancerClient.class}, (proxy, method, args) -> {
                    if ("choose".equals(method.getName())) {
                        assertEquals("user-service", args[0]);
                        choices.incrementAndGet();
                        return instance;
                    }
                    if ("reconstructURI".equals(method.getName())) {
                        URI original = (URI) args[1];
                        return URI.create("http://127.0.0.1:8030" + original.getRawPath());
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        // Reproduce construction before SmartInitializingSingleton customization.
        WebClient client = new AuthClientConfig().authWebClientBuilder(balancer)
                .exchangeFunction(request -> {
                    assertEquals("http://127.0.0.1:8030/internal/auth/validate",
                            request.url().toString());
                    assertEquals("Bearer test-token", request.headers().getFirst("Authorization"));
                    return Mono.just(ClientResponse.create(HttpStatus.OK).build());
                }).build();

        ClientResponse result = client.get()
                .uri("http://user-service/internal/auth/validate")
                .header("Authorization", "Bearer test-token")
                .exchange().block(Duration.ofSeconds(2));
        assertEquals(HttpStatus.OK, result.statusCode());
        assertEquals(1, choices.get());
    }
}
