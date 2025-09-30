package io.emop.integrationtest.usecase.common;

import io.emop.model.common.RevisionRule;
import io.emop.model.common.Revisionable;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleDepartment;
import io.emop.integrationtest.domain.SampleMaterial;
import io.emop.integrationtest.domain.SampleMaterialReference;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.dsl.DSLExecutionService;
import io.emop.service.api.metadata.MetadataService;
import io.emop.service.api.data.ObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.List;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 示例部门层级结构测试
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SampleTraitTest {

    private static final String dateCode = String.valueOf(System.currentTimeMillis());

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testSetupMetadata() {
        S.withStrongConsistency(this::setupMetadata);
    }

    @Test
    @Order(2)
    public void testCreateDepartmentStructure() {
        S.withStrongConsistency(this::createDepartmentStructure);
    }

    @Test
    @Order(3)
    public void testQueryDepartmentStructure() {
        S.withStrongConsistency(this::queryDepartmentStructure);
    }

    @Test
    @Order(4)
    public void testBusinessCode() {
        S.withStrongConsistency(this::testBusinessCodeInternal);
    }

    @Test
    @Order(5)
    public void testRevisionableRef() {
        S.withStrongConsistency(this::testRevisionableRefInternal);
    }

    private void setupMetadata() {
        // 使用DSL设置编码生成格式
        String dsl = """
                update type SampleDepartment {
                    codeGenPattern: "DEPT-${attr(name='name')}-${date(pattern='YYMMdd')}-${autoIncrease(scope='PrePath',start='001',step='1',max='999')}"
                    multilang {
                        name.zh_CN: "示例部门"
                        name.en_US: "Sample Department"
                    }
                }
                """;

        // 更新元数据
        S.service(DSLExecutionService.class).execute(dsl);
        S.service(MetadataService.class).reloadTypeDefinitions();
        assertTrue(S.service(MetadataService.class).retrieveFullTypeDefinition("SampleDepartment").getCodeGenPattern().equals("DEPT-${attr(name='name')}-${date(pattern='YYMMdd')}-${autoIncrease(scope='PrePath',start='001',step='1',max='999')}"));
    }

    private void createDepartmentStructure() {
        TimerUtils.measureExecutionTime("创建示例部门层级结构", () -> {
            // 创建总公司
            SampleDepartment headquarters = SampleDepartment.newModel("总公司");
            headquarters.setDescription("公司总部");
            headquarters.generateCode(false); // 会生成类似: DEPT-总公司-240118-001
            headquarters.preAllocateNewIdIfNull();
            S.service(ObjectService.class).save(headquarters);

            // 创建子公司
            SampleDepartment branch1 = SampleDepartment.newModel("上海分公司");
            branch1.setDescription("上海区域分部");
            branch1.generateCode(false); // 会生成类似: DEPT-上海分公司-240118-002
            branch1.setParent(headquarters);
            branch1 = S.service(ObjectService.class).save(branch1); //remote call

            SampleDepartment branch2 = SampleDepartment.newModel("广州分公司");
            branch2.setDescription("广州区域分部");
            branch2.generateCode(false); // 会生成类似: DEPT-广州分公司-240118-003
            branch2.setParent(headquarters);
            S.service(ObjectService.class).save(branch2);

            // 创建部门
            SampleDepartment dept1 = SampleDepartment.newModel("研发部");
            dept1.setDescription("产品研发部门");
            dept1.generateCode(false); // 会生成类似: DEPT-研发部-240118-004
            dept1.setParent(branch1);
            S.service(ObjectService.class).save(dept1);

            SampleDepartment dept2 = SampleDepartment.newModel("销售部");
            dept2.setDescription("销售部门");
            dept2.generateCode(false); // 会生成类似: DEPT-销售部-240118-005
            dept2.setParent(branch1);
            S.service(ObjectService.class).save(dept2);
        });
    }


    private void queryDepartmentStructure() {
        TimerUtils.measureExecutionTime("查询示例部门结构", () -> {
            // 查询总公司
            SampleDepartment headquarters = Q.result(SampleDepartment.class).where("name = ?", "总公司").sortDesc("_creationDate").first();
            assertNotNull(headquarters);
            assertNotNull(headquarters.getCode());

            // 验证子公司
            assertEquals(2, headquarters.queryChildren().size());

            // 验证上海分公司的部门
            SampleDepartment branch1 = headquarters.queryChildren().get(0);
            assertEquals(2, branch1.queryChildren().size());
            assertEquals(headquarters.getId(), branch1.get("parentId"));
        });
    }

    private void testBusinessCodeInternal() {
        TimerUtils.measureExecutionTime("测试业务编码", () -> {
            // 创建新部门并测试编码生成
            SampleDepartment newDept = SampleDepartment.newModel("测试部门");
            String code = newDept.generateCode(false);
            assertNotNull(code);
            assertTrue(code.startsWith("DEPT-测试部门-")); // 验证编码格式
            assertTrue(code.matches("DEPT-测试部门-\\d{6}-\\d{3}")); // 验证格式是否符合模式
            assertEquals(code, newDept.getCode());

            // 测试强制重新生成编码
            String newCode = newDept.generateCode(true);
            assertNotNull(newCode);
            assertTrue(newCode.matches("DEPT-测试部门-\\d{6}-\\d{3}")); // 验证格式是否符合模式

            // 测试不允许非强制重新生成已有编码
            try {
                newDept.generateCode(false);
                fail("应该抛出异常");
            } catch (IllegalStateException e) {
                // 预期的异常
            }
        });
    }

    private void testRevisionableRefInternal() {
        TimerUtils.measureExecutionTime("测试可修订对象引用", () -> {
            // 创建两个版本的材料对象作为测试数据
            String materialCode = "M-" + System.currentTimeMillis();

            SampleMaterial material1 = SampleMaterial.newModel(materialCode, "A");
            material1.setName("测试材料");
            material1 = S.service(ObjectService.class).save(material1);

            SampleMaterial material2 = SampleMaterial.newModel(materialCode, "B");
            material2.setName("测试材料-修订版");
            material2 = S.service(ObjectService.class).save(material2);

            log.info("创建了两个材料版本：{}-A, {}-B", materialCode, materialCode);

            // 创建引用对象
            SampleMaterialReference reference = SampleMaterialReference.newModel();
            reference.setQuantity(10);
            reference.setRemark("引用测试");
            reference.setName("引用测试" + System.currentTimeMillis());

            // 测试设置引用对象
            reference.setTarget(material1);

            // 验证引用信息正确设置
            assertEquals(material1.get_objectType(), reference.getTargetObjectType());
            assertEquals(material1.getCode(), reference.getTargetItemCode());
            assertEquals(material1.getRevId(), reference.getTargetRevId());

            // 保存引用对象
            reference = S.service(ObjectService.class).save(reference);

            // 测试解析对象 - 使用精确版本规则
            Revisionable exactResolved = reference.resolveTarget(RevisionRule.PRECISE);
            assertNotNull(exactResolved);
            assertEquals(material1.getId(), exactResolved.getId());
            assertEquals("A", exactResolved.getRevId());

            // 测试解析对象 - 使用最新版本规则
            Revisionable latestResolved = reference.resolveTarget(RevisionRule.LATEST);
            assertNotNull(latestResolved);
            assertEquals(material2.getId(), latestResolved.getId());
            assertEquals("B", latestResolved.getRevId());

            log.info("成功验证了RevisionRule.EXACT解析到版本A，RevisionRule.LATEST解析到版本B");

            // 测试清除引用
            reference.setTarget(null);
            assertNull(reference.getTargetObjectType());
            assertNull(reference.getTargetItemCode());
            assertNull(reference.getTargetRevId());

            // 保存更改
            S.service(ObjectService.class).save(reference);

            // 重新加载并验证引用已被清除
            SampleMaterialReference reloaded = Q.result(SampleMaterialReference.class)
                    .where("id = ?", reference.getId()).first();
            assertNotNull(reloaded);
            assertNull(reloaded.getTargetObjectType());
            assertNull(reloaded.getTargetItemCode());
            assertNull(reloaded.getTargetRevId());
        });
    }
}