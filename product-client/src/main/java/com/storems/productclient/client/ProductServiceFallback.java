package com.storems.productclient.client;

import com.storems.productclient.model.Product;
import com.storems.productclient.exception.ProductServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ProductServiceFallback implements ProductServiceClient {

    @Override
    public Product findByProductId(Long productId) {
        log.warn("Product lookup unavailable, productId={}", productId);
        throw new ProductServiceUnavailableException();
    }

    @Override
    public List<Product> queryAllProduct() {
        log.warn("Product list unavailable");
        throw new ProductServiceUnavailableException();
    }

    @Override
    public List<Product> queryAll() {
        throw new ProductServiceUnavailableException();
    }
}
