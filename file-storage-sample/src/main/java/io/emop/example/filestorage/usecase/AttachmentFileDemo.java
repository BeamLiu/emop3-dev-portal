package io.emop.example.filestorage.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.emop.model.document.File;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.storage.MinioProxyService;
import io.emop.service.api.storage.StorageService;
import io.emop.service.config.EMOPConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 附件文件管理演示
 * 演示附件文件操作，包括：
 * 1. 获取支持的附件扩展名
 * 2. 上传附件文件（RPC方式）
 * 3. 检查附件文件存在性
 * 4. 生成附件访问票据
 * 5. 列出文件的所有附件
 * 6. 批量附件操作
 * <p>
 * 注意：本示例演示 REST API 调用方式，实际业务服务应使用 RPC 方式（MinioProxyService + StorageService）
 */
@Slf4j
public class AttachmentFileDemo {

    private static final String MINIO_PROXY_BASE_URL = "http://minioproxy-" +
            EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + ":9003/minioproxy/api";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AttachmentFileDemo() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 运行附件文件演示
     */
    public void runDemo() {
        log.info("\n=== 附件文件管理演示 ===");
        log.info("注意：本示例演示 REST API 调用方式");
        log.info("实际业务服务应使用 RPC 方式：MinioProxyService.buildAttachmentPath() + StorageService.upload()");

        try {
            // 1. 演示支持的附件扩展名
            demonstrateSupportedExtensions();

            // 2. 演示上传附件文件
            Long fileId = demonstrateAttachmentUpload();

            // 3. 演示附件存在性检查
            demonstrateAttachmentCheck(fileId);

            // 4. 演示附件访问
            demonstrateAttachmentAccess(fileId);

            // 5. 演示附件列表
            demonstrateAttachmentListing(fileId);
        } catch (Exception e) {
            throw new RuntimeException("附件文件管理场景失败", e);
        }
    }

    /**
     * 演示支持的附件扩展名
     */
    public void demonstrateSupportedExtensions() throws Exception {
        log.info("\n--- 1. 支持的附件扩展名演示 ---");

        // 1. 获取所有支持的附件扩展名
        log.info("获取所有支持的附件扩展名");
        List<String> allExtensions = getSupportedAttachmentExtensions();
        log.info("支持的附件扩展名: {}", allExtensions);
        log.info("常见用途：");
        log.info("  - jpg, png: 缩略图、预览图");
        log.info("  - md5: 文件校验");
        log.info("  - pdf: 格式转换");
    }

    /**
     * 演示上传附件文件
     */
    public Long demonstrateAttachmentUpload() throws Exception {
        log.info("\n--- 2. 上传附件文件演示 ---");
        log.info("使用 RPC 方式：MinioProxyService + StorageService");

        // 使用一个之前演示中上传的文件
        File originalFile = Q.result(File.class).where("name=?", "goc04zhijia.prt").first();
        log.info("原文件: {} (ID: {})", originalFile.getName(), originalFile.getId());

        // 1. 上传 MD5 校验文件
        log.info("");
        log.info("1. 上传 MD5 校验文件");
        uploadMd5Attachment(originalFile.getId(), originalFile);
        log.info("✓ MD5 校验文件上传成功");

        // 2. 上传缩略图
        log.info("");
        log.info("2. 上传缩略图");
        uploadThumbnailAttachment(originalFile.getId());
        log.info("✓ 缩略图上传成功");

        // 3. 批量上传示例
        log.info("");
        log.info("3. 批量上传附件示例");
        List<Long> fileIds = Arrays.asList(originalFile.getId());
        batchUploadAttachments(fileIds);
        log.info("✓ 批量上传完成");

        log.info("");
        log.info("常见场景：");
        log.info("  - 图片上传后生成缩略图 (.jpg)");
        log.info("  - 文件上传后计算 MD5 (.md5)");
        log.info("  - 图片格式转换 (.webp)");
        log.info("  - PDF 预览图生成 (.png)");

        return originalFile.getId();
    }

