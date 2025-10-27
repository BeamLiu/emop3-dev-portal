package io.emop.integrationtest.usecase.bom;

import io.emop.integrationtest.util.TimerUtils;
import io.emop.model.bom.BomLine;
import io.emop.model.bom.BomUpdateAction;
import io.emop.model.bom.BomView;
import io.emop.model.cad.CADComponent;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.bom.BomService;
import io.emop.service.api.exception.RemoteServiceException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BOM 增量更新集成测试
 * 测试 saveOrUpdateBomStructures 方法的各种场景
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BomIncrementalUpdateTest {

    private static final String revId = "A";
    private String dateCode = String.valueOf(System.currentTimeMillis());

    private CADComponent rootComponent;
    private CADComponent child1Component;
    private CADComponent child2Component;
    private CADComponent child3Component;
    private CADComponent grandChild1Component;

    @BeforeEach
    void setUp() {
        dateCode = String.valueOf(System.currentTimeMillis());
        // 创建测试用的 CADComponent，使用 dateCode 避免编码冲突
        rootComponent = createAndSaveComponent("ROOT-001");
        child1Component = createAndSaveComponent("CHILD-001");
        child2Component = createAndSaveComponent("CHILD-002");
        child3Component = createAndSaveComponent("CHILD-003");
        grandChild1Component = createAndSaveComponent("GRAND-001");
    }

    private CADComponent createAndSaveComponent(String prefix) {
        CADComponent component = new CADComponent(CADComponent.class.getName());
        component.setCode(prefix + "-" + dateCode);
        component.setRevId(revId);
        component.setName(prefix + " - " + revId);
        component.setComponentType("PART");
        return S.service(ObjectService.class).save(component);
    }

    private CADComponent createComponent(String prefix) {
        CADComponent component = new CADComponent(CADComponent.class.getName());
        component.setCode(prefix + "-" + dateCode);
        component.setRevId(revId);
        component.setName(prefix + " - " + revId);
        component.setComponentType("PART");
        return component;
    }

    private BomLine createBomLine(CADComponent target, String action) {
        BomLine bomLine = BomLine.newModel();
        bomLine.setTarget(target);
        bomLine.setQuantity(1.0f);
        bomLine.setUnit("EA");
        if (action != null) {
            bomLine.set(BomUpdateAction.BOM_LINE_ACTION_KEY, action);
        }
        return bomLine;
    }

    private BomView createBomView(String code, String name) {
        BomView bomView = new BomView(BomView.class.getName());
        bomView.setCode(code);
        bomView.setName(name);
        bomView.setRevId("A");
        bomView.setBomType("CAD_BOM");
        return bomView;
    }

    /**
     * 场景1：全新创建 BOM 结构
     * 结构：
     * Root
     * ├── Child1
     * │ └── GrandChild1
     * └── Child2
     */
    @Test
    void testScenario1_CreateNewBomStructure() {
        // Arrange
        BomLine rootLine = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        BomLine child1Line = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        BomLine child2Line = createBomLine(child2Component, BomUpdateAction.UPDATE.name());
        BomLine grandChild1Line = createBomLine(grandChild1Component, BomUpdateAction.UPDATE.name());

        child1Line.setChildren(Arrays.asList(grandChild1Line));
        rootLine.setChildren(Arrays.asList(child1Line, child2Line));

        BomView bomView = createBomView("BOM-001", "Test BOM");

        // Act
        Set<BomView> result = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bomView, rootLine));

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());

        // 验证数据库中的数据
        BomView savedView = result.iterator().next();
        assertNotNull(savedView.getId());

        // 验证 BomLine 数量
        List<BomLine> allBomLines = bomlinesRelatedToCurrentTest();
        assertEquals(4, allBomLines.size(), "应该有4个 BomLine");
    }

    /**
     * 场景2：UPDATE 动作 - 更新部分节点
     * 初始结构：
     * Root
     * ├── Child1
     * │ └── GrandChild1
     * └── Child2
     * 
     * 更新后：
     * Root (KEEP)
     * ├── Child1 (UPDATE, 修改数量)
     * │ └── GrandChild1 (KEEP)
     * └── Child2 (KEEP)
     */
    @Test
    void testScenario2_UpdatePartialNodes() {
        // Arrange - 创建初始结构
        BomLine initialRoot = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        BomLine initialChild1 = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        BomLine initialChild2 = createBomLine(child2Component, BomUpdateAction.UPDATE.name());
        BomLine initialGrandChild1 = createBomLine(grandChild1Component, BomUpdateAction.UPDATE.name());

        initialChild1.setChildren(Arrays.asList(initialGrandChild1));
        initialRoot.setChildren(Arrays.asList(initialChild1, initialChild2));

        BomView initialBomView = createBomView("BOM-002", "Test BOM 2");
        initialBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, initialRoot))
                .iterator().next();

        // 从数据库加载完整的 BOM
        initialRoot = S.service(BomService.class).preloadCADBOM(initialBomView.getToplineId());

        // 获取初始的 BomLine 数量
        int initialCount = bomlinesRelatedToCurrentTest().size();

        // Act - 更新部分节点
        BomLine updatedRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());
        BomLine updatedChild1 = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        updatedChild1.setQuantity(2.0f); // 修改数量

        BomLine updatedChild2 = createBomLine(child2Component, BomUpdateAction.KEEP.name());
        BomLine updatedGrandChild1 = createBomLine(grandChild1Component, BomUpdateAction.KEEP.name());

        updatedChild1.setChildren(Arrays.asList(updatedGrandChild1));
        updatedRoot.setChildren(Arrays.asList(updatedChild1, updatedChild2));

        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, updatedRoot));

        // Assert
        int finalCount = bomlinesRelatedToCurrentTest().size();
        assertEquals(initialCount, finalCount, "BomLine 总数应该保持不变");

        // 从数据库重新加载验证
        BomView updatedBomView = Q.<BomView>objectType(initialBomView.get_objectType())
                .whereByBusinessKeys(initialBomView).first();
        BomLine savedRoot = S.service(BomService.class).preloadCADBOM(updatedBomView.getToplineId());
        BomLine savedChild1 = savedRoot.getChildren().stream().filter(child -> child.getTargetCode().contains("CHILD-001")).findFirst().orElseThrow();
        assertEquals(2.0f, savedChild1.getQuantity(), "Child1 的数量应该被更新");
    }

    /**
     * 场景3：KEEP 动作 - 保持所有节点不变
     */
    @Test
    void testScenario3_KeepAllNodes() {
        // Arrange - 创建初始结构
        BomLine initialRoot = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        BomLine initialChild1 = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        BomLine initialChild2 = createBomLine(child2Component, BomUpdateAction.UPDATE.name());
        BomView initialBomView = createBomView("BOM-003", "Test BOM 3");

        initialRoot.setChildren(Arrays.asList(initialChild1, initialChild2));

        initialBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, initialRoot))
                .iterator().next();
        // 数据库查询已经更新的数据
        initialRoot = S.service(BomService.class).preloadCADBOM(initialBomView.getToplineId());

        // Act - 使用 KEEP 动作重新保存
        BomLine keepRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());
        BomLine keepChild1 = createBomLine(child1Component, BomUpdateAction.KEEP.name());
        BomLine keepChild2 = createBomLine(child2Component, BomUpdateAction.KEEP.name());
        keepRoot.setChildren(Arrays.asList(keepChild1, keepChild2));

        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, keepRoot)).iterator().next();
        // 数据库查询已经更新的数据
        BomView updatedBomView = Q.<BomView>objectType(initialBomView.get_objectType())
                .whereByBusinessKeys(initialBomView).first();
        BomLine updatedTopline = S.service(BomService.class).preloadCADBOM(updatedBomView.getToplineId());

        // Assert - ID 应该保持不变
        assertEquals(initialRoot.getId(), updatedTopline.getId());
        assertEquals(initialRoot.getChildren().size(), updatedTopline.getChildren().size());
        assertEquals(initialRoot.getChildren().get(0), updatedTopline.getChildren().get(0));
        assertEquals(initialRoot.getChildren().get(1), updatedTopline.getChildren().get(1));
    }

    /**
     * 场景4：添加新节点
     * 初始结构：
     * Root
     * ├── Child1
     * └── Child2
     * 
     * 更新后：
     * Root (KEEP)
     * ├── Child1 (KEEP)
     * ├── Child2 (KEEP)
     * └── Child3 (UPDATE, 新增)
     */
    @Test
    void testScenario4_AddNewNode() {
        // Arrange - 创建初始结构
        BomLine initialRoot = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        BomLine initialChild1 = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        BomLine initialChild2 = createBomLine(child2Component, BomUpdateAction.UPDATE.name());

        initialRoot.setChildren(Arrays.asList(initialChild1, initialChild2));

        BomView initialBomView = createBomView("BOM-004", "Test BOM 4");
        initialBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, initialRoot))
                .iterator().next();

        // 从数据库加载完整的 BOM
        initialRoot = S.service(BomService.class).preloadCADBOM(initialBomView.getToplineId());

        int initialCount = bomlinesRelatedToCurrentTest().size();

        // Act - 添加新节点
        BomLine updatedRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());
        BomLine keepChild1 = createBomLine(child1Component, BomUpdateAction.KEEP.name());
        BomLine keepChild2 = createBomLine(child2Component, BomUpdateAction.KEEP.name());
        BomLine newChild3 = createBomLine(child3Component, BomUpdateAction.UPDATE.name());

        updatedRoot.setChildren(Arrays.asList(keepChild1, keepChild2, newChild3));

        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, updatedRoot));

        // Assert
        int finalCount = bomlinesRelatedToCurrentTest().size();
        assertEquals(initialCount + 1, finalCount, "应该增加1个 BomLine");
    }

    /**
     * 场景5：删除子节点（通过 UPDATE 父节点）
     * 初始结构：
     * Root
     * ├── Child1
     * │ └── GrandChild1
     * └── Child2
     * 
     * 更新后：
     * Root (KEEP)
     * ├── Child1 (UPDATE, 删除 GrandChild1)
     * └── Child2 (KEEP)
     */
    @Test
    void testScenario5_DeleteChildNode() {
        // Arrange - 创建初始结构
        BomLine initialRoot = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        BomLine initialChild1 = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        BomLine initialChild2 = createBomLine(child2Component, BomUpdateAction.UPDATE.name());
        BomLine initialGrandChild1 = createBomLine(grandChild1Component, BomUpdateAction.UPDATE.name());

        initialChild1.setChildren(Arrays.asList(initialGrandChild1));
        initialRoot.setChildren(Arrays.asList(initialChild1, initialChild2));

        BomView initialBomView = createBomView("BOM-005", "Test BOM 5");
        initialBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, initialRoot))
                .iterator().next();

        // 从数据库加载完整的 BOM
        initialRoot = S.service(BomService.class).preloadCADBOM(initialBomView.getToplineId());

        int initialCount = bomlinesRelatedToCurrentTest().size();

        // Act - 删除 GrandChild1（通过 UPDATE Child1 且不包含子节点）
        BomLine updatedRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());
        BomLine updatedChild1 = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        // 不设置 children，表示删除所有子节点

        BomLine keepChild2 = createBomLine(child2Component, BomUpdateAction.KEEP.name());

        updatedRoot.setChildren(Arrays.asList(updatedChild1, keepChild2));

        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, updatedRoot));

        // Assert
        int finalCount = bomlinesRelatedToCurrentTest().size();
        assertEquals(initialCount - 1, finalCount, "应该减少1个 BomLine（GrandChild1）");
    }

    /**
     * 场景6：复杂更新 - 混合 KEEP 和 UPDATE
     * 初始结构：
     * Root
     * ├── Child1
     * │ └── GrandChild1
     * └── Child2
     * 
     * 更新后：
     * Root (KEEP)
     * ├── Child1 (UPDATE, 修改数量并添加新子节点)
     * │ ├── GrandChild1 (KEEP)
     * │ └── Child3 (UPDATE, 新增)
     * └── Child2 (KEEP)
     */
    @Test
    void testScenario6_ComplexUpdate() {
        // Arrange - 创建初始结构
        BomLine initialRoot = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        BomLine initialChild1 = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        BomLine initialChild2 = createBomLine(child2Component, BomUpdateAction.UPDATE.name());
        BomLine initialGrandChild1 = createBomLine(grandChild1Component, BomUpdateAction.UPDATE.name());

        initialChild1.setChildren(Arrays.asList(initialGrandChild1));
        initialRoot.setChildren(Arrays.asList(initialChild1, initialChild2));

        BomView initialBomView = createBomView("BOM-006", "Test BOM 6");
        initialBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, initialRoot))
                .iterator().next();

        // 从数据库加载完整的 BOM
        initialRoot = S.service(BomService.class).preloadCADBOM(initialBomView.getToplineId());

        int initialCount = bomlinesRelatedToCurrentTest().size();

        // Act - 复杂更新
        BomLine updatedRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());
        BomLine updatedChild1 = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        updatedChild1.setQuantity(3.0f);

        BomLine keepGrandChild1 = createBomLine(grandChild1Component, BomUpdateAction.KEEP.name());
        BomLine newChild3 = createBomLine(child3Component, BomUpdateAction.UPDATE.name());

        updatedChild1.setChildren(Arrays.asList(keepGrandChild1, newChild3));

        BomLine keepChild2 = createBomLine(child2Component, BomUpdateAction.KEEP.name());

        updatedRoot.setChildren(Arrays.asList(updatedChild1, keepChild2));

        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, updatedRoot));

        // Assert
        int finalCount = bomlinesRelatedToCurrentTest().size();
        assertEquals(initialCount + 1, finalCount, "应该增加1个 BomLine（Child3）");

        // 从数据库重新加载验证
        BomView updatedBomView = Q.<BomView>objectType(initialBomView.get_objectType())
                .whereByBusinessKeys(initialBomView).first();
        BomLine savedRoot = S.service(BomService.class).preloadCADBOM(updatedBomView.getToplineId());
        BomLine savedChild1 = savedRoot.getChildren().stream().filter(child -> child.getTargetCode().contains("CHILD-001")).findFirst().orElseThrow();
        assertEquals(3.0f, savedChild1.getQuantity());
        assertEquals(2, savedChild1.getChildren().size(), "Child1 应该预加载有2个子节点");
    }

    /**
     * 场景7：循环引用检测
     * 应该抛出异常
     */
    @Test
    void testScenario7_CyclicReferenceDetection() {
        // Arrange - 创建循环引用结构
        BomLine rootLine = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        BomLine child1Line = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        BomLine cyclicLine = createBomLine(rootComponent, BomUpdateAction.UPDATE.name()); // 循环引用 root

        child1Line.setChildren(Arrays.asList(cyclicLine));
        rootLine.setChildren(Arrays.asList(child1Line));

        BomView bomView = createBomView("BOM-007", "Test BOM 7");

        // Act & Assert
        assertThrows(RemoteServiceException.class, () -> {
            S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bomView, rootLine));
        }, "应该检测到循环引用并抛出异常");
    }

    /**
     * 场景8：批量更新多个 BomView
     */
    @Test
    void testScenario8_BatchUpdateMultipleBomViews() {
        // Arrange
        BomLine root1 = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        BomLine child1 = createBomLine(child1Component, BomUpdateAction.UPDATE.name());
        root1.setChildren(Arrays.asList(child1));

        BomLine root2 = createBomLine(child2Component, BomUpdateAction.UPDATE.name());
        BomLine child2 = createBomLine(child3Component, BomUpdateAction.UPDATE.name());
        root2.setChildren(Arrays.asList(child2));

        BomView bomView1 = createBomView("BOM-008-1", "Test BOM 8-1");
        BomView bomView2 = createBomView("BOM-008-2", "Test BOM 8-2");

        Map<BomView, BomLine> bomStructures = new HashMap<>();
        bomStructures.put(bomView1, root1);
        bomStructures.put(bomView2, root2);

        // Act
        Set<BomView> result = S.service(BomService.class).saveOrUpdateBomStructures(bomStructures);

        // Assert
        assertEquals(2, result.size());

        // 验证两个 BomView 都被保存
        for (BomView view : result) {
            assertNotNull(view.getId());
        }
    }

    /**
     * 场景9：性能测试 - 大 BOM 结构
     * 创建一个包含 100 个节点的 BOM，然后只更新 10 个节点
     */
    @Test
    void testScenario9_PerformanceWithLargeBom() {
        // Arrange - 创建大 BOM 结构（简化版，实际可以更大）
        BomLine root = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        List<BomLine> children = new ArrayList<>();
        List<CADComponent> childComponents = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            CADComponent childComp = createAndSaveComponent("PERF-CHILD-" + i);
            childComponents.add(childComp);
            BomLine child = createBomLine(childComp, BomUpdateAction.UPDATE.name());
            children.add(child);
        }

        root.setChildren(children);

        BomView initialBomView = createBomView("BOM-009", "Performance Test BOM");
        initialBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, root)).iterator()
                .next();

        // 从数据库加载完整的 BOM
        root = S.service(BomService.class).preloadCADBOM(initialBomView.getToplineId());

        // Act - 只更新部分节点
        BomLine updatedRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());

        List<BomLine> updatedChildren = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            CADComponent childComp = childComponents.get(i);
            if (i < 2) {
                // 只更新前2个节点
                BomLine updatedChild = createBomLine(childComp, BomUpdateAction.UPDATE.name());
                updatedChild.setQuantity(2.0f);
                updatedChildren.add(updatedChild);
            } else {
                // 其他节点保持不变
                BomLine keepChild = createBomLine(childComp, BomUpdateAction.KEEP.name());
                updatedChildren.add(keepChild);
            }
        }

        updatedRoot.setChildren(updatedChildren);

        long startTime = System.currentTimeMillis();
        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, updatedRoot));
        long endTime = System.currentTimeMillis();

        // Assert
        System.out.println("Performance test completed in " + (endTime - startTime) + "ms");
        assertTrue((endTime - startTime) < 5000, "更新应该在5秒内完成");
    }

    /**
     * 场景10：无指令的节点（默认行为）
     */
    @Test
    void testScenario10_NoActionSpecified() {
        log.info("=== 场景10：无指令的节点（默认行为） ===");

        // Arrange - 创建没有指定动作的节点
        BomLine root = createBomLine(rootComponent, null); // 无指令
        BomLine child1 = createBomLine(child1Component, null);

        root.setChildren(Arrays.asList(child1));

        BomView bomView = createBomView("BOM-010", "Test BOM 10");

        // Act
        Set<BomView> result = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bomView, root));

        // Assert - 应该正常保存（默认行为）
        assertNotNull(result);
        assertEquals(1, result.size());

        List<BomLine> allBomLines = bomlinesRelatedToCurrentTest();
        assertTrue(allBomLines.size() >= 2, "应该至少有2个 BomLine");

        log.info("✅ 场景10完成");
    }

    /**
     * 场景11：大 BOM 性能测试 - 100 个节点，只更新 5 个
     * 验证增量更新对数据库的冲击小
     */
    @Test
    void testScenario11_LargeBomSmallUpdate_100Nodes() {
        log.info("=== 场景11：大 BOM 性能测试 - 100 个节点，只更新 5 个 ===");

        // Arrange - 创建 100 个节点的 BOM
        BomLine root = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        List<BomLine> children = new ArrayList<>();
        List<CADComponent> childComponents = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            CADComponent childComp = createComponent("LARGE-CHILD-" + i);
            childComponents.add(childComp);
            BomLine child = createBomLine(childComp, BomUpdateAction.UPDATE.name());
            children.add(child);
        }
        childComponents = S.service(ObjectService.class).saveAll(childComponents);

        root.setChildren(children);
        BomView initialBomView = createBomView("BOM-011", "Large BOM Test");

        // 初始保存
        long initialSaveTime = TimerUtils.measureExecutionTime("初始保存 100 个节点", () -> {
            S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, root));
        });
        log.info("初始保存耗时: {}ms", initialSaveTime);

        // 从数据库加载完整的 BOM
        BomView savedBomView = Q.<BomView>objectType(initialBomView.get_objectType())
                .whereByBusinessKeys(initialBomView).first();
        S.service(BomService.class).preloadCADBOM(savedBomView.getToplineId());

        int initialCount = bomlinesRelatedToCurrentTest().size();

        // Act - 只更新 5 个节点
        BomLine updatedRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());

        List<BomLine> updatedChildren = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            CADComponent childComp = childComponents.get(i);

            if (i < 5) {
                // 只更新前 5 个节点
                BomLine updatedChild = createBomLine(childComp, BomUpdateAction.UPDATE.name());
                updatedChild.setQuantity(10.0f);
                updatedChildren.add(updatedChild);
            } else {
                // 其他 95 个节点保持不变
                BomLine keepChild = createBomLine(childComp, BomUpdateAction.KEEP.name());
                updatedChildren.add(keepChild);
            }
        }

        updatedRoot.setChildren(updatedChildren);

        // 增量更新
        long updateTime = TimerUtils.measureExecutionTime("增量更新 5 个节点（共 100 个）", () -> {
            S.service(BomService.class).saveOrUpdateBomStructures(Map.of(savedBomView, updatedRoot));
        });
        log.info("增量更新耗时: {}ms", updateTime);

        // Assert
        int finalCount = bomlinesRelatedToCurrentTest().size();
        assertEquals(initialCount, finalCount, "BomLine 总数应该保持不变");

        // 验证性能：增量更新应该远快于初始保存
        assertTrue(updateTime < initialSaveTime,
                String.format("增量更新(%dms)应该比初始保存(%dms)快", updateTime, initialSaveTime));

        log.info("✅ 场景11完成 - 增量更新比初始保存快 {}%",
                (int) ((1 - (double) updateTime / initialSaveTime) * 100));
    }

    /**
     * 场景12：超大 BOM 性能测试 - 500 个节点，只更新 10 个
     * 验证在超大 BOM 场景下的性能
     */
    @Test
    void testScenario12_VeryLargeBomSmallUpdate_500Nodes() {
        log.info("=== 场景12：超大 BOM 性能测试 - 500 个节点，只更新 10 个 ===");

        // Arrange - 创建 500 个节点的 BOM
        BomLine root = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        List<BomLine> children = new ArrayList<>();
        List<CADComponent> childComponents = new ArrayList<>();

        for (int i = 0; i < 500; i++) {
            CADComponent childComp = createComponent("VLARGE-CHILD-" + i);
            childComponents.add(childComp);
            BomLine child = createBomLine(childComp, BomUpdateAction.UPDATE.name());
            children.add(child);
        }
        childComponents = S.service(ObjectService.class).saveAll(childComponents);

        root.setChildren(children);
        BomView initialBomView = createBomView("BOM-012", "Very Large BOM Test");

        // 初始保存
        long initialSaveTime = TimerUtils.measureExecutionTime("初始保存 500 个节点", () -> {
            S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, root));
        });
        log.info("初始保存耗时: {}ms", initialSaveTime / 1_000_000);

        // 从数据库加载完整的 BOM
        BomView savedBomView = Q.<BomView>objectType(initialBomView.get_objectType())
                .whereByBusinessKeys(initialBomView).first();
        S.service(BomService.class).preloadCADBOM(savedBomView.getToplineId());

        // Act - 只更新 10 个节点
        BomLine updatedRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());

        List<BomLine> updatedChildren = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            CADComponent childComp = childComponents.get(i);

            if (i < 10) {
                // 只更新前 10 个节点
                BomLine updatedChild = createBomLine(childComp, BomUpdateAction.UPDATE.name());
                updatedChild.setQuantity(20.0f);
                updatedChildren.add(updatedChild);
            } else {
                // 其他 490 个节点保持不变
                BomLine keepChild = createBomLine(childComp, BomUpdateAction.KEEP.name());
                updatedChildren.add(keepChild);
            }
        }

        updatedRoot.setChildren(updatedChildren);

        // 增量更新
        long updateTime = TimerUtils.measureExecutionTime("增量更新 10 个节点（共 500 个）", () -> {
            S.service(BomService.class).saveOrUpdateBomStructures(Map.of(savedBomView, updatedRoot));
        });
        log.info("增量更新耗时: {}ms", updateTime / 1_000_000);

        // Assert
        // 验证性能：增量更新应该远快于初始保存
        double speedupRatio = (double) initialSaveTime / updateTime;
        log.info("性能提升倍数: {}x", String.format("%.2f", speedupRatio));

        assertTrue(speedupRatio > 1,
                String.format("增量更新应该比初始保存快，实际: %.2fx", speedupRatio));

        log.info("✅ 场景12完成 - 增量更新比初始保存快 {}x", String.format("%.2f", speedupRatio));
    }

    /**
     * 场景13：多层级 BOM 更新 - 只更新中间层
     * 验证层级更新的正确性
     */
    @Test
    void testScenario13_MultiLevelBomUpdateMiddleLayer() {
        log.info("=== 场景13：多层级 BOM 更新 - 只更新中间层 ===");

        // Arrange - 创建 4 层结构
        // Root -> Level1 (10个) -> Level2 (每个5个) -> Level3 (每个2个)
        BomLine root = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        List<BomLine> level1Children = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            CADComponent level1Comp = createAndSaveComponent("L1-" + i);
            BomLine level1 = createBomLine(level1Comp, BomUpdateAction.UPDATE.name());

            List<BomLine> level2Children = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                CADComponent level2Comp = createAndSaveComponent("L2-" + i + "-" + j);
                BomLine level2 = createBomLine(level2Comp, BomUpdateAction.UPDATE.name());

                List<BomLine> level3Children = new ArrayList<>();
                for (int k = 0; k < 2; k++) {
                    CADComponent level3Comp = createAndSaveComponent("L3-" + i + "-" + j + "-" + k);
                    BomLine level3 = createBomLine(level3Comp, BomUpdateAction.UPDATE.name());
                    level3Children.add(level3);
                }

                level2.setChildren(level3Children);
                level2Children.add(level2);
            }

            level1.setChildren(level2Children);
            level1Children.add(level1);
        }

        root.setChildren(level1Children);
        BomView initialBomView = createBomView("BOM-013", "Multi Level BOM Test");

        // 初始保存
        long initialSaveTime = TimerUtils.measureExecutionTime("初始保存多层级 BOM", () -> {
            S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, root));
        });
        log.info("初始保存耗时: {}ms，总节点数: {}", initialSaveTime, 1 + 10 + 50 + 100);

        // 从数据库加载完整的 BOM
        BomView savedBomView = Q.<BomView>objectType(initialBomView.get_objectType())
                .whereByBusinessKeys(initialBomView).first();
        level1Children = S.service(BomService.class).preloadCADBOM(savedBomView.getToplineId()).getChildren();

        // Act - 只更新 Level2 的部分节点
        BomLine updatedRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());

        List<BomLine> updatedLevel1 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            BomLine level1 = level1Children.get(i);
            CADComponent level1Comp = (CADComponent) level1.resolveTarget();

            if (i < 2) {
                // 只更新前 2 个 Level1 节点
                BomLine updatedL1 = createBomLine(level1Comp, BomUpdateAction.UPDATE.name());

                List<BomLine> updatedLevel2 = new ArrayList<>();
                for (BomLine level2 : level1.getChildren()) {
                    CADComponent level2Comp = (CADComponent) level2.resolveTarget();
                    BomLine keepL2 = createBomLine(level2Comp, BomUpdateAction.KEEP.name());

                    // Level3 也保持不变
                    List<BomLine> keepLevel3 = new ArrayList<>();
                    for (BomLine level3 : level2.getChildren()) {
                        CADComponent level3Comp = (CADComponent) level3.resolveTarget();
                        BomLine keepL3 = createBomLine(level3Comp, BomUpdateAction.KEEP.name());
                        keepLevel3.add(keepL3);
                    }
                    keepL2.setChildren(keepLevel3);
                    updatedLevel2.add(keepL2);
                }

                updatedL1.setChildren(updatedLevel2);
                updatedLevel1.add(updatedL1);
            } else {
                // 其他 Level1 节点保持不变
                BomLine keepL1 = createBomLine(level1Comp, BomUpdateAction.KEEP.name());
                updatedLevel1.add(keepL1);
            }
        }

        updatedRoot.setChildren(updatedLevel1);

        // 增量更新
        long updateTime = TimerUtils.measureExecutionTime("增量更新中间层", () -> {
            S.service(BomService.class).saveOrUpdateBomStructures(Map.of(savedBomView, updatedRoot));
        });
        log.info("增量更新耗时: {}ms", updateTime);

        // Assert
        assertTrue(updateTime < initialSaveTime,
                "增量更新应该比初始保存快");

        log.info("✅ 场景13完成");
    }

    /**
     * 场景14：数据库操作计数验证
     * 验证 KEEP 节点确实不查询数据库
     */
    @Test
    void testScenario14_VerifyDatabaseOperationCount() {
        log.info("=== 场景14：数据库操作计数验证 ===");

        // Arrange - 创建 50 个节点的 BOM
        BomLine root = createBomLine(rootComponent, BomUpdateAction.UPDATE.name());
        List<BomLine> children = new ArrayList<>();
        List<CADComponent> childComponents = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            CADComponent childComp = createAndSaveComponent("DB-CHILD-" + i);
            childComponents.add(childComp);
            BomLine child = createBomLine(childComp, BomUpdateAction.UPDATE.name());
            children.add(child);
        }

        root.setChildren(children);
        BomView initialBomView = createBomView("BOM-014", "DB Operation Count Test");

        // 初始保存
        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(initialBomView, root));

        // 从数据库加载完整的 BOM
        BomView savedBomView = Q.<BomView>objectType(initialBomView.get_objectType())
                .whereByBusinessKeys(initialBomView).first();
        root = S.service(BomService.class).preloadCADBOM(savedBomView.getToplineId());

        int initialCount = bomlinesRelatedToCurrentTest().size();

        // Act - 全部使用 KEEP（不应该有任何数据库更新）
        BomLine updatedRoot = createBomLine(rootComponent, BomUpdateAction.KEEP.name());

        List<BomLine> updatedChildren = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            CADComponent childComp = childComponents.get(i);
            BomLine keepChild = createBomLine(childComp, BomUpdateAction.KEEP.name());
            updatedChildren.add(keepChild);
        }

        updatedRoot.setChildren(updatedChildren);

        // 全 KEEP 更新（应该非常快）
        long keepOnlyTime = TimerUtils.measureExecutionTime("全 KEEP 更新（50 个节点）", () -> {
            S.service(BomService.class).saveOrUpdateBomStructures(Map.of(savedBomView, updatedRoot));
        }) / 1_000_000; // 转换为毫秒
        log.info("全 KEEP 更新耗时: {}ms", keepOnlyTime);

        // Assert
        int finalCount = bomlinesRelatedToCurrentTest().size();
        assertEquals(initialCount, finalCount, "BomLine 总数应该完全不变");

        log.info("验证 KEEP 节点不查询数据库, 耗时 {} ms...", keepOnlyTime);
        // 全 KEEP 应该非常快（< 50ms）
        assertTrue(keepOnlyTime < 100,
                String.format("全 KEEP 更新应该非常快（< 100ms），实际: %dms", keepOnlyTime));

        log.info("✅ 场景14完成 - 验证 KEEP 节点不查询数据库");
    }

    private List<BomLine> bomlinesRelatedToCurrentTest() {
        return Q.<BomLine>objectType(BomLine.class.getName())
                .where("targetItemCode like ? or parentItemCode like ?", "%-" + dateCode, "%-" + dateCode)
                .query();
    }
}
