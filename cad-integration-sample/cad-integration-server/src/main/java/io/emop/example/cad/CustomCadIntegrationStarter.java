package io.emop.example.cad;

import io.emop.model.common.UserContext;
import io.emop.service.S;
import io.emop.service.api.dsl.DSLExecutionService;
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
        init();
        log.info("CustomCadIntegrationStarter started, swagger: http://localhost:891/cad-integration/api");
    }

    public static void init() {
        UserContext.runAsSystem(() -> {
            S.service(DSLExecutionService.class).execute(
                """
                        create type sample.CADIntegrationCust extends CADComponent{
                            schema: SAMPLE
                            tableName: CAD_Integration_Cust
                        } if not exists

                        update type CADIntegrationCust {
                            codeGenPattern: "CADCUST-${date(pattern='YYMM')}-${autoIncrease(scope='Rule',start='000001',max='999999')}"
                        }

                        create type sample.CreoCADBomLine extends BomLine {
                            //必须覆盖父类children，返回对象为CreoCADBomLine
                            -> sample.CreoCADBomLine[] as children {
                                foreignKey: parentId
                            }
                            //必须覆盖父类parent，返回对象为CreoCADBomLine
                            -> sample.CreoCADBomLine as `parent` {
                                foreignKey: parentId
                            }
                            multilang {
                                name.zh_CN: "CREO集成BOM行"
                                name.en_US: "CREO BOM Line"
                                description.zh_CN: "CREO集成BOM行，单独存储以达到定制化和分表的目的"
                                description.en_US: "CREO CAD BOM Structure Line, stored separately for customization and partitioning"
                            }
                            table: CREO_CAD_BomLine
                            schema: SAMPLE
                        } if not exists


                        create type sample.CreoCADBomView extends BomView {
                            //必须覆盖父类topline，返回对象为CreoCADBomView
                            -> sample.CreoCADBomLine as topline {
                                foreignKey: toplineId
                            }
                            multilang {
                                name.zh_CN: "CREO集成BOM视图"
                                name.en_US: "CREO BOM View"
                                description.zh_CN: "CREO集成BOM视图，单独存储以达到定制化和分表的目的"
                                description.en_US: "CREO CAD BOM View, stored separately for customization and partitioning"
                            }
                            table: CREO_CAD_BomView
                            schema: SAMPLE
                        } if not exists
                        """);
        });
    }
}
