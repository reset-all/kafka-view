package com.kafkaview.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopicInfo {
    private String name;
    private int partitionCount;
    private int replicationFactor;
    
    // 增强信息
    private long messageCount;      // 总消息数
    private long minOffset;         // 最小 Offset (sum of start offsets)
    private long maxOffset;         // 最大 Offset (sum of end offsets)
    private int consumerGroupCount; // 消费者组数量
    
    private java.util.List<Integer> brokerIds; // 涉及的 Broker ID 列表
}
