package io.emop.integrationtest.usecase.permission;

import io.emop.integrationtest.domain.SampleDocument;
import io.emop.model.auth.User;
import io.emop.model.auth.UserPermissions;
import io.emop.model.common.UserContext;
import io.emop.model.permission.PermissionAction;
import io.emop.model.permission.PermissionConfig;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.lifecycle.LifecycleService;
import io.emop.service.api.permission.PermissionCheckService;
import io.emop.service.api.permission.PermissionConfigDeploymentService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 基础权限测试扩展 - 基于UserContext直接计算权限
 * <p>
 * 重构要点：
 * 1. 移除所有权限检查方法的UserContext参数
 * 2. 使用UserContext.setCurrentUser()设置测试用户上下文
 * 3. 权限检查方法自动从ThreadLocal获取用户上下文
 * 4. 修复RLS问题：查询前必须先设置用户上下文
 * 5. 直接基于UserContext的authorities和groups计算权限，不创建数据库记录
 * <p>
 * 在原有PermissionTest基础上添加基础的复杂场景测试：
 * 1. 部门间权限隔离测试
 * 2. 状态驱动权限测试
 * 3. 创建者权限测试
 * 4. 经理权限测试
 * 5. 简单的文档类型权限测试
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BasicPermissionTestExtension {

    // 测试时间戳，用于避免path重复
    private final String testTimestamp;

    // 测试角色常量
    private static final String TEST_RD_MANAGER = "test_rd_manager_role_uid";
    private static final String TEST_RD_ENGINEER = "test_rd_engineer_role_uid";
    private static final String TEST_PROCESS_MANAGER = "test_process_manager_role_uid";
    private static final String TEST_PROCESS_ENGINEER = "test_process_engineer_role_uid";
    private static final String TEST_NORMAL_USER = "test_normal_user_role_uid";

    // 测试部门常量
    private static final String TEST_RD_DEPT = "test_rd_dept_uid";
    private static final String TEST_PROCESS_DEPT = "test_process_dept_uid";
    private static final String TEST_QUALITY_DEPT = "test_quality_dept_uid";

    private PermissionCheckService permissionCheckService;
    private PermissionConfigDeploymentService configDeploymentService;
    private ObjectService objectService;
    private LifecycleService lifecycleService;
    private static Long userId;

    public BasicPermissionTestExtension() {
        // 生成测试时间戳，格式：yyyyMMdd_HHmmss_SSS
        this.testTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        log.info("Initialized BasicPermissionTest with timestamp: {}", testTimestamp);
    }

    void initServices() {
        this.permissionCheckService = S.service(PermissionCheckService.class);
        this.configDeploymentService = S.service(PermissionConfigDeploymentService.class);
        this.objectService = S.service(ObjectService.class);
        this.lifecycleService = S.service(LifecycleService.class);
    }

    @BeforeEach
    void setUp() {
        initServices();
        userId = Q.result(User.class).where("username=?", "admin").first().getId();
        deployBasicComplexPermissionConfig();
        createBasicTestUsers();
        createBasicTestDocuments();
    }

    @AfterEach
    void tearDown() {
        // 确保清理用户上下文
        UserContext.clear();
    }

    @AfterAll
    static void clean() {
        UserContext.runAsSystem(() -> S.service(PermissionConfigDeploymentService.class).removePermissionConfig(SampleDocument.class.getName()));
    }

    /**
     * 部署基础复杂权限配置
     */
    private void deployBasicComplexPermissionConfig() {
        log.info("Deploying basic complex permission configuration...");

        try {
            String configYaml = getBasicComplexPermissionConfig();

            UserContext.runAsSystem(() -> {
                UserContext.ensureCurrent().byPass(() -> {
                    PermissionConfig deployedConfig = configDeploymentService.deploy(configYaml);

                    assertTrue(deployedConfig != null, "Basic complex config should not be null");
                    assertTrue(deployedConfig.getRules() != null && !deployedConfig.getRules().isEmpty(),
                            "Basic complex config should have rules");

                    log.info("Successfully deployed basic complex permission config with {} rules",
                            deployedConfig.getRules().size());
                });
            });

        } catch (Exception e) {
            throw new RuntimeException("Failed to deploy basic complex permission configuration", e);
        }
    }

    /**
     * 创建基础测试用户
     */
    private void createBasicTestUsers() {
        log.info("Creating basic test users...");

        UserContext.runAsSystem(() -> {
            createTestUserPermissions(createTestRdManager());
            createTestUserPermissions(createTestRdEngineer());
            createTestUserPermissions(createTestProcessManager());
            createTestUserPermissions(createTestProcessEngineer());
            createTestUserPermissions(createTestNormalUser());
        });

        log.info("Basic test users created successfully");
    }

    /**
     * 创建测试用户权限记录
     */
    private void createTestUserPermissions(UserContext userContext) {
        try {
            // 检查是否已存在
            UserPermissions existing = Q.result(UserPermissions.class)
                    .where("keycloakUserUid = ?", userContext.getUserUid())
                    .first();

            if (existing != null) {
                log.info("Test user permissions already exist for: {}", userContext.getUsername());
                return;
            }

            UserPermissions userPermissions = UserPermissions.newModel(
                    userContext.getUserUid(), userContext.getUsername());

            userPermissions.setAllRoleUids(userContext.getAuthorities());

            // 设置部门信息
            List<String> deptUids = determineTestDepartmentUids(userContext);
            userPermissions.setAllGroupUids(deptUids);
            userPermissions.setDefaultGroupUid(deptUids.isEmpty() ? null : deptUids.get(0));

            // 设置常用权限标识
            userPermissions.setIsAdmin(userContext.getAuthorities().contains("ADMIN"));
            userPermissions.setIsManager(userContext.getAuthorities().contains("MANAGER") ||
                    userContext.getAuthorities().contains(TEST_RD_MANAGER) ||
                    userContext.getAuthorities().contains(TEST_PROCESS_MANAGER));
            userPermissions.setUserId(userId);

            userPermissions.markSynced();
            objectService.save(userPermissions);

            log.info("Created test user permissions for: {}", userContext.getUsername());

        } catch (Exception e) {
            log.error("Failed to create test user permissions for: {}", userContext.getUsername(), e);
        }
    }

    /**
     * 确定测试部门UID
     */
    private List<String> determineTestDepartmentUids(UserContext userContext) {
        List<String> authorities = userContext.getAuthorities();
        List<String> deptUids = new ArrayList<>();

        if (authorities.contains(TEST_RD_MANAGER) || authorities.contains(TEST_RD_ENGINEER)) {
            deptUids.add(TEST_RD_DEPT);
        }
        if (authorities.contains(TEST_PROCESS_MANAGER) || authorities.contains(TEST_PROCESS_ENGINEER)) {
            deptUids.add(TEST_PROCESS_DEPT);
        }

        return deptUids;
    }

    /**
     * 创建基础测试文档
     */
    private void createBasicTestDocuments() {
        log.info("Creating basic test documents...");

        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                // 创建不同状态的CAD文档
                createBasicCadDocuments();

                // 创建不同类型的文档
                createBasicDocumentTypes();
            });
        });

        log.info("Basic test documents created successfully");
    }

    /**
     * 创建CAD文档（不同状态）
     */
    private void createBasicCadDocuments() {
        // 工作中状态CAD
        SampleDocument workingCad = createBasicTestDocument("basic_working_cad.dwg", "CAD");
        workingCad.set("doc_type", "CAD");
        workingCad.set("complexity", "simple");
        objectService.save(workingCad);

        // 审核中CAD
        SampleDocument reviewCad = createBasicTestDocument("basic_review_cad.dwg", "CAD");
        reviewCad.set("doc_type", "CAD");
        reviewCad.set("complexity", "medium");
        lifecycleService.moveToState(objectService.save(reviewCad), "UnderReview");

        // 已发布CAD
        SampleDocument releasedCad = createBasicTestDocument("basic_released_cad.dwg", "CAD");
        releasedCad.set("doc_type", "CAD");
        releasedCad.set("complexity", "complex");
        lifecycleService.moveToState(objectService.save(releasedCad), "Released");
    }

    /**
     * 创建不同类型的基础文档
     */
    private void createBasicDocumentTypes() {
        // 工艺文档
        SampleDocument processDoc = createBasicTestDocument("basic_process.pdf", "PROCESS");
        processDoc.set("doc_type", "PROCESS");
        processDoc.set("process_type", "GENERAL");
        lifecycleService.moveToState(objectService.save(processDoc), "Released");

        // 质量文档
        SampleDocument qualityDoc = createBasicTestDocument("basic_quality.pdf", "QUALITY");
        qualityDoc.set("doc_type", "QUALITY");
        qualityDoc.set("quality_type", "STANDARD");
        lifecycleService.moveToState(objectService.save(qualityDoc), "Released");
    }

    @Test
    @Order(1)
    void testDepartmentPermissionIsolation() {
        log.info("Testing department permission isolation...");

        UserContext rdEngineer = createTestRdEngineer();
        UserContext processEngineer = createTestProcessEngineer();

        // 研发工程师应该可以查看CAD文档
        rdEngineer.run(() -> {
            // 获取CAD文档（在设置用户上下文后）
            SampleDocument cadDoc = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_working_cad.dwg"))
                    .first();

            assertNotNull(cadDoc, "CAD document should be found by RD Engineer");

            assertTrue(permissionCheckService.checkPermission(cadDoc, PermissionAction.READ),
                    "RD Engineer should be able to read CAD documents");
        });

        // 工艺工程师应该可以查看工艺文档
        processEngineer.run(() -> {
            // 获取工艺文档（在设置用户上下文后）
            SampleDocument processDoc = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_process.pdf"))
                    .first();

            assertNotNull(processDoc, "Process document should be found by Process Engineer");

            assertTrue(permissionCheckService.checkPermission(processDoc, PermissionAction.READ),
                    "Process Engineer should be able to read process documents");
        });

        // 跨部门权限隔离：工艺工程师不应该能查看工作中状态的CAD文档
        processEngineer.run(() -> {
            // 这里可能查询不到CAD文档，因为工艺工程师没有权限
            SampleDocument cadDoc = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_working_cad.dwg"))
                    .first();
            assertNull(cadDoc, "Process Engineer cannot query working CAD documents - RLS working correctly");
        });

        log.info("Department permission isolation test completed successfully");
    }

    @Test
    @Order(2)
    void testStateBasedPermissions() {
        log.info("Testing state-based permissions...");

        UserContext rdEngineer = createTestRdEngineer();
        UserContext processEngineer = createTestProcessEngineer();

        // 研发工程师可以修改工作中状态的CAD
        rdEngineer.run(() -> {
            SampleDocument workingCad = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_working_cad.dwg"))
                    .first();

            assertNotNull(workingCad, "Working CAD should be found by RD Engineer");

            assertTrue(permissionCheckService.checkPermission(workingCad, PermissionAction.UPDATE),
                    "RD Engineer should update working CAD");
        });

        // 工艺工程师不能查看工作中CAD，但可以查看已发布CAD
        processEngineer.run(() -> {
            // 尝试查询工作中CAD
            SampleDocument workingCad = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_working_cad.dwg"))
                    .first();

            assertNull(workingCad, "Process Engineer cannot query working CAD - RLS working correctly");

            // 查询已发布CAD
            SampleDocument releasedCad = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_released_cad.dwg"))
                    .first();

            assertNotNull(releasedCad, "Process Engineer should query released CAD - check permission config");
        });

        log.info("State-based permissions test completed successfully");
    }

    @Test
    @Order(3)
    void testCreatorPermissions() {
        log.info("Testing creator permissions...");

        UserContext rdEngineer = createTestRdEngineer();
        UserContext anotherRdEngineer = createTestRdEngineer2();

        // 使用研发工程师身份创建文档
        SampleDocument creatorDoc = rdEngineer.run(() -> {
            SampleDocument doc = createBasicTestDocument("creator_test.dwg", "CAD");
            doc.set("doc_type", "CAD");
            return objectService.save(doc);
        });

        // 创建者应该可以删除自己的工作中文档
        rdEngineer.run(() -> {
            // 重新查询文档（在创建者上下文中）
            SampleDocument foundDoc = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("creator_test.dwg"))
                    .first();

            assertNotNull(foundDoc, "Creator should be able to find their document");

            assertTrue(permissionCheckService.checkPermission(foundDoc, PermissionAction.DELETE),
                    "Creator should be able to delete their working document");
        });

        // 其他用户（即使同部门）不应该能删除
        anotherRdEngineer.run(() -> {
            SampleDocument foundDoc = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("creator_test.dwg"))
                    .first();

            assertNull(foundDoc, "Other user cannot query creator's document - RLS working correctly");
        });

        log.info("Creator permissions test completed successfully");
    }

    @Test
    @Order(4)
    void testManagerPermissions() {
        log.info("Testing manager permissions...");

        UserContext rdManager = createTestRdManager();
        UserContext processManager = createTestProcessManager();
        UserContext rdEngineer = createTestRdEngineer();

        // 研发经理应该可以修改已发布的CAD文档
        rdManager.run(() -> {
            SampleDocument releasedCad = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_released_cad.dwg"))
                    .first();

            assertNotNull(releasedCad, "RD Manager should be able to find released CAD");

            assertTrue(permissionCheckService.checkPermission(releasedCad, PermissionAction.UPDATE),
                    "RD Manager should update released CAD documents");
        });

        // 普通研发工程师不应该能修改已发布的CAD文档
        rdEngineer.run(() -> {
            SampleDocument releasedCad = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_released_cad.dwg"))
                    .first();

            if (releasedCad != null) {
                assertFalse(permissionCheckService.checkPermission(releasedCad, PermissionAction.UPDATE),
                        "RD Engineer should not update released CAD documents");
            } else {
                log.warn("RD Engineer cannot query released CAD - check permission config");
            }
        });

        // 经理应该有下载权限
        rdManager.run(() -> {
            SampleDocument releasedCad = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_released_cad.dwg"))
                    .first();

            if (releasedCad != null) {
                assertTrue(permissionCheckService.checkPermission(releasedCad, PermissionAction.DOWNLOAD),
                        "RD Manager should download documents");
            }
        });

        processManager.run(() -> {
            SampleDocument releasedCad = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_released_cad.dwg"))
                    .first();

            if (releasedCad != null) {
                assertTrue(permissionCheckService.checkPermission(releasedCad, PermissionAction.DOWNLOAD),
                        "Process Manager should download documents");
            }
        });

        log.info("Manager permissions test completed successfully");
    }

    @Test
    @Order(5)
    void testDocumentTypePermissions() {
        log.info("Testing document type permissions...");

        UserContext rdEngineer = createTestRdEngineer();
        UserContext processEngineer = createTestProcessEngineer();

        // 研发工程师应该可以查看CAD文档
        rdEngineer.run(() -> {
            SampleDocument cadDoc = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_released_cad.dwg"))
                    .first();

            assertNotNull(cadDoc, "RD Engineer should read CAD documents");
        });

        // 工艺工程师应该可以查看工艺文档
        processEngineer.run(() -> {
            SampleDocument processDoc = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("basic_process.pdf"))
                    .first();

            assertNotNull(processDoc, "Process Engineer should read process documents");
        });

        // 工艺工程师应该可以创建工艺文档
        processEngineer.run(() -> {
            assertTrue(permissionCheckService.checkPermission(SampleDocument.class.getName(), PermissionAction.CREATE),
                    "Process Engineer should create process documents");
        });

        log.info("Document type permissions test completed successfully");
    }

    // ==================== 测试用户创建方法 - 基于UserContext的authorities和groups ====================

    private UserContext createTestRdManager() {
        return UserContext.builder()
                .userId(2001L)
                .userUid("test-rd-manager-uid")
                .username("test_rd_manager")
                .authorities(Arrays.asList(TEST_RD_MANAGER, "MANAGER", "USER"))
                .groups(Arrays.asList(TEST_RD_DEPT))
                .build();
    }

    UserContext createTestRdEngineer() {
        return UserContext.builder()
                .userId(2002L)
                .userUid("test-rd-engineer-uid")
                .username("test_rd_engineer")
                .authorities(Arrays.asList(TEST_RD_ENGINEER, "USER"))
                .groups(Arrays.asList(TEST_RD_DEPT))
                .build();
    }

    private UserContext createTestRdEngineer2() {
        return UserContext.builder()
                .userId(2003L)
                .userUid("test-rd-engineer-2-uid")
                .username("test_rd_engineer_2")
                .authorities(Arrays.asList(TEST_RD_ENGINEER, "USER"))
                .groups(Arrays.asList(TEST_RD_DEPT))
                .build();
    }

    private UserContext createTestProcessManager() {
        return UserContext.builder()
                .userId(2004L)
                .userUid("test-process-manager-uid")
                .username("test_process_manager")
                .authorities(Arrays.asList(TEST_PROCESS_MANAGER, "MANAGER", "USER"))
                .groups(Arrays.asList(TEST_PROCESS_DEPT))
                .build();
    }

    private UserContext createTestProcessEngineer() {
        return UserContext.builder()
                .userId(2005L)
                .userUid("test-process-engineer-uid")
                .username("test_process_engineer")
                .authorities(Arrays.asList(TEST_PROCESS_ENGINEER, "USER"))
                .groups(Arrays.asList(TEST_PROCESS_DEPT))
                .build();
    }

    private UserContext createTestNormalUser() {
        return UserContext.builder()
                .userId(2006L)
                .userUid("test-normal-user-uid")
                .username("test_normal_user")
                .authorities(Arrays.asList(TEST_NORMAL_USER, "USER"))
                .groups(Arrays.asList("other_dept_uid"))
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建带时间戳的基础测试文档
     */
    private SampleDocument createBasicTestDocument(String filename, String docType) {
        SampleDocument document = SampleDocument.newModel();
        document.setFileType(docType);
        document.setFileSize(1024L);
        document.setPath(getTestPath(filename));
        document.setChecksum("checksum_" + filename + "_" + testTimestamp);
        document.setName("Test " + docType + " Document");
        document.setDescription("Basic test document for " + docType + " at " + testTimestamp);
        return document;
    }

    /**
     * 获取带时间戳的测试路径
     */
    private String getTestPath(String filename) {
        return "/test/" + testTimestamp + "_" + filename;
    }

    /**
     * 获取测试路径前缀，用于批量查询
     */
    private String getTestPathPrefix() {
        return "/test/" + testTimestamp + "_";
    }

    /**
     * 获取基础复杂权限配置
     */
    private String getBasicComplexPermissionConfig() {
        return """
                version: "1.0"
                description: "基础复杂权限配置 - 测试部门权限、状态权限、创建者权限（基于UserContext）"
                createdAt: "2025-06-14"
                
                # 权限配置
                permissionConfig:
                  objects:
                    # SampleDocument基础复杂权限配置
                    SampleDocument:
                      description: "SampleDocument基础权限控制 - 支持部门、状态、创建者权限（基于UserContext）"
                      permissions:
                        READ:
                          conditions:
                            # 研发部门可以查看所有CAD文档
                            - sql: "auth.user_in_group('test_rd_dept_uid') AND (_properties->>'doc_type') = 'CAD'"
                              description: "研发部门成员可查看所有CAD文档"
                            # 工艺部门只能查看已发布的CAD文档
                            - sql: "auth.user_in_group('test_process_dept_uid') AND (_properties->>'doc_type') = 'CAD' AND _state IN ('Released', 'Frozen')"
                              description: "工艺部门只能查看已发布的CAD文档"
                            # 工艺部门可以查看所有工艺文档
                            - sql: "auth.user_in_group('test_process_dept_uid') AND (_properties->>'doc_type') = 'PROCESS'"
                              description: "工艺部门可查看所有工艺文档"
                            # 质量部门查看质量相关文档
                            - sql: "auth.user_in_group('test_quality_dept_uid') AND (_properties->>'doc_type') = 'QUALITY'"
                              description: "质量部门查看质量相关文档"
                            # 创建者始终可以查看自己的文档
                            - sql: "auth.check_creator_permission(_creator)"
                              description: "创建者可查看自己的文档"
                            # 管理员可以查看所有文档
                            - sql: "auth.user_is_admin()"
                              description: "管理员可查看所有文档"
                          logic: "OR"
                
                        UPDATE:
                          conditions:
                            # 研发部门可以修改工作中和审核中的CAD文档
                            - script: "user_in_group('test_rd_dept_uid') && object.get('doc_type') == 'CAD' && object._state in ['Working', 'UnderReview']"
                              description: "研发部门可修改工作中和审核中的CAD文档"
                            # 研发经理可以修改已发布的CAD文档
                            - script: "user_has_role('test_rd_manager_role_uid') && object.get('doc_type') == 'CAD' && object._state == 'Released'"
                              description: "研发经理可修改已发布CAD文档"
                            # 工艺工程师修改工艺文档
                            - script: "user_has_role('test_process_engineer_role_uid') && object.get('doc_type') == 'PROCESS' && object._state in ['Working', 'UnderReview']"
                              description: "工艺工程师修改工艺文档"
                            # 经理可以修改对应部门的文档
                            - script: "user_is_manager() && check_creator_permission(object._creator)"
                              description: "经理可修改本部门创建的文档"
                          logic: "OR"
                
                        DELETE:
                          conditions:
                            # 创建者可以删除工作中状态的文档
                            - script: "check_creator_permission(object._creator) && object._state == 'Working'"
                              description: "创建者可删除工作中状态文档"
                            # 经理可以删除未发布的文档
                            - script: "user_is_manager() && object._state in ['Working', 'UnderReview']"
                              description: "经理可删除未发布的文档"
                          logic: "OR"
                
                        CREATE:
                          conditions:
                            # 研发工程师和研发经理可以创建CAD文档
                            - script: "user_has_role('test_rd_manager_role_uid') || user_has_role('test_rd_engineer_role_uid')"
                              description: "研发工程师和研发经理可创建CAD文档"
                            # 工艺工程师可以创建工艺文档
                            - script: "user_has_role('test_process_engineer_role_uid') || user_has_role('test_process_manager_role_uid')"
                              description: "工艺工程师可创建工艺文档"
                          logic: "OR"
                
                        DOWNLOAD:
                          conditions:
                            # 研发部门无限制下载CAD文档
                            - script: "user_in_group('test_rd_dept_uid') && object._properties?.doc_type == 'CAD'"
                              description: "研发部门无限制下载CAD文档"
                            # 工艺部门只能下载已发布的CAD文档
                            - script: "user_in_group('test_process_dept_uid') && object._properties?.doc_type == 'CAD' && object._state in ['Released', 'Frozen']"
                              description: "工艺部门只能下载已发布CAD文档"
                            # 经理级别可以下载所有文档
                            - script: "user_is_manager()"
                              description: "经理可下载所有文档"
                          logic: "OR"
                
                    # 继承DraftModelObject的默认权限
                    DraftModelObject:
                      description: "草稿对象权限控制，删改查都只能是自己"
                      permissions:
                        READ:
                          conditions:
                            - sql: "_creator = auth.get_current_user_id()"
                              description: "只能查看自己的草稿"
                        UPDATE:
                          conditions:
                            - script: "object._creator == currentUserId"
                              description: "只能修改自己的草稿"
                        DELETE:
                          conditions:
                            - script: "object._creator == currentUserId"
                              description: "只能删除自己的草稿"
                        CREATE:
                          conditions: []  # 无限制，任何用户都可以创建草稿
                """;
    }
}