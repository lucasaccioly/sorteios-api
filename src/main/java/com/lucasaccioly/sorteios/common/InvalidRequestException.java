package com.lucasaccioly.sorteios.common;

/** Input failed a check that needs the clock or other fields. Rendered as 400 with {@code errors.<field>}. */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
