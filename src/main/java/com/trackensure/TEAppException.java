package com.trackensure;

public class TEAppException extends Exception {
    public TEAppException(String message) {
        super(message);
    }

    public TEAppException(String message, Throwable cause) {
        super(message, cause);
    }
}
