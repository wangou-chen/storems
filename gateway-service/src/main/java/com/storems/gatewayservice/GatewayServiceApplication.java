package com.storems.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient; // 必须导入这个

@SpringBootApplication
@EnableDiscoveryClient // 开启服务发现，从Eureka拉取服务
public class GatewayServiceApplication
{
    public static void main( String[] args ){
        SpringApplication.run(GatewayServiceApplication.class,args);
    }
}