package com.kafkaview.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageHistory {
    private Long id;
    private Long clusterId;
    private String topicName;
    private Integer partitionId;
    private String keyContent;
    private String valueContent;
    private LocalDateTime createdAt;
}
