package com.kafkaview.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopicMessage {
    private int partition;
    private long offset;
    private long timestamp;
    private String key;
    private String value;
    private String timestampType;
}
