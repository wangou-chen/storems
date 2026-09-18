package com.storems.productclient.exception;

public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException() {
        super("Product service is temporarily unavailable. Please try again later.");
    }
}
