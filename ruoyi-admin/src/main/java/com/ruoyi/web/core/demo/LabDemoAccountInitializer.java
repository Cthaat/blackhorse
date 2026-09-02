package com.ruoyi.web.core.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.system.service.ISysPostService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LabDemoAccountInitializer implements ApplicationRunner
{
    private static final String ACCOUNT_MARKER = "LAB_DEMO_ACCOUNT_V1";

    private static final String ENABLED_VARIABLE = "LAB_DEMO_DATA_ENABLED";
    private static final Profiles PRODUCTION_PROFILE = Profiles.of("prod");
    private static final long DEMO_DEPARTMENT_ID = 103L;
    private static final long SYSTEM_OPERATOR_USER_ID = 9000L;
    private static final long SYSTEM_OPERATOR_CONFIG_ID = 100L;
    private static final String SYSTEM_OPERATOR_CONFIG_KEY = "lab.system.operator-user-id";
    private static final String SYSTEM_OPERATOR_CONFIG_VALUE = "9000";
    private static final String LOCK_SYSTEM_OPERATOR_CONFIG_SQL =
            "select config_value from sys_config where config_id = ? and config_key = ? for update";
    private static final String COUNT_ANY_USER_NAME_SQL =
            "select count(*) from sys_user where user_name = ?";
    private static final String INITIALIZER_NAME = "lab-demo-initializer";
    private static final List<AccountSpec> ACCOUNT_SPECS = List.of(
            new AccountSpec("lab_student", "演示学生", 100L, "lab_student",
                    "LAB_DEMO_STUDENT_PASSWORD"),
            new AccountSpec("lab_manager", "演示实验室管理员", 101L, "lab_manager",
                    "LAB_DEMO_MANAGER_PASSWORD"),
            new AccountSpec("lab_safety_officer", "演示安全员", 102L, "lab_safety_officer",
                    "LAB_DEMO_SAFETY_PASSWORD"),
            new AccountSpec("lab_repair_worker", "演示维修人员", 103L, "lab_repair_worker",
                    "LAB_DEMO_REPAIR_PASSWORD"),
            new AccountSpec("lab_system_admin", "演示系统管理员", 104L, "lab_system_admin",
                    "LAB_DEMO_ADMIN_PASSWORD"));

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final ISysUserService userService;
    private final ISysRoleService roleService;
    private final ISysPostService postService;
    private final BCryptPasswordEncoder passwordEncoder;

    public LabDemoAccountInitializer(Environment environment, JdbcTemplate jdbcTemplate,
            ISysUserService userService, ISysRoleService roleService, ISysPostService postService,
            BCryptPasswordEncoder passwordEncoder)
    {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        this.userService = userService;
        this.roleService = roleService;
        this.postService = postService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments)
    {
        if (!"true".equalsIgnoreCase(environment.getProperty(ENABLED_VARIABLE)))
        {
            return;
        }
        if (environment.acceptsProfiles(PRODUCTION_PROFILE))
        {
            throw new IllegalStateException("Demo data must not be enabled in production.");
        }

        List<String> passwords = readRequiredPasswords();
        lockAndValidateSystemOperatorConfig();
        validateRoles();
        List<PreparedAccount> accounts = preflightAccounts(passwords);
        accounts.forEach(this::applyAccount);
    }

    private List<String> readRequiredPasswords()
    {
        List<String> passwords = new ArrayList<>(ACCOUNT_SPECS.size());
        for (AccountSpec spec : ACCOUNT_SPECS)
        {
            String password = environment.getProperty(spec.passwordEnvironment());
            if (password == null || password.isBlank()
                    || password.length() < UserConstants.PASSWORD_MIN_LENGTH
                    || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
            {
                throw new IllegalStateException(
                        "Required environment variable is missing, blank, or outside allowed length: "
                                + spec.passwordEnvironment());
            }
            passwords.add(password);
        }
        return passwords;
    }

    private void lockAndValidateSystemOperatorConfig()
    {
        List<String> values = jdbcTemplate.queryForList(LOCK_SYSTEM_OPERATOR_CONFIG_SQL, String.class,
                SYSTEM_OPERATOR_CONFIG_ID, SYSTEM_OPERATOR_CONFIG_KEY);
        if (!List.of(SYSTEM_OPERATOR_CONFIG_VALUE).equals(values))
        {
            throw new IllegalStateException(
                    "Required system operator configuration is missing or invalid: "
                            + SYSTEM_OPERATOR_CONFIG_KEY);
        }
    }

    private void validateRoles()
    {
        for (AccountSpec spec : ACCOUNT_SPECS)
        {
            SysRole role = roleService.selectRoleById(spec.roleId());
            if (role == null || !Objects.equals(spec.roleId(), role.getRoleId())
                    || !spec.roleKey().equals(role.getRoleKey())
                    || !"0".equals(role.getStatus()) || !"0".equals(role.getDelFlag()))
            {
                throw new IllegalStateException(
                        "Required demo role mapping is unavailable: " + spec.roleId() + " -> " + spec.roleKey());
            }
        }
    }

    private List<PreparedAccount> preflightAccounts(List<String> passwords)
    {
        List<PreparedAccount> accounts = new ArrayList<>(ACCOUNT_SPECS.size());
        for (int index = 0; index < ACCOUNT_SPECS.size(); index++)
        {
            AccountSpec spec = ACCOUNT_SPECS.get(index);
            SysUser existing = userService.selectUserByUserName(spec.userName());
            if (existing == null)
            {
                Integer hiddenAccountCount = jdbcTemplate.queryForObject(
                        COUNT_ANY_USER_NAME_SQL, Integer.class, spec.userName());
                if (!Integer.valueOf(0).equals(hiddenAccountCount))
                {
                    throw new IllegalStateException(
                            "Refusing to recreate a hidden or deleted account: " + spec.userName());
                }
                accounts.add(new PreparedAccount(spec, passwords.get(index), null, null, null));
                continue;
            }
            if (existing.getUserId() == null || existing.getUserId() == SYSTEM_OPERATOR_USER_ID)
            {
                throw new IllegalStateException(
                        "Demo account identity conflicts with the reserved system operator: " + spec.userName());
            }
            if (!ACCOUNT_MARKER.equals(existing.getRemark()))
            {
                throw new IllegalStateException("Refusing to take over an existing account: " + spec.userName());
            }
            List<Long> roleIds = roleService.selectRoleListByUserId(existing.getUserId());
            List<Long> postIds = postService.selectPostListByUserId(existing.getUserId());
            accounts.add(new PreparedAccount(spec, passwords.get(index), existing, roleIds, postIds));
        }
        return accounts;
    }

    private void applyAccount(PreparedAccount account)
    {
        if (account.existing() == null)
        {
            SysUser user = newUser(account.spec(), passwordEncoder.encode(account.password()));
            requireSingleRow(userService.insertUser(user), "create", account.spec().userName());
            return;
        }

        SysUser existing = account.existing();
        boolean passwordMatches = passwordMatches(account.password(), existing.getPassword());
        boolean roleMatches = List.of(account.spec().roleId()).equals(account.roleIds());
        boolean postsEmpty = account.postIds() != null && account.postIds().isEmpty();
        if (requiresProfileRepair(account.spec(), existing) || !postsEmpty)
        {
            SysUser update = repairUser(account.spec(), existing.getUserId(),
                    passwordMatches ? null : passwordEncoder.encode(account.password()));
            requireSingleRow(userService.updateUser(update), "repair", account.spec().userName());
            return;
        }
        if (!passwordMatches)
        {
            requireSingleRow(userService.resetUserPwd(existing.getUserId(), passwordEncoder.encode(account.password())),
                    "repair password for", account.spec().userName());
        }
        if (!roleMatches)
        {
            userService.insertUserAuth(existing.getUserId(), new Long[] { account.spec().roleId() });
        }
    }

    private boolean passwordMatches(String password, String encodedPassword)
    {
        if (encodedPassword == null || encodedPassword.isBlank())
        {
            return false;
        }
        try
        {
            return passwordEncoder.matches(password, encodedPassword);
        }
        catch (IllegalArgumentException ignored)
        {
            return false;
        }
    }

    private boolean requiresProfileRepair(AccountSpec spec, SysUser user)
    {
        return !spec.userName().equals(user.getUserName())
                || !spec.nickName().equals(user.getNickName())
                || !Objects.equals(DEMO_DEPARTMENT_ID, user.getDeptId())
                || !"0".equals(user.getStatus());
    }

    private SysUser newUser(AccountSpec spec, String encodedPassword)
    {
        SysUser user = new SysUser();
        user.setDeptId(DEMO_DEPARTMENT_ID);
        user.setUserName(spec.userName());
        user.setNickName(spec.nickName());
        user.setEmail("");
        user.setPhonenumber("");
        user.setSex("2");
        user.setAvatar("");
        user.setPassword(encodedPassword);
        user.setStatus("0");
        user.setDelFlag("0");
        user.setCreateBy(INITIALIZER_NAME);
        user.setRemark(ACCOUNT_MARKER);
        user.setRoleIds(new Long[] { spec.roleId() });
        user.setPostIds(new Long[0]);
        return user;
    }

    private SysUser repairUser(AccountSpec spec, Long userId, String encodedPassword)
    {
        SysUser user = new SysUser(userId);
        user.setDeptId(DEMO_DEPARTMENT_ID);
        user.setNickName(spec.nickName());
        user.setPassword(encodedPassword);
        user.setStatus("0");
        user.setUpdateBy(INITIALIZER_NAME);
        user.setRoleIds(new Long[] { spec.roleId() });
        user.setPostIds(new Long[0]);
        return user;
    }

    private void requireSingleRow(int rows, String action, String userName)
    {
        if (rows != 1)
        {
            throw new IllegalStateException("Failed to " + action + " managed demo account: " + userName);
        }
    }

    private record AccountSpec(String userName, String nickName, Long roleId, String roleKey,
            String passwordEnvironment)
    {
    }

    private record PreparedAccount(AccountSpec spec, String password, SysUser existing,
            List<Long> roleIds, List<Long> postIds)
    {
    }
}
