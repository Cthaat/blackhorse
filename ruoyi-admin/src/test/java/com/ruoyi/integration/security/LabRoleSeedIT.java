package com.ruoyi.integration.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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

class LabRoleSeedIT
{
    private static final String MIGRATION_RESOURCE =
            "db/migration/V1_2__lab_roles_menus_dictionaries.sql";

    private static final Pattern SAFE_URL = Pattern.compile(
            "\\Ajdbc:mysql://(?<host>localhost|127\\.0\\.0\\.1):(?<port>[0-9]{1,5})/"
                    + "(?<database>lab_test_[A-Za-z0-9_]+)(?:\\?[^\\s#]*)?\\z");

    private static final List<Long> BUSINESS_ROLE_IDS = List.of(100L, 101L, 102L, 103L);

    private static final List<Long> SYSTEM_ADMIN_MENU_IDS = List.of(
            1L, 2L,
            100L, 101L, 102L, 103L, 105L, 106L, 108L, 110L,
            500L, 501L,
            1000L, 1004L,
            1007L, 1011L,
            1012L,
            1016L, 1017L, 1018L, 1019L,
            1025L, 1026L, 1027L, 1028L, 1029L,
            1030L, 1031L, 1032L, 1033L, 1034L,
            1039L, 1040L, 1041L,
            1042L, 1043L, 1044L, 1045L,
            1049L, 1050L, 1051L, 1052L, 1053L, 1054L,
            2000L);

    private static final List<Long> IAM_WRITE_MENU_IDS = List.of(
            1001L, 1002L, 1003L, 1005L, 1006L,
            1008L, 1009L, 1010L,
            1013L, 1014L, 1015L);

    @Test
    void migrationGuardsEveryFixedIdentityBeforePersistentSeedWrites() throws IOException
    {
        String sql = migrationSql();
        String normalized = sql.toUpperCase(Locale.ROOT);

        assertThat(normalized)
                .contains("CREATE TEMPORARY TABLE LAB_V1_2_SEED_GUARD")
                .contains("PRIMARY KEY (GUARD_KEY)")
                .contains("ROLE_ID IN (100, 101, 102, 103, 104)")
                .contains("MENU_ID = 2000")
                .contains("PATH = 'LAB'")
                .contains("USER_ID = 9000")
                .contains("USER_NAME = '__LAB_SYSTEM_OPERATOR__'")
                .contains("CONFIG_ID = 100")
                .contains("CONFIG_KEY = 'LAB.SYSTEM.OPERATOR-USER-ID'")
                .doesNotContain("INSERT IGNORE")
                .doesNotContain("ON DUPLICATE KEY UPDATE")
                .doesNotContain("CHECK (")
                .doesNotContain("LAB_COMMON_STATUS");
        assertThat(normalized).containsPattern(
                "(?s)FROM\\s+SYS_USER_ROLE\\s+WHERE\\s+USER_ID\\s*=\\s*9000\\s+OR\\s+ROLE_ID\\s+IN\\s*"
                        + "\\(100,\\s*101,\\s*102,\\s*103,\\s*104\\)");

        int guardCheck = normalized.indexOf("INSERT INTO LAB_V1_2_SEED_GUARD");
        int firstPersistentWrite = normalized.indexOf("INSERT INTO SYS_ROLE");
        assertThat(guardCheck).isGreaterThanOrEqualTo(0);
        assertThat(firstPersistentWrite).isGreaterThan(guardCheck);
    }

    @Test
    void migrationKeepsSystemAdministratorIamReadOnly() throws IOException
    {
        String sql = migrationSql();
        List<Long> expectedSystemMenus = new ArrayList<>(SYSTEM_ADMIN_MENU_IDS);
        expectedSystemMenus.remove(2000L);

        assertThat(expectedSystemMenuIds(sql))
                .containsExactlyElementsOf(expectedSystemMenus)
                .doesNotContainAnyElementsOf(IAM_WRITE_MENU_IDS);
        assertThat(systemAdministratorMenuIds(sql))
                .containsExactlyInAnyOrderElementsOf(SYSTEM_ADMIN_MENU_IDS)
                .doesNotContainAnyElementsOf(IAM_WRITE_MENU_IDS);
    }

