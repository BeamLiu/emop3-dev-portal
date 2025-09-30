package io.emop.integrationtest.util;

import io.emop.model.auth.User;
import io.emop.model.auth.UserPermissions;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * 测试用户辅助工具类
 * 统一管理测试用户的创建、权限设置和清理
 */
@Slf4j
public class TestUserHelper {

    // 测试用户UID常量
    public static final String ADMIN_TEST_UID = "admin-test-uid";
    public static final String MANAGER_TEST_UID = "manager-test-uid";
    public static final String NORMAL_USER_TEST_UID = "user-test-uid";
    public static final String FINANCE_TEST_UID = "finance-test-uid";
    public static final String RD_TEST_UID = "rd-test-uid";

    // 全文检索测试专用
    public static final String ADMIN_SEARCH_TEST_UID = "admin-search-test-uid";
    public static final String MANAGER_SEARCH_TEST_UID = "manager-search-test-uid";
    public static final String PARTIAL_SEARCH_TEST_UID = "partial-search-test-uid";
    public static final String NOPERM_SEARCH_TEST_UID = "noperm-search-test-uid";

    // 测试角色常量
    public static final String TEST_ADMIN_ROLE = "bd23de3f-086a-42ff-ba39-fb9139d94af9";
    public static final String TEST_MANAGER_ROLE = "748a51ce-308b-404c-ba02-4b48dd8983fb";
    public static final String TEST_FINANCE_ROLE = "finance-role-uid";
    public static final String TEST_RD_ROLE = "rd-role-uid";

    // 测试部门常量
    public static final String TEST_ADMIN_DEPT = "f7669429-3da1-47f9-b143-abdb5b95d77e";
    public static final String TEST_FINANCE_DEPT = "finance-dept-uid";
    public static final String TEST_RD_DEPT = "rd-dept-uid";
    public static final String TEST_NORMAL_DEPT = "normal-dept-uid";
    public static final String TEST_GUEST_DEPT = "guest-dept-uid";

    /**
     * 创建基础测试用户（用于PermissionTest）
     */
    public static void createBasicTestUsers() {
        log.info("Creating basic test users...");

        UserContext.runAsSystem(() -> {
            createUserWithPermissions(createTestAdminUser());
            createUserWithPermissions(createTestManagerUser());
            createUserWithPermissions(createTestNormalUser());
            createUserWithPermissions(createTestFinanceUser());
            createUserWithPermissions(createTestRdUser());
        });

        log.info("Basic test users created successfully");
    }

    /**
     * 创建全文检索测试用户（用于FullTextSearchTest）
     */
    public static void createFullTextSearchTestUsers() {
        log.info("Creating fulltext search test users...");

        UserContext.runAsSystem(() -> {
            createUserWithPermissions(createTestAdminSearchUser());
            createUserWithPermissions(createTestManagerSearchUser());
            createUserWithPermissions(createPartialPermissionUser());
            createUserWithPermissions(createNoPermissionUser());
        });

        log.info("Fulltext search test users created successfully");
    }

    /**
     * 创建User和UserPermissions
     */
    public static void createUserWithPermissions(UserContext userContext) {
        try {
            // 1. 先检查或创建User对象
            User user = getOrCreateUser(userContext);

            // 2. 检查UserPermissions是否已存在
            UserPermissions existing = Q.result(UserPermissions.class)
                    .where("keycloakUserUid = ?", userContext.getUserUid())
                    .first();

            if (existing != null) {
                log.debug("User permissions already exist for: {}", userContext.getUsername());
                return;
            }

            // 3. 创建UserPermissions对象
            UserPermissions userPermissions = UserPermissions.newModel(
                    userContext.getUserUid(), userContext.getUsername());

            // 设置userId关联
            userPermissions.setUserId(user.getId());

            // 设置角色信息
            userPermissions.setAllRoleUids(userContext.getAuthorities());

            // 设置部门信息
            userPermissions.setAllGroupUids(userContext.getGroups());
            userPermissions.setDefaultGroupUid(
                    userContext.getGroups().isEmpty() ? null : userContext.getGroups().get(0));

            // 设置常用权限标识
            userPermissions.setIsAdmin(userContext.getAuthorities().contains("ADMIN"));
            userPermissions.setIsManager(userContext.getAuthorities().contains("MANAGER"));

            userPermissions.markSynced();
            S.service(ObjectService.class).upsertByBusinessKey(userPermissions);

            log.debug("Created user permissions for: {} with roles: {} and groups: {}",
                    userContext.getUsername(), userContext.getAuthorities(), userContext.getGroups());

        } catch (Exception e) {
            log.error("Failed to create user permissions for: {}", userContext.getUsername(), e);
            throw new RuntimeException("Failed to create user permissions for: " + userContext.getUsername(), e);
        }
    }

