package io.emop.integrationtest.usecase.common;

import io.emop.model.common.CopyRule;
import io.emop.model.common.UserContext;
import io.emop.model.draft.DraftModelObject;
import io.emop.model.lifecycle.LifecycleState;
import io.emop.model.lifecycle.WorkflowContext;
import io.emop.integrationtest.domain.SampleMaterial;
import io.emop.service.S;
import io.emop.service.api.domain.common.CheckoutService;
import io.emop.service.api.domain.common.RevisionService;
import io.emop.service.api.lifecycle.LifecycleService;
import io.emop.service.api.data.ObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.emop.integrationtest.util.Assertion.*;

@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LifecycleStateTest {

    private final CheckoutService checkoutService = S.service(CheckoutService.class);
    private final RevisionService revisionService = S.service(RevisionService.class);
    private final ObjectService objectService = S.service(ObjectService.class);
    private final LifecycleService lifecycleService = S.service(LifecycleService.class);

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testStateTransitionSuccessfulFlow() {
        S.withStrongConsistency(this::testStateTransition_SuccessfulFlow);
    }

    @Test
    @Order(2)
    public void testStateTransitionInvalidEvent() {
        S.withStrongConsistency(this::testStateTransition_InvalidEvent);
    }

    @Test
    @Order(3)
    public void testMoveForwardAndBackward() {
        S.withStrongConsistency(this::testMoveForwardAndBackwardInternal);
    }

    @Test
    @Order(4)
    public void testComplexWorkflow() {
        S.withStrongConsistency(this::testComplexWorkflowInternal);
    }

    @Test
    @Order(5)
    public void testWorkflowDrivenTransition() {
        S.withStrongConsistency(this::testWorkflowDrivenTransitionInternal);
    }

    @Test
    @Order(6)
    public void testWorkflowRequiredCheck() {
        S.withStrongConsistency(this::testWorkflowRequiredCheckInternal);
    }

    @Test
    @Order(7)
    public void testConditionalTransition() {
        S.withStrongConsistency(this::testConditionalTransitionInternal);
    }

    private void testStateTransition_SuccessfulFlow() {
        // Step 1: 创建草稿对象
        DraftModelObject draft = new DraftModelObject();
        draft.setTargetObjectType(SampleMaterial.class.getName());
        draft.set("code", "DRAFT-MATERIAL-" + System.currentTimeMillis());
        draft.set("name", "Test Draft Material");
        draft.set("revId", "A");

        // 保存草稿
        draft = objectService.save(draft);
        assertEquals("Working", draft.currentState().getName()); // DraftModelObject 的状态是 Working

        // Step 2: 转换为正式对象
        SampleMaterial material = (SampleMaterial) draft.convertToModelObject();
        assertEquals("Working", material.currentState().getName());

        // Step 3: 后续的状态流转测试
        Map<String, Object> context = new HashMap<>();
        context.put("currentUser", "engineer");

        // Step 3: 提交审核，状态从 Working 转为 UnderReview
        LifecycleState state = material.stateChange("Submit", context);
        assertEquals("UnderReview", state.getName());

        // Step 4: 审核通过，状态从 UnderReview 转为 Released
        state = material.stateChange("Approve", context);
        assertEquals("Released", state.getName());

        // Step 5: 冻结产品，防止进一步修改，状态转为 Frozen
        state = material.stateChange("Freeze", context);
        assertEquals("Frozen", state.getName());

        // Step 6: 解冻产品，状态恢复为 Released
        state = material.stateChange("Unfreeze", context);
        assertEquals("Released", state.getName());

        // Step 7: 产品升版，状态从 Released 转为 Working
        state = material.stateChange("Revise", context);
        assertEquals("Working", state.getName());
    }

    private void testStateTransition_InvalidEvent() {
        // Step 1: 创建草稿对象
        DraftModelObject draft = new DraftModelObject();
        draft.setTargetObjectType(SampleMaterial.class.getName());
        draft.set("code", "INVALID-TEST-" + System.currentTimeMillis());
        draft.set("name", "Invalid Event Test Material");
        draft.set("revId", "A");

        // 保存草稿并转换为正式对象
        draft = objectService.save(draft);
        SampleMaterial material = (SampleMaterial) draft.convertToModelObject();

        Map<String, Object> context = new HashMap<>();
        context.put("currentUser", "engineer");

        // 尝试从 Working 状态执行无效的事件 (Working 状态不能直接 Approve)
        assertException(() -> {
            material.stateChange("Approve", context);
        });
    }

    private void testMoveForwardAndBackwardInternal() {
        // Step 1: 创建草稿对象
        DraftModelObject draft = new DraftModelObject();
        draft.setTargetObjectType(SampleMaterial.class.getName());
        draft.set("code", "MOVE-TEST-" + System.currentTimeMillis());
        draft.set("name", "Move Forward Test Material");
        draft.set("revId", "A");

        // 保存草稿并转换为正式对象
        draft = objectService.save(draft);
        SampleMaterial material = (SampleMaterial) draft.convertToModelObject();

        Map<String, Object> context = new HashMap<>();
        context.put("currentUser", "engineer");

        // Step 2: 产品从 Working 开始
        assertEquals("Working", material.currentState().getName());

        // Step 3: 使用通用的前进事件，状态线性流转到 UnderReview
        LifecycleState state = material.stateChange("MoveForward", context);
        assertEquals("UnderReview", state.getName());
    }

    private void testComplexWorkflowInternal() {
        String code = "MATERIAL-001-" + System.currentTimeMillis();

        // 1. 创建草稿对象
        DraftModelObject draft = new DraftModelObject();
        draft.setTargetObjectType(SampleMaterial.class.getName());
        draft.set("code", code);
        draft.set("revId", "A");
        draft.set("name", "Name of MATERIAL-001");

        // 保存草稿
        draft = objectService.save(draft);

        // 2. 转换为正式对象
        SampleMaterial material = (SampleMaterial) draft.convertToModelObject();

        // 3. 初始状态检查
        assertEquals("Working", material.currentState().getName());

        // 4. 签出并修改
        material = checkoutService.checkout(material, "Initial modification", 60);
        material.setName("Sample Material v1");
        material = objectService.save(material);

        // 5. 签入并提交审核
        material = checkoutService.checkin(material, "Completed initial modification");
        material = lifecycleService.applyStateChange(material, "Submit"); // Working -> UnderReview

        // 6. 尝试在审核状态下签出(应该失败)
        SampleMaterial finalMaterial = material;
        assertException(() -> {
            checkoutService.checkout(finalMaterial, "Should fail", 60);
        });

        // 7. 批准并发布
        WorkflowContext workflowContext = createWorkflowContext("ReviewProcess");
        material = lifecycleService.applyStateChangeWithWorkflow(material, "Approve", workflowContext); // UnderReview -> Released
        assertEquals("Released", material.currentState().getName());
        assertEquals(true, material.currentState().isRevisable());
        assertEquals(false, material.currentState().isEditable());
        //发布后不允许修改
        SampleMaterial finalMaterial2 = material;
        assertException(() -> {
            checkoutService.checkout(finalMaterial2, "Should fail", 60);
        });

        // 8. 创建新版本
        SampleMaterial newRevision = revisionService.revise(material, CopyRule.CopyReference);
        assertEquals("B", newRevision.getRevId());
        assertEquals("Working", newRevision.currentState().getName());

        // 9. 修改新版本
        newRevision = checkoutService.checkout(newRevision, "Modifying new revision", 60);
        newRevision.setName("Sample Material v2");
        newRevision = objectService.save(newRevision);
        newRevision = checkoutService.checkin(newRevision, "Completed modification");

        // 10. 将新版本快速审批发布 (Working -> Released, 需要先到 UnderReview 再 Approve)
        newRevision = lifecycleService.applyStateChange(newRevision, "Submit"); // Working -> UnderReview
        newRevision = lifecycleService.applyStateChangeWithWorkflow(newRevision, "Approve", workflowContext); // UnderReview -> Released

        // 11. 验证两个版本的状态
        assertEquals("Released", material.currentState().getName());
        assertEquals("Released", newRevision.currentState().getName());
        assertEquals(code, material.getCode());
        assertEquals(code, newRevision.getCode());
        assertEquals("A", material.getRevId());
        assertEquals("B", newRevision.getRevId());
    }

    private WorkflowContext createWorkflowContext(String processType) {
        WorkflowContext context = new WorkflowContext();
        context.setWorkflowInstanceId("WF-" + System.currentTimeMillis());
        context.setNodeId("Node-" + System.currentTimeMillis());
        context.setNodeName("测试节点");
        context.setOperatorId("testUser");
        context.setOperatorName("测试用户");
        context.setOperationTime(new Date());
        context.put("processDefinitionKey", processType);
        return context;
    }

    /**
     * 测试工作流驱动的状态转换
     */
    private void testWorkflowDrivenTransitionInternal() {
        // 1. 创建草稿对象并转换为正式对象
        DraftModelObject draft = new DraftModelObject();
        draft.setTargetObjectType(SampleMaterial.class.getName());
        draft.set("code", "WF-TEST-" + System.currentTimeMillis());
        draft.set("revId", "A");
        draft.set("name", "Workflow Test Material");

        draft = objectService.save(draft);
        SampleMaterial material = (SampleMaterial) draft.convertToModelObject();

        // 2. 设置到UnderReview状态（这里使用moveToState直接跳转，简化测试）
        material = lifecycleService.moveToState(material, "UnderReview");
        assertEquals("UnderReview", material.currentState().getName());

        // 3. 创建工作流上下文
        WorkflowContext workflowContext = new WorkflowContext();
        workflowContext.setWorkflowInstanceId("WF-TEST-001");
        workflowContext.setNodeId("ApprovalNode");
        workflowContext.setNodeName("审批节点");
        workflowContext.setOperatorId("reviewer");
        workflowContext.setOperatorName("审批人");
        workflowContext.setOperationTime(new Date());
        workflowContext.put("comments", "测试通过，同意发布");

        // 4. 通过工作流上下文执行状态转换
        material = lifecycleService.applyStateChangeWithWorkflow(material, "Approve", createWorkflowContext("Approve"));

        // 5. 验证状态已成功转换
        assertEquals("Released", material.currentState().getName());
        assertEquals(true, material.currentState().isReleased());

        // 6. 验证条件触发的工作流 - 冻结对象
        workflowContext.setNodeName("冻结节点");
        workflowContext.put("processDefinitionKey", "ChangeManagement");
        material = lifecycleService.applyStateChangeWithWorkflow(material, "Freeze", workflowContext);
        assertEquals("Frozen", material.currentState().getName());
    }

    /**
     * 测试工作流要求检查
     */
    private void testWorkflowRequiredCheckInternal() {
        // 1. 创建测试对象并设置到UnderReview状态
        SampleMaterial material = new SampleMaterial(SampleMaterial.class.getName());
        material.setCode("WF-CHECK-" + System.currentTimeMillis());
        material.setRevId("A");
        material.set("_state", LifecycleState.STATE_UNDER_REVIEW);

        // 2. 验证状态转换需要工作流驱动
        boolean requiresWorkflow = lifecycleService.isWorkflowRequired(material, "Approve");
        assertTrue(requiresWorkflow, "UnderReview到Released的转换应该需要工作流驱动");

        // 3. 尝试手动执行需要工作流驱动的状态转换（应该抛出异常）
        SampleMaterial finalMaterial = material;
        assertException(() -> {
            lifecycleService.applyStateChange(finalMaterial, "Approve");
        });
    }

    /**
     * 测试条件控制的状态转换
     */
    private void testConditionalTransitionInternal() {
        // 1. 创建测试对象
        SampleMaterial material = new SampleMaterial(SampleMaterial.class.getName());
        material.setCode("CONDITION-TEST-" + System.currentTimeMillis());
        material.setRevId("A");
        material.set("_state", LifecycleState.STATE_RELEASED);

        // 3. 测试条件转换 - 有引用的情况（不应该允许修订）,预留功能，并未真正实现
        /**
        material.set("hasReferences", true);
        Map<String, Object> context = new HashMap<>();
        context.put("document.hasReferences", true);

        SampleMaterial finalMaterial = material;
        assertException(() -> {
            finalMaterial.stateChange("Revise", context);
        });
         */
    }
}