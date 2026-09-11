package com.storems.inventoryservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/findByProductId/{productId}")
    Product findByProductId(@PathVariable("productId") Long productId);

    @PutMapping("/updateStock/{productId}/{stock}")
    String updateStock(@PathVariable("productId") Long productId,
                       @PathVariable("stock") Long stock);
}