package io.emop.example.filestorage.usecase;

import io.emop.model.common.UserContext;
import io.emop.model.document.File;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.storage.StorageService;
import io.emop.service.config.EMOPConfig;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 基础文件上传下载演示
 * 演示基本的文件上传和下载操作，包括：
 * 1. 获取临时上传票据
 * 2. 获取直接上传票据
 * 3. 获取文件下载访问票据
 * 4. 检查文件存在性
 */
@Slf4j
public class BasicUploadDownloadDemo {

    private static final String MINIO_PROXY_BASE_URL = "http://" +
            EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + ":9003/minioproxy/api";

    public BasicUploadDownloadDemo() {
        // 配置 Unirest
        Unirest.config()
                .connectTimeout(30000)
                .socketTimeout(60000);
    }

    /**
     * 运行基础上传下载演示
     */
    public void runDemo() {
        log.info("\n=== 基础文件上传下载演示 ===");
        try {
            // 1. 演示真实文件上传和File对象创建
            Long uploadedFileId = demonstrateRealFileUpload();

            // 2. 演示文件下载
            demonstrateFileDownload(uploadedFileId);
        } catch (Exception e) {
            throw new RuntimeException("基础文件上传下载场景失败", e);
        }
    }

    /**
     * 演示获取上传票据
     * 包括临时上传票据和直接上传票据
     * <p>
     * 临时上传票据：文件先上传到临时区域，后续可通过API移动到正式目录
     * 直接上传票据：文件直接上传到指定的bucket和路径
     */
    public void demonstrateUploadTickets() throws Exception {
        log.info("\n--- 上传票据演示 ---");

        // 1. 获取临时上传票据
        log.info("1. 获取临时上传票据");
        String tempUploadTicket = getUploadTicket("sample-document.pdf", 30);
        log.info("临时上传票据: {}", tempUploadTicket);

        // 2. 获取直接上传票据到不同bucket
        log.info("2. 获取直接上传票据");

        // CAD文件上传
        String cadUploadTicket = getDirectUploadTicket("cad", "products/12345", "goc04zhijia.prt", 15);
        log.info("CAD文件上传票据: {}", cadUploadTicket);

        // 文档上传
        String docUploadTicket = getDirectUploadTicket("document", "products/12345", "specification.pdf", 15);
        log.info("文档上传票据: {}", docUploadTicket);
    }

    /**
     * 演示真实文件上传和File对象创建
     *
     * @return 返回最后一个上传文件的ID用于下载测试
     */
    public Long demonstrateRealFileUpload() throws Exception {
        log.info("\n--- 真实文件上传演示 ---");

        // 1. 上传CAD文件
        log.info("1. 上传CAD文件");
        File cadFile = uploadFileAndCreateRecord("cad", "products/12345", "goc04zhijia.prt",
                "test-files/goc04zhijia.prt", "PRT");
        log.info("CAD文件上传成功，File ID: {}", cadFile.getId());

        // 2. 上传文档文件
        log.info("2. 上传文档文件");
        File docFile = uploadFileAndCreateRecord("document", "products/12345", "specification.pdf",
                "test-files/specification.pdf", "PDF");
        log.info("文档文件上传成功，File ID: {}", docFile.getId());

        // 3. 上传临时文件
        log.info("3. 上传临时文件");
        File tempFile = uploadTempFileAndCreateRecord("sample-document.pdf",
                "test-files/sample-document.pdf", "PDF");
        log.info("临时文件上传成功，File ID: {}", tempFile.getId());

        // 返回最后一个文件的ID用于下载测试
        return tempFile.getId();
    }

