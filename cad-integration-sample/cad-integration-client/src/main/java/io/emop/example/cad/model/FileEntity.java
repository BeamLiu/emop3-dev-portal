package io.emop.example.cad.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 文件实体（对应服务器端的FileEntity）
 * 注意：服务器端的fileId在这里映射为id
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileEntity {
    private Long id;  // 服务器端的主键，对应File对象的id
    private String name;
    private String path;
    private String checksum;
    private Long revisionId;  // 对应的ItemRevision的uid
}
