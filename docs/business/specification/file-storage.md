# EMOP文件数据存储规范

## 1. 核心原则

### 1.1 文件处理统一原则

**🚫 禁止在业务应用中直接处理文件IO**
- 不要在业务服务中上传、下载、存储文件
- 不要在业务服务中处理文件IO操作

**✅ 统一使用`minio-proxy`服务**
- 所有文件操作必须通过`minio-proxy`服务完成
- 业务服务只处理文件ID和业务逻辑的关联
- 文件的存储、访问、安全由`minio-proxy`统一管理

### 1.2 设计理念

- **职责分离**：业务逻辑与文件存储完全分离
- **安全统一**：文件安全策略统一管控
- **性能优化**：专业的文件服务提供更好的性能
- **运维简化**：集中化的文件管理和监控

## 2. 文件存储架构

### 2.1 服务架构

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   业务服务      │    │   minio-proxy    │    │   MinIO集群     │
│                 │    │     服务         │    │                 │
│ - 业务逻辑      │◄──►│ - 文件管理       │◄──►│ - 对象存储      │
│ - 文件ID关联    │    │ - 权限控制       │    │ - 数据持久化    │
│ - 文件元数据管理  │    │ - 批量操作       │    │ - 高可用        │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### 2.2 数据流转

```
上传流程：
前端/第三方 → 获取上传票据 → minio-proxy → 直接上传到MinIO → 返回文件ID → 业务服务保存关联

下载流程：
前端 → 业务服务 → 文件ID → minio-proxy → 生成访问票据 → 前端直接访问MinIO
```

## 3. API规范

### 3.1 文件上传规范

#### 临时上传票据方式
先由客户端将文件上传至临时目录，然后业务服务从临时目录获取文件处理或移动到实际的目录下面
```java
// 1. 获取上传票据
@RestController
public class FileController {

    @PostMapping("/documents/upload-ticket")
    public ResponseEntity<UploadTicketResponse> getUploadTicket(
            @RequestParam String filename) {

        // 通过RPC调用minio-proxy获取上传票据
        MinioProxyService.FileAccessTicket ticket = S.service(MinioProxyService.class)
                .generateUploadTicket(filename, 30);

        // 会生成上传至临时文件夹的 ticket
        return ResponseEntity.ok(new UploadTicketResponse(
                ticket.getUrl(),           // 前端用此URL直接上传
                ticket.getExpiresAt(),     // 票据过期时间
                ticket.getTargetPath()     // 文件存储路径
        ));
    }
}
```

#### 直接路径上传方式
直接上传至目标目录，减少一次移动操作，但是安全性没有先到临时目录更符合规范
```java
@PostMapping("/projects/{projectId}/documents/upload-ticket")
public ResponseEntity<UploadTicketResponse> getProjectUploadTicket(
@PathVariable Long projectId,
@RequestParam String filename) {

        // 构建项目专用路径
        String targetPath = String.format("projects/%d/documents", projectId);

        // 通过RPC获取直接上传至目标路径的票据
        MinioProxyService.FileAccessTicket ticket = S.service(MinioProxyService.class)
        .generateDirectUploadTicket(
        "documents",    // bucket
        targetPath,     // 目标路径  
        filename,       // 文件名
        5               // 5分钟过期
        );

        return ResponseEntity.ok(new UploadTicketResponse(
        ticket.getUrl(),
        ticket.getExpiresAt(),
        ticket.getTargetPath()
        ));
        }
```

### 3.2 文件下载规范

#### 单文件下载

```java
@GetMapping("/documents/{documentId}/download")
public ResponseEntity<Void> downloadDocument(@PathVariable Long documentId) {
    
    // 1. 获取业务对象
    Document document = documentService.findById(documentId);
    if (document == null) {
        return ResponseEntity.notFound().build();
    }
    
    // 2. 通过RPC调用minio-proxy获取下载URL
    MinioProxyService.FileAccessTicket ticket = S.service(MinioProxyService.class)
        .generateAccessTicket(
            document.getFileId(),  // 文件ID
            60,                    // 60分钟有效期
            false                  // 外部访问地址
        );
    
    // 3. 重定向到预签名URL
    return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(ticket.getUrl()))
            .build();
}
```
## 4. 文件附加文件管理

### 4.1 附加文件概念

附加文件是指基于原文件生成的衍生文件，如缩略图、PDF版本、校验文件等。这些文件与原文件存储在同一目录下，通过文件扩展名来直接标识类型。

