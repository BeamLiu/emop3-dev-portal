package io.emop.example.cad.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.emop.example.cad.model.ItemEntity;
import io.emop.example.cad.model.PostItemEntityResponse;
import io.emop.example.cad.util.Utils;
import io.emop.service.config.EMOPConfig;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * CAD API调用服务
 */
@Slf4j
public class CadApiService {
    
    private static final String CAD_BASE_URL = "http://" + 
            EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + 
            ":891/cad-integration/api/cad-integration";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static volatile boolean unirestConfigured = false;
    
    public CadApiService() {
        if (!unirestConfigured) {
            synchronized (CadApiService.class) {
                if (!unirestConfigured) {
                    Unirest.config()
                            .connectTimeout(30000)
                            .socketTimeout(60000);
                    unirestConfigured = true;
                }
            }
        }
    }
    
    /**
     * 比对BOM数据
     */
    public List<ItemEntity> compareItemEntity(List<ItemEntity> itemEntities) throws Exception {
        String url = CAD_BASE_URL + "/item/compare";
        log.info("调用Compare API: {}", url);
        
        HttpResponse<String> response = Unirest.post(url)
                .header("Content-Type", "application/json")
                .header("x-cad-type", "CREO")
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .body(objectMapper.writeValueAsString(itemEntities))
                .asString();
        
        if (response.isSuccess()) {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            JsonNode dataNode = jsonNode.get("content");
            return objectMapper.convertValue(dataNode, new TypeReference<List<ItemEntity>>() {});
        } else {
            throw new RuntimeException("Compare失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }
    
    /**
     * 提交BOM结构
     */
    public PostItemEntityResponse postItemEntity(List<ItemEntity> itemEntities) throws Exception {
        String url = CAD_BASE_URL + "/item";
        log.info("调用Post API: {}", url);
        
        String requestBody = objectMapper.writeValueAsString(Map.of("itemEntities", itemEntities));

        log.info("BOM提交: {} ...", Utils.previewString(requestBody));
        
        HttpResponse<String> response = Unirest.post(url)
                .header("Content-Type", "application/json")
                .header("x-cad-type", "CREO")
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .body(requestBody)
                .asString();
        
        if (!response.isSuccess()) {
            throw new RuntimeException("Post失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
        
        String body = response.getBody();
        log.info("BOM提交成功, 返回: {}", Utils.previewString(body));
        
        // 解析响应
        JsonNode jsonNode = objectMapper.readTree(body);
        JsonNode dataNode = jsonNode.get("content");
        return objectMapper.convertValue(dataNode, PostItemEntityResponse.class);
    }
    
    /**
     * 获取BOM结构
     */
    public List<ItemEntity> getItemEntity(Long componentId) throws Exception {
        String url = CAD_BASE_URL + "/item/" + componentId;
        log.info("调用Get API: {}", url);
        
        HttpResponse<String> response = Unirest.get(url)
                .header("x-cad-type", "CREO")
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .asString();
        
        if (response.isSuccess()) {
            String body = response.getBody();
            log.info("加载BOM成功, 返回: {}", Utils.previewString(body));
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode dataNode = jsonNode.get("content").get("itemEntities");
            return objectMapper.convertValue(dataNode, new TypeReference<List<ItemEntity>>() {});
        } else {
            throw new RuntimeException("Get失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }
    
    /**
     * 提交CAD模型轻量化转图任务
     * 用于将大文件转换为轻量化格式（CDXFB）以便界面查看
     * 转换后的文件会存储在MinIO的 {fileId}/converted/ 子目录下
     */
    public String submitConversionJob(Long componentId) throws Exception {
        String conversionUrl = "http://" + 
                EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + 
                ":860/cad-model-conversion/api/cad/conversion/item/" + componentId + "/convert";
        log.info("提交轻量化转图任务: componentId={} 至 {}", componentId, conversionUrl);
        
        HttpResponse<String> response = Unirest.post(conversionUrl)
                .header("Content-Type", "application/json")
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .asString();
        
        if (response.isSuccess()) {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String jobId = jsonNode.get("jobId").asText();
            String message = jsonNode.get("message").asText();
            log.info("转图任务提交成功: jobId={}", jobId);
            log.info("任务状态查看: {}", message);
            return jobId;
        } else {
            throw new RuntimeException("转图任务提交失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }
}
