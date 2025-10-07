package io.emop.example.cad.service;

import io.emop.example.cad.model.ItemEntity;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ID映射管理服务
 * 从compare响应中提取文件名到fileId的映射关系
 */
@Slf4j
public class IdMappingService {

    /**
     * 从ItemEntity列表中提取文件ID映射
     */
    public Map<String, Long> extractFileIdMapping(List<ItemEntity> itemEntities) {
        Map<String, Long> mapping = new HashMap<>();

        for (ItemEntity entity : itemEntities) {
            if (entity.getModelFile() != null && entity.getModelFile().getId() != null) {
                String filename = entity.getModelFile().getName();
                Long fileId = entity.getModelFile().getId();
                mapping.put(filename, fileId);
                log.debug("映射: {} -> {}", filename, fileId);
            }
        }

        log.info("提取到 {} 个文件ID映射", mapping.size());
        return mapping;
    }


    /**
     * 收集所有文件ID
     */
    public void collectFileIds(ItemEntity entity, List<Long> fileIds) {
        if (entity.getModelFile() != null && entity.getModelFile().getId() != null) {
            fileIds.add(entity.getModelFile().getId());
        }

        if (entity.getDrwFiles() != null) {
            entity.getDrwFiles().stream()
                    .filter(f -> f.getId() != null)
                    .forEach(f -> fileIds.add(f.getId()));
        }

        if (entity.getPdfFiles() != null) {
            entity.getPdfFiles().stream()
                    .filter(f -> f.getId() != null)
                    .forEach(f -> fileIds.add(f.getId()));
        }

        if (entity.getStepFiles() != null) {
            entity.getStepFiles().stream()
                    .filter(f -> f.getId() != null)
                    .forEach(f -> fileIds.add(f.getId()));
        }

        if (entity.getJtFiles() != null) {
            entity.getJtFiles().stream()
                    .filter(f -> f.getId() != null)
                    .forEach(f -> fileIds.add(f.getId()));
        }
    }
}
