package io.emop.example.cad.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * BOM实体模型（对应服务器端的ItemEntity）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemEntity {
    private Long id;  // revision的id
    private String revId;  // 版本
    private String name;
    private String itemCode;
    private Boolean root;
    private String subType;
    private Map<String, Object> props;
    
    // 文件相关
    private FileEntity modelFile;
    private List<FileEntity> stepFiles;
    private List<FileEntity> jtFiles;
    private List<FileEntity> drwFiles;
    private List<FileEntity> dwgFiles;
    private List<FileEntity> pdfFiles;
    private List<FileEntity> extraFiles;
    
    // BOM结构
    private List<ChildReference> children;
    
    // 状态相关
    private String useType;
    private String releaseStatus;
    private String owner;
    private Boolean readable;
    private Boolean writable;
    private Boolean copyable;
    private Boolean checkedOut;
    private String checkedOutUserName;
    private Long checkedOutUserId;
}
