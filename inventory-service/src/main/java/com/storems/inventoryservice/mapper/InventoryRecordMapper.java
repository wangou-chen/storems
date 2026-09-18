package com.storems.inventoryservice.mapper;

import com.storems.inventoryservice.po.InventoryRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryRecordMapper {

    @Insert("INSERT INTO inventory_record(product_id, type, quantity, operator) " +
            "VALUES(#{productId}, #{type}, #{quantity}, #{operator})")
    int insert(InventoryRecord record);

    @Select("SELECT id, " +
            "product_id AS productId, " +
            "type, " +
            "quantity, " +
            "operator, " +
            "created_at AS createdAt " +
            "FROM inventory_record ORDER BY id DESC")
    List<InventoryRecord> queryAll();
}