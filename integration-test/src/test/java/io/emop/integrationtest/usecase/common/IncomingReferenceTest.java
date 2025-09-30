package io.emop.integrationtest.usecase.common;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.UserContext;
import io.emop.model.relation.ReferenceInfo;
import io.emop.model.relation.ReferenceInfo.ReferenceType;
import io.emop.integrationtest.domain.SampleMaterial;
import io.emop.integrationtest.domain.SampleMaterialReference;
import io.emop.integrationtest.domain.SampleTask;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.domain.common.IncomingReferenceService;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.relation.RelationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.emop.integrationtest.util.Assertion.assertEquals;
import static io.emop.integrationtest.util.Assertion.assertTrue;

/**
 * 引用关系反向查找测试
 * <p>
 * 测试三种类型的引用关系反向查找功能：
 * 1. 关联关系（Associate）
 * 2. 结构关系（Structural）
 * 3. 可版本化引用（RevisionableRefTrait）
 */
@Slf4j
@RequiredArgsConstructor
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IncomingReferenceTest {

    private static final String dateCode = String.valueOf(System.currentTimeMillis());

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testAssociateReferenceIncoming() {
        S.withStrongConsistency(this::testAssociateReferenceIncomingImpl);
    }

    @Test
    @Order(2)
    public void testStructuralReferenceIncoming() {
        S.withStrongConsistency(this::testStructuralReferenceIncomingImpl);
    }

    @Test
    @Order(3)
    public void testRevisionableRefIncoming() {
        S.withStrongConsistency(this::testRevisionableRefIncomingImpl);
    }

    /**
     * 测试关联关系的反向查找
     */
    private void testAssociateReferenceIncomingImpl() {
        TimerUtils.measureExecutionTime("测试关联关系反向引用查询", () -> {
            // 准备测试数据
            List<ItemRevision> revisions = prepareRevisionData();

            // 添加关联关系
            ItemRevision primary = revisions.get(0);
            ItemRevision secondary1 = revisions.get(1);
            ItemRevision secondary2 = revisions.get(2);
            ItemRevision secondary3 = revisions.get(3);

            AssociateRelationService associateRelationService = S.service(AssociateRelationService.class);

            // 使用不同的关系类型创建多种关联关系
            associateRelationService.replaceRelation(primary, RelationType.reference, secondary1, secondary2);
            associateRelationService.replaceRelation(primary, RelationType.target, secondary3);

            // 测试反向引用查找
            IncomingReferenceService incomingReferenceService = S.service(IncomingReferenceService.class);

            // 查找引用secondary1的对象
            List<ReferenceInfo> refInfos1 = incomingReferenceService.findReferencers(secondary1);
            assertEquals(1, refInfos1.size());
            assertEquals(primary.getId(), refInfos1.get(0).getReferencer().getId());
            assertEquals(RelationType.reference.getName(), refInfos1.get(0).getRelationName());
            assertEquals(ReferenceType.ASSOCIATE, refInfos1.get(0).getReferenceType());

            // 查找引用secondary3的对象
            List<ReferenceInfo> refInfos3 = incomingReferenceService.findReferencers(secondary3);
            assertEquals(1, refInfos3.size());
            assertEquals(primary.getId(), refInfos3.get(0).getReferencer().getId());
            assertEquals(RelationType.target.getName(), refInfos3.get(0).getRelationName());
            assertEquals(ReferenceType.ASSOCIATE, refInfos3.get(0).getReferenceType());

            // 使用关系类型过滤
            List<ReferenceInfo> refInfosFiltered = incomingReferenceService.findReferencers(secondary1, RelationType.reference);
            assertEquals(1, refInfosFiltered.size());

            List<ReferenceInfo> refInfosFilteredEmpty = incomingReferenceService.findReferencers(secondary1, RelationType.target);
            assertEquals(0, refInfosFilteredEmpty.size());

            // 测试删除关系后的反向引用情况
            associateRelationService.removeRelations(primary, RelationType.reference, secondary1);

            List<ReferenceInfo> refInfosAfterRemove = incomingReferenceService.findReferencers(secondary1);
            assertEquals(0, refInfosAfterRemove.size());

            log.info("关联关系反向引用测试成功完成");
        });
    }

    /**
     * 测试结构关系的反向查找
     */
    private void testStructuralReferenceIncomingImpl() {
        TimerUtils.measureExecutionTime("测试结构关系反向引用查询", () -> {
            // 准备一对一结构关系测试数据
            SampleTask mainTask = new SampleTask();
            mainTask.setName("Main Task For Incoming Test");
            mainTask.setCode("TASK-MAIN-INC");
            mainTask.setRevId("A");
            mainTask = S.service(ObjectService.class).upsertByBusinessKey(mainTask);

            SampleTask subTask = new SampleTask();
            subTask.setName("Sub Task For Incoming Test");
            subTask.setCode("TASK-SUB-INC");
            subTask.setRevId("A");
            subTask = S.service(ObjectService.class).upsertByBusinessKey(subTask);

            // 建立一对一关系
            mainTask.setSubTaskId(subTask.getId());
            mainTask = S.service(ObjectService.class).upsertByBusinessKey(mainTask);

            // 测试反向引用查找
            IncomingReferenceService incomingReferenceService = S.service(IncomingReferenceService.class);
            List<ReferenceInfo> subTaskRefs = incomingReferenceService.findReferencers(subTask);

            // 验证反向引用
            assertEquals(1, subTaskRefs.size());
            assertEquals(mainTask.getId(), subTaskRefs.get(0).getReferencer().getId());
            assertEquals("subTask", subTaskRefs.get(0).getRelationName());
            assertEquals(ReferenceType.STRUCTURAL, subTaskRefs.get(0).getReferenceType());

            // 测试一对多结构关系
            SampleTask groupTask = new SampleTask();
            groupTask.setName("Group Task For Incoming Test");
            groupTask.setCode("TASK-GROUP-INC");
            groupTask.setRevId("A");
            groupTask = S.service(ObjectService.class).upsertByBusinessKey(groupTask);

            // 创建多个子任务
            SampleTask childTask1 = new SampleTask();
            childTask1.setName("Child Task 1 For Incoming Test");
            childTask1.setCode("TASK-CHILD-1-INC");
            childTask1.setRevId("A");
            childTask1.setGroupTaskId(groupTask.getId());
            childTask1 = S.service(ObjectService.class).upsertByBusinessKey(childTask1);

            SampleTask childTask2 = new SampleTask();
            childTask2.setName("Child Task 2 For Incoming Test");
            childTask2.setCode("TASK-CHILD-2-INC");
            childTask2.setRevId("A");
            childTask2.setGroupTaskId(groupTask.getId());
            childTask2 = S.service(ObjectService.class).upsertByBusinessKey(childTask2);

            // 测试一对多反向引用
            List<ReferenceInfo> groupTaskRefs = incomingReferenceService.findReferencers(childTask1);
            assertEquals(1, groupTaskRefs.size());
            assertEquals("childTasks", groupTaskRefs.get(0).getRelationName());
            assertEquals(groupTask.getId(), groupTaskRefs.get(0).getReferencer().getId());
            assertEquals(ReferenceType.STRUCTURAL, groupTaskRefs.get(0).getReferenceType());

            groupTaskRefs = incomingReferenceService.findReferencers(childTask2);
            assertEquals(1, groupTaskRefs.size());
            assertEquals("childTasks", groupTaskRefs.get(0).getRelationName());
            assertEquals(groupTask.getId(), groupTaskRefs.get(0).getReferencer().getId());
            assertEquals(ReferenceType.STRUCTURAL, groupTaskRefs.get(0).getReferenceType());

            // 测试修改结构关系后的反向引用
            childTask1.setGroupTaskId(null);
            childTask1 = S.service(ObjectService.class).upsertByBusinessKey(childTask1);

            List<ReferenceInfo> groupTaskRefsAfterUpdate = incomingReferenceService.findReferencers(childTask1);
            assertEquals(0, groupTaskRefsAfterUpdate.size());

            log.info("结构关系反向引用测试成功完成");
        });
    }

    /**
     * 测试可版本化引用的反向查找
     */
    private void testRevisionableRefIncomingImpl() {
        TimerUtils.measureExecutionTime("测试可版本化引用反向查询", () -> {
            // 创建材料对象
            String materialCode = "M-INC-" + System.currentTimeMillis();
            SampleMaterial material = SampleMaterial.newModel(materialCode, "A");
            material.setName("测试材料-反向引用");
            material = S.service(ObjectService.class).save(material);

            // 创建多个引用对象
            SampleMaterialReference ref1 = SampleMaterialReference.newModel();
            ref1.setQuantity(10);
            ref1.setRemark("引用测试1");
            ref1.setName("引用测试1-" + System.currentTimeMillis());
            ref1.setTarget(material); // 设置引用
            ref1 = S.service(ObjectService.class).save(ref1);

            SampleMaterialReference ref2 = SampleMaterialReference.newModel();
            ref2.setQuantity(20);
            ref2.setRemark("引用测试2");
            ref2.setName("引用测试2-" + System.currentTimeMillis());
            ref2.setTarget(material); // 设置引用
            ref2 = S.service(ObjectService.class).save(ref2);

            // 测试反向引用查找
            IncomingReferenceService incomingReferenceService = S.service(IncomingReferenceService.class);
            List<ReferenceInfo> materialRefs = incomingReferenceService.findReferencers(material);

            // 验证反向引用
            assertEquals(2, materialRefs.size());

            // 检查引用类型全部是TRAIT_REFERENCE
            assertTrue(materialRefs.stream()
                    .allMatch(ref -> ref.getReferenceType() == ReferenceType.REVISIONABLE_REFERENCE));

            // 检查引用关系名称全部是target
            assertTrue(materialRefs.stream()
                    .allMatch(ref -> "target".equals(ref.getRelationName())));

            // 验证引用者ID包含我们创建的两个引用
            List<Long> refererIds = materialRefs.stream()
                    .map(ref -> ref.getReferencer().getId())
                    .collect(Collectors.toList());
            assertTrue(refererIds.contains(ref1.getId()));
            assertTrue(refererIds.contains(ref2.getId()));

            // 测试修改版本后的反向引用
            SampleMaterial materialB = SampleMaterial.newModel(materialCode, "B");
            materialB.setName("测试材料-反向引用-版本B");
            materialB = S.service(ObjectService.class).save(materialB);

            // 修改引用到版本B
            ref1.setTarget(materialB);
            ref1 = S.service(ObjectService.class).save(ref1);

            // 查找特定版本的引用（精确匹配）
            List<ReferenceInfo> materialARefsAfterUpdate = incomingReferenceService.findReferencers(material, true);
            assertEquals(1, materialARefsAfterUpdate.size());
            assertEquals(ref2.getId(), materialARefsAfterUpdate.get(0).getReferencer().getId());
            // 精确查找版本B的引用
            List<ReferenceInfo> materialBRefs = incomingReferenceService.findReferencers(materialB, true);
            assertEquals(1, materialBRefs.size());
            assertEquals(ref1.getId(), materialBRefs.get(0).getReferencer().getId());

            // 查找所有版本的引用（非精确匹配）
            List<ReferenceInfo> allMaterialRefs = incomingReferenceService.findReferencers(material, false);
            // 这里应该有两个引用
            assertEquals(2, allMaterialRefs.size());
            allMaterialRefs = incomingReferenceService.findReferencers(materialB, false);
            // 这里应该有两个引用
            assertEquals(2, allMaterialRefs.size());


            log.info("可版本化引用反向查询测试成功完成");
        });
    }

    /**
     * 准备ItemRevision测试数据
     */
    private List<ItemRevision> prepareRevisionData() {
        return IntStream.rangeClosed(1, 5).mapToObj(idx -> {
            ItemRevision rev = ItemRevision.newModel(dateCode + "-INC-" + idx, "A");
            rev.setName("IncomingReference-" + dateCode + "-" + idx);
            rev = S.service(ObjectService.class).save(rev);
            return rev;
        }).collect(Collectors.toList());
    }
}