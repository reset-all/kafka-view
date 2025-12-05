package com.kafkaview.service;

import com.kafkaview.model.*;
import com.kafkaview.mapper.MessageHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.kafka.common.errors.UnsupportedVersionException;

@Service
@Slf4j
@RequiredArgsConstructor
public class TopicService {

    private final KafkaAdminService kafkaAdminService;
    private final MessageHistoryMapper messageHistoryMapper;
    private final TopicVolumeService topicVolumeService;

    public PageResult<ProducerInfo> getTopicProducers(Long clusterId, String topicName, int page, int pageSize) throws ExecutionException, InterruptedException {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
        
        DescribeTopicsResult describeResult = admin.describeTopics(Collections.singleton(topicName));
        TopicDescription td = describeResult.all().get().get(topicName);
        
        List<TopicPartition> tps = td.partitions().stream()
                .map(p -> new TopicPartition(topicName, p.partition()))
                .collect(Collectors.toList());
        
        try {
            Map<TopicPartition, DescribeProducersResult.PartitionProducerState> producerStates = 
                    admin.describeProducers(tps).all().get();
            
            List<ProducerInfo> producers = new ArrayList<>();
            
            for (Map.Entry<TopicPartition, DescribeProducersResult.PartitionProducerState> entry : producerStates.entrySet()) {
                int partition = entry.getKey().partition();
                for (ProducerState state : entry.getValue().activeProducers()) {
                    producers.add(ProducerInfo.builder()
                            .partition(partition)
                            .producerId(state.producerId())
                            .producerEpoch(state.producerEpoch())
                            .lastSequence(state.lastSequence())
                            .lastTimestamp(state.lastTimestamp())
                            .currentTransactionStartOffset(state.currentTransactionStartOffset().isPresent() ? state.currentTransactionStartOffset().getAsLong() : null)
                            .build());
                }
            }
            
            List<ProducerInfo> sortedProducers = producers.stream()
                    .sorted(Comparator.comparingInt(ProducerInfo::getPartition)
                            .thenComparingLong(ProducerInfo::getProducerId))
                    .collect(Collectors.toList());

            int total = sortedProducers.size();
            int start = (page - 1) * pageSize;
            if (start >= total) {
                return new PageResult<>(Collections.emptyList(), total, page, pageSize);
            }
            int end = Math.min(start + pageSize, total);
            return new PageResult<>(sortedProducers.subList(start, end), total, page, pageSize);
            
        } catch (ExecutionException e) {
             if (e.getCause() instanceof UnsupportedVersionException) {
                 log.warn("describeProducers not supported by broker");
                 return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
             }
             // Handle authorization or other errors gracefully if needed, similar to configs
             if (e.getCause() instanceof org.apache.kafka.common.errors.TopicAuthorizationException) {
                 log.warn("Authorization failed for topic producers: {}", topicName);
                 return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
             }
             throw e;
        }
    }

    public List<TopicConfigEntry> getTopicConfigs(Long clusterId, String topicName) throws ExecutionException, InterruptedException {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
        DescribeConfigsResult result = admin.describeConfigs(Collections.singleton(resource));
        
        Config config;
        try {
            config = result.all().get().get(resource);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.TopicAuthorizationException) {
                log.warn("Authorization failed for topic configs: {}", topicName);
                return Collections.emptyList();
            }
            throw e;
        } catch (Exception e) {
             log.warn("Failed to describe configs for topic {}: {}", topicName, e.getMessage());
             return Collections.emptyList();
        }
        
