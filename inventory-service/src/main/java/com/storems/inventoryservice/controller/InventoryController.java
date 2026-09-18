package com.storems.inventoryservice.controller;

import com.storems.inventoryservice.po.InventoryRecord;
import com.storems.inventoryservice.service.InventoryService;
import com.storems.inventoryservice.client.UserIdentityClient;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final UserIdentityClient userIdentityClient;

    public InventoryController(InventoryService inventoryService, UserIdentityClient userIdentityClient) {
        this.inventoryService = inventoryService;
        this.userIdentityClient = userIdentityClient;
    }

    // Records are created only by successful stock operations.

    @GetMapping("/records")
    public List<InventoryRecord> queryAllRecords() {
        return inventoryService.queryAllRecords();
    }

    @PostMapping("/inbound")
    public String inbound(@RequestParam Long productId,
                          @RequestParam Long quantity,
                          @RequestHeader(value = "Authorization", required = false) String authorization) {
        return inventoryService.inbound(productId, quantity, operator(authorization));
    }

    @PostMapping("/outbound")
    public String outbound(@RequestParam Long productId,
                           @RequestParam Long quantity,
                           @RequestHeader(value = "Authorization", required = false) String authorization) {
        return inventoryService.outbound(productId, quantity, operator(authorization));
    }

    private String operator(String authorization) {
        if (authorization == null || authorization.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        try {
            Map<String, Object> identity = userIdentityClient.currentUser(authorization);
            Object username = identity == null ? null : identity.get("username");
            if (!(username instanceof String) || ((String) username).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "invalid identity response");
            }
            return (String) username;
        } catch (FeignException ex) {
            throw new ResponseStatusException(ex.status() == 401
                    ? HttpStatus.UNAUTHORIZED : HttpStatus.SERVICE_UNAVAILABLE,
                    ex.status() == 401 ? "invalid or expired token" : "identity service unavailable");
        }
    }
}
