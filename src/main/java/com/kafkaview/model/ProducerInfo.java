package com.kafkaview.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProducerInfo {
    private int partition;
    private long producerId;
    private int producerEpoch;
    private long lastSequence;
    private long lastTimestamp;
    private Long currentTransactionStartOffset;
}
