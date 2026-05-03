package com.example.demo.service.scanner;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DetectedObject {
    String yoloLabel;
    Double yoloConfidence;
    String cropBase64;
}
