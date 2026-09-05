package com.ruoyi.lab.mapper;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.domain.LabMessageTemplate;
import org.apache.ibatis.annotations.*;

public interface LabMessageTemplateMapper
{
    String COLUMNS = "id,event_type AS eventType,title,content,status,operator_id AS operatorId,create_time AS createTime,publish_time AS publishTime";
    @Select("<script>SELECT " + COLUMNS + " FROM lab_message_template WHERE 1=1 <if test='eventType != null and eventType != &quot;&quot;'>AND event_type=#{eventType}</if> ORDER BY id DESC</script>")
    List<LabMessageTemplate> list(String eventType);
    @Select("SELECT " + COLUMNS + " FROM lab_message_template WHERE id=#{id}") LabMessageTemplate byId(Long id);
    @Select("SELECT " + COLUMNS + " FROM lab_message_template WHERE event_type=#{eventType} AND status='PUBLISHED' ORDER BY publish_time DESC,id DESC LIMIT 1") LabMessageTemplate active(String eventType);
    @Insert("INSERT INTO lab_message_template(event_type,title,content,status,operator_id,create_time) VALUES(#{eventType},#{title},#{content},'DRAFT',#{operatorId},#{createTime})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(LabMessageTemplate row);
    @Update("UPDATE lab_message_template SET title=#{title},content=#{content},operator_id=#{operatorId} WHERE id=#{id} AND status='DRAFT' AND event_type=#{eventType}") int edit(LabMessageTemplate row);
    @Update("UPDATE lab_message_template SET status='PUBLISHED',publish_time=#{now},operator_id=#{operator} WHERE id=#{id} AND status='DRAFT'")
    int publish(@Param("id") Long id,@Param("operator") Long operator,@Param("now") LocalDateTime now);
    @Select("SELECT optional_reminders FROM lab_notification_preference WHERE user_id=#{id}") Boolean preference(Long id);
    @Insert("INSERT INTO lab_notification_preference(user_id,optional_reminders,update_time) VALUES(#{id},#{enabled},#{now}) ON DUPLICATE KEY UPDATE optional_reminders=#{enabled},update_time=#{now}")
    int preferenceUpdate(@Param("id") Long id,@Param("enabled") boolean enabled,@Param("now") LocalDateTime now);
}
