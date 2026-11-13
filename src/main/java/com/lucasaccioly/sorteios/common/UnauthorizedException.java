package com.lucasaccioly.sorteios.common;

/** Credentials were rejected. Rendered as 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String detail) {
        super(detail);
    }
}
