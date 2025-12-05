package com.kafkaview.mapper;

import com.kafkaview.model.MessageHistory;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MessageHistoryMapper {

    @Insert("INSERT INTO message_history (cluster_id, topic_name, partition_id, key_content, value_content, created_at) " +
            "VALUES (#{clusterId}, #{topicName}, #{partitionId}, #{keyContent}, #{valueContent}, CURRENT_TIMESTAMP)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(MessageHistory history);

    @Select("SELECT * FROM message_history WHERE cluster_id = #{clusterId} AND topic_name = #{topicName} ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "clusterId", column = "cluster_id"),
        @Result(property = "topicName", column = "topic_name"),
        @Result(property = "partitionId", column = "partition_id"),
        @Result(property = "keyContent", column = "key_content"),
        @Result(property = "valueContent", column = "value_content"),
        @Result(property = "createdAt", column = "created_at")
    })
    List<MessageHistory> selectByTopic(@Param("clusterId") Long clusterId, @Param("topicName") String topicName, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM message_history WHERE cluster_id = #{clusterId} AND topic_name = #{topicName}")
    int countByTopic(@Param("clusterId") Long clusterId, @Param("topicName") String topicName);

    @Delete("DELETE FROM message_history WHERE cluster_id = #{clusterId} AND topic_name = #{topicName} AND id NOT IN (SELECT id FROM message_history WHERE cluster_id = #{clusterId} AND topic_name = #{topicName} ORDER BY created_at DESC LIMIT 100)")
    void deleteOldRecords(@Param("clusterId") Long clusterId, @Param("topicName") String topicName);
}
