package io.emop.example.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.emop.model.common.UserContext;
import io.emop.service.S;
import io.emop.service.api.dsl.DSLExecutionService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

/**
 * DSL 演示服务
 * 演示如何使用 DSL 进行数据操作
 */
@Slf4j
public class DslTestService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serverUrl;

    public DslTestService(String serverUrl) {
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.serverUrl = serverUrl;
    }

    /**
     * 执行所有DSL演示
     */
    public void runAll() {
        log.info("=== 开始 DSL 演示 ===");

        // 创建对象
        testCreateObject();

        // 更新对象
        testUpdateObject();

        // 删除对象
        testDeleteObject();

        log.info("=== DSL 演示完成 ===");
    }


    /**
     * 演示创建对象
     */
    private void testCreateObject() {
        String dsl = """
                create object HelloTask {
                    name: "DSL演示任务",
                    description: "通过DSL创建的演示任务",
                    status: "PENDING",
                    code: "%s",
                    revId: "A",
                    title: "%s",
                }
                """;

        dsl = String.format(dsl, "T-" + System.currentTimeMillis(), "DSL演示任务 T-" + System.currentTimeMillis());
        executeDsl("创建对象", dsl);
    }

    /**
     * 演示更新对象
     */
    private void testUpdateObject() {
        String dsl = """
                update object HelloTask where name = 'DSL演示任务' {
                    description: "通过DSL更新的任务描述",
                    status: "COMPLETED"
                }
                """;

        executeDsl("更新对象", dsl);
    }

    /**
     * 演示删除对象
     */
    private void testDeleteObject() {
        String dsl = """
                delete object HelloTask where name = 'DSL演示任务'
                """;

        executeDsl("删除对象", dsl);
    }

    /**
     * 执行DSL命令
     */
    @SneakyThrows
    private void executeDsl(String operation, String dsl) {
        try {
            UserContext.runAsSystem(() -> {
                S.service(DSLExecutionService.class).execute(dsl);
            });
            log.info("DSL {} 执行成功", operation);
        } catch (Exception e) {
            throw new RuntimeException("DSL " + operation + " 执行失败", e);
        }
    }
}