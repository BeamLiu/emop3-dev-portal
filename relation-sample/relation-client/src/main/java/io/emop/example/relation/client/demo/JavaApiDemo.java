package io.emop.example.relation.client.demo;

import io.emop.example.relation.model.RSampleProject;
import io.emop.example.relation.model.RSampleTask;
import io.emop.example.relation.service.RSampleProjectManagementService;
import io.emop.model.common.UserContext;
import io.emop.model.document.File;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.relation.RelationType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Java API 操作演示
 * 演示如何使用Java API进行关系操作
 */
@Slf4j
public class JavaApiDemo {

    private final String timestamp = String.valueOf(System.currentTimeMillis());

    public void demonstrateJavaApi() {
        log.info("开始Java API演示...");
        UserContext.runAsSystem(() -> {
            // 1. 演示RPC服务调用
            demonstrateRpcService();

            // 2. 演示结构关系操作
            demonstrateStructuralRelations();

            // 3. 演示关联关系操作
            demonstrateAssociateRelations();

            // 4. 演示查询操作
            demonstrateQueryOperations();
        });
    }

    /**
     * 演示RPC服务调用
     */
    private void demonstrateRpcService() {
        log.info("--- RPC服务调用演示 ---");

        // 通过RPC调用创建项目
        RSampleProjectManagementService service = S.service(RSampleProjectManagementService.class);
        RSampleProject project = service.createProjectWithTasks(
                "PROJ-RPC-" + timestamp,
                "Java API演示项目",
                "张三"
        );

        log.info("通过RPC创建项目成功: {} - {}", project.getCode(), project.getName());

        // 获取项目任务树
        List<RSampleTask> taskTree = service.getProjectTaskTree(project.getId());
        log.info("项目包含 {} 个根任务", taskTree.size());

        for (RSampleTask task : taskTree) {
            log.info("  根任务: {}", task.getName());
            List<RSampleTask> subTasks = task.get("subTasks");
            if (subTasks != null) {
                for (RSampleTask subTask : subTasks) {
                    log.info("    子任务: {}", subTask.getName());
                }
            }
        }
    }

    /**
     * 演示结构关系操作
     */
    private void demonstrateStructuralRelations() {
        log.info("--- 结构关系操作演示 ---");

        // 1. 创建项目
        RSampleProject project = RSampleProject.newModel("PROJ-STRUCT-" + timestamp, "A");
        project.setName("结构关系演示项目");
        project.setProjectManager("李四");
        project = S.service(ObjectService.class).save(project);

        // 2. 创建父任务
        RSampleTask parentTask = RSampleTask.newModel("TASK-PARENT-" + timestamp, "A");
        parentTask.setName("父任务");
        parentTask.setAssignee("王五");
        parentTask.setProjectId(project.getId()); // 设置外键
        parentTask = S.service(ObjectService.class).save(parentTask);

        // 3. 创建子任务
        RSampleTask childTask1 = RSampleTask.newModel("TASK-CHILD-1-" + timestamp, "A");
        childTask1.setName("子任务1");
        childTask1.setAssignee("赵六");
        childTask1.setProjectId(project.getId());
        childTask1.setParentTaskId(parentTask.getId()); // 设置父任务外键
        childTask1 = S.service(ObjectService.class).save(childTask1);

        RSampleTask childTask2 = RSampleTask.newModel("TASK-CHILD-2-" + timestamp, "A");
        childTask2.setName("子任务2");
        childTask2.setAssignee("孙七");
        childTask2.setProjectId(project.getId());
        childTask2.setParentTaskId(parentTask.getId()); // 设置父任务外键
        childTask2 = S.service(ObjectService.class).save(childTask2);

        // 4. 验证结构关系
        RSampleProject loadedProject = Q.id(project.getId());

        List<RSampleTask> projectTasks = loadedProject.get("tasks");
        log.info("项目包含 {} 个任务", projectTasks != null ? projectTasks.size() : 0);

        RSampleTask loadedParentTask = Q.id(parentTask.getId());

        List<RSampleTask> subTasks = loadedParentTask.get("subTasks");
        log.info("父任务包含 {} 个子任务", subTasks != null ? subTasks.size() : 0);

        if (subTasks != null) {
            for (RSampleTask subTask : subTasks) {
                log.info("  子任务: {} (负责人: {})", subTask.getName(), subTask.getAssignee());
            }
        }
    }

    /**
     * 演示关联关系操作
     */
    private void demonstrateAssociateRelations() {
        log.info("--- 关联关系操作演示 ---");

        // 1. 创建任务
        RSampleTask task = RSampleTask.newModel("TASK-ASSOC-" + timestamp, "A");
        task.setName("关联关系演示任务");
        task.setAssignee("周八");
        task = S.service(ObjectService.class).save(task);

        // 2. 创建文件对象
        File file1 = File.newModel();
        file1.setName("需求文档.docx");
        file1.setFileType("DOCX");
        file1.setPath("demo:documents/requirement.docx");
        file1.setFileSize(1024L);
        file1 = S.service(ObjectService.class).save(file1);

        File file2 = File.newModel();
        file2.setName("设计图.png");
        file2.setFileType("PNG");
        file2.setPath("demo:images/design.png");
        file2.setFileSize(2048L);
        file2 = S.service(ObjectService.class).save(file2);

        // 3. 建立关联关系
        AssociateRelationService relationService = S.service(AssociateRelationService.class);
        relationService.appendRelation(task, RelationType.valueOf("attachments"), file1, file2);

        log.info("为任务建立关联关系: {} -> [{}, {}]", task.getName(), file1.getName(), file2.getName());

        // 4. 验证关联关系
        RSampleTask loadedTask = Q.id(task.getId());

        List<File> attachments = loadedTask.get("attachments");
        log.info("任务关联了 {} 个文件", attachments != null ? attachments.size() : 0);

        if (attachments != null) {
            for (File attachment : attachments) {
                log.info("  关联文件: {} (类型: {})", attachment.getName(), attachment.getFileType());
            }
        }
    }

    /**
     * 演示查询操作
     */
    private void demonstrateQueryOperations() {
        log.info("--- 查询操作演示 ---");

        // 1. 基础查询
        List<RSampleProject> allProjects = Q.result(RSampleProject.class)
                .where("name like ?", "%演示%")
                .asc("code")
                .query();

        log.info("查询到 {} 个演示项目", allProjects.size());

        // 2. 关联查询
        List<RSampleTask> tasksWithFiles = Q.result(RSampleTask.class)
                .where("name like ?", "%关联%")
                .query();

        for (RSampleTask task : tasksWithFiles) {
            List<File> files = task.get("attachments");
            if (files != null && !files.isEmpty()) {
                log.info("任务 '{}' 有 {} 个关联文件", task.getName(), files.size());
            }
        }

        // 3. 统计查询
        long projectCount = Q.result(RSampleProject.class)
                .where("projectManager = ?", "张三")
                .count();

        log.info("张三负责的项目数量: {}", projectCount);

        // 4. 存在性检查
        boolean hasUrgentTasks = Q.result(RSampleTask.class)
                .where("priority = ?", RSampleTask.Priority.URGENT)
                .exists();

        log.info("是否存在紧急任务: {}", hasUrgentTasks);
    }
}