package io.emop.example.cad.service;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import io.emop.model.query.tuple.Tuple;
import io.emop.model.query.tuple.Tuple2;

/**
 * ZIP文件重组服务
 * 根据服务器返回的fileId重组ZIP文件结构
 */
@Slf4j
public class ZipReorganizer {

    /**
     * 重组ZIP文件
     * 
     * @param originalZipFile  原始ZIP文件
     * @param rawFileIdMapping 文件名到fileId的映射
     * @return 重组后的ZIP文件（包含 __file_metadata__.json）
     * @throws IOException 读取或写入文件时抛出异常
     */
    public File reorganizeZip(File originalZipFile, Map<String, Long> rawFileIdMapping) throws IOException {
        log.info("开始重组ZIP文件");

        Map<String, Long> newFileIdMapping = new HashMap<>();
        rawFileIdMapping = removeLastVersionNumber(rawFileIdMapping);
        // 创建临时文件
        File newZipFile = File.createTempFile("reorganized-", ".zip");

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(originalZipFile));
                ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(newZipFile))) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String originalPath = entry.getName();
                String filename = extractFilename(originalPath);
                Long newFileId = null;
                if (filename.endsWith(".jpg")) {
                    // 对于jpg文件，去掉扩展名再查找映射
                    newFileId = rawFileIdMapping.get(
                            removeLastVersionNumber(filename.substring(0, filename.length() - 4)));
                } else {
                    newFileId = rawFileIdMapping.get(removeLastVersionNumber(filename));
                }

                if (newFileId == null) {
                    throw new RuntimeException("未找到文件的fileId映射: " + filename);
                }

                // 构建新路径: fileId/filename
                String newPath = newFileId + "/" + filename;

                ZipEntry newEntry = new ZipEntry(newPath);
                zos.putNextEntry(newEntry);
                // 只有主模型才需要更新元数据，也就是说附加文件不需要
                if (!filename.endsWith(".jpg")) {
                    newFileIdMapping.put(newPath, newFileId);
                }
                log.debug("Added entry: {}", newPath);

                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }

                zos.closeEntry();
                zis.closeEntry();
            }

            // 添加元数据配置文件到ZIP
            addMetadataFileToZip(zos, newFileIdMapping);
        }

        log.info("ZIP文件重组完成，包含 {} 个文件的元数据配置", newFileIdMapping.size());
        return newZipFile;
    }

    /**
     * 将元数据配置文件添加到ZIP中
     */
    private void addMetadataFileToZip(ZipOutputStream zos, Map<String, Long> fileIdMapping) throws IOException {
        String metadataJson = generateFileMetadataConfig(fileIdMapping);
        
        ZipEntry metadataEntry = new ZipEntry("__file_metadata__.json");
        zos.putNextEntry(metadataEntry);
        zos.write(metadataJson.getBytes("UTF-8"));
        zos.closeEntry();
        
        log.info("已添加 __file_metadata__.json 到ZIP文件");
    }

    /**
     * 移除文件名最后的版本号（如 ".1", ".2" 等），如果存在
     */
    public static Map<String, Long> removeLastVersionNumber(Map<String, Long> fileIdMapping) {
        Map<String, Long> cleanedMapping = new HashMap<>();
        for (Map.Entry<String, Long> entry : fileIdMapping.entrySet()) {
            String cleanedKey = removeLastVersionNumber(entry.getKey());
            cleanedMapping.put(cleanedKey, entry.getValue());
        }
        return cleanedMapping;
    }

    /**
     * 移除文件名最后的版本号（如 ".1", ".2" 等），如果存在
     */
    public static String removeLastVersionNumber(String filename) {
        if (filename == null) {
            return null;
        }
        // 使用正则移除类似 ".1", ".2" 结尾的版本号
        return filename.replaceAll("(\\.\\d+)$", "");
    }

    /**
     * 从路径中提取文件名
     */
    private String extractFilename(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * 生成fileMetadataConfig JSON
     */
    public String generateFileMetadataConfig(Map<String, Long> fileIdMapping) {
        Map<String, Map<String, Object>> config = new HashMap<>();

        for (Map.Entry<String, Long> entry : fileIdMapping.entrySet()) {
            String path = entry.getKey();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("fileId", entry.getValue());
            metadata.put("additionalProperties", Map.of("additionalSampleProp", System.currentTimeMillis()));
            config.put(path, metadata);
        }

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException("生成fileMetadataConfig失败", e);
        }
    }
}
