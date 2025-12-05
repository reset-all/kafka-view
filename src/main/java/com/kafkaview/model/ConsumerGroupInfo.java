package com.kafkaview.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConsumerGroupInfo {
    private String groupId;
    private String state;
    private String protocolType;
    private String coordinator;
    private List<MemberInfo> members;
    
    // 针对特定 Topic 的统计信息 (如果指定了 topic)
    private Long totalLag;
    private Long currentOffset;     // 当前消费 Offset (sum of current offsets)
    private Long logStartOffset;    // Topic 最小 Offset (sum of start offsets)
    private Long logEndOffset;      // Topic 最大 Offset (sum of end offsets)
    private Long topicMessageCount; // Deprecated or alias for currentOffset
}
