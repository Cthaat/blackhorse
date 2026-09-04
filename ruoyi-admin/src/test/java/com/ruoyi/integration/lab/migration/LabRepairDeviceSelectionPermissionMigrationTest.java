package com.ruoyi.integration.lab.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Locks the device-list grant required by the repair report form. */
class LabRepairDeviceSelectionPermissionMigrationTest
{
    private static final String MIGRATION =
            "db/migration/V6_4__grant_repair_worker_device_selection.sql";

    @Test
    void grantsExistingDeviceListMenuToRepairWorker() throws IOException
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION))
        {
            assertThat(input).as(MIGRATION).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql)
                    .contains("role_key = 'lab_repair_worker'", "menu_id = 2202", ", 2202)")
                    .doesNotContain("role_id = 103");
        }
    }
}
