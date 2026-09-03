package com.ruoyi.integration.lab.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabAssetSchemaMigrationIT
{
    private static final Pattern SAFE_URL = Pattern.compile(
            "\\Ajdbc:mysql://(?<host>localhost|127\\.0\\.0\\.1):(?<port>[0-9]{1,5})/"
                    + "(?<database>lab_test_[A-Za-z0-9_]+)(?:\\?[^\\s#]*)?\\z");

    @Test
    void migratesAssetsAndQualificationsFromMilestoneOne() throws Exception
    {
        DatabaseConfig database = databaseOrSkip();
        migrate(database, "1.2");
        migrate(database, "2.0");

        try (Connection connection = database.connect())
        {
            assertThat(tableNames(connection)).contains(
                    "lab_laboratory",
                    "lab_device",
                    "lab_qualification",
                    "lab_attachment",
                    "lab_status_history");
            assertThat(indexNames(connection, "lab_laboratory"))
                    .contains("uk_lab_laboratory_code", "idx_lab_laboratory_scope");
            assertThat(indexNames(connection, "lab_device"))
                    .contains("uk_lab_device_asset_no", "idx_lab_device_query");
            assertThat(indexNames(connection, "lab_qualification"))
                    .contains("idx_lab_qualification_user_validity", "idx_lab_qualification_scope");
            assertThat(indexNames(connection, "lab_attachment"))
                    .contains("idx_lab_attachment_object");
            assertThat(indexNames(connection, "lab_status_history"))
                    .contains("idx_lab_status_history_object");
            assertThat(foreignKeys(connection, "lab_device"))
                    .contains(new ForeignKey("fk_lab_device_laboratory", "laboratory_id",
                            "lab_laboratory", "id"));
            assertThat(migrationHistory(connection)).containsExactly(
                    new Migration("1", true),
                    new Migration("1.1", true),
                    new Migration("1.2", true),
                    new Migration("2", true));

            long laboratoryId = insertLaboratory(connection, "LAB-001");
            assertIntegrityViolation(() -> insertLaboratory(connection, "LAB-001"));

            insertDevice(connection, "ASSET-001", laboratoryId);
            assertIntegrityViolation(() -> insertDevice(connection, "ASSET-001", laboratoryId));
            assertIntegrityViolation(() -> insertDevice(connection, "ASSET-ORPHAN", Long.MAX_VALUE));
        }
    }

    private static void migrate(DatabaseConfig database, String target)
    {
        Flyway.configure()
                .dataSource(database.url(), database.username(), database.password())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(target))
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private static DatabaseConfig databaseOrSkip()
    {
        String marker = environment("LAB_TEST_WRAPPER_ACTIVE");
        Assumptions.assumeTrue(!marker.isBlank(),
                "real database assertions require scripts/run-lab-tests.ps1");
        assertThat(marker).isEqualTo("true");
        assertThat(environment("LAB_TEST_FLYWAY_ENABLED")).isEqualTo("true");

        String url = environment("LAB_TEST_DB_URL");
        Matcher matcher = SAFE_URL.matcher(url);
        assertThat(matcher.matches())
                .as("LAB_TEST_DB_URL identifies an isolated loopback lab_test database")
                .isTrue();
        assertThat(Integer.parseInt(matcher.group("port"))).isBetween(1, 65535);

        String username = environment("LAB_TEST_DB_USERNAME");
        String password = environment("LAB_TEST_DB_PASSWORD");
        assertThat(username).isNotBlank();
        assertThat(password).isNotBlank();
        return new DatabaseConfig(url, username, password);
    }

    private static List<String> tableNames(Connection connection) throws SQLException
    {
        List<String> names = new ArrayList<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rows = metadata.getTables(connection.getCatalog(), null, "lab_%", new String[]{"TABLE"}))
        {
            while (rows.next())
            {
                names.add(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private static List<String> indexNames(Connection connection, String tableName) throws SQLException
    {
        List<String> names = new ArrayList<>();
        try (ResultSet rows = connection.getMetaData()
                .getIndexInfo(connection.getCatalog(), null, tableName, false, false))
        {
            while (rows.next())
            {
                String name = rows.getString("INDEX_NAME");
                if (name != null)
                {
                    names.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return names;
    }

    private static List<ForeignKey> foreignKeys(Connection connection, String tableName) throws SQLException
    {
        List<ForeignKey> keys = new ArrayList<>();
        try (ResultSet rows = connection.getMetaData()
                .getImportedKeys(connection.getCatalog(), null, tableName))
        {
            while (rows.next())
            {
                keys.add(new ForeignKey(
                        lower(rows.getString("FK_NAME")),
                        lower(rows.getString("FKCOLUMN_NAME")),
                        lower(rows.getString("PKTABLE_NAME")),
                        lower(rows.getString("PKCOLUMN_NAME"))));
            }
        }
        return keys;
    }

    private static List<Migration> migrationHistory(Connection connection) throws SQLException
    {
        List<Migration> migrations = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "select version, success from flyway_schema_history order by installed_rank"))
        {
            while (rows.next())
            {
                migrations.add(new Migration(rows.getString("version"), rows.getBoolean("success")));
            }
        }
        return migrations;
    }

    private static long insertLaboratory(Connection connection, String labCode) throws SQLException
    {
        String sql = "insert into lab_laboratory "
                + "(lab_code, name, dept_id, manager_id, location) values (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            statement.setString(1, labCode);
            statement.setString(2, "材料实验室");
            statement.setLong(3, 100L);
            statement.setLong(4, 1L);
            statement.setString(5, "A-101");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys())
            {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private static void insertDevice(Connection connection, String assetNo, long laboratoryId) throws SQLException
    {
        String sql = "insert into lab_device "
                + "(asset_no, laboratory_id, name, category_code, risk_level, location, manager_id) "
                + "values (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, assetNo);
            statement.setLong(2, laboratoryId);
            statement.setString(3, "电子天平");
            statement.setString(4, "MEASURE");
            statement.setString(5, "LOW");
            statement.setString(6, "A-101");
            statement.setLong(7, 1L);
            statement.executeUpdate();
        }
    }

    private static void assertIntegrityViolation(SqlAction action)
    {
        assertThatThrownBy(action::execute)
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23000");
    }

    private static String lower(String value)
    {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String environment(String name)
    {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    @FunctionalInterface
    private interface SqlAction
    {
        void execute() throws SQLException;
    }

    private record DatabaseConfig(String url, String username, String password)
    {
        Connection connect() throws SQLException
        {
            return DriverManager.getConnection(url, username, password);
        }
    }

    private record ForeignKey(String name, String column, String referencedTable, String referencedColumn)
    {
    }

    private record Migration(String version, boolean success)
    {
    }
}
