package com.ruoyi.integration.lab.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Locks the JSON query contract consumed by the RuoYi sidebar. */
class LabReservationMenuQueryMigrationTest
{
    private static final String MIGRATION =
            "db/migration/V6_3__fix_lab_reservation_menu_queries.sql";

    @Test
    void storesReservationMenuQueriesAsJson() throws IOException
    {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(MIGRATION))
        {
            assertThat(input).as(MIGRATION).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql)
                    .contains("menu_id = 2301", "menu_id = 2302",
                            "'{\"mode\":\"mine\"}'", "'{\"mode\":\"approval\"}'")
                    .doesNotContain("'mode=mine'", "'mode=approval'");
        }
    }
}