    @Test
    void migrationChecksEachRequiredDictionaryRowExactlyOnce() throws IOException
    {
        String sql = migrationSql();
        int firstDictionaryTable = sql.toLowerCase(Locale.ROOT).indexOf("from sys_dict_data");
        int dictionaryGuardStart = sql.lastIndexOf("(select count(*)", firstDictionaryTable);
        int dictionaryGuardEnd = sql.indexOf(";", dictionaryGuardStart);
        String dictionaryGuard = sql.substring(dictionaryGuardStart, dictionaryGuardEnd);

        assertThat(dictionaryGuard)
                .contains("dict_type = 'sys_normal_disable' and dict_label = '正常' and dict_value = '0'")
                .contains("dict_type = 'sys_normal_disable' and dict_label = '停用' and dict_value = '1'")
                .contains("dict_type = 'sys_yes_no' and dict_label = '是' and dict_value = 'Y'")
                .contains("dict_type = 'sys_yes_no' and dict_label = '否' and dict_value = 'N'");
        assertThat(Pattern.compile(
                "(?is)\\(select\\s+count\\(\\*\\)\\s+from\\s+sys_dict_data\\b.*?\\)\\s*=\\s*1")
                .matcher(dictionaryGuard).results().count()).isEqualTo(4L);
    }

    @Test
    void flywaySeedsExactRolesMenusOperatorConfigAndExistingDictionaries() throws Exception
    {
        DatabaseConfig database = databaseOrSkip();
        migrate(database);

        try (Connection connection = database.connect())
        {
            assertThat(queryLongs(connection,
                    "select count(*) from flyway_schema_history where version = '1.2' and success = 1"))
                    .containsExactly(1L);

            assertThat(queryRoles(connection)).containsExactly(
                    new RoleSeed(100L, "学生", "lab_student", 100, "5", "0", "0"),
                    new RoleSeed(101L, "实验室管理员", "lab_manager", 101, "3", "0", "0"),
                    new RoleSeed(102L, "安全员", "lab_safety_officer", 102, "3", "0", "0"),
                    new RoleSeed(103L, "维修人员", "lab_repair_worker", 103, "5", "0", "0"),
                    new RoleSeed(104L, "系统管理员", "lab_system_admin", 104, "1", "0", "0"));

            assertThat(queryMenu(connection)).containsExactly(
                    new MenuSeed(2000L, "实验室管理", 0L, "lab", "M", "0", "0", ""));

            for (Long roleId : BUSINESS_ROLE_IDS)
            {
                assertThat(queryLongs(connection,
                        "select menu_id from sys_role_menu where role_id = ? order by menu_id", roleId))
                        .as("business role %s has only the lab root", roleId)
                        .containsExactly(2000L);
            }
            assertThat(queryLongs(connection,
                    "select menu_id from sys_role_menu where role_id = 104 order by menu_id"))
                    .containsExactlyElementsOf(SYSTEM_ADMIN_MENU_IDS);

            assertThat(queryLongs(connection,
                    "select count(*) from sys_role_menu rm join sys_menu m on m.menu_id = rm.menu_id "
                            + "where rm.role_id between 100 and 104 and m.menu_type = 'F' and m.perms like 'lab:%'"))
                    .containsExactly(0L);

            assertThat(queryOperator(connection)).containsExactly(
                    new OperatorSeed(9000L, null, "__lab_system_operator__", "实验室系统任务",
                            "00", "2", "!NO_LOGIN!", "1", "0"));
            assertThat(queryLongs(connection,
                    "select count(*) from sys_user_role where user_id = 9000"))
                    .containsExactly(0L);
            assertThat(queryLongs(connection,
                    "select count(*) from sys_user_post where user_id = 9000"))
                    .containsExactly(0L);
            assertThat(queryLongs(connection,
                    "select count(*) from sys_user_role where role_id between 100 and 104"))
                    .containsExactly(0L);

            assertThat(queryConfig(connection)).containsExactly(
                    new ConfigSeed(100L, "实验室系统操作账号", "lab.system.operator-user-id", "9000", "Y"));

            assertThat(queryStrings(connection,
                    "select dict_type from sys_dict_type where dict_type in "
                            + "('sys_normal_disable', 'sys_yes_no') order by dict_type"))
                    .containsExactly("sys_normal_disable", "sys_yes_no");
            assertThat(queryStrings(connection,
                    "select concat(dict_type, ':', dict_value) from sys_dict_data where dict_type in "
                            + "('sys_normal_disable', 'sys_yes_no') order by dict_type, dict_sort"))
                    .containsExactly("sys_normal_disable:0", "sys_normal_disable:1",
                            "sys_yes_no:Y", "sys_yes_no:N");
            assertThat(queryLongs(connection,
                    "select (select count(*) from sys_dict_type where dict_type = 'lab_common_status') "
                            + "+ (select count(*) from sys_dict_data where dict_type = 'lab_common_status')"))
                    .containsExactly(0L);
        }
    }

