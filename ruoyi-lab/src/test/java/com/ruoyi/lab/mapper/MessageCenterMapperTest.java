package com.ruoyi.lab.mapper;

import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MessageCenterMapperTest
{
    @Test void operatorQueryCannotExposePrivatePayloadAndClaimIsFenced()
    {
        Configuration configuration=new Configuration();
        configuration.addMapper(LabMessageDeliveryMapper.class);
        configuration.addMapper(LabMessageTemplateMapper.class);
        configuration.addMapper(LabReservationWaitlistMapper.class);
        String list=sql(configuration,LabMessageDeliveryMapper.class,"list",Map.of("status","MANUAL_REQUIRED","eventType","WAITLIST_OFFERED"));
        assertThat(list).doesNotContain("receiver_id","dedupe_key","title_snapshot","content_snapshot");
        assertThat(list).contains("status=?","event_type=?","ORDER BY id DESC");
        String claim=sql(configuration,LabMessageDeliveryMapper.class,"claim",Map.of());
        assertThat(claim).contains("attempt_count<5","status IN ('PENDING','RETRY_WAIT')","execution_version=execution_version+1");
        String finish=sql(configuration,LabMessageDeliveryMapper.class,"finish",Map.of());
        assertThat(finish).contains("status='PROCESSING' AND execution_version=?");
        String edit=sql(configuration,LabMessageTemplateMapper.class,"edit",Map.of());
        assertThat(edit).contains("status='DRAFT'");
        String transition=sql(configuration,LabReservationWaitlistMapper.class,"transition",Map.of());
        assertThat(transition).contains("offered_until=COALESCE(?,offered_until)");
    }
    private static String sql(Configuration config,Class<?> mapper,String method,Map<String,Object> parameters)
    {return config.getMappedStatement(mapper.getName()+"."+method).getBoundSql(parameters).getSql();}
}
