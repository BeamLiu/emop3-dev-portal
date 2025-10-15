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
POST /api/file/bulk-upload-zip?bucket={bucket}&basePath={path}&strategy={strategy}
Content-Type: multipart/form-data
Body: file (zip文件，可在根目录包含 __file_metadata__.json)

参数说明：
- strategy: 元数据更新策略 (CREATE_ONLY/UPDATE_ONLY/CREATE_OR_UPDATE)
- 元数据配置：在ZIP根目录放置 __file_metadata__.json 文件（可选）

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

### 5.3. 使用场景区分

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

## 6. 文件元数据配置

### 6.1 批量上传文件元数据管理

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

#### 文件元数据配置方式

**推荐方式：在 ZIP 文件中包含 `__file_metadata__.json`**

在 ZIP 文件的根目录放置 `__file_metadata__.json` 文件，系统会自动读取并应用配置。该文件不会被上传到 MinIO，仅用于配置。

**配置格式：**

```json
{
  "2604230192773361664/零件-23.0525.1.SLDPRT": {
    "fileId": 2604230192773361664,
    "additionalProperties": {
      "projectId": "456",
      "category": "part",
      "status": "active"
    }
  },
  "2604231086126559232/装配体.23.0530.2.SLDASM": {
    "fileId": 2604231086126559232,
    "additionalProperties": {
      "projectId": "456", 
      "category": "assembly"
    }
  }
}
```

**路径格式说明：**
- 支持正斜杠 `/` 和反斜杠 `\` 两种路径分隔符
- 系统会自动规范化路径，确保正确匹配
- 路径应与 ZIP 内的文件路径一致

**ZIP 文件结构示例：**
```
upload.zip
├── __file_metadata__.json          # 元数据配置文件（不会上传到MinIO）
├── 2604230192773361664/
│   ├── 零件-23.0525.1.SLDPRT      # 主文件
│   └── 零件-23.0525.1.SLDPRT.jpg  # 附加文件（缩略图）
└── 2604231086126559232/
    ├── 装配体.23.0530.2.SLDASM
    └── 装配体.23.0530.2.SLDASM.jpg
```

**配置规则：**
- 如果给定 `fileId` 则会根据 id 进行更新，否则是创建动作
- 具体行为依据全局的 `MetadataStrategy` 配置
- 附加文件（如 `.jpg`）不需要单独配置元数据，会自动关联到主文件

#### 使用示例

**示例 1：准备包含元数据的 ZIP 文件**

```java
// 1. 创建元数据配置
Map<String, Object> metadataConfig = new HashMap<>();
metadataConfig.put("2604230192773361664/零件-23.0525.1.SLDPRT", Map.of(
    "fileId", 2604230192773361664L,
    "additionalProperties", Map.of(
        "projectId", projectId.toString(),
        "category", "part",
        "status", "active"
    )
));

// 2. 将元数据写入 ZIP 文件
try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
    // 添加元数据配置文件
    ZipEntry metadataEntry = new ZipEntry("__file_metadata__.json");
    zos.putNextEntry(metadataEntry);
    String metadataJson = objectMapper.writeValueAsString(metadataConfig);
    zos.write(metadataJson.getBytes("UTF-8"));
    zos.closeEntry();
    
    // 添加实际文件
    ZipEntry fileEntry = new ZipEntry("2604230192773361664/零件-23.0525.1.SLDPRT");
    zos.putNextEntry(fileEntry);
    // ... 写入文件内容
    zos.closeEntry();
}
```

**示例 2：批量更新现有文件**

```java
@PostMapping("/projects/{projectId}/documents/bulk-update")
public ResponseEntity<ZipUploadResult> bulkUpdateDocuments(
        @PathVariable Long projectId,
        @RequestParam("file") MultipartFile zipFile) {
    
    // ZIP 文件已包含 __file_metadata__.json，直接上传
    // 使用 UPDATE_ONLY 策略
    HttpResponse<String> response = Unirest.post(minioProxyUrl + "/api/file/bulk-upload-zip")
        .queryString("bucket", "documents")
        .queryString("basePath", "projects/" + projectId)
        .queryString("strategy", "UPDATE_ONLY")
        .field("file", zipFile)
        .asString();
    
    return ResponseEntity.ok(parseResponse(response.getBody()));
}
```

**示例 3：批量创建新文件记录**

```java
@PostMapping("/projects/{projectId}/documents/bulk-create")
public ResponseEntity<ZipUploadResult> bulkCreateDocuments(
        @PathVariable Long projectId,
        @RequestParam("file") MultipartFile zipFile) {
    
    // 使用 CREATE_ONLY 策略，不需要在元数据中指定 fileId
    // 系统会自动生成新的 fileId
    HttpResponse<String> response = Unirest.post(minioProxyUrl + "/api/file/bulk-upload-zip")
        .queryString("bucket", "documents")
        .queryString("basePath", "projects/" + projectId)
        .queryString("strategy", "CREATE_ONLY")
        .field("file", zipFile)
        .asString();
    
    return ResponseEntity.ok(parseResponse(response.getBody()));
}
```

**示例 4：使用 curl 测试**

```bash
# 准备 ZIP 文件（包含 __file_metadata__.json）
curl -X POST "http://localhost:9003/minioproxy/api/file/bulk-upload-zip?bucket=cad&basePath=demo&strategy=UPDATE_ONLY" \
  -H "x-user: {\"userId\":-1,\"authorities\":[\"ADMIN\"]}" \
  -F "file=@upload.zip"
