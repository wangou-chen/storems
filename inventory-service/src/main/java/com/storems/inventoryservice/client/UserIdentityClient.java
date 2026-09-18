package com.storems.inventoryservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import java.util.Map;

@FeignClient(name = "user-service")
public interface UserIdentityClient {
    @GetMapping("/user/me")
    Map<String, Object> currentUser(@RequestHeader("Authorization") String authorization);
}
