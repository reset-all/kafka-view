package com.kafkaview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSearchResult {
    private List<TopicMessage> list;
    private long total;
    private int page;
    private int pageSize;
    private Map<Integer, PartitionOffsetRange> partitionOffsets;
}
