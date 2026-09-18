package com.storems.inventoryservice.controller;

import com.storems.inventoryservice.service.InventoryService;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class InventoryIdentityTest {
    @Test
    public void operatorCannotBeSpoofedAndMissingLoginCannotWriteStock() throws Exception {
        AtomicReference<String> recordedOperator = new AtomicReference<>();
        InventoryService stock = new InventoryService(null, null) {
            public String inbound(Long product, Long quantity, String operator) {
                recordedOperator.set(operator); return "success";
            }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InventoryController(stock,
                authorization -> {
                    assertEquals("Bearer verified-token", authorization);
                    return Collections.<String, Object>singletonMap("username", "real_user");
                })).build();
        assertEquals(200, mvc.perform(post("/inventory/inbound")
                .param("productId", "1").param("quantity", "10").param("operator", "fake_admin")
                .header("Authorization", "Bearer verified-token"))
                .andReturn().getResponse().getStatus());
        assertEquals("real_user", recordedOperator.get());
        recordedOperator.set(null);
        assertEquals(401, mvc.perform(post("/inventory/inbound")
                .param("productId", "1").param("quantity", "10").param("operator", "fake_admin"))
                .andReturn().getResponse().getStatus());
        assertNull(recordedOperator.get());
        int manualRecordStatus = mvc.perform(post("/inventory/record")
                .contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus();
        assertEquals(404, manualRecordStatus);
    }
}
