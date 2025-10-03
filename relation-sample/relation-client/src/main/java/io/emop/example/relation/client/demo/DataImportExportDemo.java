package io.emop.example.relation.client.demo;

import io.emop.example.relation.model.RSampleProject;
import io.emop.example.relation.model.RSampleTask;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.dataexchange.importer.DataImportService;
import io.emop.service.api.dataexchange.importer.ImportRequest;
import io.emop.service.api.dataexchange.importer.ImportRequest.*;
import io.emop.service.api.dataexchange.importer.ImportResult;
import io.emop.service.api.dsl.DSLExecutionService;
import io.emop.service.api.storage.StorageService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 数据导入导出演示
 * 演示如何使用数据导入导出功能
 */
@Slf4j
public class DataImportExportDemo {

    public void demonstrateImportExport() {
        log.info("开始数据导入导出演示...");
        UserContext.runAsSystem(() -> {
            // 1. 表格数据导入演示
            demonstrateTableImport();

            // 2. 树形数据导入演示
            demonstrateTreeImport();

            // 3. 文件导入演示
            demonstrateFileImport();
        });
    }

    /**
     * 表格数据导入演示
     */
    private void demonstrateTableImport() {
        log.info("--- 表格数据导入演示 ---");

        // 1. 准备表格数据
        TableData tableData = TableData.builder()
                .headers(Arrays.asList("项目编码", "项目名称", "项目经理", "状态", "描述"))
                .rows(Arrays.asList(
                        Map.of("项目编码", "PROJ-IMPORT-001", "项目名称", "导入演示项目1", "项目经理", "导入管理员1", "状态", "PLANNING", "描述", "通过表格导入的项目1"),
                        Map.of("项目编码", "PROJ-IMPORT-002", "项目名称", "导入演示项目2", "项目经理", "导入管理员2", "状态", "IN_PROGRESS", "描述", "通过表格导入的项目2"),
                        Map.of("项目编码", "PROJ-IMPORT-003", "项目名称", "导入演示项目3", "项目经理", "导入管理员3", "状态", "COMPLETED", "描述", "通过表格导入的项目3")
                ))
                .build();

        // 2. 配置导入请求
        ImportRequest request = ImportRequest.builder()
                .tableData(tableData)
                .targetType(RSampleProject.class.getName())
                .columnMappings(Map.of(
                        "项目编码", List.of("code"),
                        "项目名称", List.of("name"),
                        "项目经理", List.of("projectManager"),
                        "状态", List.of("status"),
                        "描述", List.of("description")
                ))
                .config(ImportConfigModel.builder()
                        .existence(DuplicateStrategy.update) // 存在则更新
                        .dataMappingScript("""
                                data.put("revId", "A")
                                return data
                                """) //设置固定的版本信息
                        .build())
                .build();

        // 3. 执行导入
        DataImportService importService = S.service(DataImportService.class);
        ImportResult result = importService.importTable(request);

        log.info("表格导入结果: {} / {}",
                result.getProcessedRows(), result.getTotalRows());

        if (result.getStatus() != ImportResult.ImportStatus.COMPLETED || !result.getErrors().isEmpty()) {
            log.warn("导入错误: {}", result.getErrors());
            throw new RuntimeException("导入错误: " + result.getErrors());
        }
    }

    /**
     * 树形数据导入演示
     */
    private void demonstrateTreeImport() {
        log.info("--- 树形数据导入演示 ---");

        // 1. 先创建一个项目作为任务的容器
        RSampleProject project = RSampleProject.newModel("PROJ-TREE-001", "A");
        project.setName("树形导入演示项目");
        project.setProjectManager("树形管理员");
        project = S.service(ObjectService.class).upsertByBusinessKey(project);

        // 2. 准备树形数据
        TableData treeData = TableData.builder()
                .headers(Arrays.asList("任务编码", "任务标题", "负责人", "父任务编码", "项目ID"))
                .rows(Arrays.asList(
                        Map.of("任务编码", "TASK-ROOT-001", "任务标题", "根任务1", "负责人", "负责人A", "父任务编码", "", "项目ID", project.getId().toString()),
                        Map.of("任务编码", "TASK-CHILD-001", "任务标题", "子任务1-1", "负责人", "负责人B", "父任务编码", "TASK-ROOT-001", "项目ID", project.getId().toString()),
                        Map.of("任务编码", "TASK-CHILD-002", "任务标题", "子任务1-2", "负责人", "负责人C", "父任务编码", "TASK-ROOT-001", "项目ID", project.getId().toString()),
                        Map.of("任务编码", "TASK-ROOT-002", "任务标题", "根任务2", "负责人", "负责人D", "父任务编码", "", "项目ID", project.getId().toString()),
                        Map.of("任务编码", "TASK-CHILD-003", "任务标题", "子任务2-1", "负责人", "负责人E", "父任务编码", "TASK-ROOT-002", "项目ID", project.getId().toString())
                ))
                .build();

        // 3. 配置树形导入请求
        TreeImportRequest treeRequest = TreeImportRequest.builder()
                .tableData(treeData)
                .targetType(RSampleTask.class.getName())
                .parentKeyMapping(ParentKeyMapping.builder()
                        .fromColumns(List.of("父任务编码"))
                        .toColumns(List.of("任务编码"))
                        .build())
                .columnMappings(Map.of(
                        "任务编码", List.of("code"),
                        "任务标题", List.of("name"),
                        "负责人", List.of("assignee"),
                        "项目ID", List.of("projectId")
                ))
                .config(ImportConfigModel.builder()
                        .existence(DuplicateStrategy.update)
                        .relationType("subTasks") // 使用结构关系
                        .dataMappingScript("""
                                data.put("revId", "A")
                                return data
                                """)
                        .build())
                .build();

        // 4. 执行树形导入
        DataImportService importService = S.service(DataImportService.class);
        ImportResult result = importService.importTree(treeRequest);

        log.info("树形导入结果: {} / {}",
                result.getProcessedRows(), result.getTotalRows());

        if (result.getStatus() != ImportResult.ImportStatus.COMPLETED || !result.getErrors().isEmpty()) {
            log.warn("导入错误: {}", result.getErrors());
            throw new RuntimeException("导入错误: " + result.getErrors());
        }

        // 验证树形结构
        List<RSampleTask> rootTasks = Q.result(RSampleTask.class)
                .where("projectId = ? and parentTaskId is null", project.getId())
                .query();

        log.info("导入后的根任务数量: {}", rootTasks.size());
        String dslCommand = String.format("""
                show object RSampleProject(id=%d) as tree with {
                    maxDepth: 5,
                    relations: [tasks, subTasks, attachments],
                    attributes: [name, code, revId]
                }
                """, project.getId());
        String treeResult = S.service(DSLExecutionService.class).execute(dslCommand).toString();

        log.info("导入后的项目树结构展示:\n{}", treeResult);
    }

