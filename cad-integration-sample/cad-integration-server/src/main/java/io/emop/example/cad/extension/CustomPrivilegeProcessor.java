package io.emop.example.cad.extension;

import io.emop.cad.api.CadContext;
import io.emop.cad.api.extension.ItemPrivilegeProcessor;
import io.emop.cad.model.ItemEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 权限处理扩展示例
 * 演示如何自定义ItemEntity的权限设置逻辑
 */
@Slf4j
@Component
public class CustomPrivilegeProcessor implements ItemPrivilegeProcessor {
    
    @Override
    public void processPrivilege(ItemEntity item) {
        log.debug("=== 定制化：处理权限 ===");
        
        if (!item.hasItemRevision()) {
            // 新建Item，设置默认权限
            item.setReadable(true);
            item.setWritable(true);
            item.setCopyable(true);
            log.debug("ItemCode [{}] 新建Item，设置默认权限", item.getItemCode());
            return;
        }
        
        // 获取当前状态和用户
        String state = item.getItemRevision().currentState().getName();
        String currentUser = CadContext.currentUsername();
        Long creator = item.getItemRevision().get_creator();
        
        log.debug("ItemCode [{}] 状态: {}, 当前用户: {}, 创建者: {}", 
            item.getItemCode(), state, currentUser, creator);
        
        // 示例1：基于状态设置权限
        if ("Released".equals(state)) {
            // 已发布状态：只读，可复制
            item.setReadable(true);
            item.setWritable(false);
            item.setCopyable(true);
            log.debug("ItemCode [{}] 已发布状态，设置为只读", item.getItemCode());
        } else if ("InWork".equals(state)) {
            // 工作中状态：只有所有者可以编辑
            boolean isOwner = currentUser.equals(creator);
            item.setReadable(true);
            item.setWritable(isOwner);
            item.setCopyable(true);
            log.debug("ItemCode [{}] 工作中状态，所有者: {}, 可编辑: {}", 
                item.getItemCode(), isOwner, isOwner);
        } else {
            // 其他状态：默认可编辑
            item.setReadable(true);
            item.setWritable(true);
            item.setCopyable(true);
        }
        
        // 示例2：根据CAD客户端类型设置权限
        String client = CadContext.currentClient();
        if ("Creo".equals(client)) {
            // Creo客户端的特殊处理
            // 例如：某些类型的文件不允许复制
            if (item.getModelFile() != null && 
                item.getModelFile().getName().endsWith(".drw")) {
                item.setCopyable(false);
                log.debug("ItemCode [{}] Creo图纸文件，禁止复制", item.getItemCode());
            }
        }
        
        // 示例3：根据Item类型设置权限
        // 可以根据业务需求添加更多规则
    }
}
