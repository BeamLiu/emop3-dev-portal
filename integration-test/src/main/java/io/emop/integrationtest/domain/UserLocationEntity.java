package io.emop.integrationtest.domain;

import io.emop.model.annotation.PersistentEntity;
import io.emop.model.common.Schema;
import io.emop.model.common.ItemRevision;
import lombok.Data;
import io.emop.model.annotation.QuerySqlField;

/**
 * 由于没有PersistentEntity的类，因此使用父类的table来存储
 */
@Data
@PersistentEntity(schema = Schema.SAMPLE, name = "UserLocationEntity")
public class UserLocationEntity extends ItemRevision {
    @QuerySqlField
    private String city;

    @QuerySqlField
    private String country;

    @QuerySqlField
    private Long userId;


    public UserLocationEntity(String objectType) {
        super(objectType);
    }

    public UserLocationEntity() {
        super(UserLocationEntity.class.getName());
    }

}
