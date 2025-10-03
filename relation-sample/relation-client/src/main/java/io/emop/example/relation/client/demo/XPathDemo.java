package io.emop.example.relation.client.demo;

import io.emop.example.relation.model.RSampleProject;
import io.emop.example.relation.model.RSampleTask;
import io.emop.example.relation.service.RSampleProjectManagementService;
import io.emop.model.common.UserContext;
import io.emop.model.document.File;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.dsl.DSLExecutionService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * XPath 路径操作演示
 * 演示如何使用XPath进行复杂的数据访问，对比传统代码和JXPath表达式的差异
 * 同时展示平台内置DSL方法的使用
 */
@Slf4j
public class XPathDemo {

    public void demonstrateXPath() {
        log.info("开始XPath演示...");

        UserContext.runAsSystem(() -> {
            // 1. 准备测试数据
            RSampleProject project = prepareTestData();

            // 2. 传统代码 vs JXPath对比演示
            demonstrateTraditionalVsXPath(project);

            // 3. JXPath高级表达式演示
            demonstrateAdvancedXPath(project);

            // 4. 使用平台内置DSL展示树结构
            demonstrateTreeVisualization(project);
        });
    }

    /**
     * 准备测试数据
     * 注：数据准备仍使用服务方法，主要演示在于XPath表达式的使用
     */
    private RSampleProject prepareTestData() {
        log.info("--- 准备XPath演示数据 ---");

        // 使用服务创建完整的项目结构
        RSampleProjectManagementService service = S.service(RSampleProjectManagementService.class);
        RSampleProject project = service.createProjectWithTasks(
                "PROJ-XPATH-001",
                "XPath演示项目",
                "XPath管理员"
        );

        // 创建一些文件作为交付物
        File file1 = File.newModel();
        file1.setName("项目计划.docx");
        file1.setFileType("DOCX");
        file1.setPath("xpath:documents/plan.docx");
        file1.setFileSize(1024L);
        file1 = S.service(ObjectService.class).save(file1);

        File file2 = File.newModel();
        file2.setName("架构图.png");
        file2.setFileType("PNG");
        file2.setPath("xpath:images/architecture.png");
        file2.setFileSize(2048L);
        file2 = S.service(ObjectService.class).save(file2);

        // 为项目的第一个任务添加交付物
        List<RSampleTask> tasks = service.getProjectTaskTree(project.getId());
        if (!tasks.isEmpty()) {
            service.addDeliverablesToTask(tasks.get(0).getId(), List.of(file1.getId(), file2.getId()));
        }

        log.info("XPath演示数据准备完成");
        return project;
    }

    /**
     * 传统代码 vs JXPath对比演示
     * 展示传统层级展开代码和JXPath表达式的差异
     */
    private void demonstrateTraditionalVsXPath(RSampleProject project) {
        log.info("--- 传统代码 vs JXPath对比演示 ---");

        RSampleProject loadedProject = Q.id(project.getId());

        // === 传统代码方式 ===
        log.info("=== 传统代码方式 ===");

        // 1. 传统方式：逐层访问项目任务
        List<RSampleTask> tasks = loadedProject.get("tasks", List.class);
        log.info("传统方式 - 项目任务数量: {}", tasks != null ? tasks.size() : 0);

        if (tasks != null && !tasks.isEmpty()) {
            RSampleTask firstTask = tasks.get(0);
            String taskName = firstTask.get("name", String.class);
            log.info("传统方式 - 第一个任务名称: {}", taskName);

            // 传统方式：访问任务的交付物
            List<File> attachments = firstTask.get("attachments", List.class);
            if (attachments != null && !attachments.isEmpty()) {
                File firstFile = attachments.get(0);
                String fileName = firstFile.getName();
                log.info("传统方式 - 第一个交付物名称: {}", fileName);
            }
        }

        // === JXPath表达式方式 ===
        log.info("=== JXPath表达式方式 ===");

        // 1. JXPath方式：直接获取所有任务
        List<RSampleTask> xpathTasks = loadedProject.get("tasks[*]", List.class);
        log.info("JXPath方式 - 项目任务数量: {}", xpathTasks != null ? xpathTasks.size() : 0);

        // 2. JXPath方式：直接获取第一个任务名称, 下标从1开始
        List<?> firstTaskName = loadedProject.get("tasks[1]/name", List.class);
        log.info("JXPath方式 - 第一个任务名称: {}", firstTaskName);

        // 3. JXPath方式：直接获取第一个任务的第一个交付物名称, 下标从1开始
        List<?> firstFileName = loadedProject.get("tasks[1]/attachments[1]/name", List.class);
        log.info("JXPath方式 - 第一个交付物名称: {}", firstFileName);

        // 4. JXPath方式：获取所有任务名称
        List<?> allTaskNames = loadedProject.get("tasks[*]/name", List.class);
        log.info("JXPath方式 - 所有任务名称: {}", allTaskNames);

        // 5. JXPath方式：获取所有交付物名称
        List<String> allFileNames = loadedProject.get("tasks[*]/attachments[*]/name", List.class);
        log.info("JXPath方式 - 所有交付物名称: {}", allFileNames);
    }

