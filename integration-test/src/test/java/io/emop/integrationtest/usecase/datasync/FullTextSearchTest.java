package io.emop.integrationtest.usecase.datasync;

import io.emop.integrationtest.domain.SampleDocument;
import io.emop.model.auth.UserPermissions;
import io.emop.model.common.*;
import io.emop.model.permission.PermissionConfig;
import io.emop.model.query.Pagination;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleFolder;
import io.emop.service.S;
import io.emop.service.api.permission.PermissionConfigDeploymentService;
import io.emop.service.api.data.FullTextSearchService;
import io.emop.service.api.data.ObjectService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;

import static io.emop.integrationtest.util.Assertion.*;
import static io.emop.integrationtest.util.TestUserHelper.createFullTextSearchTestUsers;
import static io.emop.integrationtest.util.TestUserHelper.createTestAdminSearchUser;

/**
 * PostgreSQL pg_bigm 全文检索测试用例
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullTextSearchTest {

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
        prepareTestData();
    }

    @AfterAll
    static void clean() {
        UserContext.runAsSystem(() -> S.service(PermissionConfigDeploymentService.class).removePermissionConfig(ItemRevision.class.getName()));
    }


    @Test
    @Order(1)
    public void testSearchAcrossAllCaches_SimpleKeyword() {
        testSearchAcrossAllCaches_SimpleKeywordImpl();
    }

    @Test
    @Order(2)
    public void testSearchAcrossAllCaches_ExactCode() {
        testSearchAcrossAllCaches_ExactCodeImpl();
    }

    @Test
    @Order(3)
    public void testSearchAcrossAllCaches_CombinedTerms() {
        testSearchAcrossAllCaches_CombinedTermsImpl();
    }

    @Test
    @Order(4)
    public void testSearchAfterDeletion() {
        testSearchAfterDeletionImpl();
    }

    @Test
    @Order(5)
    public void testGetSuggestions() {
        testGetSuggestionsImpl();
    }

    @Test
    @Order(6)
    public void testSearchWithPartialMatch() {
        testSearchWithPartialMatchImpl();
    }

    @Test
    @Order(7)
    public void testSearchWithNonExistentTerm() {
        testSearchWithNonExistentTermImpl();
    }

    @Test
    @Order(8)
    public void testSearchWithEmptyString() {
        testSearchWithEmptyStringImpl();
    }

    @Test
    @Order(9)
    public void testSearchWithNull() {
        testSearchWithNullImpl();
    }

    @Test
    @Order(10)
    public void testSearchWithAggregations() {
        testSearchWithAggregationsImpl();
    }

    @Test
    @Order(11)
    public void testAggregationWithSpecificFields() {
        testAggregationWithSpecificFieldsImpl();
    }

    @Test
    @Order(12)
    public void testAggregationAfterDataChanges() {
        testAggregationAfterDataChangesImpl();
    }

    @Test
    @Order(13)
    public void testHighlightMultipleKeywords() {
        testHighlightMultipleKeywordsImpl();
    }

    @Test
    @Order(14)
    public void testHighlightWithSpecialCharacters() {
        testHighlightWithSpecialCharactersImpl();
    }

    @Test
    @Order(15)
    public void testHighlightOverlappingKeywords() {
        testHighlightOverlappingKeywordsImpl();
    }

    @Test
    @Order(16)
    public void testHighlightLongContent() {
        testHighlightLongContentImpl();
    }

    @Test
    @Order(17)
    public void testPaginationSearch() {
        testPaginationSearchImpl();
    }

    @Test
    @Order(18)
    public void testSortingSearch() {
        testSortingSearchImpl();
    }

    @Test
    @Order(19)
    public void testPerformanceWithLargeDataset() {
        testPerformanceWithLargeDatasetImpl();
    }

    @Test
    @Order(20)
    public void testAdvancedFilters() {
        testAdvancedFiltersImpl();
    }

    @Test
    @Order(21)
    public void testLogicModelObjectVsObjectRef() {
        testLogicModelObjectVsObjectRefImpl();
    }

    @Test
    @Order(22)
    public void testRevisionableObjectHandling() {
        testRevisionableObjectHandlingImpl();
    }

    @Test
    @Order(23)
    public void testNonRevisionableObjectHandling() {
        testNonRevisionableObjectHandlingImpl();
    }

    @Test
    @Order(24)
    public void testMixedSearchResults() {
        testMixedSearchResultsImpl();
    }

    @Test
    @Order(25)
    public void testVersionRuleResolution() {
        testVersionRuleResolutionImpl();
    }

    @Test
    @Order(26)
    public void testPermissionFiltering() {
        testPermissionFilteringImpl();
    }

    private void prepareTestData() {
        // 清理旧数据
        Q.result(ItemRevision.class).where("code like ?", "ITEM-%").delete();
        Q.result(ItemRevision.class).where("code like ?", "ENGINE-%").delete();
        Q.result(SampleFolder.class).where("name in (?,?,?)", "设计文档", "测试报告", "技术文档").delete();

        // 准备ItemRevision测试数据
        ItemRevision revision1 = ItemRevision.newModel("ITEM-001", "A");
        revision1.setName("测试零件");
        revision1.setDescription("测试零件设计图纸第一版");
        S.service(ObjectService.class).save(revision1);

        ItemRevision revision2 = ItemRevision.newModel("ITEM-002", "B");
        revision2.setName("发动机组件");
        revision2.setDescription("发动机组件设计图纸");
        S.service(ObjectService.class).save(revision2);

        ItemRevision revision3 = ItemRevision.newModel("ENGINE-001", "C");
        revision3.setName("新型发动机");
        revision3.setDescription("新型发动机测试报告");
        S.service(ObjectService.class).save(revision3);

        // 准备更多测试数据用于分页测试
        for (int i = 3; i <= 25; i++) {
            ItemRevision rev = ItemRevision.newModel("ITEM-" + String.format("%03d", i), "A");
            rev.setName("批量测试零件" + i);
            rev.setDescription("用于分页测试的零件描述 " + i);
            S.service(ObjectService.class).save(rev);
        }

        // 准备Folder测试数据
        SampleFolder folder1 = SampleFolder.newModel();
        folder1.setName("设计文档");
        folder1.setDescription("包含各种设计图纸的文件夹");
        S.service(ObjectService.class).save(folder1);

        SampleFolder folder2 = SampleFolder.newModel();
        folder2.setName("测试报告");
        folder2.setDescription("发动机相关的测试报告文件夹");
        S.service(ObjectService.class).save(folder2);

        SampleFolder folder3 = SampleFolder.newModel();
        folder3.setName("技术文档");
        folder3.setDescription("技术规范和设计标准文档");
        S.service(ObjectService.class).save(folder3);

        // 立刻同步
        S.service(FullTextSearchService.class).syncImmediately();
    }

    private void waitForIndexing(long t) {
        try {
            log.info("等待全文检索同步....");
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitForIndexing() {
        waitForIndexing(5100);
    }

    // ============== 基础搜索测试 ==============

    private void testSearchAcrossAllCaches_SimpleKeywordImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class).search("发动机", Pagination.of(0, 20));

        assertNotNull(results);
        assertNotNull(results.getItems());
        assertTrue(results.getTotalHits() >= 3);

        // 验证搜索结果包含预期项目
        boolean foundEngineComponent = false;
        boolean foundNewEngine = false;
        boolean foundTestReport = false;

        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            if (item.getObjectType().equals(ItemRevision.class.getName())) {
                ItemRevision rev = (ItemRevision) item.retrieveModelObject();
                if ("ITEM-002".equals(rev.getCode())) foundEngineComponent = true;
                if ("ENGINE-001".equals(rev.getCode())) foundNewEngine = true;
            } else if (item.getObjectType().equals(SampleFolder.class.getName())) {
                SampleFolder folder = (SampleFolder) item.retrieveModelObject();
                if ("测试报告".equals(folder.getName())) foundTestReport = true;
            }
        }

        assertTrue(foundEngineComponent);
        assertTrue(foundNewEngine);
        assertTrue(foundTestReport);
    }

    private void testSearchAcrossAllCaches_ExactCodeImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class).search("ITEM-001");

        assertNotNull(results);
        assertTrue(results.getTotalHits() >= 1);

        boolean foundCorrectItem = false;
        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            if (item.retrieveModelObject() instanceof ItemRevision) {
                ItemRevision rev = (ItemRevision) item.retrieveModelObject();
                if ("ITEM-001".equals(rev.getCode())) {
                    foundCorrectItem = true;
                    break;
                }
            }
        }
        assertTrue(foundCorrectItem);
    }

    private void testSearchAcrossAllCaches_CombinedTermsImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class).search("设计 图纸");

        assertNotNull(results);
        assertNotNull(results.getItems());

        int itemRevCount = 0;
        int folderCount = 0;

        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            if (item.getObjectType().equals(ItemRevision.class.getName())) {
                itemRevCount++;
            } else if (item.getObjectType().equals(SampleFolder.class.getName())) {
                folderCount++;
            }
        }

        assertTrue(itemRevCount >= 2);
        assertTrue(folderCount >= 1);
    }

    private void testSearchAfterDeletionImpl() {
        // 先确认可以搜索到
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class).search("ITEM-001");
        assertTrue(results.getTotalHits() > 0);

        // 删除数据
        Q.result(ItemRevision.class).where("code=?", "ITEM-001").delete();
        S.service(FullTextSearchService.class).syncImmediately();

        // 再次搜索，确认搜不到了
        results = S.service(FullTextSearchService.class).search("ITEM-001");

        boolean foundDeletedItem = false;
        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            if (item.retrieveModelObject() instanceof ItemRevision) {
                ItemRevision rev = (ItemRevision) item.retrieveModelObject();
                if ("ITEM-001".equals(rev.getCode())) {
                    foundDeletedItem = true;
                    break;
                }
            }
        }
        assertFalse(foundDeletedItem);
    }

    private void testGetSuggestionsImpl() {
        // 测试中文建议
        List<String> suggestions = S.service(FullTextSearchService.class).getSuggestions("测试", 5);
        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());

        suggestions = S.service(FullTextSearchService.class).getSuggestions("发动机", 5);
        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());

        // 测试英文代码建议
        suggestions = S.service(FullTextSearchService.class).getSuggestions("ENGINE", 5);
        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty());

        suggestions = S.service(FullTextSearchService.class).getSuggestions("ITEM", 10);
        assertNotNull(suggestions);
        assertTrue(suggestions.size() > 0);
    }

    private void testSearchWithPartialMatchImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class).search("发动");
        assertNotNull(results);
        assertTrue(results.getTotalHits() >= 2);
    }

    private void testSearchWithNonExistentTermImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class).search("不存在的关键词XYZ");
        assertTrue(results.getTotalHits() == 0);

        results = S.service(FullTextSearchService.class).search("发动机 不存在的关键词XYZ");
        assertTrue(results.getTotalHits() == 0);
    }

    private void testSearchWithEmptyStringImpl() {
        try {
            S.service(FullTextSearchService.class).search("");
            fail("Expected exception for empty search string");
        } catch (Exception e) {
            // Expected exception
        }
    }

    private void testSearchWithNullImpl() {
        try {
            S.service(FullTextSearchService.class).search(null);
            fail("Expected exception for null search string");
        } catch (Exception e) {
            // Expected exception
        }
    }

    // ============== 聚合功能测试 ==============

    private void testSearchWithAggregationsImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("测试", null, true, Pagination.of(0, 10));

        assertNotNull(results);
        assertNotNull(results.getAggregations());

        Map<String, Map<String, Long>> aggregations = results.getAggregations();
        assertTrue(aggregations.containsKey("_objectType"));

        Map<String, Long> objectTypeAggs = aggregations.get("_objectType");
        assertTrue(objectTypeAggs.containsKey(ItemRevision.class.getName()));
    }

    private void testAggregationWithSpecificFieldsImpl() {
        Map<String, Object> filters = new HashMap<>();
        List<String> customAggFields = Arrays.asList("_objectType", "_creator");
        filters.put("aggregationFields", customAggFields);

        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("测试", filters, true, Pagination.of(0, 10));

        Map<String, Map<String, Long>> aggregations = results.getAggregations();
        assertNotNull(aggregations);
        assertTrue(aggregations.containsKey("_objectType"));
    }

    private void testAggregationAfterDataChangesImpl() {
        FullTextSearchService.SearchResult initialResults = S.service(FullTextSearchService.class)
                .search("批量", null, true, Pagination.of(0, 10));

        long initialCount = initialResults.getTotalHits();

        // 添加新数据
        ItemRevision newRevision = ItemRevision.newModel("ITEM-AGG-TEST-" + System.currentTimeMillis(), "A");
        newRevision.setName("聚合测试批量零件");
        newRevision.setDescription("用于测试聚合功能的批量测试零件");
        S.service(ObjectService.class).save(newRevision);

        S.service(FullTextSearchService.class).syncImmediately();

        FullTextSearchService.SearchResult updatedResults = S.service(FullTextSearchService.class)
                .search("批量", null, true, Pagination.of(0, 10));

        assertTrue(updatedResults.getTotalHits() > initialCount);
    }

    // ============ 高亮功能

    /**
     * 测试多关键词高亮功能
     */
    private void testHighlightMultipleKeywordsImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("设计 图纸 发动机", Pagination.of(0, 10));

        assertNotNull(results);
        assertTrue(results.getTotalHits() > 0);

        // 验证高亮结果
        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            Map<String, List<String>> highlights = item.getHighlights();
            assertNotNull(highlights);

            if (highlights.containsKey("content") && !highlights.get("content").isEmpty()) {
                List<String> contentHighlights = highlights.get("content");

                // 检查是否有片段同时包含多个高亮关键词
                boolean foundMultipleHighlights = false;
                for (String snippet : contentHighlights) {
                    log("高亮片段: " + snippet);

                    // 计算高亮标签数量
                    int highlightCount = (snippet.split("<em class=\"highlight\">").length - 1);
                    if (highlightCount >= 2) {
                        foundMultipleHighlights = true;
                        log("发现多关键词高亮片段，包含 " + highlightCount + " 个高亮");
                        break;
                    }
                }

                // 至少应该有一个片段包含多个高亮
                assertTrue("应该有片段同时高亮多个关键词", foundMultipleHighlights);
            }
        }
    }

    /**
     * 测试特殊字符的高亮
     */
    private void testHighlightWithSpecialCharactersImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("C++ HTML", Pagination.of(0, 10));

        assertNotNull(results);
        if (results.getTotalHits() > 0) {
            for (FullTextSearchService.SearchResultItem item : results.getItems()) {
                Map<String, List<String>> highlights = item.getHighlights();
                if (highlights != null && highlights.containsKey("content")) {
                    for (String snippet : highlights.get("content")) {
                        log("特殊字符高亮片段: " + snippet);
                        // 验证特殊字符没有破坏HTML结构
                        assertTrue("高亮片段应该包含正确的HTML标签",
                                snippet.contains("<em class=\"highlight\">"));
                    }
                }
            }
        }
    }

    /**
     * 测试重叠关键词的高亮
     */
    private void testHighlightOverlappingKeywordsImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("图纸 设计图纸", Pagination.of(0, 10));

        assertNotNull(results);
        if (results.getTotalHits() > 0) {
            for (FullTextSearchService.SearchResultItem item : results.getItems()) {
                Map<String, List<String>> highlights = item.getHighlights();
                if (highlights != null && highlights.containsKey("content")) {
                    for (String snippet : highlights.get("content")) {
                        log("重叠关键词高亮片段: " + snippet);
                        // 验证没有嵌套的高亮标签
                        assertFalse("不应该有嵌套的高亮标签",
                                snippet.contains("<em class=\"highlight\"><em class=\"highlight\">"));
                    }
                }
            }
        }
    }

    /**
     * 测试长内容的高亮
     */
    private void testHighlightLongContentImpl() {
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("设计 文档", Pagination.of(0, 10));

        assertNotNull(results);
        if (results.getTotalHits() > 0) {
            for (FullTextSearchService.SearchResultItem item : results.getItems()) {
                Map<String, List<String>> highlights = item.getHighlights();
                if (highlights != null && highlights.containsKey("content")) {
                    List<String> contentHighlights = highlights.get("content");

                    // 验证片段长度合理
                    for (String snippet : contentHighlights) {
                        assertTrue("高亮片段长度应该合理", snippet.length() < 500);
                        assertTrue("高亮片段应该包含实际内容", snippet.trim().length() > 0);

                        // 验证省略号的使用
                        if (snippet.startsWith("...") || snippet.endsWith("...")) {
                            log("包含省略号的片段: " + snippet);
                        }
                    }

                    // 不应该有太多片段
                    assertTrue("高亮片段数量应该合理", contentHighlights.size() <= 5);
                }
            }
        }
    }

    // ============== 分页功能测试（PostgreSQL特有） ==============

    private void testPaginationSearchImpl() {
        // 测试第一页
        FullTextSearchService.SearchResult page1 = S.service(FullTextSearchService.class).search("零件", Pagination.of(0, 5));
        assertNotNull(page1);
        assertEquals(5, page1.getItems().size());
        long totalHits = page1.getTotalHits();
        assertTrue(totalHits > 5);

        // 测试第二页
        FullTextSearchService.SearchResult page2 = S.service(FullTextSearchService.class).search("零件", Pagination.of(2, 5));
        assertNotNull(page2);
        assertEquals(Math.min(5, (int) (totalHits - 5)), page2.getItems().size());
        assertEquals(totalHits, page2.getTotalHits()); // 总数应该一致

        // 验证两页数据不重复
        Set<Long> page1Ids = new HashSet<>();
        Set<Long> page2Ids = new HashSet<>();

        page1.getItems().forEach(item -> page1Ids.add(item.getId()));
        page2.getItems().forEach(item -> page2Ids.add(item.getId()));

        assertTrue(Collections.disjoint(page1Ids, page2Ids)); // 两个集合没有交集
    }

    private void testSortingSearchImpl() {
        // 测试按ID升序排序
        List<Pagination.Sort> ascSorts = Arrays.asList(
                new Pagination.Sort("id", Pagination.Sort.Direction.ASC)
        );
        Pagination ascPagination = new Pagination(10, 0, ascSorts);
        FullTextSearchService.SearchResult ascResults = S.service(FullTextSearchService.class).search("零件", null, false, ascPagination);

        // 测试按ID降序排序
        List<Pagination.Sort> descSorts = Arrays.asList(
                new Pagination.Sort("id", Pagination.Sort.Direction.DESC)
        );
        Pagination descPagination = new Pagination(10, 0, descSorts);
        FullTextSearchService.SearchResult descResults = S.service(FullTextSearchService.class).search("零件", null, false, descPagination);

        assertNotNull(ascResults);
        assertNotNull(descResults);
        assertTrue(ascResults.getItems().size() > 0);
        assertTrue(descResults.getItems().size() > 0);

        // 验证排序结果不同（第一条记录应该不同）
        if (ascResults.getItems().size() > 0 && descResults.getItems().size() > 0) {
            Long ascFirstId = ascResults.getItems().get(0).getId();
            Long descFirstId = descResults.getItems().get(0).getId();
            assertNotEquals(ascFirstId, descFirstId);
        }
    }

    private void testAdvancedFiltersImpl() {
        // 测试对象类型过滤
        Map<String, Object> filters = new HashMap<>();
        filters.put("_objectType", ItemRevision.class.getName());

        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("测试", filters, false);

        assertNotNull(results);
        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            assertEquals(ItemRevision.class.getName(), item.getObjectType());
        }

        // 测试多值过滤
        List<String> multiTypes = Arrays.asList(
                ItemRevision.class.getName(),
                SampleFolder.class.getName()
        );
        filters.put("_objectType", multiTypes);

        results = S.service(FullTextSearchService.class).search("测试", filters, false);
        assertNotNull(results);

        Set<String> foundTypes = new HashSet<>();
        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            foundTypes.add(item.getObjectType());
        }
        assertTrue(foundTypes.size() <= 2); // 最多两种类型
    }

    private void testPerformanceWithLargeDatasetImpl() {
        long startTime = System.currentTimeMillis();

        // 搜索大量数据
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("零件", null, true, Pagination.of(0, 100));

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertNotNull(results);
        assertTrue(results.getTotalHits() > 0);

        // 性能断言：搜索应该在合理时间内完成（5秒内）
        assertTrue("Search took too long: " + duration + "ms", duration < 100);

        log("Performance test - Search duration: " + duration + "ms, Total hits: " + results.getTotalHits());
    }

    // ============== 权限相关测试 ==============

    private void testPermissionFilteringImpl() {
        try {
            // 创建分层级的权限测试数据
            createLayeredPermissionTestData();

            // 确保权限配置已部署
            ensurePermissionConfigDeployed();

            // 等待索引同步以及权限部署
            waitForIndexing();

            // 场景1：全部查不到 - 无权限用户
            testNoPermissionUserSearch();

            // 场景2：部分查到 - 部分权限用户
            testPartialPermissionUserSearch();

            // 场景3：全部查到 - 完全权限用户
            testFullPermissionUserSearch();

            // 场景4：经理用户搜索
            testManagerUserSearch();

            // 场景5：bypass权限检查
            testBypassPermissionSearch();

            // 场景6：权限聚合测试
            testPermissionFilteredAggregation();
        } finally {
            cleanupPermissionTestData();
        }
    }

    // ================ 新增：LogicModelObject 和 ObjectRef 测试 ================

    /**
     * 测试LogicModelObject vs ObjectRef的基本逻辑
     */
    private void testLogicModelObjectVsObjectRefImpl() {
        log("开始测试LogicModelObject vs ObjectRef的基本逻辑");

        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("设计", Pagination.of(0, 20));

        assertNotNull(results);
        assertTrue(results.getTotalHits() > 0);

        int versionableObjectCount = 0;
        int nonVersionableObjectCount = 0;

        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            log("检查搜索结果项: ID=" + item.getId() + ", Type=" + item.getObjectType() + ", Code=" + item.getCode());

            if (item.retrieveIsRevisionable()) {
                // 有code的对象应该是版本对象
                versionableObjectCount++;
                assertNotNull(item.getCode());
                assertNotNull(item.retrieveLogicalModelObject());

                // 验证LogicalModelObject能正确解析
                LogicalModelObject<?> logicalObject = item.retrieveLogicalModelObject();
                assertNotNull(logicalObject);
                assertEquals(item.getCode(), logicalObject.getCode());
                assertEquals(item.getObjectType(), logicalObject.getObjectType());

                // 验证能解析到具体对象
                ModelObject resolvedObject = logicalObject.resolve();
                assertNotNull(resolvedObject);
                assertTrue("解析的对象应该是Revisionable", resolvedObject instanceof Revisionable);

                Revisionable revObject = (Revisionable) resolvedObject;
                assertEquals(item.getCode(), revObject.getCode());

                log("验证版本对象: " + item.getCode() + " -> " + resolvedObject.getClass().getSimpleName());

            } else {
                // 没有code的对象应该是普通对象
                nonVersionableObjectCount++;
                assertTrue("普通对象的code应该为空", item.getCode() == null || item.getCode().trim().isEmpty());
                assertNull(item.retrieveLogicalModelObject());

                // 验证ObjectRef能正确工作
                ObjectRef<?> objectRef = item.retrieveObjectRef();
                assertNotNull(objectRef);
                assertEquals(item.getId(), objectRef.getId());

                // 验证能解析到具体对象
                ModelObject resolvedObject = item.retrieveModelObject();
                assertNotNull(resolvedObject);
                assertEquals(item.getId(), resolvedObject.getId());

                log("验证普通对象: ID=" + item.getId() + " -> " + resolvedObject.getClass().getSimpleName());
            }
        }

        // 验证至少有两种类型的对象
        assertTrue("应该有版本对象（有code）", versionableObjectCount > 0);
        assertTrue("应该有普通对象（无code）", nonVersionableObjectCount > 0);

        log("测试完成 - 版本对象: " + versionableObjectCount + ", 普通对象: " + nonVersionableObjectCount);
    }

    /**
     * 测试版本对象（Revisionable）的处理
     */
    private void testRevisionableObjectHandlingImpl() {
        log("开始测试版本对象处理");

        // 搜索特定的版本对象
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("ITEM-002", Pagination.of(0, 10));

        assertNotNull(results);
        assertTrue(results.getTotalHits() > 0);

        boolean foundTargetItem = false;

        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            if (item.getObjectType().equals(ItemRevision.class.getName()) && "ITEM-002".equals(item.getCode())) {
                foundTargetItem = true;

                // 验证是版本对象
                assertTrue("应该被识别为版本对象", item.retrieveIsRevisionable());
                assertEquals("ITEM-002", item.getCode());

                // 验证LogicalModelObject
                LogicalModelObject<?> logicalObject = item.retrieveLogicalModelObject();
                assertNotNull(logicalObject);
                assertEquals("ITEM-002", logicalObject.getCode());
                assertEquals(ItemRevision.class.getName(), logicalObject.getObjectType());

                // 验证版本规则
                assertEquals(RevisionRule.LATEST_WORKING, logicalObject.getRevisionRule());

                // 验证解析
                ModelObject resolved = item.retrieveModelObject();
                assertNotNull(resolved);
                assertTrue("解析的对象应该是ItemRevision", resolved instanceof ItemRevision);

                ItemRevision itemRevision = (ItemRevision) resolved;
                assertEquals("ITEM-002", itemRevision.getCode());
                assertEquals("发动机组件", itemRevision.getName());

                log("成功验证版本对象: " + itemRevision.getCode() + " - " + itemRevision.getName());
                break;
            }
        }

        assertTrue("应该找到目标版本对象 ITEM-002", foundTargetItem);
    }

    /**
     * 测试非版本对象的处理
     */
    private void testNonRevisionableObjectHandlingImpl() {
        log("开始测试非版本对象处理");

        // 搜索文件夹（非版本对象）
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("设计文档", Pagination.of(0, 10));

        assertNotNull(results);
        assertTrue(results.getTotalHits() > 0);

        boolean foundTargetFolder = false;

        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            if (item.getObjectType().equals(SampleFolder.class.getName())) {
                foundTargetFolder = true;

                // 验证不是版本对象
                assertFalse("应该不被识别为版本对象", item.retrieveIsRevisionable());
                assertTrue("code应该为空", item.getCode() == null || item.getCode().trim().isEmpty());

                // 验证没有LogicalModelObject
                assertNull(item.retrieveLogicalModelObject());

                // 验证ObjectRef
                ObjectRef<?> objectRef = item.retrieveObjectRef();
                assertNotNull(objectRef);
                assertEquals(item.getId(), objectRef.getId());

                // 验证解析
                ModelObject resolved = item.retrieveModelObject();
                assertNotNull(resolved);
                assertTrue("解析的对象应该是SampleFolder", resolved instanceof SampleFolder);

                SampleFolder folder = (SampleFolder) resolved;
                assertEquals(item.getId(), folder.getId());
                assertEquals("设计文档", folder.getName());

                log("成功验证非版本对象: ID=" + folder.getId() + " - " + folder.getName());
                break;
            }
        }

        assertTrue("应该找到目标文件夹对象", foundTargetFolder);
    }

    /**
     * 测试混合搜索结果的处理
     */
    private void testMixedSearchResultsImpl() {
        log("开始测试混合搜索结果处理");

        // 搜索包含版本对象和非版本对象的结果
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("测试", Pagination.of(0, 20));

        assertNotNull(results);
        assertTrue(results.getTotalHits() > 0);

        Map<String, Integer> objectTypeCounts = new HashMap<>();
        int versionableCount = 0;
        int nonVersionableCount = 0;

        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            String objectType = item.getObjectType();
            objectTypeCounts.put(objectType, objectTypeCounts.getOrDefault(objectType, 0) + 1);

            ModelObject resolved = item.retrieveModelObject();
            assertNotNull(resolved);

            if (item.retrieveIsRevisionable()) {
                versionableCount++;
                assertNotNull(item.getCode());
                assertTrue("版本对象应该实现Revisionable", resolved instanceof Revisionable);

                LogicalModelObject<?> logicalObject = item.retrieveLogicalModelObject();
                assertNotNull(logicalObject);

                log("版本对象: " + item.getCode() + " (" + objectType + ")");
            } else {
                nonVersionableCount++;
                assertTrue("非版本对象code应该为空", item.getCode() == null || item.getCode().trim().isEmpty());

                ObjectRef<?> objectRef = item.retrieveObjectRef();
                assertNotNull(objectRef);

                log("非版本对象: ID=" + item.getId() + " (" + objectType + ")");
            }
        }

        // 验证结果多样性
        assertTrue("应该有版本对象", versionableCount > 0);
        assertTrue("应该有非版本对象", nonVersionableCount > 0);
        assertTrue("应该有多种对象类型", objectTypeCounts.size() >= 2);

        log("混合搜索结果统计 - 版本对象: " + versionableCount + ", 非版本对象: " + nonVersionableCount);
        log("对象类型分布: " + objectTypeCounts);
    }

    /**
     * 测试版本规则解析
     */
    private void testVersionRuleResolutionImpl() {
        log("开始测试版本规则解析");

        // 搜索版本对象
        FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                .search("ENGINE-001", Pagination.of(0, 5));

        assertNotNull(results);
        assertTrue(results.getTotalHits() > 0);

        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            if (item.retrieveIsRevisionable() && "ENGINE-001".equals(item.getCode())) {
                LogicalModelObject<?> logicalObject = item.retrieveLogicalModelObject();
                assertNotNull(logicalObject);

                // 验证默认版本规则
                assertEquals(RevisionRule.LATEST_WORKING, logicalObject.getRevisionRule());

                // 测试不同版本规则的LogicalModelObject
                LogicalModelObject<?> latestWorkingObj = LogicalModelObject.of(
                        item.getObjectType(), item.getCode(), RevisionRule.LATEST_WORKING);

                LogicalModelObject<?> latestObj = LogicalModelObject.of(
                        item.getObjectType(), item.getCode(), RevisionRule.LATEST);

                // 验证都能解析（在测试环境中应该指向同一对象）
                ModelObject workingResolved = latestWorkingObj.resolve();
                ModelObject latestResolved = latestObj.resolve();

                assertNotNull(workingResolved);
                assertNotNull(latestResolved);

                // 在测试环境中，这两个应该是同一个对象（因为只有一个版本）
                assertEquals(workingResolved.getId(), latestResolved.getId());

                log("版本规则测试通过: " + item.getCode() +
                        " -> ID=" + workingResolved.getId());
                break;
            }
        }
    }

    // ============== 权限相关测试的剩余部分 ==============

    /**
     * 确保权限配置已部署
     */
    private void ensurePermissionConfigDeployed() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                String simpleConfig = getSimplePermissionConfig();
                PermissionConfig deployedConfig = S.service(PermissionConfigDeploymentService.class).deploy(simpleConfig);

                assertTrue("Permission config should be deployed", deployedConfig != null);
                assertTrue("Permission config should have rules",
                        deployedConfig.getRules() != null && !deployedConfig.getRules().isEmpty());

                log.info("Simple permission config deployed for fulltext search test with {} rules",
                        deployedConfig.getRules().size());
            });
        });
    }

    /**
     * 创建分层级的权限测试数据
     */
    private void createLayeredPermissionTestData() {
        createTestAdminSearchUser().run(() -> {
            // 先创建测试用户的权限记录
            createFullTextSearchTestUsers();

            // Level 1: 公开数据 - 所有人可见
            ItemRevision publicItem = ItemRevision.newModel("PUBLIC-FT-001", "A");
            publicItem.setName("公开搜索零件");
            publicItem.setDescription("全文检索公开测试数据");
            S.service(ObjectService.class).save(publicItem);

            // Level 2: 部门数据 - 特定部门可见
            ItemRevision deptItem = ItemRevision.newModel("DEPT-FT-001", "A");
            deptItem.setName("部门搜索零件");
            deptItem.setDescription("全文检索部门测试数据");
            S.service(ObjectService.class).save(deptItem);

            // Level 3: 敏感数据 - 只有管理员可见
            ItemRevision adminItem = ItemRevision.newModel("ADMIN-FT-001", "A");
            adminItem.setName("管理员搜索零件");
            adminItem.setDescription("全文检索管理员测试数据");
            S.service(ObjectService.class).save(adminItem);

            // Level 4: 禁止数据 - 任何人都不能通过普通权限看到
            ItemRevision restrictedItem = ItemRevision.newModel("RESTRICTED-FT-001", "A");
            restrictedItem.setName("限制搜索零件");
            restrictedItem.setDescription("全文检索限制测试数据");
            S.service(ObjectService.class).save(restrictedItem);

            // 立即同步索引
            S.service(FullTextSearchService.class).syncImmediately();

            log.info("Created layered permission test data for fulltext search");

        });
    }

    /**
     * 场景1：全部查不到 - 无权限用户
     */
    private void testNoPermissionUserSearch() {
        createNoPermissionUser().run(() -> {
            FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                    .search("搜索零件", Pagination.of(0, 20));

            assertNotNull(results);

            // 统计找到的测试数据
            int foundTestItems = countFoundTestItems(results, "FT-001");

            // 无权限用户应该查不到任何测试数据（或最多只能看到public）
            assertTrue("No permission user should see 0 or only public items, but found: " + foundTestItems,
                    foundTestItems <= 1);

            // 如果有严格权限配置，应该一个都看不到
            assertEquals(0, foundTestItems, "No permission user should see no test items");

            log.info("No permission user search test completed - found {} test items", foundTestItems);
        });
    }

    /**
     * 场景2：部分查到 - 部分权限用户
     */
    private void testPartialPermissionUserSearch() {
        waitForIndexing();
        createPartialPermissionUser().run(() -> {
            FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                    .search("搜索零件", Pagination.of(0, 20));

            assertNotNull(results);

            // 统计找到的测试数据
            int foundTestItems = countFoundTestItems(results, "FT-001");
            Map<String, Boolean> foundItems = getFoundTestItemsMap(results, "FT-001");

            // 部分权限用户应该能看到public和dept，看不到admin和restricted
            assertTrue("Partial permission user should see some but not all items",
                    foundTestItems > 0 && foundTestItems < 4);

            // 具体验证
            assertTrue("Should see public item", foundItems.getOrDefault("PUBLIC-FT-001", false));
            assertTrue("Should see dept item", foundItems.getOrDefault("DEPT-FT-001", false));
            assertFalse("Should not see admin item", foundItems.getOrDefault("ADMIN-FT-001", false));
            assertFalse("Should not see restricted item", foundItems.getOrDefault("RESTRICTED-FT-001", false));

            log.info("Partial permission user search test completed - found {} test items: {}",
                    foundTestItems, foundItems.keySet());
        });
    }

    /**
     * 场景3：全部查到 - 完全权限用户
     */
    private void testFullPermissionUserSearch() {

        createTestAdminUser().run(() -> {
            FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                    .search("搜索零件", Pagination.of(0, 20));

            assertNotNull(results);

            // 统计找到的测试数据
            int foundTestItems = countFoundTestItems(results, "FT-001");
            Map<String, Boolean> foundItems = getFoundTestItemsMap(results, "FT-001");

            // 管理员应该能看到除了restricted之外的所有数据
            assertTrue("Admin user should see most items", foundTestItems >= 3);

            // 具体验证
            assertTrue("Admin should see public item", foundItems.getOrDefault("PUBLIC-FT-001", false));
            assertTrue("Admin should see dept item", foundItems.getOrDefault("DEPT-FT-001", false));
            assertTrue("Admin should see admin item", foundItems.getOrDefault("ADMIN-FT-001", false));

            log.info("Full permission user search test completed - found {} test items: {}",
                    foundTestItems, foundItems.keySet());

        });
    }

    /**
     * 场景4：经理用户搜索
     */
    private void testManagerUserSearch() {
        createTestManagerUser().run(() -> {
            FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                    .search("搜索零件", Pagination.of(0, 20));

            assertNotNull(results);

            // 统计找到的测试数据
            int foundTestItems = countFoundTestItems(results, "FT-001");
            Map<String, Boolean> foundItems = getFoundTestItemsMap(results, "FT-001");

            // 经理应该能看到大部分数据（除了restricted）
            assertTrue("Manager user should see most items", foundTestItems >= 2);

            // 具体验证（根据权限配置，经理可能有与管理员类似的权限）
            assertTrue("Manager should see public item",
                    foundItems.getOrDefault("PUBLIC-FT-001", false));

            // 如果有经理特定的权限配置，进行更具体的验证
            assertTrue("Manager should see dept item",
                    foundItems.getOrDefault("DEPT-FT-001", false));
            assertTrue("Manager should see admin item",
                    foundItems.getOrDefault("ADMIN-FT-001", false));

            log.info("Manager user search test completed - found {} test items: {}",
                    foundTestItems, foundItems.keySet());

        });
    }

    /**
     * 场景5：bypass权限检查
     */
    private void testBypassPermissionSearch() {
        UserContext noPermUser = createNoPermissionUser();

        // 先验证正常情况下看不到数据
        UserContext.setCurrentUser(noPermUser);
        int normalCount;
        try {
            FullTextSearchService.SearchResult normalResults = S.service(FullTextSearchService.class)
                    .search("搜索零件", Pagination.of(0, 20));
            normalCount = countFoundTestItems(normalResults, "FT-001");
        } finally {
            UserContext.clear();
        }

        // 使用bypass权限查看
        UserContext.setCurrentUser(noPermUser);
        int bypassCount;
        try {
            // 重新搜索获取bypass结果
            FullTextSearchService.SearchResult bypassResults = UserContext.ensureCurrent().byPass(() ->
                    S.service(FullTextSearchService.class).search("搜索零件", Pagination.of(0, 20))
            );
            bypassCount = countFoundTestItems(bypassResults, "FT-001");

        } finally {
            UserContext.clear();
        }

        // bypass应该能看到更多数据
        assertTrue("Bypass should show more items than normal permission check",
                bypassCount >= normalCount);

        // 如果有权限限制，bypass应该显著增加可见数据
        assertTrue("Bypass should show significantly more items when normal access is restricted",
                bypassCount >= 3);

        log.info("Bypass permission search test completed - normal: {}, bypass: {}",
                normalCount, bypassCount);
    }

    /**
     * 场景6：权限聚合测试
     */
    private void testPermissionFilteredAggregation() {
        createPartialPermissionUser().run(() -> {
            FullTextSearchService.SearchResult results = S.service(FullTextSearchService.class)
                    .search("零件", null, true, Pagination.of(0, 10));

            assertNotNull(results);
            assertNotNull(results.getAggregations());

            // 验证聚合结果也受权限控制
            Map<String, Map<String, Long>> aggregations = results.getAggregations();

            if (aggregations.containsKey("_objectType")) {
                Map<String, Long> typeAggs = aggregations.get("_objectType");

                // 聚合结果应该反映权限过滤后的数据
                assertTrue("Should have object type aggregations", !typeAggs.isEmpty());

                Long itemRevisionCount = typeAggs.get("io.emop.model.common.ItemRevision");
                if (itemRevisionCount != null) {
                    // 部分权限用户的聚合计数应该小于实际总数
                    log.info("Partial permission user sees {} ItemRevision in aggregation", itemRevisionCount);
                }
            }

            log.info("Permission filtered aggregation test completed");
        });
    }

    /**
     * 创建测试管理员用户
     */
    private UserContext createTestAdminUser() {
        return UserContext.builder()
                .userId(1L)
                .userUid("admin-search-test-uid")
                .username("admin_search")
                .authorities(Arrays.asList("ADMIN", "USER"))
                .groups(Arrays.asList("admin-dept-uid"))
                .build();
    }

    /**
     * 创建测试经理用户
     */
    private UserContext createTestManagerUser() {
        return UserContext.builder()
                .userId(2L)
                .userUid("manager-search-test-uid")
                .username("manager_search")
                .authorities(Arrays.asList("MANAGER", "USER"))
                .groups(Arrays.asList("manager-dept-uid"))
                .build();
    }

    /**
     * 创建无权限用户
     */
    private UserContext createNoPermissionUser() {
        return UserContext.builder()
                .userId(10L)
                .userUid("noperm-search-test-uid")
                .username("noperm_search")
                .authorities(Arrays.asList("GUEST"))  // 只有最基本权限
                .groups(Arrays.asList("guest-dept-uid"))
                .build();
    }

    /**
     * 创建部分权限用户（可以看到public和dept数据）
     */
    private UserContext createPartialPermissionUser() {
        return UserContext.builder()
                .userId(11L)
                .userUid("partial-search-test-uid")
                .username("partial_search")
                .authorities(Arrays.asList("USER", "DEPT_MEMBER"))
                .groups(Arrays.asList("normal-dept-uid"))
                .build();
    }

    /**
     * 统计找到的测试数据项数量
     */
    private int countFoundTestItems(FullTextSearchService.SearchResult results, String codePattern) {
        int count = 0;
        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            if (item.retrieveModelObject() instanceof ItemRevision) {
                ItemRevision rev = (ItemRevision) item.retrieveModelObject();
                if (rev.getCode().contains(codePattern)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 获取找到的测试数据项映射
     */
    private Map<String, Boolean> getFoundTestItemsMap(FullTextSearchService.SearchResult results, String codePattern) {
        Map<String, Boolean> foundItems = new HashMap<>();
        foundItems.put("PUBLIC-FT-001", false);
        foundItems.put("DEPT-FT-001", false);
        foundItems.put("ADMIN-FT-001", false);
        foundItems.put("RESTRICTED-FT-001", false);

        for (FullTextSearchService.SearchResultItem item : results.getItems()) {
            if (item.retrieveModelObject() instanceof ItemRevision) {
                ItemRevision rev = (ItemRevision) item.retrieveModelObject();
                if (rev.getCode().contains(codePattern)) {
                    foundItems.put(rev.getCode(), true);
                }
            }
        }
        return foundItems;
    }

    /**
     * 权限配置（用于全文检索测试）
     */
    private String getSimplePermissionConfig() {
        return """
                version: "1.0"
                description: "严格权限配置用于全文检索测试"
                createdAt: "2025-06-22"
                            
                permissionConfig:
                  objects:
                    io.emop.model.common.ItemRevision:
                      description: "ItemRevision严格权限控制"
                      permissions:
                        READ:
                          conditions:
                            # 管理员可以查看除了RESTRICTED之外的所有
                            - sql: "auth.user_is_admin() AND NOT (code LIKE 'RESTRICTED-%')"
                              description: "管理员查看非限制项目"
                            # 经理可以查看PUBLIC、DEPT和ADMIN
                            - sql: "auth.user_is_manager() AND (code LIKE 'PUBLIC-%' OR code LIKE 'DEPT-%' OR code LIKE 'ADMIN-%')"
                              description: "经理查看大部分项目"
                            # 部门用户可以查看PUBLIC和DEPT
                            - sql: "auth.user_has_role('DEPT_MEMBER') AND (code LIKE 'PUBLIC-%' OR code LIKE 'DEPT-%')"
                              description: "部门用户查看公开和部门项目"  
                            # 普通用户只能查看PUBLIC
                            - sql: "auth.user_has_role('USER') AND code LIKE 'PUBLIC-%'"
                              description: "普通用户查看公开项目"
                            # GUEST用户什么都看不到（没有条件匹配）
                          logic: "OR"
                """;
    }

    /**
     * 清除测试权限配置和数据，避免影响其他测试
     */
    private void cleanupPermissionTestData() {
        log.info("Starting cleanup of permission test data and configuration...");

        try {
            // 1. 清除权限配置（恢复到空配置或默认配置）
            UserContext.runAsSystem(() -> {
                UserContext.ensureCurrent().byPass(() -> {
                    S.service(PermissionConfigDeploymentService.class).removePermissionConfig(ItemRevision.class.getName());
                    log.info("Deployed empty permission config to cleanup test configuration");
                });
            });

            // 2. 清除测试数据
            UserContext.runAsSystem(() -> {
                UserContext.ensureCurrent().byPass(() -> {
                    // 删除权限测试专用的数据
                    Q.result(ItemRevision.class).where("code LIKE ?", "PUBLIC-FT-%").delete();
                    Q.result(ItemRevision.class).where("code LIKE ?", "DEPT-FT-%").delete();
                    Q.result(ItemRevision.class).where("code LIKE ?", "ADMIN-FT-%").delete();
                    Q.result(ItemRevision.class).where("code LIKE ?", "RESTRICTED-FT-%").delete();

                    log.info("Deleted permission test data items");
                });
            });
            // 3. 立即同步索引，清除搜索索引中的测试数据
            S.service(FullTextSearchService.class).syncImmediately();

            // 4. 1清理测试用户权限
            List<String> testUserUids = Arrays.asList(
                    "admin-search-test-uid",
                    "manager-search-test-uid",
                    "partial-search-test-uid",
                    "noperm-search-test-uid"
            );

            for (String userUid : testUserUids) {
                UserPermissions userPermissions = Q.result(UserPermissions.class)
                        .where("keycloakUserUid = ?", userUid)
                        .first();
                if (userPermissions != null) {
                    UserContext.runAsSystem(() -> {
                        S.service(ObjectService.class).delete(Arrays.asList(userPermissions.getId()));
                    });
                    log.debug("Cleaned up user permissions for: {}", userUid);
                }
            }

            log.info("Permission test cleanup completed successfully");
        } catch (Exception e) {
            log.warn("Failed to cleanup permission test data: {}", e.getMessage(), e);
            // 不抛异常，避免影响其他测试
        }
    }

    // ============== 辅助方法 ==============

    private void log(String message) {
        System.out.println("[PostgreSQLFullTextSearchTest] " + message);
    }
}