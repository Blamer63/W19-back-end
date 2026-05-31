package com.example.demo.exception;

public class ObjectDetectionUnavailableException extends RuntimeException {

    public ObjectDetectionUnavailableException(String message) {
        super(message);
    }

    public ObjectDetectionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
