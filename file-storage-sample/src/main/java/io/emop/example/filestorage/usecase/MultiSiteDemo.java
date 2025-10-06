package io.emop.example.filestorage.usecase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.emop.service.config.EMOPConfig;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 异地卷支持演示
 * 演示多站点架构下的站点选择和文件上传：
 * 1. 基于用户属性选择站点
 * 2. 显式指定站点
 * 3. 使用选择的站点上传文件
 */
@Slf4j
public class MultiSiteDemo {

    // 普通的 rest 请求仍然去到中心节点，这里选择不经过网关，避免登录，正式代码需要把端口去掉以经过网关验证用户登录有效性
    private static final String MINIO_PROXY_BASE_URL = "http://minioproxy-" +
            EMOPConfig.getInstance().getString("EMOP_DOMAIN", "dev.emop.emopdata.com") + ":9003/minioproxy/api";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MultiSiteDemo() {
        Unirest.config()
                .connectTimeout(30000)
                .socketTimeout(60000);
    }

    /**
     * 运行异地卷支持演示
     */
    public void runDemo() {
        log.info("\n=== 异地卷支持演示 ===");
        log.info("说明：无论单站点还是多站点，代码逻辑是一样的，只是配置不同");
        log.info("提示：如果需要缓存站点选择结果以提升性能，可以在应用层实现");
        
        try {
            // 1. 基于用户属性选择站点并上传文件
            demonstrateUserBasedSelection();

            // 2. 显式指定站点并上传文件
            demonstrateExplicitSiteSelection();

        } catch (Exception e) {
            log.error("异地卷支持演示失败", e);
            throw new RuntimeException("异地卷支持演示失败", e);
        }
    }

    /**
     * 演示1：基于用户属性的站点选择
     * 场景：根据用户所属组织自动选择最优站点
     */
    public void demonstrateUserBasedSelection() throws Exception {
        log.info("\n--- 场景1：基于用户属性的站点选择 ---");

        // 构建选择上下文
        Map<String, Object> context = new HashMap<>();
        context.put("logicalBucket", "cad");

        // 添加用户属性（例如：CAD类型）, 当前登录用户的信息是不需要从客户端添加的
        Map<String, Object> userAttrs = new HashMap<>();
        userAttrs.put("cadType", "solidworks");
        context.put("userAttributes", userAttrs);

        log.info("请求参数: logicalBucket=cad, cadType=solidworks");

        // 调用站点选择 API
        SiteSelectionResult result = selectSite(context);

        log.info("站点选择结果:");
        log.info("  - Site ID: {}", result.siteId);
        log.info("  - Site Name: {}", result.siteName);
        log.info("  - Proxy URL: {}", result.proxyUrl);
        log.info("  - Actual Bucket: {} (从逻辑bucket 'cad' 映射)", result.actualBucket);
        log.info("  - Matched Rule: {}", result.matchedRule);

        // 使用选择的站点上传文件
        uploadFile(result.proxyUrl, result.actualBucket, "user-based-test.pdf");
    }

    /**
     * 演示2：显式指定站点
     * 场景：用户手动选择站点（例如：切换到中心站点）
     */
    public void demonstrateExplicitSiteSelection() throws Exception {
        log.info("\n--- 场景2：显式指定站点 ---");

        // 构建选择上下文，客户端自主显式指定站点 explicitSiteId
        Map<String, Object> context = new HashMap<>();
        context.put("explicitSiteId", "default");
        context.put("logicalBucket", "cad");

        log.info("请求参数: explicitSiteId=default, logicalBucket=cad");

        // 调用站点选择 API
        SiteSelectionResult result = selectSite(context);

        log.info("站点选择结果:");
        log.info("  - Site ID: {}", result.siteId);
        log.info("  - Proxy URL: {}", result.proxyUrl);
        log.info("  - Actual Bucket: {}", result.actualBucket);
    }

    /**
     * 调用站点选择 API
     */
    private SiteSelectionResult selectSite(Map<String, Object> context) throws IOException {
        String url = MINIO_PROXY_BASE_URL + "/site-selection/select";

        HttpResponse<String> response = Unirest.post(url)
                .header("Content-Type", "application/json")
                .header("x-user", "{\"userId\":-1,\"authorities\":[\"ADMIN\"]}")
                .body(objectMapper.writeValueAsString(context))
                .asString();

        if (response.isSuccess()) {
            JsonNode json = objectMapper.readTree(response.getBody());
            return new SiteSelectionResult(
                    json.get("siteId").asText(),
                    json.get("siteName").asText(),
                    json.get("proxyUrl").asText(),
                    json.get("actualBucket").asText(),
                    json.get("reason").asText(),
                    json.get("matchedRule").asText()
            );
        } else {
            throw new IOException("站点选择失败: HTTP " + response.getStatus() + ": " + response.getBody());
        }
    }

    /**
     * 使用选择的站点上传文件
     */
    private void uploadFile(String proxyUrl, String bucket, String filename) throws Exception {
        log.info("\n使用选择的站点上传文件:");
        log.info("  - Proxy URL: {}", proxyUrl);
        log.info("  - Bucket: {}", bucket);
        log.info("  - Filename: {}", filename);

        // 1. 获取上传票据
        String uploadTicketUrl = proxyUrl + "/file/direct-upload-ticket"
                + "?bucket=" + bucket
                + "&targetPath=remote-site-demo"
                + "&filename=" + filename
                + "&expiryMinutes=15";
        log.info("从指定 minio-proxy 中获取上传的ticket : {}", uploadTicketUrl);
        log.info("这里由于默认返回的是经过gateway的地址，就不演示了，具体代码参见 BasicUploadDownloadDemo.java");
    }

    /**
     * 站点选择结果
     */
    @AllArgsConstructor
    private static class SiteSelectionResult {
        String siteId;
        String siteName;
        String proxyUrl;
        String actualBucket;
        String reason;
        String matchedRule;
    }
}
