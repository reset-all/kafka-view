package com.kafkaview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartitionOffsetRange {
    private int partition;
    private long minOffset;
    private long maxOffset;
}
