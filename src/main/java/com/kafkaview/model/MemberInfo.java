package com.kafkaview.model;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class MemberInfo {
    private String memberId;
    private String clientId;
    private String host;
    private Set<String> assignment; // Assigned topics
}
