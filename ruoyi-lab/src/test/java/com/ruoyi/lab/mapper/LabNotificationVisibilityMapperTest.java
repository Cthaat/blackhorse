package com.ruoyi.lab.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabNotificationVisibilityMapperTest
{
    private Configuration configuration;

    @BeforeEach
    void parseMapper() throws Exception
    {
        configuration = new Configuration();
        String resource = "mapper/lab/LabNotificationMapper.xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
        {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void currentUserQueriesAndReadMarkerHideFailedNotifications()
    {
        assertThat(sql("selectMineById", Map.of("notificationId", 1L, "receiverId", 7L)))
                .contains("delivery_status = 'sent'");
        assertThat(sql("selectMine", Map.of("receiverId", 7L, "unreadOnly", false)))
                .contains("delivery_status = 'sent'");
        assertThat(sql("markReadMine", Map.of("notificationId", 1L, "receiverId", 7L,
                "readAt", java.time.LocalDateTime.of(2026, 9, 3, 12, 0))))
                .contains("delivery_status = 'sent'", "read_at is null");
    }

    private String sql(String id, Map<String, Object> parameters)
    {
        MappedStatement statement = configuration.getMappedStatement(
                LabNotificationMapper.class.getName() + "." + id);
        return statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ")
                .trim().toLowerCase(java.util.Locale.ROOT);
    }
}
