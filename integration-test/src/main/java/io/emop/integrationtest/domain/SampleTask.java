package io.emop.integrationtest.domain;

import io.emop.model.annotation.AssociateRelation;
import io.emop.model.annotation.PersistentEntity;
import io.emop.model.annotation.StructuralRelation;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.Schema;
import lombok.Getter;
import lombok.Setter;
import io.emop.model.annotation.QuerySqlField;

import java.util.List;

@PersistentEntity(schema = Schema.SAMPLE, name = "SampleTask")
@Getter
@Setter
public final class SampleTask extends ItemRevision {
    @QuerySqlField
    private Long subTaskId;  // 一对一，外键在当前对象

    @QuerySqlField
    private Long parentTaskId;  // 一对一，外键在当前对象(作为目标对象)

    @QuerySqlField
    private Long groupTaskId;  // 一对多，外键在当前对象(作为子对象)

    @StructuralRelation(foreignKeyField = "subTaskId")
    private SampleTask subTask;  // 一对一，外键在源对象

    @StructuralRelation(foreignKeyField = "parentTaskId")
    private SampleTask refTask;  // 一对一，外键在目标对象

    @StructuralRelation(foreignKeyField = "groupTaskId")
    private List<SampleTask> childTasks;  // 一对多关系

    @AssociateRelation
    private SampleDocument majorSpecificationDocs;  // 一对一关系

    public SampleTask() {
        super(SampleTask.class.getName());
    }

    @Override
    public String toString() {
        return "ID: " + getId() + ", Code: " + getCode() + ", RevId: " + getRevId();
    }
}