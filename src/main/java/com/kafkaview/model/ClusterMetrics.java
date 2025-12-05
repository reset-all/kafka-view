package com.kafkaview.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClusterMetrics {
    // --- 基础指标 (通过 AdminClient 获取) ---
    private int brokerCount;
    private int topicCount;
    private int partitionCount;
    
    // 健康度核心指标
    private int underReplicatedPartitions; // 未同步副本数 (应为0)
    private int underReplicatedReplicas;   // 缺失的副本数量（replicas - isr 的总和）
    private int offlinePartitions;         // 离线分区数 (应为0)
    
    // 存储指标
    private long totalDiskUsageBytes;      // 集群总磁盘占用
    
    // --- 高级指标 (JMX 已移除) ---
    private Double cpuUsage;               // 0.0 - 100.0
    private Long heapMemoryUsed;           // Bytes
    private Long heapMemoryMax;            // Bytes
    private double avgReplicationFactor;   // 平均副本数 (replicas per partition)
}
