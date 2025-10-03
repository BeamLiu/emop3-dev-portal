package io.emop.example.relation;

import io.emop.webconsole.ServerWebConsolePortable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 关系演示服务器启动类
 * <p>
 * 启动后可以访问:
 * - Swagger API: http://localhost:870/webconsole/api
 */
@Slf4j
@SpringBootApplication
public class RelationServerApplication {

    public static void main(String[] args) {
        log.info("启动EMOP Server服务器...");

        // 使用EMOP WebConsole内嵌方式启动
        ServerWebConsolePortable.startup(args);

        log.info("EMOP Server服务器启动完成!");
        log.info("访问地址:");
        log.info("  - Swagger API: http://localhost:870/webconsole/api");
    }
}