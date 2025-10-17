package io.emop.integrationtest.usecase.common;

import io.emop.model.lifecycle.LifecycleState;
import io.emop.integrationtest.util.Assertion;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.model.common.CopyRule;
import io.emop.model.common.ModelObject;
import io.emop.model.common.RevisionRule;
import io.emop.model.common.Revisionable;
import io.emop.model.common.UserContext;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.domain.common.RevisionService;
import io.emop.model.common.ItemRevision;
import io.emop.service.api.lifecycle.LifecycleService;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.relation.RelationFilter;
import io.emop.service.api.relation.RelationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RelationAndReviseTest {

    private static final String dateCode = String.valueOf(System.currentTimeMillis());
    private List<ItemRevision> testData;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
        testData = S.withStrongConsistency(this::prepareData);
    }

    @Test
    @Order(1)
    public void testReplaceRelation() {
        S.withStrongConsistency(() -> {
            TimerUtils.measureExecutionTime("替换关系", () -> {
                replaceRelation(testData);
            });
        });
    }

    @Test
    @Order(2)
    public void testAppendRelation() {
        S.withStrongConsistency(() -> {
            TimerUtils.measureExecutionTime("附加关系", () -> {
                appendRelation(testData);
            });
        });
    }

    @Test
    @Order(3)
    public void testRemoveRelations() {
        S.withStrongConsistency(() -> {
            TimerUtils.measureExecutionTime("移除关系", () -> {
                removeRelations(testData);
            });
        });
    }

    @Test
    @Order(4)
    public void testRevise() {
        S.withStrongConsistency(() -> {
            TimerUtils.measureExecutionTime("升版后的关系处理", () -> {
                revise(testData);
            });
        });
    }

    @Test
    @Order(5)
    public void testReviseWithProperties() {
        S.withStrongConsistency(() -> {
            TimerUtils.measureExecutionTime("修订时更新属性", () -> {
                reviseWithProperties(testData);
            });
        });
    }

    private List<ItemRevision> prepareData() {
        List<ItemRevision> data = IntStream.rangeClosed(1, 20).mapToObj(idx -> {
            ItemRevision rev = ItemRevision.newModel(dateCode + "-" + idx, "A");
            rev.setName("RelationAndRevise-" + dateCode + "-" + idx);
            rev = S.service(ObjectService.class).save(rev);
            return rev;
        }).collect(Collectors.toList());
        log.info("saved {}", data);
        return data;
    }

    private void replaceRelation(List<ItemRevision> data) {
        //1-2, 1-3, 1-4
        //1-5
        AssociateRelationService associateRelationService = S.service(AssociateRelationService.class);
        ItemRevision primary = data.get(0);
        associateRelationService.replaceRelation(primary, RelationType.reference, data.get(1), data.get(2), data.get(3));
        associateRelationService.replaceRelation(primary, RelationType.target, data.get(4));
        List<? extends ModelObject> allChildren = primary.rel();
        Assertion.assertEquals(4, allChildren.size());
        List<? extends ModelObject> targetChildren = primary.rel(RelationType.target);
        Assertion.assertEquals(1, targetChildren.size());
        Assertion.assertEquals(data.get(4).getId(), targetChildren.get(0).getId());

        //replace 1-5 by 1-6
        associateRelationService.replaceRelation(primary, RelationType.target, data.get(5));
        targetChildren = primary.rel(RelationType.target);
        Assertion.assertEquals(1, targetChildren.size());
        Assertion.assertEquals(data.get(5).getId(), targetChildren.get(0).getId());
    }

    private void appendRelation(List<ItemRevision> data) {
        AssociateRelationService associateRelationService = S.service(AssociateRelationService.class);
        ItemRevision primary = data.get(0);
        associateRelationService.appendRelation(primary, RelationType.reference, data.get(3), data.get(4));
        Set<Long> expectedIds = new HashSet<>(Arrays.asList(data.get(1).getId(), data.get(2).getId(), data.get(3).getId(), data.get(4).getId()));
        List<? extends ModelObject> targetChildren = primary.rel(RelationType.reference);
        Assertion.assertEquals(4, targetChildren.size());
        Assertion.assertTrue(expectedIds.contains(targetChildren.get(0).getId()));
        Assertion.assertTrue(expectedIds.contains(targetChildren.get(1).getId()));
        Assertion.assertTrue(expectedIds.contains(targetChildren.get(2).getId()));
        Assertion.assertTrue(expectedIds.contains(targetChildren.get(3).getId()));

        associateRelationService.appendRelation(primary, RelationType.reference, data.get(3), data.get(4));
        Assertion.assertEquals(4, targetChildren.size());
    }

    private void removeRelations(List<ItemRevision> data) {
        AssociateRelationService associateRelationService = S.service(AssociateRelationService.class);
        ItemRevision primary = data.get(0);
        associateRelationService.appendRelation(primary, RelationType.usedBy, data.get(2), data.get(3), data.get(4));
        List<? extends ModelObject> usedByChildren = primary.rel(RelationType.usedBy);
        Assertion.assertEquals(3, usedByChildren.size());

        //删除不存在的子项
        Assertion.assertEquals(0l, associateRelationService.removeRelations(primary, RelationType.usedBy, data.get(1)));
        Assertion.assertEquals(3, primary.rel(RelationType.usedBy).size());
        Assertion.assertEquals(1l, associateRelationService.removeRelations(primary, RelationType.usedBy, data.get(2)));
        Assertion.assertEquals(2, primary.rel(RelationType.usedBy).size());
        Assertion.assertEquals(2l, associateRelationService.removeRelations(primary, RelationType.usedBy));
        //clear子项
        Assertion.assertEquals(0, primary.rel(RelationType.usedBy).size());
    }

    private void revise(List<ItemRevision> data) {
        AssociateRelationService associateRelationService = S.service(AssociateRelationService.class);
        ItemRevision primary = data.get(1);
        associateRelationService.appendRelation(primary, RelationType.bom, data.get(2), data.get(3), data.get(4));
        Assertion.assertEquals(3, primary.rel().size());

        S.service(LifecycleService.class).moveToState(data, LifecycleState.STATE_RELEASED);
        data.forEach(ModelObject::reload);
        Revisionable revisedPrimary = S.service(RevisionService.class).revise(primary, CopyRule.CopyReference);
        Assertion.assertEquals("B", revisedPrimary.getRevId());
        Assertion.assertEquals(3, revisedPrimary.rel().size());

        Revisionable secondary1 = S.service(RevisionService.class).revise(data.get(2), CopyRule.NoCopy);
        Revisionable secondary2 = S.service(RevisionService.class).revise(data.get(3), CopyRule.NoCopy);
        Revisionable secondary3 = S.service(RevisionService.class).revise(data.get(4), CopyRule.NoCopy);

        Assertion.assertEquals(3, primary.rel().size());
        Assertion.assertEquals("A", ((Revisionable) primary.rel(new RelationFilter(RevisionRule.PRECISE)).get(0)).getRevId());
        Assertion.assertEquals("B", ((Revisionable) primary.rel(new RelationFilter(RevisionRule.LATEST)).get(0)).getRevId());

        S.service(LifecycleService.class).moveToState(primary, LifecycleState.STATE_RELEASED);
        primary.reload();
        revisedPrimary = S.service(RevisionService.class).revise(primary);
        Assertion.assertEquals("C", revisedPrimary.getRevId());
        Assertion.assertEquals(3, primary.rel().size());
        Assertion.assertEquals(0, revisedPrimary.rel().size());
    }

    private void reviseWithProperties(List<ItemRevision> data) {
        // 准备测试对象
        ItemRevision testItem = data.get(10);
        S.service(LifecycleService.class).moveToState(testItem, LifecycleState.STATE_RELEASED);
        testItem.reload();

        String originalName = testItem.getName();
        log.info("原始名称: {}", originalName);

        // 测试单个修订并更新属性
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "修订后的新名称-" + dateCode);
        
        RevisionService.ReviseRequest<ItemRevision> request = new RevisionService.ReviseRequest<>(
                testItem, CopyRule.NoCopy, properties);
        
        ItemRevision revisedItem = S.service(RevisionService.class).reviseByRequest(request);
        Assertion.assertEquals("B", revisedItem.getRevId());
        Assertion.assertEquals("修订后的新名称-" + dateCode, revisedItem.getName());
        log.info("修订后名称: {}", revisedItem.getName());

        // 测试批量修订并更新属性（统一属性）
        List<ItemRevision> batchItems = List.of(data.get(11), data.get(12), data.get(13));
        S.service(LifecycleService.class).moveToState(batchItems, LifecycleState.STATE_RELEASED);
        batchItems.forEach(ModelObject::reload);

        Map<String, Object> batchProperties = new HashMap<>();
        batchProperties.put("name", "批量修订后的名称-" + dateCode);
        
        List<RevisionService.ReviseRequest<ItemRevision>> batchRequests = batchItems.stream()
                .map(item -> new RevisionService.ReviseRequest<>(item, CopyRule.NoCopy, batchProperties))
                .collect(Collectors.toList());
        
        List<ItemRevision> revisedItems = S.service(RevisionService.class).reviseByRequests(batchRequests);
        Assertion.assertEquals(3, revisedItems.size());
        for (ItemRevision item : revisedItems) {
            Assertion.assertEquals("B", item.getRevId());
            Assertion.assertEquals("批量修订后的名称-" + dateCode, item.getName());
            log.info("批量修订后对象 {} 的名称: {}", item.getCode(), item.getName());
        }

        // 测试批量修订并更新属性（每个对象不同属性）
        List<ItemRevision> individualItems = List.of(data.get(14), data.get(15), data.get(16));
        S.service(LifecycleService.class).moveToState(individualItems, LifecycleState.STATE_RELEASED);
        individualItems.forEach(ModelObject::reload);

        List<RevisionService.ReviseRequest<ItemRevision>> individualRequests = new ArrayList<>();
        for (int i = 0; i < individualItems.size(); i++) {
            Map<String, Object> props = new HashMap<>();
            props.put("name", "独立修订-" + i + "-" + dateCode);
            individualRequests.add(new RevisionService.ReviseRequest<>(individualItems.get(i), CopyRule.NoCopy, props));
        }
        
        List<ItemRevision> individualRevisedItems = S.service(RevisionService.class).reviseByRequests(individualRequests);
        Assertion.assertEquals(3, individualRevisedItems.size());
        for (int i = 0; i < individualRevisedItems.size(); i++) {
            ItemRevision item = individualRevisedItems.get(i);
            Assertion.assertEquals("B", item.getRevId());
            Assertion.assertEquals("独立修订-" + i + "-" + dateCode, item.getName());
            log.info("独立修订后对象 {} 的名称: {}", item.getCode(), item.getName());
        }
    }
}