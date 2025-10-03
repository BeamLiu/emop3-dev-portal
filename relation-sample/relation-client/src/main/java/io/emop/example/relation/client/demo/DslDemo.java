package io.emop.example.relation.client.demo;

import io.emop.model.common.UserContext;
import io.emop.service.S;
import io.emop.service.api.dsl.DSLExecutionService;
import lombok.extern.slf4j.Slf4j;

/**
 * DSL 数据操作演示
 * 演示如何使用DSL进行数据操作
 */
@Slf4j
public class DslDemo {

    private final String timestamp = String.valueOf(System.currentTimeMillis());
    
    public void demonstrateDsl() {
        log.info("开始DSL演示...");

        UserContext.runAsSystem(()->{
            // 1. 创建对象
            demonstrateCreateObjects();

            // 2. 建立关系
            demonstrateCreateRelations();

            // 3. 查询数据
            demonstrateQueryData();

            // 4. 更新数据
            demonstrateUpdateData();

            // 5. 删除操作
            demonstrateDeleteOperations();
        });
    }
    
    /**
     * 演示创建对象
     */
    private void demonstrateCreateObjects() {
        log.info("--- DSL创建对象演示 ---");

        DSLExecutionService dslService = S.service(DSLExecutionService.class);
        
        // 创建项目
        String createProjectDsl = """
            create object RSampleProject {
                code: "PROJ-DSL-%s",
                revId: "A",
                name: "DSL演示项目",
                projectManager: "DSL管理员",
                status: "PLANNING",
                description: "通过DSL创建的演示项目"
            }
            """.formatted(timestamp);
        
        Object result = dslService.execute(createProjectDsl);
        log.info("DSL创建项目结果: {}", result);
        
        // 创建任务
        String createTaskDsl = """
            create object RSampleTask {
                code: "TASK-DSL-%s",
                revId: "A",
                name: "DSL演示任务",
                assignee: "DSL开发者",
                status: "TODO",
                priority: "MEDIUM",
                description: "通过DSL创建的演示任务"
            }
            """.formatted(timestamp);
        
        result = dslService.execute(createTaskDsl);
        log.info("DSL创建任务结果: {}", result);
    }
    
    /**
     * 演示建立关系
     */
    private void demonstrateCreateRelations() {
        log.info("--- DSL建立关系演示 ---");

        DSLExecutionService dslService = S.service(DSLExecutionService.class);

        // 建立项目和任务的关系
        String createRelationDsl = """
            relation RSampleProject(code='PROJ-DSL-%s' and revId='A') {
                -> RSampleTask(code='TASK-DSL-%s' and revId='A') as tasks
            }
            """.formatted(timestamp, timestamp);

        Object result = dslService.execute(createRelationDsl);
        log.info("DSL建立关系结果: {}", result);
    }

    /**
     * 演示查询数据
     */
    private void demonstrateQueryData() {
        log.info("--- DSL查询数据演示 ---");

        DSLExecutionService dslService = S.service(DSLExecutionService.class);
        
        // 查询项目
        String queryProjectDsl = """
            show object RSampleProject(name like '%DSL%')
            """;
        
        Object result = dslService.execute(queryProjectDsl);
        log.info("DSL查询项目结果: {}", result);
        
        // 查询任务
        String queryTaskDsl = """
            show object RSampleTask(assignee = 'DSL开发者')
            """;
        
        result = dslService.execute(queryTaskDsl);
        log.info("DSL查询任务结果: {}", result);
    }
    
    /**
     * 演示更新数据
     */
    private void demonstrateUpdateData() {
        log.info("--- DSL更新数据演示 ---");

        DSLExecutionService dslService = S.service(DSLExecutionService.class);
        
        // 更新项目状态
        String updateProjectDsl = """
            update object RSampleProject where code = 'PROJ-DSL-%s' {
                status: "IN_PROGRESS",
                description: "项目已开始执行"
            }
            """.formatted(timestamp);
        
        Object result = dslService.execute(updateProjectDsl);
        log.info("DSL更新项目结果: {}", result);
        
        // 更新任务状态
        String updateTaskDsl = """
            update object RSampleTask where code = 'TASK-DSL-%s' {
                status: "IN_PROGRESS",
                progress: 50
            }
            """.formatted(timestamp);
        
        result = dslService.execute(updateTaskDsl);
        log.info("DSL更新任务结果: {}", result);
    }
    
    /**
     * 演示删除操作
     */
    private void demonstrateDeleteOperations() {
        log.info("--- DSL删除操作演示 ---");

        DSLExecutionService dslService = S.service(DSLExecutionService.class);

        // 删除关系
        String removeRelationDsl = """
            remove relation between RSampleTask(code = 'TASK-DSL-%s') and RSampleProject(code = 'PROJ-DSL-%s')
            """.formatted(timestamp, timestamp);

        // TODO: 检查结构关系是否有效移除
        Object result = dslService.execute(removeRelationDsl);
        log.info("DSL删除关系结果: {}", result);

        // 注意：实际删除对象操作需要谨慎，这里只是演示语法
        log.info("删除对象操作已跳过，避免影响其他演示");
    }
}