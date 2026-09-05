package com.ruoyi.lab.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import com.ruoyi.lab.security.LabDataScope;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabReservationTraceMapperTest
{
    private Configuration configuration;

    @BeforeEach void parseMapper() throws Exception
    {
        configuration = new Configuration();
        String resource = "mapper/lab/LabReservationTraceMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
        {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }

    @Test void notificationsStayReceiverOnlyAndSentEvenForAdministrator()
    {
        String sql = sql("notifications", Map.of("reservationId", 7L, "receiverId", 1L, "limit", 21));
        assertThat(sql).contains("receiver_id=? and delivery_status='sent'", "limit ?")
                .doesNotContain("repair_order", "usage_record", "or 1=1");
    }

    @Test void hazardsMatchTargetsButRequireTheirOwnScope()
    {
        String sql = sql("hazards", Map.of("deviceId", 8L, "viewerId", 9L,
                "scope", new LabDataScope(9L, false, Set.of()), "limit", 21));
        assertThat(sql).contains("h.target_type='device'", "h.target_type='laboratory'",
                "same_target_context", "and (h.owner_id=? )", "limit ?")
                .doesNotContain("or 1=1");
    }

    @Test void qualificationMatchesCurrentValidityLaboratoryAndCategory()
    {
        String sql = sql("qualificationCount", Map.of("deviceId", 8L, "applicantId", 9L,
                "viewerId", 10L, "scope", new LabDataScope(10L, false, Set.of())));
        assertThat(sql).contains("q.valid_from <= ?", "? < q.valid_until", "q.revoked_at is null",
                "q.laboratory_id=d.laboratory_id", "q.scope_id=d.category_code", "and (?=? )")
                .doesNotContain("or 1=1");
    }

    private String sql(String id, Map<String, Object> values)
    {
        return configuration.getMappedStatement(LabReservationTraceMapper.class.getName() + "." + id)
                .getBoundSql(values).getSql().replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
    }
}
