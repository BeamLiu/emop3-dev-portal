package io.emop.example.cad.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.emop.service.config.EMOPConfig;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
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
    
    /**
     * 批量上传ZIP文件
     */
    public void bulkUploadZip(File zipFile, String bucket, String basePath, 
                              String strategy, String fileMetadataConfig) throws Exception {
        String url = MINIO_PROXY_BASE_URL + "/file/bulk-upload-zip";
        log.info("批量上传ZIP: bucket={}, basePath={}, strategy={}", bucket, basePath, strategy);
        
        HttpResponse<String> response = Unirest.post(url)
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .queryString("bucket", bucket)
                .queryString("basePath", basePath)
                .queryString("strategy", strategy)
                .queryString("fileMetadataConfig", fileMetadataConfig)
                .field("file", zipFile)
                .asString();
        
        if (response.isSuccess()) {
            log.info("ZIP上传成功: {}", response.getBody());
        } else {
            throw new RuntimeException("ZIP上传失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }
    
    /**
     * 按文件ID批量下载为ZIP
     */
    public byte[] bulkDownloadByIds(List<Long> fileIds, String zipFileName) throws Exception {
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
