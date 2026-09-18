package com.storems.gatewayservice.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Component
public class AuthFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    private final WebClient authClient;

    public AuthFilter(WebClient.Builder authWebClientBuilder) {
        this.authClient = authWebClientBuilder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && ("/user/register".equals(path) || "/user/login".equals(path))) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null
                || !authorization.matches("(?i:Bearer) [A-Za-z0-9_-]{43}")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }

        Mono<HttpStatus> validation = authClient.get()
                .uri("http://user-service/internal/auth/validate")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange()
                .flatMap(response -> {
                    HttpStatus status = response.statusCode();
                    if (status != HttpStatus.OK && status != HttpStatus.UNAUTHORIZED) {
                        log.warn("Token validation service returned HTTP {}", status.value());
                    }
                    return response.bodyToMono(String.class).defaultIfEmpty("")
                            .map(body -> status == HttpStatus.OK
                                    ? HttpStatus.OK
                                    : status == HttpStatus.UNAUTHORIZED
                                        ? HttpStatus.UNAUTHORIZED
                                        : HttpStatus.SERVICE_UNAVAILABLE);
                })
                .timeout(Duration.ofSeconds(3))
                .doOnError(error -> log.warn("Token validation call failed ({})",
                        error.getClass().getSimpleName()))
                .onErrorReturn(HttpStatus.SERVICE_UNAVAILABLE);

        // Handle only authentication failures above, preserving downstream errors.
        return validation.flatMap(status -> status == HttpStatus.OK
                ? chain.filter(exchange)
                : reject(exchange, status));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        if (status == HttpStatus.UNAUTHORIZED) {
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
