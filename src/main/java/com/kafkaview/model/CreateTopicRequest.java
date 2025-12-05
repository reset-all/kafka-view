package com.kafkaview.model;

import lombok.Data;

@Data
public class CreateTopicRequest {
    private String name;
    private int partitions;
    private short replicationFactor;
}
