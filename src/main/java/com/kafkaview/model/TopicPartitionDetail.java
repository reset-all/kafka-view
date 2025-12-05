package com.kafkaview.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TopicPartitionDetail {
    private int partition;
    private String leader;
    private List<String> replicas;
    private List<String> isr;
    private long startOffset;
    private long endOffset;
    private long messageCount;
}
