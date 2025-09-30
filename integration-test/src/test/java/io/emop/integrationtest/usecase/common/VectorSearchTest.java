package io.emop.integrationtest.usecase.common;

import io.emop.model.common.UserContext;
import io.emop.service.S;
import io.emop.service.api.data.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static io.emop.integrationtest.util.Assertion.*;

@Slf4j
public class VectorSearchTest implements Runnable {

    private static final int VECTOR_DIM = 128;
    private float[] baseVector;

    /**
     * 生成指定维度的随机向量
     */
    private float[] generateRandomVector() {
        if (baseVector != null) {
            return baseVector;
        }
        Random random = new Random();
        baseVector = new float[VECTOR_DIM];
        for (int i = 0; i < VECTOR_DIM; i++) {
            baseVector[i] = random.nextFloat();
        }
        return baseVector;
    }

    /**
     * 生成一个基于基础向量的相似向量
     */
    private float[] generateSimilarVector(float[] baseVector, float variance) {
        Random random = new Random();
        float[] similarVector = new float[VECTOR_DIM];
        for (int i = 0; i < VECTOR_DIM; i++) {
            similarVector[i] = baseVector[i] + (random.nextFloat() - 0.5f) * variance;
        }
        return similarVector;
    }

    /**
     * 将float[]转换为List<Float>
     */
    private List<Float> vectorToList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }

    @Override
    public void run() {
        UserContext.runAsSystem(() -> {
            testAddAndSearch();
            testBatchAddAndSearch();
            testSearchByMetadata();
            testDeleteByVector();
            testDeleteByMetadata();
            testInvalidVectorDimension();
        });
    }

    void testAddAndSearch() {
        // 生成测试向量并添加
        float[] vector = generateRandomVector();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("category", "test");
        metadata.put("tag", "basic");

        VectorSearchService.AddVectorRequest request = VectorSearchService.AddVectorRequest.builder()
                .id("test1")
                .vector(vectorToList(vector))
                .metadata(metadata)
                .build();

        S.service(VectorSearchService.class).addVector(request);

        // 使用相同向量搜索
        VectorSearchService.SearchVectorRequest searchRequest = VectorSearchService.SearchVectorRequest.builder()
                .vector(vectorToList(vector))
                .topK(5)
                .minScore(0.5f)
                .build();

        List<VectorSearchService.VectorSearchResult> searchResults = S.service(VectorSearchService.class)
                .searchVector(searchRequest);

        assertNotNull(searchResults);
        assertFalse(searchResults.isEmpty());

        VectorSearchService.VectorSearchResult bestMatch = searchResults.get(0);
        assertEquals("test1", bestMatch.getId());
        assertTrue(bestMatch.getScore() >= 0.5f);
    }

    void testBatchAddAndSearch() {
        // 生成基础向量和相似向量
        float[] baseVector = generateRandomVector();
        float[] similarVector = generateSimilarVector(baseVector, 0.2f);

        // 准备批量添加的向量数据
        List<VectorSearchService.VectorData> vectors = new ArrayList<>();

        VectorSearchService.VectorData vector1 = new VectorSearchService.VectorData();
        vector1.setId("batch1");
        vector1.setVector(vectorToList(baseVector));
        Map<String, String> metadata1 = new HashMap<>();
        metadata1.put("category", "batch");
        metadata1.put("type", "base");
        vector1.setMetadata(metadata1);
        vectors.add(vector1);

        VectorSearchService.VectorData vector2 = new VectorSearchService.VectorData();
        vector2.setId("batch2");
        vector2.setVector(vectorToList(similarVector));
        Map<String, String> metadata2 = new HashMap<>();
        metadata2.put("category", "batch");
        metadata2.put("type", "similar");
        vector2.setMetadata(metadata2);
        vectors.add(vector2);

        S.service(VectorSearchService.class).batchAddVectors(vectors);

        // 使用基础向量搜索，应该能找到两个相似的向量
        VectorSearchService.SearchVectorRequest searchRequest = VectorSearchService.SearchVectorRequest.builder()
                .vector(vectorToList(baseVector))
                .topK(5)
                .minScore(0.5f)
                .build();

        List<VectorSearchService.VectorSearchResult> searchResults = S.service(VectorSearchService.class)
                .searchVector(searchRequest);

        assertNotNull(searchResults);
        assertTrue(searchResults.size() >= 2);

        // 验证搜索结果包含我们添加的两个向量
        Set<String> resultIds = searchResults.stream()
                .map(VectorSearchService.VectorSearchResult::getId)
                .collect(Collectors.toSet());
        assertTrue(resultIds.contains("batch1"));
        assertTrue(resultIds.contains("batch2"));
    }

    void testSearchByMetadata() {
        Map<String, String> metadataFilter = new HashMap<>();
        metadataFilter.put("category", "batch");

        VectorSearchService.SearchByMetadataRequest request = VectorSearchService.SearchByMetadataRequest.builder()
                .metadata(metadataFilter)
                .maxResults(2)
                .build();

        List<VectorSearchService.VectorSearchResult> results = S.service(VectorSearchService.class)
                .searchByMetadata(request);

        assertNotNull(results);
        assertTrue(results.size() >= 2);

        // 验证返回的结果都是batch类别
        for (VectorSearchService.VectorSearchResult result : results) {
            assertTrue(result.getId().startsWith("batch"));
            assertEquals("batch", result.getMetadata().get("category"));
            log.info("Found vector by metadata: {} with type: {}",
                    result.getId(),
                    result.getMetadata().get("type"));
        }
    }

    void testDeleteByVector() {
        // 先添加一个测试向量
        float[] testVector = generateRandomVector();
        Map<String, String> metadata = new HashMap<>();
        metadata.put("category", "delete_test");

        VectorSearchService.AddVectorRequest addRequest = VectorSearchService.AddVectorRequest.builder()
                .id("delete_test1")
                .vector(vectorToList(testVector))
                .metadata(metadata)
                .build();

        S.service(VectorSearchService.class).addVector(addRequest);

        // 删除向量
        S.service(VectorSearchService.class).deleteVector("delete_test1");

        // 验证向量已被删除 - 通过搜索确认
        VectorSearchService.SearchVectorRequest searchRequest = VectorSearchService.SearchVectorRequest.builder()
                .vector(vectorToList(testVector))
                .topK(10)
                .minScore(0.9f)
                .build();

        List<VectorSearchService.VectorSearchResult> searchResults = S.service(VectorSearchService.class)
                .searchVector(searchRequest);

        // 确认删除的向量不在搜索结果中
        boolean found = searchResults.stream()
                .anyMatch(result -> "delete_test1".equals(result.getId()));
        assertFalse(found);
    }

    void testDeleteByMetadata() {
        // 先添加一些用于删除测试的向量
        List<VectorSearchService.VectorData> testVectors = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            VectorSearchService.VectorData vectorData = new VectorSearchService.VectorData();
            vectorData.setId("delete_meta_test" + i);
            vectorData.setVector(vectorToList(generateSimilarVector(generateRandomVector(), 0.1f)));
            Map<String, String> metadata = new HashMap<>();
            metadata.put("category", "delete_meta_test");
            metadata.put("index", String.valueOf(i));
            vectorData.setMetadata(metadata);
            testVectors.add(vectorData);
        }

        S.service(VectorSearchService.class).batchAddVectors(testVectors);

        // 通过元数据搜索确认向量存在
        Map<String, String> metadataFilter = new HashMap<>();
        metadataFilter.put("category", "delete_meta_test");

        VectorSearchService.SearchByMetadataRequest searchRequest = VectorSearchService.SearchByMetadataRequest.builder()
                .metadata(metadataFilter)
                .maxResults(10)
                .build();

        List<VectorSearchService.VectorSearchResult> beforeDelete = S.service(VectorSearchService.class)
                .searchByMetadata(searchRequest);
        assertTrue(beforeDelete.size() >= 3);

        // 逐个删除测试向量（由于接口只支持按ID删除，我们需要逐个删除）
        for (int i = 0; i < 3; i++) {
            S.service(VectorSearchService.class).deleteVector("delete_meta_test" + i);
        }

        // 验证向量已被删除
        List<VectorSearchService.VectorSearchResult> afterDelete = S.service(VectorSearchService.class)
                .searchByMetadata(searchRequest);

        boolean anyFound = afterDelete.stream()
                .anyMatch(result -> result.getId().startsWith("delete_meta_test"));
        assertFalse(anyFound);
    }

    void testInvalidVectorDimension() {
        // 测试维度错误的向量（127维）
        float[] invalidVector = new float[VECTOR_DIM - 1];
        Arrays.fill(invalidVector, 1.0f);

        VectorSearchService.AddVectorRequest request = VectorSearchService.AddVectorRequest.builder()
                .id("invalid")
                .vector(vectorToList(invalidVector))
                .build();

        assertException(() -> {
            S.service(VectorSearchService.class).addVector(request);
        });
    }
}