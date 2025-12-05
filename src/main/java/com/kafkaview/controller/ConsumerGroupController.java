package com.kafkaview.controller;

import com.kafkaview.model.ConsumerGroupInfo;
import com.kafkaview.model.PageResult;
import com.kafkaview.model.Result;
import com.kafkaview.service.ConsumerGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clusters/{clusterId}/consumer-groups")
@RequiredArgsConstructor
public class ConsumerGroupController {

    private final ConsumerGroupService consumerGroupService;

    @GetMapping
    public Result<PageResult<ConsumerGroupInfo>> list(
            @PathVariable Long clusterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String topic) throws Exception {
        return Result.success(consumerGroupService.listConsumerGroups(clusterId, page, pageSize, keyword, topic));
    }
}
