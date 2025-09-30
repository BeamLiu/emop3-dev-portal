package io.emop.integrationtest;

import io.emop.integrationtest.domain.SampleDataset;
import io.emop.integrationtest.domain.SampleDocument;
import io.emop.integrationtest.usecase.common.GraphQueryTest;
import io.emop.model.common.AbstractModelObject;
import io.emop.model.common.UserContext;
import io.emop.model.metadata.TypeDefinition;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.metadata.MetadataService;
import io.emop.service.api.metadata.MetadataUpdateService;
import io.emop.service.registry.ServiceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 集成测试套件
 * 自动扫描 usecase 包下的所有集成测试
 */
@Suite
@SuiteDisplayName("EMOP 集成测试套件")
@SelectClasses({
        // 按顺序指定需要优先执行的测试类
        GraphQueryTest.class, //放在前面执行，后面的性能测试等会导致同步延时增加从而影响测试用例
})
@SelectPackages({
        "io.emop.integrationtest.usecase",
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class EmopIntegrationTestSuite implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        suiteSetup();
    }

    private void suiteSetup() {
        UserContext.setCurrentUser(UserContext.SYSTEM);
        // 设置发现SPI
        ServiceRegistry.initAllServices();
        initCodePattern();
        log.info("=== 开始 EMOP 集成测试套件初始化 ===");
        log.info("自动扫描以下包的集成测试:");
        log.info("  - io.emop.integrationtest.usecase.*");
        log.info("=== EMOP 集成测试套件初始化完成 ===");
    }

    private static void initCodePattern() {
        String docCodePattern = "SAMPLE-DOC-${autoIncrease(scope=\"Rule\",start=\"0000000\",step=\"1\",max=\"9999999\")}";
        TypeDefinition docDefinition = Q.result(TypeDefinition.class)
                .where("name=?", SampleDocument.class.getName()).first();
        docDefinition.modifier().codeGenPattern(docCodePattern);
        S.service(ObjectService.class).save(docDefinition);
        S.withStrongConsistency(() -> S.service(MetadataService.class).reloadTypeDefinitions());

        TypeDefinition modelObjectDefinition = Q.result(TypeDefinition.class)
                .where("name=?", AbstractModelObject.class.getName()).first();
        modelObjectDefinition.modifier()
                .codeGenPattern("${autoIncrease(scope=\"Rule\",start=\"0000000\",step=\"1\",max=\"9999999\")}");
        S.service(MetadataUpdateService.class).createOrUpdateType(modelObjectDefinition);

        String datasetCodePattern = "DOC-${attr(name=\"type\")}-${script(content=\"return modelObject.get('name').substring(0,3);\")}-${date(pattern=\"YYMM\")}-${alpha(scope=\"Element[1]\",start=\"AA\",max=\"ZZZZ\")}";
        TypeDefinition datasetDefinition = Q.result(TypeDefinition.class)
                .where("name=?", SampleDataset.class.getName()).first();
        datasetDefinition.modifier().codeGenPattern(datasetCodePattern);
        S.service(MetadataUpdateService.class).createOrUpdateType(datasetDefinition);
        S.service(MetadataService.class).reloadTypeDefinitions();
    }
}