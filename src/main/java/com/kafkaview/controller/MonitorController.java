package com.kafkaview.controller;

import com.kafkaview.model.ClusterMetrics;
import com.kafkaview.model.Result;
import com.kafkaview.service.MonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    @GetMapping("/{clusterId}")
    public Result<ClusterMetrics> getMetrics(@PathVariable Long clusterId) {
        return Result.success(monitorService.getMetrics(clusterId));
    }
}
