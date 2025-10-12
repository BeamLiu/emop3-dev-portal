package io.emop.example.cad;

import io.emop.service.S;
import io.emop.service.registry.ServiceRegistry;
import io.emop.spring.serviceprovider.SpringResourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * CAD集成定制化示例应用
 * 
 */
@SpringBootApplication
@Slf4j
@ComponentScan(value = "io.emop")
public class CustomCadIntegrationStarter {

    public static void main(String[] args) {
        ServiceRegistry.initAllServices();
        ConfigurableApplicationContext cac = SpringApplication.run(CustomCadIntegrationStarter.class, args);
        ServiceRegistry.register(S.ExternalResourceProvider.class, new SpringResourceProvider(cac));
        log.info("CustomCadIntegrationStarter started, swagger: http://localhost:891/cad-integration/api");
    }
}
