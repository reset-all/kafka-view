package com.kafkaview.mapper;

import com.kafkaview.model.TopicVolume;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TopicVolumeMapper {

    @Insert("INSERT OR REPLACE INTO topic_volume (cluster_id, topic_name, day, produced_count, cumulative_offset, created_at) VALUES (#{clusterId}, #{topicName}, #{day}, #{producedCount}, #{cumulativeOffset}, CURRENT_TIMESTAMP)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void upsert(TopicVolume v);

    @Select("SELECT * FROM topic_volume WHERE cluster_id = #{clusterId} AND topic_name = #{topicName} ORDER BY day DESC LIMIT #{days}")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "clusterId", column = "cluster_id"),
        @Result(property = "topicName", column = "topic_name"),
        @Result(property = "day", column = "day"),
        @Result(property = "producedCount", column = "produced_count"),
        @Result(property = "cumulativeOffset", column = "cumulative_offset"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<TopicVolume> selectLastDays(@Param("clusterId") Long clusterId, @Param("topicName") String topicName, @Param("days") int days);

    @Select("SELECT * FROM topic_volume WHERE cluster_id = #{clusterId} AND topic_name = #{topicName} ORDER BY day DESC LIMIT 1")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "clusterId", column = "cluster_id"),
        @Result(property = "topicName", column = "topic_name"),
        @Result(property = "day", column = "day"),
        @Result(property = "producedCount", column = "produced_count"),
        @Result(property = "cumulativeOffset", column = "cumulative_offset"),
        @Result(property = "createdAt", column = "created_at")
    })
    TopicVolume selectLatest(@Param("clusterId") Long clusterId, @Param("topicName") String topicName);
}
