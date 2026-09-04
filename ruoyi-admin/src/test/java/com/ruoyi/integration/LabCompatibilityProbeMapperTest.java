package com.ruoyi.integration;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ruoyi.lab.domain.LabCompatibilityProbe;
import com.ruoyi.lab.mapper.LabCompatibilityProbeMapper;
import com.ruoyi.quartz.service.ISysJobService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysDictTypeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Sql("/sql/mybatis-plus-probe.sql")
@ExtendWith(LabCompatibilityProbeMapperTest.SafeLabDatabaseCondition.class)
class LabCompatibilityProbeMapperTest
{
    @Autowired
    private LabCompatibilityProbeMapper probeMapper;

    @MockitoBean
    private ISysConfigService configService;

    @MockitoBean
    private ISysDictTypeService dictTypeService;

    @MockitoBean
    private ISysJobService jobService;

    @Test
    void baseMapperSelectByIdMapsProbeEntity()
    {
        LabCompatibilityProbe probe = probeMapper.selectById(3L);

        assertThat(probe).isNotNull();
        assertThat(probe.getId()).isEqualTo(3L);
        assertThat(probe.getProbeName()).isEqualTo("probe-charlie");
        assertThat(probe.getSortOrder()).isEqualTo(30);
    }

    @Test
    void pageHelperPaginatesTheMapperQuery()
    {
        PageHelper.startPage(2, 2);
        List<LabCompatibilityProbe> probes = probeMapper.selectOrdered();
        PageInfo<LabCompatibilityProbe> page = new PageInfo<>(probes);

        assertThat(page.getTotal()).isEqualTo(5L);
        assertThat(page.getPageNum()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getPages()).isEqualTo(3);
        assertThat(page.getList())
                .extracting(LabCompatibilityProbe::getId)
                .containsExactly(3L, 4L);
        assertThat(page.getList())
                .extracting(LabCompatibilityProbe::getProbeName)
                .containsExactly("probe-charlie", "probe-delta");
    }

    static final class SafeLabDatabaseCondition implements ExecutionCondition
    {
        private static final Pattern SAFE_URL = Pattern.compile(
                "\\Ajdbc:mysql://(?<host>localhost|127\\.0\\.0\\.1):(?<port>[0-9]{1,5})/"
                        + "(?<database>lab_test_[A-Za-z0-9_]+)(?:\\?[^\\s#]*)?\\z");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context)
        {
            String wrapperMarker = System.getenv("LAB_TEST_WRAPPER_ACTIVE");
            if (wrapperMarker == null || wrapperMarker.trim().isEmpty())
            {
                return disabled("real database tests require the safety wrapper");
            }
            require("true".equals(wrapperMarker),
                    "LAB_TEST_WRAPPER_ACTIVE must be exactly true");

            Matcher matcher = SAFE_URL.matcher(environment("LAB_TEST_DB_URL"));
            require(matcher.matches(),
                    "LAB_TEST_DB_URL must identify an isolated loopback lab_test database");

            int port = Integer.parseInt(matcher.group("port"));
            require(port >= 1 && port <= 65535,
                    "LAB_TEST_DB_URL must contain a valid TCP port");
            require(!isBlank(environment("LAB_TEST_DB_USERNAME"))
                            && !isBlank(environment("LAB_TEST_DB_PASSWORD")),
                    "LAB_TEST_DB credentials must be configured");
            require("true".equals(environment("LAB_TEST_FLYWAY_ENABLED")),
                    "LAB_TEST_FLYWAY_ENABLED must be exactly true");
            return ConditionEvaluationResult.enabled("isolated lab test database is configured");
        }

        private static ConditionEvaluationResult disabled(String reason)
        {
            return ConditionEvaluationResult.disabled(reason);
        }

        private static String environment(String name)
        {
            String value = System.getenv(name);
            return value == null ? "" : value;
        }

        private static void require(boolean valid, String reason)
        {
            if (!valid)
            {
                throw new IllegalStateException(reason);
            }
        }

        private static boolean isBlank(String value)
        {
            return value.trim().isEmpty();
        }
    }
}
