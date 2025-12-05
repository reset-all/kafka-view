package com.kafkaview.service;

import com.kafkaview.entity.ClusterInfo;
import com.kafkaview.mapper.ClusterInfoMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.errors.UnsupportedVersionException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaAdminService {

    private final ClusterInfoMapper clusterInfoMapper;
    private final Map<Long, AdminClient> adminClientCache = new ConcurrentHashMap<>();

    public AdminClient getAdminClient(Long clusterId) {
        if (adminClientCache.containsKey(clusterId)) {
            return adminClientCache.get(clusterId);
        }

        ClusterInfo clusterInfo = clusterInfoMapper.selectById(clusterId);
        if (clusterInfo == null) {
            throw new IllegalArgumentException("Cluster not found: " + clusterId);
        }

        AdminClient adminClient = createAdminClient(clusterInfo);
        adminClientCache.put(clusterId, adminClient);
        return adminClient;
    }

    public void testConnection(ClusterInfo clusterInfo) throws ExecutionException, InterruptedException, TimeoutException {
        log.info("Testing connection for cluster: {}", clusterInfo.getName());
        int timeout = (clusterInfo.getTimeout() != null && clusterInfo.getTimeout() > 0) ? clusterInfo.getTimeout() : 15000;
        try (AdminClient client = createAdminClient(clusterInfo)) {
            Node controller = null;
            try {
                DescribeClusterResult result = client.describeCluster();
                String clusterId = result.clusterId().get(timeout, TimeUnit.MILLISECONDS);
                controller = result.controller().get(timeout, TimeUnit.MILLISECONDS);
                log.info("Successfully connected to cluster: {}, id: {}", clusterInfo.getName(), clusterId);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof UnsupportedVersionException) {
                    log.warn("Cluster {} appears to be older version (describeCluster failed), trying listTopics fallback", clusterInfo.getName());
                    // Fallback: Try listTopics to verify connectivity
                    client.listTopics().names().get(timeout, TimeUnit.MILLISECONDS);
                    // If successful, we assume connected.
                    if (!StringUtils.hasText(clusterInfo.getKafkaVersion())) {
                        clusterInfo.setKafkaVersion("Legacy");
                    }
                } else {
                    log.error("Failed to describe cluster", e);
                    throw e;
                }
            } catch (TimeoutException e) {
                log.error("Connection timed out while describing cluster", e);
                throw new TimeoutException("Connection timed out. Please check your network, bootstrap servers, and security settings.");
            }

            // Try to get Kafka Version from broker config
            if (controller != null) {
                try {
                    ConfigResource resource = new ConfigResource(ConfigResource.Type.BROKER, String.valueOf(controller.id()));
                    Map<ConfigResource, Config> configs = client.describeConfigs(Collections.singleton(resource)).all().get(5, TimeUnit.SECONDS);
                    if (configs != null) {
                        Config config = configs.get(resource);
                        if (config != null) {
                            ConfigEntry versionEntry = config.get("inter.broker.protocol.version");
                            if (versionEntry != null) {
                                clusterInfo.setKafkaVersion(versionEntry.value());
                            } else {
                                // Fallback for older versions or different configs
                                ConfigEntry logFormatEntry = config.get("log.message.format.version");
                                if (logFormatEntry != null) {
                                    clusterInfo.setKafkaVersion(logFormatEntry.value());
                                }
                            }
                        }
                    }
                } catch (org.apache.kafka.common.errors.UnsupportedVersionException e) {
                    log.warn("Kafka version detection not supported by cluster {} (likely older version): {}", clusterInfo.getName(), e.getMessage());
                    clusterInfo.setVersionSupported(false);
                    clusterInfo.setKafkaVersion("低版本");
                    // Don't fail the connection test just because version check failed
                } catch (Exception e) {
                    log.warn("Failed to fetch Kafka version for cluster {}: {}", clusterInfo.getName(), e.getMessage());
                    // Don't fail the connection test just because version check failed
                }
            }

            // Don't use client version as fallback for cluster version
            // If version is not detected, leave it empty
        } catch (Exception e) {
            log.error("testConnection failed", e);
            throw e;
        }
    }

    public Properties getClusterProperties(Long clusterId) {
        ClusterInfo clusterInfo = clusterInfoMapper.selectById(clusterId);
        if (clusterInfo == null) {
            throw new IllegalArgumentException("Cluster not found: " + clusterId);
        }
        return createProperties(clusterInfo);
    }

    private AdminClient createAdminClient(ClusterInfo info) {
        Properties props = createProperties(info);
        return AdminClient.create(props);
    }

    private Properties createProperties(ClusterInfo info) {
        Properties props = new Properties();
        if (info.getBootstrapServers() != null) {
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, info.getBootstrapServers());
        }
        // Use configured timeout or default to 15 seconds
        int timeout = (info.getTimeout() != null && info.getTimeout() > 0) ? info.getTimeout() : 15000;
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeout);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeout);
        // Retry settings
        props.put(AdminClientConfig.RETRIES_CONFIG, 3);

        if (StringUtils.hasText(info.getSecurityProtocol())) {
            props.put("security.protocol", info.getSecurityProtocol());
        }

        if (StringUtils.hasText(info.getSaslMechanism())) {
            props.put(SaslConfigs.SASL_MECHANISM, info.getSaslMechanism());
        }

        // Construct JAAS config if username/password provided, otherwise use raw config
        if (StringUtils.hasText(info.getUsername()) && StringUtils.hasText(info.getPassword())) {
            String jaasTemplate = "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";";
            // Adjust template based on mechanism if needed, but ScramLoginModule is common for PLAIN/SCRAM
            if ("PLAIN".equalsIgnoreCase(info.getSaslMechanism())) {
                jaasTemplate = "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";";
            }
            props.put(SaslConfigs.SASL_JAAS_CONFIG, String.format(jaasTemplate, info.getUsername(), info.getPassword()));
        } else if (StringUtils.hasText(info.getSaslJaasConfig())) {
            props.put(SaslConfigs.SASL_JAAS_CONFIG, info.getSaslJaasConfig());
        }
        return props;
    }

    public void closeClient(Long clusterId) {
        if (clusterId == null) return;
        AdminClient client = adminClientCache.remove(clusterId);
        if (client != null) {
            client.close();
        }
    }

    @PreDestroy
    public void cleanup() {
        adminClientCache.values().forEach(AdminClient::close);
        adminClientCache.clear();
    }
}
