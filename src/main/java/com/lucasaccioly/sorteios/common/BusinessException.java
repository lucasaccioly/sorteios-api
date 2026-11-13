package com.lucasaccioly.sorteios.common;

import java.util.Map;

/** A business rule rejected the request. Rendered as 409 Conflict. */
public class BusinessException extends RuntimeException {

    private final Map<String, Object> properties;

    public BusinessException(String detail) {
        this(detail, Map.of());
    }

    public BusinessException(String detail, Map<String, Object> properties) {
        super(detail);
        this.properties = Map.copyOf(properties);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
