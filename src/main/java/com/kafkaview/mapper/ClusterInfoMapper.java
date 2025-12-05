package com.kafkaview.mapper;

import com.kafkaview.entity.ClusterInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ClusterInfoMapper {
    
    int insert(ClusterInfo clusterInfo);
    
    int update(ClusterInfo clusterInfo);
    
    int deleteById(Long id);
    
    ClusterInfo selectById(Long id);
    
    List<ClusterInfo> selectAll();
    
    ClusterInfo selectByName(String name);
}
