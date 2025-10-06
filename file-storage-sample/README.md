# EMOP 文件存储演示项目

演示EMOP平台文件存储系统的完整使用方法，包括基础上传下载、批量操作、附件管理三个核心场景。

## 相关文档

建议先阅读以下文档了解文件存储系统的架构和设计：
- [EMOP文件存储规范](../docs/platform/file-storage.md)
- [minio-proxy服务文档](../../extensions/minio-proxy/README.md)

## 功能特性

### 1. 基础上传下载 (BasicUploadDownloadDemo)
- 临时上传票据和直接上传票据
- 真实文件上传和File对象创建
- 文件访问票据和下载

### 2. 批量操作 (BatchOperationsDemo)
- 批量ZIP上传并自动解压
- 文件上传的同时更新元数据，CREATE_ONLY和UPDATE_ONLY策略
- 批量下载和目录下载
- 元数据验证

### 3. 附件文件管理 (AttachmentFileDemo)
- 针对已有文件上传附加文件
- 批量上传附加文件
- 附加文件存在性检查和访问票据
- 列出文件的所有附加文件

### 4. 异地卷支持 (RemoteSiteDemo)
- 基于用户属性的站点选择
- 显式指定站点
- 使用选择的站点上传文件

## 快速开始

### 1. 确认环境
- 确保EMOP平台已部署并运行，本地只有客户端服务
- 保证以下服务可用, 并设置本地的`hosts`解析
```
# 注册中心(注册到远程EMOP平台所在的注册中心)
192.168.10.103 registry-dev.emop.emopdata.com
# 远程MINIO服务可用及域名映射
192.168.10.103 storage-dev.emop.emopdata.com
# 远程MINIO-PROXY服务可用及域名映射
192.168.10.103 minioproxy-dev.emop.emopdata.com
# 远程EMOP网关及域名映射
192.168.10.103 dev.emop.emopdata.com
```
- 确认 maven 已经使用了阿里云私仓
- JDK 17+

### 2. 构建项目
```bash
mvn clean compile
```

### 3. 运行演示
```bash
# 运行所有演示场景
mvn exec:java

# 运行单个场景
mvn exec:java -Dexec.args="basic"      # 基础上传下载
mvn exec:java -Dexec.args="batch"      # 批量操作
mvn exec:java -Dexec.args="attachment" # 附件管理
mvn exec:java -Dexec.args="multiSite"     # 异地卷支持
```

## 项目结构

```
file-storage-sample/
├── src/main/java/io/emop/example/filestorage/
│   ├── FileStorageClientDemo.java           # 主入口
│   └── usecase/
│       ├── BasicUploadDownloadDemo.java     # 场景1：基础上传下载
│       ├── BatchOperationsDemo.java         # 场景2：批量操作
│       ├── AttachmentFileDemo.java          # 场景3：附件管理
│       └── MultiSiteDemo.java              # 场景4：异地卷支持
└── README.md
```

## 核心API

### REST API（minio-proxy服务）
```
基础操作：
GET  /file/upload-ticket              获取临时上传票据
GET  /file/direct-upload-ticket       获取直接上传票据
GET  /file/{fileId}/access-ticket     获取文件访问票据

批量操作：
POST /file/bulk-upload-zip            批量上传ZIP并解压
POST /file/bulk-download-by-ids-zip   按ID批量下载
POST /file/bulk-download-zip          按路径批量下载
GET  /file/download-directory-zip     下载目录

附件管理：
GET  /file/supported-extensions                        支持的附件类型
GET  /file/{fileId}/attachment/{extension}/exists      检查附件存在
GET  /file/{fileId}/attachment/{extension}/access-ticket  附件访问票据
GET  /file/{fileId}/attachments                        列出所有附件
```

## 核心概念

### 票据机制
文件操作通过预签名URL（票据）进行，票据有过期时间，需及时使用。

### REST vs RPC
| 调用方式 | 适用场景 | 特点 |
|---------|---------|------|
| REST API | 外部系统、前端 | 标准HTTP接口，易于集成 |
| RPC服务 | 内部业务服务 | 性能更好，避免HTTP开销 |

### 附加文件
附加文件（如缩略图.jpg、校验文件.md5）与主文件关联，通过扩展名区分。

### 异地卷支持
多站点架构下，客户端通过站点选择API获取最优的minio-proxy URL和bucket映射，实现就近访问。

**核心要点**：
- 无论单站点还是多站点，代码逻辑一致，只是配置不同
- 使用逻辑bucket名称（如"cad"），minio-proxy自动映射到实际bucket（如"cad-zjk1"）
- 可选：在应用层缓存站点选择结果以提升性能

**使用示例**：
```java
// 1. 调用站点选择API
POST /api/site-selection/select
Body: {
  "logicalBucket": "cad",
  "userAttributes": {
    "organizationId": "org-zjk-001"
  }
}

// 2. 使用返回的proxyUrl和actualBucket进行文件操作
String uploadTicketUrl = result.getProxyUrl() + "/file/direct-upload-ticket"
    + "?bucket=" + result.getActualBucket()
    + "&targetPath=models&filename=test.zip";
```
