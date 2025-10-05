package io.emop.example.filestorage.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.emop.service.config.EMOPConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 批量文件操作演示
 * 演示批量文件操作，包括：
 * 1. 批量ZIP文件上传和解压
 * 2. 批量文件下载为ZIP
 * 3. 目录批量下载
 * 4. 批量文件访问票据生成
 */
@Slf4j
public class BatchOperationsDemo {

    private static final String MINIO_PROXY_BASE_URL = "http://minioproxy-" +
            EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + ":9003/minioproxy/api";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BatchOperationsDemo() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 运行批量操作演示
     */
    public void runDemo() {
        log.info("\n=== 批量文件操作演示 ===");

        try {
            // 1. 演示批量ZIP上传
            demonstrateBulkZipUpload();

            // 2. 演示批量下载
            demonstrateBatchDownload();

            // 3. 演示目录下载
            demonstrateDirectoryDownload();
        } catch (Exception e) {
            log.error("批量操作演示失败", e);
            throw new RuntimeException("批量操作演示失败", e);
        }
    }

    /**
     * 演示批量ZIP上传和解压
     */
    public void demonstrateBulkZipUpload() throws IOException {
        log.info("\n--- 批量ZIP上传演示 ---");

        log.info("注意：批量ZIP上传需要实际的ZIP文件");
        log.info("此演示将创建一个测试ZIP文件并上传到MinIO");

        // 创建测试ZIP文件
        log.info("1. 创建测试ZIP文件");
        java.io.File tempZipFile = createTestZipFile();
        log.info("测试ZIP文件已创建: {}", tempZipFile.getAbsolutePath());

        try {
            // 2. 第一次上传：创建文件记录
            log.info("2. 第一次批量上传ZIP文件到 document bucket（CREATE_ONLY）");
            JsonNode firstUploadResult = bulkUploadZip(tempZipFile, "document", "products/test-batch-demo", "CREATE_ONLY", null);

            // 3. 从第一次上传结果中提取 fileId
            log.info("3. 提取第一次上传的 fileId");
            Map<String, Long> fileIdMap = extractFileIds(firstUploadResult);
            log.info("提取到的 fileId 映射: {}", fileIdMap);

            // 4. 第二次上传：更新文件记录并添加新属性
            log.info("4. 第二次批量上传ZIP文件（UPDATE_ONLY，添加 additionalProp）");
            Map<String, Object> updateMetadata = createUpdateMetadata(fileIdMap);
            String metadataJson = objectMapper.writeValueAsString(updateMetadata);
            log.info("更新元数据配置: {}", metadataJson);
            JsonNode secondUploadResult = bulkUploadZip(tempZipFile, "document", "products/test-batch-demo", "UPDATE_ONLY", metadataJson);

            // 5. 查询文件验证 additionalProp 是否已更新
            log.info("5. 查询文件验证 additionalProp 属性");
            for (Map.Entry<String, Long> entry : fileIdMap.entrySet()) {
                queryAndVerifyFile(entry.getValue(), entry.getKey());
            }

        } finally {
            // 清理临时文件
            if (tempZipFile.exists()) {
                tempZipFile.delete();
                log.info("临时ZIP文件已删除");
            }
        }
    }

    /**
     * 演示批量文件下载
     */
    public void demonstrateBatchDownload() throws IOException {
        log.info("\n--- 批量文件下载演示 ---");

        // 注意：这里使用示例文件ID，实际使用时需要使用真实存在的文件ID
        List<Long> fileIds = Arrays.asList(11111L, 22222L, 33333L, 44444L);

        // 1. 批量下载指定文件ID为ZIP
        log.info("1. 批量下载指定文件ID为ZIP");
        byte[] zipContent = bulkDownloadByIdsZip(fileIds, "batch-download-by-ids.zip");
        log.info("批量下载成功，ZIP文件大小: {} bytes", zipContent.length);

        // 可选：保存到本地文件
        java.io.File outputFile = new java.io.File("/tmp/batch-download-by-ids.zip");
        java.nio.file.Files.write(outputFile.toPath(), zipContent);
        log.info("ZIP文件已保存到: {}", outputFile.getAbsolutePath());

        // 2. 按路径批量下载
        log.info("2. 按路径批量下载");
        List<String> filePaths = Arrays.asList("test-file1.txt", "test-file2.txt");
        zipContent = bulkDownloadByPathsZip("cad", "products/test-batch", filePaths, "batch-download-by-paths.zip");
        log.info("按路径批量下载成功，ZIP文件大小: {} bytes", zipContent.length);
    }