    @Test
    void fullMigrationRejectsExistingIdentityWithoutChangingPersistentTargets() throws Exception
    {
        DatabaseConfig database = databaseOrSkip();
        assertFullMigrationGuardRejects(database, connection -> executeStatements(connection, """
                insert into sys_role
                  (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
                   dept_check_strictly, status, del_flag)
                values
                  (100, '冲突角色', 'conflict_role', 100, '5', 1, 1, '0', '0');
                """));
    }

    @Test
    void fullMigrationRejectsOrphanRelationshipWithoutChangingPersistentTargets() throws Exception
    {
        DatabaseConfig database = databaseOrSkip();
        assertFullMigrationGuardRejects(database, connection -> executeStatements(connection, """
                insert into sys_user_role (user_id, role_id) values (2, 100);
                """));
    }

    @Test
    void fullMigrationRejectsDriftedBaselineMenuWithoutChangingPersistentTargets() throws Exception
    {
        DatabaseConfig database = databaseOrSkip();
        assertFullMigrationGuardRejects(database, connection -> executeStatements(connection, """
                update sys_menu set path = 'drifted-system' where menu_id = 1;
                """));
    }

    @Test
    void fullMigrationRejectsDriftedDepartmentWithoutChangingPersistentTargets() throws Exception
    {
        DatabaseConfig database = databaseOrSkip();
        assertFullMigrationGuardRejects(database, connection -> executeStatements(connection, """
                update sys_dept set status = '1' where dept_id = 103;
                """));
    }

    @Test
    void fullMigrationRejectsMissingDictionaryRowMaskedByDuplicateWithoutChangingPersistentTargets()
            throws Exception
    {
        DatabaseConfig database = databaseOrSkip();
        assertFullMigrationGuardRejects(database, connection -> executeStatements(connection, """
                delete from sys_dict_data
                 where dict_type = 'sys_yes_no' and dict_label = '否' and dict_value = 'N';
                insert into sys_dict_data
                  (dict_code, dict_sort, dict_label, dict_value, dict_type, css_class,
                   list_class, is_default, status, create_by, create_time, update_by,
                   update_time, remark)
                select 99, dict_sort, dict_label, dict_value, dict_type, css_class,
                       list_class, is_default, status, create_by, create_time, update_by,
                       update_time, remark
                  from sys_dict_data
                 where dict_type = 'sys_normal_disable'
                   and dict_label = '正常'
                   and dict_value = '0';
                """));
    }

    private static void migrate(DatabaseConfig database)
    {
        Flyway.configure()
                .dataSource(database.url(), database.username(), database.password())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1.2"))
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

        String url = environment("LAB_TEST_DB_URL");
        Matcher matcher = SAFE_URL.matcher(url);
        assertThat(matcher.matches())
                .as("LAB_TEST_DB_URL identifies an isolated loopback lab_test database")
                .isTrue();
        int port = Integer.parseInt(matcher.group("port"));
        assertThat(port).isBetween(1, 65535);

        String username = environment("LAB_TEST_DB_USERNAME");
        String password = environment("LAB_TEST_DB_PASSWORD");
        assertThat(username).isNotBlank();
        assertThat(password).isNotBlank();
        assertThat(environment("LAB_TEST_FLYWAY_ENABLED")).isEqualTo("true");
        return new DatabaseConfig(url, username, password);
    }

