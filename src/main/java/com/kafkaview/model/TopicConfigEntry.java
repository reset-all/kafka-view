package com.kafkaview.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopicConfigEntry {
    private String name;
    private String value;
    private boolean isDefault;
    private boolean isSensitive;
    private boolean isReadOnly;
}
