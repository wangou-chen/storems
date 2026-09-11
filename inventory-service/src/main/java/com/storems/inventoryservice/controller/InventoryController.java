package com.storems.inventoryservice.controller;

import com.storems.inventoryservice.po.InventoryRecord;
import com.storems.inventoryservice.service.InventoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/record")
    public String addRecord(@RequestBody InventoryRecord record) {
        int result = inventoryService.addRecord(record);

        if (result > 0) {
            return "success";
        }

        return "failed";
    }

    @GetMapping("/records")
    public List<InventoryRecord> queryAllRecords() {
        return inventoryService.queryAllRecords();
    }

    @PostMapping("/inbound")
    public String inbound(@RequestParam Long productId,
                          @RequestParam Long quantity,
                          @RequestParam String operator) {
        return inventoryService.inbound(productId, quantity, operator);
    }

    @PostMapping("/outbound")
    public String outbound(@RequestParam Long productId,
                           @RequestParam Long quantity,
                           @RequestParam String operator) {
        return inventoryService.outbound(productId, quantity, operator);
    }

}