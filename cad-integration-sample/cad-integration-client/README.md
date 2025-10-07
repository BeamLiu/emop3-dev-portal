# CAD Integration Client Sample - CAD集成客户端示例

这是一个CAD集成客户端示例项目，演示如何通过REST API与EMOP平台进行CAD数据交互，包括保存到EMOP和从EMOP打开两个核心场景。

## 项目概述

本项目模拟CAD客户端（如Creo、SolidWorks等）与EMOP平台的集成，展示了完整的数据流转过程：
- **保存到EMOP**：将CAD模型和BOM结构上传到EMOP系统
- **从EMOP打开**：从EMOP系统下载CAD模型和BOM结构

## 核心特性

- ✅ 动态ID适配：自动处理服务器端动态生成的ID
- ✅ ZIP文件重组：根据服务器返回的fileId自动重组文件结构
- ✅ 多站点支持：支持异地卷架构，自动选择最优站点
- ✅ 批量文件操作：使用bulk-upload-zip接口高效上传
- ✅ 元数据关联：通过fileMetadataConfig关联File对象
- ✅ 完整的错误处理和日志记录

## 项目结构

```
cad-integration-client/
├── src/main/java/io/emop/example/cad/
│   ├── CadIntegrationClientDemo.java          # 主入口
│   ├── model/                                  # 数据模型
│   │   ├── ItemEntity.java                    # BOM实体
│   │   ├── ModelFile.java                     # 模型文件信息
│   │   ├── ChildReference.java                # 子组件引用
│   │   └── SiteSelectionResult.java           # 站点选择结果
│   ├── service/                                # 核心服务
│   │   ├── CadApiService.java                 # CAD API调用服务
│   │   ├── FileStorageService.java            # 文件存储服务
│   │   ├── SiteSelectionService.java          # 站点选择服务
│   │   ├── ZipReorganizer.java                # ZIP重组服务
│   │   └── IdMappingService.java              # ID映射管理服务
│   └── scenario/                               # 场景实现
│       ├── SaveToEmopScenario.java            # 保存到EMOP场景
│       └── OpenFromEmopScenario.java          # 从EMOP打开场景
├── test-data/                                  # 测试数据（录制数据）
│   ├── 1_compare_request.json                 # Compare请求数据
│   ├── 2_post_to_bom.json                     # Post BOM数据
│   ├── 3_1_creo-upload17453228522993597702.zip # CAD文件ZIP包
│   └── 3_2_filemetadata.json                  # 文件元数据映射
├── pom.xml
└── README.md
```

## 快速开始

### 1. 环境准备

确保以下服务可用，并设置本地hosts解析：

```bash
# 注册中心
192.168.10.103 registry-dev.emop.emopdata.com

# CAD Integration服务
192.168.10.103 dev.emop.emopdata.com

# MinIO Proxy服务
192.168.10.103 minioproxy-dev.emop.emopdata.com

# MinIO存储服务
192.168.10.103 storage-dev.emop.emopdata.com
```

### 2. 启动cad-integration-server

在运行客户端之前，需要先启动cad-integration-server：

```bash
cd ../cad-integration-server
mvn spring-boot:run
```

服务启动后访问：http://localhost:870/webconsole/api

### 3. 构建项目

```bash
cd cad-integration-client
mvn clean compile
```

### 4. 运行演示

```bash
# 运行所有场景
mvn exec:java

# 运行单个场景
mvn exec:java -Dexec.args="save"    # 保存到EMOP场景
mvn exec:java -Dexec.args="open"    # 从EMOP打开场景
```

## 核心场景详解

### 场景1：保存到EMOP（Save to EMOP）

模拟CAD客户端将模型保存到EMOP系统的完整流程。

#### 流程步骤

```
1. [可选] 站点选择
   └─> POST /api/site-selection/select
       - 根据用户属性（如CAD类型）选择最优站点
       - 获取proxyUrl和actualBucket

2. BOM数据比对
   └─> POST /api/cad-integration/item/compare
       - 发送录制的BOM数据（1_compare_request.json）
       - 服务器返回带临时ID的数据

3. 提交BOM结构
   └─> POST /api/cad-integration/item
       - 提交BOM数据
       - 创建Item、Component和File对象
       - 返回最终的fileId（重要：使用此fileId组织文件）

4. ZIP文件重组
   - 从原始ZIP（3_1_creo-upload.zip）读取文件
   - 按Post返回的最终fileId重组目录结构
   - 生成fileMetadataConfig JSON

5. 批量上传CAD文件
   └─> POST /file/bulk-upload-zip
       - 上传重组后的ZIP文件
       - 使用UPDATE_ONLY策略更新File对象
       - 传递fileMetadataConfig关联元数据
```

