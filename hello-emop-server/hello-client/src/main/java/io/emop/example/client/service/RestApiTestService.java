package io.emop.example.client.service;

import kong.unirest.Unirest;
import kong.unirest.HttpResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * REST API 演示服务
 * 演示如何使用 REST API 进行数据操作
 */
@Slf4j
public class RestApiTestService {

    private final String serverUrl;

    public RestApiTestService(String serverUrl) {
        this.serverUrl = serverUrl;
        // 配置 Unirest
        Unirest.config()
                .setDefaultHeader("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .setDefaultHeader("Content-Type", "application/json");
    }

    /**
     * 执行所有REST API演示
     */
    public void runAll() throws IOException {
        log.info("=== 开始 REST API 演示 ===");

        // 演示任务CRUD操作
        testTaskCrudOperations();

        // 演示查询接口
        testQueryOperations();

        // 演示元数据接口
        testMetadataOperations();

        log.info("=== REST API 演示完成 ===");
    }

    /**
     * 演示任务CRUD操作
     */
    public void testTaskCrudOperations() throws IOException {
        log.info("--- 演示任务CRUD操作 ---");

        // 创建任务
        Long taskId = createTask();
        // 查询任务
        queryTask(taskId);

        // 更新任务
        updateTask(taskId);

        // 删除任务
        deleteTask(taskId);
    }

    /**
     * 演示查询操作
     */
    public void testQueryOperations() throws IOException {
        log.info("--- 演示查询操作 ---");

        // 分页查询
        testPageQuery();

        // 条件查询
        testConditionQuery();
    }

    /**
     * 演示元数据操作
     */
    public void testMetadataOperations() throws IOException {
        log.info("--- 演示元数据操作 ---");

        // 查询类型定义
        queryTypeDefinitions();
    }

    /**
     * 创建任务
     */
    private Long createTask() throws IOException {
        Map<String, Object> taskData = new HashMap<>();
        taskData.put("name", "REST API 演示任务");
        taskData.put("description", "通过REST API创建的演示任务");
        taskData.put("code", "T-" + System.currentTimeMillis());
        taskData.put("revId", "A");
        taskData.put("title", "演示任务 " + taskData.get("code"));

        HttpResponse<kong.unirest.JsonNode> response = Unirest.post(serverUrl + "/webconsole/api/data/HelloTask")
                .body(Map.of("data", taskData))
                .asJson();

        if (response.isSuccess()) {
            Long taskId = response.getBody().getObject().getLong("id");
            log.info("创建任务成功，ID: {}", taskId);
            return taskId;
        } else {
            log.error("创建任务失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("创建任务失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
        }
    }

    /**
     * 查询任务
     */
    private void queryTask(Long taskId) throws IOException {
        HttpResponse<String> response = Unirest.get(serverUrl + "/webconsole/api/data/" + taskId)
                .asString();

        if (response.isSuccess()) {
            log.info("查询任务成功: {}", response.getBody());
        } else {
            log.error("查询任务失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("查询任务失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
        }
    }

    /**
     * 更新任务
     */
    private void updateTask(Long taskId) throws IOException {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", "REST API 更新后的任务");
        updateData.put("description", "通过REST API更新的任务描述");
        updateData.put("status", "COMPLETED");

        HttpResponse<String> response = Unirest.put(serverUrl + "/webconsole/api/data/" + taskId)
                .body(Map.of("data", updateData, "_version", 2))
                .asString();

        if (response.isSuccess()) {
            log.info("更新任务成功，ID: {}", taskId);
        } else {
            log.error("更新任务失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("更新任务失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
        }
    }

    /**
     * 删除任务
     */
    private void deleteTask(Long taskId) throws IOException {
        HttpResponse<String> response = Unirest.delete(serverUrl + "/webconsole/api/data/" + taskId)
                .asString();

        if (response.isSuccess()) {
            log.info("删除任务成功，ID: {}", taskId);
        } else {
            log.error("删除任务失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("删除任务失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
        }
    }

    /**
     * 演示分页查询
     */
    private void testPageQuery() throws IOException {
        Map<String, Object> queryData = new HashMap<>();
        queryData.put("page", 0);
        queryData.put("size", 10);

        HttpResponse<String> response = Unirest.post(serverUrl + "/webconsole/api/data/query/HelloTask/page")
                .body(queryData)
                .asString();

        if (response.isSuccess()) {
            log.info("分页查询成功: {}", response.getBody());
        } else {
            log.error("分页查询失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("分页查询失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
        }
    }

    /**
     * 演示条件查询
     */
    private void testConditionQuery() throws IOException {
        Map<String, Object> queryData = new HashMap<>();
        queryData.put("filters", new Object[]{
                Map.of("field", "status", "operator", "EQUALS", "value", "ACTIVE")
        });

        HttpResponse<String> response = Unirest.post(serverUrl + "/webconsole/api/data/query/HelloTask")
                .body(queryData)
                .asString();

        if (response.isSuccess()) {
            log.info("条件查询成功: {}", response.getBody());
        } else {
            log.error("条件查询失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("条件查询失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
        }
    }

    /**
     * 查询类型定义
     */
    private void queryTypeDefinitions() throws IOException {
        HttpResponse<String> response = Unirest.get(serverUrl + "/webconsole/api/metadata/types")
                .asString();

        if (response.isSuccess()) {
            log.info("查询类型定义成功: {}", response.getBody());
        } else {
            log.error("查询类型定义失败，状态码: {}, 响应体: {}", response.getStatus(), response.getBody());
            throw new RuntimeException("查询类型定义失败，状态码: " + response.getStatus() + ", 响应体: " + response.getBody());
        }
    }
}