package io.emop.example.cad.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.emop.example.cad.model.ItemEntity;
import io.emop.example.cad.model.PostItemEntityResponse;
import io.emop.service.config.EMOPConfig;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * CAD API调用服务
 */
@Slf4j
public class CadApiService {
    
    private static final String CAD_BASE_URL = "http://" + 
            EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + 
            ":891/cad-integration-sample/api/cad-integration";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public CadApiService() {
        Unirest.config()
                .connectTimeout(30000)
                .socketTimeout(60000);
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
        
        String requestBody = objectMapper.writeValueAsString(
            new PostRequest(itemEntities)
        );
        
        HttpResponse<String> response = Unirest.post(url)
                .header("Content-Type", "application/json")
                .header("x-cad-type", "CREO")
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .body(requestBody)
                .asString();
        
        if (!response.isSuccess()) {
            throw new RuntimeException("Post失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
        
        log.info("BOM提交成功");
        
        // 解析响应
        JsonNode jsonNode = objectMapper.readTree(response.getBody());
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
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            JsonNode dataNode = jsonNode.get("content").get("itemEntities");
            return objectMapper.convertValue(dataNode, new TypeReference<List<ItemEntity>>() {});
        } else {
            throw new RuntimeException("Get失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }
    
    /**
     * Post请求包装类
     */
    private static class PostRequest {
        public List<ItemEntity> itemEntities;
        
        public PostRequest(List<ItemEntity> itemEntities) {
            this.itemEntities = itemEntities;
        }
    }
}
