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
public class TopicVolume {
    private Long id;
    private Long clusterId;
    private String topicName;
    private String day; // YYYY-MM-DD
    private Long producedCount;
    private Long cumulativeOffset;
    private LocalDateTime createdAt;
}
