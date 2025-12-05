package com.kafkaview.controller;

import com.kafkaview.entity.ClusterInfo;
import com.kafkaview.model.Result;
import com.kafkaview.service.ClusterService;
import com.kafkaview.service.KafkaAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clusters")
@RequiredArgsConstructor
@Slf4j
public class ClusterController {

    private final ClusterService clusterService;
    private final KafkaAdminService kafkaAdminService;

    @GetMapping
    public Result<List<ClusterInfo>> list() {
        return Result.success(clusterService.getAllClusters());
    }

    @PostMapping
    public Result<Void> add(@RequestBody ClusterInfo clusterInfo) {
        try {
            // Test connection before adding
            kafkaAdminService.testConnection(clusterInfo);
            clusterService.addCluster(clusterInfo);
            return Result.success();
        } catch (Exception e) {
            log.error("Add cluster failed", e);
            return Result.error("Connection failed: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    @PutMapping
    public Result<Void> update(@RequestBody ClusterInfo clusterInfo) {
        try {
            // Test connection and refresh version info
            kafkaAdminService.testConnection(clusterInfo);
            clusterService.updateCluster(clusterInfo);
            // Invalidate cache
            kafkaAdminService.closeClient(clusterInfo.getId());
            return Result.success();
        } catch (Exception e) {
            log.error("Update cluster failed", e);
            return Result.error("Connection failed: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        clusterService.deleteCluster(id);
        kafkaAdminService.closeClient(id);
        return Result.success();
    }
}
