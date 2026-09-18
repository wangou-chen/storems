package com.storems.productclient.controller;

import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.storems.productclient.exception.ProductServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = ProductServiceClientController.class)
public class ProductExceptionHandler {

    // Hystrix wraps exceptions thrown by fallbacks; handle the wrapper as well.
    @ExceptionHandler({ProductServiceUnavailableException.class, HystrixRuntimeException.class})
    public ResponseEntity<Map<String, Object>> unavailable() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 503);
        body.put("code", "PRODUCT_SERVICE_UNAVAILABLE");
        body.put("message", "Product service is temporarily unavailable. Please try again later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