### 4.2 极简化命名规则

**命名规则**：`原文件路径 + .扩展名`

示例：
- 原文件：`documents/123/report.pdf`
- 缩略图：`documents/123/report.pdf.jpg`
- MD5校验：`documents/123/report.pdf.md5`

**支持的扩展名类型**：系统内置支持常见的安全扩展名，无需任何配置：
`jpg, bmp, dwg, png, gif, webapp, zip, cdxfb, md5`

如果需要拓展更多，请修改application.yml中 `emop.file.attachment.extensions`

### 4.3 设计优势

- **零配置**：无需任何配置文件，扩展名即类型
- **极简直观**：一看扩展名就知道是什么类型的附加文件
- **无限扩展**：支持任意安全扩展名，满足各种业务需求
- **自动关联**：基于文件名模式自动关联，无需数据库存储关系
- **职责分离**：MinioProxyService 负责路径构建，StorageService 负责实际存储

### 4.4 附加文件上传

#### 使用 RPC 方式上传附加文件

业务服务通过 `MinioProxyService` 构建路径，然后使用 `StorageService` 进行上传，避免 byte[] 拷贝：

```java
@Service
public class ThumbnailService {
    
    /**
     * 为文件生成并上传缩略图
     */
    public void generateAndUploadThumbnail(Long fileId, InputStream thumbnailStream) {
        // 1. 构建附加文件路径
        String attachmentPath = S.service(MinioProxyService.class)
            .buildAttachmentPath(fileId, "jpg");
        
        // 2. 使用 StorageService 上传（使用 InputStream 避免内存拷贝）
        StorageService storageService = S.service(StorageService.class);
        storageService.upload(attachmentPath, "", thumbnailStream, true);
        
        log.info("Thumbnail uploaded for file {}: {}", fileId, attachmentPath);
    }
}
```

#### 删除附加文件

```java
// 删除单个附加文件
String attachmentPath = S.service(MinioProxyService.class)
    .buildAttachmentPath(fileId, "jpg");
S.service(StorageService.class).delete(attachmentPath);

// 批量删除
Map<Long, String> paths = S.service(MinioProxyService.class)
    .batchBuildAttachmentPaths(fileIds, "jpg");
StorageService storageService = S.service(StorageService.class);
paths.values().forEach(storageService::delete);
```

## 5. API接口

### 5.1 RPC接口列表

部分接口，详细见 `MinioProxyService.java`
```java
// 单个文件访问票据
FileAccessTicket generateAccessTicket(Long fileId, Integer expiryMinutes, boolean fullInternalPath);

// 批量文件访问票据  
Map<Long, FileAccessTicket> batchGenerateAccessTickets(List<Long> fileIds, Integer expiryMinutes, boolean fullInternalPath);

// 临时上传票据
FileAccessTicket generateUploadTicket(String filename, Integer expiryMinutes);

// 直接路径上传票据
FileAccessTicket generateDirectUploadTicket(String bucket, String targetPath, String filename, Integer expiryMinutes);

// 检查文件存在性
Map<Long, Boolean> checkFilesExist(List<Long> fileIds);

// 检查附加文件存在性（按扩展名）
boolean checkAttachmentExists(Long fileId, String extension);

// 批量检查附加文件
Map<Long, Boolean> batchCheckAttachments(List<Long> fileIds, String extension);

// 构建附加文件路径（配合 StorageService 使用）
String buildAttachmentPath(Long fileId, String extension);

// 批量构建附加文件路径
Map<Long, String> batchBuildAttachmentPaths(List<Long> fileIds, String extension);
```

### 5.2 REST接口列表

部分接口，详细见swagger `http://localhost:9003/minioproxy/api`
```http
# 文件下载
GET /api/file/{fileId}/download

# 图片预览
GET /api/file/{fileId}/preview?width={width}&height={height}

# 获取上传票据
GET /api/file/upload-ticket?filename={filename}&expiryMinutes={minutes}

# 获取直接上传票据
GET /api/file/direct-upload-ticket?bucket={bucket}&targetPath={path}&filename={filename}

# 获取访问票据
GET /api/file/{fileId}/access-ticket?expiryMinutes={minutes}&fullInternalPath={boolean}

# 批量通过zip上传，会解压zip的内容然后按zip中路径存储，支持文件元数据配置
POST /api/file/bulk-upload-zip?bucket={bucket}&basePath={path}&strategy={strategy}&fileMetadataConfig={config}
Content-Type: multipart/form-data
Body: file (zip文件)

参数说明：
- strategy: 元数据更新策略 (CREATE_ONLY/UPDATE_ONLY/CREATE_OR_UPDATE)
- fileMetadataConfig: JSON格式的文件元数据配置

# 按文件ID批量下载
POST /api/file/bulk-download-by-ids-zip?zipFileName={name}
Content-Type: application/json
Body: [fileId1, fileId2, ...]

# 按路径批量下载
POST /api/file/bulk-download-zip?bucket={bucket}&basePath={path}&zipFileName={name}
Content-Type: application/json  
Body: ["file1.pdf", "dir/file2.doc", ...]

# 目录下载
GET /api/file/download-directory-zip?bucket={bucket}&basePath={path}&zipFileName={name}&includeSubdirectories={boolean}

### 附加文件操作
```http
# 获取系统支持的扩展名
GET /api/file/supported-extensions