    private static String migrationSql() throws IOException
    {
        try (InputStream input = LabRoleSeedIT.class.getClassLoader().getResourceAsStream(MIGRATION_RESOURCE))
        {
            assertThat(input).as("Task 7 Flyway migration resource").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<Long> expectedSystemMenuIds(String sql)
    {
        int start = sql.indexOf("insert into lab_v1_2_expected_system_menu");
        int end = sql.indexOf("insert into lab_v1_2_seed_guard", start + 1);
        return rowIds(sql.substring(start, end),
                Pattern.compile("(?m)^\\s*\\((?<id>\\d+),"), "id");
    }

    private static List<Long> systemAdministratorMenuIds(String sql)
    {
        int start = sql.indexOf("insert into sys_role_menu");
        Pattern row = Pattern.compile("(?m)^\\s*\\(104,\\s*(?<id>\\d+)\\)[,;]");
        return rowIds(sql.substring(start), row, "id");
    }

    private static List<Long> rowIds(String sql, Pattern row, String group)
    {
        List<Long> ids = new ArrayList<>();
        Matcher matcher = row.matcher(sql);
        while (matcher.find())
        {
            ids.add(Long.parseLong(matcher.group(group)));
        }
        return ids;
    }

    private static void assertFullMigrationGuardRejects(DatabaseConfig database, DatabaseMutation mutation)
            throws Exception
    {
        migrate(database);
        try (Connection connection = database.connect())
        {
            connection.setAutoCommit(false);
            try
            {
                removePersistentSeed(connection);
                mutation.apply(connection);
                SeedFingerprint before = fingerprint(connection);

                assertThatThrownBy(() -> executeStatements(connection, migrationSql()))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("PRIMARY");
                assertThat(fingerprint(connection)).isEqualTo(before);
            }
            finally
            {
                connection.rollback();
            }
        }
    }

    private static void removePersistentSeed(Connection connection) throws SQLException
    {
        executeStatements(connection, """
                delete from sys_role_menu
                 where role_id in (100, 101, 102, 103, 104) or menu_id = 2000;
                delete from sys_role_dept where role_id in (100, 101, 102, 103, 104);
                delete from sys_user_role
                 where user_id = 9000 or role_id in (100, 101, 102, 103, 104);
                delete from sys_user_post where user_id = 9000;
                delete from sys_role
                 where role_id in (100, 101, 102, 103, 104)
                    or role_key in ('lab_student', 'lab_manager', 'lab_safety_officer',
                                    'lab_repair_worker', 'lab_system_admin');
                delete from sys_menu where menu_id = 2000 or path = 'lab';
                delete from sys_user where user_id = 9000 or user_name = '__lab_system_operator__';
                delete from sys_config
                 where config_id = 100 or config_key = 'lab.system.operator-user-id';
                """);
    }

    private static List<RoleSeed> queryRoles(Connection connection) throws SQLException
    {
        String sql = "select role_id, role_name, role_key, role_sort, data_scope, status, del_flag "
                + "from sys_role where role_id between 100 and 104 or role_key in "
                + "('lab_student', 'lab_manager', 'lab_safety_officer', 'lab_repair_worker', 'lab_system_admin') "
                + "order by role_id";
        List<RoleSeed> roles = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            while (rows.next())
            {
                roles.add(new RoleSeed(rows.getLong("role_id"), rows.getString("role_name"),
                        rows.getString("role_key"), rows.getInt("role_sort"), rows.getString("data_scope"),
                        rows.getString("status"), rows.getString("del_flag")));
            }
        }
        return roles;
    }

    private static List<MenuSeed> queryMenu(Connection connection) throws SQLException
    {
        String sql = "select menu_id, menu_name, parent_id, path, menu_type, visible, status, perms "
                + "from sys_menu where menu_id = 2000 or path = 'lab' order by menu_id";
        List<MenuSeed> menus = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            while (rows.next())
            {
                menus.add(new MenuSeed(rows.getLong("menu_id"), rows.getString("menu_name"),
                        rows.getLong("parent_id"), rows.getString("path"), rows.getString("menu_type"),
                        rows.getString("visible"), rows.getString("status"), rows.getString("perms")));
            }
        }
        return menus;
    }

