package io.emop.integrationtest.domain;

import io.emop.model.annotation.AssociateRelation;
import io.emop.model.annotation.LocalizedNameDesc;
import io.emop.model.annotation.PersistentEntity;
import io.emop.model.annotation.QuerySqlField;
import io.emop.model.common.AbstractModelObject;
import io.emop.model.common.Schema;
import io.emop.model.traits.BusinessCodeTrait;
import io.emop.model.traits.impl.BusinessCodeTraitImpl;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "SampleDataset")
@LocalizedNameDesc(name = "文档集合", description = "一系列的相关文档的集合")
public class SampleDataset extends AbstractModelObject {

    @QuerySqlField(notNull = true)
    private String type;

    private final BusinessCodeTrait<? extends SampleDataset> businessCode;

    @AssociateRelation
    @LocalizedNameDesc(name = "文档", description = "Dataset下面的Document")
    private List<SampleDocument> content = new ArrayList<>();

    public SampleDataset(String objectType) {
        super(objectType);
        this.businessCode = new BusinessCodeTraitImpl<>(this);
    }

    public static SampleDataset newModel() {
        return new SampleDataset(SampleDataset.class.getName());
    }

    public String generateCode(boolean forceRegenerate) {
        return this.businessCode.generateCode(forceRegenerate);
    }

    public String getCode() {
        return this.businessCode.getCode();
    }

    public void setCode(@NonNull String code) {
        this.businessCode.setCode(code);
    }
}