    /**
     * 获取或创建User对象
     */
    private static User getOrCreateUser(UserContext userContext) {
        // 先尝试根据keycloakUserUid查找
        User existingUser = Q.result(User.class)
                .where("keycloakUserUid = ? OR username = ? ", userContext.getUserUid(), userContext.getUsername())
                .first();

        if (existingUser != null) {
            return existingUser;
        }

        // 如果不存在，创建新的User对象
        User newUser = User.newModel(userContext.getUserUid(), userContext.getUsername());

        // 设置其他基本信息
        newUser.setEnabled(true);
        newUser.markSynced();

        // 保存并返回
        newUser = S.service(ObjectService.class).save(newUser);

        log.debug("Created new User: {} ({})", newUser.getUsername(), newUser.getKeycloakUserUid());

        return newUser;
    }

    // ==================== 基础测试用户创建方法 ====================

    /**
     * 创建测试管理员用户
     */
    public static UserContext createTestAdminUser() {
        return UserContext.builder()
                .userId(1L)
                .userUid(ADMIN_TEST_UID)
                .username("admin")
                .authorities(Arrays.asList("ADMIN", "USER", TEST_ADMIN_ROLE))
                .groups(Arrays.asList(TEST_ADMIN_DEPT))
                .build();
    }

    /**
     * 创建测试经理用户
     */
    public static UserContext createTestManagerUser() {
        return UserContext.builder()
                .userId(2L)
                .userUid(MANAGER_TEST_UID)
                .username("manager")
                .authorities(Arrays.asList("MANAGER", "USER", TEST_MANAGER_ROLE))
                .groups(Arrays.asList(TEST_ADMIN_DEPT))
                .build();
    }

    /**
     * 创建测试普通用户
     */
    public static UserContext createTestNormalUser() {
        return UserContext.builder()
                .userId(3L)
                .userUid(NORMAL_USER_TEST_UID)
                .username("user")
                .authorities(Arrays.asList("USER"))
                .groups(Arrays.asList(TEST_NORMAL_DEPT))
                .build();
    }

    /**
     * 创建测试财务用户
     */
    public static UserContext createTestFinanceUser() {
        return UserContext.builder()
                .userId(4L)
                .userUid(FINANCE_TEST_UID)
                .username("finance_user")
                .authorities(Arrays.asList("USER", "FINANCE", TEST_FINANCE_ROLE))
                .groups(Arrays.asList(TEST_FINANCE_DEPT))
                .build();
    }

    /**
     * 创建测试研发用户
     */
    public static UserContext createTestRdUser() {
        return UserContext.builder()
                .userId(5L)
                .userUid(RD_TEST_UID)
                .username("rd_user")
                .authorities(Arrays.asList("USER", "RD", TEST_RD_ROLE))
                .groups(Arrays.asList(TEST_RD_DEPT))
                .build();
    }

    // ==================== 全文检索测试用户创建方法 ====================

    /**
     * 创建全文检索测试管理员用户
     */
    public static UserContext createTestAdminSearchUser() {
        return UserContext.builder()
                .userId(11L)
                .userUid(ADMIN_SEARCH_TEST_UID)
                .username("admin_search")
                .authorities(Arrays.asList("ADMIN", "USER"))
                .groups(Arrays.asList(TEST_ADMIN_DEPT))
                .build();
    }

