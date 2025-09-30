package io.emop.integrationtest.domain;

import io.emop.model.annotation.LocalizedNameDesc;
import io.emop.model.annotation.PersistentEntity;
import io.emop.model.common.AbstractModelObject;
import io.emop.model.common.Schema;
import io.emop.model.traits.BusinessCodeTrait;
import io.emop.model.traits.impl.BusinessCodeTraitImpl;
import lombok.Getter;
import lombok.Setter;
import io.emop.model.annotation.QuerySqlField;

/**
 * 具体的一个文件
 */
@Getter
@Setter
@PersistentEntity(schema = Schema.SAMPLE, name = "SampleDocument")
@LocalizedNameDesc(name = "文档", description = "单个文件的定义")
public class SampleDocument extends AbstractModelObject {

    @QuerySqlField(notNull = true)
    private Long fileSize;

    @QuerySqlField(notNull = true)
    @LocalizedNameDesc(name = "文件类型")
    private String fileType;

    @QuerySqlField(notNull = true)
    @LocalizedNameDesc(name = "存储路径", description = "在minio中的存储路径")
    private String path;

    @QuerySqlField(notNull = true)
    private String checksum;

    @QuerySqlField(mapToDBColumn = false)
    private Double cost;

    @QuerySqlField(mapToDBColumn = false)
    private Double price;

    @QuerySqlField(mapToDBColumn = false)
    private String techParameter;

    @QuerySqlField(mapToDBColumn = false)
    private String specification;

    private final BusinessCodeTrait<? extends SampleDocument> businessCode;

    public SampleDocument(String objectType) {
        super(objectType);
        this.businessCode = new BusinessCodeTraitImpl<>(this);
    }

    @Override
    public String get_icon() {
        return fileType == null ? null : ("type_file_" + fileType.toLowerCase());
    }

    //访问该文件的URL，暂时hardcode minio 地址
    public String getFileUrl() {
        return this.path;
    }

    public static SampleDocument newModel() {
        return new SampleDocument(SampleDocument.class.getName());
    }

    public String getCode() {
        return businessCode.getCode();
    }
}
