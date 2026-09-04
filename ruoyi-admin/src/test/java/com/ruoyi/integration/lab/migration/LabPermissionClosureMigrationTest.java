package com.ruoyi.integration.lab.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Locks the additive role grants required by the completed business flows. */
class LabPermissionClosureMigrationTest
{
    private static final String MIGRATION =
            "db/migration/V6_2__lab_workflow_permission_closure.sql";

    @Test
    void grantsAttachmentsRectificationAndFaultReportingToWorkflowActors() throws IOException
    {
        String sql;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION))
        {
            assertThat(input).as(MIGRATION).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("role_key = 'lab_student'", "role_key = 'lab_safety_officer'",
                        "role_key = 'lab_repair_worker'")
                .contains("2223", "2225", "4312", "4400", "4403", "4429")
                .doesNotContain("role_id = 101", "role_id = 102", "role_id = 103");
    }
}