    /**
     * JXPath高级表达式演示
     * 展示JXPath的强大查询能力
     */
    private void demonstrateAdvancedXPath(RSampleProject project) {
        log.info("--- JXPath高级表达式演示 ---");

        RSampleProject loadedProject = Q.id(project.getId());

        // 1. 条件过滤 - 查找特定负责人的任务
        List<RSampleTask> architectTasks = loadedProject.get("tasks[@assignee='架构师B']", List.class);
        log.info("JXPath条件查询 - 架构师B负责的任务数量: {}", architectTasks != null ? architectTasks.size() : 0);
        if (architectTasks != null && !architectTasks.isEmpty()) {
            log.info("  任务名称: {}", architectTasks.get(0).getName());
        }

        // 2. 嵌套条件查询 - 查找前端工程师负责的子任务
        List<RSampleTask> frontendSubTasks = loadedProject.get("tasks[*]/subTasks[@assignee='前端工程师']", List.class);
        log.info("JXPath嵌套查询 - 前端工程师负责的子任务数量: {}", frontendSubTasks != null ? frontendSubTasks.size() : 0);
        if (frontendSubTasks != null && !frontendSubTasks.isEmpty()) {
            log.info("  子任务名称: {}", frontendSubTasks.get(0).getName());
        }

        // 3. 多条件查询 - 查找特定状态和优先级的任务
        List<RSampleTask> todoMediumTasks = loadedProject.get("tasks[@status='TODO' and @priority='MEDIUM']", List.class);
        log.info("JXPath多条件查询 - TODO状态且中等优先级的任务数量: {}", todoMediumTasks != null ? todoMediumTasks.size() : 0);

        // 4. 函数使用 - 统计任务数量
        List<Integer> taskCount = loadedProject.get("count(tasks[*])", List.class);
        log.info("JXPath函数 - 任务总数: {}", taskCount);

        // 5. 字符串函数 - 查找名称包含特定字符的任务
        List<RSampleTask> designTasks = loadedProject.get("tasks[contains(name, '设计')]", List.class);
        log.info("JXPath字符串函数 - 名称包含'设计'的任务数量: {}", designTasks != null ? designTasks.size() : 0);

        // 6. 位置函数 - 获取最后一个任务
        List<RSampleTask> lastTask = loadedProject.get("tasks[last()]", List.class);
        if (lastTask != null) {
            log.info("JXPath位置函数 - 最后一个任务: {}", lastTask.iterator().next().getName());
        }

        // 7. 复杂路径 - 获取所有子任务的项目名称（反向关系）
        List<String> projectNames = loadedProject.get("tasks[*]/subTasks[*]/project/name", List.class);
        log.info("JXPath复杂路径 - 通过子任务反向获取的项目名称数量: {}", projectNames != null ? projectNames.size() : 0);

        // 8. 属性值比较 - 查找进度大于0的任务
        List<RSampleTask> progressTasks = loadedProject.get("tasks[@progress>0]", List.class);
        log.info("JXPath数值比较 - 进度大于0的任务数量: {}", progressTasks != null ? progressTasks.size() : 0);
    }

    /**
     * 使用平台内置DSL展示树结构
     * 替代传统的RPC调用，使用平台提供的DSL方法
     */
    private void demonstrateTreeVisualization(RSampleProject project) {
        log.info("--- 平台内置DSL树结构展示 ---");

        // 使用平台内置DSL方法展示项目树结构
        DSLExecutionService dslService = S.service(DSLExecutionService.class);

        String dslCommand = """
                show object RSampleProject(id=%d) as tree with {
                    maxDepth: 5,
                    relations: [tasks, subTasks, attachments],
                    attributes: [name, code, revId]
                }
                """;
        // 构建DSL命令：show object RSampleProject(id=projectId) as tree
        dslCommand = String.format(dslCommand, project.getId());

        log.info("执行DSL命令: {}", dslCommand);

        // 执行DSL命令
        String treeResult = dslService.execute(dslCommand).toString();

        log.info("项目树结构展示:\n{}", treeResult);
    }

    /**
     * 递归构建任务树
     */
    private void buildTaskTree(RSampleTask task, StringBuilder builder, String prefix, String childPrefix) {
        builder.append(prefix)
                .append(task.getName())
                .append(" (负责人: ").append(task.getAssignee()).append(")")
                .append("\n");

        // 使用JXPath获取子任务
        List<RSampleTask> subTasks = task.get("subTasks[*]", List.class);
        if (subTasks != null && !subTasks.isEmpty()) {
            for (int i = 0; i < subTasks.size(); i++) {
                RSampleTask subTask = subTasks.get(i);
                boolean isLast = (i == subTasks.size() - 1);
                buildTaskTree(subTask, builder,
                        childPrefix + (isLast ? "└── " : "├── "),
                        childPrefix + (isLast ? "    " : "│   "));
            }
        }
    }
}