    private static List<OperatorSeed> queryOperator(Connection connection) throws SQLException
    {
        String sql = "select user_id, dept_id, user_name, nick_name, user_type, sex, password, status, del_flag "
                + "from sys_user where user_id = 9000 or user_name = '__lab_system_operator__' order by user_id";
        List<OperatorSeed> users = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            while (rows.next())
            {
                Long deptId = rows.getObject("dept_id", Long.class);
                users.add(new OperatorSeed(rows.getLong("user_id"), deptId, rows.getString("user_name"),
                        rows.getString("nick_name"), rows.getString("user_type"), rows.getString("sex"),
                        rows.getString("password"), rows.getString("status"), rows.getString("del_flag")));
            }
        }
        return users;
    }

    private static List<ConfigSeed> queryConfig(Connection connection) throws SQLException
    {
        String sql = "select config_id, config_name, config_key, config_value, config_type from sys_config "
                + "where config_id = 100 or config_key = 'lab.system.operator-user-id' order by config_id";
        List<ConfigSeed> configs = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql))
        {
            while (rows.next())
            {
                configs.add(new ConfigSeed(rows.getLong("config_id"), rows.getString("config_name"),
                        rows.getString("config_key"), rows.getString("config_value"),
                        rows.getString("config_type")));
            }
        }
        return configs;
    }

    private static List<Long> queryLongs(Connection connection, String sql, Object... parameters)
            throws SQLException
    {
        List<Long> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            bind(statement, parameters);
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    values.add(rows.getLong(1));
                }
            }
        }
        return values;
    }

    private static List<String> queryStrings(Connection connection, String sql, Object... parameters)
            throws SQLException
    {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            bind(statement, parameters);
            try (ResultSet rows = statement.executeQuery())
            {
                while (rows.next())
                {
                    values.add(rows.getString(1));
                }
            }
        }
        return values;
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException
    {
        for (int index = 0; index < parameters.length; index++)
        {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private static SeedFingerprint fingerprint(Connection connection) throws SQLException
    {
        return new SeedFingerprint(queryLongs(connection, """
                select count(*) from sys_role
                 where role_id in (100, 101, 102, 103, 104)
                    or role_key in ('lab_student', 'lab_manager', 'lab_safety_officer',
                                    'lab_repair_worker', 'lab_system_admin')
                union all
                select count(*) from sys_menu where menu_id = 2000 or path = 'lab'
                union all
                select count(*) from sys_user
                 where user_id = 9000 or user_name = '__lab_system_operator__'
                union all
                select count(*) from sys_config
                 where config_id = 100 or config_key = 'lab.system.operator-user-id'
                union all
                select count(*) from sys_role_menu
                 where role_id in (100, 101, 102, 103, 104) or menu_id = 2000
                union all
                select count(*) from sys_role_dept where role_id in (100, 101, 102, 103, 104)
                union all
                select count(*) from sys_user_role
                 where user_id = 9000 or role_id in (100, 101, 102, 103, 104)
                union all
                select count(*) from sys_user_post where user_id = 9000
                """));
    }

    private static void executeStatements(Connection connection, String sql) throws SQLException
    {
        String withoutComments = sql.replaceAll("(?m)^\\s*--.*(?:\\R|$)", "");
        try (Statement statement = connection.createStatement())
        {
            for (String fragment : withoutComments.split(";"))
            {
                if (!fragment.isBlank())
                {
                    statement.execute(fragment);
                }
            }
        }
    }

    private static String environment(String name)
    {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private record DatabaseConfig(String url, String username, String password)
    {
        private Connection connect() throws SQLException
        {
            return DriverManager.getConnection(url, username, password);
        }
    }

    private record RoleSeed(long id, String name, String key, int sort, String dataScope,
                            String status, String delFlag)
    {
    }

    private record MenuSeed(long id, String name, long parentId, String path, String type,
                            String visible, String status, String permissions)
    {
    }

    private record OperatorSeed(long id, Long departmentId, String username, String nickname,
                                String userType, String sex, String password, String status, String delFlag)
    {
    }

    private record ConfigSeed(long id, String name, String key, String value, String type)
    {
    }

    @FunctionalInterface
    private interface DatabaseMutation
    {
        void apply(Connection connection) throws SQLException;
    }

    private record SeedFingerprint(List<Long> rowCounts)
    {
    }
}