    /**
     * 上传 MD5 校验文件附件
     */
    private void uploadMd5Attachment(Long fileId, File originalFile) throws Exception {
        // 1. 构建附加文件路径
        String attachmentPath = S.service(MinioProxyService.class)
                .buildAttachmentPath(fileId, "md5");

        log.info("   附件路径: {}", attachmentPath);

        // 2. 计算 MD5（模拟）
        String md5Hash = calculateMd5(originalFile);
        byte[] md5Content = md5Hash.getBytes(StandardCharsets.UTF_8);

        // 3. 使用 StorageService 上传
        S.service(StorageService.class)
                .upload(attachmentPath, md5Content, true);

        log.info("   MD5 值: {}", md5Hash);
    }

    /**
     * 上传缩略图附件
     */
    private void uploadThumbnailAttachment(Long fileId) throws Exception {
        // 1. 构建附加文件路径
        String attachmentPath = S.service(MinioProxyService.class)
                .buildAttachmentPath(fileId, "jpg");

        log.info("   附件路径: {}", attachmentPath);

        // 2. 生成模拟缩略图内容（实际应用中应该是真实的图片处理）
        byte[] thumbnailContent = generateMockThumbnail();

        // 3. 使用 InputStream 上传（推荐方式，避免内存拷贝）
        try (InputStream inputStream = new ByteArrayInputStream(thumbnailContent)) {
            S.service(StorageService.class)
                    .upload(attachmentPath, inputStream, true);
        }

        log.info("   缩略图大小: {} bytes", thumbnailContent.length);
    }

    /**
     * 批量上传附件
     */
    private void batchUploadAttachments(List<Long> fileIds) throws Exception {
        // 1. 批量构建路径
        Map<Long, String> paths = S.service(MinioProxyService.class)
                .batchBuildAttachmentPaths(fileIds, "png");

        log.info("   批量构建了 {} 个附件路径", paths.size());

        // 2. 批量上传
        StorageService storageService = S.service(StorageService.class);
        paths.forEach((fileId, path) -> {
            byte[] previewContent = generateMockPreview();
            storageService.upload(path, previewContent, true);
            log.info("   ✓ 文件ID {} 预览图上传成功", fileId);
        });
    }

