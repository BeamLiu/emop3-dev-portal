package io.emop.integrationtest.usecase.permission;

import io.emop.model.common.UserContext;
import io.emop.model.permission.PermissionAction;
import io.emop.model.permission.PermissionConfig;
import io.emop.integrationtest.domain.SampleDocument;
import io.emop.service.S;
import io.emop.service.api.permission.AttributePermissionService;
import io.emop.service.api.permission.PermissionConfigDeploymentService;
import io.emop.service.api.data.ObjectService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 属性级别权限控制测试
 * <p>
 * 测试场景：
 * 1. 财务敏感字段：cost、price字段只有财务人员可见
 * 2. 技术机密字段：techParameter字段只有研发人员可见
 * 4. 状态字段：某些状态字段只有特定角色可以修改
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AttributePermissionTest {

    private AttributePermissionService attributePermissionService;
    private PermissionConfigDeploymentService configDeploymentService;
    private ObjectService objectService;

    @BeforeEach
    void setUp() {
        log.info("Starting attribute permission control test setup...");
        initServices();
        deployAttributePermissionConfig();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @Order(1)
    void testFinancialAttributePermissions() {
        log.info("Testing financial attribute permissions...");

        String objectType = SampleDocument.class.getName();

        // 测试财务用户对财务字段的权限
        UserContext financialUser = createFinancialUser();
        UserContext.setCurrentUser(financialUser);

        try {
            // 财务用户应该能读取成本字段
            boolean canReadCost = attributePermissionService.checkAttributePermission(objectType, "cost", PermissionAction.READ);
            assertTrue(canReadCost, "Financial user should be able to read cost attribute");

            // 财务用户应该能修改成本字段
            boolean canUpdateCost = attributePermissionService.checkAttributePermission(objectType, "cost", PermissionAction.UPDATE);
            assertTrue(canUpdateCost, "Financial user should be able to update cost attribute");

            log.info("Financial user cost permissions: read={}, update={}", canReadCost, canUpdateCost);

        } finally {
            UserContext.clear();
        }

        // 测试普通用户对财务字段的权限
        UserContext normalUser = createNormalUser();
        UserContext.setCurrentUser(normalUser);

        try {
            // 普通用户不应该能读取成本字段
            boolean canReadCost = attributePermissionService.checkAttributePermission(objectType, "cost", PermissionAction.READ);
            assertFalse(canReadCost, "Normal user should not be able to read cost attribute");

            // 普通用户不应该能修改成本字段
            boolean canUpdateCost = attributePermissionService.checkAttributePermission(objectType, "cost", PermissionAction.UPDATE);
            assertFalse(canUpdateCost, "Normal user should not be able to update cost attribute");

            log.info("Normal user cost permissions: read={}, update={}", canReadCost, canUpdateCost);

        } finally {
            UserContext.clear();
        }

        log.info("Financial attribute permissions test completed");
    }

    @Test
    @Order(2)
    void testTechnicalAttributePermissions() {
        log.info("Testing technical attribute permissions...");

        String objectType = SampleDocument.class.getName();

        // 测试研发用户对技术字段的权限
        UserContext rdUser = createRDUser();
        UserContext.setCurrentUser(rdUser);

        try {
            // 研发用户应该能读取技术参数字段
            boolean canReadTech = attributePermissionService.checkAttributePermission(objectType, "techParameter", PermissionAction.READ);
            assertTrue(canReadTech, "RD user should be able to read tech parameter attribute");

            // 研发用户应该能修改技术参数字段
            boolean canUpdateTech = attributePermissionService.checkAttributePermission(objectType, "techParameter", PermissionAction.UPDATE);
            assertTrue(canUpdateTech, "RD user should be able to update tech parameter attribute");

            log.info("RD user tech parameter permissions: read={}, update={}", canReadTech, canUpdateTech);

        } finally {
            UserContext.clear();
        }

        // 测试非研发用户对技术字段的权限
        UserContext normalUser = createNormalUser();
        UserContext.setCurrentUser(normalUser);

        try {
            // 普通用户不应该能读取技术参数字段
            boolean canReadTech = attributePermissionService.checkAttributePermission(objectType, "techParameter", PermissionAction.READ);
            assertFalse(canReadTech, "Normal user should not be able to read tech parameter attribute");

            // 普通用户不应该能修改技术参数字段
            boolean canUpdateTech = attributePermissionService.checkAttributePermission(objectType, "techParameter", PermissionAction.UPDATE);
            assertFalse(canUpdateTech, "Normal user should not be able to update tech parameter attribute");

            log.info("Normal user tech parameter permissions: read={}, update={}", canReadTech, canUpdateTech);

        } finally {
            UserContext.clear();
        }

        log.info("Technical attribute permissions test completed");
    }

    @Test
    @Order(3)
    void testAuditAttributePermissions() {
        log.info("Testing audit attribute permissions...");

        String objectType = SampleDocument.class.getName();

        // 测试普通用户对审计字段的权限
        UserContext normalUser = createNormalUser();
        UserContext.setCurrentUser(normalUser);

        try {
            // 普通用户应该能读取创建者字段
            boolean canReadCreator = attributePermissionService.checkAttributePermission(objectType, "_creator", PermissionAction.READ);
            assertTrue(canReadCreator, "Normal user should be able to read creator attribute");

            // 普通用户不应该能修改创建者字段
            boolean canUpdateCreator = attributePermissionService.checkAttributePermission(objectType, "_creator", PermissionAction.UPDATE);
            assertFalse(canUpdateCreator, "Normal user should not be able to update creator attribute");

            log.info("Normal user audit permissions: read={}, update={}", canReadCreator, canUpdateCreator);

        } finally {
            UserContext.clear();
        }

        // 测试管理员对审计字段的权限
        UserContext adminUser = createAdminUser();
        UserContext.setCurrentUser(adminUser);

        try {
            // 管理员应该能修改创建者字段
            boolean canUpdateCreator = attributePermissionService.checkAttributePermission(objectType, "_creator", PermissionAction.UPDATE);
            assertTrue(canUpdateCreator, "Admin user should be able to update creator attribute");

            log.info("Admin user audit update permission: {}", canUpdateCreator);

        } finally {
            UserContext.clear();
        }

        log.info("Audit attribute permissions test completed");
    }

    @Test
    @Order(4)
    void testStatusAttributePermissions() {
        log.info("Testing status attribute permissions...");

        String objectType = SampleDocument.class.getName();

        // 测试普通用户对状态字段的权限
        UserContext normalUser = createNormalUser();
        UserContext.setCurrentUser(normalUser);

        try {
            // 普通用户应该能读取状态字段
            boolean canReadStatus = attributePermissionService.checkAttributePermission(objectType, "_state", PermissionAction.READ);
            assertTrue(canReadStatus, "Normal user should be able to read state attribute");

            // 普通用户不应该能修改状态字段
            boolean canUpdateStatus = attributePermissionService.checkAttributePermission(objectType, "_state", PermissionAction.UPDATE);
            assertFalse(canUpdateStatus, "Normal user should not be able to update state attribute");

            log.info("Normal user status permissions: read={}, update={}", canReadStatus, canUpdateStatus);

        } finally {
            UserContext.clear();
        }

        // 测试经理对状态字段的权限
        UserContext managerUser = createManagerUser();
        UserContext.setCurrentUser(managerUser);

        try {
            // 经理应该能修改状态字段
            boolean canUpdateStatus = attributePermissionService.checkAttributePermission(objectType, "_state", PermissionAction.UPDATE);
            assertTrue(canUpdateStatus, "Manager user should be able to update state attribute");

            log.info("Manager user status update permission: {}", canUpdateStatus);

        } finally {
            UserContext.clear();
        }

        log.info("Status attribute permissions test completed");
    }

    @Test
    @Order(5)
    void testBatchAttributePermissions() {
        log.info("Testing batch attribute permissions...");

        String objectType = SampleDocument.class.getName();
        List<String> attributes = Arrays.asList("cost", "techParameter", "_creator", "_state", "fileSize");

        // 测试财务用户的批量权限
        UserContext financialUser = createFinancialUser();
        UserContext.setCurrentUser(financialUser);

        try {
            Map<String, Boolean> readPermissions = attributePermissionService.checkBatchAttributePermissions(
                    objectType, attributes, PermissionAction.READ);

            assertTrue(readPermissions.get("cost"), "Financial user should read cost");
            assertFalse(readPermissions.get("techParameter"), "Financial user should not read tech parameter");
            assertTrue(readPermissions.get("_creator"), "Financial user should read creator");
            assertTrue(readPermissions.get("fileSize"), "Financial user should read file size");

            log.info("Financial user batch read permissions: {}", readPermissions);

        } finally {
            UserContext.clear();
        }

        // 测试研发用户的批量权限
        UserContext rdUser = createRDUser();
        UserContext.setCurrentUser(rdUser);

        try {
            Map<String, Boolean> updatePermissions = attributePermissionService.checkBatchAttributePermissions(
                    objectType, attributes, PermissionAction.UPDATE);

            assertFalse(updatePermissions.get("cost"), "RD user should not update cost");
            assertTrue(updatePermissions.get("techParameter"), "RD user should update tech parameter");
            assertFalse(updatePermissions.get("_creator"), "RD user should not update creator");
            assertTrue(updatePermissions.get("fileSize"), "RD user should update file size");

            log.info("RD user batch update permissions: {}", updatePermissions);

        } finally {
            UserContext.clear();
        }

        log.info("Batch attribute permissions test completed");
    }

    @Test
    @Order(6)
    void testObjectAttributeFiltering() {
        log.info("Testing object attribute filtering...");

        // 创建测试文档
        SampleDocument document = createTestDocumentWithSensitiveData();
        UserContext adminUser = createAdminUser();

        // 使用管理员保存文档
        UserContext.setCurrentUser(adminUser);
        try {
            document = objectService.save(document);
        } finally {
            UserContext.clear();
        }

        // 测试普通用户读取时的属性过滤
        SampleDocument finalDocument = document;
        createNormalUser().run(() -> {
            SampleDocument filteredDoc = attributePermissionService.filterObjectAttributes(finalDocument, PermissionAction.READ);

            // 验证敏感字段被过滤
            assertTrue(filteredDoc.get("cost") == null, "Cost should be filtered out for normal user");
            assertTrue(filteredDoc.get("techParameter") == null, "Tech parameter should be filtered out for normal user");
            assertTrue(filteredDoc.get("fileSize") != null, "File size should not be filtered");

            log.info("Object attribute filtering test completed for normal user");

        });

        // 测试财务用户读取时的属性过滤
        SampleDocument finalDocument1 = document;
        createFinancialUser().run(() -> {
            SampleDocument filteredDoc = attributePermissionService.filterObjectAttributes(finalDocument1, PermissionAction.READ);

            // 验证财务用户可以看到成本字段
            assertTrue(filteredDoc.get("cost") != null, "Cost should not be filtered for financial user");
            assertTrue(filteredDoc.get("techParameter") == null, "Tech parameter should be filtered out for financial user");

            log.info("Object attribute filtering test completed for financial user");

        });

        log.info("Object attribute filtering test completed");
    }

    @Test
    @Order(7)
    void testUpdateAttributeValidation() {
        log.info("Testing update attribute validation...");

        String objectType = SampleDocument.class.getName();

        // 测试普通用户尝试更新敏感字段
        createNormalUser().run(() -> {
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("fileSize", 1024L);
            updateData.put("cost", 100.0); // 普通用户不应该能修改成本
            updateData.put("techParameter", "secret"); // 普通用户不应该能修改技术参数

            // 过滤更新数据
            Map<String, Object> filteredData = attributePermissionService.filterUpdateAttributes(objectType, updateData);

            assertTrue(filteredData.containsKey("fileSize"), "File size should be allowed");
            assertFalse(filteredData.containsKey("cost"), "Cost should be filtered out");
            assertFalse(filteredData.containsKey("techParameter"), "Tech parameter should be filtered out");

            log.info("Filtered update data for normal user: {}", filteredData);

            // 验证更新权限（应该抛出异常）
            assertException(() -> attributePermissionService.validateUpdateAttributePermissions(objectType, updateData));
        });

        // 测试管理员更新
        createAdminUser().run(() -> {
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("fileSize", 1024L);
            updateData.put("cost", 100.0);
            updateData.put("_creator", 1L);

            // 管理员应该能通过验证
            attributePermissionService.validateUpdateAttributePermissions(objectType, updateData);
            log.info("Admin user passed update validation");

        });

        log.info("Update attribute validation test completed");
    }

    // ==================== 辅助方法 ====================

    /**
     * 初始化服务
     */
    private void initServices() {
        this.attributePermissionService = S.service(AttributePermissionService.class);
        this.configDeploymentService = S.service(PermissionConfigDeploymentService.class);
        this.objectService = S.service(ObjectService.class);
        log.info("Attribute permission services initialized");
    }

    /**
     * 部署属性权限配置
     */
    private void deployAttributePermissionConfig() {
        log.info("Deploying attribute permission configuration...");

        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                PermissionConfig config = configDeploymentService.deploy(getAttributePermissionConfig());
                assertTrue(config != null, "Deployed attribute config should not be null");
                assertTrue(config.getAttributeRules() != null && !config.getAttributeRules().isEmpty(),
                        "Deployed config should have attribute rules");
                log.info("Successfully deployed attribute permission config with {} attribute rules",
                        config.getAttributeRules().size());
            });
        });
    }

    /**
     * 创建带有敏感数据的测试文档
     */
    private SampleDocument createTestDocumentWithSensitiveData() {
        SampleDocument document = SampleDocument.newModel();
        document.setName("sensitive_test.dwg");
        document.setFileType("dwg");
        document.setFileSize(1024L);
        document.setPath("/test/sensitive_test.dwg");
        document.setChecksum("checksum_sensitive");

        // 设置敏感字段
        document.set("cost", 1000.0);
        document.set("techParameter", "secret_parameter");

        return document;
    }

    /**
     * 创建财务用户
     */
    private UserContext createFinancialUser() {
        return UserContext.builder()
                .userId(10L)
                .userUid("finance-test-uid")
                .username("finance_user")
                .authorities(Arrays.asList("USER", "FINANCE", "finance-role-uid"))
                .groups(Arrays.asList("finance-dept-uid"))
                .build();
    }

    /**
     * 创建研发用户
     */
    private UserContext createRDUser() {
        return UserContext.builder()
                .userId(11L)
                .userUid("rd-test-uid")
                .username("rd_user")
                .authorities(Arrays.asList("USER", "RD", "rd-role-uid"))
                .groups(Arrays.asList("rd-dept-uid"))
                .build();
    }

    /**
     * 创建管理员用户
     */
    private UserContext createAdminUser() {
        return UserContext.builder()
                .userId(1L)
                .userUid("admin-test-uid")
                .username("admin")
                .authorities(Arrays.asList("ADMIN", "USER"))
                .groups(Arrays.asList("admin-dept-uid"))
                .build();
    }

    /**
     * 创建经理用户
     */
    private UserContext createManagerUser() {
        return UserContext.builder()
                .userId(2L)
                .userUid("manager-test-uid")
                .username("manager")
                .authorities(Arrays.asList("MANAGER", "USER"))
                .groups(Arrays.asList("manager-dept-uid"))
                .build();
    }

    /**
     * 创建普通用户
     */
    private UserContext createNormalUser() {
        return UserContext.builder()
                .userId(3L)
                .userUid("user-test-uid")
                .username("user")
                .authorities(Arrays.asList("USER"))
                .groups(Arrays.asList("user-dept-uid"))
                .build();
    }

    /**
     * 获取属性权限配置
     */
    private String getAttributePermissionConfig() {
        return """
                version: "1.0"
                description: "属性级别权限配置示例"
                createdAt: "2025-06-17"

                # 权限配置
                permissionConfig:
                  objects:
                    # SampleDocument属性权限配置
                    io.emop.integrationtest.domain.SampleDocument:
                      description: "文档属性权限控制"
                      
                      # 对象级别权限保持不变
                      permissions:
                        READ:
                          conditions:
                            - script: "true"
                              description: "所有用户都可以读取文档"
                      
                      # 属性级别权限配置
                      attributePermissions:
                        # 财务敏感字段
                        cost:
                          description: "成本字段权限控制"
                          permissions:
                            READ:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_has_role('finance-role-uid') || user_is_admin()"
                                  description: "只有财务人员或管理员可以查看成本"
                            UPDATE:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_has_role('finance-role-uid') || user_is_admin()"
                                  description: "只有财务人员或管理员可以修改成本"
                        
                        price:
                          description: "价格字段权限控制"
                          permissions:
                            READ:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_has_role('finance-role-uid') || user_is_manager() || user_is_admin()"
                                  description: "财务人员、经理或管理员可以查看价格"
                            UPDATE:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_has_role('finance-role-uid') || user_is_admin()"
                                  description: "只有财务人员或管理员可以修改价格"
                        
                        # 技术机密字段
                        techParameter:
                          description: "技术参数字段权限控制"
                          permissions:
                            READ:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_has_role('rd-role-uid') || user_is_admin()"
                                  description: "只有研发人员或管理员可以查看技术参数"
                            UPDATE:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_has_role('rd-role-uid') || user_is_admin()"
                                  description: "只有研发人员或管理员可以修改技术参数"
                        
                        specification:
                          description: "规格字段权限控制"
                          permissions:
                            READ:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_has_role('rd-role-uid') || user_is_manager() || user_is_admin()"
                                  description: "研发人员、经理或管理员可以查看规格"
                            UPDATE:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_has_role('rd-role-uid') || user_is_admin()"
                                  description: "只有研发人员或管理员可以修改规格"
                        
                        # 审计字段 - 只读限制
                        _creator:
                          description: "创建者字段权限控制"
                          permissions:
                            READ:
                              effect: "ALLOW"
                              conditions:
                                - script: "true"
                                  description: "所有用户都可以查看创建者"
                            UPDATE:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_is_admin()"
                                  description: "只有管理员可以修改创建者"
                        
                        # 状态字段
                        _state:
                          description: "状态字段权限控制"
                          permissions:
                            READ:
                              effect: "ALLOW"
                              conditions:
                                - script: "true"
                                  description: "所有用户都可以查看状态"
                            UPDATE:
                              effect: "ALLOW"
                              conditions:
                                - script: "user_is_manager() || user_is_admin()"
                                  description: "只有经理或管理员可以修改状态"
                """;
    }
}