        return config.entries().stream()
                .map(entry -> TopicConfigEntry.builder()
                        .name(entry.name())
                        .value(entry.value())
                        .isDefault(entry.isDefault())
                        .isSensitive(entry.isSensitive())
                        .isReadOnly(entry.isReadOnly())
                        .build())
                .sorted(Comparator.comparing(TopicConfigEntry::getName))
                .collect(Collectors.toList());
    }

    public void updateTopicConfig(Long clusterId, String topicName, Map<String, String> configs) throws ExecutionException, InterruptedException {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
        
        Collection<AlterConfigOp> ops = configs.entrySet().stream()
                .map(entry -> new AlterConfigOp(
                        new ConfigEntry(entry.getKey(), entry.getValue()), 
                        AlterConfigOp.OpType.SET))
                .collect(Collectors.toList());
        
        Map<ConfigResource, Collection<AlterConfigOp>> updateMap = new HashMap<>();
        updateMap.put(resource, ops);
        
        admin.incrementalAlterConfigs(updateMap).all().get();
    }

    public PageResult<TopicPartitionDetail> getTopicPartitions(Long clusterId, String topicName, int page, int pageSize) throws ExecutionException, InterruptedException {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
        
        DescribeTopicsResult describeResult = admin.describeTopics(Collections.singleton(topicName));
        TopicDescription td = describeResult.all().get().get(topicName);
        
        List<org.apache.kafka.common.TopicPartitionInfo> allPartitions = new ArrayList<>(td.partitions());
        // Sort partitions by ID to ensure consistent order
        allPartitions.sort(Comparator.comparingInt(org.apache.kafka.common.TopicPartitionInfo::partition));

        int total = allPartitions.size();
        int start = (page - 1) * pageSize;
        if (start >= total) {
            return new PageResult<>(Collections.emptyList(), total, page, pageSize);
        }
        int end = Math.min(start + pageSize, total);
        List<org.apache.kafka.common.TopicPartitionInfo> pagePartitions = allPartitions.subList(start, end);
        
        List<TopicPartition> tps = pagePartitions.stream()
                .map(p -> new TopicPartition(topicName, p.partition()))
                .collect(Collectors.toList());
        
        Map<TopicPartition, OffsetSpec> startSpecs = tps.stream().collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.earliest()));
        Map<TopicPartition, OffsetSpec> endSpecs = tps.stream().collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
        
        var startOffsets = admin.listOffsets(startSpecs).all().get();
        var endOffsets = admin.listOffsets(endSpecs).all().get();
        
        List<TopicPartitionDetail> details = new ArrayList<>();
        for (org.apache.kafka.common.TopicPartitionInfo info : pagePartitions) {
            TopicPartition tp = new TopicPartition(topicName, info.partition());
            long startOffset = startOffsets.get(tp).offset();
            long endOffset = endOffsets.get(tp).offset();
            
            details.add(TopicPartitionDetail.builder()
                    .partition(info.partition())
                    .leader(info.leader().host() + ":" + info.leader().port())
                    .replicas(info.replicas().stream().map(n -> n.idString()).collect(Collectors.toList()))
                    .isr(info.isr().stream().map(n -> n.idString()).collect(Collectors.toList()))
                    .startOffset(startOffset)
                    .endOffset(endOffset)
                    .messageCount(endOffset - startOffset)
                    .build());
        }
        return new PageResult<>(details, total, page, pageSize);
    }

    public PageResult<TopicInfo> listTopics(Long clusterId, int page, int pageSize, String keyword) throws ExecutionException, InterruptedException {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
        
        // 1. 获取所有 Topic 名称
        ListTopicsResult listResult = admin.listTopics();
        Set<String> allNames = listResult.names().get();
        
        // 2. 内存过滤
        List<String> filteredNames = allNames.stream()
                .filter(name -> !StringUtils.hasText(keyword) || name.toLowerCase().contains(keyword.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
        
        int total = filteredNames.size();
        
        // 3. 内存分页
        int start = (page - 1) * pageSize;
        if (start >= total) {
            return new PageResult<>(Collections.emptyList(), total, page, pageSize);
        }
        int end = Math.min(start + pageSize, total);
        List<String> pageNames = filteredNames.subList(start, end);
        
        // 4. 获取详细信息 (Describe)
        DescribeTopicsResult describeResult = admin.describeTopics(pageNames);
        Map<String, TopicDescription> descriptions = describeResult.all().get();
        
        // 5. 批量获取 Offset 信息 (LogStart 和 LogEnd)
        // 构造所有分区的查询请求
        List<TopicPartition> allPartitions = new ArrayList<>();
        for (TopicDescription td : descriptions.values()) {
            for (org.apache.kafka.common.TopicPartitionInfo tpi : td.partitions()) {
                allPartitions.add(new TopicPartition(td.name(), tpi.partition()));
            }
        }
        
        Map<TopicPartition, OffsetSpec> startSpecs = new HashMap<>();
        Map<TopicPartition, OffsetSpec> endSpecs = new HashMap<>();
        allPartitions.forEach(tp -> startSpecs.put(tp, OffsetSpec.earliest()));
        allPartitions.forEach(tp -> endSpecs.put(tp, OffsetSpec.latest()));
        
        // 并行查询 Start 和 End Offsets
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> startOffsets = new HashMap<>();
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets = new HashMap<>();
        
        try {
            var startOffsetsFuture = admin.listOffsets(startSpecs).all();
            var endOffsetsFuture = admin.listOffsets(endSpecs).all();
            
            startOffsets = startOffsetsFuture.get();
            endOffsets = endOffsetsFuture.get();
        } catch (Exception e) {
            log.warn("Failed to fetch topic offsets: {}", e.getMessage());
        }
        
        // 6. Calculate Consumer Group Counts (Active + Inactive with offsets)
        Map<String, Integer> topicGroupCounts = new HashMap<>();
        try {
            Collection<ConsumerGroupListing> groupListings = admin.listConsumerGroups().all().get();
            List<String> allGroupIds = groupListings.stream().map(ConsumerGroupListing::groupId).collect(Collectors.toList());
            
            if (!allGroupIds.isEmpty()) {
                // Map to store unique groups per topic
                Map<String, Set<String>> topicToGroups = new HashMap<>();
                for (String name : pageNames) {
                    topicToGroups.put(name, new HashSet<>());
                }

                // 1. Check Active Members (via DescribeGroups)
                Map<String, KafkaFuture<ConsumerGroupDescription>> describeFutures = admin.describeConsumerGroups(allGroupIds).describedGroups();
                
                for (Map.Entry<String, KafkaFuture<ConsumerGroupDescription>> entry : describeFutures.entrySet()) {
                    try {
                        ConsumerGroupDescription group = entry.getValue().get();
                        String groupId = group.groupId();
                        for (MemberDescription member : group.members()) {
                            for (TopicPartition tp : member.assignment().topicPartitions()) {
                                if (topicToGroups.containsKey(tp.topic())) {
                                    topicToGroups.get(tp.topic()).add(groupId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore authorization errors or other failures for specific groups
                    }
                }

                // 2. Check Committed Offsets for groups not yet counted or just check all
                // We need to check offsets for ALL groups for the topics in the page.
                // To optimize, we batch requests.
                
                // Prepare all partitions for topics in the current page
                List<TopicPartition> allPagePartitions = new ArrayList<>();
                for (String name : pageNames) {
                    TopicDescription td = descriptions.get(name);
                    for (org.apache.kafka.common.TopicPartitionInfo tpi : td.partitions()) {
                        allPagePartitions.add(new TopicPartition(name, tpi.partition()));
                    }
                }

                if (!allPagePartitions.isEmpty()) {
                    Map<String, KafkaFuture<Map<TopicPartition, OffsetAndMetadata>>> futures = new HashMap<>();
                    ListConsumerGroupOffsetsOptions options = new ListConsumerGroupOffsetsOptions()
                            .topicPartitions(allPagePartitions);

                    for (String groupId : allGroupIds) {
                        futures.put(groupId, admin.listConsumerGroupOffsets(groupId, options).partitionsToOffsetAndMetadata());
                    }
                    
                    for (Map.Entry<String, KafkaFuture<Map<TopicPartition, OffsetAndMetadata>>> entry : futures.entrySet()) {
                        try {
                            String groupId = entry.getKey();
                            Map<TopicPartition, OffsetAndMetadata> offsets = entry.getValue().get();
                            
                            for (Map.Entry<TopicPartition, OffsetAndMetadata> offsetEntry : offsets.entrySet()) {
                                if (offsetEntry.getValue() != null) {
                                    String topic = offsetEntry.getKey().topic();
                                    if (topicToGroups.containsKey(topic)) {
                                        topicToGroups.get(topic).add(groupId);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Ignore errors for specific groups
                        }
                    }
                }
                
                // Summarize
                for (Map.Entry<String, Set<String>> entry : topicToGroups.entrySet()) {
                    topicGroupCounts.put(entry.getKey(), entry.getValue().size());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch consumer group counts", e);
        }

        // 7. 组装结果
        List<TopicInfo> topicInfos = new ArrayList<>();
        for (String name : pageNames) {
            TopicDescription td = descriptions.get(name);
            long totalMsg = 0;
            long minOff = 0; // 这里展示的是所有分区 StartOffset 之和，或者可以展示最小的那个
            long maxOff = 0; // 同上
            
            Set<Integer> brokerIds = new HashSet<>();
            
            for (org.apache.kafka.common.TopicPartitionInfo tpi : td.partitions()) {
                TopicPartition tp = new TopicPartition(name, tpi.partition());
                long startOffset = 0;
                long endOffset = 0;
                
                if (startOffsets.containsKey(tp)) {
                    startOffset = startOffsets.get(tp).offset();
                }
                if (endOffsets.containsKey(tp)) {
                    endOffset = endOffsets.get(tp).offset();
                }
                
                totalMsg += (endOffset - startOffset);
                minOff += startOffset;
                maxOff += endOffset;
                
                tpi.replicas().forEach(node -> brokerIds.add(node.id()));
            }
            
            topicInfos.add(TopicInfo.builder()
                    .name(name)
                    .partitionCount(td.partitions().size())
                    .replicationFactor(td.partitions().get(0).replicas().size())
                    .messageCount(totalMsg)
                    .minOffset(minOff)
                    .maxOffset(maxOff)
                    .consumerGroupCount(topicGroupCounts.getOrDefault(name, 0))
                    .brokerIds(new ArrayList<>(brokerIds).stream().sorted().collect(Collectors.toList()))
                    .build());
        }
        
        return new PageResult<>(topicInfos, total, page, pageSize);
    }

    // Expose topic volume helper
    public List<Long> getTopicVolumeList(Long clusterId, String topicName, int days) {
        return topicVolumeService.getTopicVolumes(clusterId, topicName, days);
    }

    public Map<String, List<Long>> getTopicsVolumeBatch(Long clusterId, List<String> topics, int days) {
        return topicVolumeService.getTopicsVolumesBatch(clusterId, topics, days);
    }

    public void backfillTopicVolumes(Long clusterId, List<String> topics, int days) {
        topicVolumeService.backfillTopicsBatch(clusterId, topics, days);
    }

    public void createTopic(Long clusterId, CreateTopicRequest request) throws ExecutionException, InterruptedException {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
        NewTopic newTopic = new NewTopic(request.getName(), request.getPartitions(), request.getReplicationFactor());
        admin.createTopics(Collections.singleton(newTopic)).all().get();
    }

    public void deleteTopic(Long clusterId, String topicName) throws ExecutionException, InterruptedException {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
        admin.deleteTopics(Collections.singleton(topicName)).all().get();
    }

    public void sendMessage(Long clusterId, String topicName, Integer partition, String key, String value, Integer count) throws ExecutionException, InterruptedException {
        Properties props = kafkaAdminService.getClusterProperties(clusterId);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        int sendCount = (count == null || count < 1) ? 1 : count;
        if (sendCount > 200) sendCount = 200;

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            List<java.util.concurrent.Future<RecordMetadata>> futures = new ArrayList<>();
            for (int i = 0; i < sendCount; i++) {
                ProducerRecord<String, String> record;
                if (partition != null) {
                    record = new ProducerRecord<>(topicName, partition, key, value);
                } else {
                    record = new ProducerRecord<>(topicName, key, value);
                }
                futures.add(producer.send(record));
            }
            
            // Wait for all to complete
            for (java.util.concurrent.Future<RecordMetadata> future : futures) {
                future.get();
            }
            
            // Save history (only once per batch to avoid clutter, or maybe just the last one?)
            // Requirement says "save history", usually implies the action. 
            // If we send 200 messages, saving 200 history records might be too much for the UI list.
            // But let's stick to saving one record representing the batch or just the content.
            // Given the UI shows "Resend", saving one record with the content is enough to resend the same content.
            MessageHistory history = MessageHistory.builder()
                    .clusterId(clusterId)
                    .topicName(topicName)
                    .partitionId(partition)
                    .keyContent(key)
                    .valueContent(value)
                    .build();
            messageHistoryMapper.insert(history);
            
            // Cleanup old records (keep last 100)
            messageHistoryMapper.deleteOldRecords(clusterId, topicName);
        }
    }

    public PageResult<MessageHistory> getMessageHistory(Long clusterId, String topicName, int page, int pageSize) {
        int total = messageHistoryMapper.countByTopic(clusterId, topicName);
        int offset = (page - 1) * pageSize;
        List<MessageHistory> list = messageHistoryMapper.selectByTopic(clusterId, topicName, pageSize, offset);
        return new PageResult<>(list, total, page, pageSize);
    }

    private TopicInfo convertToTopicInfo(TopicDescription td) {
        return TopicInfo.builder()
                .name(td.name())
                .partitionCount(td.partitions().size())
                .replicationFactor((int) td.partitions().get(0).replicas().size())
                .build();
    }

    public MessageSearchResult searchMessages(Long clusterId, String topicName, List<Integer> partitionIds, Long startTime, Long endTime, Long startOffset, Long endOffset, String key, String keyword, int limit, int page, int pageSize, String sortField, String sortOrder, String scanDirection, int timeout, int retryCount) {
        Properties props = kafkaAdminService.getClusterProperties(clusterId);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafka-view-search-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Optimized for faster scanning
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 5000);
        props.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 50 * 1024 * 1024);
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 10 * 1024 * 1024);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        
        // Enforce limit constraints
        if (limit > 1000) limit = 1000;
        if (limit < 1) limit = 1;
        
        List<TopicMessage> allMatches = new ArrayList<>();
        Map<Integer, PartitionOffsetRange> partitionBounds = new HashMap<>();
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> tps = new ArrayList<>();
            if (partitionIds != null && !partitionIds.isEmpty()) {
                for (Integer pid : partitionIds) {
                    tps.add(new TopicPartition(topicName, pid));
                }
            } else {
                consumer.partitionsFor(topicName).forEach(p -> tps.add(new TopicPartition(topicName, p.partition())));
            }
            consumer.assign(tps);

            // Fetch Partition Bounds (Min/Max) for UI
            Map<TopicPartition, Long> logStartOffsets = consumer.beginningOffsets(tps);
            Map<TopicPartition, Long> logEndOffsets = consumer.endOffsets(tps);
            
            for (TopicPartition tp : tps) {
                partitionBounds.put(tp.partition(), PartitionOffsetRange.builder()
                        .partition(tp.partition())
                        .minOffset(logStartOffsets.getOrDefault(tp, 0L))
                        .maxOffset(logEndOffsets.getOrDefault(tp, 0L))
                        .build());
            }

            Map<TopicPartition, Long> startOffsets = new HashMap<>();
            Map<TopicPartition, Long> endOffsets = new HashMap<>();

            // Initialize with Log Bounds
            for (TopicPartition tp : tps) {
                startOffsets.put(tp, logStartOffsets.getOrDefault(tp, 0L));
                endOffsets.put(tp, logEndOffsets.getOrDefault(tp, 0L));
            }

            boolean hasTimeConstraint = startTime != null || endTime != null;
            boolean hasOffsetConstraint = startOffset != null || endOffset != null;

            if (!hasTimeConstraint && !hasOffsetConstraint) {
                // Default behavior
                if ("desc".equalsIgnoreCase(scanDirection)) {
                    // Newest First: Last limit messages
                    for (TopicPartition tp : tps) {
                        long end = logEndOffsets.get(tp);
                        startOffsets.put(tp, Math.max(0, end - limit));
                        endOffsets.put(tp, end);
                    }
                } else {
                    // Oldest First: From Beginning (Log Start Offset)
                    // startOffsets are already initialized to logStartOffsets
                    // endOffsets are already initialized to logEndOffsets
                }
            } else {
                // Apply Time Constraints
                if (startTime != null) {
                    Map<TopicPartition, Long> timestampsToSearch = new HashMap<>();
                    for (TopicPartition tp : tps) timestampsToSearch.put(tp, startTime);
                    Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(timestampsToSearch);
                    for (Map.Entry<TopicPartition, OffsetAndTimestamp> entry : offsets.entrySet()) {
                        TopicPartition tp = entry.getKey();
                        if (entry.getValue() != null) {
                            startOffsets.put(tp, Math.max(startOffsets.get(tp), entry.getValue().offset()));
                        } else {
                            startOffsets.put(tp, endOffsets.get(tp)); // Time is future
                        }
                    }
                }
                if (endTime != null) {
                    Map<TopicPartition, Long> timestampsToSearch = new HashMap<>();
                    for (TopicPartition tp : tps) timestampsToSearch.put(tp, endTime);
                    Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(timestampsToSearch);
                    for (Map.Entry<TopicPartition, OffsetAndTimestamp> entry : offsets.entrySet()) {
                        TopicPartition tp = entry.getKey();
                        if (entry.getValue() != null) {
                            endOffsets.put(tp, Math.min(endOffsets.get(tp), entry.getValue().offset()));
                        }
                        // If null, time is future, so keep Log End Offset
                    }
                }

                // Apply Offset Constraints
                if (startOffset != null) {
                    for (TopicPartition tp : tps) {
                        startOffsets.put(tp, Math.max(startOffsets.get(tp), startOffset));
                    }
                }
                if (endOffset != null) {
                    for (TopicPartition tp : tps) {
                        endOffsets.put(tp, Math.min(endOffsets.get(tp), endOffset));
                    }
                }
            }

            if ("desc".equalsIgnoreCase(scanDirection)) {
                // Reverse Scan Strategy (Newest First)
                // We scan backwards in chunks from endOffsets to startOffsets
                int chunkSize = 20000;
                Map<TopicPartition, Long> currentEnds = new HashMap<>(endOffsets);
                boolean anyPartitionHasData = true;
                
                while (allMatches.size() < limit && anyPartitionHasData) {
                    anyPartitionHasData = false;
                    List<TopicPartition> activePartitions = new ArrayList<>();
                    Map<TopicPartition, Long> chunkTargetEnds = new HashMap<>();

                    for (TopicPartition tp : tps) {
                        long absStart = startOffsets.get(tp);
                        long currentEnd = currentEnds.get(tp);
                        
                        if (currentEnd > absStart) {
                            long chunkStart = Math.max(absStart, currentEnd - chunkSize);
                            consumer.seek(tp, chunkStart);
                            activePartitions.add(tp);
                            chunkTargetEnds.put(tp, currentEnd);
                            anyPartitionHasData = true;
                            
                            // Update cursor for next iteration
                            currentEnds.put(tp, chunkStart);
                        } else {
                            consumer.pause(Collections.singleton(tp));
                        }
                    }
                    
                    if (!anyPartitionHasData) break;
                    
                    consumer.resume(activePartitions);

                    // Poll this chunk
                    boolean chunkComplete = false;
                    int emptyPolls = 0;
                    
                    while (!chunkComplete && emptyPolls < retryCount) {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(timeout));
                        if (records.isEmpty()) {
                            emptyPolls++;
                        } else {
                            emptyPolls = 0;
                            for (ConsumerRecord<String, String> record : records) {
                                TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                                if (!chunkTargetEnds.containsKey(tp)) continue;
                                if (record.offset() >= chunkTargetEnds.get(tp)) continue;

                                boolean match = true;
                                if (StringUtils.hasText(key) && (record.key() == null || !record.key().contains(key))) match = false;
                                if (StringUtils.hasText(keyword) && (record.value() == null || !record.value().contains(keyword))) match = false;

                                if (match) {
                                    allMatches.add(TopicMessage.builder()
                                            .partition(record.partition())
                                            .offset(record.offset())
                                            .timestamp(record.timestamp())
                                            .timestampType(record.timestampType().name())
                                            .key(record.key())
                                            .value(record.value())
                                            .build());
                                }
                            }
                        }
                        
                        // Check if all active partitions reached their target
                        boolean allReached = true;
                        for (TopicPartition tp : activePartitions) {
                            if (consumer.position(tp) < chunkTargetEnds.get(tp)) {
                                allReached = false;
                                break;
                            }
                        }
                        if (allReached) chunkComplete = true;
                        if (allMatches.size() >= limit) chunkComplete = true;
                    }
                }
            } else {
                // Forward Scan (ASC) - Oldest First
                for (TopicPartition tp : tps) {
                    Long start = startOffsets.get(tp);
                    if (start != null && start != -1L) {
                        consumer.seek(tp, start);
                    } else {
                        consumer.seek(tp, endOffsets.get(tp));
                    }
                }

                int maxScanned = 500000;
                int scanned = 0;
                boolean allDone = false;

                while (scanned < maxScanned && !allDone) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(timeout));
                    if (records.isEmpty()) {
                        boolean anyRemaining = false;
                        for (TopicPartition tp : tps) {
                            if (consumer.position(tp) < endOffsets.get(tp)) {
                                anyRemaining = true;
                                break;
                            }
                        }
                        if (!anyRemaining) break;
                        continue;
                    }

                    for (ConsumerRecord<String, String> record : records) {
                        scanned++;
                        TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                        if (record.offset() >= endOffsets.get(tp)) {
                            continue;
                        }

                        boolean match = true;
                        if (StringUtils.hasText(key) && (record.key() == null || !record.key().contains(key))) match = false;
                        if (StringUtils.hasText(keyword) && (record.value() == null || !record.value().contains(keyword))) match = false;

                        if (match) {
                            allMatches.add(TopicMessage.builder()
                                    .partition(record.partition())
                                    .offset(record.offset())
                                    .timestamp(record.timestamp())
                                    .timestampType(record.timestampType().name())
                                    .key(record.key())
                                    .value(record.value())
                                    .build());
                            if (allMatches.size() >= limit) {
                                allDone = true;
                                break;
                            }
                        }
                    }
                    
                    boolean anyRemaining = false;
                    for (TopicPartition tp : tps) {
                        if (consumer.position(tp) < endOffsets.get(tp)) {
                            anyRemaining = true;
                            break;
                        }
                    }
                    if (!anyRemaining) allDone = true;
                }
            }
        }
        
        // Sort
        Comparator<TopicMessage> comparator;
        if ("offset".equalsIgnoreCase(sortField)) {
            comparator = Comparator.comparingLong(TopicMessage::getOffset);
        } else if ("partition".equalsIgnoreCase(sortField)) {
            comparator = Comparator.comparingInt(TopicMessage::getPartition);
        } else {
            // Default to timestamp
            comparator = Comparator.comparingLong(TopicMessage::getTimestamp);
        }

        if ("asc".equalsIgnoreCase(sortOrder)) {
            allMatches.sort(comparator);
        } else {
            allMatches.sort(comparator.reversed());
        }

        int total = allMatches.size();
        int start = (page - 1) * pageSize;
        if (start >= total) {
            return MessageSearchResult.builder()
                    .list(Collections.emptyList())
                    .total(total)
                    .page(page)
                    .pageSize(pageSize)
                    .partitionOffsets(partitionBounds)
                    .build();
        }
        int end = Math.min(start + pageSize, total);
        return MessageSearchResult.builder()
                .list(allMatches.subList(start, end))
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .partitionOffsets(partitionBounds)
                .build();
    }
}
