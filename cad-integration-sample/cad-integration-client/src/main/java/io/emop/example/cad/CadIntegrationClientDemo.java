package io.emop.example.cad;

import io.emop.example.cad.scenario.SaveToEmopScenario;
import io.emop.example.cad.scenario.OpenFromEmopScenario;
import lombok.extern.slf4j.Slf4j;

/**
 * CAD集成客户端演示主程序
 * 演示如何通过REST API与EMOP平台进行CAD数据交互
 */
@Slf4j
public class CadIntegrationClientDemo {

    public static void main(String[] args) {
        log.info("=== CAD Integration Client Demo ===");
        log.info("演示CAD客户端与EMOP平台的集成");
        
        try {
            String scenario = args.length > 0 ? args[0] : "all";
            
            switch (scenario.toLowerCase()) {
                case "save":
                    runSaveScenario();
                    break;
                case "open":
                    runOpenScenario();
                    break;
                case "all":
                default:
                    runSaveScenario();
                    log.info("\n\n");
                    runOpenScenario();
                    break;
            }
            
            log.info("\n=== 演示完成 ===");
        } catch (Exception e) {
            log.error("演示执行失败", e);
            System.exit(1);
        }
    }
    
    /**
     * 运行保存到EMOP场景
     */
    private static void runSaveScenario() {
        log.info("\n>>> 场景1：保存到EMOP <<<");
        SaveToEmopScenario scenario = new SaveToEmopScenario();
        scenario.execute();
    }
    
    /**
     * 运行从EMOP打开场景
     */
    private static void runOpenScenario() {
        log.info("\n>>> 场景2：从EMOP打开 <<<");
        OpenFromEmopScenario scenario = new OpenFromEmopScenario();
        scenario.execute();
    }
}
