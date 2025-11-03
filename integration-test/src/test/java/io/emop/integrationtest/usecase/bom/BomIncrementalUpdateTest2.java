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

    /**
     * 场景19：重复 UPDATE 根节点导致唯一约束冲突
     * 
     * 这个测试用例复现了一个 bug：
     * 当第二次使用 UPDATE 动作保存同一个根节点时，会违反数据库的唯一约束
     * idx_root: UNIQUE(targetobjecttypeid, targetitemcode, targetrevid) WHERE parent IS NULL
     * 
     * 结构：
     * Root
     * └── Part1 (qty=1)
     * 
     * 操作：
     * 1. 第一次创建 BOM（useType=OVERRIDE, action=UPDATE）
     * 2. 第二次更新 BOM（useType=OVERRIDE, action=UPDATE）- 应该会失败
     * 
     * 预期：第二次保存应该更新现有的根节点，而不是尝试创建新的根节点
     */
    @Test
    @Order(19)
    void testScenario19_DuplicateRootUpdateCausesUniqueConstraintViolation() {
        log.info("=== 场景19：重复 UPDATE 根节点导致唯一约束冲突 ===");

        // 创建组件
        CADComponent root = createAndSaveComponent("DUP-ROOT", "A");
        CADComponent part1 = createAndSaveComponent("DUP-PART1", "A");

        // 1. 第一次创建 BOM（useType=OVERRIDE, action=UPDATE）
        BomLine rootLine1 = createBomLine(root, BomUpdateAction.UPDATE.name());

        BomLine part1Line1 = createBomLine(part1, BomUpdateAction.UPDATE.name());
        part1Line1.setQuantity(1.0f);

        rootLine1.setChildren(Arrays.asList(part1Line1));

        BomView bomView = createBomView("DUP-ROOT-BOM", "Duplicate Root BOM", "A");
        BomView savedBomView1 = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bomView, rootLine1))
                .iterator().next();

        Long firstToplineId = savedBomView1.getToplineId();
        assertNotNull(firstToplineId, "第一次保存应该成功，toplineId 不应为空");
        log.info("第一次保存成功，toplineId: {}", firstToplineId);

        // 验证第一次保存的数据
        BomLine firstBom = topline(savedBomView1);
        assertNotNull(firstBom);
        assertEquals(1, firstBom.getChildren().size());
        assertEquals(1.0f, firstBom.getChildren().get(0).getQuantity());

        // 2. 第二次更新 BOM（useType=OVERRIDE, action=UPDATE）
        // 这里模拟用户再次发送完整的 BOM 结构，action 仍然是 UPDATE
        BomLine rootLine2 = createBomLine(root, BomUpdateAction.UPDATE.name());

        BomLine part1Line2 = createBomLine(part1, BomUpdateAction.UPDATE.name());
        part1Line2.setQuantity(2.0f); // 修改数量

        rootLine2.setChildren(Arrays.asList(part1Line2));

        // 这里应该会抛出唯一约束冲突异常
        log.info("第二次保存 BOM（应该会失败）...");

        BomView savedBomView2 = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(savedBomView1, rootLine2))
                .iterator().next();

        Long secondToplineId = savedBomView2.getToplineId();
        log.info("第二次保存成功，toplineId: {}", secondToplineId);

        // 验证第二次保存的数据
        BomLine secondBom = topline(savedBomView2);
        assertNotNull(secondBom);
        assertEquals(1, secondBom.getChildren().size());
        assertEquals(2.0f, secondBom.getChildren().get(0).getQuantity(),
                "第二次保存后，Part1 的数量应该更新为 2");

        // 验证数据库中只有一个根节点
        List<BomLine> bomlines = Q.<BomLine>objectType(BomLine.class.getName())
                .whereTuples("targetObjectTypeId, targetItemCode, targetRevId",
                        rootLine2.getTargetInfo().toTuple())
                .query();

        
        assertEquals(1, bomlines.stream().filter(bl -> bl.getHierarchical().getParentItemCode() == null).count(),
                "数据库中应该只有一个根节点（不应该创建重复的根节点）");

        log.info("✅ 场景19完成 - 第二次 UPDATE 根节点成功，没有违反唯一约束");

    }

    /**
     * 场景20：复现外键约束冲突 - BomView 引用的 BomLine 被删除
     * 
     * 这个测试用例复现了外键约束冲突的问题：
     * 当一个 BomLine 既是某个 BomView 的 topline，又需要被删除时，
     * 会违反 fk_bom_view_top 外键约束。
     * 
     * 结构：
     * BigBom (Root)
     * └── SubAssembly1 (这个节点同时也是 SubBom 的 topline)
     *     └── Part1 (qty=1)
     * 
     * SubBom (独立的 BOM)
     * └── SubAssembly1 (topline)
     *     └── Part1 (qty=1)
     * 
     * 操作：
     * 1. 创建大 BOM
     * 2. 创建独立的 SubBom，topline 指向 SubAssembly1
     * 3. 更新 SubBom，修改 Part1 数量（这会触发删除 SubAssembly1 的操作）
     * 4. 预期：应该抛出外键约束冲突异常
     */
    @Test
    @Order(20)
    void testScenario20_ForeignKeyConstraintViolation() {
        log.info("=== 场景20：复现外键约束冲突 - 更新大 BOM 时删除被 BomView 引用的子节点 ===");

        // 创建组件
        CADComponent root = createAndSaveComponent("FK-ROOT", "A");
        CADComponent subAsm1 = createAndSaveComponent("FK-SUBASM1", "A");
        CADComponent part1 = createAndSaveComponent("FK-PART1", "A");

        // 1. 先创建独立的 SubBom（这会创建 SubAssembly1 作为 topline，parent=null）
        BomLine subAsm1Root = createBomLine(subAsm1, BomUpdateAction.UPDATE.name());
        BomLine subPart1 = createBomLine(part1, BomUpdateAction.UPDATE.name());
        subPart1.setQuantity(1.0f);
        subAsm1Root.setChildren(Arrays.asList(subPart1));

        BomView subBomView = createBomView("FK-SUB-BOM", "FK Sub BOM", "A");
        BomView savedSubBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(subBomView, subAsm1Root))
                .iterator().next();
        
        Long subBomToplineId = savedSubBomView.getToplineId();
        assertNotNull(subBomToplineId, "SubBom 的 toplineId 不应为空");
        log.info("独立 SubBom 创建完成，toplineId: {}", subBomToplineId);

        // 验证数据库中有一个 SubAssembly1 的 BomLine（parent=null）
        List<BomLine> subAsm1Lines = Q.<BomLine>objectType(BomLine.class.getName())
                .where("targetItemCode=?", subAsm1.getCode())
                .query();
        assertEquals(1, subAsm1Lines.size(), "此时应该只有一个 SubAssembly1 的 BomLine");
        assertNull(subAsm1Lines.get(0).getHierarchical().getParentItemCode(), 
            "这个 BomLine 应该是 topline（parent=null）");

        // 2. 再创建大 BOM（这会创建另一个 SubAssembly1 作为子节点，parent=Root）
        BomLine rootLine = createBomLine(root, BomUpdateAction.UPDATE.name());
        
        BomLine subAsm1Line = createBomLine(subAsm1, BomUpdateAction.UPDATE.name());
        BomLine part1Line = createBomLine(part1, BomUpdateAction.UPDATE.name());
        part1Line.setQuantity(1.0f);
        subAsm1Line.setChildren(Arrays.asList(part1Line));
        
        rootLine.setChildren(Arrays.asList(subAsm1Line));

        BomView bigBomView = createBomView("FK-BIG-BOM", "FK Big BOM", "A");
        BomView savedBigBomView = S.service(BomService.class).saveOrUpdateBomStructures(Map.of(bigBomView, rootLine))
                .iterator().next();
        
        log.info("大 BOM 创建完成");

        // 验证数据库中现在有两个 SubAssembly1 的 BomLine
        subAsm1Lines = Q.<BomLine>objectType(BomLine.class.getName())
                .where("targetItemCode=?", subAsm1.getCode())
                .query();
        assertEquals(2, subAsm1Lines.size(), "此时应该有两个 SubAssembly1 的 BomLine");
        
        long toplineCount = subAsm1Lines.stream()
                .filter(line -> line.getHierarchical().getParentItemCode() == null)
                .count();
        long childCount = subAsm1Lines.stream()
                .filter(line -> line.getHierarchical().getParentItemCode() != null)
                .count();
        assertEquals(1, toplineCount, "应该有一个 topline（parent=null）");
        assertEquals(1, childCount, "应该有一个子节点（parent=Root）");

        // 3. 更新大 BOM（修改 Part1 数量，这会触发删除 SubAssembly1 子节点的操作）
        BomLine updatedRootLine = createBomLine(root, BomUpdateAction.KEEP.name());
        
        BomLine updatedSubAsm1Line = createBomLine(subAsm1, BomUpdateAction.UPDATE.name());
        BomLine updatedPart1Line = createBomLine(part1, BomUpdateAction.UPDATE.name());
        updatedPart1Line.setQuantity(5.0f); // 修改数量
        updatedSubAsm1Line.setChildren(Arrays.asList(updatedPart1Line));
        
        updatedRootLine.setChildren(Arrays.asList(updatedSubAsm1Line));

        log.info("尝试更新大 BOM（应该成功，不会触发外键约束冲突）...");

        // 4. 验证：更新应该成功，不会抛出外键约束冲突异常
        // 因为 batchDeleteChildRelations 会识别 topline 并跳过删除
        S.service(BomService.class).saveOrUpdateBomStructures(Map.of(savedBigBomView, updatedRootLine));
        
        log.info("大 BOM 更新成功");

        // 5. 验证数据库状态
        subAsm1Lines = Q.<BomLine>objectType(BomLine.class.getName())
                .where("targetItemCode=?", subAsm1.getCode())
                .query();
        
        // 应该仍然有两个 SubAssembly1 的 BomLine（一个 topline，一个子节点）
        assertEquals(2, subAsm1Lines.size(), "更新后应该仍有两个 SubAssembly1 的 BomLine");
        
        toplineCount = subAsm1Lines.stream()
                .filter(line -> line.getHierarchical().getParentItemCode() == null)
                .count();
        childCount = subAsm1Lines.stream()
                .filter(line -> line.getHierarchical().getParentItemCode() != null)
                .count();
        assertEquals(1, toplineCount, "应该仍有一个 topline（parent=null）");
        assertEquals(1, childCount, "应该仍有一个子节点（parent=Root）");

        // 6. 验证 SubBom 的 topline 仍然有效
        BomView reloadedSubBomView = Q.<BomView>objectType(BomView.class.getName())
                .whereByBusinessKeys(savedSubBomView)
                .first();
        assertNotNull(reloadedSubBomView.getToplineId(), "SubBom 的 toplineId 应该仍然有效");
        assertEquals(subBomToplineId, reloadedSubBomView.getToplineId(), 
            "SubBom 的 toplineId 应该保持不变");

        // 7. 验证大 BOM 中的 Part1 数量已更新
        BomLine reloadedBigBom = topline(savedBigBomView);
        BomLine reloadedPart1 = findBomLineByTargetCode(reloadedBigBom, part1.getCode());
        assertNotNull(reloadedPart1, "应该能在大 BOM 中找到 Part1");
        assertEquals(5.0f, reloadedPart1.getQuantity(), 
            "大 BOM 中 Part1 的数量应该已更新为 5");

        log.info("✅ 场景20完成 - 外键约束冲突已修复，topline 被保留并更新");
    }
}
