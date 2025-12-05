package com.kafkaview.service;

import com.kafkaview.entity.ClusterInfo;
import com.kafkaview.mapper.ClusterInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClusterService {

    private final ClusterInfoMapper clusterInfoMapper;

    public List<ClusterInfo> getAllClusters() {
        return clusterInfoMapper.selectAll();
    }

    public ClusterInfo getClusterById(Long id) {
        return clusterInfoMapper.selectById(id);
    }

    @Transactional
    public void addCluster(ClusterInfo clusterInfo) {
        if (clusterInfoMapper.selectByName(clusterInfo.getName()) != null) {
            throw new IllegalArgumentException("Cluster name already exists: " + clusterInfo.getName());
        }
        clusterInfoMapper.insert(clusterInfo);
    }

    @Transactional
    public void updateCluster(ClusterInfo clusterInfo) {
        clusterInfoMapper.update(clusterInfo);
    }

    @Transactional
    public void deleteCluster(Long id) {
        clusterInfoMapper.deleteById(id);
    }
}
