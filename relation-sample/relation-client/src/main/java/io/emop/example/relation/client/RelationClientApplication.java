package io.emop.example.relation.client;

import io.emop.example.relation.client.demo.*;
import io.emop.service.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 关系演示客户端应用
 * <p>
 * 演示内容:
 * 1. Java API 操作演示
 * 2. REST API 调用演示
 * 3. DSL 数据操作演示
 * 4. XPath 路径操作演示
 */
@Slf4j
public class RelationClientApplication {

    public static void main(String[] args) {
        log.info("启动关系演示客户端...");

        ServiceRegistry.initAllServices();
        try {
            // 1. Java API 演示
            log.info("\n1. === Java API 操作演示 ===");
            new JavaApiDemo().demonstrateJavaApi();

            // 2. REST API 演示
            log.info("\n2. === REST API 调用演示 ===");
            new RestApiDemo().demonstrateRestApi();

            // 3. DSL 演示
            log.info("\n3. === DSL 数据操作演示 ===");
            new DslDemo().demonstrateDsl();

            // 4. XPath 演示
            log.info("\n4. === XPath 路径操作演示 ===");
            new XPathDemo().demonstrateXPath();

            // 5. 数据导入导出演示
            log.info("\n5. === 数据导入导出演示 ===");
            new DataImportExportDemo().demonstrateImportExport();

            log.info("\n=== 所有演示完成 ===");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(1);
        }
        System.exit(0);
    }
}