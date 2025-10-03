package io.emop.example.relation.service;

import io.emop.example.relation.model.RSampleProject;
import io.emop.example.relation.model.RSampleTask;
import io.emop.model.annotation.Remote;
import io.emop.model.document.File;

import java.util.List;

/**
 * 项目管理服务接口
 * 演示关系操作的业务服务
 */
@Remote
public interface RSampleProjectManagementService {
    
    /**
     * 创建项目并初始化基本任务结构
     * 
     * @param projectCode 项目编码
     * @param name 项目名称
     * @param projectManager 项目经理
     * @return 创建的项目
     */
    RSampleProject createProjectWithTasks(String projectCode, String name, String projectManager);
    
    /**
     * 为任务添加交付物文件
     * 
     * @param taskId 任务ID
     * @param fileIds 文件ID列表
     */
    void addDeliverablesToTask(Long taskId, List<Long> fileIds);
    
    /**
     * 获取项目的完整任务树
     * 
     * @param projectId 项目ID
     * @return 任务树结构
     */
    List<RSampleTask> getProjectTaskTree(Long projectId);
}