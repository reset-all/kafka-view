package com.kafkaview.controller;

import com.kafkaview.model.*;
import com.kafkaview.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clusters/{clusterId}/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping("/{topicName}/partitions")
    public Result<PageResult<TopicPartitionDetail>> getPartitions(
            @PathVariable Long clusterId,
            @PathVariable String topicName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) throws Exception {
        return Result.success(topicService.getTopicPartitions(clusterId, topicName, page, pageSize));
    }

    @GetMapping("/{topicName}/configs")
    public Result<List<TopicConfigEntry>> getConfigs(@PathVariable Long clusterId, @PathVariable String topicName) throws Exception {
        return Result.success(topicService.getTopicConfigs(clusterId, topicName));
    }

    @GetMapping("/{topicName}/producers")
    public Result<PageResult<ProducerInfo>> getProducers(
            @PathVariable Long clusterId,
            @PathVariable String topicName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) throws Exception {
        return Result.success(topicService.getTopicProducers(clusterId, topicName, page, pageSize));
    }

    @GetMapping("/{topicName}/messages")
    public Result<MessageSearchResult> getMessages(
            @PathVariable Long clusterId,
            @PathVariable String topicName,
            @RequestParam(required = false) List<Integer> partitions,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) Long startOffset,
            @RequestParam(required = false) Long endOffset,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "timestamp") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "desc") String scanDirection,
            @RequestParam(defaultValue = "1000") int timeout,
            @RequestParam(defaultValue = "10") int retryCount) throws Exception {
        return Result.success(topicService.searchMessages(clusterId, topicName, partitions, startTime, endTime, startOffset, endOffset, key, keyword, limit, page, pageSize, sortField, sortOrder, scanDirection, timeout, retryCount));
    }

    @PutMapping("/{topicName}/configs")
    public Result<Void> updateConfigs(@PathVariable Long clusterId, @PathVariable String topicName, @RequestBody Map<String, String> configs) throws Exception {
        topicService.updateTopicConfig(clusterId, topicName, configs);
        return Result.success();
    }

    @GetMapping
    public Result<PageResult<TopicInfo>> list(
            @PathVariable Long clusterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) throws Exception {
        return Result.success(topicService.listTopics(clusterId, page, pageSize, keyword));
    }

    @PostMapping
    public Result<Void> create(@PathVariable Long clusterId, @RequestBody CreateTopicRequest request) throws Exception {
        topicService.createTopic(clusterId, request);
        return Result.success();
    }

    @DeleteMapping("/{topicName}")
    public Result<Void> delete(@PathVariable Long clusterId, @PathVariable String topicName) throws Exception {
        topicService.deleteTopic(clusterId, topicName);
        return Result.success();
    }

    @PostMapping("/{topicName}/messages")
    public Result<Void> sendMessage(
            @PathVariable Long clusterId,
            @PathVariable String topicName,
            @RequestBody SendMessageRequest request) throws Exception {
        topicService.sendMessage(clusterId, topicName, request.getPartition(), request.getKey(), request.getValue(), request.getCount());
        return Result.success();
    }

    @GetMapping("/{topicName}/messages/history")
    public Result<PageResult<MessageHistory>> getMessageHistory(
            @PathVariable Long clusterId,
            @PathVariable String topicName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(topicService.getMessageHistory(clusterId, topicName, page, pageSize));
    }

    @GetMapping("/{topicName}/volume")
    public Result<List<Long>> getTopicVolume(@PathVariable Long clusterId, @PathVariable String topicName, @RequestParam(defaultValue = "7") int days) {
        // return list of last N days produced counts (oldest -> newest)
        return Result.success(topicService.getTopicVolumeList(clusterId, topicName, days));
    }

    @GetMapping("/volumes")
    public Result<Map<String, List<Long>>> getTopicsVolumeBatch(@PathVariable Long clusterId, @RequestParam List<String> topics, @RequestParam(defaultValue = "7") int days) {
        return Result.success(topicService.getTopicsVolumeBatch(clusterId, topics, days));
    }

    @PostMapping("/volumes/backfill")
    public Result<Void> backfillVolumes(@PathVariable Long clusterId,
                                        @RequestParam(required = false) List<String> topics,
                                        @RequestParam(defaultValue = "7") int days,
                                        @RequestParam(defaultValue = "false") boolean all) {
        // If all=true, fetch all topics and backfill
        try {
            List<String> tlist = topics;
            if (all) {
                // get list of topics via topicService
                PageResult<TopicInfo> page = topicService.listTopics(clusterId, 1, Integer.MAX_VALUE, null);
                tlist = page.getList().stream().map(TopicInfo::getName).collect(java.util.stream.Collectors.toList());
            }
            if (tlist == null || tlist.isEmpty()) return Result.success();
            topicService.backfillTopicVolumes(clusterId, tlist, days);
            return Result.success();
        } catch (Exception e) {
            return Result.error("Backfill failed: " + e.getMessage());
        }
    }
}