    /**
     * 文件导入演示
     */
    private void demonstrateFileImport() {
        log.info("--- 文件导入演示 ---");

        try {
            // 1. 先创建一个项目作为任务的容器
            RSampleProject project = RSampleProject.newModel("PROJ-FILE-001", "A");
            project.setName("文件导入演示项目");
            project.setProjectManager("文件管理员");
            project = S.service(ObjectService.class).upsertByBusinessKey(project);

            // 2. 准备CSV文件路径
            String csvFileName = "sample-tasks.csv";
            File csvFile = new File(csvFileName);

            if (!csvFile.exists()) {
                log.warn("CSV文件不存在: {}", csvFileName);
                return;
            }

            // 3. 上传文件到存储服务
            StorageService storageService = S.service(StorageService.class);
            String uploadedFilePath;

            try (FileInputStream fileInputStream = new FileInputStream(csvFile)) {
                uploadedFilePath = storageService.upload("temp:upload/" + System.currentTimeMillis(), csvFileName, fileInputStream, true);
                log.info("文件上传成功，存储路径: {}", uploadedFilePath);
            }

            // 4. 配置树形导入请求，使用filePath而不是tableData
            TreeImportRequest treeRequest = TreeImportRequest.builder()
                    .filePath(uploadedFilePath)
                    .targetType(RSampleTask.class.getName())
                    .parentKeyMapping(ParentKeyMapping.builder()
                            .fromColumns(List.of("父任务编码"))
                            .toColumns(List.of("任务编码"))
                            .build())
                    .columnMappings(Map.of(
                            "任务编码", List.of("code"),
                            "任务标题", List.of("name"),
                            "负责人", List.of("assignee"),
                            "项目ID", List.of("projectId")
                    ))
                    .config(ImportConfigModel.builder()
                            .existence(DuplicateStrategy.update)
                            .relationType("subTasks") // 使用结构关系
                            .dataMappingScript(String.format("""
                                    data.put("revId", "A")
                                    data.put("projectId", %d)
                                    return data
                                    """, project.getId()))
                            .build())
                    .build();

            // 5. 执行树形导入
            DataImportService importService = S.service(DataImportService.class);
            ImportResult result = importService.importTree(treeRequest);

            log.info("文件导入结果: {} / {}",
                    result.getProcessedRows(), result.getTotalRows());

            if (result.getStatus() != ImportResult.ImportStatus.COMPLETED || !result.getErrors().isEmpty()) {
                log.warn("导入错误: {}", result.getErrors());
                throw new RuntimeException("导入错误: " + result.getErrors());
            }

            // 6. 验证导入结果
            List<RSampleTask> rootTasks = Q.result(RSampleTask.class)
                    .where("projectId = ? and parentTaskId is null", project.getId())
                    .query();

            log.info("导入后的根任务数量: {}", rootTasks.size());

            // 7. 使用DSL展示项目树结构
            String dslCommand = String.format("""
                    show object RSampleProject(id=%d) as tree with {
                        maxDepth: 5,
                        relations: [tasks, subTasks, attachments],
                        attributes: [name, code, revId]
                    }
                    """, project.getId());
            String treeResult = S.service(DSLExecutionService.class).execute(dslCommand).toString();

            log.info("文件导入后的项目树结构展示:\n{}", treeResult);

        } catch (IOException e) {
            log.error("文件操作失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件操作失败", e);
        }
    }
}