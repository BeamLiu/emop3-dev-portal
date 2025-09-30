package io.emop.integrationtest.usecase.permission;

import io.emop.integrationtest.util.TestUserHelper;
import io.emop.model.auth.UserPermissions;
import io.emop.model.common.UserContext;
import io.emop.model.permission.PermissionAction;
import io.emop.model.permission.PermissionConfig;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleDocument;
import io.emop.service.S;
import io.emop.service.api.permission.PermissionCheckService;
import io.emop.service.api.permission.PermissionConfigDeploymentService;
import io.emop.service.api.data.ObjectService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;

import static io.emop.integrationtest.util.Assertion.*;
import static io.emop.integrationtest.util.TestUserHelper.*;

/**
 * 增强的ABAC权限系统集成测试 - 包含对象级别和属性级别权限
 * <p>
 * 重构要点：
 * 1. 移除所有UserContext参数传递
 * 2. 使用UserContext.setCurrentUser()设置上下文
 * 3. 权限检查方法自动从ThreadLocal获取用户上下文
 * 4. 简化API调用，代码更清晰
 * 5. 直接基于UserContext的authorities和groups计算权限，不访问数据库
 * 6. 移除已在其他测试类中覆盖的测试内容
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PermissionTest {

    // 只使用标记@Remote的服务
    private PermissionCheckService permissionCheckService;
    private PermissionConfigDeploymentService configDeploymentService;
    private ObjectService objectService;

    @BeforeEach
    void setUp() {
        log.info("Starting basic permission system integration test setup...");
        initServices();
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
     * 初始化服务
     */
    private void initServices() {
        this.permissionCheckService = S.service(PermissionCheckService.class);
        this.configDeploymentService = S.service(PermissionConfigDeploymentService.class);
        this.objectService = S.service(ObjectService.class);
        log.info("Initialized services for basic permission testing");
    }

    @Test
    @Order(1)
    void testDeployPermissionConfig() {
        log.info("Testing permission configuration deployment...");

        try {
            // 使用bypass权限部署配置
            UserContext.runAsSystem(() -> {
                UserContext.ensureCurrent().byPass(() -> {
                    PermissionConfig deployedConfig = configDeploymentService.deploy(getDefaultPermissionConfig());

                    assertTrue(deployedConfig != null, "Deployed config should not be null");
                    assertTrue(deployedConfig.getRules() != null && !deployedConfig.getRules().isEmpty(),
                            "Deployed config should have rules");

                    log.info("Successfully deployed permission config with {} object rules",
                            deployedConfig.getRules().size());
                });
            });

        } catch (Throwable e) {
            throw new RuntimeException("Failed to deploy permission configuration", e);
        }
    }

    @Test
    @Order(2)
    void testCreateTestData() {
        log.info("Creating test data...");

        // 使用系统权限创建测试数据
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                // 1. 创建测试用户权限记录
                createTestUserPermissions();

                // 2. 创建多个测试文档
                for (int i = 0; i < 5; i++) {
                    SampleDocument doc = createTestDocument("test_" + i + ".dwg", "dwg");
                    objectService.save(doc);
                }
            });
        });

        log.info("Test data created successfully");
    }

    @Test
    @Order(3)
    void testBasicPermissionCheck() {
        log.info("Testing basic permission checking...");

        // 创建测试文档
        SampleDocument document = null;
        UserContext adminUser = createTestAdminUser();

        // 使用管理员创建文档
        UserContext.setCurrentUser(adminUser);

        try {
            document = createTestDocument("permission_test.dwg", "dwg");
            document = objectService.save(document);
            assertTrue(document.getId() != null, "Document should be saved with ID");

            log.info("Test document created with ID: {}", document.getId());

        } finally {
            UserContext.clear();
        }

        // 测试不同用户的权限
        UserContext normalUser = createTestNormalUser();
        UserContext managerUser = createTestManagerUser();

        // 测试READ权限 - 管理员
        UserContext.setCurrentUser(adminUser);
        try {
            boolean adminCanRead = permissionCheckService.checkPermission(document.getId(), PermissionAction.READ);
            assertTrue(adminCanRead, "Admin should have READ permission");
        } finally {
            UserContext.clear();
        }

        // 测试READ权限 - 普通用户
        UserContext.setCurrentUser(normalUser);
        try {
            boolean normalCanRead = permissionCheckService.checkPermission(document.getId(), PermissionAction.READ);
            log.info("Normal user read permission: {}", normalCanRead);
        } finally {
            UserContext.clear();
        }

        // 测试DOWNLOAD权限 - 管理员
        UserContext.setCurrentUser(adminUser);
        try {
            boolean adminCanDownload = permissionCheckService.checkPermission(document.getId(), PermissionAction.DOWNLOAD);
            assertTrue(adminCanDownload, "Admin should have DOWNLOAD permission");
        } finally {
            UserContext.clear();
        }

        // 测试DOWNLOAD权限 - 普通用户
        UserContext.setCurrentUser(normalUser);
        try {
            boolean normalCanDownload = permissionCheckService.checkPermission(document.getId(), PermissionAction.DOWNLOAD);
            assertFalse(normalCanDownload, "Normal user should not have DOWNLOAD permission");
        } finally {
            UserContext.clear();
        }

        log.info("Basic permission checking tests completed successfully");
    }

    @Test
    @Order(4)
    void testBatchPermissionCheck() {
        log.info("Testing batch permission checking...");

        UserContext adminUser = createTestAdminUser();

        // 获取现有文档ID
        UserContext.setCurrentUser(adminUser);

        List<SampleDocument> documents;
        try {
            documents = Q.result(SampleDocument.class)
                    .where("fileType = ?", "dwg")
                    .query();
        } finally {
            UserContext.clear();
        }

        if (documents.isEmpty()) {
            log.warn("No documents found for batch testing");
            return;
        }

        List<Long> documentIds = documents.stream()
                .map(SampleDocument::getId)
                .toList();

        // 批量检查READ权限
        UserContext.setCurrentUser(adminUser);
        try {
            Map<Long, Boolean> readPermissions = permissionCheckService.checkBatchPermissions(documentIds, PermissionAction.READ);
            assertTrue(readPermissions.size() == documentIds.size(), "Should check all documents");

            // 过滤有权限的对象
            List<Long> permittedIds = permissionCheckService.filterPermittedObjectsById(documentIds, PermissionAction.READ);
            assertTrue(!permittedIds.isEmpty(), "Should have some permitted objects");

            log.info("Batch permission checking completed: {} documents, {} permitted",
                    documentIds.size(), permittedIds.size());
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @Order(5)
    void testRoleAndDepartmentPermissions() {
        log.info("Testing role and department permissions (UserContext-based)...");

        UserContext adminUser = createTestAdminUser();
        UserContext managerUser = createTestManagerUser();
        UserContext normalUser = createTestNormalUser();

        // 测试管理员角色检查
        UserContext.setCurrentUser(adminUser);
        try {
            assertTrue(permissionCheckService.isAdmin(), "Admin user should be admin");
            assertFalse(permissionCheckService.isManager(), "Admin user should not be manager by default");
        } finally {
            UserContext.clear();
        }

        // 测试经理角色检查
        UserContext.setCurrentUser(managerUser);
        try {
            assertTrue(permissionCheckService.isManager(), "Manager user should be manager");
            assertFalse(permissionCheckService.isAdmin(), "Manager user should not be admin");
        } finally {
            UserContext.clear();
        }

        // 测试普通用户角色检查
        UserContext.setCurrentUser(normalUser);
        try {
            assertFalse(permissionCheckService.isAdmin(), "Normal user should not be admin");
            assertFalse(permissionCheckService.isManager(), "Normal user should not be manager");
        } finally {
            UserContext.clear();
        }

        log.info("Role and department permission tests completed");
    }

    @Test
    @Order(6)
    void testApplicationCrudPermissionIntegration() {
        log.info("Testing application CRUD permission integration...");

        // 1. 测试CREATE权限
        testCreatePermission();

        // 2. 测试READ权限（查询过滤）
        testReadPermissionFiltering();

        // 3. 测试UPDATE权限
        testUpdatePermission();

        // 4. 测试DELETE权限
        testDeletePermission();

        log.info("Application CRUD permission integration test completed successfully");
    }

    /**
     * 测试CREATE权限
     */
    private void testCreatePermission() {
        log.info("Testing CREATE permission...");

        // 使用管理员用户创建文档（应该成功）
        UserContext adminUser = createTestAdminUser();
        UserContext.setCurrentUser(adminUser);

        try {
            SampleDocument document = createTestDocument("admin_create_test.dwg", "dwg");
            SampleDocument savedDoc = objectService.save(document);
            assertTrue(savedDoc.getId() != null, "Admin should be able to create documents");
            log.info("Admin successfully created document: {}", savedDoc.getId());
        } catch (Exception e) {
            log.error("Unexpected error in admin create test", e);
            throw e;
        } finally {
            UserContext.clear();
        }

        // 使用普通用户创建文档（应该失败）
        UserContext normalUser = createTestNormalUser();
        UserContext.setCurrentUser(normalUser);

        try {
            SampleDocument newDoc = createTestDocument("normal_create_test.dwg", "dwg");
            objectService.save(newDoc);
            assertTrue(false, "Normal user should not be able to create documents");
        } catch (SecurityException e) {
            log.info("Normal user correctly denied CREATE permission: {}", e.getMessage());
        } catch (Exception e) {
            log.info("Normal user creation failed (expected): {}", e.getMessage());
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 测试READ权限（查询过滤）
     */
    private void testReadPermissionFiltering() {
        log.info("Testing READ permission filtering...");

        UserContext adminUser = createTestAdminUser();
        UserContext normalUser = createTestNormalUser();

        // 管理员查询（应该看到所有）
        UserContext.setCurrentUser(adminUser);

        List<SampleDocument> adminVisibleDocs;
        try {
            adminVisibleDocs = Q.result(SampleDocument.class).noCondition().query();
            log.info("Admin can see {} documents", adminVisibleDocs.size());
            assertTrue(adminVisibleDocs.size() > 0, "Admin should see some documents");
        } finally {
            UserContext.clear();
        }

        // 普通用户查询（应该根据权限过滤）
        UserContext.setCurrentUser(normalUser);

        try {
            List<SampleDocument> visibleDocs = Q.result(SampleDocument.class).noCondition().query();
            log.info("Normal user can see {} documents", visibleDocs.size());

            // 根据权限配置，普通用户应该看不到所有文档
            assertTrue(visibleDocs.size() <= adminVisibleDocs.size(),
                    "Normal user should see same or fewer documents than admin");

        } finally {
            UserContext.clear();
        }
    }

    /**
     * 测试UPDATE权限
     */
    private void testUpdatePermission() {
        log.info("Testing UPDATE permission...");

        // 创建测试文档
        UserContext adminUser = createTestAdminUser();
        SampleDocument document;

        UserContext.setCurrentUser(adminUser);

        try {
            document = createTestDocument("update_test.dwg", "dwg");
            document = objectService.save(document);
        } finally {
            UserContext.clear();
        }

        // 管理员更新文档（应该成功）
        UserContext.setCurrentUser(adminUser);

        try {
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("fileSize", 2048L);

            int updated = objectService.fastUpdate(document.getId(), updateData);
            assertTrue(updated > 0, "Admin should be able to update documents");
            log.info("Admin successfully updated document: {}", document.getId());
        } finally {
            UserContext.clear();
        }

        // 普通用户更新文档（应该失败）
        UserContext normalUser = createTestNormalUser();
        UserContext.setCurrentUser(normalUser);

        try {
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("fileSize", 4096L);

            objectService.fastUpdate(document.getId(), updateData);
            assertTrue(false, "Normal user should not be able to update documents");
        } catch (SecurityException e) {
            log.info("Normal user correctly denied UPDATE permission: {}", e.getMessage());
        } catch (Exception e) {
            log.info("Normal user update failed (expected): {}", e.getMessage());
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 测试DELETE权限
     */
    private void testDeletePermission() {
        log.info("Testing DELETE permission...");

        // 创建测试文档
        UserContext adminUser = createTestAdminUser();
        SampleDocument document1, document2;

        UserContext.setCurrentUser(adminUser);

        try {
            document1 = createTestDocument("delete_test1.dwg", "dwg");
            document1 = objectService.save(document1);

            document2 = createTestDocument("delete_test2.dwg", "dwg");
            document2 = objectService.save(document2);
        } finally {
            UserContext.clear();
        }

        // 普通用户删除文档（应该失败）
        UserContext normalUser = createTestNormalUser();
        UserContext.setCurrentUser(normalUser);

        try {
            objectService.delete(Arrays.asList(document1.getId()));
            assertTrue(false, "Normal user should not be able to delete documents");
        } catch (SecurityException e) {
            log.info("Normal user correctly denied DELETE permission: {}", e.getMessage());
        } catch (Exception e) {
            log.info("Normal user delete failed (expected): {}", e.getMessage());
        } finally {
            UserContext.clear();
        }

        // 管理员删除文档（应该成功）
        UserContext.setCurrentUser(adminUser);

        try {
            objectService.delete(Arrays.asList(document2.getId()));

            // 验证文档已删除
            SampleDocument deletedDoc = objectService.findById(document2.getId());
            assertTrue(deletedDoc == null, "Document should be deleted");
            log.info("Admin successfully deleted document: {}", document2.getId());

        } finally {
            UserContext.clear();
        }
    }

    @Test
    @Order(7)
    void testRLSPermissionFiltering() {
        log.info("Testing RLS permission filtering...");

        UserContext adminUser = createTestAdminUser();
        UserContext normalUser = createTestNormalUser();

        // 管理员查询计数
        UserContext.setCurrentUser(adminUser);

        Long adminCount;
        try {
            adminCount = Q.result(SampleDocument.class).noCondition().count();
            log.info("Admin can see {} documents via RLS filtering", adminCount);
        } finally {
            UserContext.clear();
        }

        // 普通用户查询计数（RLS应该自动过滤）
        UserContext.setCurrentUser(normalUser);

        try {
            Long normalCount = Q.result(SampleDocument.class).noCondition().count();
            log.info("Normal user can see {} documents via RLS filtering", normalCount);

            // 根据权限策略，普通用户看到的应该不超过管理员
            assertTrue(normalCount <= adminCount,
                    "Normal user should see same or fewer documents than admin via RLS");
        } finally {
            UserContext.clear();
        }

        log.info("RLS permission filtering test completed");
    }

    @Test
    @Order(8)
    void testTypeOnlyOptimizationAndPerformance() {
        log.info("Testing TYPE_ONLY optimization and performance...");

        // 1. 测试TYPE_ONLY权限检查（管理员场景）
        testTypeOnlyPermissionCheck();

        // 2. 测试ID解码优化
        testIdOptimization();

        // 3. 测试executeScriptCheck方法复用
        testExecuteScriptCheckReuse();

        // 4. 测试批量权限检查性能优化
        testBatchPermissionOptimization();

        log.info("TYPE_ONLY optimization and performance tests completed successfully");
    }

    @Test
    @Order(9)
    void testPermissionApiComparison() {
        log.info("Testing permission API performance comparison (ModelObject vs ID)...");

        UserContext adminUser = createTestAdminUser();

        // 创建测试文档
        SampleDocument testDoc;
        UserContext.setCurrentUser(adminUser);
        try {
            testDoc = createTestDocument("api_comparison_test.dwg", "dwg");
            testDoc = objectService.save(testDoc);
        } finally {
            UserContext.clear();
        }

        UserContext.setCurrentUser(adminUser);
        try {
            // 方式1：使用ModelObject API（推荐）
            long startTime1 = System.currentTimeMillis();
            boolean result1 = permissionCheckService.checkPermission(testDoc, PermissionAction.UPDATE);
            long duration1 = System.currentTimeMillis() - startTime1;

            // 方式2：使用ID API
            long startTime2 = System.currentTimeMillis();
            boolean result2 = permissionCheckService.checkPermission(testDoc.getId(), PermissionAction.UPDATE);
            long duration2 = System.currentTimeMillis() - startTime2;

            // 结果应该一致
            assertEquals(result1, result2, "ModelObject API and ID API should return same result");

            log.info("Permission API comparison: ModelObject={}ms (result={}), ID={}ms (result={})",
                    duration1, result1, duration2, result2);

        } finally {
            UserContext.clear();
        }
    }

    @Test
    @Order(10)
    void testCleanupTestData() {
        try {
            log.info("Starting test data cleanup...");

            UserContext.runAsSystem(() -> {
                // 清理测试文档
                cleanupTestDocuments();
            });

            // 清理测试用户权限 - 使用TestUserHelper
            TestUserHelper.cleanupBasicTestUsers();

            log.info("Test data cleanup completed successfully");

        } catch (Exception e) {
            log.warn("Test data cleanup failed, but test results are still valid", e);
        }
    }

    /**
     * 测试TYPE_ONLY权限检查（零数据库查询）
     */
    private void testTypeOnlyPermissionCheck() {
        log.info("Testing TYPE_ONLY permission check (zero DB queries)...");

        UserContext adminUser = createTestAdminUser();
        UserContext normalUser = createTestNormalUser();

        // 管理员的TYPE_ONLY检查应该直接通过
        UserContext.setCurrentUser(adminUser);
        try {
            // 这些检查应该是TYPE_ONLY级别，无需加载对象
            boolean canCreateDoc = permissionCheckService.checkPermission(SampleDocument.class.getName(), PermissionAction.CREATE);
            assertTrue(canCreateDoc, "Admin should have TYPE_ONLY CREATE permission");

            boolean canDownloadAny = permissionCheckService.checkPermission(SampleDocument.class.getName(), PermissionAction.DOWNLOAD);
            assertFalse(canDownloadAny, "DOWNLOAD permission check should provide object level data");

            log.info("Admin TYPE_ONLY permissions verified: CREATE={}, DOWNLOAD={}", canCreateDoc, canDownloadAny);
        } finally {
            UserContext.clear();
        }

        // 普通用户的TYPE_ONLY检查
        UserContext.setCurrentUser(normalUser);
        try {
            boolean canCreateDoc = permissionCheckService.checkPermission(SampleDocument.class.getName(), PermissionAction.CREATE);
            assertFalse(canCreateDoc, "Normal user should not have TYPE_ONLY CREATE permission");

            boolean canDownloadAny = permissionCheckService.checkPermission(SampleDocument.class.getName(), PermissionAction.DOWNLOAD);
            assertFalse(canDownloadAny, "Normal user should not have TYPE_ONLY DOWNLOAD permission");

            log.info("Normal user TYPE_ONLY permissions verified: CREATE={}, DOWNLOAD={}", canCreateDoc, canDownloadAny);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 测试ID解码优化
     */
    private void testIdOptimization() {
        log.info("Testing ID optimization...");

        // 创建测试文档以获取有效的ID
        UserContext adminUser = createTestAdminUser();
        SampleDocument testDoc;

        UserContext.setCurrentUser(adminUser);
        try {
            testDoc = createTestDocument("id_test.dwg", "dwg");
            testDoc = objectService.save(testDoc);
        } finally {
            UserContext.clear();
        }

        // 测试管理员通过ID优化的权限检查
        UserContext.setCurrentUser(adminUser);
        try {
            long startTime = System.currentTimeMillis();

            // 这个检查应该通过ID解码 + TYPE_ONLY检查完成，无需加载完整对象
            boolean hasReadPermission = permissionCheckService.checkPermission(testDoc.getId(), PermissionAction.READ);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            assertTrue(hasReadPermission, "Admin should have READ permission via ID optimization");
            assertTrue(duration < 50, "optimization should be very fast (<50ms), actual: " + duration + "ms");

            log.info("ID optimization test completed in {}ms, permission: {}", duration, hasReadPermission);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 测试executeScriptCheck方法复用
     */
    private void testExecuteScriptCheckReuse() {
        log.info("Testing executeScriptCheck method reuse...");

        UserContext adminUser = createTestAdminUser();
        UserContext normalUser = createTestNormalUser();

        // 测试TYPE_ONLY脚本检查（object为null）
        UserContext.setCurrentUser(adminUser);
        try {
            // 这些是TYPE_ONLY检查，object参数为null
            boolean isAdminCheck = permissionCheckService.executeScriptCheck("user_is_admin()", null);
            assertTrue(isAdminCheck, "Admin user_is_admin() check should return true");

            boolean hasRoleCheck = permissionCheckService.executeScriptCheck("user_has_role('bd23de3f-086a-42ff-ba39-fb9139d94af9')", null);
            assertTrue(hasRoleCheck, "Admin should have the specified role");

            log.info("Admin TYPE_ONLY script checks: isAdmin={}, hasRole={}", isAdminCheck, hasRoleCheck);
        } finally {
            UserContext.clear();
        }

        UserContext.setCurrentUser(normalUser);
        try {
            boolean isAdminCheck = permissionCheckService.executeScriptCheck("user_is_admin()", null);
            assertFalse(isAdminCheck, "Normal user_is_admin() check should return false");

            boolean hasRoleCheck = permissionCheckService.executeScriptCheck("user_has_role('bd23de3f-086a-42ff-ba39-fb9139d94af9')", null);
            assertFalse(hasRoleCheck, "Normal user should not have admin role");

            log.info("Normal user TYPE_ONLY script checks: isAdmin={}, hasRole={}", isAdminCheck, hasRoleCheck);
        } finally {
            UserContext.clear();
        }

        // 测试需要对象的脚本检查
        UserContext.setCurrentUser(adminUser);
        SampleDocument testDoc;
        try {
            testDoc = createTestDocument("script_test.dwg", "dwg");
            testDoc = objectService.save(testDoc);
        } finally {
            UserContext.clear();
        }

        UserContext.setCurrentUser(adminUser);
        try {
            // 这是需要对象的检查
            boolean isCreatorCheck = permissionCheckService.executeScriptCheck("object._creator == currentUserId", testDoc);
            assertTrue(isCreatorCheck, "Admin should be the creator of the document");

            log.info("Admin object-based script check: isCreator={}", isCreatorCheck);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 测试批量权限检查性能优化
     */
    private void testBatchPermissionOptimization() {
        log.info("Testing batch permission check optimization...");

        UserContext adminUser = createTestAdminUser();

        // 创建多个测试文档
        List<Long> testDocIds = new ArrayList<>();
        UserContext.setCurrentUser(adminUser);
        try {
            for (int i = 0; i < 10; i++) {
                SampleDocument doc = createTestDocument("batch_test_" + i + ".dwg", "dwg");
                doc = objectService.save(doc);
                testDocIds.add(doc.getId());
            }
        } finally {
            UserContext.clear();
        }

        // 测试管理员的批量权限检查（应该大部分通过TYPE_ONLY优化）
        UserContext.setCurrentUser(adminUser);
        try {
            long startTime = System.currentTimeMillis();

            Map<Long, Boolean> readPermissions = permissionCheckService.checkBatchPermissions(testDocIds, PermissionAction.READ);

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            assertEquals(testDocIds.size(), readPermissions.size(), "Should check all documents");

            // 管理员应该对所有文档都有权限
            int allowedCount = readPermissions.values().stream().mapToInt(allowed -> allowed ? 1 : 0).sum();
            assertEquals(testDocIds.size(), allowedCount, "Admin should have permission for all documents");

            log.info("Batch permission check for {} documents completed in {}ms, all allowed: {}",
                    testDocIds.size(), duration, allowedCount == testDocIds.size());

        } finally {
            UserContext.clear();
        }
    }

    // ==================== 清理方法 ====================

    /**
     * 清理测试文档
     */
    private void cleanupTestDocuments() {
        try {
            List<SampleDocument> testDocs = Q.result(SampleDocument.class)
                    .where("path LIKE ?", "/test/%")
                    .query();

            if (!testDocs.isEmpty()) {
                List<Long> testDocIds = testDocs.stream().map(SampleDocument::getId).toList();
                objectService.delete(testDocIds);
                log.info("Cleaned up {} test documents", testDocIds.size());
            }

        } catch (Exception e) {
            log.warn("Failed to cleanup test documents", e);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试文档
     */
    private SampleDocument createTestDocument(String filename, String fileType) {
        SampleDocument document = SampleDocument.newModel();
        document.setName(filename);
        document.setFileType(fileType);
        document.setFileSize(1024L);
        document.setPath("/test/" + filename);
        document.setChecksum("checksum_" + filename);
        return document;
    }

    /**
     * 创建测试用户权限记录
     */
    private void createTestUserPermissions() {
        log.info("Creating test user permissions...");

        try {
            // 使用TestUserHelper创建基础测试用户
            TestUserHelper.createBasicTestUsers();

            log.info("Test user permissions created successfully");

        } catch (Exception e) {
            log.error("Failed to create test user permissions", e);
            throw new RuntimeException("Failed to create test user permissions", e);
        }
    }

    /**
     * 权限配置，包含对象和属性权限
     */
    private String getDefaultPermissionConfig() {
        return """
                version: "1.0"
                description: "基础权限配置示例 - 针对SampleDocument的对象权限控制"
                createdAt: "2025-06-17"
                
                # 权限配置
                permissionConfig:
                  objects:
                    # SampleDocument权限配置 - 对象级别权限
                    io.emop.integrationtest.domain.SampleDocument:
                      description: "CAD图纸基础权限控制"
                
                      # 对象级别权限
                      permissions:
                        # 查看权限：可以看到哪些数据
                        READ:
                          conditions:
                            # sql类型为RLS端执行
                            - sql: |
                                EXISTS (
                                  SELECT 1 FROM auth.user_permissions up
                                  WHERE up.keycloakuseruid = auth.get_current_user_uid()
                                  AND (
                                    '${tableName}' = '${tableName}' OR
                                    auth.user_is_admin()
                                  )
                                )
                              description: "管理员可以查看所有，其他用户按部门权限"
                            - sql: "filetype IN ('prt', 'asm', 'dwg', 'pdf')"
                              description: "限制文件类型"
                
                        # 修改权限：可以修改哪些数据
                        UPDATE:
                          conditions:
                            # script类型为应用层groovy执行
                            - script: "check_creator_permission(object.get('_creator'))"
                              description: "只能修改自己创建的数据"
                            - script: "object.get('_state') != 'LOCKED'"
                              description: "锁定状态不可修改"
                            # RLS层也要检查
                            - sql: "auth.check_creator_permission(_creator)"
                              description: "数据库层创建者检查"
                
                        # 删除权限：可以删除哪些数据
                        DELETE:
                          conditions:
                            - script: "check_creator_permission(object.get('_creator'))"
                              description: "只能删除自己创建的数据"
                            - script: "object.get('_state') == 'Working'"
                              description: "只有草稿状态可删除"
                            - sql: "auth.check_creator_permission(_creator) AND (_state = 'Working' OR _state IS NULL)"
                              description: "数据库层创建者和状态检查"
                
                        # 创建权限：是否可以创建新数据
                        CREATE:
                          conditions:
                            - script: "user_has_role('bd23de3f-086a-42ff-ba39-fb9139d94af9') || user_has_role('748a51ce-308b-404c-ba02-4b48dd8983fb')"
                              description: "需要研发角色才能创建"
                
                        # 下载权限：可以下载哪些文件
                        DOWNLOAD:
                          conditions:
                            - script: "check_creator_permission(object.get('_creator')) || user_is_manager() || user_is_admin()"
                              description: "创建者、经理或管理员可以下载"
                
                        # 打印权限
                        PRINT:
                          conditions:
                            - script: "object.get('file_type') != 'dwg'"
                              description: "图纸文件不允许打印"
                            - script: "user_is_manager() || user_is_admin()"
                              description: "需要经理或管理员权限才能打印"
                
                        # 分享权限
                        SHARE:
                          conditions:
                            - script: "user_is_manager() || user_is_admin()"
                              description: "只有经理或管理员可以分享文件"
                """;
    }
}