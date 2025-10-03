package io.emop.example.relation.client.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API 调用演示
 * 演示如何通过HTTP REST API操作数据
 */
@Slf4j
public class RestApiDemo {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl = "http://localhost:870/webconsole/api";
    private final String timestamp = String.valueOf(System.currentTimeMillis());

    public RestApiDemo() {
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @SneakyThrows
    public void demonstrateRestApi() {
        log.info("开始REST API演示...");

        // 1. 创建项目
        Long projectId = createProject();

        // 2. 创建任务
        Long taskId = createTask(projectId);

        // 3. 更新数据
        updateProject(projectId);
    }

    private Request.Builder builderWithAuthHeader() {
        return new Request.Builder().header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}");
    }

    /**
     * 创建项目
     */
    private Long createProject() throws IOException {
        log.info("--- 通过REST API创建项目 ---");

        Map<String, Object> projectData = new HashMap<>();
        projectData.put("code", "PROJ-REST-" + timestamp);
        projectData.put("revId", "A");
        projectData.put("name", "REST API演示项目");
        projectData.put("projectManager", "REST管理员");
        projectData.put("status", "PLANNING");

        String json = objectMapper.writeValueAsString(Map.of("data", projectData));
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));

        Request request = builderWithAuthHeader()
                .url(baseUrl + "/data/RSampleProject")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                Long projectId = jsonNode.get("id").asLong();
                log.info("项目创建成功，ID: {}", projectId);
                return projectId;
            } else {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                log.error("创建项目失败，状态码: {}, 响应体: {}", response.code(), errorBody);
                throw new RuntimeException("创建项目失败，状态码: " + response.code() + ", 响应体: " + errorBody);
            }
        }
    }

    /**
     * 创建任务
     */
    private Long createTask(Long projectId) throws IOException {
        if (projectId == null) {
            return null;
        }

        log.info("--- 通过REST API创建任务 ---");

        Map<String, Object> taskData = new HashMap<>();
        taskData.put("code", "TASK-REST-" + timestamp);
        taskData.put("revId", "A");
        taskData.put("name", "REST API演示任务");
        taskData.put("assignee", "REST开发者");
        taskData.put("status", "TODO");
        taskData.put("priority", "HIGH");
        taskData.put("projectId", projectId);

        String json = objectMapper.writeValueAsString(Map.of("data", taskData));
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));

        Request request = builderWithAuthHeader()
                .url(baseUrl + "/data/RSampleTask")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                Long taskId = jsonNode.get("id").asLong();
                log.info("任务创建成功，ID: {}", taskId);
                return taskId;
            } else {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                log.error("创建任务失败，状态码: {}, 响应体: {}", response.code(), errorBody);
                throw new RuntimeException("创建任务失败，状态码: " + response.code() + ", 响应体: " + errorBody);
            }
        }
    }

    /**
     * 更新项目
     */
    private void updateProject(Long projectId) throws IOException {
        if (projectId == null) {
            return;
        }

        log.info("--- 通过REST API更新项目 ---");

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "REST API演示项目 - 已更新");
        updateData.put("status", "IN_PROGRESS");

        String json = objectMapper.writeValueAsString(Map.of("data", updateData, "_version", 2));
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));

        Request request = builderWithAuthHeader()
                .url(baseUrl + "/data/" + projectId)
                .put(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                log.info("项目更新成功");
            } else {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                log.error("更新项目失败，状态码: {}, 响应体: {}", response.code(), errorBody);
                throw new RuntimeException("更新项目失败，状态码: " + response.code() + ", 响应体: " + errorBody);
            }
        }
    }
}