    /**
     * 演示目录批量下载
     */
    public void demonstrateDirectoryDownload() throws IOException {
        log.info("\n--- 目录批量下载演示 ---");

        // 1. 下载整个产品目录
        log.info("1. 下载产品CAD目录");
        byte[] zipContent = downloadDirectoryZip("cad", "products/test-batch", "product-cad-files.zip", true);
        log.info("CAD目录下载成功，ZIP文件大小: {} bytes", zipContent.length);

        // 可选：保存到本地文件
        java.io.File outputFile = new java.io.File("/tmp/product-cad-files.zip");
        java.nio.file.Files.write(outputFile.toPath(), zipContent);
        log.info("ZIP文件已保存到: {}", outputFile.getAbsolutePath());

        // 2. 下载文档目录（不包含子目录）
        log.info("2. 下载产品文档目录（不包含子目录）");
        zipContent = downloadDirectoryZip("document", "products/test-batch-with-metadata", "product-documents.zip", false);
        log.info("文档目录下载成功，ZIP文件大小: {} bytes", zipContent.length);
    }

    // ========== 实际的REST API调用方法 ==========

    /**
     * 创建测试ZIP文件
     */
    private java.io.File createTestZipFile() throws IOException {
        java.io.File tempZip = java.io.File.createTempFile("test-batch-", ".zip");

        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(tempZip))) {

            // 添加几个测试文件
            addZipEntry(zos, "test-file1.txt", "这是测试文件1的内容");
            addZipEntry(zos, "test-file2.txt", "这是测试文件2的内容");
            addZipEntry(zos, "subdir/test-file3.txt", "这是子目录中的测试文件3");

            log.info("测试ZIP文件创建成功，包含3个文件");
        }

        return tempZip;
    }

    /**
     * 向ZIP添加条目
     */
    private void addZipEntry(java.util.zip.ZipOutputStream zos, String entryName, String content) throws IOException {
        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /**
     * 批量上传ZIP文件
     */
    private JsonNode bulkUploadZip(java.io.File zipFile, String bucket, String basePath,
                                   String strategy, String fileMetadataConfig) throws IOException {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(MINIO_PROXY_BASE_URL + "/file/bulk-upload-zip")
                .newBuilder()
                .addQueryParameter("bucket", bucket)
                .addQueryParameter("basePath", basePath)
                .addQueryParameter("strategy", strategy);

        if (fileMetadataConfig != null && !fileMetadataConfig.isEmpty()) {
            urlBuilder.addQueryParameter("fileMetadataConfig", fileMetadataConfig);
        }

        RequestBody fileBody = RequestBody.create(zipFile, MediaType.parse("application/zip"));
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", zipFile.getName(), fileBody)
                .build();

        Request request = builderWithAuthHeader()
                .url(urlBuilder.build())
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                log.error("批量ZIP上传失败 - HTTP {}: {}", response.code(), responseBody);
                throw new IOException("HTTP " + response.code() + ": " + responseBody);
            }

            log.info("result: {}", responseBody);
            JsonNode result = objectMapper.readTree(responseBody);
            log.info("批量ZIP上传成功！");
            return result;
        }
    }

    /**
     * 按文件ID批量下载为ZIP
     */
    private byte[] bulkDownloadByIdsZip(List<Long> fileIds, String zipFileName) throws IOException {
        HttpUrl url = HttpUrl.parse(MINIO_PROXY_BASE_URL + "/file/bulk-download-by-ids-zip")
                .newBuilder()
                .addQueryParameter("zipFileName", zipFileName)
                .build();

        String jsonBody = objectMapper.writeValueAsString(fileIds);

        Request request = builderWithAuthHeader()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body().string();
                log.error("按ID批量下载失败 - HTTP {}: {}", response.code(), errorBody);
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }

            return response.body().bytes();
        }
    }

    /**
     * 按路径批量下载为ZIP
     */
    private byte[] bulkDownloadByPathsZip(String bucket, String basePath,
                                          List<String> filePaths, String zipFileName) throws IOException {
        HttpUrl url = HttpUrl.parse(MINIO_PROXY_BASE_URL + "/file/bulk-download-zip")
                .newBuilder()
                .addQueryParameter("bucket", bucket)
                .addQueryParameter("basePath", basePath)
                .addQueryParameter("zipFileName", zipFileName)
                .build();

        String jsonBody = objectMapper.writeValueAsString(filePaths);

        Request request = builderWithAuthHeader()
                .url(url)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body().string();
                log.error("按路径批量下载失败 - HTTP {}: {}", response.code(), errorBody);
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }

            return response.body().bytes();
        }
    }

    /**
     * 下载整个目录为ZIP
     */
    private byte[] downloadDirectoryZip(String bucket, String basePath,
                                        String zipFileName, boolean includeSubdirectories) throws IOException {
        HttpUrl url = HttpUrl.parse(MINIO_PROXY_BASE_URL + "/file/download-directory-zip")
                .newBuilder()
                .addQueryParameter("bucket", bucket)
                .addQueryParameter("basePath", basePath)
                .addQueryParameter("zipFileName", zipFileName)
                .addQueryParameter("includeSubdirectories", String.valueOf(includeSubdirectories))
                .build();

        Request request = builderWithAuthHeader()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body().string();
                log.error("目录下载失败 - HTTP {}: {}", response.code(), errorBody);
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }

            return response.body().bytes();
        }
    }

    /**
     * 从上传结果中提取文件ID映射
     */
    private Map<String, Long> extractFileIds(JsonNode uploadResult) {
        Map<String, Long> fileIdMap = new HashMap<>();

        JsonNode filesNode = uploadResult.get("files");
        if (filesNode != null && filesNode.isArray()) {
            for (JsonNode fileNode : filesNode) {
                String fileName = fileNode.get("fileName").asText();
                Long fileId = fileNode.get("fileId").asLong();
                fileIdMap.put(fileName, fileId);
                log.info("文件: {} -> fileId: {}", fileName, fileId);
            }
        }

        return fileIdMap;
    }

    /**
     * 创建更新元数据配置（包含 fileId 和新属性）
     */
    private Map<String, Object> createUpdateMetadata(Map<String, Long> fileIdMap) {
        Map<String, Object> fileMetadataConfig = new HashMap<>();
        long currentTimeMillis = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : fileIdMap.entrySet()) {
            String fileName = entry.getKey();
            Long fileId = entry.getValue();

            Map<String, Object> config = new HashMap<>();
            config.put("fileId", fileId);

            Map<String, String> additionalProps = new HashMap<>();
            additionalProps.put("additionalProp", String.valueOf(currentTimeMillis));
            additionalProps.put("updateTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            additionalProps.put("updatedBy", "BatchOperationsDemo");

            config.put("additionalProperties", additionalProps);
            if ("test-file3.txt".equals(fileName)) {
                //需要特殊处理一下，因为在子文件夹下面
                fileMetadataConfig.put("subdir/" + fileName, config);
            } else {
                fileMetadataConfig.put(fileName, config);
            }

        }

        return fileMetadataConfig;
    }

    /**
     * 查询并验证文件的 additionalProp 属性
     */
    private void queryAndVerifyFile(Long fileId, String fileName) {
        try {
            log.info("查询文件 {} (ID: {})", fileName, fileId);

            io.emop.model.document.File file = io.emop.service.S.service(io.emop.service.api.data.ObjectService.class)
                    .findById(fileId);

            if (file != null) {
                String additionalProp = file.get("additionalProp");
                String updateTime = file.get("updateTime");
                String updatedBy = file.get("updatedBy");

                log.info("文件查询成功:");
                log.info("  - 文件名: {}", file.getName());
                log.info("  - 文件路径: {}", file.getPath());
                log.info("  - additionalProp: {}", additionalProp);
                log.info("  - updateTime: {}", updateTime);
                log.info("  - updatedBy: {}", updatedBy);

                if (additionalProp != null) {
                    log.info("✓ additionalProp 属性已成功更新！");
                } else {
                    throw new RuntimeException("Property is not updated into File object " + fileId);
                }
            } else {
                log.error("文件未找到: {}", fileId);
            }
        } catch (Exception e) {
            log.error("查询文件失败: {}", e.getMessage(), e);
        }
    }

    private okhttp3.Request.Builder builderWithAuthHeader() {
        return new Request.Builder().header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}");
    }
}