    /**
     * 创建全文检索测试经理用户
     */
    public static UserContext createTestManagerSearchUser() {
        return UserContext.builder()
                .userId(12L)
                .userUid(MANAGER_SEARCH_TEST_UID)
                .username("manager_search")
                .authorities(Arrays.asList("MANAGER", "USER"))
                .groups(Arrays.asList(TEST_ADMIN_DEPT))
                .build();
    }

    /**
     * 创建部分权限用户（可以看到public和dept数据）
     */
    public static UserContext createPartialPermissionUser() {
        return UserContext.builder()
                .userId(13L)
                .userUid(PARTIAL_SEARCH_TEST_UID)
                .username("partial_search")
                .authorities(Arrays.asList("USER", "DEPT_MEMBER"))
                .groups(Arrays.asList(TEST_NORMAL_DEPT))
                .build();
    }

    /**
     * 创建无权限用户
     */
    public static UserContext createNoPermissionUser() {
        return UserContext.builder()
                .userId(14L)
                .userUid(NOPERM_SEARCH_TEST_UID)
                .username("noperm_search")
                .authorities(Arrays.asList("GUEST"))  // 只有最基本权限
                .groups(Arrays.asList(TEST_GUEST_DEPT))
                .build();
    }

    // ==================== 清理方法 ====================

    /**
     * 清理所有测试用户数据
     */
    public static void cleanupAllTestUsers() {
        List<String> allTestUserUids = Arrays.asList(
                ADMIN_TEST_UID,
                MANAGER_TEST_UID,
                NORMAL_USER_TEST_UID,
                FINANCE_TEST_UID,
                RD_TEST_UID,
                ADMIN_SEARCH_TEST_UID,
                MANAGER_SEARCH_TEST_UID,
                PARTIAL_SEARCH_TEST_UID,
                NOPERM_SEARCH_TEST_UID
        );

        cleanupTestUsers(allTestUserUids);
    }

    /**
     * 清理基础测试用户
     */
    public static void cleanupBasicTestUsers() {
        List<String> basicTestUserUids = Arrays.asList(
                ADMIN_TEST_UID,
                MANAGER_TEST_UID,
                NORMAL_USER_TEST_UID,
                FINANCE_TEST_UID,
                RD_TEST_UID
        );

        cleanupTestUsers(basicTestUserUids);
    }

    /**
     * 清理全文检索测试用户
     */
    public static void cleanupFullTextSearchTestUsers() {
        List<String> searchTestUserUids = Arrays.asList(
                ADMIN_SEARCH_TEST_UID,
                MANAGER_SEARCH_TEST_UID,
                PARTIAL_SEARCH_TEST_UID,
                NOPERM_SEARCH_TEST_UID
        );

        cleanupTestUsers(searchTestUserUids);
    }

    /**
     * 清理指定的测试用户列表
     */
    public static void cleanupTestUsers(List<String> userUids) {
        try {
            UserContext.runAsSystem(() -> {
                UserContext.ensureCurrent().byPass(() -> {
                    for (String userUid : userUids) {
                        // 先删除UserPermissions
                        UserPermissions userPermissions = Q.result(UserPermissions.class)
                                .where("keycloakUserUid = ?", userUid)
                                .first();
                        if (userPermissions != null) {
                            S.service(ObjectService.class).delete(Arrays.asList(userPermissions.getId()));
                            log.debug("Cleaned up user permissions for: {}", userUid);
                        }

                        // 再删除User
                        User user = Q.result(User.class)
                                .where("keycloakUserUid = ?", userUid)
                                .first();
                        if (user != null) {
                            S.service(ObjectService.class).delete(Arrays.asList(user.getId()));
                            log.debug("Cleaned up user for: {}", userUid);
                        }
                    }

                    log.info("Test users cleanup completed for {} users", userUids.size());
                });
            });
        } catch (Exception e) {
            log.warn("Failed to cleanup test users", e);
        }
    }

    /**
     * 检查测试用户是否存在
     */
    public static boolean testUserExists(String userUid) {
        User user = Q.result(User.class)
                .where("keycloakUserUid = ?", userUid)
                .first();
        return user != null;
    }
}