# 获取附加文件（按扩展名）
GET /api/file/{fileId}/attachment/{extension}
# extension: jpg, png, pdf, md5, webp 等

# 检查附加文件是否存在
GET /api/file/{fileId}/attachment/{extension}/exists

# 列出文件的所有可用附加文件
GET /api/file/{fileId}/attachments

# 获取附加文件访问票据
GET /api/file/{fileId}/attachment/{extension}/access-ticket?expiryMinutes={minutes}&fullInternalPath={boolean}

# 批量检查附加文件状态
POST /api/file/batch-check-attachments?extension={extension}
Content-Type: application/json
Body: [fileId1, fileId2, ...]

# 批量生成附加文件访问票据
POST /api/file/batch-attachment-tickets?extension={extension}&expiryMinutes={minutes}
Content-Type: application/json  
Body: [fileId1, fileId2, ...]
```

## 6. 使用场景区分

| 操作类型 | 使用方式 | 原因 |
|---------|----------|------|
| 文件访问票据生成 | RPC优先 | 高频调用，轻量级，更好性能 |
| 批量票据生成 | RPC优先 | 避免循环调用 |
| 文件存在性检查 | RPC | 业务逻辑校验 |
| 附加文件路径构建 | RPC | 轻量级操作，配合 StorageService 使用 |
| 附加文件上传 | RPC (MinioProxyService + StorageService) | 避免 byte[] 拷贝，使用 InputStream |
| 文件上传 | REST | 文件流传输 |
| 文件下载 | REST | 文件流传输 |
| 批量zip操作 | REST | 复杂文件处理 |
| 批量文件元数据管理 | REST | 支持复杂的元数据配置和策略 |
| 图片预览 | REST | 图片处理和缩放 |
| 附加文件下载 | REST | 文件流传输 |

## 7. 文件元数据配置

### 7.1 批量上传文件元数据管理

`bulkUploadZip` 接口支持文件元数据配置，允许在上传时对文件记录进行批量创建或更新操作。

#### 元数据更新策略

```java
// 策略枚举
public enum MetadataStrategy {
    CREATE_ONLY,      // 仅创建新文件记录
    UPDATE_ONLY,      // 仅更新现有文件记录 
    CREATE_OR_UPDATE  // 根据fileId自动选择创建或更新
}
```

#### 文件元数据配置格式

如果给定`fileId`则会根据id进行更新，否则是创建动作，当然要依据全局的`MetadataStrategy`配置
```json
{
  "file1.pdf": {
    "fileId": 123,
    "additionalProperties": {
      "projectId": "456",
      "category": "report",
      "status": "draft"
    }
  },
  "file2.docx": {
    "additionalProperties": {
      "projectId": "456", 
      "category": "document"
    }
  }
}
```

#### 使用示例

```java
// 1. 批量更新现有文件
@PostMapping("/projects/{projectId}/documents/bulk-update")
public ResponseEntity<ZipUploadResult> bulkUpdateDocuments(
        @PathVariable Long projectId,
        @RequestParam("file") MultipartFile zipFile) {
    
    // 构建元数据配置
    Map<String, Object> config = Map.of(
        "report.pdf", Map.of(
            "fileId", 123L,
            "additionalProperties", Map.of(
                "projectId", projectId.toString(),
                "status", "updated"
            )
        )
    );
    
    String configJson = objectMapper.writeValueAsString(config);
    
    // 调用批量上传，使用UPDATE_ONLY策略
    return minioProxyClient.bulkUploadZip(
        zipFile, 
        "documents", 
        "projects/" + projectId,
        MetadataStrategy.UPDATE_ONLY,
        configJson
    );
}