```

#### 注意事项

1. **策略选择**：
    - `CREATE_ONLY`：适用于上传全新文件，系统会自动生成 fileId
    - `UPDATE_ONLY`：适用于更新现有文件，必须在元数据中提供 fileId
    - `CREATE_OR_UPDATE`：混合场景，有 fileId 则更新，无 fileId 则创建

2. **路径匹配**：
    - 元数据配置中的路径必须与 ZIP 内的文件路径一致
    - 支持正斜杠 `/` 和反斜杠 `\` 两种格式，系统会自动规范化
    - 路径区分大小写

3. **附加文件处理**：
    - 附加文件（如 `.jpg` 缩略图）不需要单独配置元数据
    - 附加文件会自动关联到主文件，无需在 `__file_metadata__.json` 中列出

4. **元数据配置文件**：
    - `__file_metadata__.json` 必须放在 ZIP 根目录
    - 该文件不会被上传到 MinIO，仅用于配置
    - 如果 ZIP 中没有该文件，则不应用任何元数据配置

5. **错误处理**：
    - `UPDATE_ONLY` 模式下，如果 fileId 不存在会抛出异常
    - 文件上传成功但元数据更新失败时，文件仍会保留在存储中
    - 如果元数据配置中有重复的 fileId，会抛出验证异常

6. **性能优化**：
    - 系统会批量处理元数据操作，提升性能
    - 大量文件时建议使用 `UPDATE_ONLY` 或 `CREATE_ONLY` 策略，避免 `CREATE_OR_UPDATE` 的额外判断开销

## 7. 文件存储安全机制

### 7.1 路径安全设计

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

## 8. 附加文件最佳实践

### 8.1 推荐的使用模式

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

### 8.2 性能优化建议

1. **使用 InputStream** - 避免将整个文件加载到内存
2. **批量操作** - 使用批量接口减少 RPC 调用
3. **异步处理** - 附加文件生成可以异步进行
4. **按需生成** - 只在需要时生成附加文件

### 8.3 常见场景

| 场景 | 扩展名 | 说明 |
|------|--------|------|
| 图片缩略图 | jpg, png | 用于列表展示 |
| 文件校验 | md5, sha256 | 完整性验证 |
| 格式转换 | pdf, webp | 兼容性处理 |
| 预览图 | jpg, png | 快速预览 |

---
**优先使用RPC接口进行轻量级文件操作，使用REST API处理复杂的文件传输和元数据管理。附加文件上传使用 MinioProxyService + StorageService 组合，避免内存拷贝。业务应用专注于业务逻辑，将文件处理委托给专业的文件服务！**

## 9. 多站点支持（异地架构）

### 9.1 概述

minio-proxy 支持多站点架构，允许在不同地理位置部署 MinIO 集群，客户端可以访问就近的站点以提升性能。

**核心特性：**
- 支持配置多个 MinIO 站点
- 基于用户、客户端信息智能选择站点
- Bucket 自动映射（逻辑名 -> 站点特定名）
- 站点内负载均衡由 Kong 处理
- 单站点零认知负担，默认有一个 default 的站点

### 9.2 架构设计

```
                    ┌──────────────────┐
                    │  CAD Client /    │
                    │  Web Client      │
                    └────────┬─────────┘
                             │
                             │ 1. 请求站点选择
                             ▼
                    ┌──────────────────┐
                    │  minio-proxy     │
                    │  (任意站点)       │
                    │  站点选择 API    │
                    └────────┬─────────┘
                             │
                             │ 2. 返回目标站点 URL + bucket
                             ▼
                    ┌──────────────────┐
                    │  Client 使用     │
                    │  返回的信息      │
                    └────────┬─────────┘
                             │
                             │ 3. 直接访问目标站点
                             ▼
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
   ┌─────────┐         ┌─────────┐         ┌─────────┐
   │ Kong LB │         │ Kong LB │         │ Kong LB │
   │ (中心)  │         │ (异地1) │         │ (异地2) │
   └────┬────┘         └────┬────┘         └────┬────┘
        │                   │                   │
        ▼                   ▼                   ▼
   ┌─────────┐         ┌─────────┐         ┌─────────┐
   │ minio-  │         │ minio-  │         │ minio-  │
   │ proxy   │         │ proxy   │         │ proxy   │
   │ cluster │         │ cluster │         │ cluster │
   └────┬────┘         └────┬────┘         └────┬────┘
        │                   │                   │
        ▼                   ▼                   ▼
   ┌─────────┐         ┌─────────┐         ┌─────────┐
   │ MinIO   │         │ MinIO   │         │ MinIO   │
   │ Cluster │         │ Cluster │         │ Cluster │
   └─────────┘         └─────────┘         └─────────┘
