package io.emop.example.client.service;

import io.emop.example.model.hello.HelloTask;
import io.emop.example.service.api.hello.HelloTaskService;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * RPC 演示服务
 * 演示如何使用 RPC 调用进行数据操作
 */
@Slf4j
public class RpcTestService {

    /**
     * 执行所有RPC演示
     */
    public void runAll() {
        log.info("=== 开始 RPC 演示 ===");
        UserContext.runAsSystem(() -> {
            // 演示自定义RPC服务
            testCustomRpcService();

            // 演示Q类查询操作
            testQueryOperations();

            // 演示ObjectService操作
            testObjectCrudOperations();
        });
        log.info("=== RPC 演示完成 ===");
    }

    /**
     * 演示自定义RPC服务
     */
    public void testCustomRpcService() {
        log.info("--- 演示自定义RPC服务 ---");

        // 演示 HelloTaskService 的 sayHello 方法
        HelloTaskService helloTaskService = S.service(HelloTaskService.class);
        String result = helloTaskService.sayHello("RPC Client");
        log.info("sayHello 调用结果: {}", result);
    }

    /**
     * 演示Q类查询操作
     */
    public void testQueryOperations() {
        log.info("--- 演示Q类查询操作 ---");

        // 演示基础查询
        testBasicQuery();

        // 演示条件查询
        testConditionalQuery();

        // 演示分页查询
        testPageQuery();

        // 演示统计查询
        testCountQuery();

        // 演示存在性查询
        testExistsQuery();
    }

    /**
     * 演示基础查询
     */
    private void testBasicQuery() {
        // 查询所有HelloTask对象
        List<HelloTask> allTasks = Q.result(HelloTask.class).noCondition().query();
        log.info("查询到 {} 个HelloTask对象", allTasks.size());

        // 查询第一个对象
        HelloTask firstTask = Q.result(HelloTask.class).noCondition().first();
        if (firstTask != null) {
            log.info("第一个任务: ID={}, Name={}", firstTask.getId(), firstTask.getName());
        } else {
            log.info("没有找到任何HelloTask对象");
        }
    }

    /**
     * 演示条件查询
     */
    private void testConditionalQuery() {
        // 根据状态查询
        List<HelloTask> activeTasks = Q.result(HelloTask.class)
                .where("status = ?", "ACTIVE")
                .query();
        log.info("查询到 {} 个活跃状态的任务", activeTasks.size());

        // 根据名称模糊查询
        List<HelloTask> namedTasks = Q.result(HelloTask.class)
                .where("name LIKE ?", "%演示%")
                .query();
        log.info("查询到 {} 个包含'演示'的任务", namedTasks.size());

        // 复合条件查询
        List<HelloTask> complexQuery = Q.result(HelloTask.class)
                .where("status = ? AND name LIKE ?", "ACTIVE", "%RPC%")
                .asc("name")
                .query();
        log.info("复合条件查询到 {} 个任务", complexQuery.size());
    }

    /**
     * 演示分页查询
     */
    private void testPageQuery() {
        // 分页查询
        var page = Q.result(HelloTask.class)
                .noCondition()
                .asc("name")
                .pageSize(5)
                .pageNumber(0)
                .queryPage();

        log.info("分页查询结果: 总数={}, 当前页={}, 页大小={}",
                page.getTotalElements(), page.getPageNumber(), page.getPageSize());

        page.getContent().forEach(task ->
                log.info("  任务: ID={}, Name={}", task.getId(), task.getName()));
    }

    /**
     * 演示统计查询
     */
    private void testCountQuery() {
        // 统计总数
        Long totalCount = Q.result(HelloTask.class).noCondition().count();
        log.info("HelloTask总数: {}", totalCount);

        // 条件统计
        Long activeCount = Q.result(HelloTask.class)
                .where("status = ?", "ACTIVE")
                .count();
        log.info("活跃状态任务数: {}", activeCount);
    }

    /**
     * 演示存在性查询
     */
    private void testExistsQuery() {
        // 检查是否存在特定条件的对象
        boolean hasActiveTasks = Q.result(HelloTask.class)
                .where("status = ?", "ACTIVE")
                .exists();
        log.info("是否存在活跃任务: {}", hasActiveTasks);

        boolean hasTestTasks = Q.result(HelloTask.class)
                .where("name LIKE ?", "%RPC%")
                .exists();
        log.info("是否存在RPC相关任务: {}", hasTestTasks);
    }

    /**
     * 演示对象CRUD操作
     */
    private void testObjectCrudOperations() {
        ObjectService objectService = S.service(ObjectService.class);

        // 创建对象
        HelloTask newTask = new HelloTask();
        newTask.setName("RPC演示任务");
        newTask.setDescription("通过RPC创建的演示任务");
        newTask.setStatus(HelloTask.TaskStatus.IN_PROGRESS);
        newTask.setCode("T-" + System.currentTimeMillis());
        newTask.setRevId("A");
        newTask.setTitle(newTask.getName() + " " + newTask.getCode());

        HelloTask savedTask = objectService.save(newTask);
        log.info("创建任务成功: ID={}, Name={}", savedTask.getId(), savedTask.getName());

        // 更新对象
        savedTask.setDescription("通过RPC更新的任务描述");
        savedTask.setStatus(HelloTask.TaskStatus.COMPLETED);
        HelloTask updatedTask = objectService.save(savedTask);
        log.info("更新任务成功: ID={}, Status={}", updatedTask.getId(), updatedTask.getStatus());

        // 查询对象
        HelloTask queriedTask = objectService.findById(savedTask.getId());
        if (queriedTask != null) {
            log.info("查询任务成功: ID={}, Description={}",
                    queriedTask.getId(), queriedTask.getDescription());
        }

        // 删除对象
        Q.result(HelloTask.class).where("id=?", savedTask.getId()).delete();
        log.info("删除任务成功: ID={}", savedTask.getId());

        // 验证删除
        HelloTask deletedTask = objectService.findById(savedTask.getId());
        if (deletedTask == null) {
            log.info("确认任务已删除: ID={}", savedTask.getId());
        }
    }

    /**
     * 创建演示任务对象
     */
    private HelloTask createTestTask(String name, String description) {
        HelloTask task = new HelloTask();
        task.setName(name);
        task.setDescription(description);
        task.setStatus(HelloTask.TaskStatus.IN_PROGRESS);
        task.setCode("T-" + System.currentTimeMillis());
        task.setRevId("A");
        task.setTitle(task.getName() + " " + task.getCode());
        return task;
    }
}