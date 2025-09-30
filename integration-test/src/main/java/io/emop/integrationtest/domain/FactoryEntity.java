package io.emop.integrationtest.domain;

import io.emop.model.annotation.PersistentEntity;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.Schema;
import lombok.Data;
import io.emop.model.annotation.QuerySqlField;

/**
 * 使用父类的table来存储, 但是不修改 ItemRevision 表
 */
@Data
@PersistentEntity(schema = Schema.COMMON, name = "ItemRevision")
public final class FactoryEntity extends ItemRevision {

    @QuerySqlField(mapToDBColumn = false)
    private String factoryLocation;

    @QuerySqlField(mapToDBColumn = false)
    private Long managerUserId;

    public FactoryEntity() {
        super(FactoryEntity.class.getName());
    }
}