```

### 9.3 站点选择 API

#### 选择站点

```java
// 1. 客户端调用站点选择 API
POST /api/site-selection/select
Content-Type: application/json

{
  "logicalBucket": "cad",
  "userAttributes": {
    "organizationId": "org-zjk-001"
  }
}

// 2. 返回站点信息
{
  "siteId": "remote-zjk",
  "siteName": "张家口站点",
  "proxyUrl": "http://minioproxy-zjk.emop.emopdata.com/minioproxy",
  "actualBucket": "cad-zjk1",
  "reason": "Rule matched",
  "matchedRule": "user-organization"
}

// 3. 客户端使用返回的信息组装 URL
String uploadUrl = result.getProxyUrl() 
    + "/api/file/direct-upload-ticket"
    + "?bucket=" + result.getActualBucket()
    + "&targetPath=models&filename=test.zip";
```

#### 其他 API

```java
// 获取站点数量（判断是否需要站点选择）
GET /api/site-selection/site-count
// 返回: 1 (单站点) 或 > 1 (多站点)

// 获取所有站点列表
GET /api/site-selection/sites

// 获取站点详情
GET /api/site-selection/sites/{siteId}
```

### 9.4 配置说明

#### 单站点配置

```yaml
# 单站点配置示例（默认配置）
# 使用原有的单站点配置, endpoint 为 kong(gateway) 的服务地址，后面有多个minio节点
minio:
   endpoint: http://storage-${EMOP_DOMAIN}:9000
   accessKey: minioadmin
   secretKey: EmopIs2Fun!
   defaultBucket: emop
EMOP_DOMAIN: dev.emop.emopdata.com
```

#### 多站点配置

```yaml
# 多站点配置示例
minio:
   multi-site:
      enabled: true
      # 站点配置（YAML 数组格式）
      sites:
         - siteId: central
           siteName: 集团中心站点
           proxyUrl: http://minioproxy-${EMOP_DOMAIN}/minioproxy
           isDefault: true
           tags:
              location: central
              region: beijing
           bucketMapping:
              cad: cad
              documents: documents
              temp: temp

         - siteId: remote-zjk
           siteName: 张家口站点
           proxyUrl: http://minioproxy-zjk-${EMOP_DOMAIN}/minioproxy
           isDefault: false
           tags:
              location: remote
              region: zhangjiakou
              site: zjk
           bucketMapping:
              cad: cad-zjk1
              documents: documents-zjk1
              temp: temp-zjk1

      # 站点选择规则（YAML 数组格式）
      selection-rules:
         - name: explicit-site
           priority: 1
           type: EXPLICIT_SITE
           description: 显式指定站点优先

         - name: user-organization
           priority: 10
           type: USER_ATTRIBUTE
           description: 张家口组织用户使用本地站点
           targetSiteId: remote-zjk
           conditions:
              attribute: organizationId
              value: org-zjk-*

         - name: client-ip-range
           priority: 20
           type: CLIENT_IP
           description: 张家口IP段使用本地站点
           targetSiteId: remote-zjk
           conditions:
              ipRange: 192.167.100.0/24

         - name: default-rule
           priority: 999
           type: DEFAULT
           description: 默认使用集团中心站点
           targetSiteId: central
   #当前 site 的minio配置，不同的 site 的 minio-proxy 应该配置不一样的
   #endpoint 为当前site的 kong(gateway) 的服务地址，后面有多个minio节点
   endpoint: http://storage-zjk-${EMOP_DOMAIN}:9000
   accessKey: minioadmin
   secretKey: EmopIs2Fun!
   defaultBucket: emop
EMOP_DOMAIN: dev.emop.emopdata.com
```

### 9.6 MinIO Bucket Replication 配置

多站点架构下，MinIO 站点间通过 Bucket Replication 进行数据同步：

```bash
# 配置 bucket 级别的同步
mc replicate add central/cad-zjk1 --remote-bucket cad-zjk1 --priority 1
mc replicate add central/documents-zjk1 --remote-bucket documents-zjk1 --priority 1
```

---

**多站点架构使用建议：**
1. 单站点场景：无需任何改动，直接使用原有方式
2. 多站点场景：客户端启动时检查站点数量，动态决定是否调用站点选择 API
4. Bucket 映射：使用逻辑 bucket 名称，由 minio-proxy 自动映射到站点特定的 bucket
5. 数据同步：配置 MinIO Site Replication 或 Bucket Replication 确保数据一致性
