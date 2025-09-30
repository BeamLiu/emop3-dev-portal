package io.emop.integrationtest.usecase.common;

import io.emop.model.common.*;
import io.emop.model.metadata.*;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleMaterial;
import io.emop.service.S;
import io.emop.service.api.metadata.MetadataService;
import io.emop.service.api.metadata.MetadataUpdateService;
import io.emop.service.api.data.NativeSqlService;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.data.XpathService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.emop.integrationtest.util.Assertion.*;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MetadataOperationTest {

    MetadataService metaDataService;
    private String testDate;
    private Long testProductId;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
        metaDataService = S.service(MetadataService.class);
        testDate = String.valueOf(System.currentTimeMillis());
    }

    @AfterAll
    public void cleanup() {
        S.withStrongConsistency(this::cleanUpOutsourcedPartData);
    }

    @Test
    @Order(1)
    public void testCreateOutsourcedPart() {
        S.withStrongConsistency(this::testCreateOutsourcedPartImpl);
    }

    @Test
    @Order(2)
    public void testCreatePurchaseOrder() {
        S.withStrongConsistency(this::testCreatePurchaseOrderImpl);
    }

    @Test
    @Order(3)
    public void testAddPriceToOutsourcedPart() {
        S.withStrongConsistency(this::testAddPriceToOutsourcedPartImpl);
    }

    @Test
    @Order(4)
    public void testCreatePurchaseOrderInstance() {
        S.withStrongConsistency(() -> {
            testCreatePurchaseOrderInstanceImpl();
        });
    }

    @Test
    @Order(5)
    public void testDeleteAttributeFromOutsourcedPart() {
        S.withStrongConsistency(this::testDeleteAttributeFromOutsourcedPartImpl);
    }

    @Test
    @Order(6)
    public void testCreatePurchaseOrderInstanceAgain() {
        S.withStrongConsistency(() -> {
            testCreatePurchaseOrderInstanceImpl();
        });
    }

    @Test
    @Order(7)
    public void testGetAndSetOperation() {
        S.withStrongConsistency(() -> {
            testProductId = testGetAndSetOperationImpl(testDate);
        });
    }

    @Test
    @Order(8)
    public void testXpath() {
        S.withStrongConsistency(() -> {
            testXpathImpl(testDate, testProductId);
        });
    }

    @Test
    @Order(9)
    public void testPropertiesRelationHandling() {
        S.withStrongConsistency(this::testPropertiesRelationHandlingImpl);
    }

    @Test
    @Order(10)
    public void testDatabaseColumnVsPropertiesHandling() {
        S.withStrongConsistency(this::testDatabaseColumnVsPropertiesHandlingImpl);
    }

    /**
     * 测试关系对象是否正确从_properties中移除
     * 重现问题1：Product保存完后，_properties中还保留关系对象数据
     */
    private void testPropertiesRelationHandlingImpl() {
        log.info("=== 开始测试_properties中的关系对象处理 ===");

        final String date = String.valueOf(System.currentTimeMillis());
        final String productObjectType = "Product";

        // 确保Product类型存在
        S.service(MetadataUpdateService.class).createOrUpdateType(createProductTypeDefinition(productObjectType));
        metaDataService.reloadTypeDefinitions();

        // 1. 创建Product和Materials
        ItemRevision product = new ItemRevision(productObjectType);
        product.setCode("PROP-TEST-" + date);
        product.setRevId("A");
        product.setName("Properties Relation Test Product");
        product = S.service(ObjectService.class).save(product);

        SampleMaterial m1 = createMaterial(date + "-PROP", 1);
        SampleMaterial m2 = createMaterial(date + "-PROP", 2);

        log.info("创建的Materials: m1.id={}, m2.id={}", m1.getId(), m2.getId());

        // 2. 设置关系 - 这应该会将materials放到_properties中
        product.set("materials", Arrays.asList(m1, m2));

        // 验证设置关系后，_properties中确实包含materials
        Map<String, Object> propertiesBeforeSave = product.get_properties();
        log.info("保存前_properties内容: {}", propertiesBeforeSave);
        assertTrue(propertiesBeforeSave.containsKey("materials"), "保存前_properties应该包含materials");

        // 3. 启用关系处理并保存
        product.directiveHandleAssociateRelations();
        product = S.service(ObjectService.class).save(product);

        // 4. 关键验证：保存后_properties中不应该再包含materials
        log.info("保存后product.id={}", product.getId());

        // 从数据库重新加载对象进行验证
        ModelObject reloadedProduct = S.service(ObjectService.class).findById(product.getId());
        Map<String, Object> propertiesAfterSave = reloadedProduct.get_properties();

        log.info("保存后_properties内容: {}", propertiesAfterSave);
        log.info("保存后_properties是否包含materials: {}", propertiesAfterSave.containsKey("materials"));

        // 关键断言：materials不应该在_properties中
        assertFalse(propertiesAfterSave.containsKey("materials"), "保存后_properties不应该包含materials关系对象");

        // 5. 验证关系确实建立了
        ObjectRef productRef = new ObjectRef(product.getId());
        List<ModelObject> relatedMaterials = (List<ModelObject>) productRef.rel();
        log.info("关系数量: {}", relatedMaterials.size());
        assertEquals(2, relatedMaterials.size(), "应该有2个关系");

        // 6. 验证通过xpath可以访问materials
        Object materialsViaXpath = productRef.get("materials");
        assertTrue(materialsViaXpath instanceof List<?>, "通过xpath应该能访问到materials");
        assertEquals(2, ((List<?>) materialsViaXpath).size(), "xpath访问的materials数量应该是2");

        log.info("_properties关系对象处理测试通过");
    }

    /**
     * 测试数据库列字段与_properties字段的正确处理
     * 使用SQL直接查询验证真实的数据库存储状态
     */
    private void testDatabaseColumnVsPropertiesHandlingImpl() {
        log.info("=== 开始测试数据库列vs_properties字段处理（SQL验证版本） ===");

        final String date = String.valueOf(System.currentTimeMillis());

        // 1. 确保OutsourcedPart类型存在且有price属性
        testCreateOutsourcedPartImpl(); // 创建基础类型
        testAddPriceToOutsourcedPartImpl(); // 添加price属性

        // 2. 创建一个OutsourcedPart实例，包含各种字段类型
        ItemRevision part = new ItemRevision("OutsourcedPart");
        String partNumber = "PRICE-TEST-" + date;
        part.setCode(partNumber);
        part.setName(partNumber);
        part.setRevId("A");
        part.set("partNumber", partNumber);

        // 设置price字段 - 这在Java中没有对应属性，会放到_properties中
        part.set("price", 299.99);

        // 设置一个自定义字段 - 这个应该保留在_properties中
        part.set("customField", "customValue");
        part.set("extraInfo", "这个字段应该保留在_properties中");

        log.info("保存前的_properties: {}", part.get_properties());

        // 验证保存前price在_properties中
        assertTrue(part.get_properties().containsKey("price"));
        assertEquals(299.99, part.get_properties().get("price"));

        // 3. 保存对象
        part = S.service(ObjectService.class).save(part);
        log.info("保存后part.id={}", part.getId());

        // 4. 关键修改：使用SQL直接查询数据库验证存储状态
        NativeSqlService sqlService = S.service(NativeSqlService.class);

        // 4.1 查询price列的值
        String priceQuery = "SELECT price FROM sample.outsourcedpart WHERE id = ?";
        List<List<?>> priceResults = sqlService.executeNativeQuery(priceQuery, part.getId());

        log.info("数据库price列查询结果: {}", priceResults);
        assertFalse(priceResults.isEmpty());

        Object dbPriceValue = priceResults.get(0).get(0);
        log.info("数据库price列的值: {} (类型: {})", dbPriceValue, dbPriceValue != null ? dbPriceValue.getClass().getSimpleName() : "null");

        // 验证price正确存储到数据库列
        assertNotNull(dbPriceValue);
        assertTrue(Math.abs(((Number) dbPriceValue).doubleValue() - 299.99) < 0.01);

        // 4.2 查询_properties JSONB字段的内容
        String propertiesQuery = "SELECT _properties FROM sample.outsourcedpart WHERE id = ?";
        List<List<?>> propertiesResults = sqlService.executeNativeQuery(propertiesQuery, part.getId());

        log.info("数据库_properties JSONB查询结果: {}", propertiesResults);
        assertFalse(propertiesResults.isEmpty());

        Object dbPropertiesValue = propertiesResults.get(0).get(0);
        log.info("数据库_properties的值: {} (类型: {})", dbPropertiesValue, dbPropertiesValue != null ? dbPropertiesValue.getClass().getSimpleName() : "null");

        // 4.3 检查_properties中是否包含price键
        String priceInPropertiesQuery = "SELECT _properties->>'price' as price_in_properties FROM sample.outsourcedpart WHERE id = ?";
        List<List<?>> priceInPropertiesResults = sqlService.executeNativeQuery(priceInPropertiesQuery, part.getId());

        Object priceInProperties = priceInPropertiesResults.get(0).get(0);
        log.info("_properties中的price值: {}", priceInProperties);

        assertNull(priceInProperties);

        // 4.4 验证自定义字段在_properties中
        String customFieldQuery = "SELECT _properties->>'customField' as custom_field FROM sample.outsourcedpart WHERE id = ?";
        List<List<?>> customFieldResults = sqlService.executeNativeQuery(customFieldQuery, part.getId());

        Object customFieldValue = customFieldResults.get(0).get(0);
        log.info("_properties中的customField值: {}", customFieldValue);
        assertEquals("customValue", customFieldValue);

        // 5. 测试更新price字段
        log.info("--- 测试更新price字段 ---");
        ObjectService objectService = S.service(ObjectService.class);

        // 使用update方法更新price
        Map<String, Object> updateData = Map.of(
                "price", 399.99,  // 更新price字段
                "customField", "updatedCustomValue"  // 更新自定义字段
        );

        // 获取当前版本进行更新
        String versionQuery = "SELECT _version FROM sample.outsourcedpart WHERE id = ?";
        List<List<?>> versionResults = sqlService.executeNativeQuery(versionQuery, part.getId());
        Integer currentVersion = (Integer) versionResults.get(0).get(0);

        objectService.update(part.getId(), currentVersion, updateData);

        // 6. 验证更新后的状态
        // 6.1 验证price列更新
        List<List<?>> updatedPriceResults = sqlService.executeNativeQuery(priceQuery, part.getId());
        Object updatedDbPrice = updatedPriceResults.get(0).get(0);
        log.info("更新后数据库price列的值: {}", updatedDbPrice);
        assertTrue(Math.abs(((Number) updatedDbPrice).doubleValue() - 399.99) < 0.01);

        // 6.2 验证_properties中仍然没有price
        List<List<?>> updatedPriceInPropertiesResults = sqlService.executeNativeQuery(priceInPropertiesQuery, part.getId());
        Object updatedPriceInProperties = updatedPriceInPropertiesResults.get(0).get(0);
        log.info("更新后_properties中的price值: {}", updatedPriceInProperties);

        assertNull(updatedPriceInProperties);

        // 6.3 验证自定义字段正确更新
        List<List<?>> updatedCustomFieldResults = sqlService.executeNativeQuery(customFieldQuery, part.getId());
        Object updatedCustomField = updatedCustomFieldResults.get(0).get(0);
        log.info("更新后_properties中的customField值: {}", updatedCustomField);
        assertEquals("updatedCustomValue", updatedCustomField);

        // 7. 完整的_properties内容验证
        List<List<?>> finalPropertiesResults = sqlService.executeNativeQuery(propertiesQuery, part.getId());
        Object finalProperties = finalPropertiesResults.get(0).get(0);
        log.info("最终数据库_properties完整内容: {}", finalProperties);

        log.info("数据库列vs_properties字段处理测试完成（SQL验证版本）");
    }

    private void testXpathImpl(String date, Long productId) {
        ModelObject product = new ObjectRef(productId);
        Object result;

        // 1. 测试读取操作
        result = product.get("materials");
        assertTrue(result instanceof List<?>);
        assertEquals(4, ((List<?>) result).size());

        result = product.get("materials[*]");
        assertTrue(result instanceof List<?>);
        assertEquals(4, ((List<?>) result).size());

        //因为从直接属性获取
        result = product.get("materials");
        assertTrue(result instanceof List<?>);
        assertEquals(4, ((List<?>) result).size());

        //返回第一个 id>0 的数据
        result = product.get("materials[position()=1]");
        assertTrue(result instanceof ModelObject);

        //返回所有 id>0 的数据
        result = product.get("materials[*]");
        assertTrue(result instanceof List<?>);
        assertEquals(4, ((List<?>) result).size());

        result = product.get("materials[position()=1][@code='P" + date + "-1'][*]/code");
        assertTrue(result instanceof List<?>);
        assertEquals(1, ((List<?>) result).size());
        assertEquals("P" + date + "-1", ((List<?>) result).get(0));

        // 2. 测试设置操作
        // 2.1 测试设置简单属性
        ((Settable) product).set("name", "Updated Name");
        assertEquals("Updated Name", product.get("name"));

        // 2.2 测试设置材料的属性
        product = ((ObjectRef) product).unbox();
        product = S.service(XpathService.class).xpathSet(product, "materials[position()=1]/name", "Updated Material 1");
        assertEquals("Updated Material 1",
                ((ModelObject) ((List<?>) product.get("materials")).get(0)).get("name"));

        // 2.3 测试设置第二个材料的属性
        product = S.service(XpathService.class).xpathSet(product, "materials[position()=2]/description", "Updated Description");
        assertEquals("Updated Description",
                ((ModelObject) ((List<?>) product.get("materials")).get(1)).get("description"));

        // 2.4 使用条件设置属性
        product = S.service(XpathService.class).xpathSet(product, "materials[@code='P" + date + "-1']/name", "Material One");
        result = product.get("materials[@code='P" + date + "-1']/name");
        assertEquals("Material One", result.toString());
        assertEmptyProperties("sample.Product", product.getId());

        // 2.5 测试设置后保存和重新加载
        S.service(ObjectService.class).saveAll((List<? extends ModelObject>) product.get("materials"));
        product.get_properties().remove("materials");
        product = S.service(ObjectService.class).save(product);
        assertEmptyProperties("sample.Product", product.getId());
        ModelObject reloadedProduct = new ObjectRef(product.getId());
        assertEquals("Updated Name", reloadedProduct.get("name"));
        assertEquals("Material One",
                reloadedProduct.get("materials[@code='P" + date + "-1']/name").toString());

        // 3. 测试错误场景
        // 3.1 测试设置不存在的路径
        ModelObject finalProduct = product;
        assertException(new Runnable() {
            @Override
            public void run() {
                finalProduct.set("nonexistent/path", "value");
            }
        });

        // 3.2 测试设置无效索引
        assertException(new Runnable() {
            @Override
            public void run() {
                finalProduct.set("materials[99]/name", "Invalid");
            }
        });
    }

    /**
     * 增强的testGetAndSetOperation，添加更多_properties验证
     */
    private Long testGetAndSetOperationImpl(String date) {
        final String productObjectType = "Product";
        //创建Product类型，下挂 materials 和 documents
        S.service(MetadataUpdateService.class).createOrUpdateType(createProductTypeDefinition(productObjectType));
        metaDataService.reloadTypeDefinitions();
        ItemRevision product = new ItemRevision(productObjectType);
        product.setCode("P" + date);
        product.setRevId("A");
        product.setName("Product: " + product.getCode());
        product = S.service(ObjectService.class).save(product);
        SampleMaterial m1 = createMaterial(date, 1);
        SampleMaterial m2 = createMaterial(date, 2);
        SampleMaterial m3 = createMaterial(date, 3);

        product.set("materials", Arrays.asList(m1, m2, m3));

        // 增强验证：检查设置关系前的_properties状态
        Map<String, Object> propertiesBeforeRelationProcessing = product.get_properties();
        log.info("设置materials关系前_properties: {}", propertiesBeforeRelationProcessing);
        assertTrue(propertiesBeforeRelationProcessing.containsKey("materials"));

        product.directiveHandleAssociateRelations();
        product = S.service(ObjectService.class).save(product);
        assertEquals("P" + date, new ObjectRef(product.getId()).get("code"));
        assertEquals(3, new ObjectRef(product.getId()).rel().size());

        // 增强验证：检查关系处理后的_properties状态
        ModelObject savedProduct = S.service(ObjectService.class).findById(product.getId());
        Map<String, Object> propertiesAfterSave = savedProduct.get_properties();
        log.info("处理关系并保存后_properties: {}", propertiesAfterSave);

        // 关键验证：materials不应该在_properties中
        assertFalse(propertiesAfterSave.containsKey("materials"));
        assertEmptyProperties("sample.Product", savedProduct.getId());

        product.set("materials", Arrays.asList(m1, m2));
        product = S.service(ObjectService.class).save(product);
        assertEquals(2, new ObjectRef(product.getId()).rel().size());

        SampleMaterial m4 = SampleMaterial.newModel("P" + date + "-" + 4, "A");
        m4.setDescription("P" + date + "-" + 4);
        m4.setName("Material :" + m4.getCode());

        product.set("materials", Arrays.asList(m1, m2, m3, m4));
        product = S.service(ObjectService.class).save(product);
        assertEquals(4, new ObjectRef(product.getId()).rel().size());

        // 最终验证_properties状态
        ModelObject finalProduct = S.service(ObjectService.class).findById(product.getId());
        assertEmptyProperties("sample.Product", finalProduct.getId());

        return product.getId();
    }

    private void assertEmptyProperties(@NonNull String table, @NonNull Long id) {
        String propertiesQuery = "SELECT _properties as props FROM " + table + " WHERE id = ?";
        List<List<?>> propertiesQueryResults = S.service(NativeSqlService.class).executeNativeQuery(propertiesQuery, id);

        Object result = propertiesQueryResults.get(0).get(0);
        log.info("Table {} _properties中值: {}", table, result);
        assertTrue(result == null || ((Map) result).isEmpty());
    }

    private static SampleMaterial createMaterial(String date, int seq) {
        SampleMaterial material = SampleMaterial.newModel("P" + date + "-" + seq, "A");
        material.setDescription("P" + date + "-" + seq);
        material.setName("Sample Material: " + material.getCode());
        return S.service(ObjectService.class).save(material);
    }

    private TypeDefinition createProductTypeDefinition(String productObjectType) {
        MetadataBuilder.RegularTypeBuilder builder = TypeDefinition.builder(productObjectType).superType(ItemRevision.class.getName()).persistentInfo(Schema.SAMPLE, "PRODUCT");

        builder.attribute("name", Types.STRING);
        builder.attribute("description", Types.STRING);
        builder.attribute("price", Types.STRING);
        builder.oneToManyAssociateRelation("materials", Types.simple(SampleMaterial.class));

        return builder.build();
    }

    private void testCreatePurchaseOrderInstanceImpl() {
        testCreatePurchaseOrderInstanceImpl("xxx-xx" + System.currentTimeMillis());
    }

    /**
     * 增强的testCreatePurchaseOrderInstance，添加_properties验证
     */
    private ModelObject testCreatePurchaseOrderInstanceImpl(String partNumber) {
        ItemRevision part = new ItemRevision("OutsourcedPart");
        part.audit();
        part.set("partNumber", partNumber);
        part.setCode(partNumber);
        part.setName(partNumber);
        part.setRevId("A");
        part.set("newField", "yyyyyyyyy");
        part.set("price", 1.0);
        part.set("notExistsField", "val");

        part = S.service(ObjectService.class).save(part);

        assertTrue(Math.abs(((Number) part.get("price")).doubleValue() - 1.0) < 0.01);
        // 增强验证：保存后_properties状态
        String priceInPropertiesQuery = "SELECT _properties->>'price' as price_in_properties FROM sample.outsourcedpart WHERE id = ?";
        List<List<?>> priceInPropertiesResults = S.service(NativeSqlService.class).executeNativeQuery(priceInPropertiesQuery, part.getId());

        Object priceInProperties = priceInPropertiesResults.get(0).get(0);
        log.info("_properties中的price值: {}", priceInProperties);

        assertNull(priceInProperties);

        List<ItemRevision> pos = Q.<ItemRevision>objectType("OutsourcedPart").where("partNumber=?", partNumber).query();
        assertTrue(pos.size() > 0);
        assertEquals(partNumber, pos.get(0).getCode());
        assertEquals("A", pos.get(0).getRevId());

        return pos.get(0);
    }

    //2. 创建 外购件 类型，继承 ItemRevision，并添加属性
    private void testCreateOutsourcedPartImpl() {
        cleanUpOutsourcedPartData();
        // 创建外购件类型定义，继承于ItemRevision
        TypeDefinition outsourcedPartType = TypeDefinition.builder("OutsourcedPart")
                .superType(ItemRevision.class.getName())
                .persistentInfo(Schema.SAMPLE, "OutsourcedPart")
                .description("manually create outsourcedPartType")
                .requiredAttribute("partNumber", Types.STRING)
                .build();

        // 创建类型
        S.service(MetadataUpdateService.class).createOrUpdateType(outsourcedPartType);
        metaDataService.reloadTypeDefinitions();
        // 验证继承关系
        TypeDefinition queriedType = metaDataService.retrieveFullTypeDefinition("OutsourcedPart");
        assertNotNull(queriedType);
        assertEquals("OutsourcedPart", queriedType.getName());
        assertEquals("io.emop.model.common.ItemRevision", queriedType.getSuperType());
        assertTrue(queriedType.getAttributes().containsKey("partNumber"));
    }

    //3.创建 采购单 类型，继承 ItemRevision，添加属性和覆盖方法
    private void testCreatePurchaseOrderImpl() {
        // 创建采购单类型定义，继承于ItemRevision
        TypeDefinition purchaseOrderType = TypeDefinition.builder("PurchaseOrder")
                .superType(GenericModelObject.class.getName())
                .persistentInfo(Schema.SAMPLE, "PurchaseOrder")
                .description("manually create purchaseOrderType")
                .requiredAttribute("orderNumber", Types.STRING)
                .requiredAttribute("id", Types.LONG)
                .oneToManyAssociateRelation("outsourcedParts", Types.simple("OutsourcedPart"))
                .method("advanceState", Types.STRING, "status = purchaseOrderStateMachine(status); return status;")
                .build();

        // 创建采购单类型
        S.service(MetadataUpdateService.class).createOrUpdateType(purchaseOrderType);
        metaDataService.reloadTypeDefinitions();
        // 验证继承关系和方法覆盖
        TypeDefinition queriedType = metaDataService.retrieveFullTypeDefinition("PurchaseOrder");
        assertNotNull(queriedType);
        assertEquals("PurchaseOrder", queriedType.getName());
        assertEquals(GenericModelObject.class.getName(), queriedType.getSuperType());
        assertTrue(queriedType.getMethods().containsKey("advanceState"));
        assertEquals("status = purchaseOrderStateMachine(status); return status;", queriedType.getMethods().get("advanceState").getImplementation());
    }

    //4. 为 外购件 添加 price 属性，考虑已有数据
    private void testAddPriceToOutsourcedPartImpl() {
        // 获取外购件定义
        TypeDefinition outsourcedPartType = metaDataService.retrieveFullTypeDefinition("OutsourcedPart");
        assertNotNull(outsourcedPartType);

        // 添加price属性
        AttributeDefinition priceAttr = new AttributeDefinition("price", "description", Types.simple("double"), true);

        // 添加新的属性，例如partNumber
        AttributeDefinition newFieldAttr = new AttributeDefinition("newField", "description", Types.STRING, true);

        S.service(MetadataUpdateService.class).createOrUpdateAttribute("OutsourcedPart", priceAttr, newFieldAttr);
        metaDataService.reloadTypeDefinitions();
        // 验证属性是否正确添加
        TypeDefinition updatedType = metaDataService.retrieveFullTypeDefinition("OutsourcedPart");
        assertTrue(updatedType.getAttributes().containsKey("price"));
    }

    //5. 删除已有属性，考虑已有数据
    private void testDeleteAttributeFromOutsourcedPartImpl() {
        // 删除 partNumber 属性
        AttributeDefinition priceAttr = new AttributeDefinition("price", "description", Types.simple("int"), true);
        S.service(MetadataUpdateService.class).createOrUpdateAttribute("OutsourcedPart", priceAttr);
        //restore the previous data type
        AttributeDefinition priceAttr2 = new AttributeDefinition("price", "description", Types.simple("double"), true);
        S.service(MetadataUpdateService.class).createOrUpdateAttribute("OutsourcedPart", priceAttr2);
        //cannot delete as there are data
        assertException(() -> S.service(MetadataUpdateService.class).deleteAttribute("OutsourcedPart", "newField"));
        cleanUpOutsourcedPartData();
        //delete successfully
        S.service(MetadataUpdateService.class).deleteAttribute("OutsourcedPart", "newField");
        //TODO: refresh automatically, currently manual refresh first
        metaDataService.reloadTypeDefinitions();
        // 验证属性是否已删除
        TypeDefinition updatedType = metaDataService.retrieveFullTypeDefinition("OutsourcedPart");
        assertFalse(updatedType.getAttributes().containsKey("newField"));
    }

    private void cleanUpOutsourcedPartData() {
        try {
            //remove existing data
            List<Long> existindIds = Q.<ItemRevision>objectType("OutsourcedPart").noCondition().query().stream().map(ItemRevision::getId).collect(Collectors.toList());
            S.service(ObjectService.class).delete(existindIds);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}