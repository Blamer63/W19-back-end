package com.example.demo.exception;

public class DuplicateSavedWordException extends RuntimeException {
    public DuplicateSavedWordException(String message) {
        super(message);
    }
}
