package io.emop.example.relation.client.demo;

import kong.unirest.Unirest;
import kong.unirest.HttpResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API 调用演示
 * 演示如何通过HTTP REST API操作数据
 */
@Slf4j
public class RestApiDemo {

    private final String baseUrl = "http://localhost:870/webconsole/api";
    private final String timestamp = String.valueOf(System.currentTimeMillis());

    public RestApiDemo() {
        // 配置 Unirest
        Unirest.config()
                .setDefaultHeader("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .setDefaultHeader("Content-Type", "application/json");
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

        HttpResponse<kong.unirest.JsonNode> response = Unirest.post(baseUrl + "/data/RSampleProject")
                .body(Map.of("data", projectData))
                .asJson();

        if (response.isSuccess()) {
            Long projectId = response.getBody().getObject().getLong("id");
            log.info("项目创建成功，ID: {}", projectId);
            return projectId;
        } else {
            log.error("创建项目失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("创建项目失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
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

        HttpResponse<kong.unirest.JsonNode> response = Unirest.post(baseUrl + "/data/RSampleTask")
                .body(Map.of("data", taskData))
                .asJson();

        if (response.isSuccess()) {
            Long taskId = response.getBody().getObject().getLong("id");
            log.info("任务创建成功，ID: {}", taskId);
            return taskId;
        } else {
            log.error("创建任务失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("创建任务失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
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

        HttpResponse<String> response = Unirest.put(baseUrl + "/data/" + projectId)
                .body(Map.of("data", updateData, "_version", 2))
                .asString();

        if (response.isSuccess()) {
            log.info("项目更新成功");
        } else {
            log.error("更新项目失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("更新项目失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
        }
    }
}