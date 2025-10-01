package io.emop.example.model.hello;

import io.emop.model.annotation.LocalizedNameDesc;
import io.emop.model.annotation.PersistentEntity;
import io.emop.model.annotation.QuerySqlField;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Hello任务模型
 * 演示EMOP平台的PersistentEntity用法
 */
@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "HelloTask")
@LocalizedNameDesc(name = "Hello任务", description = "Hello示例任务模型")
public final class HelloTask extends ItemRevision {
    
    @QuerySqlField(index = true, notNull = true)
    @LocalizedNameDesc(name = "任务标题", description = "任务的标题")
    private String title;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "任务描述", description = "任务的详细描述")
    private String description;
    
    @QuerySqlField(index = true)
    @LocalizedNameDesc(name = "任务状态", description = "任务的当前状态")
    private TaskStatus status = TaskStatus.PENDING;
    
    @QuerySqlField(index = true)
    @LocalizedNameDesc(name = "优先级", description = "任务优先级")
    private Priority priority = Priority.MEDIUM;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "截止时间", description = "任务的截止时间")
    private LocalDateTime dueDate;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "完成时间", description = "任务的完成时间")
    private LocalDateTime completedAt;
    
    @QuerySqlField
    @LocalizedNameDesc(name = "负责人", description = "任务负责人")
    private String assignee;

    public HelloTask(String objectType) {
        super(objectType);
    }

    public HelloTask() {
        super(HelloTask.class.getName());
    }

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        PENDING("待处理"),
        IN_PROGRESS("进行中"),
        COMPLETED("已完成"),
        CANCELLED("已取消");
        
        private final String displayName;
        
        TaskStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * 优先级枚举
     */
    public enum Priority {
        LOW("低"),
        MEDIUM("中"),
        HIGH("高"),
        URGENT("紧急");
        
        private final String displayName;
        
        Priority(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}