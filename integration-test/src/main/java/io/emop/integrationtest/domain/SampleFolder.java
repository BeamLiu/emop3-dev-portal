package io.emop.integrationtest.domain;

import io.emop.model.annotation.AssociateRelation;
import io.emop.model.annotation.LocalizedNameDesc;
import io.emop.model.annotation.PersistentEntity;
import io.emop.model.common.AbstractModelObject;
import io.emop.model.common.ModelObject;
import io.emop.model.common.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "SampleFolder")
@LocalizedNameDesc(name = "文件夹")
public class SampleFolder extends AbstractModelObject implements ModelObject {
    @AssociateRelation
    @LocalizedNameDesc(name = "内容", description = "文件夹下面的内容")
    private List<ModelObject> content = new ArrayList<>();

    public SampleFolder(String name) {
        super(SampleFolder.class.getName());
        setName(name);
    }

    public static SampleFolder newModel() {
        return new SampleFolder(SampleFolder.class.getName());
    }
}
