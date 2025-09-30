package io.emop.integrationtest.domain;

import io.emop.model.annotation.LocalizedNameDesc;
import io.emop.model.annotation.PersistentEntity;
import io.emop.model.common.AbstractModelObject;
import io.emop.model.common.Schema;
import io.emop.model.traits.BusinessCodeTrait;
import io.emop.model.traits.HierarchicalTrait;
import io.emop.model.traits.impl.BusinessCodeTraitImpl;
import io.emop.model.traits.impl.HierarchicalTraitImpl;
import lombok.Getter;
import lombok.Setter;
import io.emop.model.annotation.QuerySqlField;
import lombok.NonNull;

import java.util.List;

/**
 * 示例部门模型，具备层级特性和业务编码特性
 */
@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "SampleDepartment")
@LocalizedNameDesc(name = "示例部门")
public class SampleDepartment extends AbstractModelObject {

    @QuerySqlField(notNull = true)
    @LocalizedNameDesc(name = "部门名称")
    private String name;

    @QuerySqlField
    @LocalizedNameDesc(name = "部门描述")
    private String description;

    private final HierarchicalTrait<SampleDepartment> hierarchical;
    private final BusinessCodeTrait<SampleDepartment> businessCode;

    // 非 final 类，只提供带 objectType 的构造
    public SampleDepartment(String objectType) {
        super(objectType);
        this.hierarchical = new HierarchicalTraitImpl<>(this);
        this.businessCode = new BusinessCodeTraitImpl<>(this);
    }

    // 推荐的创建方法
    public static SampleDepartment newModel(@NonNull String name) {
        SampleDepartment dept = new SampleDepartment(SampleDepartment.class.getName());
        dept.setName(name);
        return dept;
    }

    public String getCode() {
        return businessCode.getCode();
    }

    public String generateCode(boolean forceRegenerate) {
        return businessCode.generateCode(forceRegenerate);
    }

    public void setParent(SampleDepartment modelObject) {
        hierarchical.setParent(modelObject);
    }

    public List<SampleDepartment> queryChildren() {
        return hierarchical.queryChildren();
    }
}