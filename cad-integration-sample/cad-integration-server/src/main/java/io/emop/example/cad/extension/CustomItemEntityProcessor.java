package io.emop.example.cad.extension;

import io.emop.cad.api.CadContext;
import io.emop.cad.api.extension.CadItemEntityProcessor;
import io.emop.cad.model.ItemEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ItemEntity处理扩展示例
 * 演示如何在关键节点插入自定义逻辑
 */
@Slf4j
@Component
public class CustomItemEntityProcessor implements CadItemEntityProcessor {
    
    @Override
    public void beforeCompare(List<ItemEntity> entities) {
        log.info("=== 定制化：对比前处理 ===");
        log.info("CAD客户端: {}", CadContext.currentClient());
        log.info("当前用户: {}", CadContext.currentUsername());
        log.info("ItemEntity数量: {}", entities.size());
        
        // 示例：为所有ItemEntity添加自定义属性
        for (ItemEntity entity : entities) {
            // 可以在这里添加自定义逻辑
            // 例如：设置默认值、补充信息等
            log.debug("处理ItemEntity: {}", entity.getItemCode());
        }
    }
    
    @Override
    public void afterCompare(List<ItemEntity> entities) {
        log.info("=== 定制化：对比后处理 ===");
        
        // 示例：统计不同UseType的数量
        long createCount = entities.stream()
            .filter(e -> e.getUseType() != null && e.getUseType().name().equals("CREATE"))
            .count();
        long updateCount = entities.stream()
            .filter(e -> e.getUseType() != null && e.getUseType().name().equals("OVERRIDE"))
            .count();
        long referenceCount = entities.stream()
            .filter(e -> e.getUseType() != null && e.getUseType().name().equals("REFERENCE"))
            .count();
            
        log.info("对比结果统计 - 新建: {}, 更新: {}, 引用: {}", createCount, updateCount, referenceCount);
    }
    
    @Override
    public void beforeSave(List<ItemEntity> entities) {
        log.info("=== 定制化：保存前处理 ===");
        
        // 示例：保存前的最后验证
        for (ItemEntity entity : entities) {
            // 可以在这里进行业务规则验证
            // 如果验证失败，抛出异常即可阻止保存
        }
    }
    
    @Override
    public void afterSave(List<ItemEntity> entities) {
        log.info("=== 定制化：保存后处理 ===");
        
        // 示例：保存后发送通知、触发工作流等
        // 这里可以调用外部系统API
    }
    
    @Override
    public void afterLoad(List<ItemEntity> entities) {
        log.info("=== 定制化：加载后处理 ===");
        log.info("从EMOP加载了 {} 个ItemEntity", entities.size());
        
        // 示例：补充额外信息
        for (ItemEntity entity : entities) {
            // 可以从其他系统加载额外数据
            // 例如：ERP系统的成本信息、库存信息等
        }
    }
}
