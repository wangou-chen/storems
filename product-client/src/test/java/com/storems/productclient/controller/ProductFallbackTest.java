package com.storems.productclient.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.storems.productclient.client.ProductServiceClient;
import com.storems.productclient.client.ProductServiceFallback;
import com.storems.productclient.model.Product;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class ProductFallbackTest {
    private final ObjectMapper json = new ObjectMapper();

    private MockMvc mvc(ProductServiceClient client) {
        return MockMvcBuilders.standaloneSetup(new ProductServiceClientController(client))
                .setControllerAdvice(new ProductExceptionHandler()).build();
    }

    private <T> T failedCall(Supplier<T> fallback) {
        return new HystrixCommand<T>(HystrixCommand.Setter
                .withGroupKey(HystrixCommandGroupKey.Factory.asKey("product-fallback-test"))
                .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()
                        .withCircuitBreakerEnabled(false)
                        .withExecutionTimeoutEnabled(false))) {
            @Override
            protected T run() {
                throw new IllegalStateException("Simulated upstream connection failure");
            }

            @Override
            protected T getFallback() {
                return fallback.get();
            }
        }.execute();
    }

    private void assertUnavailable(MockHttpServletResponse response) throws Exception {
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        JsonNode body = json.readTree(response.getContentAsString());
        assertEquals(503, body.get("status").asInt());
        assertEquals("PRODUCT_SERVICE_UNAVAILABLE", body.get("code").asText());
        assertTrue(body.get("message").asText().contains("temporarily unavailable"));
    }

    @Test
    public void directFallbackReturnsExplicit503() throws Exception {
        assertUnavailable(mvc(new ProductServiceFallback())
                .perform(get("/findByProductId/1")).andReturn().getResponse());
    }

    @Test
    public void hystrixWrappedFallbackReturns503ForBothQueries() throws Exception {
        ProductServiceFallback fallback = new ProductServiceFallback();
        ProductServiceClient client = new ProductServiceClient() {
            public Product findByProductId(Long id) {
                return failedCall(() -> fallback.findByProductId(id));
            }
            public List<Product> queryAllProduct() {
                return failedCall(fallback::queryAllProduct);
            }
            public List<Product> queryAll() {
                return failedCall(fallback::queryAll);
            }
        };
        MockMvc mvc = mvc(client);
        assertUnavailable(mvc.perform(get("/findByProductId/1")).andReturn().getResponse());
        assertUnavailable(mvc.perform(get("/queryAllProduct")).andReturn().getResponse());
    }

    @Test
    public void successfulProductAndEmptyListRemain200() throws Exception {
        Product product = new Product();
        product.setId(1L);
        product.setStock(120L);
        ProductServiceClient client = new ProductServiceClient() {
            public Product findByProductId(Long id) { return product; }
            public List<Product> queryAllProduct() { return Collections.emptyList(); }
            public List<Product> queryAll() { return Collections.emptyList(); }
        };
        MockMvc mvc = mvc(client);
        MockHttpServletResponse detail = mvc.perform(get("/findByProductId/1"))
                .andReturn().getResponse();
        assertEquals(200, detail.getStatus());
        assertEquals(120, json.readTree(detail.getContentAsString()).get("stock").asInt());
        MockHttpServletResponse list = mvc.perform(get("/queryAllProduct"))
                .andReturn().getResponse();
        assertEquals(200, list.getStatus());
        assertEquals("[]", list.getContentAsString());
    }
}
