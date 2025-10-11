package io.emop.integrationtest.usecase.bom;

import io.emop.integrationtest.util.TimerUtils;
import io.emop.model.bom.BomLine;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.Revisionable;
import io.emop.model.common.UserContext;
import io.emop.model.query.tuple.Tuple2;
import io.emop.model.query.tuple.Tuple3;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.bom.BomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * BomService 基础功能测试
 * 测试单行 BOM 的增删改查功能
 */
@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BomServiceTest {

    private static final String revId = "A";
    private static final String dateCode = String.valueOf(System.currentTimeMillis());

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testAddBomLine() {
        S.withStrongConsistency(this::testAddBomLineImpl);
    }

    @Test
    @Order(2)
    public void testAddBomLineWithExtendedProperties() {
        S.withStrongConsistency(this::testAddBomLineWithExtendedPropertiesImpl);
    }

    @Test
    @Order(3)
    public void testUpdateBomLine() {
        S.withStrongConsistency(this::testUpdateBomLineImpl);
    }

    @Test
    @Order(4)
    public void testDeleteBomLineWithCascade() {
        S.withStrongConsistency(this::testDeleteBomLineWithCascadeImpl);
    }

    @Test
    @Order(5)
    public void testDeleteBomLineWithoutCascade() {
        S.withStrongConsistency(this::testDeleteBomLineWithoutCascadeImpl);
    }

    @Test
    @Order(6)
    public void testMoveBomLine() {
        S.withStrongConsistency(this::testMoveBomLineImpl);
    }

    @Test
    @Order(7)
    public void testBatchAddBomLines() {
        S.withStrongConsistency(this::testBatchAddBomLinesImpl);
    }

    @Test
    @Order(8)
    public void testBatchDeleteBomLines() {
        S.withStrongConsistency(this::testBatchDeleteBomLinesImpl);
    }

    /**
     * 测试添加单个 BOM 行
     */
    private void testAddBomLineImpl() {
        log.info("=== 测试添加单个 BOM 行 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);

        // 准备测试数据
        ItemRevision parent = createTestItem("add-parent");
        ItemRevision child = createTestItem("add-child");

        final ItemRevision parentF = objectService.save(parent);
        final ItemRevision childF = objectService.save(child);

        TimerUtils.measureExecutionTime("添加 BOM 行", () -> {
            Map<String, Object> props = new HashMap<>();
            props.put("quantity", 2.5f);
            props.put("unit", "个");
            props.put("description", "测试子件");

            BomLine bomLine = bomService.addBomLine(parentF, childF, props);

            assertNotNull(bomLine);
            assertNotNull(bomLine.getId());
            assertEquals(2.5f, bomLine.getQuantity());
            assertEquals("个", bomLine.getUnit());
            assertEquals("测试子件", bomLine.getDescription());

            log.info("✅ 添加 BOM 行成功: ID={}", bomLine.getId());
        });

        // 清理
        cleanupTestData(parent, List.of(child));
    }

    /**
     * 测试添加带扩展属性的 BOM 行
     */
    private void testAddBomLineWithExtendedPropertiesImpl() {
        log.info("=== 测试添加带扩展属性的 BOM 行 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);

        ItemRevision parent = createTestItem("ext-parent");
        ItemRevision child = createTestItem("ext-child");

        final ItemRevision parentF = objectService.save(parent);
        final ItemRevision childF = objectService.save(child);

        TimerUtils.measureExecutionTime("添加带扩展属性的 BOM 行", () -> {
            Map<String, Object> props = new HashMap<>();
            props.put("quantity", 1.0f);
            props.put("unit", "套");
            props.put("description", "带扩展属性");
            props.put("transform", "matrix(1,0,0,1,100,200)");
            props.put("occurrencePath", "/root/assembly/part1");
            // 扩展字段
            props.put("customField1", "自定义值1");
            props.put("customField2", 12345);

            BomLine bomLine = bomService.addBomLine(parentF, childF, props);

            assertNotNull(bomLine);
            assertEquals("matrix(1,0,0,1,100,200)", bomLine.getTransform());
            assertEquals("/root/assembly/part1", bomLine.getOccurrencePath());
            assertEquals("自定义值1", bomLine.get("customField1"));
            assertEquals(12345, bomLine.get("customField2"));

            log.info("✅ 添加带扩展属性的 BOM 行成功");
        });

        // 清理
        cleanupTestData(parent, List.of(child));
    }

    /**
     * 测试更新 BOM 行
     */
    private void testUpdateBomLineImpl() {
        log.info("=== 测试更新 BOM 行 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);

        ItemRevision parent = createTestItem("update-parent");
        ItemRevision child = createTestItem("update-child");

        final ItemRevision parentF = objectService.save(parent);
        final ItemRevision childF = objectService.save(child);

        // 先添加
        Map<String, Object> props = new HashMap<>();
        props.put("quantity", 1.0f);
        props.put("unit", "个");
        BomLine bomLine = bomService.addBomLine(parentF, childF, props);

        TimerUtils.measureExecutionTime("更新 BOM 行", () -> {
            Map<String, Object> updates = new HashMap<>();
            updates.put("quantity", 5.0f);
            updates.put("unit", "套");
            updates.put("description", "更新后的描述");
            updates.put("customField", "新增扩展字段");

            BomLine updated = bomService.updateBomLine(parent, child, updates);

            assertNotNull(updated);
            assertEquals(5.0f, updated.getQuantity());
            assertEquals("套", updated.getUnit());
            assertEquals("更新后的描述", updated.getDescription());
            assertEquals("新增扩展字段", updated.get("customField"));

            log.info("✅ 更新 BOM 行成功");
        });

        // 清理
        cleanupTestData(parent, List.of(child));
    }

    /**
     * 测试级联删除 BOM 行
     */
    private void testDeleteBomLineWithCascadeImpl() {
        log.info("=== 测试级联删除 BOM 行 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);

        // 创建三层结构：grandparent -> parent -> child
        ItemRevision grandparent = createTestItem("cascade-grandparent");
        ItemRevision parent = createTestItem("cascade-parent");
        ItemRevision child = createTestItem("cascade-child");

        final ItemRevision grandparentF = objectService.save(grandparent);
        final ItemRevision parentF = objectService.save(parent);
        child = objectService.save(child);

        // 构建层级关系
        BomLine parentLine = bomService.addBomLine(grandparent, parent, Map.of("quantity", 1.0f));
        BomLine childLine = bomService.addBomLine(parent, child, Map.of("quantity", 1.0f));

        TimerUtils.measureExecutionTime("级联删除 BOM 行", () -> {
            // 级联删除 parent，应该同时删除 child
            boolean result = bomService.deleteBomLine(grandparentF, parentF, true);

            assertTrue(result);

            // 验证 parent 和 child 的 BomLine 都被删除
            BomLine parentCheck = objectService.findById(parentLine.getId());
            BomLine childCheck = objectService.findById(childLine.getId());

            assertNull(parentCheck);
            assertNull(childCheck);

            log.info("✅ 级联删除成功，父节点和子节点都被删除");
        });

        // 清理
        cleanupTestData(grandparent, List.of(parent, child));
    }

    /**
     * 测试非级联删除 BOM 行（子节点上移）
     */
    private void testDeleteBomLineWithoutCascadeImpl() {
        log.info("=== 测试非级联删除 BOM 行 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);

        // 创建三层结构：grandparent -> parent -> child
        ItemRevision grandparent = createTestItem("nocase-grandparent");
        ItemRevision parent = createTestItem("nocase-parent");
        ItemRevision child = createTestItem("nocase-child");

        final ItemRevision grandparentF = objectService.save(grandparent);
        final ItemRevision parentF = objectService.save(parent);
        final ItemRevision childF = objectService.save(child);

        // 构建层级关系
        BomLine parentLine = bomService.addBomLine(grandparent, parent, Map.of("quantity", 1.0f));
        BomLine childLine = bomService.addBomLine(parentF, childF, Map.of("quantity", 1.0f));

        TimerUtils.measureExecutionTime("非级联删除 BOM 行", () -> {
            // 非级联删除 parent，child 应该上移到 grandparent 下
            boolean result = bomService.deleteBomLine(grandparent, parent, false);

            assertTrue(result);

            // 验证 parent 被删除
            BomLine parentCheck = objectService.findById(parentLine.getId());
            assertNull(parentCheck);

            // 验证 child 仍然存在
            BomLine childCheck = objectService.findById(childLine.getId());
            assertNotNull(childCheck);

            log.info("✅ 非级联删除成功，子节点上移");
        });

        // 清理
        cleanupTestData(grandparent, List.of(parent, child));
    }

    /**
     * 测试移动 BOM 行
     */
    private void testMoveBomLineImpl() {
        log.info("=== 测试移动 BOM 行 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);

        // 创建结构：parent1 -> child, parent2
        ItemRevision parent1 = createTestItem("move-parent1");
        ItemRevision parent2 = createTestItem("move-parent2");
        ItemRevision child = createTestItem("move-child");

        final ItemRevision parent1F = objectService.save(parent1);
        final ItemRevision parent2F = objectService.save(parent2);
        final ItemRevision childF = objectService.save(child);

        // child 初始在 parent1 下
        BomLine childLine = bomService.addBomLine(parent1, child, Map.of("quantity", 1.0f));
        bomService.addBomLine(parent1, parent2, Map.of("quantity", 1.0f)); // parent2 也在 parent1 下

        TimerUtils.measureExecutionTime("移动 BOM 行", () -> {
            // 将 child 从 parent1 移动到 parent2 下
            BomLine moved = bomService.moveBomLine(childF, parent1F, parent2F);

            assertNotNull(moved);
            assertEquals(childLine.getId(), moved.getId());

            // 验证新的父节点
            BomLine movedCheck = objectService.findById(moved.getId());
            assertNotNull(movedCheck);
            assertNotNull(movedCheck.getParent());

            log.info("✅ 移动 BOM 行成功");
        });

        // 清理
        cleanupTestData(parent1, List.of(parent2, child));
    }

    /**
     * 测试批量添加 BOM 行
     */
    private void testBatchAddBomLinesImpl() {
        log.info("=== 测试批量添加 BOM 行 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);

        ItemRevision parent = createTestItem("batch-add-parent");
        List<ItemRevision> children = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            children.add(createTestItem("batch-add-child-" + i));
        }

        parent = objectService.save(parent);
        children = objectService.saveAll(children);

        final ItemRevision finalParent = parent;
        final List<ItemRevision> finalChildren = children;

        TimerUtils.measureExecutionTime("批量添加 5 个 BOM 行", () -> {
            List<Tuple3<Revisionable, Revisionable, Map<String, Object>>> pairs = new ArrayList<>();

            for (int i = 0; i < finalChildren.size(); i++) {
                Map<String, Object> props = new HashMap<>();
                props.put("quantity", (float) (i + 1));
                props.put("unit", "个");

                pairs.add(Tuple3.of(finalParent, finalChildren.get(i), props));
            }

            List<BomLine> results = bomService.addBomLines(pairs);

            assertEquals(5, results.size());

            for (int i = 0; i < results.size(); i++) {
                BomLine bomLine = results.get(i);
                assertNotNull(bomLine);
                assertNotNull(bomLine.getId());
                assertEquals((float) (i + 1), bomLine.getQuantity());
            }

            log.info("✅ 批量添加 {} 个 BOM 行成功", results.size());
        });

        // 清理
        cleanupTestData(finalParent, finalChildren);
    }

    /**
     * 测试批量删除 BOM 行
     */
    private void testBatchDeleteBomLinesImpl() {
        log.info("=== 测试批量删除 BOM 行 ===");

        ObjectService objectService = S.service(ObjectService.class);
        BomService bomService = S.service(BomService.class);

        ItemRevision parent = createTestItem("batch-del-parent");
        List<ItemRevision> children = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            children.add(createTestItem("batch-del-child-" + i));
        }

        parent = objectService.save(parent);
        children = objectService.saveAll(children);

        // 先添加 BOM 行
        for (ItemRevision child : children) {
            bomService.addBomLine(parent, child, Map.of("quantity", 1.0f));
        }

        final ItemRevision finalParent = parent;
        final List<ItemRevision> finalChildren = children;

        TimerUtils.measureExecutionTime("批量删除 5 个 BOM 行", () -> {
            List<Tuple2<Revisionable, Revisionable>> pairs = new ArrayList<>();

            for (ItemRevision child : finalChildren) {
                pairs.add(Tuple2.of(finalParent, child));
            }

            int count = bomService.deleteBomLines(pairs, true);

            assertEquals(5, count);

            log.info("✅ 批量删除 {} 个 BOM 行成功", count);
        });

        // 清理
        cleanupTestData(finalParent, finalChildren);
    }

    /**
     * 创建测试 Item
     */
    private ItemRevision createTestItem(String prefix) {
        ItemRevision rev = ItemRevision.newModel(
                prefix + "-" + dateCode + "-" + UUID.randomUUID().toString().substring(0, 8),
                revId
        );
        rev.setName("BOM测试对象-" + prefix);
        return rev;
    }

    /**
     * 清理测试数据
     */
    private void cleanupTestData(ItemRevision parent, List<ItemRevision> children) {
        ObjectService objectService = S.service(ObjectService.class);
        List<Long> allIds = new ArrayList<>();
        allIds.add(parent.getId());
        allIds.addAll(children.stream().map(ModelObject::getId).collect(Collectors.toList()));

        try {
            objectService.forceDelete(allIds);
        } catch (Exception e) {
            log.warn("清理测试数据时出错: {}", e.getMessage());
        }
    }
}
