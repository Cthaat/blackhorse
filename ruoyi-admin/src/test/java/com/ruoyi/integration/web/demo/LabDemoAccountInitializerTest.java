package com.ruoyi.integration.web.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.web.core.demo.LabDemoAccountInitializer;
import com.ruoyi.system.service.ISysPostService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LabDemoAccountInitializerTest
{
    private static final long SYSTEM_OPERATOR_CONFIG_ID = 100L;
    private static final String SYSTEM_OPERATOR_CONFIG_KEY = "lab.system.operator-user-id";
    private static final String SYSTEM_OPERATOR_CONFIG_VALUE = "9000";
    private static final String LOCK_SYSTEM_OPERATOR_CONFIG_SQL =
            "select config_value from sys_config where config_id = ? and config_key = ? for update";
    private static final String COUNT_ANY_USER_NAME_SQL =
            "select count(*) from sys_user where user_name = ?";

    private static final List<ExpectedAccount> ACCOUNTS = List.of(
            new ExpectedAccount("lab_student", "演示学生", 100L, "lab_student",
                    "LAB_DEMO_STUDENT_PASSWORD", "Student#42Pass"),
            new ExpectedAccount("lab_manager", "演示实验室管理员", 101L, "lab_manager",
                    "LAB_DEMO_MANAGER_PASSWORD", "Manager#42Pass"),
            new ExpectedAccount("lab_safety_officer", "演示安全员", 102L, "lab_safety_officer",
                    "LAB_DEMO_SAFETY_PASSWORD", "Safety#42Pass"),
            new ExpectedAccount("lab_repair_worker", "演示维修人员", 103L, "lab_repair_worker",
                    "LAB_DEMO_REPAIR_PASSWORD", "Repair#42Pass"),
            new ExpectedAccount("lab_system_admin", "演示系统管理员", 104L, "lab_system_admin",
                    "LAB_DEMO_ADMIN_PASSWORD", "Admin#42Pass"));

    @Mock
    private Environment environment;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ISysUserService userService;

    @Mock
    private ISysRoleService roleService;

    @Mock
    private ISysPostService postService;

    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp()
    {
        passwordEncoder = spy(new BCryptPasswordEncoder(4));
    }

    @Test
    void disabledInitializerReturnsBeforeProfilesSecretsOrDatabase()
            throws Exception
    {
        when(environment.getProperty("LAB_DEMO_DATA_ENABLED")).thenReturn("false");

        initializer().run(null);

        verify(environment, never()).acceptsProfiles(any(Profiles.class));
        for (ExpectedAccount account : ACCOUNTS)
        {
            verify(environment, never()).getProperty(account.passwordEnvironment());
        }
        verifyNoInteractions(userService, roleService, postService);
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void productionProfileWithEnabledDemoDataFailsBeforeSecretsOrDatabase()
    {
        when(environment.getProperty("LAB_DEMO_DATA_ENABLED")).thenReturn("true");
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        assertThatThrownBy(() -> initializer().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production")
                .hasMessageNotContaining("Password#");

        for (ExpectedAccount account : ACCOUNTS)
        {
            verify(environment, never()).getProperty(account.passwordEnvironment());
        }
        verifyNoInteractions(userService, roleService, postService);
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void defaultProductionProfileWithEnabledDemoDataFailsBeforeSecretsOrDatabase()
    {
        MockEnvironment defaultProduction = spy(new MockEnvironment());
        defaultProduction.setProperty("LAB_DEMO_DATA_ENABLED", "true");
        defaultProduction.setDefaultProfiles("prod");

        assertThatThrownBy(() -> initializer(defaultProduction).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production");

        for (ExpectedAccount account : ACCOUNTS)
        {
            verify(defaultProduction, never()).getProperty(account.passwordEnvironment());
        }
        verifyNoInteractions(userService, roleService, postService);
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(passwordEncoder);
    }

    @ParameterizedTest(name = "rejects blank secret {0}")
    @MethodSource("invalidPasswords")
    void missingOrBlankPasswordFailsBeforeRoleAndUserAccess(String variableName, String invalidValue)
    {
        enableWithValidSecrets();
        when(environment.getProperty(variableName)).thenReturn(invalidValue);

        assertThatThrownBy(() -> initializer().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(variableName)
                .satisfies(error -> rawPasswords().values()
                        .forEach(password -> assertThat(error.getMessage()).doesNotContain(password)));

        verifyNoInteractions(userService, roleService, postService);
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(passwordEncoder);
    }

    @ParameterizedTest(name = "rejects out-of-range secret {0}")
    @MethodSource("outOfRangePasswords")
    void outOfRangePasswordFailsBeforeRoleAndUserAccess(String variableName, String invalidValue)
    {
        enableWithValidSecrets();
        when(environment.getProperty(variableName)).thenReturn(invalidValue);

        assertThatThrownBy(() -> initializer().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(variableName)
                .hasMessageNotContaining(invalidValue)
                .satisfies(error -> rawPasswords().values()
                        .forEach(password -> assertThat(error.getMessage()).doesNotContain(password)));

        verifyNoInteractions(userService, roleService, postService);
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void missingOperatorConfigFailsBeforeRoleOrUserAccess()
    {
        enableWithValidSecrets();
        when(jdbcTemplate.queryForList(LOCK_SYSTEM_OPERATOR_CONFIG_SQL, String.class,
                SYSTEM_OPERATOR_CONFIG_ID, SYSTEM_OPERATOR_CONFIG_KEY)).thenReturn(List.of());

        assertThatThrownBy(() -> initializer().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SYSTEM_OPERATOR_CONFIG_KEY);

        verify(jdbcTemplate).queryForList(LOCK_SYSTEM_OPERATOR_CONFIG_SQL, String.class,
                SYSTEM_OPERATOR_CONFIG_ID, SYSTEM_OPERATOR_CONFIG_KEY);
        verifyNoInteractions(roleService, userService, postService, passwordEncoder);
    }

    @Test
    void wrongOperatorConfigValueFailsBeforeRoleOrUserAccess()
    {
        enableWithValidSecrets();
        when(jdbcTemplate.queryForList(LOCK_SYSTEM_OPERATOR_CONFIG_SQL, String.class,
                SYSTEM_OPERATOR_CONFIG_ID, SYSTEM_OPERATOR_CONFIG_KEY)).thenReturn(List.of("unexpected-user"));

        assertThatThrownBy(() -> initializer().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SYSTEM_OPERATOR_CONFIG_KEY)
                .hasMessageNotContaining("unexpected-user");

        verify(jdbcTemplate).queryForList(LOCK_SYSTEM_OPERATOR_CONFIG_SQL, String.class,
                SYSTEM_OPERATOR_CONFIG_ID, SYSTEM_OPERATOR_CONFIG_KEY);
        verifyNoInteractions(roleService, userService, postService, passwordEncoder);
    }

    @Test
    void invalidRoleMappingFailsBeforeReadingOrWritingUsers()
    {
        enableWithValidSecrets();
        stubValidRoles();
        SysRole mismatchedRole = role(104L, "unexpected_role");
        when(roleService.selectRoleById(104L)).thenReturn(mismatchedRole);

        assertThatThrownBy(() -> initializer().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("104")
                .hasMessageContaining("lab_system_admin")
                .satisfies(error -> rawPasswords().values()
                        .forEach(password -> assertThat(error.getMessage()).doesNotContain(password)));

        verifyNoInteractions(userService, postService);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createsFiveAccountsWithFixedFieldsBcryptOneRoleAndNoPosts()
            throws Exception
    {
        enableWithValidSecrets();
        stubValidRoles();
        when(userService.insertUser(any(SysUser.class))).thenReturn(1);
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);

        initializer().run(null);

        InOrder databaseOrder = inOrder(jdbcTemplate, roleService, userService);
        databaseOrder.verify(jdbcTemplate).queryForList(LOCK_SYSTEM_OPERATOR_CONFIG_SQL, String.class,
                SYSTEM_OPERATOR_CONFIG_ID, SYSTEM_OPERATOR_CONFIG_KEY);
        databaseOrder.verify(roleService).selectRoleById(100L);
        databaseOrder.verify(userService).selectUserByUserName("lab_student");
        verify(userService, times(5)).insertUser(userCaptor.capture());
        Map<String, SysUser> users = new LinkedHashMap<>();
        userCaptor.getAllValues().forEach(user -> users.put(user.getUserName(), user));
        assertThat(users).hasSize(5).doesNotContainKey("__lab_system_operator__");
        for (ExpectedAccount expected : ACCOUNTS)
        {
            SysUser user = users.get(expected.userName());
            assertThat(user).isNotNull();
            assertThat(user.getUserId()).isNull();
            assertThat(user.getNickName()).isEqualTo(expected.nickName());
            assertThat(user.getDeptId()).isEqualTo(103L);
            assertThat(user.getSex()).isEqualTo("2");
            assertThat(user.getStatus()).isEqualTo("0");
            assertThat(user.getDelFlag()).isEqualTo("0");
            assertThat(user.getEmail()).isEmpty();
            assertThat(user.getPhonenumber()).isEmpty();
            assertThat(user.getAvatar()).isEmpty();
            assertThat(user.getCreateBy()).isEqualTo("lab-demo-initializer");
            assertThat(user.getRemark()).isEqualTo("LAB_DEMO_ACCOUNT_V1");
            assertThat(user.getRoleIds()).containsExactly(expected.roleId());
            assertThat(user.getPostIds()).isEmpty();
            assertThat(user.getPassword()).isNotEqualTo(expected.rawPassword());
            assertThat(passwordEncoder.matches(expected.rawPassword(), user.getPassword())).isTrue();
        }
        verify(userService, never()).selectUserById(anyLong());
        verify(userService, never()).resetUserPwd(eq(9000L), anyString());
        verify(userService, never()).insertUserAuth(eq(9000L), any(Long[].class));
        verify(userService, never()).updateUser(argThat(user -> user != null && Long.valueOf(9000L).equals(user.getUserId())));
    }

    @Test
    void nonManagedNameCollisionFailsBeforeTheFirstWrite()
    {
        enableWithValidSecrets();
        stubValidRoles();
        SysUser manualUser = new SysUser(7000L);
        manualUser.setUserName("lab_system_admin");
        manualUser.setRemark("manually-created-account");
        when(userService.selectUserByUserName(anyString()))
                .thenAnswer(invocation -> "lab_system_admin".equals(invocation.getArgument(0, String.class))
                        ? manualUser : null);

        assertThatThrownBy(() -> initializer().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lab_system_admin")
                .hasMessageNotContaining("manually-created-account");

        verifyNoWrites();
        verifyNoInteractions(postService);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void hiddenOrDeletedManagedNameFailsBeforeTheFirstWrite()
    {
        enableWithValidSecrets();
        stubValidRoles();
        when(jdbcTemplate.queryForObject(COUNT_ANY_USER_NAME_SQL, Integer.class, "lab_student"))
                .thenReturn(1);

        assertThatThrownBy(() -> initializer().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to recreate")
                .hasMessageContaining("lab_student");

        verifyNoWrites();
        verifyNoInteractions(postService);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void managedNameCannotEverReferToSystemOperatorId()
    {
        enableWithValidSecrets();
        stubValidRoles();
        SysUser forbiddenUser = managedUser(ACCOUNTS.get(4), 9000L, "irrelevant-hash");
        when(userService.selectUserByUserName(anyString()))
                .thenAnswer(invocation -> "lab_system_admin".equals(invocation.getArgument(0, String.class))
                        ? forbiddenUser : null);

        assertThatThrownBy(() -> initializer().run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reserved system operator");

        verifyNoWrites();
        verifyNoInteractions(postService);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void secondRunDoesNotDuplicateAccountsOrRehashPasswords()
            throws Exception
    {
        enableWithValidSecrets();
        stubValidRoles();
        Map<String, SysUser> users = new HashMap<>();
        Map<Long, List<Long>> assignedRoles = new HashMap<>();
        AtomicLong nextUserId = new AtomicLong(10000L);
        when(userService.selectUserByUserName(anyString()))
                .thenAnswer(invocation -> users.get(invocation.getArgument(0, String.class)));
        when(userService.insertUser(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0, SysUser.class);
            user.setUserId(nextUserId.getAndIncrement());
            users.put(user.getUserName(), user);
            assignedRoles.put(user.getUserId(), List.of(user.getRoleIds()[0]));
            return 1;
        });
        when(roleService.selectRoleListByUserId(anyLong()))
                .thenAnswer(invocation -> assignedRoles.get(invocation.getArgument(0, Long.class)));
        when(postService.selectPostListByUserId(anyLong())).thenReturn(List.of());

        LabDemoAccountInitializer initializer = initializer();
        initializer.run(null);
        initializer.run(null);

        verify(userService, times(5)).insertUser(any(SysUser.class));
        verify(passwordEncoder, times(5)).encode(anyString());
        verify(userService, never()).updateUser(any(SysUser.class));
        verify(userService, never()).resetUserPwd(anyLong(), anyString());
        verify(userService, never()).insertUserAuth(anyLong(), any(Long[].class));
        verify(jdbcTemplate, times(2)).queryForList(LOCK_SYSTEM_OPERATOR_CONFIG_SQL, String.class,
                SYSTEM_OPERATOR_CONFIG_ID, SYSTEM_OPERATOR_CONFIG_KEY);
        assertThat(users).hasSize(5).doesNotContainKey("__lab_system_operator__");
    }

    @Test
    void repairsOnlyTheManagedPasswordRoleAndProfileThatDiffer()
            throws Exception
    {
        enableWithValidSecrets();
        stubValidRoles();
        Map<String, SysUser> users = new HashMap<>();
        Map<Long, List<Long>> assignedRoles = new HashMap<>();
        Map<Long, List<Long>> assignedPosts = new HashMap<>();
        long userId = 11000L;
        for (ExpectedAccount account : ACCOUNTS)
        {
            SysUser user = managedUser(account, userId++, passwordEncoder.encode(account.rawPassword()));
            users.put(account.userName(), user);
            assignedRoles.put(user.getUserId(), List.of(account.roleId()));
            assignedPosts.put(user.getUserId(), List.of());
        }
        SysUser student = users.get("lab_student");
        student.setPassword(passwordEncoder.encode("OldStudent#42"));
        SysUser manager = users.get("lab_manager");
        assignedRoles.put(manager.getUserId(), List.of(999L));
        SysUser safetyOfficer = users.get("lab_safety_officer");
        safetyOfficer.setNickName("过期昵称");
        assignedPosts.put(safetyOfficer.getUserId(), List.of(1L));
        clearInvocations(passwordEncoder);
        when(userService.selectUserByUserName(anyString()))
                .thenAnswer(invocation -> users.get(invocation.getArgument(0, String.class)));
        when(roleService.selectRoleListByUserId(anyLong()))
                .thenAnswer(invocation -> assignedRoles.get(invocation.getArgument(0, Long.class)));
        when(postService.selectPostListByUserId(anyLong()))
                .thenAnswer(invocation -> assignedPosts.get(invocation.getArgument(0, Long.class)));
        when(userService.resetUserPwd(anyLong(), anyString())).thenReturn(1);
        when(userService.updateUser(any(SysUser.class))).thenReturn(1);

        initializer().run(null);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(userService).resetUserPwd(eq(student.getUserId()), passwordCaptor.capture());
        assertThat(passwordEncoder.matches(ACCOUNTS.get(0).rawPassword(), passwordCaptor.getValue())).isTrue();
        verify(userService).insertUserAuth(eq(manager.getUserId()), argThat(roleIds ->
                roleIds != null && roleIds.length == 1 && roleIds[0].equals(101L)));
        ArgumentCaptor<SysUser> updateCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(userService).updateUser(updateCaptor.capture());
        SysUser update = updateCaptor.getValue();
        assertThat(update.getUserId()).isEqualTo(safetyOfficer.getUserId());
        assertThat(update.getNickName()).isEqualTo("演示安全员");
        assertThat(update.getPassword()).isNull();
        assertThat(update.getEmail()).isNull();
        assertThat(update.getPhonenumber()).isNull();
        assertThat(update.getSex()).isNull();
        assertThat(update.getAvatar()).isNull();
        assertThat(update.getDelFlag()).isNull();
        assertThat(update.getRemark()).isNull();
        assertThat(update.getRoleIds()).containsExactly(102L);
        assertThat(update.getPostIds()).isEmpty();
        verify(userService, never()).insertUser(any(SysUser.class));
        verify(userService, times(1)).insertUserAuth(anyLong(), any(Long[].class));
        verify(userService, times(1)).resetUserPwd(anyLong(), anyString());
        verify(userService, times(1)).updateUser(any(SysUser.class));
    }

    @Test
    void unmanagedProfileDifferencesDoNotCauseARepeatedRepair()
            throws Exception
    {
        enableWithValidSecrets();
        stubValidRoles();
        Map<String, SysUser> users = new HashMap<>();
        Map<Long, List<Long>> assignedRoles = new HashMap<>();
        long userId = 12000L;
        for (ExpectedAccount account : ACCOUNTS)
        {
            SysUser user = managedUser(account, userId++, passwordEncoder.encode(account.rawPassword()));
            users.put(account.userName(), user);
            assignedRoles.put(user.getUserId(), List.of(account.roleId()));
        }
        SysUser student = users.get("lab_student");
        student.setEmail("student@example.test");
        student.setPhonenumber("13800000000");
        student.setSex("1");
        student.setAvatar("profile/avatar.png");
        clearInvocations(passwordEncoder);
        when(userService.selectUserByUserName(anyString()))
                .thenAnswer(invocation -> users.get(invocation.getArgument(0, String.class)));
        when(roleService.selectRoleListByUserId(anyLong()))
                .thenAnswer(invocation -> assignedRoles.get(invocation.getArgument(0, Long.class)));
        when(postService.selectPostListByUserId(anyLong())).thenReturn(List.of());

        initializer().run(null);

        verifyNoWrites();
        verify(passwordEncoder, never()).encode(anyString());
        verify(passwordEncoder, times(5)).matches(anyString(), anyString());
    }

    private LabDemoAccountInitializer initializer()
    {
        return initializer(environment);
    }

    private LabDemoAccountInitializer initializer(Environment testEnvironment)
    {
        return new LabDemoAccountInitializer(
                testEnvironment, jdbcTemplate, userService, roleService, postService, passwordEncoder);
    }

    private void enableWithValidSecrets()
    {
        lenient().when(environment.getProperty("LAB_DEMO_DATA_ENABLED")).thenReturn("true");
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[] { "test" });
        lenient().when(jdbcTemplate.queryForList(LOCK_SYSTEM_OPERATOR_CONFIG_SQL, String.class,
                SYSTEM_OPERATOR_CONFIG_ID, SYSTEM_OPERATOR_CONFIG_KEY))
                .thenReturn(List.of(SYSTEM_OPERATOR_CONFIG_VALUE));
        for (ExpectedAccount account : ACCOUNTS)
        {
            lenient().when(environment.getProperty(account.passwordEnvironment())).thenReturn(account.rawPassword());
            lenient().when(jdbcTemplate.queryForObject(
                    COUNT_ANY_USER_NAME_SQL, Integer.class, account.userName())).thenReturn(0);
        }
    }

    private void stubValidRoles()
    {
        for (ExpectedAccount account : ACCOUNTS)
        {
            when(roleService.selectRoleById(account.roleId())).thenReturn(role(account.roleId(), account.roleKey()));
        }
    }

    private static SysRole role(long roleId, String roleKey)
    {
        SysRole role = new SysRole(roleId);
        role.setRoleKey(roleKey);
        role.setStatus("0");
        role.setDelFlag("0");
        return role;
    }

    private SysUser managedUser(ExpectedAccount account, long userId, String encodedPassword)
    {
        SysUser user = new SysUser(userId);
        user.setUserName(account.userName());
        user.setNickName(account.nickName());
        user.setDeptId(103L);
        user.setSex("2");
        user.setStatus("0");
        user.setDelFlag("0");
        user.setEmail("");
        user.setPhonenumber("");
        user.setAvatar("");
        user.setPassword(encodedPassword);
        user.setCreateBy("lab-demo-initializer");
        user.setRemark("LAB_DEMO_ACCOUNT_V1");
        return user;
    }

    private Map<String, String> rawPasswords()
    {
        Map<String, String> passwords = new HashMap<>();
        ACCOUNTS.forEach(account -> passwords.put(account.passwordEnvironment(), account.rawPassword()));
        return passwords;
    }

    private void verifyNoWrites()
    {
        verify(userService, never()).insertUser(any(SysUser.class));
        verify(userService, never()).updateUser(any(SysUser.class));
        verify(userService, never()).resetUserPwd(anyLong(), anyString());
        verify(userService, never()).insertUserAuth(anyLong(), any(Long[].class));
    }

    private static Stream<Arguments> invalidPasswords()
    {
        List<Arguments> values = new ArrayList<>();
        for (ExpectedAccount account : ACCOUNTS)
        {
            values.add(Arguments.of(account.passwordEnvironment(), null));
            values.add(Arguments.of(account.passwordEnvironment(), ""));
            values.add(Arguments.of(account.passwordEnvironment(), "   \t"));
        }
        return values.stream();
    }

    private static Stream<Arguments> outOfRangePasswords()
    {
        List<Arguments> values = new ArrayList<>();
        for (ExpectedAccount account : ACCOUNTS)
        {
            values.add(Arguments.of(account.passwordEnvironment(), "1234"));
            values.add(Arguments.of(account.passwordEnvironment(), "123456789012345678901"));
        }
        return values.stream();
    }

    private record ExpectedAccount(String userName, String nickName, long roleId, String roleKey,
            String passwordEnvironment, String rawPassword)
    {
    }
}
