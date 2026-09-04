package com.storems.gatewayservice.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthFilter implements GlobalFilter, Ordered { // 必须实现 Ordered 接口

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取请求参数中的 token
        String token = exchange.getRequest().getQueryParams().getFirst("token");

        // 防御性编程：避免空指针
        if (!"1".equals(token)) {
            System.out.println("Token 校验失败: " + token);
            // 响应 http 状态码（401）
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            // 请求结束
            return exchange.getResponse().setComplete();
        }

        System.out.println("Token 校验通过，继续转发...");
        // 继续执行过滤器链中的下一个资源
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 优先级：数值越小，优先级越高。
        // 必须设置为负数，确保它在负载均衡器（Order 通常在 10000 以上）之前执行，
        // 且不会破坏后续的响应式上下文。
        return -100;
    }
}