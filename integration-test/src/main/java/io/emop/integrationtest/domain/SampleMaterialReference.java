package io.emop.integrationtest.domain;

import io.emop.model.annotation.GraphNode;
import io.emop.model.annotation.LocalizedNameDesc;
import io.emop.model.annotation.PersistentEntity;
import io.emop.model.common.AbstractModelObject;
import io.emop.model.common.RevisionRule;
import io.emop.model.common.Revisionable;
import io.emop.model.common.Schema;
import io.emop.model.traits.RevisionableRefTrait;
import io.emop.model.traits.impl.RevisionableRefTraitImpl;
import lombok.Getter;
import lombok.Setter;
import io.emop.model.annotation.QuerySqlField;

/**
 * 示例材料引用类，用于测试RevisionableRefTrait
 */
@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "SampleMaterialReference")
@LocalizedNameDesc(name = "材料引用")
@GraphNode(properties = {"quantity"})
public final class SampleMaterialReference extends AbstractModelObject {

    @QuerySqlField
    private Integer quantity;

    @QuerySqlField
    private String remark;

    private final RevisionableRefTrait<SampleMaterialReference> reference;

    public SampleMaterialReference() {
        super(SampleMaterialReference.class.getName());
        this.reference = new RevisionableRefTraitImpl<>(this);
    }

    /**
     * 创建新的材料引用实例
     */
    public static SampleMaterialReference newModel() {
        return new SampleMaterialReference();
    }

    /**
     * 获取目标对象类型
     */
    public String getTargetObjectType() {
        return reference.getTargetObjectType();
    }

    /**
     * 获取目标对象编码
     */
    public String getTargetItemCode() {
        return reference.getTargetItemCode();
    }

    /**
     * 获取目标对象版本号
     */
    public String getTargetRevId() {
        return reference.getTargetRevId();
    }

    /**
     * 设置引用的目标对象
     */
    public void setTarget(Revisionable target) {
        reference.setTarget(target);
    }

    /**
     * 解析目标对象
     */
    public Revisionable resolveTarget(RevisionRule rule) {
        return reference.resolveTarget(rule);
    }
}