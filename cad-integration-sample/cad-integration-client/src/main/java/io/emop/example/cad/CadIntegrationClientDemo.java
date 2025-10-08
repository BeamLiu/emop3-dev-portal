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
                    runOpenScenario(args[1]); // 需要提供cadComponentCode参数
                    break;
                case "all":
                default:
                    // 保存并获得根节点图号
                    String rootComponentCode = runSaveScenario();
                    // String rootComponentCode = "CAD-2510-003627"; // 使用固定图号以便多次运行
                    log.info("\n\n");
                    runOpenScenario(rootComponentCode);
                    break;
            }
            
            log.info("\n=== 演示完成 ===");
            System.exit(0);
        } catch (Exception e) {
            log.error("演示执行失败", e);
            System.exit(1);
        }
    }
    
    /**
     * 运行保存到EMOP场景
     */
    private static String runSaveScenario() {
        log.info("\n>>> 场景1：保存到EMOP <<<");
        SaveToEmopScenario scenario = new SaveToEmopScenario();
        String rootCode = scenario.execute();
        log.info("根节点图号: {}", rootCode);
        return rootCode;
    }
    
    /**
     * 运行从EMOP打开场景
     */
    private static void runOpenScenario(String cadComponentCode) {
        log.info("\n>>> 场景2：从EMOP打开 <<<");
        OpenFromEmopScenario scenario = new OpenFromEmopScenario(cadComponentCode);
        scenario.execute();
    }
}
