package io.emop.integrationtest.usecase.permission;

import io.emop.model.auth.User;
import io.emop.model.auth.UserPermissions;
import io.emop.model.common.ModelObject;
import io.emop.model.common.UserContext;
import io.emop.model.permission.PermissionAction;
import io.emop.model.permission.PermissionConfig;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleDocument;
import io.emop.service.S;
import io.emop.service.api.lifecycle.LifecycleService;
import io.emop.service.api.permission.PermissionCheckService;
import io.emop.service.api.permission.PermissionConfigDeploymentService;
import io.emop.service.api.data.ObjectService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * PLM系统复杂权限测试类 - 基于UserContext直接计算权限
 * <p>
 * 测试复杂的PLM业务场景：
 * 1. CAD设计文档的跨部门权限控制
 * 2. BOM物料清单的成本敏感信息控制
 * 3. 工艺文档的生产制造权限
 * 4. 质量文档的质量管控权限
 * 5. 项目文档的跨部门协作权限
 * 6. 不同状态下的权限变化
 * 7. 批量权限检查性能测试
 * 8. RLS集成效果测试
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlmComplexPermissionTest {

    // 服务依赖
    private PermissionCheckService permissionCheckService;
    private PermissionConfigDeploymentService configDeploymentService;
    private ObjectService objectService;
    private LifecycleService lifecycleService;

    // 测试时间戳，用于避免path重复
    private final String testTimestamp;

    // 测试角色UID常量
    private static final String TEST_RD_MANAGER = "test_rd_manager_role_uid";
    private static final String TEST_RD_ENGINEER = "test_rd_engineer_role_uid";
    private static final String TEST_PROCESS_MANAGER = "test_process_manager_role_uid";
    private static final String TEST_PROCESS_ENGINEER = "test_process_engineer_role_uid";
    private static final String TEST_QUALITY_MANAGER = "test_quality_manager_role_uid";
    private static final String TEST_QUALITY_ENGINEER = "test_quality_engineer_role_uid";
    private static final String TEST_PROJECT_MANAGER = "test_project_manager_role_uid";
    private static final String TEST_PROJECT_MEMBER = "test_project_member_role_uid";
    private static final String TEST_FINANCE_MANAGER = "test_finance_manager_role_uid";
    private static final String TEST_NORMAL_USER = "test_normal_user_role_uid";

    // 测试部门UID常量
    private static final String TEST_RD_DEPT = "test_rd_dept_uid";
    private static final String TEST_PROCESS_DEPT = "test_process_dept_uid";
    private static final String TEST_QUALITY_DEPT = "test_quality_dept_uid";
    private static final String TEST_PRODUCTION_DEPT = "test_production_dept_uid";
    private static final String TEST_FINANCE_DEPT = "test_finance_dept_uid";
    private Long userId;


    public PlmComplexPermissionTest() {
        // 生成测试时间戳，格式：yyyyMMdd_HHmmss_SSS
        this.testTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        log.info("Initialized PLM test with timestamp: {}", testTimestamp);
    }

    @BeforeEach
    void setUp() {
        log.info("Starting PLM complex permission system integration test setup with timestamp: {}...", testTimestamp);

        userId = Q.result(User.class).where("username=?", "admin").first().getId();
        // 初始化服务
        initServices();

        // 1. 部署复杂权限配置
        deployComplexPlmPermissionConfig();

        // 2. 创建测试用户和权限数据
        createComplexTestUsers();

        // 3. 创建各类测试文档数据
        createComplexTestDocuments();
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
        this.lifecycleService = S.service(LifecycleService.class);

        log.info("Initialized services for PLM complex permission testing");
    }

    /**
     * 部署复杂的PLM权限配置
     */
    private void deployComplexPlmPermissionConfig() {
        log.info("Deploying complex PLM permission configuration...");

        try {
            // 读取复杂的PLM权限配置文件
            String configYaml = loadComplexPlmPermissionConfigYaml();

            // 使用bypass权限部署配置
            UserContext.runAsSystem(() -> {
                UserContext.ensureCurrent().byPass(() -> {
                    PermissionConfig deployedConfig = configDeploymentService.deploy(configYaml);

                    assertTrue(deployedConfig != null, "Deployed PLM config should not be null");
                    assertTrue(deployedConfig.getRules() != null && !deployedConfig.getRules().isEmpty(),
                            "Deployed PLM config should have rules");

                    log.info("Successfully deployed complex PLM permission config with {} rules",
                            deployedConfig.getRules().size());
                });
            });

        } catch (Exception e) {
            throw new RuntimeException("Failed to deploy complex PLM permission configuration", e);
        }
    }

    /**
     * 创建复杂测试用户和权限数据
     */
    private void createComplexTestUsers() {
        log.info("Creating complex test users and permission data...");

        UserContext.runAsSystem(() -> {
            // 创建各部门测试用户
            createComplexUserPermissions(createPlmRdManagerUser());
            createComplexUserPermissions(createPlmRdEngineerUser());
            createComplexUserPermissions(createPlmProcessManagerUser());
            createComplexUserPermissions(createPlmProcessEngineerUser());
            createComplexUserPermissions(createPlmQualityManagerUser());
            createComplexUserPermissions(createPlmQualityEngineerUser());
            createComplexUserPermissions(createPlmProjectManagerUser());
            createComplexUserPermissions(createPlmProjectMemberUser());
            createComplexUserPermissions(createPlmFinanceManagerUser());
            createComplexUserPermissions(createPlmNormalUser());
        });

        log.info("Complex test users and permissions created successfully");
    }

    /**
     * 创建复杂用户权限记录
     */
    private void createComplexUserPermissions(UserContext userContext) {
        try {
            // 查找是否已存在
            UserPermissions existing = Q.result(UserPermissions.class)
                    .where("keycloakUserUid = ?", userContext.getUserUid())
                    .first();

            if (existing != null) {
                log.info("Complex user permissions already exist for: {}", userContext.getUsername());
                return;
            }

            UserPermissions userPermissions = UserPermissions.newModel(
                    userContext.getUserUid(), userContext.getUsername());

            userPermissions.setAllRoleUids(userContext.getAuthorities());

            // 设置部门信息
            List<String> deptUids = determineComplexDepartmentUids(userContext);
            userPermissions.setAllGroupUids(deptUids);
            userPermissions.setDefaultGroupUid(deptUids.isEmpty() ? null : deptUids.get(0));

            // 设置常用权限标识
            userPermissions.setIsAdmin(userContext.getAuthorities().contains("ADMIN"));
            userPermissions.setIsManager(userContext.getAuthorities().contains("MANAGER") ||
                    userContext.getAuthorities().contains(TEST_RD_MANAGER) ||
                    userContext.getAuthorities().contains(TEST_PROCESS_MANAGER) ||
                    userContext.getAuthorities().contains(TEST_QUALITY_MANAGER) ||
                    userContext.getAuthorities().contains(TEST_PROJECT_MANAGER) ||
                    userContext.getAuthorities().contains(TEST_FINANCE_MANAGER));

            userPermissions.setUserId(userId);
            userPermissions.markSynced();
            objectService.save(userPermissions);

            log.info("Created complex user permissions for: {}", userContext.getUsername());

        } catch (Exception e) {
            log.error("Failed to create complex user permissions for: {}", userContext.getUsername(), e);
        }
    }

    /**
     * 根据用户角色确定复杂部门UID
     */
    private List<String> determineComplexDepartmentUids(UserContext userContext) {
        List<String> authorities = userContext.getAuthorities();
        List<String> deptUids = new ArrayList<>();

        if (authorities.contains(TEST_RD_MANAGER) || authorities.contains(TEST_RD_ENGINEER)) {
            deptUids.add(TEST_RD_DEPT);
        }
        if (authorities.contains(TEST_PROCESS_MANAGER) || authorities.contains(TEST_PROCESS_ENGINEER)) {
            deptUids.add(TEST_PROCESS_DEPT);
        }
        if (authorities.contains(TEST_QUALITY_MANAGER) || authorities.contains(TEST_QUALITY_ENGINEER)) {
            deptUids.add(TEST_QUALITY_DEPT);
        }
        if (authorities.contains(TEST_FINANCE_MANAGER)) {
            deptUids.add(TEST_FINANCE_DEPT);
        }

        return deptUids;
    }

    /**
     * 创建各类复杂测试文档（不再需要创建用户权限数据）
     */
    private void createComplexTestDocuments() {
        log.info("Creating complex test documents for different scenarios...");

        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                // 创建CAD设计文档
                createComplexCadTestDocuments();

                // 创建BOM文档
                createComplexBomTestDocuments();

                // 创建工艺文档
                createComplexProcessTestDocuments();

                // 创建质量文档
                createComplexQualityTestDocuments();

                // 创建项目文档
                createComplexProjectTestDocuments();
            });
        });

        log.info("Complex test documents created successfully");
    }

    /**
     * 创建复杂CAD测试文档
     */
    private void createComplexCadTestDocuments() {
        // 创建不同状态的CAD文档
        SampleDocument workingCad = createComplexTestDocument("complex_working_engine.dwg", "CAD");
        workingCad.set("doc_type", "CAD");
        workingCad.set("cad_type", "ENGINE");
        workingCad.set("complexity", "high");
        S.service(ObjectService.class).save(workingCad);

        SampleDocument reviewCad = createComplexTestDocument("complex_engine_review.dwg", "CAD");
        reviewCad.set("doc_type", "CAD");
        reviewCad.set("cad_type", "PUMP");
        reviewCad.set("complexity", "medium");
        S.service(LifecycleService.class).moveToState(objectService.save(reviewCad), "UnderReview");

        SampleDocument releasedCad = createComplexTestDocument("complex_released_pump.dwg", "CAD");
        releasedCad.set("doc_type", "CAD");
        releasedCad.set("cad_type", "VALVE");
        releasedCad.set("complexity", "low");
        S.service(LifecycleService.class).moveToState(objectService.save(releasedCad), "Released");

        SampleDocument frozenCad = createComplexTestDocument("complex_frozen_motor.dwg", "CAD");
        frozenCad.set("doc_type", "CAD");
        frozenCad.set("cad_type", "MOTOR");
        frozenCad.set("complexity", "high");
        S.service(LifecycleService.class).moveToState(objectService.save(frozenCad), "Frozen");
    }

    /**
     * 创建复杂BOM测试文档
     */
    private void createComplexBomTestDocuments() {
        // 工程BOM
        SampleDocument ebom = createComplexTestDocument("complex_engine_ebom.xml", "BOM");
        ebom.set("doc_type", "BOM");
        ebom.set("bom_type", "EBOM");
        ebom.set("cost", 15000.00);  // 敏感成本信息
        ebom.set("material_count", 150);
        S.service(LifecycleService.class).moveToState(objectService.save(ebom), "Released");

        // 制造BOM
        SampleDocument mbom = createComplexTestDocument("complex_engine_mbom.xml", "BOM");
        mbom.set("doc_type", "BOM");
        mbom.set("bom_type", "MBOM");
        mbom.set("cost", 16500.00);
        mbom.set("material_count", 180);
        objectService.save(mbom);

        // 采购BOM
        SampleDocument pbom = createComplexTestDocument("complex_engine_pbom.xml", "BOM");
        pbom.set("doc_type", "BOM");
        pbom.set("bom_type", "PBOM");
        pbom.set("cost", 18000.00);
        pbom.set("material_count", 200);
        objectService.save(pbom);
    }

    /**
     * 创建复杂工艺测试文档
     */
    private void createComplexProcessTestDocuments() {
        // 通用工艺文档
        SampleDocument generalProcess = createComplexTestDocument("complex_machining_process.pdf", "PROCESS");
        generalProcess.set("doc_type", "PROCESS");
        generalProcess.set("process_type", "MACHINING");
        generalProcess.set("difficulty", "medium");
        S.service(LifecycleService.class).moveToState(objectService.save(generalProcess), "Released");

        // 质控工艺文档
        SampleDocument qualityProcess = createComplexTestDocument("complex_quality_control_process.pdf", "PROCESS");
        qualityProcess.set("doc_type", "PROCESS");
        qualityProcess.set("process_type", "QUALITY_CONTROL");
        qualityProcess.set("difficulty", "high");
        objectService.save(qualityProcess);

        // 装配工艺文档
        SampleDocument assemblyProcess = createComplexTestDocument("complex_assembly_process.pdf", "PROCESS");
        assemblyProcess.set("doc_type", "PROCESS");
        assemblyProcess.set("process_type", "ASSEMBLY");
        assemblyProcess.set("difficulty", "low");
        objectService.save(assemblyProcess);
    }

    /**
     * 创建复杂质量测试文档
     */
    private void createComplexQualityTestDocuments() {
        // 设计标准
        SampleDocument designStd = createComplexTestDocument("complex_design_standard.pdf", "QUALITY");
        designStd.set("doc_type", "QUALITY");
        designStd.set("doc_category", "DESIGN_STANDARD");
        designStd.set("iso_level", "ISO9001");
        S.service(LifecycleService.class).moveToState(objectService.save(designStd), "Released");

        // 生产标准
        SampleDocument productionStd = createComplexTestDocument("complex_production_standard.pdf", "QUALITY");
        productionStd.set("doc_type", "QUALITY");
        productionStd.set("doc_category", "PRODUCTION_STANDARD");
        productionStd.set("iso_level", "ISO14001");
        S.service(LifecycleService.class).moveToState(objectService.save(productionStd), "Released");

        // 检验标准
        SampleDocument inspectionStd = createComplexTestDocument("complex_inspection_standard.pdf", "QUALITY");
        inspectionStd.set("doc_type", "QUALITY");
        inspectionStd.set("doc_category", "INSPECTION_STANDARD");
        inspectionStd.set("iso_level", "ISO45001");
        S.service(LifecycleService.class).moveToState(objectService.save(inspectionStd), "UnderReview");
    }

    /**
     * 创建复杂项目测试文档
     */
    private void createComplexProjectTestDocuments() {
        // 项目计划文档
        SampleDocument projectPlan = createComplexTestDocument("complex_project_plan.docx", "PROJECT");
        projectPlan.set("doc_type", "PROJECT");
        projectPlan.set("project_id", 3001L);
        projectPlan.set("project_leader", getUserId(createPlmRdManagerUser()));
        projectPlan.set("project_members", Arrays.asList(
                String.valueOf(getUserId(createPlmRdEngineerUser())),
                String.valueOf(getUserId(createPlmProcessEngineerUser())),
                String.valueOf(getUserId(createPlmQualityEngineerUser()))
        ));
        objectService.save(projectPlan);

        // 项目进度文档
        SampleDocument projectProgress = createComplexTestDocument("complex_project_progress.xlsx", "PROJECT");
        projectProgress.set("doc_type", "PROJECT");
        projectProgress.set("project_id", 3002L);
        projectProgress.set("project_leader", getUserId(createPlmProjectManagerUser()));
        projectProgress.set("project_members", Arrays.asList(
                String.valueOf(getUserId(createPlmProcessManagerUser())),
                String.valueOf(getUserId(createPlmQualityManagerUser()))
        ));
        objectService.save(projectProgress);
    }

    @Test
    @Order(1)
    void testCadDocumentComplexPermissions() {
        log.info("Testing CAD document complex permissions...");

        // 测试用户
        UserContext rdManager = createPlmRdManagerUser();
        UserContext rdEngineer = createPlmRdEngineerUser();
        UserContext processManager = createPlmProcessManagerUser();
        UserContext processEngineer = createPlmProcessEngineerUser();

        // 研发部门权限测试 - 能够查看所有CAD文档
        SampleDocument workingCad = rdManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_working_engine.dwg")).first());
        assertNotNull(workingCad, "RD Manager should read working CAD documents");

        SampleDocument releasedCad = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_released_pump.dwg")).first());
        assertNotNull(releasedCad, "RD Engineer should read released CAD documents");

        SampleDocument frozenCad = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_frozen_motor.dwg")).first());
        assertNotNull(frozenCad, "RD Engineer should read frozen CAD documents");

        // 工艺部门权限测试 - 只能查看已发布和冻结的CAD
        SampleDocument workingCadByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_working_engine.dwg")).first());
        assertNull(workingCadByProcess, "Process Engineer should not read working CAD");

        SampleDocument releasedCadByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_released_pump.dwg")).first());
        assertNotNull(releasedCadByProcess, "Process Engineer should read released CAD");

        SampleDocument frozenCadByProcess = processManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_frozen_motor.dwg")).first());
        assertNotNull(frozenCadByProcess, "Process Manager should read frozen CAD");

        // 修改权限测试（使用checkPermission因为这是操作权限，不是查询权限）
        assertTrue(checkPermission(workingCad, PermissionAction.UPDATE, rdEngineer),
                "RD Engineer should update working CAD");
        assertFalse(checkPermission(releasedCad, PermissionAction.UPDATE, rdManager),
                "RD Manager should not update released CAD");
        assertFalse(checkPermission(releasedCad, PermissionAction.UPDATE, processEngineer),
                "Process Engineer should not update CAD");

        // 下载权限测试
        assertTrue(checkPermission(releasedCad, PermissionAction.DOWNLOAD, rdEngineer),
                "RD Engineer should download CAD");
        assertTrue(checkPermission(releasedCad, PermissionAction.DOWNLOAD, processManager),
                "Process Manager should download released CAD");
        assertFalse(checkPermission(workingCad, PermissionAction.DOWNLOAD, processEngineer),
                "Process Engineer should not download working CAD");

        log.info("CAD document complex permission tests completed successfully");
    }

    @Test
    @Order(2)
    void testBomDocumentComplexPermissions() {
        log.info("Testing BOM document complex permissions...");

        UserContext rdEngineer = createPlmRdEngineerUser();
        UserContext processEngineer = createPlmProcessEngineerUser();
        UserContext financeManager = createPlmFinanceManagerUser();

        // 研发部门查看工程BOM
        SampleDocument ebom = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_engine_ebom.xml")).first());
        assertNotNull(ebom, "RD Engineer should read EBOM");

        // 工艺部门查看制造BOM和工程BOM
        SampleDocument mbomByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_engine_mbom.xml")).first());
        assertNotNull(mbomByProcess, "Process Engineer should read MBOM");

        SampleDocument ebomByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_engine_ebom.xml")).first());
        assertNotNull(ebomByProcess, "Process Engineer should read EBOM");

        // 财务部门查看所有BOM（包含成本）
        SampleDocument ebomByFinance = financeManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_engine_ebom.xml")).first());
        assertNotNull(ebomByFinance, "Finance Manager should read EBOM with cost info");

        SampleDocument mbomByFinance = financeManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_engine_mbom.xml")).first());
        assertNotNull(mbomByFinance, "Finance Manager should read MBOM with cost info");

        SampleDocument pbomByFinance = financeManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_engine_pbom.xml")).first());
        assertNotNull(pbomByFinance, "Finance Manager should read PBOM with cost info");

        // 修改权限测试
        assertFalse(checkPermission(ebom, PermissionAction.UPDATE, rdEngineer),
                "RD Engineer should not update EBOM when released");
        assertTrue(checkPermission(mbomByProcess, PermissionAction.UPDATE, processEngineer),
                "Process Engineer should update MBOM when working");

        log.info("BOM document complex permission tests completed successfully");
    }

    @Test
    @Order(3)
    void testProcessDocumentComplexPermissions() {
        log.info("Testing process document complex permissions...");

        UserContext processManager = createPlmProcessManagerUser();
        UserContext processEngineer = createPlmProcessEngineerUser();
        UserContext qualityEngineer = createPlmQualityEngineerUser();

        // 工艺部门可以查看所有工艺文档
        SampleDocument generalProcess = processManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_machining_process.pdf")).first());
        assertNotNull(generalProcess, "Process Manager should read all process documents");

        SampleDocument assemblyProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_assembly_process.pdf")).first());
        assertNotNull(assemblyProcess, "Process Engineer should read all process documents");

        // 质量部门查看质控相关工艺
        SampleDocument qualityProcess = qualityEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_quality_control_process.pdf")).first());
        assertNotNull(qualityProcess, "Quality Engineer should read quality control process");

        SampleDocument generalProcessByQuality = qualityEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_machining_process.pdf")).first());
        assertNull(generalProcessByQuality, "Quality Engineer should not read general process");

        // 修改权限测试
        assertTrue(checkPermission(assemblyProcess, PermissionAction.UPDATE, processEngineer),
                "Process Engineer should update working process documents");
        assertTrue(checkPermission(qualityProcess, PermissionAction.UPDATE, processEngineer),
                "Process Engineer should update working documents");

        // 审批权限测试
        assertTrue(checkPermission(generalProcess, PermissionAction.APPROVE, processManager),
                "Process Manager should approve process documents");

        log.info("Process document complex permission tests completed successfully");
    }

    @Test
    @Order(4)
    void testQualityDocumentComplexPermissions() {
        log.info("Testing quality document complex permissions...");

        UserContext qualityManager = createPlmQualityManagerUser();
        UserContext qualityEngineer = createPlmQualityEngineerUser();
        UserContext rdEngineer = createPlmRdEngineerUser();

        // 质量部门查看所有质量文档
        SampleDocument designStd = qualityManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_design_standard.pdf")).first());
        assertNotNull(designStd, "Quality Manager should read all quality documents");

        SampleDocument inspectionStd = qualityEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_inspection_standard.pdf")).first());
        assertNotNull(inspectionStd, "Quality Engineer should read all quality documents");

        // 研发部门查看设计相关质量标准
        SampleDocument designStdByRd = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_design_standard.pdf")).first());
        assertNotNull(designStdByRd, "RD Engineer should read design quality standards");

        SampleDocument productionStdByRd = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_production_standard.pdf")).first());
        assertNull(productionStdByRd, "RD Engineer should not read production standards");

        // 修改权限测试
        assertTrue(checkPermission(inspectionStd, PermissionAction.UPDATE, qualityEngineer),
                "Quality Engineer should update under-review quality documents");

        // 审批权限测试
        assertTrue(checkPermission(designStd, PermissionAction.APPROVE, qualityManager),
                "Quality Manager should approve quality documents");

        log.info("Quality document complex permission tests completed successfully");
    }

    @Test
    @Order(5)
    void testProjectDocumentComplexPermissions() {
        log.info("Testing project document complex cross-department permissions...");

        UserContext projectManager = createPlmProjectManagerUser();
        UserContext rdManager = createPlmRdManagerUser();
        UserContext rdEngineer = createPlmRdEngineerUser();
        UserContext processEngineer = createPlmProcessEngineerUser();
        UserContext qualityEngineer = createPlmQualityEngineerUser();
        UserContext normalUser = createPlmNormalUser();

        // 项目经理查看所有项目文档
        SampleDocument projectPlan = projectManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_project_plan.docx")).first());
        assertNotNull(projectPlan, "Project Manager should read all project documents");

        SampleDocument projectProgress = projectManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_project_progress.xlsx")).first());
        assertNotNull(projectProgress, "Project Manager should read all project documents");

        // 测试第一个项目：project_plan - 项目负责人是rdManager，成员包括rdEngineer, processEngineer, qualityEngineer
        // 项目负责人（rdManager）可以查看负责的项目文档（根据project_leader字段）
        SampleDocument projectPlanByRdManager = rdManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_project_plan.docx")).first());
        assertNotNull(projectPlanByRdManager, "Project Leader(rdManager) should read project documents they lead");

        // 项目成员可以查看参与的项目文档（根据project_members字段）
        SampleDocument projectPlanByRdEngineer = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_project_plan.docx")).first());
        assertNotNull(projectPlanByRdEngineer, "Project member(rdEngineer) should read project documents");

        SampleDocument projectPlanByProcessEngineer = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_project_plan.docx")).first());
        assertNotNull(projectPlanByProcessEngineer, "Project member(processEngineer) should read project documents");

        SampleDocument projectPlanByQualityEngineer = qualityEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_project_plan.docx")).first());
        assertNotNull(projectPlanByQualityEngineer, "Project member(qualityEngineer) should read project documents");

        // 测试第二个项目：project_progress - 项目负责人是projectManager，成员包括processManager, qualityManager
        // 注意：rdManager不是project_progress的负责人或成员，不应该能看到
        SampleDocument projectProgressByRdManager = rdManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_project_progress.xlsx")).first());
        assertNull(projectProgressByRdManager, "rdManager should not read project documents they don't participate in");

        // 非项目成员不能查看项目文档
        SampleDocument projectPlanByNormalUser = normalUser.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("complex_project_plan.docx")).first());
        assertNull(projectPlanByNormalUser, "Non-member should not read project documents");

        // 项目负责人可以修改项目文档
        assertTrue(checkPermission(projectPlan, PermissionAction.UPDATE, rdManager),
                "Project Leader should update project documents");

        // 项目经理可以审批项目文档
        assertTrue(checkPermission(projectPlan, PermissionAction.APPROVE, projectManager),
                "Project Manager should approve project documents");

        log.info("Project document complex permission tests completed successfully");
    }

    @Test
    @Order(6)
    void testComplexStateBasedPermissions() {
        log.info("Testing complex state-based permission changes...");

        UserContext rdEngineer = createPlmRdEngineerUser();
        UserContext processEngineer = createPlmProcessEngineerUser();
        UserContext rdManager = createPlmRdManagerUser();

        // 创建测试文档并测试状态变更
        UserContext.setCurrentUser(rdEngineer);
        SampleDocument testDoc;
        try {
            testDoc = createComplexTestDocument("state_change_test.dwg", "CAD");
            testDoc.set("doc_type", "CAD");
            testDoc = objectService.save(testDoc);
        } finally {
            UserContext.clear();
        }

        // 工作中状态：研发可以修改，工艺不能查看
        assertTrue(checkPermission(testDoc, PermissionAction.UPDATE, rdEngineer),
                "RD Engineer should update working CAD");

        SampleDocument workingCadByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("state_change_test.dwg")).first());
        assertNull(workingCadByProcess, "Process Engineer should not read working CAD");

        // 模拟状态变更为审核中
        UserContext.setCurrentUser(rdEngineer);
        try {
            testDoc = lifecycleService.applyStateChange(testDoc, "Submit"); // Working -> UnderReview
            testDoc.reload();
        } finally {
            UserContext.clear();
        }

        // 审核中状态：研发可以修改，工艺仍不能查看
        assertTrue(checkPermission(testDoc, PermissionAction.UPDATE, rdEngineer),
                "RD Engineer should update under-review CAD");

        SampleDocument reviewCadByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("state_change_test.dwg")).first());
        assertNull(reviewCadByProcess, "Process Engineer should not read under-review CAD");

        // 模拟状态变更为已发布
        SampleDocument finalTestDoc = testDoc;
        UserContext.runAsSystem(() -> {
            S.service(LifecycleService.class).moveToState(finalTestDoc, "Released");
            finalTestDoc.reload();
        });

        // 已发布状态：工艺可以查看，普通研发不能修改，但经理可以
        SampleDocument releasedCadByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("state_change_test.dwg")).first());
        assertNotNull(releasedCadByProcess, "Process Engineer should read released CAD");

        assertFalse(checkPermission(testDoc, PermissionAction.UPDATE, rdEngineer),
                "RD Engineer should not update released CAD");
        assertFalse(checkPermission(testDoc, PermissionAction.UPDATE, rdManager),
                "RD Manager should not update released CAD");

        log.info("Complex state-based permission tests completed successfully");
    }

    private boolean checkPermission(ModelObject obj, PermissionAction action, UserContext userContext) {
        UserContext.setCurrentUser(userContext);
        try {
            return permissionCheckService.checkPermission(obj, action);
        } finally {
            UserContext.clear();
        }
    }

    @Test
    @Order(7)
    void testBatchPermissionPerformance() {
        log.info("Testing batch permission check performance...");

        UserContext rdManager = createPlmRdManagerUser();

        // 获取所有复杂测试文档ID（通过有权限的用户查询）
        List<SampleDocument> allDocs = rdManager.run(() ->
                Q.result(SampleDocument.class).where("path LIKE ?", getTestPathPrefix() + "%").query());
        List<Long> docIds = allDocs.stream().map(SampleDocument::getId).toList();

        if (docIds.isEmpty()) {
            log.warn("No complex test documents found for batch testing");
            return;
        }

        // 测试批量READ权限检查
        long startTime = System.currentTimeMillis();
        UserContext.setCurrentUser(rdManager);
        Map<Long, Boolean> readPermissions;
        try {
            readPermissions = permissionCheckService.checkBatchPermissions(
                    docIds, PermissionAction.READ);
        } finally {
            UserContext.clear();
        }
        long readDuration = System.currentTimeMillis() - startTime;

        assertTrue(readPermissions.size() == docIds.size(), "Should check all documents for READ");
        log.info("Batch READ permission check completed in {} ms for {} documents", readDuration, docIds.size());

        // 测试批量UPDATE权限检查
        startTime = System.currentTimeMillis();
        UserContext.setCurrentUser(rdManager);
        Map<Long, Boolean> updatePermissions;
        try {
            updatePermissions = permissionCheckService.checkBatchPermissions(
                    docIds, PermissionAction.UPDATE);
        } finally {
            UserContext.clear();
        }
        long updateDuration = System.currentTimeMillis() - startTime;

        assertTrue(updatePermissions.size() == docIds.size(), "Should check all documents for UPDATE");
        log.info("Batch UPDATE permission check completed in {} ms for {} documents", updateDuration, docIds.size());

        // 测试批量DOWNLOAD权限检查
        startTime = System.currentTimeMillis();
        UserContext.setCurrentUser(rdManager);
        Map<Long, Boolean> downloadPermissions;
        try {
            downloadPermissions = permissionCheckService.checkBatchPermissions(
                    docIds, PermissionAction.DOWNLOAD);
        } finally {
            UserContext.clear();
        }
        long downloadDuration = System.currentTimeMillis() - startTime;

        assertTrue(downloadPermissions.size() == docIds.size(), "Should check all documents for DOWNLOAD");
        log.info("Batch DOWNLOAD permission check completed in {} ms for {} documents", downloadDuration, docIds.size());

        // 过滤有权限的对象
        UserContext.setCurrentUser(rdManager);
        List<Long> readPermittedIds;
        try {
            readPermittedIds = permissionCheckService.filterPermittedObjectsById(
                    docIds, PermissionAction.READ);
        } finally {
            UserContext.clear();
        }

        UserContext.setCurrentUser(rdManager);
        List<Long> updatePermittedIds;
        try {
            updatePermittedIds = permissionCheckService.filterPermittedObjectsById(
                    docIds, PermissionAction.UPDATE);
        } finally {
            UserContext.clear();
        }

        log.info("Manager can read {} out of {} documents", readPermittedIds.size(), docIds.size());
        log.info("Manager can update {} out of {} documents", updatePermittedIds.size(), docIds.size());

        log.info("Batch permission performance tests completed successfully");
    }

    @Test
    @Order(8)
    void testRLSComplexIntegration() {
        log.info("Testing RLS complex integration effectiveness...");

        UserContext rdEngineer = createPlmRdEngineerUser();
        UserContext processEngineer = createPlmProcessEngineerUser();
        UserContext qualityEngineer = createPlmQualityEngineerUser();

        // 测试研发工程师通过RLS查询
        List<SampleDocument> rdVisibleDocs = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path LIKE ?", getTestPathPrefix() + "%").query());
        log.info("RD Engineer can see {} documents via RLS", rdVisibleDocs.size());

        // 验证研发工程师主要看到CAD和相关文档
        long cadCount = rdVisibleDocs.stream()
                .filter(doc -> "CAD".equals(doc.get("doc_type")))
                .count();
        assertTrue(cadCount > 0, "RD Engineer should see CAD documents via RLS");

        // 测试工艺工程师通过RLS查询
        List<SampleDocument> processVisibleDocs = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path LIKE ?", getTestPathPrefix() + "%").query());
        log.info("Process Engineer can see {} documents via RLS", processVisibleDocs.size());

        // 验证工艺工程师主要看到工艺文档和已发布CAD
        long processCount = processVisibleDocs.stream()
                .filter(doc -> "PROCESS".equals(doc.get("doc_type")))
                .count();
        assertTrue(processCount > 0, "Process Engineer should see process documents via RLS");

        // 测试质量工程师通过RLS查询
        List<SampleDocument> qualityVisibleDocs = qualityEngineer.run(() ->
                Q.result(SampleDocument.class).where("path LIKE ?", getTestPathPrefix() + "%").query());
        log.info("Quality Engineer can see {} documents via RLS", qualityVisibleDocs.size());

        // 验证质量工程师主要看到质量文档
        long qualityCount = qualityVisibleDocs.stream()
                .filter(doc -> "QUALITY".equals(doc.get("doc_type")))
                .count();
        assertTrue(qualityCount > 0, "Quality Engineer should see quality documents via RLS");

        log.info("RLS complex integration tests completed successfully");
    }

    @Test
    @Order(9)
    void testComplexBusinessScenarios() {
        log.info("Testing complex business scenario combinations...");

        // 场景1：新产品开发流程权限测试
        testNewProductDevelopmentScenario();

        // 场景2：跨部门协作项目权限测试
        testCrossDepartmentCollaborationScenario();

        // 场景3：质量审查流程权限测试
        testQualityReviewProcessScenario();

        log.info("Complex business scenario tests completed successfully");
    }

    /**
     * 测试新产品开发流程场景
     */
    private void testNewProductDevelopmentScenario() {
        log.info("Testing new product development scenario...");

        UserContext rdEngineer = createPlmRdEngineerUser();
        UserContext rdManager = createPlmRdManagerUser();
        UserContext processEngineer = createPlmProcessEngineerUser();

        // 1. 研发工程师创建CAD设计工作中状态
        UserContext.setCurrentUser(rdEngineer);
        SampleDocument newProductCad;
        try {
            newProductCad = createComplexTestDocument("new_product_cad.dwg", "CAD");
            newProductCad.set("doc_type", "CAD");
            newProductCad.set("product_name", "New Engine V2.0");
            newProductCad = objectService.save(newProductCad);
        } finally {
            UserContext.clear();
        }

        // 2. 验证工作中阶段权限 - 创建者（研发工程师）可以查看和修改
        SampleDocument cadByCreator = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("new_product_cad.dwg")).first());
        assertNotNull(cadByCreator, "Creator should read their CAD working");

        assertTrue(checkPermission(newProductCad, PermissionAction.UPDATE, rdEngineer),
                "Creator should update their CAD working");

        // 工艺工程师在工作中阶段不能查看
        SampleDocument cadByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("new_product_cad.dwg")).first());
        assertNull(cadByProcess, "Process Engineer should not see CAD working");

        // 3. 提交审核
        UserContext.setCurrentUser(rdEngineer);
        try {
            SampleDocument currentDoc = Q.result(SampleDocument.class)
                    .where("path = ?", getTestPath("new_product_cad.dwg"))
                    .first();
            if (currentDoc != null) {
                newProductCad = lifecycleService.applyStateChange(currentDoc, "Submit"); // Working -> UnderReview
            }
        } finally {
            UserContext.clear();
        }

        // 4. 验证审核中阶段权限
        assertTrue(checkPermission(newProductCad, PermissionAction.UPDATE, rdEngineer),
                "RD Engineer should update under-review CAD");

        SampleDocument reviewCadByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("new_product_cad.dwg")).first());
        assertNull(reviewCadByProcess, "Process Engineer should still not see under-review CAD");

        // 5. 最终发布
        SampleDocument finalNewProductCad = newProductCad;
        UserContext.runAsSystem(()->{
            S.service(LifecycleService.class).moveToState(finalNewProductCad, "Released");
            finalNewProductCad.reload();
        });

        // 6. 验证发布后权限
        SampleDocument releasedCadByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("new_product_cad.dwg")).first());
        assertNotNull(releasedCadByProcess, "Process Engineer should read released CAD");

        assertFalse(checkPermission(newProductCad, PermissionAction.UPDATE, rdEngineer),
                "RD Engineer should not update released CAD without manager permission");

        log.info("New product development scenario test completed successfully");
    }

    /**
     * 测试跨部门协作项目场景
     */
    private void testCrossDepartmentCollaborationScenario() {
        log.info("Testing cross-department collaboration scenario...");

        UserContext projectManager = createPlmProjectManagerUser();
        UserContext rdEngineer = createPlmRdEngineerUser();
        UserContext processEngineer = createPlmProcessEngineerUser();
        UserContext qualityEngineer = createPlmQualityEngineerUser();
        UserContext outsider = createPlmNormalUser();

        // 1. 项目经理创建跨部门项目
        UserContext.setCurrentUser(projectManager);
        SampleDocument crossDeptProject;
        try {
            crossDeptProject = createComplexTestDocument("cross_dept_project.docx", "PROJECT");
            crossDeptProject.set("doc_type", "PROJECT");
            crossDeptProject.set("project_id", 4001L);
            crossDeptProject.set("project_name", "Cross Department Collaboration");
            crossDeptProject.set("project_leader", getUserId(projectManager));
            crossDeptProject.set("project_members", Arrays.asList(
                    String.valueOf(getUserId(rdEngineer)),
                    String.valueOf(getUserId(processEngineer)),
                    String.valueOf(getUserId(qualityEngineer))
            ));
            crossDeptProject = objectService.save(crossDeptProject);
        } finally {
            UserContext.clear();
        }

        // 2. 验证项目成员权限 - 通过查询验证READ权限
        SampleDocument projectByRdEngineer = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("cross_dept_project.docx")).first());
        assertNotNull(projectByRdEngineer, "Project member (RD) should read project documents");

        SampleDocument projectByProcessEngineer = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("cross_dept_project.docx")).first());
        assertNotNull(projectByProcessEngineer, "Project member (Process) should read project documents");

        SampleDocument projectByQualityEngineer = qualityEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("cross_dept_project.docx")).first());
        assertNotNull(projectByQualityEngineer, "Project member (Quality) should read project documents");

        // 3. 验证非项目成员权限
        SampleDocument projectByOutsider = outsider.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("cross_dept_project.docx")).first());
        assertNull(projectByOutsider, "Non-member should not read project documents");

        // 4. 验证项目经理完整权限
        assertTrue(checkPermission(crossDeptProject, PermissionAction.UPDATE, projectManager),
                "Project Manager should update project documents");
        assertTrue(checkPermission(crossDeptProject, PermissionAction.APPROVE, projectManager),
                "Project Manager should approve project documents");

        log.info("Cross-department collaboration scenario test completed successfully");
    }

    /**
     * 测试质量审查流程场景
     */
    private void testQualityReviewProcessScenario() {
        log.info("Testing quality review process scenario...");

        UserContext qualityManager = createPlmQualityManagerUser();
        UserContext qualityEngineer = createPlmQualityEngineerUser();
        UserContext rdEngineer = createPlmRdEngineerUser();
        UserContext processEngineer = createPlmProcessEngineerUser();

        // 1. 质量工程师创建检验标准草稿
        UserContext.setCurrentUser(qualityEngineer);
        SampleDocument qualityStandard;
        try {
            qualityStandard = createComplexTestDocument("quality_review_standard.pdf", "QUALITY");
            qualityStandard.set("doc_type", "QUALITY");
            qualityStandard.set("doc_category", "INSPECTION_STANDARD");
            qualityStandard = objectService.save(qualityStandard);
        } finally {
            UserContext.clear();
        }

        // 2. 验证工作中阶段权限
        assertTrue(checkPermission(qualityStandard, PermissionAction.UPDATE, qualityEngineer),
                "Quality Engineer should update their quality working");

        SampleDocument qualityDocByManager = qualityManager.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("quality_review_standard.pdf")).first());
        assertNotNull(qualityDocByManager, "Quality Manager should read quality documents");

        // 3. 进入审查阶段
        UserContext.setCurrentUser(qualityManager);
        try {
            qualityStandard = lifecycleService.applyStateChange(qualityStandard, "Submit"); // Working -> UnderReview
        } finally {
            UserContext.clear();
        }

        // 4. 验证审查阶段权限
        assertTrue(checkPermission(qualityStandard, PermissionAction.UPDATE, qualityEngineer),
                "Quality Engineer should update under-review documents");
        assertTrue(checkPermission(qualityStandard, PermissionAction.APPROVE, qualityManager),
                "Quality Manager should approve quality documents");

        // 5. 验证其他部门在审查期间的权限
        SampleDocument qualityDocByRd = rdEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("quality_review_standard.pdf")).first());
        assertNull(qualityDocByRd, "RD Engineer should not read under-review quality documents");

        SampleDocument qualityDocByProcess = processEngineer.run(() ->
                Q.result(SampleDocument.class).where("path=?", getTestPath("quality_review_standard.pdf")).first());
        assertNull(qualityDocByProcess, "Process Engineer should not read under-review quality documents");

        log.info("Quality review process scenario test completed successfully");
    }

    // ==================== 测试用户创建方法 - 基于UserContext的authorities和groups ====================

    private UserContext createPlmRdManagerUser() {
        return UserContext.builder()
                .userId(3001L)
                .userUid("plm-rd-manager-uid")
                .username("plm_rd_manager")
                .authorities(Arrays.asList(TEST_RD_MANAGER, "MANAGER", "USER"))
                .groups(Arrays.asList(TEST_RD_DEPT))
                .build();
    }

    private UserContext createPlmRdEngineerUser() {
        return UserContext.builder()
                .userId(3002L)
                .userUid("plm-rd-engineer-uid")
                .username("plm_rd_engineer")
                .authorities(Arrays.asList(TEST_RD_ENGINEER, "USER"))
                .groups(Arrays.asList(TEST_RD_DEPT))
                .build();
    }

    private UserContext createPlmProcessManagerUser() {
        return UserContext.builder()
                .userId(3003L)
                .userUid("plm-process-manager-uid")
                .username("plm_process_manager")
                .authorities(Arrays.asList(TEST_PROCESS_MANAGER, "MANAGER", "USER"))
                .groups(Arrays.asList(TEST_PROCESS_DEPT))
                .build();
    }

    private UserContext createPlmProcessEngineerUser() {
        return UserContext.builder()
                .userId(3004L)
                .userUid("plm-process-engineer-uid")
                .username("plm_process_engineer")
                .authorities(Arrays.asList(TEST_PROCESS_ENGINEER, "USER"))
                .groups(Arrays.asList(TEST_PROCESS_DEPT))
                .build();
    }

    private UserContext createPlmQualityManagerUser() {
        return UserContext.builder()
                .userId(3005L)
                .userUid("plm-quality-manager-uid")
                .username("plm_quality_manager")
                .authorities(Arrays.asList(TEST_QUALITY_MANAGER, "MANAGER", "USER"))
                .groups(Arrays.asList(TEST_QUALITY_DEPT))
                .build();
    }

    private UserContext createPlmQualityEngineerUser() {
        return UserContext.builder()
                .userId(3006L)
                .userUid("plm-quality-engineer-uid")
                .username("plm_quality_engineer")
                .authorities(Arrays.asList(TEST_QUALITY_ENGINEER, "USER"))
                .groups(Arrays.asList(TEST_QUALITY_DEPT))
                .build();
    }

    private UserContext createPlmProjectManagerUser() {
        return UserContext.builder()
                .userId(3007L)
                .userUid("plm-project-manager-uid")
                .username("plm_project_manager")
                .authorities(Arrays.asList(TEST_PROJECT_MANAGER, "MANAGER", "USER"))
                .groups(Arrays.asList("project_dept_uid"))
                .build();
    }

    private UserContext createPlmProjectMemberUser() {
        return UserContext.builder()
                .userId(3008L)
                .userUid("plm-project-member-uid")
                .username("plm_project_member")
                .authorities(Arrays.asList(TEST_PROJECT_MEMBER, "USER"))
                .groups(Arrays.asList("project_dept_uid"))
                .build();
    }

    private UserContext createPlmFinanceManagerUser() {
        return UserContext.builder()
                .userId(3009L)
                .userUid("plm-finance-manager-uid")
                .username("plm_finance_manager")
                .authorities(Arrays.asList(TEST_FINANCE_MANAGER, "MANAGER", "USER"))
                .groups(Arrays.asList(TEST_FINANCE_DEPT))
                .build();
    }

    private UserContext createPlmNormalUser() {
        return UserContext.builder()
                .userId(3010L)
                .userUid("plm-normal-user-uid")
                .username("plm_normal_user")
                .authorities(Arrays.asList(TEST_NORMAL_USER, "USER"))
                .groups(Arrays.asList("other_dept_uid"))
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建带时间戳的测试文档
     */
    private SampleDocument createComplexTestDocument(String filename, String docType) {
        SampleDocument document = SampleDocument.newModel();
        document.setFileType(docType);
        document.setFileSize(2048L);
        document.setPath(getTestPath(filename));
        document.setChecksum("checksum_" + filename + "_" + testTimestamp);
        document.setName("Complex " + docType + " Document");
        document.setDescription("Complex test document for " + docType + " at " + testTimestamp);
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

    private Long getUserId(UserContext userContext) {
        return userContext.getUserId();
    }

    /**
     * 加载复杂PLM权限配置YAML文件
     */
    private String loadComplexPlmPermissionConfigYaml() {
        try {
            String[] possiblePaths = {
                    "src/test/resources/complex-plm-permission-config.yml",
                    "complex-plm-permission-config.yml",
                    "plm-complex-permission-config.yml"
            };

            for (String path : possiblePaths) {
                try {
                    return Files.readString(Paths.get(path));
                } catch (IOException e) {
                    log.debug("Failed to load complex config from {}: {}", path, e.getMessage());
                }
            }

            // 如果找不到文件，使用内嵌的默认配置
            return getComplexPlmPermissionConfig();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load complex PLM permission configuration", e);
        }
    }

    /**
     * 获取复杂的PLM权限配置 - 更新为基于UserContext的groups进行部门检查
     */
    private String getComplexPlmPermissionConfig() {
        return """
                version: "1.0"
                description: "PLM系统复杂权限配置 - 基于UserContext（authorities和groups）"
                createdAt: "2025-06-14"

                # 权限配置
                permissionConfig:
                  objects:
                    # SampleDocument - 基于UserContext的复杂权限场景
                    SampleDocument:
                      description: "SampleDocument权限控制 - 支持CAD设计、BOM、工艺、质量等多种文档类型（基于UserContext）"
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
                            # 质量部门查看质控工艺
                            - sql: "auth.user_in_group('test_quality_dept_uid') AND (_properties->>'doc_type') = 'PROCESS' AND (_properties->>'process_type') = 'QUALITY_CONTROL'"
                              description: "质量部门查看质控工艺"
                            # 研发部门可以查看工程BOM
                            - sql: "auth.user_in_group('test_rd_dept_uid') AND (_properties->>'doc_type') = 'BOM' AND (_properties->>'bom_type') = 'EBOM'"
                              description: "研发部门查看工程BOM"
                            # 工艺部门可以查看制造BOM和工程BOM
                            - sql: "auth.user_in_group('test_process_dept_uid') AND (_properties->>'doc_type') = 'BOM' AND (_properties->>'bom_type') IN ('MBOM', 'EBOM')"
                              description: "工艺部门查看制造BOM和工程BOM"
                            # 财务部门可以查看所有BOM（包含成本）
                            - sql: "auth.user_in_group('test_finance_dept_uid') AND (_properties->>'doc_type') = 'BOM'"
                              description: "财务部门查看所有BOM含成本信息"
                            # 项目负责人查看项目文档
                            - sql: "(_properties->>'doc_type') = 'PROJECT' AND (_properties->>'project_leader')::text = auth.get_current_user_id()::text"
                              description: "项目负责人查看项目文档"
                            # 项目成员查看项目文档 (使用JSON数组包含检查), 问号转义
                            - sql: "(_properties->>'doc_type') = 'PROJECT' AND (_properties->'project_members')::jsonb ?? auth.get_current_user_id()::text"
                              description: "项目成员查看项目文档"
                            # 项目经理查看所有项目文档
                            - sql: "auth.user_has_role('test_project_manager_role_uid') AND (_properties->>'doc_type') = 'PROJECT'"
                              description: "项目经理查看所有项目文档"
                            # 研发部门查看设计质量标准
                            - sql: "auth.user_in_group('test_rd_dept_uid') AND (_properties->>'doc_type') = 'QUALITY' AND (_properties->>'doc_category') = 'DESIGN_STANDARD'"
                              description: "研发部门查看设计质量标准"
                            # 创建者始终可以查看自己的文档
                            - sql: "auth.check_creator_permission(_creator)"
                              description: "创建者可查看自己的文档"
                            # 管理员可以查看所有文档
                            - sql: "auth.user_is_admin()"
                              description: "管理员可查看所有文档"
                          logic: "OR"
                          
                        UPDATE:
                           conditions:
                             # 研发部门可以修改未发布的CAD文档
                             - script: "user_in_group('test_rd_dept_uid') && object.get('doc_type') == 'CAD' && !(object._state in ['Released', 'Frozen'])"
                               description: "研发部门可修改未发布的CAD文档"
                             # 研发工程师修改未发布的工程BOM
                             - script: "user_has_role('test_rd_engineer_role_uid') && object.get('doc_type') == 'BOM' && object.get('bom_type') == 'EBOM' && !(object._state in ['Released', 'Frozen'])"
                               description: "研发工程师修改未发布的工程BOM"
                             # 工艺工程师修改未发布的制造BOM
                             - script: "user_has_role('test_process_engineer_role_uid') && object.get('doc_type') == 'BOM' && object.get('bom_type') == 'MBOM' && !(object._state in ['Released', 'Frozen'])"
                               description: "工艺工程师修改未发布的制造BOM"
                             # 工艺工程师修改未发布的工艺文档
                             - script: "user_has_role('test_process_engineer_role_uid') && object.get('doc_type') == 'PROCESS' && !(object._state in ['Released', 'Frozen'])"
                               description: "工艺工程师修改未发布的工艺文档"
                             # 质量工程师修改未发布的质量文档
                             - script: "user_has_role('test_quality_engineer_role_uid') && object.get('doc_type') == 'QUALITY' && !(object._state in ['Released', 'Frozen'])"
                               description: "质量工程师修改未发布的质量文档"
                             # 项目负责人可以修改未发布的项目文档
                             - script: "object.get('doc_type') == 'PROJECT' && object.get('project_leader') == currentUserId && !(object._state in ['Released', 'Frozen'])"
                               description: "项目负责人可修改未发布的项目文档"
                             # 创建者可以修改自己创建的未发布文档
                             - script: "check_creator_permission(object._creator) && !(object._state in ['Released', 'Frozen'])"
                               description: "创建者可修改自己创建的未发布文档"
                           logic: "OR"
                          
                        DELETE:
                          conditions:
                            # 创建者可以删除工作中状态的文档
                            - script: "check_creator_permission(object._creator) && object._state == 'Working'"
                              description: "创建者可删除工作中状态文档"
                            # 研发经理可以删除审核中的CAD文档
                            - script: "user_has_role('test_rd_manager_role_uid') && object.get('doc_type') == 'CAD' && object._state == 'UnderReview'"
                              description: "研发经理可删除审核中的CAD文档"
                            # 经理可以删除工作中和审核中的文档
                            - script: "user_is_manager() && object._state in ['Working', 'UnderReview']"
                              description: "经理可删除未发布的文档"
                          logic: "OR"
                          
                        CREATE:
                          conditions:
                            # 研发工程师和研发经理可以创建CAD文档和工程BOM
                            - script: "user_has_role('test_rd_manager_role_uid') || user_has_role('test_rd_engineer_role_uid')"
                              description: "研发工程师和研发经理可创建CAD文档和工程BOM"
                            # 工艺工程师可以创建工艺文档和制造BOM
                            - script: "user_has_role('test_process_engineer_role_uid') || user_has_role('test_process_manager_role_uid')"
                              description: "工艺工程师可创建工艺文档和制造BOM"
                            # 质量工程师可以创建质量文档
                            - script: "user_has_role('test_quality_engineer_role_uid') || user_has_role('test_quality_manager_role_uid')"
                              description: "质量工程师可创建质量文档"
                            # 项目相关角色可以创建项目文档
                            - script: "user_has_role('test_project_manager_role_uid') || user_has_role('test_project_member_role_uid')"
                              description: "项目相关角色可创建项目文档"
                          logic: "OR"
                              
                        DOWNLOAD:
                          conditions:
                            # 研发部门无限制下载CAD和BOM文档
                            - script: "user_in_group('test_rd_dept_uid') && object.get('doc_type') in ['CAD', 'BOM']"
                              description: "研发部门无限制下载CAD和BOM文档"
                            # 工艺部门只能下载已发布的CAD文档，但可以下载所有工艺文档
                            - script: "user_in_group('test_process_dept_uid') && ((object.get('doc_type') == 'CAD' && object._state in ['Released', 'Frozen']) || object.get('doc_type') == 'PROCESS')"
                              description: "工艺部门下载规则"
                            # 质量部门可以下载质量相关文档
                            - script: "user_in_group('test_quality_dept_uid') && object.get('doc_type') == 'QUALITY'"
                              description: "质量部门下载质量文档"
                            # 经理级别可以下载所有文档
                            - script: "user_is_manager()"
                              description: "经理可下载所有文档"
                            # 项目成员可以下载项目文档
                            - script: "object.get('doc_type') == 'PROJECT' && (object.get('project_members')?.contains(currentUserId.toString()) || user_has_role('test_project_manager_role_uid'))"
                              description: "项目成员可下载项目文档"
                          logic: "OR"
                          
                        APPROVE:
                          conditions:
                            # 研发经理审批CAD文档
                            - script: "user_has_role('test_rd_manager_role_uid') && object.get('doc_type') == 'CAD'"
                              description: "研发经理审批CAD文档"
                            # 工艺经理审批工艺文档
                            - script: "user_has_role('test_process_manager_role_uid') && object.get('doc_type') == 'PROCESS'"
                              description: "工艺经理审批工艺文档"
                            # 质量经理审批质量文档
                            - script: "user_has_role('test_quality_manager_role_uid') && object.get('doc_type') == 'QUALITY'"
                              description: "质量经理审批质量文档"
                            # 项目经理审批项目文档
                            - script: "user_has_role('test_project_manager_role_uid') && object.get('doc_type') == 'PROJECT'"
                              description: "项目经理审批项目文档"
                          logic: "OR"

                    # 继承DraftModelObject的默认权限
                    DraftModelObject:
                      description: "草稿对象权限控制，删改查都只能是自己"
                      permissions:
                        READ:
                          conditions:
                            - script: "object._creator == currentUserId"
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