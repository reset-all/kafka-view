package com.kafkaview.model;

import lombok.Data;

@Data
public class SendMessageRequest {
    private Integer partition;
    private String key;
    private String value;
    private Integer count;
}
