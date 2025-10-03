package io.emop.example.relation.model;

import io.emop.model.annotation.*;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.Schema;
import io.emop.model.document.File;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 任务模型 - 演示树形结构关系和关联关系
 * 
 * 关系说明:
 * 1. 结构关系: Task -> Task (树形结构，父子任务)
 * 2. 结构关系: Project -> Task (外键关系，任务属于项目)
 * 3. 关联关系: Task -> File (多对多，任务交付物)
 */
@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "RSampleTask")
@LocalizedNameDesc(name = "示例任务", description = "任务管理关系演示模型")
@BusinessKeys({
    @BusinessKeys.BusinessKey({"code", "revId"})
})
public final class RSampleTask extends ItemRevision {
    

    
    @QuerySqlField
    @LocalizedNameDesc(name = "任务描述", description = "任务的详细描述")
    private String description;
    
    @QuerySqlField(index = true)
    @LocalizedNameDesc(name = "任务状态", description = "任务的当前状态")
    private TaskStatus status = TaskStatus.TODO;
    
    @QuerySqlField(index = true)
    @LocalizedNameDesc(name = "优先级", description = "任务优先级")
    private Priority priority = Priority.MEDIUM;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "开始日期", description = "任务开始日期")
    private LocalDate startDate;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "截止日期", description = "任务截止日期")
    private LocalDate dueDate;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "负责人", description = "任务负责人")
    private String assignee;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "完成百分比", description = "任务完成进度(0-100)")
    private Integer progress = 0;
    
    // 外键字段：所属项目
    @QuerySqlField(index = true)
    @LocalizedNameDesc(name = "项目ID", description = "任务所属的项目ID")
    private Long projectId;
    
    // 外键字段：父任务（用于树形结构）
    @QuerySqlField(index = true)
    @LocalizedNameDesc(name = "父任务ID", description = "父任务ID，用于构建任务树")
    private Long parentTaskId;
    
    // 结构关系：所属项目
    @StructuralRelation(foreignKeyField = "projectId")
    @LocalizedNameDesc(name = "所属项目", description = "任务所属的项目")
    private RSampleProject project;
    
    // 结构关系：子任务列表（一对多）
    @StructuralRelation(foreignKeyField = "parentTaskId")
    @LocalizedNameDesc(name = "子任务", description = "当前任务的子任务列表")
    private List<RSampleTask> subTasks;
    
    // 关联关系：任务交付物（多对多）
    @AssociateRelation
    @LocalizedNameDesc(name = "任务交付物", description = "任务相关的文件和交付物")
    private List<File> attachments;

    public RSampleTask() {
        super(RSampleTask.class.getName());
    }

    public static RSampleTask newModel(String code, String revId) {
        RSampleTask task = new RSampleTask();
        task.setCode(code);
        task.setRevId(revId);
        return task;
    }

    /**
     * 任务状态枚举
     */
    @Getter
    public enum TaskStatus {
        TODO("待办"),
        IN_PROGRESS("进行中"),
        REVIEW("评审中"),
        DONE("已完成"),
        CANCELLED("已取消");
        
        private final String displayName;
        
        TaskStatus(String displayName) {
            this.displayName = displayName;
        }
    }
    
    /**
     * 优先级枚举
     */
    @Getter
    public enum Priority {
        LOW("低"),
        MEDIUM("中"),
        HIGH("高"),
        URGENT("紧急");
        
        private final String displayName;
        
        Priority(String displayName) {
            this.displayName = displayName;
        }
    }
}