package com.ruoyi.lab.mapper;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import com.ruoyi.lab.security.LabDataScope;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class LabMaintenanceMapperTest
{
    @Test void emptyManagerScopeFailsClosedAndWindowQueryIsBounded()
    {
        var configuration=new Configuration();configuration.addMapper(LabMaintenanceMapper.class);
        var query=configuration.getMappedStatement(LabMaintenanceMapper.class.getName()+".plans");
        assertThat(query.getBoundSql(Map.of("scope",new LabDataScope(7L,false,Set.of()),"now",LocalDateTime.now(),"soon",LocalDateTime.now())).getSql()).contains("AND 1=0");
        var conflict=configuration.getMappedStatement(LabMaintenanceMapper.class.getName()+".conflicts");
        assertThat(conflict.getBoundSql(Map.of()).getSql()).contains("status='OFFERED'","LIMIT 100","CHECKED_OUT");
    }
}
