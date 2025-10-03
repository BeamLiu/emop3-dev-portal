package io.emop.example.client;

import io.emop.example.client.service.DslTestService;
import io.emop.example.client.service.RestApiTestService;
import io.emop.example.client.service.RpcTestService;
import io.emop.service.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Hello EMOP Client 应用程序
 * 演示如何通过REST API、RPC和DSL调用EMOP服务
 */
@Slf4j
public class HelloClientApplication {

    public static void main(String[] args) {
        log.info("=== Hello EMOP Client 演示开始 ===");
        ServiceRegistry.initAllServices();
        try {
            // 1. REST API 演示
            log.info("--- 开始 REST API 演示 ---");
            RestApiTestService restApiTestService = new RestApiTestService("http://localhost:870");
            restApiTestService.runAll();
            log.info("--- REST API 演示完成 ---");

            // 2. RPC 服务演示
            log.info("--- 开始 RPC 服务演示 ---");
            RpcTestService rpcTestService = new RpcTestService();
            rpcTestService.runAll();
            log.info("--- RPC 服务演示完成 ---");

            // 3. DSL 数据操作演示
            log.info("--- 开始 DSL 数据操作演示 ---");
            DslTestService dslTestService = new DslTestService("http://localhost:870");
            dslTestService.runAll();
            log.info("--- DSL 数据操作演示完成 ---");

            log.info("=== Hello EMOP Client 所有演示完成 ===");
        } catch (Exception e) {
            log.error("客户端演示执行失败", e);
            System.exit(1);
        }
        System.exit(0);
    }
}