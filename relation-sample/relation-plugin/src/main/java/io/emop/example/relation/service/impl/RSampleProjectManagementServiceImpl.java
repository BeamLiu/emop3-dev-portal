package io.emop.example.relation.service.impl;

import io.emop.example.relation.model.RSampleProject;
import io.emop.example.relation.model.RSampleTask;
import io.emop.example.relation.service.RSampleProjectManagementService;
import io.emop.model.annotation.Service;
import io.emop.model.annotation.Transactional;
import io.emop.model.document.File;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.relation.RelationType;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目管理服务实现
 * 演示各种关系操作的具体实现
 */
@Slf4j
@Service
public class RSampleProjectManagementServiceImpl implements RSampleProjectManagementService {

    @Override
    @Transactional
    public RSampleProject createProjectWithTasks(String projectCode, String name, String projectManager) {
        log.info("创建项目: {} - {}", projectCode, name);

        // 1. 创建项目
        RSampleProject project = RSampleProject.newModel(projectCode, "A");
        project.setName(name);
        project.setProjectManager(projectManager);
        project.setStatus(RSampleProject.ProjectStatus.PLANNING);
        project.setStartDate(LocalDate.now());
        project.setCode("PROJ-" + System.currentTimeMillis());
        project.setRevId("A");
        project.setEndDate(LocalDate.now().plusMonths(3));

        // 使用业务键保存
        project = S.service(ObjectService.class).save(project);

        // 2. 创建根任务
        RSampleTask rootTask = createTask(project.getId(), null, "项目启动", projectManager);
        RSampleTask designTask = createTask(project.getId(), null, "需求分析与设计", projectManager);
        RSampleTask devTask = createTask(project.getId(), null, "开发实施", projectManager);

        // 3. 为设计任务创建子任务
        createTask(project.getId(), designTask.getId(), "需求调研", "分析师A");
        createTask(project.getId(), designTask.getId(), "系统设计", "架构师B");
        createTask(project.getId(), designTask.getId(), "UI设计", "设计师C");

        // 4. 为开发任务创建子任务
        createTask(project.getId(), devTask.getId(), "前端开发", "前端工程师");
        createTask(project.getId(), devTask.getId(), "后端开发", "后端工程师");
        createTask(project.getId(), devTask.getId(), "测试", "测试工程师");

        log.info("项目创建完成，ID: {}", project.getId());
        return project;
    }

    @Override
    @Transactional
    public void addDeliverablesToTask(Long taskId, List<Long> fileIds) {
        log.info("为任务 {} 添加交付物: {}", taskId, fileIds);

        // 1. 获取任务对象
        RSampleTask task = Q.id(taskId);

        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }

        // 2. 获取文件对象
        List<File> files = Q.result(File.class).where("id = ANY(?)", new Object[]{fileIds.toArray(new Long[0])})
                .query();

        if (files.isEmpty()) {
            log.warn("未找到有效的文件: {}", fileIds);
            return;
        }

        // 3. 建立关联关系
        AssociateRelationService relationService = S.service(AssociateRelationService.class);
        relationService.appendRelation(task, RelationType.valueOf("attachments"), files.toArray(new File[0]));

        log.info("成功为任务添加 {} 个交付物", files.size());
    }

    @Override
    public List<RSampleTask> getProjectTaskTree(Long projectId) {
        log.info("获取项目 {} 的任务树", projectId);

        // 查询项目下的所有根任务（没有父任务的任务）
        List<RSampleTask> rootTasks = Q.result(RSampleTask.class)
                .where("projectId = ? and parentTaskId is null", projectId)
                .asc("code")
                .query();

        // 为每个根任务加载子任务树
        for (RSampleTask rootTask : rootTasks) {
            loadSubTasksRecursively(rootTask);
        }

        return rootTasks;
    }

    /**
     * 创建任务的辅助方法
     */
    private RSampleTask createTask(Long projectId, Long parentTaskId, String name, String assignee) {
        // 生成任务编码
        String taskCode = "TASK-" + System.currentTimeMillis() + "-" + Math.random();

        RSampleTask task = RSampleTask.newModel(taskCode, "A");
        task.setName(name);
        task.setAssignee(assignee);
        task.setStatus(RSampleTask.TaskStatus.TODO);
        task.setPriority(RSampleTask.Priority.MEDIUM);
        task.setStartDate(LocalDate.now());
        task.setDueDate(LocalDate.now().plusWeeks(2));
        task.setProgress(0);
        task.setRevId("A");

        // 设置外键关系
        task.setProjectId(projectId);
        task.setParentTaskId(parentTaskId);

        // 使用业务键保存
        task = S.service(ObjectService.class).save(task);

        log.info("创建任务: {} - {}", task.getCode(), task.getName());
        return task;
    }

    /**
     * 递归加载子任务
     */
    private void loadSubTasksRecursively(RSampleTask task) {
        // 触发懒加载
        List<RSampleTask> subTasks = task.get("subTasks", List.class);
        if (subTasks != null) {
            for (RSampleTask subTask : subTasks) {
                loadSubTasksRecursively(subTask);
            }
        }
    }
}