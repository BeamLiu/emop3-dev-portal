package io.emop.integrationtest.usecase.common;

import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleTask;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.List;

import static io.emop.integrationtest.util.Assertion.*;

/**
 * 结构关系测试 - 使用 Task 和 ItemRevision 测试所有场景
 */
@Slf4j
@RequiredArgsConstructor
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructuralRelationTest {

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testOneToOneFK() {
        S.withStrongConsistency(this::testOneToOneFKImpl);
    }

    @Test
    @Order(2)
    public void testOneToMany() {
        S.withStrongConsistency(this::testOneToManyImpl);
    }

    // 测试一对一关系，外键在当前对象
    private void testOneToOneFKImpl() {
        // 创建主任务
        SampleTask mainTask = new SampleTask();
        mainTask.setName("Main Task");
        mainTask.setCode("TASK-MAIN");
        mainTask.setRevId("A");
        mainTask = S.service(ObjectService.class).upsertByBusinessKey(mainTask);

        // 创建子任务
        SampleTask subTask = new SampleTask();
        subTask.setName("Sub Task");
        subTask.setCode("TASK-SUB");
        subTask.setRevId("A");
        subTask = S.service(ObjectService.class).upsertByBusinessKey(subTask);

        // 设置关系
        mainTask.setSubTaskId(subTask.getId());
        mainTask = S.service(ObjectService.class).upsertByBusinessKey(mainTask);

        // 验证关系
        SampleTask loadedTask = Q.result(SampleTask.class)
                .where("id = ?", mainTask.getId())
                .first();

        assertNotNull(loadedTask);
        //懒加载
        assertNotNull(loadedTask.get("subTask"));
        assertEquals(subTask.getId(), loadedTask.getSubTask().getId());
        assertEquals("TASK-SUB", loadedTask.getSubTask().getCode());
    }

    // 测试一对多关系
    private void testOneToManyImpl() {
        // 创建主任务
        SampleTask mainTask = new SampleTask();
        mainTask.setName("Group Task");
        mainTask.setCode("TASK-GROUP");
        mainTask.setRevId("A");
        mainTask = S.service(ObjectService.class).upsertByBusinessKey(mainTask);

        // 创建两个子任务
        SampleTask childTask1 = new SampleTask();
        childTask1.setName("Child Task 1");
        childTask1.setCode("TASK-CHILD-1");
        childTask1.setRevId("A");
        childTask1.setGroupTaskId(mainTask.getId());
        childTask1 = S.service(ObjectService.class).upsertByBusinessKey(childTask1);

        SampleTask childTask2 = new SampleTask();
        childTask2.setName("Child Task 2");
        childTask2.setCode("TASK-CHILD-2");
        childTask2.setRevId("A");
        childTask2.setGroupTaskId(mainTask.getId());
        childTask2 = S.service(ObjectService.class).upsertByBusinessKey(childTask2);

        // 验证关系
        SampleTask loadedTask = Q.result(SampleTask.class)
                .where("id = ?", mainTask.getId())
                .first();

        assertNotNull(loadedTask);
        //懒加载
        assertNotNull(loadedTask.get("childTasks"));
        assertEquals(2, loadedTask.getChildTasks().size());
        assertTrue(loadedTask.getChildTasks().stream()
                .anyMatch(task -> task.getCode().equals("TASK-CHILD-1")));
        assertTrue(loadedTask.getChildTasks().stream()
                .anyMatch(task -> task.getCode().equals("TASK-CHILD-2")));
    }
}