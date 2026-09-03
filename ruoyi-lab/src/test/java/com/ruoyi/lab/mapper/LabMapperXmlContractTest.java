package com.ruoyi.lab.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LabQualification;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.service.LabSortWhitelist;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LabMapperXmlContractTest
{
    private static final List<String> MAPPER_RESOURCES = List.of(
            "mapper/lab/LabLaboratoryMapper.xml",
            "mapper/lab/LabDeviceMapper.xml",
            "mapper/lab/LabQualificationMapper.xml",
            "mapper/lab/LabDataScopeMapper.xml",
            "mapper/lab/LabDictionaryMapper.xml");

    private Configuration configuration;

    @BeforeEach
    void parseMappers() throws IOException
    {
        configuration = new Configuration();
        for (String resource : MAPPER_RESOURCES)
        {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
            {
                assertThat(input).as(resource).isNotNull();
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
    }

    @Test
    void exposesRequiredPersistenceStatements()
    {
        assertThat(configuration.getMappedStatementNames()).contains(
                statement(LabLaboratoryMapper.class, "selectListByScope"),
                statement(LabLaboratoryMapper.class, "selectByIdInScope"),
                statement(LabLaboratoryMapper.class, "selectByIdForUpdate"),
                statement(LabLaboratoryMapper.class, "updateDetailsConditionally"),
                statement(LabDeviceMapper.class, "selectListByScope"),
                statement(LabDeviceMapper.class, "selectByIdInScope"),
                statement(LabDeviceMapper.class, "selectByIdForUpdate"),
                statement(LabDeviceMapper.class, "updateDetailsConditionally"),
                statement(LabDeviceMapper.class, "updateStatusConditionally"),
                statement(LabQualificationMapper.class, "selectMine"),
                statement(LabQualificationMapper.class, "selectListByScope"),
                statement(LabQualificationMapper.class, "selectActiveById"),
                statement(LabQualificationMapper.class, "selectByIdForUpdate"),
                statement(LabQualificationMapper.class, "updateDetailsConditionally"),
                statement(LabQualificationMapper.class, "revokeConditionally"),
                statement(LabQualificationMapper.class, "countValidForDevice"),
                statement(LabDataScopeMapper.class, "hasAllLaboratoryScope"),
                statement(LabDataScopeMapper.class, "selectScopedLaboratoryIds"),
                statement(LabDictionaryMapper.class, "countEnabledValue"));
    }

    @Test
    void versionedDetailUpdatesAreFailClosed()
    {
        LabLaboratory laboratory = new LabLaboratory();
        laboratory.setId(10L);
        LabDevice device = new LabDevice();
        device.setId(20L);
        LabQualification qualification = new LabQualification();
        qualification.setId(30L);

        String laboratorySql = sql(statement(LabLaboratoryMapper.class, "updateDetailsConditionally"),
                params("laboratory", laboratory, "expectedVersion", 2));
        String deviceSql = sql(statement(LabDeviceMapper.class, "updateDetailsConditionally"),
                params("device", device, "expectedVersion", 3));
        String qualificationSql = sql(statement(LabQualificationMapper.class, "updateDetailsConditionally"),
                params("qualification", qualification, "expectedVersion", 4));
        String revokeSql = sql(statement(LabQualificationMapper.class, "revokeConditionally"),
                params("qualificationId", 30L, "expectedVersion", 4,
                        "revokedAt", LocalDateTime.of(2026, 9, 3, 12, 0),
                        "revokeReason", "资格撤销", "updateBy", "reviewer"));

        for (String updateSql : List.of(laboratorySql, deviceSql, qualificationSql))
        {
            assertThat(updateSql)
                    .contains("version = version + 1", "version = ?", "del_flag = '0'", "update_by = ?")
                    .doesNotContain("set status =", "del_flag = ?");
        }
        assertThat(revokeSql).contains(
                "revoked_at = ?", "revoke_reason = ?", "update_by = ?",
                "version = version + 1", "version = ?", "del_flag = '0'", "revoked_at is null");
    }

    @Test
    void scopedQueriesFailClosedAndUseValidatedSortClause()
    {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("scope", new LabDataScope(7L, false, Set.of()));
        parameters.put("laboratoryId", null);
        parameters.put("categoryCode", null);
        parameters.put("status", null);
        parameters.put("keyword", null);
        parameters.put("sort", new LabSortWhitelist().resolve("device", "name", "asc"));

        String sql = sql(statement(LabDeviceMapper.class, "selectListByScope"), parameters);

        assertThat(sql).contains("d.del_flag = '0'", "and 1 = 0", "order by d.name asc");
    }

    @Test
    void qualificationCoverageUsesHalfOpenValidityAndBothScopeTypes()
    {
        String sql = sql(statement(LabQualificationMapper.class, "countValidForDevice"),
                params("userId", 7L, "deviceId", 20L,
                        "at", LocalDateTime.of(2026, 9, 3, 12, 0)));

        assertThat(sql).contains(
                "q.del_flag = '0'",
                "d.del_flag = '0'",
                "q.valid_from <= ?",
                "? < q.valid_until",
                "q.revoked_at is null",
                "q.scope_type = 'laboratory'",
                "q.scope_type = 'device_category'");
    }

    @Test
    void dictionaryLookupUsesBoundValuesAndEnabledRows()
    {
        String sql = sql(statement(LabDictionaryMapper.class, "countEnabledValue"),
                params("dictType", "lab_device_category", "dictValue", "MICROSCOPE"));

        assertThat(sql)
                .contains("t.status = '0'", "d.status = '0'", "t.dict_type = ?", "d.dict_value = ?")
                .doesNotContain("${");
    }

    @Test
    void categoryQualificationManagementRequiresOnlyANonEmptyScope()
    {
        String sql = sql(statement(LabQualificationMapper.class, "selectListByScope"),
                params("scope", new LabDataScope(7L, false, Set.of(10L)),
                        "userId", null, "scopeType", null,
                        "sort", new LabSortWhitelist().resolve("qualification", "createTime", "desc")));

        assertThat(sql)
                .contains("q.scope_type = 'device_category'")
                .doesNotContain("from lab_device scoped_device");
    }

    private String sql(String statementId, Map<String, Object> parameters)
    {
        MappedStatement statement = configuration.getMappedStatement(statementId);
        return statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ")
                .trim().toLowerCase(Locale.ROOT);
    }

    private static String statement(Class<?> mapperType, String id)
    {
        return mapperType.getName() + "." + id;
    }

    private static Map<String, Object> params(Object... entries)
    {
        Map<String, Object> parameters = new HashMap<>();
        for (int index = 0; index < entries.length; index += 2)
        {
            parameters.put((String) entries[index], entries[index + 1]);
        }
        return parameters;
    }
}
