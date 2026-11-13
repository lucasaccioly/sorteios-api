package com.lucasaccioly.sorteios.common;

/** Resource does not exist or is not visible to the caller. Rendered as 404 (never 403, to avoid leaking IDs). */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Recurso não encontrado");
    }
}
