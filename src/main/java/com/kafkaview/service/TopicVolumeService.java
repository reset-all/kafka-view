package com.kafkaview.service;

import com.kafkaview.mapper.TopicVolumeMapper;
import com.kafkaview.model.TopicVolume;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TopicVolumeService {

    private final KafkaAdminService kafkaAdminService;
    private final TopicVolumeMapper topicVolumeMapper;
    private final ClusterService clusterService;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Snapshot all topics for a cluster once a day at 00:10
    // Calculates the volume for the PREVIOUS day (Yesterday)
    @Scheduled(cron = "0 10 0 * * ?")
    public void dailySnapshotAll() {
        try {
            var clusters = clusterService.getAllClusters();
            if (clusters == null || clusters.isEmpty()) return;
            for (var c : clusters) {
                Long cid = c.getId();
                try {
                    var admin = kafkaAdminService.getAdminClient(cid);
                    var names = admin.listTopics().names().get();
                    if (names != null) {
                        for (String t : names) {
                            // Calculate and save volume for Yesterday
                            calculateAndSaveDailyVolume(cid, t, LocalDate.now().minusDays(1));
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to snapshot cluster {}: {}", cid, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error running dailySnapshotAll", e);
        }
    }

    // Calculates volume for a specific completed day: [Day 00:00, Day+1 00:00)
    public void calculateAndSaveDailyVolume(Long clusterId, String topicName, LocalDate day) {
        try {
            long startTs = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTs = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

            long startOffset = getTotalOffsetAtTime(clusterId, topicName, startTs);
            long endOffset = getTotalOffsetAtTime(clusterId, topicName, endTs);

            long produced = Math.max(0L, endOffset - startOffset);

            TopicVolume v = TopicVolume.builder()
                    .clusterId(clusterId)
                    .topicName(topicName)
                    .day(day.format(DF))
                    .producedCount(produced)
                    .cumulativeOffset(endOffset) // Store end offset of that day
                    .build();

            topicVolumeMapper.upsert(v);
        } catch (Exception e) {
            log.warn("Failed to calculate daily volume for {} {}: {}", topicName, day, e.getMessage());
        }
    }

    // Helper to get total cumulative offset of a topic at a specific timestamp
    private long getTotalOffsetAtTime(Long clusterId, String topicName, long timestamp) throws ExecutionException, InterruptedException {
        AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
        var describe = admin.describeTopics(Collections.singleton(topicName)).all().get();
        var td = describe.get(topicName);
        if (td == null) return 0L;

        List<TopicPartition> tps = td.partitions().stream()
                .map(p -> new TopicPartition(topicName, p.partition()))
                .collect(Collectors.toList());

        // 1. Try to get offsets by timestamp
        Map<TopicPartition, OffsetSpec> specs = new HashMap<>();
        for (TopicPartition tp : tps) {
            specs.put(tp, OffsetSpec.forTimestamp(timestamp));
        }
        
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> timeOffsets = admin.listOffsets(specs).all().get();
        
        // 2. If any partition returns null (meaning timestamp is future relative to log end), fetch Latest
        List<TopicPartition> missingTps = new ArrayList<>();
        long total = 0L;

        for (TopicPartition tp : tps) {
            var info = timeOffsets.get(tp);
            if (info == null || info.offset() == -1L) {
                missingTps.add(tp);
            } else {
                total += info.offset();
            }
        }

        if (!missingTps.isEmpty()) {
            Map<TopicPartition, OffsetSpec> latestSpecs = new HashMap<>();
            for (TopicPartition tp : missingTps) latestSpecs.put(tp, OffsetSpec.latest());
            var latestOffsets = admin.listOffsets(latestSpecs).all().get();
            for (TopicPartition tp : missingTps) {
                var info = latestOffsets.get(tp);
                if (info != null) {
                    total += info.offset();
                }
            }
        }

        return total;
    }

    public List<Long> getTopicVolumes(Long clusterId, String topicName, int days) {
        List<TopicVolume> list = topicVolumeMapper.selectLastDays(clusterId, topicName, days);
        // Map Day -> Volume
        Map<String, Long> map = new HashMap<>();
        for (TopicVolume v : list) {
            map.put(v.getDay(), v.getProducedCount() == null ? 0L : v.getProducedCount());
        }

        List<Long> out = new ArrayList<>();
        // Generate last N days (including today)
        for (int i = days - 1; i >= 0; i--) {
            String d = LocalDate.now().minusDays(i).format(DF);
            out.add(map.getOrDefault(d, 0L));
        }
        return out;
    }

    public Map<String, List<Long>> getTopicsVolumesBatch(Long clusterId, List<String> topics, int days) {
        Map<String, List<Long>> map = new HashMap<>();
        for (String t : topics) {
            map.put(t, getTopicVolumes(clusterId, t, days));
        }
        return map;
    }

    // Backfill past N days
    public void backfillTopicPastDays(Long clusterId, String topicName, int days) {
        if (days <= 0) return;
        
        try {
            AdminClient admin = kafkaAdminService.getAdminClient(clusterId);
            var describe = admin.describeTopics(Collections.singleton(topicName)).all().get();
            var td = describe.get(topicName);
            if (td == null) return;

            List<TopicPartition> tps = td.partitions().stream()
                    .map(p -> new TopicPartition(topicName, p.partition()))
                    .collect(Collectors.toList());

            // We need offsets for: Today+1 00:00 (End of Today), Today 00:00, Yesterday 00:00 ...
            // Range: i from 0 to days.
            // i=0 -> Today. Start: Today 00:00. End: Today+1 00:00.
            // i=days-1 -> Oldest. Start: Today-(days-1) 00:00. End: Today-(days-2) 00:00.
            
            // Collect all timestamps needed
            List<Long> timestamps = new ArrayList<>();
            // Add "End of Today" (Tomorrow 00:00)
            timestamps.add(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            
            for (int i = 0; i < days; i++) {
                LocalDate d = LocalDate.now().minusDays(i);
                timestamps.add(d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            // Now timestamps has days+1 entries.
            // Index 0: Tomorrow 00:00
            // Index 1: Today 00:00
            // Index 2: Yesterday 00:00
            // ...
            
            // Fetch offsets for each timestamp
            Map<Long, Long> timestampToTotalOffset = new HashMap<>();
            
            for (Long ts : timestamps) {
                // 1. Try to get offsets by timestamp
                Map<TopicPartition, OffsetSpec> specs = new HashMap<>();
                for (TopicPartition tp : tps) {
                    specs.put(tp, OffsetSpec.forTimestamp(ts));
                }
                
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> timeOffsets = admin.listOffsets(specs).all().get();
                
                List<TopicPartition> missingTps = new ArrayList<>();
                long total = 0L;

                for (TopicPartition tp : tps) {
                    var info = timeOffsets.get(tp);
                    if (info == null || info.offset() == -1L) {
                        missingTps.add(tp);
                    } else {
                        total += info.offset();
                    }
                }

                if (!missingTps.isEmpty()) {
                    Map<TopicPartition, OffsetSpec> latestSpecs = new HashMap<>();
                    for (TopicPartition tp : missingTps) latestSpecs.put(tp, OffsetSpec.latest());
                    var latestOffsets = admin.listOffsets(latestSpecs).all().get();
                    for (TopicPartition tp : missingTps) {
                        var info = latestOffsets.get(tp);
                        if (info != null) {
                            total += info.offset();
                        }
                    }
                }
                timestampToTotalOffset.put(ts, total);
            }
            
            // Calculate and Upsert
            for (int i = 0; i < days; i++) {
                LocalDate d = LocalDate.now().minusDays(i);
                long startTs = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long endTs = d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                
                long startOffset = timestampToTotalOffset.getOrDefault(startTs, 0L);
                long endOffset = timestampToTotalOffset.getOrDefault(endTs, 0L);
                
                long produced = Math.max(0L, endOffset - startOffset);
                
                TopicVolume v = TopicVolume.builder()
                        .clusterId(clusterId)
                        .topicName(topicName)
                        .day(d.format(DF))
                        .producedCount(produced)
                        .cumulativeOffset(endOffset)
                        .build();
                
                topicVolumeMapper.upsert(v);
            }

        } catch (Exception e) {
            log.warn("Failed to backfill topic volume for {} {}: {}", topicName, clusterId, e.getMessage());
        }
    }

    public void backfillTopicsBatch(Long clusterId, List<String> topics, int days) {
        if (topics == null || topics.isEmpty()) return;
        for (String t : topics) {
            backfillTopicPastDays(clusterId, t, days);
        }
    }
}
