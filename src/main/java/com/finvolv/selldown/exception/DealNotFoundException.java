package com.finvolv.selldown.exception;

public class DealNotFoundException extends RuntimeException {
    public DealNotFoundException(Long id) {
        super("Deal not found with id: " + id);
    }
}


