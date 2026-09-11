package com.storems.inventoryservice.service;

import com.storems.inventoryservice.client.Product;
import com.storems.inventoryservice.client.ProductClient;
import com.storems.inventoryservice.mapper.InventoryRecordMapper;
import com.storems.inventoryservice.po.InventoryRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {

    private final InventoryRecordMapper inventoryRecordMapper;
    private final ProductClient productClient;

    public InventoryService(InventoryRecordMapper inventoryRecordMapper,
                            ProductClient productClient) {
        this.inventoryRecordMapper = inventoryRecordMapper;
        this.productClient = productClient;
    }

    public int addRecord(InventoryRecord record) {
        return inventoryRecordMapper.insert(record);
    }

    public List<InventoryRecord> queryAllRecords() {
        return inventoryRecordMapper.queryAll();
    }

    public String inbound(Long productId, Long quantity, String operator) {
        if (quantity == null || quantity <= 0) {
            return "quantity must be greater than 0";
        }

        Product product = productClient.findByProductId(productId);

        if (product == null) {
            return "product not found";
        }

        Long currentStock = product.getStock() == null ? 0L : product.getStock();
        Long newStock = currentStock + quantity;

        String updateResult = productClient.updateStock(productId, newStock);

        if (!"success".equals(updateResult)) {
            return "update stock failed";
        }

        InventoryRecord record = new InventoryRecord();
        record.setProductId(productId);
        record.setType("INBOUND");
        record.setQuantity(quantity.intValue());
        record.setOperator(operator);

        inventoryRecordMapper.insert(record);

        return "success";
    }

    public String outbound(Long productId, Long quantity, String operator) {
        if (quantity == null || quantity <= 0) {
            return "quantity must be greater than 0";
        }

        Product product = productClient.findByProductId(productId);

        if (product == null) {
            return "product not found";
        }

        Long currentStock = product.getStock() == null ? 0L : product.getStock();

        if (currentStock < quantity) {
            return "insufficient stock";
        }

        Long newStock = currentStock - quantity;

        String updateResult = productClient.updateStock(productId, newStock);

        if (!"success".equals(updateResult)) {
            return "update stock failed";
        }

        InventoryRecord record = new InventoryRecord();
        record.setProductId(productId);
        record.setType("OUTBOUND");
        record.setQuantity(quantity.intValue());
        record.setOperator(operator);

        inventoryRecordMapper.insert(record);

        return "success";
    }
}