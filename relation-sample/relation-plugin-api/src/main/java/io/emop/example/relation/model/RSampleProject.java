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
 * 项目模型 - 演示结构关系和关联关系
 * 
 * 关系说明:
 * 1. 结构关系: Project -> Task (一对多，树形结构)
 * 2. 关联关系: Project -> File (多对多，项目交付物)
 */
@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "RSampleProject")
@LocalizedNameDesc(name = "示例项目", description = "项目管理关系演示模型")
@BusinessKeys({
    @BusinessKeys.BusinessKey({"code", "revId"})
})
public final class RSampleProject extends ItemRevision {
    

    @QuerySqlField
    @LocalizedNameDesc(name = "项目描述", description = "项目的详细描述")
    private String description;
    
    @QuerySqlField(index = true)
    @LocalizedNameDesc(name = "项目状态", description = "项目的当前状态")
    private ProjectStatus status = ProjectStatus.PLANNING;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "开始日期", description = "项目开始日期")
    private LocalDate startDate;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "结束日期", description = "项目结束日期")
    private LocalDate endDate;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "项目经理", description = "项目负责人")
    private String projectManager;
    
    // 结构关系：项目包含任务（一对多，树形结构）
    @StructuralRelation(foreignKeyField = "projectId")
    @LocalizedNameDesc(name = "项目任务", description = "项目下的所有任务")
    private List<RSampleTask> tasks;
    
    // 关联关系：项目关联交付物文件（多对多）
    @AssociateRelation
    @LocalizedNameDesc(name = "交付物", description = "项目的交付物文件")
    private List<File> deliverables;

    public RSampleProject() {
        super(RSampleProject.class.getName());
    }

    public static RSampleProject newModel(String code, String revId) {
        RSampleProject project = new RSampleProject();
        project.setCode(code);
        project.setRevId(revId);
        return project;
    }

    /**
     * 项目状态枚举
     */
    public enum ProjectStatus {
        PLANNING("规划中"),
        IN_PROGRESS("进行中"),
        ON_HOLD("暂停"),
        COMPLETED("已完成"),
        CANCELLED("已取消");
        
        private final String displayName;
        
        ProjectStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}