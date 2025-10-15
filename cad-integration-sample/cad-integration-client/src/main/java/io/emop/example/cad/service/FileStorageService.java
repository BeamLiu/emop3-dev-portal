package io.emop.example.cad.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.emop.example.cad.util.Utils;
import io.emop.service.config.EMOPConfig;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件存储服务
 */
@Slf4j
public class FileStorageService {
    
    private static final String MINIO_PROXY_BASE_URL = "http://minioproxy-" +
            EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + 
            ":9003/minioproxy/api";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 站点选择结果
     */
    @AllArgsConstructor
    public static class SiteSelectionResult {
        public String siteId;
        public String siteName;
        public String proxyUrl;
        public String actualBucket;
        public String reason;
        public String matchedRule;
    }
    
    /**
     * 基于用户属性选择站点
     */
    public SiteSelectionResult selectMinioProxy(Map<String, Object> userAttributes) throws IOException {
        return selectMinioProxy(null, userAttributes);
    }
    /**
     * 基于用户属性选择站点
     */
    public SiteSelectionResult selectMinioProxy(String logicalBucket,
            Map<String, Object> userAttributes) throws IOException {
        Map<String, Object> context = new HashMap<>();
        context.put("logicalBucket", logicalBucket);
        if (userAttributes != null && !userAttributes.isEmpty()) {
            context.put("userAttributes", userAttributes);
        }

        log.info("基于用户属性选择站点: logicalBucket={}, userAttributes={}", logicalBucket, userAttributes);
        String url = MINIO_PROXY_BASE_URL + "/site-selection/select";

        HttpResponse<String> response = Unirest.post(url)
                .header("Content-Type", "application/json")
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .body(objectMapper.writeValueAsString(context))
                .asString();

        if (response.isSuccess()) {
            JsonNode json = objectMapper.readTree(response.getBody());
            SiteSelectionResult result = new SiteSelectionResult(
                    json.get("siteId").asText(),
                    json.get("siteName").asText(),
                    json.get("proxyUrl").asText(),
                    json.get("actualBucket").asText(),
                    json.get("reason").asText(),
                    json.get("matchedRule").asText());

            return result;
        } else {
            throw new IOException("站点选择失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }
    
    /**
     * 批量上传ZIP文件
     * ZIP文件内应包含 __file_metadata__.json 文件来配置元数据
     */
    public void bulkUploadZip(File zipFile, String bucket, String basePath, String strategy) throws Exception {
        SiteSelectionResult minioProxySelectionResult = selectMinioProxy("cad", null); // 预选站点，日志记录    
        log.info("请使用预选站点: siteId={}, proxyUrl={}, actualBucket={}", 
                minioProxySelectionResult.siteId, 
                minioProxySelectionResult.proxyUrl, 
                minioProxySelectionResult.actualBucket); 
        log.info("演示代码就不切换到预选站点了，直接使用MINIO_PROXY_BASE_URL，避免经过gateway的登录需求");                 
        String url = MINIO_PROXY_BASE_URL + "/file/bulk-upload-zip";
        log.info("批量上传ZIP: bucket={}, basePath={}, strategy={}", bucket, basePath, strategy);
        
        HttpResponse<String> response = Unirest.post(url)
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .queryString("bucket", minioProxySelectionResult.actualBucket)
                .queryString("basePath", basePath)
                .queryString("strategy", strategy)
                .field("file", zipFile)
                .asString();
        
        if (response.isSuccess()) {
            log.info("ZIP上传成功: {}", Utils.previewString(response.getBody()));
            
            // 验证所有文件的fileId都有值
            validateFileIds(response.getBody());
        } else {
            throw new RuntimeException("ZIP上传失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }
    
    /**
     * 验证返回的JSON中所有files都有fileId
     */
    private void validateFileIds(String responseBody) throws Exception {
        Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.get("files");
        
        if (files == null || files.isEmpty()) {
            log.warn("返回结果中没有files数组");
            return;
        }
        
        int totalFiles = files.size();
        int filesWithId = 0;
        int filesWithoutId = 0;
        
        for (Map<String, Object> file : files) {
            String fileId = (String) file.get("fileId");
            String fileName = (String) file.get("fileName");

            if (fileName.endsWith(".jpg")) {
                continue; // 跳过附加文件
            }

            if (fileId != null && !fileId.isEmpty()) {
                filesWithId++;
            } else {
                filesWithoutId++;
                log.warn("文件 {} 缺少fileId，元数据可能未更新", fileName);
            }
        }
        
        log.info("文件元数据验证: 总数={}(包含附加文件), 有fileId={}, 缺少fileId={}", totalFiles, filesWithId, filesWithoutId);
        
        if (filesWithoutId > 0) {
            throw new RuntimeException(
                String.format("有 %d 个文件缺少fileId，元数据未正确更新", filesWithoutId)
            );
        }
        
        log.info("✓ 所有文件的fileId都已生成，元数据更新成功");
    }
    
    /**
     * 按文件ID批量下载为ZIP
     */
    public byte[] bulkDownloadByIds(List<Long> fileIds, String zipFileName) throws Exception {
        SiteSelectionResult minioProxySelectionResult = selectMinioProxy( null); // 预选站点，日志记录    
        log.info("请使用预选站点: siteId={}, proxyUrl={}", 
                minioProxySelectionResult.siteId, 
                minioProxySelectionResult.proxyUrl); 
        log.info("演示代码就不切换到预选站点了，直接使用MINIO_PROXY_BASE_URL，避免经过gateway的登录需求");  
        String url = MINIO_PROXY_BASE_URL + "/file/bulk-download-by-ids-zip";
        log.info("批量下载文件: {} 个文件", fileIds.size());
        
        HttpResponse<byte[]> response = Unirest.post(url)
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .header("Content-Type", "application/json")
                .queryString("zipFileName", zipFileName)
                .body(fileIds)
                .asBytes();
        
        if (response.isSuccess()) {
            log.info("文件下载成功，大小: {} bytes", response.getBody().length);
            return response.getBody();
        } else {
            throw new RuntimeException("文件下载失败: HTTP " + response.getStatus());
        }
    }
}
