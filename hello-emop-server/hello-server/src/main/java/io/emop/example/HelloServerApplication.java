package io.emop.example;

import io.emop.webconsole.ServerWebConsolePortable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

/**
 * Hello EMOP Server 启动类
 * 演示如何在IDE中直接启动包含自定义插件的EMOP服务器
 */
@SpringBootApplication
@Slf4j
@ComponentScan(value = "io.emop")
@EnableRedisRepositories
public class HelloServerApplication {

    public static void main(String[] args) {
        ServerWebConsolePortable.startup(args);
    }
}