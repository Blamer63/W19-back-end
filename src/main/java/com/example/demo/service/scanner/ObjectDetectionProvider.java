package com.example.demo.service.scanner;

import java.util.List;

public interface ObjectDetectionProvider {
    List<DetectedObject> detect(String imageBase64, double confidenceThreshold, int maxResults);
}
