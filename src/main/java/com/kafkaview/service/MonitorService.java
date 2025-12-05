package com.kafkaview.service;

import com.kafkaview.entity.ClusterInfo;
import com.kafkaview.model.ClusterMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.springframework.stereotype.Service;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.util.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MonitorService {

    private final KafkaAdminService kafkaAdminService;
    private final ClusterService clusterService;

    public ClusterMetrics getMetrics(Long clusterId) {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
        ClusterInfo clusterInfo = clusterService.getClusterById(clusterId);

        try {
            // 1. 获取集群节点信息
            DescribeClusterResult clusterResult = admin.describeCluster();
            Collection<Node> nodes = clusterResult.nodes().get();
            Node controller = clusterResult.controller().get();
            int brokerCount = nodes.size();

            // 2. 获取 Topic 列表
            ListTopicsResult listTopics = admin.listTopics();
            Set<String> names = listTopics.names().get();
            int topicCount = names.size();

            // 3. 获取 Topic 详细信息 (分区、副本)
            int partitionCount = 0;
            int underReplicated = 0;
            int underReplicatedReplicas = 0;
            int offline = 0;
            int totalReplicas = 0;

            if (!names.isEmpty()) {
                try {
                    DescribeTopicsResult describeTopics = admin.describeTopics(names);
                    for (TopicDescription td : describeTopics.all().get().values()) {
                        partitionCount += td.partitions().size();
                        for (TopicPartitionInfo p : td.partitions()) {
                            int isrSize = p.isr() == null ? 0 : p.isr().size();
                            int replicaSize = p.replicas() == null ? 0 : p.replicas().size();
                            totalReplicas += replicaSize;
                            log.debug("Topic {} partition {}: isrSize={} replicaSize={} leader={}", td.name(), p.partition(), isrSize, replicaSize, p.leader());
                            if (isrSize < replicaSize) {
                                underReplicated++;
                                underReplicatedReplicas += (replicaSize - isrSize);
                                log.warn("Under-replicated detected for {}-{}: isr={} replicas={}", td.name(), p.partition(), isrSize, replicaSize);
                            }
                            if (p.leader() == null) {
                                offline++;
                            }
                        }
                    }
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof org.apache.kafka.common.errors.UnknownTopicOrPartitionException) {
                        log.warn("Some topics were deleted during metrics fetch, ignoring.");
                    } else {
                        throw e;
                    }
                }
            }

            // 4. 获取磁盘使用量 (尝试获取)
            long totalDisk = 0;
            try {
                List<Integer> brokerIds = nodes.stream().map(Node::id).collect(Collectors.toList());
                DescribeLogDirsResult logDirs = admin.describeLogDirs(brokerIds);
                for (Map<String, LogDirDescription> map : logDirs.allDescriptions().get().values()) {
                    for (LogDirDescription logDir : map.values()) {
                        for (ReplicaInfo replica : logDir.replicaInfos().values()) {
                            totalDisk += replica.size();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch log dirs for cluster {}: {}", clusterInfo.getName(), e.getMessage());
            }

                    double avgReplicas = partitionCount > 0 ? ((double) totalReplicas / partitionCount) : 0.0;

                    ClusterMetrics.ClusterMetricsBuilder builder = ClusterMetrics.builder()
                    .brokerCount(brokerCount)
                    .topicCount(topicCount)
                    .partitionCount(partitionCount)
                    .underReplicatedPartitions(underReplicated)
                    .underReplicatedReplicas(underReplicatedReplicas)
                        .avgReplicationFactor(avgReplicas)
                    .offlinePartitions(offline)
                    .totalDiskUsageBytes(totalDisk);

            return builder.build();

        } catch (Exception e) {
            log.error("Error fetching metrics", e);
            throw new RuntimeException("Failed to fetch metrics: " + e.getMessage());
        }
    }

    // JMX collection removed

    private Map<String, Double> fetchPrometheusMetrics(String host, int port) throws Exception {
        String url = String.format("http://%s:%d/metrics", host, port);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(new URI(url))
                .GET()
                .timeout(java.time.Duration.ofSeconds(5))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) return Collections.emptyMap();
        String body = resp.body();
        Map<String, Double> map = new HashMap<>();
        String[] lines = body.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                String name = parts[0];
                String valStr = parts[1];
                try {
                    Double v = Double.parseDouble(valStr);
                    map.put(name, v);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return map;
    }

    private Double findMetricValue(Map<String, Double> map, String... names) {
        for (String n : names) {
            for (Map.Entry<String, Double> e : map.entrySet()) {
                if (e.getKey().toLowerCase().contains(n.toLowerCase())) return e.getValue();
            }
        }
        return null;
    }
}