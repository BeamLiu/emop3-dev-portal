package io.emop.integrationtest.domain;

import io.emop.model.annotation.PersistentEntity;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.Schema;
import lombok.NonNull;

@PersistentEntity(schema = Schema.SAMPLE, name = "SampleMaterial")
public class SampleMaterial extends ItemRevision {
    public SampleMaterial(String objectType) {
        super(objectType);
    }

    public static SampleMaterial newModel(@NonNull String code, @NonNull String revId) {
        SampleMaterial revision = new SampleMaterial(SampleMaterial.class.getName());
        revision.setCode(code);
        revision.setRevId(revId);
        return revision;
    }
}