    /**
     * 演示文件下载访问
     * <p>
     * 通过文件ID获取访问票据，票据是带有过期时间的临时URL
     * fullInternalPath参数控制是否返回完整的内部路径信息
     */
    public void demonstrateFileDownload(Long fileId) throws Exception {
        log.info("\n--- 文件下载演示 ---");

        if (fileId != null) {
            log.info("使用上传的文件进行下载测试，文件ID: {}", fileId);

            log.info("1. 获取文件访问票据");
            String accessTicket = getFileAccessTicket(fileId, 60, false);
            log.info("文件ID {} 访问票据: {}", fileId, accessTicket);

            log.info("2. 使用访问票据下载文件");
            downloadFileFromTicket(accessTicket);

            log.info("3. 获取完整路径访问票据");
            String fullPathTicket = getFileAccessTicket(fileId, 30, true);
            log.info("文件ID {} 完整路径访问票据: {}", fileId, fullPathTicket);

            log.info("4. 使用完整路径票据下载文件");
            downloadFileFromTicket(fullPathTicket);
        } else {
            log.warn("没有提供有效的文件ID进行下载测试");
        }
    }

    /**
     * 获取临时上传票据
     */
    private String getUploadTicket(String filename, int expiryMinutes) throws IOException {
        HttpResponse<JsonNode> response = Unirest.get(MINIO_PROXY_BASE_URL + "/file/upload-ticket")
                .queryString("filename", filename)
                .queryString("expiryMinutes", expiryMinutes)
                .asJson();

        if (response.isSuccess()) {
            return response.getBody().getObject().getString("url");
        } else {
            log.error("获取上传票据失败 - HTTP {}: {}", response.getStatus(), response.getBody());
            throw new IOException("HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }

    /**
     * 获取直接上传票据
     */
    private String getDirectUploadTicket(String bucket, String targetPath, String filename, int expiryMinutes) throws IOException {
        HttpResponse<JsonNode> response = Unirest.get(MINIO_PROXY_BASE_URL + "/file/direct-upload-ticket")
                .queryString("bucket", bucket)
                .queryString("targetPath", targetPath)
                .queryString("filename", filename)
                .queryString("expiryMinutes", expiryMinutes)
                .asJson();

        if (response.isSuccess()) {
            return response.getBody().getObject().getString("url");
        } else {
            log.error("获取直接上传票据失败 - HTTP {}: {}", response.getStatus(), response.getBody());
            throw new IOException("HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }

    /**
     * 获取文件访问票据
     */
    private String getFileAccessTicket(Long fileId, int expiryMinutes, boolean fullInternalPath) throws IOException {
        HttpResponse<JsonNode> response = Unirest.get(MINIO_PROXY_BASE_URL + "/file/" + fileId + "/access-ticket")
                .queryString("expiryMinutes", expiryMinutes)
                .queryString("fullInternalPath", fullInternalPath)
                .asJson();

        if (response.isSuccess()) {
            return response.getBody().getObject().getString("url");
        } else {
            log.error("获取文件访问票据失败 - HTTP {}: {}", response.getStatus(), response.getBody());
            throw new IOException("HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }

    /**
     * 使用访问票据下载文件
     */
    private byte[] downloadFileFromTicket(String ticketUrl) throws IOException {
        // 如果URL不包含协议，添加完整的MinIO代理URL前缀
        String fullUrl = ticketUrl;
        if (!ticketUrl.startsWith("http")) {
            fullUrl = "http://" + EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + ticketUrl;
        }

        log.info("从票据URL下载文件: {}", fullUrl);

        HttpResponse<byte[]> response = Unirest.get(fullUrl).asBytes();

        if (response.isSuccess()) {
            byte[] content = response.getBody();
            log.info("文件下载成功，大小: {} bytes", content.length);
            return content;
        } else {
            log.error("文件下载失败 - HTTP {}: {}", response.getStatus(), response.getStatusText());
            throw new IOException("文件下载失败: HTTP " + response.getStatus() + ": " + response.getStatusText());
        }
    }

    /**
     * 检查文件是否存在
     */
    private boolean checkFileExists(String bucket, String filePath) {
        return S.service(StorageService.class).exists(bucket+":"+filePath);
    }

    /**
     * 上传文件到指定bucket并创建File记录
     */
    private File uploadFileAndCreateRecord(String bucket, String targetPath, String filename,
                                           String localFilePath, String fileType) throws Exception {
        // 1. 获取上传票据
        String uploadTicket = getDirectUploadTicket(bucket, targetPath, filename, 15);

        // 2. 读取本地文件
        Path filePath = Paths.get(localFilePath);
        if (!Files.exists(filePath)) {
            throw new IOException("本地文件不存在: " + localFilePath);
        }

        byte[] fileContent = Files.readAllBytes(filePath);

        // 3. 上传文件
        uploadFileToMinio(uploadTicket, fileContent, filename);

        // 4. 创建File对象
        File file = File.newModel();
        file.setName(filename);
        file.setFileType(fileType);
        file.setPath(bucket + ":" + targetPath + "/" + filename);
        file.setFileSize((long) fileContent.length);
        file.setOriginalPath(localFilePath);

        // 5. 保存到数据库
        return UserContext.runAsSystem(() -> S.service(ObjectService.class).save(file));
    }

    /**
     * 上传临时文件并创建File记录
     */
    private File uploadTempFileAndCreateRecord(String filename, String localFilePath, String fileType) throws Exception {
        // 1. 获取临时上传票据
        String uploadTicket = getUploadTicket(filename, 30);

        // 2. 读取本地文件
        Path filePath = Paths.get(localFilePath);
        if (!Files.exists(filePath)) {
            throw new IOException("本地文件不存在: " + localFilePath);
        }

        byte[] fileContent = Files.readAllBytes(filePath);

        // 3. 上传文件
        uploadFileToMinio(uploadTicket, fileContent, filename);

        // 4. 从上传票据中提取存储路径
        String storagePath = extractStoragePathFromTicket(uploadTicket);

        // 5. 创建File对象
        File file = File.newModel();
        file.setName(filename);
        file.setFileType(fileType);
        file.setPath(storagePath);
        file.setFileSize((long) fileContent.length);
        file.setOriginalPath(localFilePath);

        // 6. 保存到数据库
        return UserContext.runAsSystem(() -> S.service(ObjectService.class).save(file));
    }

    /**
     * 使用预签名URL上传文件到MinIO
     */
    private void uploadFileToMinio(String presignedUrl, byte[] fileContent, String filename) throws IOException {
        // 如果URL不包含协议，添加完整的MinIO代理URL前缀
        String fullUrl = presignedUrl;
        if (!presignedUrl.startsWith("http")) {
            fullUrl = "http://" + EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + presignedUrl;
        }

        log.info("上传文件到URL: {}", fullUrl);

        HttpResponse<String> response = Unirest.put(fullUrl)
                .header("Content-Type", "application/octet-stream")
                .body(fileContent)
                .asString();

        if (response.isSuccess()) {
            log.info("文件 {} 上传成功", filename);
        } else {
            log.error("文件上传失败 - HTTP {}: {}", response.getStatus(), response.getBody());
            throw new IOException("文件上传失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }

    /**
     * 从上传票据URL中提取存储路径
     */
    private String extractStoragePathFromTicket(String ticketUrl) {
        // 从类似 "/storage/temp/3b6abfbe-63f2-4906-9290-6a2489b94676/sample-document.pdf?..." 
        // 的URL中提取 "temp:3b6abfbe-63f2-4906-9290-6a2489b94676/sample-document.pdf"
        try {
            String path = ticketUrl.split("\\?")[0]; // 移除查询参数
            if (path.startsWith("/storage/")) {
                path = path.substring("/storage/".length()); // 移除 /storage/ 前缀
                // 将第一个 / 替换为 :
                int firstSlash = path.indexOf('/');
                if (firstSlash > 0) {
                    return path.substring(0, firstSlash) + ":" + path.substring(firstSlash + 1);
                }
            }
            return path;
        } catch (Exception e) {
            log.warn("无法从票据URL提取存储路径: {}", ticketUrl, e);
            return ticketUrl;
        }
    }
}