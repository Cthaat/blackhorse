package com.ruoyi.lab.mapper;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import com.ruoyi.lab.security.LabDataScope;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class LabRestrictionMapperTest
{
    @Test void managerQueryAlwaysContainsLaboratoryScopeAndStatusPredicate()
    {
        Configuration configuration=new Configuration();configuration.addMapper(LabRestrictionMapper.class);
        var query=configuration.getMappedStatement(LabRestrictionMapper.class.getName()+".list");
        String sql=query.getBoundSql(Map.of("scope",new LabDataScope(7L,false,Set.of(2L)),
                "status","ACTIVE","now",LocalDateTime.now())).getSql();
        assertThat(sql).contains("r.laboratory_id IN", "r.revoked_at IS NULL", "r.ends_at>");
    }
}