// 2. 批量创建新文件记录
@PostMapping("/projects/{projectId}/documents/bulk-create")
public ResponseEntity<ZipUploadResult> bulkCreateDocuments(
        @PathVariable Long projectId,
        @RequestParam("file") MultipartFile zipFile) {
    
    // 使用CREATE_ONLY策略，不需要fileId
    Map<String, Object> config = Map.of(
        "newfile.pdf", Map.of(
            "additionalProperties", Map.of(
                "projectId", projectId.toString(),
                "category", "new_document"
            )
        )
    );
    
    return minioProxyClient.bulkUploadZip(
        zipFile,
        "documents", 
        "projects/" + projectId,
        MetadataStrategy.CREATE_ONLY,
        objectMapper.writeValueAsString(config)
    );
}
```

#### 注意事项

1. **策略选择**：
    - `CREATE_ONLY`：适用于上传全新文件，系统会自动生成fileId
    - `UPDATE_ONLY`：适用于更新现有文件，必须提供fileId
    - `CREATE_OR_UPDATE`：混合场景，有fileId则更新，无fileId则创建

2. **错误处理**：
    - UPDATE_ONLY模式下，如果fileId不存在会抛出异常
    - 文件上传成功但元数据更新失败时，文件仍会保留在存储中

## 8. 文件存储安全机制

### 8.1 路径安全设计

:::warning 🔔提醒
建议：文件存储路径包含随机字符，可选的包括：
- `fileId`, 即`io.emop.model.document.File.id`
- `任意业务id`, 如`io.emop.model.common.ItemRevision.id`
- `UUID`, 可以任意语言生成的 UUID 字符串

EMOP平台生成的业务对象的`id`具有唯一性和不可猜测性，平台通过这些机制对文件本身提供进一步的安全保障
:::

```java
import java.util.UUID;

// ✅ 正确的路径格式
String correctPath = String.format("documents/%d/project-report.pdf", fileId);
// 示例：documents/1234567890/project-report.pdf

String correctPath = String.format("documents/%s/project-report.pdf", UUID.randomUUID());
// 示例：documents/xxxx-xxxx-xxx-xxx/project-report.pdf

// ❌ 错误的路径格式 - 缺少随机性路径
String wrongPath = "documents/project-report.pdf";
```

## 9. 最佳实践总结

### 9.1 设计原则

1. **单一职责**：业务服务专注业务逻辑，文件服务专注文件处理
2. **松耦合**：通过文件ID建立松耦合的关联关系
3. **安全性**：文件路径带上业务对象的id，例如`File`，由于id是全局唯一，能一定程度阻止非安全形式暴力尝试路径访问
4. **性能优先**：利用预签名URL实现高性能的直接访问

---

## 10. 附加文件最佳实践

### 10.1 推荐的使用模式

**服务端生成附加文件**（推荐）：
```java
// 1. 使用 MinioProxyService 构建路径
String attachmentPath = S.service(MinioProxyService.class)
    .buildAttachmentPath(fileId, "jpg");

// 2. 使用 StorageService 上传（InputStream 避免内存拷贝）
S.service(StorageService.class)
    .upload(attachmentPath, "", thumbnailInputStream, true);
```

**批量操作**：
```java
// 批量构建路径
Map<Long, String> paths = S.service(MinioProxyService.class)
    .batchBuildAttachmentPaths(fileIds, "jpg");

// 批量上传
paths.forEach((fileId, path) -> {
    S.service(StorageService.class)
        .upload(path, "", generateThumbnail(fileId), true);
});
```

### 10.2 性能优化建议

1. **使用 InputStream** - 避免将整个文件加载到内存
2. **批量操作** - 使用批量接口减少 RPC 调用
3. **异步处理** - 附加文件生成可以异步进行
4. **按需生成** - 只在需要时生成附加文件

### 10.3 常见场景

| 场景 | 扩展名 | 说明 |
|------|--------|------|
| 图片缩略图 | jpg, png | 用于列表展示 |
| 文件校验 | md5, sha256 | 完整性验证 |
| 格式转换 | pdf, webp | 兼容性处理 |
| 预览图 | jpg, png | 快速预览 |

---
**优先使用RPC接口进行轻量级文件操作，使用REST API处理复杂的文件传输和元数据管理。附加文件上传使用 MinioProxyService + StorageService 组合，避免内存拷贝。业务应用专注于业务逻辑，将文件处理委托给专业的文件服务！**