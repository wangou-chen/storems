package com.storems.productservice.mapper;

import com.storems.productservice.po.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import org.apache.ibatis.annotations.Update;

public interface ProductMapper {

    @Select("select p.id,p.product_name as productName,p.price as price,p.stock as stock from product p where id = #{productId}")
    Product findByProductId(@Param("productId") Long productId);

    @Select("select p.id,p.product_name as productName,p.price as price,p.stock as stock from product p")
    List<Product> queryAllProduct();

    @Update("update product set stock = #{stock} where id = #{productId}")
    int updateStock(@Param("productId") Long productId,
                    @Param("stock") Long stock);
}