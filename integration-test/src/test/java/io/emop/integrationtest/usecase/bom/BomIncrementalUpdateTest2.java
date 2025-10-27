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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BOM 增量更新新场景测试
 * 测试子 BOM 更新、版本隔离等场景
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BomIncrementalUpdateTest2 {

    private static final String dateCode = String.valueOf(System.currentTimeMillis());

    private CADComponent createAndSaveComponent(String prefix, String revId) {
        CADComponent component = new CADComponent(CADComponent.class.getName());
        component.setCode(prefix + "-" + dateCode);
        component.setRevId(revId);
        component.setName(prefix + " - " + revId);
        component.setComponentType("PART");
        return S.service(ObjectService.class).save(component);
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

    private BomView createBomView(String code, String name, String revId) {
        BomView bomView = new BomView(BomView.class.getName());
        bomView.setCode(code + "-" + dateCode);
        bomView.setName(name);
        bomView.setRevId(revId);
        bomView.setBomType("CAD_BOM");
        return bomView;
    }

    /**
     * 递归遍历 BomLine 树，查找指定 targetCode 的节点
     */
    private BomLine findBomLineByTargetCode(BomLine root, String targetCode) {
        if (root.getTargetCode().equals(targetCode)) {
            return root;
        }
        
        List<BomLine> children = root.getChildren();
        if (children != null) {
            for (BomLine child : children) {
                BomLine found = findBomLineByTargetCode(child, targetCode);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }

    private BomLine topline(BomView bomView) {
        BomView savedBomView = Q.<BomView>objectType(bomView.get_objectType()).whereByBusinessKeys(bomView)
                .first();
        return S.service(BomService.class).preloadCADBOM(savedBomView.getToplineId());
    }

    /**
     * 场景15：大 BOM 中更新小子 BOM，再打开大 BOM 应该反映更改
     * 
     * 结构：
     * BigBom (Root)
     * ├── SubAssembly1
     * │   ├── Part1 (qty=1)
     * │   └── Part2
     * └── SubAssembly2
     * 
     * 操作：
     * 1. 创建大 BOM
     * 2. 单独更新 SubAssembly1 的 BOM（修改 Part1 数量为 5），针对SubAssembly1，应该有一份新的top bomline出现，而且bomview指向该新的bomline
     * 3. 重新加载大 BOM，验证 Part1 数量已更新
     */
    @Test
    @Order(15)
    void testScenario15_UpdateSubBomThenReloadParentBom() {
        log.info("=== 场景15：大 BOM 中更新小子 BOM，再打开大 BOM 应该反映更改 ===");

        // 创建组件
        CADComponent root = createAndSaveComponent("BIG-ROOT", "A");
        CADComponent subAsm1 = createAndSaveComponent("SUB-ASM1", "A");
        CADComponent subAsm2 = createAndSaveComponent("SUB-ASM2", "A");
        CADComponent part1 = createAndSaveComponent("PART1", "A");
        CADComponent part2 = createAndSaveComponent("PART2", "A");

        // 1. 创建大 BOM
        BomLine rootLine = createBomLine(root, BomUpdateAction.UPDATE.name());
        
        BomLine subAsm1Line = createBomLine(subAsm1, BomUpdateAction.UPDATE.name());
        BomLine part1Line = createBomLine(part1, BomUpdateAction.UPDATE.name());
        part1Line.setQuantity(1.0f); // 初始数量为 1
        BomLine part2Line = createBomLine(part2, BomUpdateAction.UPDATE.name());
        subAsm1Line.setChildren(Arrays.asList(part1Line, part2Line));
        
        BomLine subAsm2Line = createBomLine(subAsm2, BomUpdateAction.UPDATE.name());
        
        rootLine.setChildren(Arrays.asList(subAsm1Line, subAsm2Line));

        BomView bigBomView = createBomView("BIG-BOM", "Big BOM", "A");
        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bigBomView, rootLine));
        
        log.info("大 BOM 创建完成，Root ID: {}, SubAsm1 ID: {}, Part1 ID: {}", 
            rootLine.getId(), subAsm1Line.getId(), part1Line.getId());
        assertNotNull(findBomLineByTargetCode(topline(bigBomView), part1.getCode()), "应该能找到 Part1");

        // 2. 单独更新 SubAssembly1 的 BOM（作为独立的 BOM）
        BomLine subAsm1Root = createBomLine(subAsm1, BomUpdateAction.UPDATE.name());
        
        BomLine updatedPart1 = createBomLine(part1, BomUpdateAction.UPDATE.name());
        updatedPart1.setQuantity(5.0f); // 修改数量为 5
        
        BomLine keepPart2 = createBomLine(part2, BomUpdateAction.KEEP.name());
        
        subAsm1Root.setChildren(Arrays.asList(updatedPart1, keepPart2));

        BomView subBomView = createBomView("SUB-BOM", "Sub BOM", "A");
        BomView updatedSub1BomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(subBomView, subAsm1Root)).iterator().next();
        List<BomLine> subAsm1BomLines = Q.<BomLine>objectType(subAsm1Root.get_objectType())
                .whereTuples("targetObjectTypeId, targetItemCode, targetRevId",
                        subAsm1Root.getTargetInfo().toTuple())
                .query();
        assertEquals(2, subAsm1BomLines.size(), "针对subAsm1应该有2份bomline");
        assertTrue(subAsm1BomLines.stream().map(BomLine::getId).toList().contains(updatedSub1BomView.getToplineId()));
        assertTrue(subAsm1BomLines.stream()
                .map(bl -> bl.getHierarchical().getParentItemCode()).toList()
                .contains("BIG-ROOT-" + dateCode), "一个是大的ASSEMBLE的子节点");
        assertTrue(subAsm1BomLines.stream().anyMatch(bl -> bl.getHierarchical().getParentItemCode() == null), "另一个是子ASSEMBLE的根节点");

        log.info("子 BOM 更新完成，Part1 数量已改为 5");

        // 3. 重新加载大 BOM，验证更改已反映
        // 查找 Part1
        BomLine reloadedPart1 = findBomLineByTargetCode(topline(bigBomView), part1.getCode());
        assertNotNull(reloadedPart1, "应该能找到 Part1");
        assertEquals(5.0f, reloadedPart1.getQuantity(), 
            "重新加载大 BOM 后，Part1 的数量应该是 5（子 BOM 的更改应该反映到大 BOM）");
        
        log.info("✅ 场景15完成 - 子 BOM 的更改已反映到大 BOM");
    }

    /**
     * 场景16：大 BOM 中更新，单独打开小子 BOM 也应该反映更改
     * 
     * 操作：
     * 1. 创建大 BOM（与场景15相同结构）
     * 2. 在大 BOM 中更新 Part1 数量为 10
     * 3. 单独加载 SubAssembly1 的 BOM，验证 Part1 数量已更新
     */
    @Test
    @Order(16)
    void testScenario16_UpdateParentBomThenReloadSubBom() {
        log.info("=== 场景16：大 BOM 中更新，单独打开小子 BOM 也应该反映更改 ===");

        // 创建组件
        CADComponent root = createAndSaveComponent("BIG-ROOT2", "A");
        CADComponent subAsm1 = createAndSaveComponent("SUB-ASM1-2", "A");
        CADComponent part1 = createAndSaveComponent("PART1-2", "A");
        CADComponent part2 = createAndSaveComponent("PART2-2", "A");

        // 1. 创建大 BOM
        BomLine rootLine = createBomLine(root, BomUpdateAction.UPDATE.name());
        
        BomLine subAsm1Line = createBomLine(subAsm1, BomUpdateAction.UPDATE.name());
        BomLine part1Line = createBomLine(part1, BomUpdateAction.UPDATE.name());
        part1Line.setQuantity(1.0f);
        BomLine part2Line = createBomLine(part2, BomUpdateAction.UPDATE.name());
        subAsm1Line.setChildren(Arrays.asList(part1Line, part2Line));
        
        rootLine.setChildren(Arrays.asList(subAsm1Line));

        BomView bigBomView = createBomView("BIG-BOM2", "Big BOM 2", "A");
        BomView updatedBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bigBomView, rootLine))
                .iterator().next();

        Long subAsm1LineId = topline(updatedBomView).getChildren().get(0).getId();
        assertNotNull(subAsm1LineId);
        log.info("大 BOM 创建完成，SubAsm1 ID: {}", subAsm1LineId);

        // 2. 在大 BOM 中更新 Part1 数量为 10
        BomLine updatedRoot = createBomLine(root, BomUpdateAction.KEEP.name());
        
        BomLine updatedSubAsm1 = createBomLine(subAsm1, BomUpdateAction.UPDATE.name());
        
        BomLine updatedPart1 = createBomLine(part1, BomUpdateAction.UPDATE.name());
        updatedPart1.setQuantity(10.0f); // 在大 BOM 中修改数量为 10
        
        BomLine keepPart2 = createBomLine(part2, BomUpdateAction.KEEP.name());
        
        updatedSubAsm1.setChildren(Arrays.asList(updatedPart1, keepPart2));
        updatedRoot.setChildren(Arrays.asList(updatedSubAsm1));

        updatedBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bigBomView, updatedRoot))
                .iterator().next();

        log.info("大 BOM 更新完成，Part1 数量已改为 10");

        // 3. 单独加载 SubAssembly1 的 BOM（使用 preloadCADBOM）
        BomLine reloadedSubBom = S.service(BomService.class).preloadCADBOM(subAsm1LineId);
        assertNotNull(reloadedSubBom);
        
        // 查找 Part1
        BomLine reloadedPart1 = findBomLineByTargetCode(reloadedSubBom, part1.getCode());
        
        assertNotNull(reloadedPart1, "应该能在子 BOM 中找到 Part1");
        assertEquals(10.0f, reloadedPart1.getQuantity(), 
            "单独加载子 BOM 后，Part1 的数量应该是 10（大 BOM 的更改应该反映到子 BOM）");
        
        log.info("✅ 场景16完成 - 大 BOM 的更改已反映到子 BOM");
    }

    /**
     * 场景17：版本隔离 - 升版后更改 BOM，不影响旧版本
     * 
     * 操作：
     * 1. 创建 Assembly-A 版本的 BOM（Part1 数量=1）
     * 2. 升版到 Assembly-B，修改 BOM（Part1 数量=5）
     * 3. 验证 Assembly-A 的 BOM 仍然是 Part1 数量=1
     * 4. 验证 Assembly-B 的 BOM 是 Part1 数量=5
     */
    @Test
    @Order(17)
    void testScenario17_VersionIsolation() {
        log.info("=== 场景17：版本隔离 - 升版后更改 BOM，不影响旧版本 ===");

        // 创建组件
        CADComponent assemblyA = createAndSaveComponent("ASSEMBLY", "A");
        CADComponent part1A = createAndSaveComponent("PART1-V", "A");
        CADComponent part2A = createAndSaveComponent("PART2-V", "A");

        // 1. 创建 Assembly-A 版本的 BOM
        BomLine assemblyALine = createBomLine(assemblyA, BomUpdateAction.UPDATE.name());
        
        BomLine part1ALine = createBomLine(part1A, BomUpdateAction.UPDATE.name());
        part1ALine.setQuantity(1.0f); // A 版本：数量为 1
        
        BomLine part2ALine = createBomLine(part2A, BomUpdateAction.UPDATE.name());
        
        assemblyALine.setChildren(Arrays.asList(part1ALine, part2ALine));

        BomView bomViewA = createBomView("ASSEMBLY-BOM", "Assembly BOM", "A");
        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bomViewA, assemblyALine));
        
        Long assemblyABomLineId = topline(bomViewA).getChildren().get(0).getId();
        log.info("Assembly-A 版本 BOM 创建完成，Part1 数量=1");

        // 2. 升版到 Assembly-B，创建新的 BOM
        CADComponent assemblyB = createAndSaveComponent("ASSEMBLY", "B"); // 同一个 itemCode，不同 revId
        CADComponent part1B = createAndSaveComponent("PART1-V", "B");
        CADComponent part2B = createAndSaveComponent("PART2-V", "B");

        BomLine assemblyBLine = createBomLine(assemblyB, BomUpdateAction.UPDATE.name());
        
        BomLine part1BLine = createBomLine(part1B, BomUpdateAction.UPDATE.name());
        part1BLine.setQuantity(5.0f); // B 版本：数量为 5
        
        BomLine part2BLine = createBomLine(part2B, BomUpdateAction.UPDATE.name());
        
        assemblyBLine.setChildren(Arrays.asList(part1BLine, part2BLine));

        BomView bomViewB = createBomView("ASSEMBLY-BOM", "Assembly BOM", "B");
        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bomViewB, assemblyBLine));
        
        Long assemblyBBomLineId = topline(bomViewB).getChildren().get(0).getId();
        log.info("Assembly-B 版本 BOM 创建完成，Part1 数量=5");

        // 3. 验证 Assembly-A 的 BOM 仍然是 Part1 数量=1
        BomLine reloadedAssemblyA = S.service(BomService.class).preloadCADBOM(assemblyABomLineId);
        assertNotNull(reloadedAssemblyA);
        
        BomLine reloadedPart1A = findBomLineByTargetCode(reloadedAssemblyA, part1A.getCode());
        
        assertNotNull(reloadedPart1A, "应该能找到 Assembly-A 中的 Part1-A");
        assertEquals(1.0f, reloadedPart1A.getQuantity(), 
            "Assembly-A 的 Part1 数量应该仍然是 1（不受 B 版本影响）");

        // 4. 验证 Assembly-B 的 BOM 是 Part1 数量=5
        BomLine reloadedAssemblyB = S.service(BomService.class).preloadCADBOM(assemblyBBomLineId);
        assertNotNull(reloadedAssemblyB);
        
        BomLine reloadedPart1B = findBomLineByTargetCode(reloadedAssemblyB, part1B.getCode());
        
        assertNotNull(reloadedPart1B, "应该能找到 Assembly-B 中的 Part1-B");
        assertEquals(5.0f, reloadedPart1B.getQuantity(), 
            "Assembly-B 的 Part1 数量应该是 5");

        log.info("✅ 场景17完成 - 版本隔离验证成功，A 版本和 B 版本的 BOM 互不影响");
    }

    /**
     * 场景18：叶子节点更新 - 在大 BOM 中更新叶子节点
     * 
     * 结构：
     * Root
     * └── SubAsm
     *     └── Part (叶子节点, qty=1)
     * 
     * 操作：
     * 1. 创建大 BOM
     * 2. 只更新叶子节点 Part 的数量为 3
     * 3. 验证更新成功且性能良好
     */
    @Test
    @Order(18)
    void testScenario18_UpdateLeafNodeInLargeBom() {
        log.info("=== 场景18：叶子节点更新 - 在大 BOM 中更新叶子节点 ===");

        // 创建组件
        CADComponent root = createAndSaveComponent("LEAF-ROOT", "A");
        CADComponent subAsm = createAndSaveComponent("LEAF-SUBASM", "A");
        CADComponent leafPart = createAndSaveComponent("LEAF-PART", "A");

        // 1. 创建大 BOM
        BomLine rootLine = createBomLine(root, BomUpdateAction.UPDATE.name());
        
        BomLine subAsmLine = createBomLine(subAsm, BomUpdateAction.UPDATE.name());
        
        BomLine leafPartLine = createBomLine(leafPart, BomUpdateAction.UPDATE.name());
        leafPartLine.setQuantity(1.0f);
        
        subAsmLine.setChildren(Arrays.asList(leafPartLine));
        rootLine.setChildren(Arrays.asList(subAsmLine));

        BomView bomView = createBomView("LEAF-BOM", "Leaf BOM Test", "A");
        BomView updatedBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bomView, rootLine)).iterator().next();
        
        log.info("大 BOM 创建完成");

        // 2. 只更新叶子节点 Part 的数量为 3
        BomLine updatedRoot = createBomLine(root, BomUpdateAction.KEEP.name());
        
        BomLine updatedSubAsm = createBomLine(subAsm, BomUpdateAction.UPDATE.name());
        
        BomLine updatedLeafPart = createBomLine(leafPart, BomUpdateAction.UPDATE.name());
        updatedLeafPart.setQuantity(3.0f); // 只更新叶子节点
        
        updatedSubAsm.setChildren(Arrays.asList(updatedLeafPart));
        updatedRoot.setChildren(Arrays.asList(updatedSubAsm));

        long updateTime = TimerUtils.measureExecutionTime("更新叶子节点", () -> {
            S.service(BomService.class).saveOrUpdateBomStructures(Map.of(updatedBomView, updatedRoot));
        }) / 1_000_000;
        
        log.info("叶子节点更新耗时: {}ms", updateTime);

        // 3. 验证更新成功
        BomLine reloadedBom = topline(updatedBomView);
        
        BomLine reloadedLeafPart = findBomLineByTargetCode(reloadedBom, leafPart.getCode());
        
        assertNotNull(reloadedLeafPart, "应该能找到叶子节点");
        assertEquals(3.0f, reloadedLeafPart.getQuantity(), 
            "叶子节点的数量应该已更新为 3");
        
        // 验证性能：叶子节点更新应该很快
        assertTrue(updateTime < 1000, 
            String.format("叶子节点更新应该很快（< 1秒），实际: %dms", updateTime));
        
        log.info("✅ 场景18完成 - 叶子节点更新成功且性能良好");
    }
}
