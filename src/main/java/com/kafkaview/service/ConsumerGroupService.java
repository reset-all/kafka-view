package com.kafkaview.service;

import com.kafkaview.model.ConsumerGroupInfo;
import com.kafkaview.model.MemberInfo;
import com.kafkaview.model.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConsumerGroupService {

    private final KafkaAdminService kafkaAdminService;

    public PageResult<ConsumerGroupInfo> listConsumerGroups(Long clusterId, int page, int pageSize, String keyword, String topic) throws ExecutionException, InterruptedException {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);

        // 1. List all groups
        Collection<ConsumerGroupListing> listings = admin.listConsumerGroups().all().get();

        // 2. Filter by keyword (Group ID)
        List<String> candidateGroupIds = listings.stream()
                .map(ConsumerGroupListing::groupId)
                .filter(id -> !StringUtils.hasText(keyword) || id.toLowerCase().contains(keyword.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());

        // 3. Describe groups to filter by topic (if needed) and get details
        List<String> filteredGroupIds = candidateGroupIds;
        Map<String, ConsumerGroupDescription> descriptionsMap = new HashMap<>();
        Map<String, KafkaFuture<Map<TopicPartition, OffsetAndMetadata>>> offsetsFutures = new HashMap<>();
        
        if (!candidateGroupIds.isEmpty()) {
             // Batch describe with partial failure handling
             Map<String, KafkaFuture<ConsumerGroupDescription>> futures = admin.describeConsumerGroups(candidateGroupIds).describedGroups();
             
             // If topic filter is enabled, we also need to check committed offsets to find inactive groups
             if (StringUtils.hasText(topic)) {
                 try {
                     DescribeTopicsResult describeTopics = admin.describeTopics(Collections.singleton(topic));
                     TopicDescription td = describeTopics.all().get().get(topic);
                     List<TopicPartition> topicPartitions = td.partitions().stream()
                             .map(p -> new TopicPartition(topic, p.partition()))
                             .collect(Collectors.toList());
                             
                     for (String groupId : candidateGroupIds) {
                         offsetsFutures.put(groupId, admin.listConsumerGroupOffsets(groupId, 
                                 new ListConsumerGroupOffsetsOptions().topicPartitions(topicPartitions)).partitionsToOffsetAndMetadata());
                     }
                 } catch (Exception e) {
                     log.warn("Failed to describe topic {} for filtering", topic, e);
                 }
             }

             for (Map.Entry<String, KafkaFuture<ConsumerGroupDescription>> entry : futures.entrySet()) {
                 try {
                     descriptionsMap.put(entry.getKey(), entry.getValue().get());
                 } catch (Exception e) {
                     log.warn("Failed to describe group {}: {}", entry.getKey(), e.getMessage());
                 }
             }
             
             if (StringUtils.hasText(topic)) {
                 final Map<String, ConsumerGroupDescription> finalDescriptionsMap = descriptionsMap;
                 filteredGroupIds = new ArrayList<>();
                 
                 for (String groupId : candidateGroupIds) {
                     boolean match = false;
                     
                     // 1. Check active members
                     ConsumerGroupDescription desc = finalDescriptionsMap.get(groupId);
                     if (desc != null) {
                         if (desc.members().stream()
                                 .anyMatch(m -> m.assignment().topicPartitions().stream()
                                         .anyMatch(tp -> tp.topic().equals(topic)))) {
                             match = true;
                         }
                     }
                     
                     // 2. Check committed offsets
                     if (!match && offsetsFutures.containsKey(groupId)) {
                         try {
                             Map<TopicPartition, OffsetAndMetadata> offsets = offsetsFutures.get(groupId).get();
                             if (offsets != null && offsets.values().stream().anyMatch(Objects::nonNull)) {
                                 match = true;
                             }
                         } catch (Exception e) {
                             // ignore
                         }
                     }
                     
                     if (match) {
                         filteredGroupIds.add(groupId);
                     }
                 }
             }
        }
        
        // 4. Pagination
        int total = filteredGroupIds.size();
        int start = (page - 1) * pageSize;
        if (start >= total) {
            return new PageResult<>(Collections.emptyList(), total, page, pageSize);
        }
        int end = Math.min(start + pageSize, total);
        List<String> pageGroupIds = filteredGroupIds.subList(start, end);
        
        // 5. Enrich details (Lag, etc.) for the page
        List<ConsumerGroupInfo> resultList = new ArrayList<>();
        
        Map<TopicPartition, Long> logEndOffsets = new HashMap<>();
        Map<TopicPartition, Long> logStartOffsets = new HashMap<>();
        
        if (StringUtils.hasText(topic) && !pageGroupIds.isEmpty()) {
            try {
                DescribeTopicsResult describeTopics = admin.describeTopics(Collections.singleton(topic));
                TopicDescription td = describeTopics.all().get().get(topic);
                List<TopicPartition> tps = td.partitions().stream()
                        .map(p -> new TopicPartition(topic, p.partition()))
                        .collect(Collectors.toList());
                
                Map<TopicPartition, OffsetSpec> endSpecs = tps.stream()
                        .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
                Map<TopicPartition, OffsetSpec> startSpecs = tps.stream()
                        .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.earliest()));
                
                var endFuture = admin.listOffsets(endSpecs).all();
                var startFuture = admin.listOffsets(startSpecs).all();
                
                endFuture.get().forEach((tp, info) -> logEndOffsets.put(tp, info.offset()));
                startFuture.get().forEach((tp, info) -> logStartOffsets.put(tp, info.offset()));
            } catch (Exception e) {
                log.warn("Failed to fetch topic info for lag calculation: {}", topic, e);
            }
        }

        // Fire offset requests for all groups in page if not already fetched
        if (StringUtils.hasText(topic) && !pageGroupIds.isEmpty()) {
             for (String groupId : pageGroupIds) {
                 if (!offsetsFutures.containsKey(groupId)) {
                     offsetsFutures.put(groupId, admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());
                 }
             }
        }

        for (String groupId : pageGroupIds) {
            ConsumerGroupDescription desc = descriptionsMap.get(groupId);
            
            // Handle case where description is missing (e.g. auth error)
            if (desc == null) {
                resultList.add(ConsumerGroupInfo.builder()
                        .groupId(groupId)
                        .state("Unknown")
                        .protocolType("Unknown")
                        .coordinator("Unknown")
                        .members(Collections.emptyList())
                        .build());
                continue;
            }
            
            long totalLag = 0;
            long currentOffsetSum = 0;
            long logStartOffsetSum = 0;
            long logEndOffsetSum = 0;
            long consumedSum = 0;
            
            if (StringUtils.hasText(topic) && offsetsFutures.containsKey(groupId)) {
                try {
                    Map<TopicPartition, OffsetAndMetadata> groupOffsets = offsetsFutures.get(groupId).get();
                    
                    for (Map.Entry<TopicPartition, Long> entry : logEndOffsets.entrySet()) {
                        TopicPartition tp = entry.getKey();
                        long logEnd = entry.getValue();
                        long logStart = logStartOffsets.getOrDefault(tp, 0L);
                        
                        logEndOffsetSum += logEnd;
                        logStartOffsetSum += logStart;
                        
                        if (groupOffsets.containsKey(tp)) {
                            long current = groupOffsets.get(tp).offset();
                            if (current <= logEnd) {
                                totalLag += (logEnd - current);
                            }
                            currentOffsetSum += current;
                            if (current > logStart) {
                                consumedSum += (current - logStart);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to get offsets for group {}", groupId, e);
                }
            }

            List<MemberInfo> members = desc.members().stream()
                    .map(m -> MemberInfo.builder()
                            .memberId(m.consumerId())
                            .clientId(m.clientId())
                            .host(m.host())
                            .assignment(m.assignment().topicPartitions().stream().map(TopicPartition::topic).collect(Collectors.toSet()))
                            .build())
                    .collect(Collectors.toList());

            resultList.add(ConsumerGroupInfo.builder()
                    .groupId(groupId)
                    .state(desc.state().toString())
                    .protocolType(desc.partitionAssignor())
                    .coordinator(desc.coordinator().idString())
                    .members(members)
                    .totalLag(StringUtils.hasText(topic) ? totalLag : null)
                    .currentOffset(StringUtils.hasText(topic) ? currentOffsetSum : null)
                    .logStartOffset(StringUtils.hasText(topic) ? logStartOffsetSum : null)
                    .logEndOffset(StringUtils.hasText(topic) ? logEndOffsetSum : null)
                    .topicMessageCount(StringUtils.hasText(topic) ? consumedSum : null)
                    .build());
        }

        return new PageResult<>(resultList, total, page, pageSize);
    }
}