#### 关键技术点

**A. ID映射提取（重要：使用Post返回的fileId）**
```java
// 先提交BOM结构
PostItemEntityResponse postResponse = cadApiService.postItemEntity(comparedEntities);

// 从Post响应中提取最终的fileId映射
Map<String, Long> fileIdMapping = new HashMap<>();
for (ItemEntity entity : postResponse.getItemEntities()) {
    String filename = entity.getModelFile().getName();
    Long fileId = entity.getModelFile().getFileId();
    fileIdMapping.put(filename, fileId);
}
```

**B. ZIP重组**
```java
// 原始结构: -7206152754037014364/prt0122_1.prt.20
// 新结构:   <newFileId>/prt0122_1.prt.20

Map<String, byte[]> originalFiles = readZip(originalZipFile);
for (Entry<String, byte[]> entry : originalFiles.entrySet()) {
    String filename = extractFilename(entry.getKey());
    Long newFileId = fileIdMapping.get(filename);
    String newPath = newFileId + "/" + filename;
    addToNewZip(newPath, entry.getValue());
}
```

**C. FileMetadataConfig生成**
```java
// 格式: { "fileId/filename": { "fileId": fileId } }
Map<String, FileMetadataConfig> config = new HashMap<>();
for (Entry<String, Long> entry : fileIdMapping.entrySet()) {
    String path = entry.getValue() + "/" + entry.getKey();
    config.put(path, new FileMetadataConfig(entry.getValue()));
}
```

#### 数据流示例

**输入数据（1_compare_request.json）**
```json
[
  {
    "root": true,
    "name": "asm0016_asm_1.asm.85",
    "itemCode": "CAD-2507-000001",
    "modelFile": {
      "name": "asm0016_asm_1.asm.85",
      "checksum": "b77cb6428f425c2f934a7ab3946fe670"
    }
  }
]
```

**Compare响应（带ID）**
```json
[
  {
    "itemId": 12345,
    "componentId": 67890,
    "name": "asm0016_asm_1.asm.85",
    "modelFile": {
      "fileId": -7206152754037014528,
      "name": "asm0016_asm_1.asm.85"
    }
  }
]
```

**FileMetadataConfig**
```json
{
  "-7206152754037014528/asm0016_asm_1.asm.85": {
    "fileId": -7206152754037014528
  }
}
```

### 场景2：从EMOP打开（Open from EMOP）

模拟CAD客户端从EMOP系统打开模型的完整流程。

#### 流程步骤

```
1. [可选] 站点选择
   └─> POST /api/site-selection/select
       - 选择最优站点

2. 获取BOM结构
   └─> GET /api/cad-integration/item/{componentId}
       - 获取完整的BOM树结构
       - 包含所有子组件和文件信息

3. 提取文件ID列表
   - 遍历BOM树
   - 收集所有modelFile、drwFile、pdfFile的fileId

4. 批量下载CAD文件
   └─> POST /file/bulk-download-by-ids-zip
       - 传递fileId列表
       - 获取ZIP格式的文件包

5. 解压到本地
   - 解压ZIP文件到工作目录
   - 按原始目录结构组织文件
   - 准备供CAD软件打开
```

#### 关键技术点

**A. BOM树遍历**
```java
List<Long> fileIds = new ArrayList<>();
void collectFileIds(ItemEntity entity) {
    if (entity.getModelFile() != null) {
        fileIds.add(entity.getModelFile().getFileId());
    }
    for (ChildReference child : entity.getChildren()) {
        collectFileIds(findEntityByFilename(child.getFilename()));
    }
}
```

**B. 批量下载**
```java
// 一次性下载所有文件
byte[] zipContent = fileStorageService.bulkDownloadByIds(
    fileIds, 
    "cad-model-download.zip"
);
```

**C. 本地解压**
```java
// 解压到工作目录
Path workDir = Paths.get("./workspace/cad-models");
unzipToDirectory(zipContent, workDir);
```

## 多站点支持（MultiSite）

### 站点选择策略

系统支持基于用户属性的自动站点选择：

```java
// 1. 基于用户属性选择
Map<String, Object> context = new HashMap<>();
context.put("logicalBucket", "cad");
context.put("userAttributes", Map.of("cadType", "CREO"));

SiteSelectionResult site = siteSelectionService.selectSite(context);
// 返回: { siteId, proxyUrl, actualBucket }

// 2. 显式指定站点
context.put("explicitSiteId", "default");
SiteSelectionResult site = siteSelectionService.selectSite(context);
```

