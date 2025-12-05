package com.kafkaview.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClusterInfo {
    private Long id;
    private String name;
    private String bootstrapServers;
    private String kafkaVersion;
    
    // Security Configuration
    private String securityProtocol; // PLAINTEXT, SASL_PLAINTEXT, SASL_SSL, SSL
    private String saslMechanism;    // PLAIN, SCRAM-SHA-256, etc.
    private String saslJaasConfig;   // Full JAAS config string
    
    // Simple Auth Fields
    private String username;
    private String password;
    
    // Monitoring
    private Integer timeout = 15000;
    private Boolean versionSupported = true;
    
    private LocalDateTime createdAt;
}
