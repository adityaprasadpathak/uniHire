package com.unihire.exceptions;

public class JobExpiredException extends RuntimeException {
    public JobExpiredException(String message) {
        super(message);
    }
}