### 使用选择的站点

```java
// 使用返回的proxyUrl和actualBucket
String uploadUrl = site.getProxyUrl() + "/file/bulk-upload-zip";
fileStorageService.bulkUploadZip(
    uploadUrl,
    site.getActualBucket(),
    "models",
    zipFile,
    fileMetadataConfig
);
```

### 站点选择配置

站点选择规则在minio-proxy服务中配置，支持：
- 基于用户属性的规则匹配
- 基于组织的站点分配
- 默认站点回退策略

## 测试数据说明

### 1_compare_request.json
- 用途：Compare接口的请求数据
- 内容：完整的BOM结构，不含ID
- 特点：包含checksum用于文件校验

### 2_post_to_bom.json
- 用途：Post接口的请求数据参考
- 内容：带有完整属性的BOM数据
- 特点：包含children关系和transform信息

### 3_1_creo-upload17453228522993597702.zip
- 用途：CAD模型文件包
- 结构：按fileId组织的目录结构
- 内容：.prt、.asm文件及其.jpg预览图

### 3_2_filemetadata.json
- 用途：文件ID映射表
- 格式：`"fileId/filename": { "fileId": fileId }`
- 作用：用于生成fileMetadataConfig

## API接口说明

### CAD Integration API

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/cad-integration/config | GET | 获取CAD配置 |
| /api/cad-integration/item/compare | POST | 比对BOM数据 |
| /api/cad-integration/item | POST | 提交BOM结构 |
| /api/cad-integration/item/{id} | GET | 获取BOM结构 |

### File Storage API

| 接口 | 方法 | 说明 |
|------|------|------|
| /file/bulk-upload-zip | POST | 批量上传ZIP |
| /file/bulk-download-by-ids-zip | POST | 按ID批量下载 |
| /file/direct-upload-ticket | GET | 获取上传票据 |
| /file/{fileId}/access-ticket | GET | 获取访问票据 |

### Site Selection API

| 接口 | 方法 | 说明 |
|------|------|------|
| /api/site-selection/select | POST | 选择站点 |

## 常见问题

### Q1: 为什么需要ZIP重组？
A: 服务器端的fileId是动态生成的，每次运行都不同。ZIP文件需要按新的fileId组织目录结构，才能正确关联到File对象。

### Q2: fileMetadataConfig的作用是什么？
A: 它告诉bulk-upload-zip接口每个文件对应的fileId，使用UPDATE_ONLY策略时会更新对应的File对象记录。

### Q3: 如何处理大文件上传？
A: bulk-upload-zip接口支持流式处理，不会将整个ZIP加载到内存。建议单个ZIP不超过2GB。

### Q4: 多站点环境下如何选择站点？
A: 调用site-selection API，系统会根据用户属性（如组织、CAD类型）自动选择最优站点。也可以显式指定站点ID。

### Q5: Compare接口的作用是什么？
A: Compare接口用于比对客户端和服务器端的BOM差异，返回带ID的数据，客户端据此更新本地数据。

## 扩展开发

### 添加新的CAD类型支持

1. 在请求头中设置CAD类型：
```java
.header("x-cad-type", "SOLIDWORKS")
```

2. 配置CAD类型特定的属性映射（在cad-integration-server中）

### 自定义文件过滤

在ZipReorganizer中添加文件过滤逻辑：
```java
if (shouldIncludeFile(filename)) {
    addToNewZip(newPath, content);
}
```

### 添加进度回调

在文件上传/下载时添加进度监听：
```java
fileStorageService.bulkUploadZip(
    zipFile, 
    (bytesTransferred, totalBytes) -> {
        int progress = (int) (bytesTransferred * 100 / totalBytes);
        System.out.println("Progress: " + progress + "%");
    }
);
```

## 相关文档

- [CAD Integration API文档](../../applications/plm/cad-integration/README.md)
- [File Storage文档](../file-storage-sample/README.md)
- [MinIO Proxy服务文档](../../extensions/minio-proxy/README.md)
- [EMOP平台开发指南](https://docs.emop.io)

## 注意事项

1. **数据一致性**：确保CAD文件的checksum与BOM数据中的checksum一致
2. **ID映射**：Compare响应中的ID必须正确映射到文件上传
3. **站点选择**：多站点环境下务必先调用站点选择API
4. **错误处理**：网络异常、文件损坏等情况需要妥善处理
5. **日志记录**：关键步骤需要记录详细日志便于排查问题

## 许可证

本示例项目仅供学习和参考使用。