    /**
     * 计算文件 MD5（模拟）
     */
    private String calculateMd5(File file) throws Exception {
        // 实际应用中应该下载文件内容计算 MD5
        // 这里为了演示，使用文件路径生成模拟 MD5
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] hash = md.digest(file.getPath().getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 生成模拟缩略图内容
     */
    private byte[] generateMockThumbnail() {
        // 实际应用中应该使用图片处理库生成真实缩略图
        return "Mock thumbnail image content".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成模拟预览图内容
     */
    private byte[] generateMockPreview() {
        // 实际应用中应该使用图片处理库生成真实预览图
        return "Mock preview image content".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 演示附件存在性检查
     */
    public void demonstrateAttachmentCheck(Long sampleFileId) throws Exception {
        log.info("\n--- 3. 附件存在性检查演示 ---");
        log.info("在上传附件后，可以检查附件是否存在");

        // 1. 检查缩略图是否存在
        log.info("1. 检查缩略图附件");
        boolean thumbnailExists = checkAttachmentExists(sampleFileId, "jpg");
        log.info("文件ID {} 缩略图存在: {}", sampleFileId, thumbnailExists);
        if (!thumbnailExists) {
            throw new RuntimeException("文件" + sampleFileId + "缩略图不存在");
        }

        // 2. 检查PNG预览图是否存在
        log.info("2. 检查PNG预览图附件");
        boolean pngExists = checkAttachmentExists(sampleFileId, "png");
        if (!pngExists) {
            throw new RuntimeException("文件" + sampleFileId + "PNG预览图不存在");
        }

        // 3. 检查MD5校验文件是否存在
        log.info("3. RPC方式检查MD5校验文件附件");
        boolean md5Exists = S.service(MinioProxyService.class).checkAttachmentExists(sampleFileId, "md5");
        if (!md5Exists) {
            throw new RuntimeException("文件" + sampleFileId + "MD5校验文件不存在");
        }
    }

    /**
     * 演示附件访问
     */
    public void demonstrateAttachmentAccess(Long sampleFileId) throws Exception {
        log.info("\n--- 4. 附件访问演示 ---");
        log.info("附件上传后，可以生成访问票据供前端下载");

        // 1. 获取缩略图访问票据
        log.info("1. 获取缩略图访问票据");
        String thumbnailTicket = getAttachmentAccessTicket(sampleFileId, "jpg", 30);
        log.info("缩略图访问票据: {}", thumbnailTicket);
        log.info("前端可使用此 URL 直接访问缩略图");

        // 2. 获取预览图访问票据
        log.info("2. 获取预览图访问票据");
        String previewTicket = getAttachmentAccessTicket(sampleFileId, "png", 60);
        log.info("预览图访问票据: {}", previewTicket);

        // 3. 获取校验文件访问票据
        log.info("3. 获取MD5校验文件访问票据");
        String md5Ticket = getAttachmentAccessTicket(sampleFileId, "md5", 15);
        log.info("MD5校验文件访问票据: {}", md5Ticket);

        log.info("");
        log.info("直接下载附件（REST API）：");
        log.info("GET /api/file/{fileId}/attachment/{extension}");
    }

    /**
     * 演示附件列表
     */
    public void demonstrateAttachmentListing(Long sampleFileId) throws IOException {
        log.info("\n--- 5. 附件列表演示 ---");
        log.info("查看文件已上传的所有附件");

        // 1. 列出文件的所有附件
        log.info("1. 列出文件的所有附件");
        List<String> allAttachments = listFileAttachments(sampleFileId);
        if (allAttachments.isEmpty()) {
            throw new RuntimeException("文件ID " + sampleFileId + " 暂无附件，需要先上传");
        } else {
            log.info("文件ID {} 的所有附件: {}", sampleFileId, allAttachments);
        }

        log.info("");
        log.info("REST API 方式：");
        log.info("GET /api/file/{fileId}/attachments");
    }

    /**
     * 获取支持的附件扩展名
     */
    private List<String> getSupportedAttachmentExtensions() throws IOException {
        HttpUrl url = HttpUrl.parse(MINIO_PROXY_BASE_URL + "/file/supported-extensions");

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                log.error("获取支持的附件扩展名失败 - HTTP {}: {}", response.code(), responseBody);
                throw new IOException("HTTP " + response.code() + ": " + responseBody);
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return objectMapper.convertValue(jsonNode, List.class);
        }
    }

    /**
     * 检查附件文件是否存在
     */
    private boolean checkAttachmentExists(Long fileId, String extension) throws IOException {
        HttpUrl url = HttpUrl.parse(MINIO_PROXY_BASE_URL + "/file/" + fileId + "/attachment/" + extension + "/exists");

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                log.warn("检查附件存在性失败 - HTTP {}: {}", response.code(), responseBody);
                return false;
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return jsonNode.get("exists").asBoolean();
        }
    }

    /**
     * 获取附件访问票据
     */
    private String getAttachmentAccessTicket(Long fileId, String extension, int expiryMinutes) throws IOException {
        HttpUrl url = HttpUrl.parse(MINIO_PROXY_BASE_URL + "/file/" + fileId + "/attachment/" + extension + "/access-ticket")
                .newBuilder()
                .addQueryParameter("expiryMinutes", String.valueOf(expiryMinutes))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                log.error("获取附件访问票据失败 - HTTP {}: {}", response.code(), responseBody);
                throw new IOException("HTTP " + response.code() + ": " + responseBody);
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return jsonNode.get("url").asText();
        }
    }

    /**
     * 列出文件的所有附件
     */
    private List<String> listFileAttachments(Long fileId) throws IOException {
        HttpUrl url = HttpUrl.parse(MINIO_PROXY_BASE_URL + "/file/" + fileId + "/attachments");

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                log.error("列出文件附件失败 - HTTP {}: {}", response.code(), responseBody);
                throw new IOException("HTTP " + response.code() + ": " + responseBody);
            }

            JsonNode jsonNode = objectMapper.readTree(responseBody);
            return objectMapper.convertValue(jsonNode.get("availableExtensions"), List.class);
        }
    }

}