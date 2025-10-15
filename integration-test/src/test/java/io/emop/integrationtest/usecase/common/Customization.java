package io.emop.integrationtest.usecase.common;

import io.emop.integrationtest.domain.FactoryEntity;
import io.emop.integrationtest.domain.UserLocationEntity;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.model.cad.CADComponent;
import io.emop.model.common.CheckoutInfo;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.ObjectRef;
import io.emop.model.common.Revisionable.CriteriaByCodeAndRevId;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.NativeSqlService;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.RevisionService;
import io.emop.service.api.dsl.DSLExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.emop.integrationtest.util.Assertion.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 自定义属性及对象测试 - 包含JSONB merge更新测试
 * <p>
 * 测试ObjectService的各种方法：
 * 1. save(obj) - 创建和更新对象，验证processNonMappedFields
 * 2. update(id, version, data) - 带版本控制的更新
 * 3. fastUpdate(id, data) - 轻量级更新，跳过版本检查但仍递增版本
 */
@Slf4j
@RequiredArgsConstructor
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Customization {
    private static final String revId = "35";
    private static final String dateCode = String.valueOf(System.currentTimeMillis());
    private static final int batchSize = 1000;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100l, List.of("admin")));
    }

    /**
     * 测试 save() 方法创建对象时的 JSONB 处理
     * 验证 processNonMappedFields 方法是否正确工作
     * 特别验证 @QuerySqlField(mapToDBColumn = false) 字段的处理
     */
    @Test
    @Order(5)
    public void testJsonbSaveCreate() {
        log.info("=== 开始测试Save创建对象的JSONB处理 ===");

        TimerUtils.measureExecutionTime("JSONB Save创建测试", () -> {
            // 创建包含多种字段类型的对象
            FactoryEntity entity = new FactoryEntity();
            String code = "SaveCreateTest-" + dateCode;

            // 设置普通列字段
            entity.setName(code);
            entity.setCode(code);
            entity.setRevId(revId);

            // 设置JSONB字段 (mapToDBColumn = false)
            entity.setFactoryLocation("Shanghai");    // JSONB字段
            entity.setManagerUserId(600L);            // JSONB字段

            // 设置动态属性 (会存储到_properties)
            entity.set("department", "R&D");
            entity.set("capacity", 12000);
            entity.set("certifications", List.of("ISO9001", "ISO27001", "SOC2"));
            entity.set("location", Map.of(
                    "country", "China",
                    "city", "Shanghai",
                    "address", "Pudong New Area"
            ));
            entity.set("isActive", true);

            ObjectService objectService = S.service(ObjectService.class);

            log.info("保存前对象状态:");
            log.info("  - factoryLocation: {}", entity.getFactoryLocation());
            log.info("  - managerUserId: {}", entity.getManagerUserId());
            log.info("  - _properties: {}", entity.get_properties());

            // 调用save方法 - 这里会触发processNonMappedFields
            FactoryEntity savedEntity = objectService.save(entity);

            log.info("保存后对象ID: {}", savedEntity.getId());
            assertNotNull(savedEntity.getId());

            // 关键验证：@QuerySqlField(mapToDBColumn = false) 的字段不应该在_properties中
            Map<String, Object> properties = savedEntity.get_properties();
            assertFalse("factoryLocation should not be in _properties",
                    properties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties",
                    properties.containsKey("managerUserId"));

            savedEntity = new ObjectRef<FactoryEntity>(savedEntity.getId()).unbox();
            // 关键验证：@QuerySqlField(mapToDBColumn = false) 的字段不应该在_properties中
            properties = savedEntity.get_properties();
            assertFalse("factoryLocation should not be in _properties",
                    properties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties",
                    properties.containsKey("managerUserId"));

            //测试批量API
            savedEntity = (FactoryEntity) S.service(ObjectService.class).findAllById(List.of(savedEntity.getId())).iterator().next();
            // 关键验证：@QuerySqlField(mapToDBColumn = false) 的字段不应该在_properties中
            properties = savedEntity.get_properties();
            assertFalse("factoryLocation should not be in _properties",
                    properties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties",
                    properties.containsKey("managerUserId"));

            // 从数据库重新查询验证
            FactoryEntity retrievedEntity = S.service(RevisionService.class)
                    .queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), code, revId, FactoryEntity.class));

            assertNotNull(retrievedEntity);

            // 验证普通列字段
            assertEquals(code, retrievedEntity.getName());
            assertEquals(code, retrievedEntity.getCode());
            assertEquals(revId, retrievedEntity.getRevId());

            // 验证JSONB字段正确存储和恢复到直接属性
            assertEquals("Shanghai", retrievedEntity.getFactoryLocation());
            assertEquals(Long.valueOf(600), retrievedEntity.getManagerUserId());

            // 关键验证：@QuerySqlField(mapToDBColumn = false) 的字段不应该在_properties中
            properties = retrievedEntity.get_properties();
            assertFalse("factoryLocation should not be in _properties",
                    properties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties",
                    properties.containsKey("managerUserId"));

            // 验证动态属性正确存储到_properties中
            assertEquals("R&D", retrievedEntity.get("department"));
            assertEquals(Integer.valueOf(12000), retrievedEntity.get("capacity"));
            assertEquals(List.of("ISO9001", "ISO27001", "SOC2"), retrievedEntity.get("certifications"));
            assertEquals(Boolean.TRUE, retrievedEntity.get("isActive"));

            // 验证复杂对象
            Map<String, Object> location = (Map<String, Object>) retrievedEntity.get("location");
            assertNotNull(location);
            assertEquals("China", location.get("country"));
            assertEquals("Shanghai", location.get("city"));
            assertEquals("Pudong New Area", location.get("address"));

            log.info("Save创建测试验证通过:");
            log.info("  - JSONB字段: factoryLocation={}, managerUserId={}",
                    retrievedEntity.getFactoryLocation(), retrievedEntity.getManagerUserId());
            log.info("  - 动态属性: department={}, capacity={}, isActive={}",
                    retrievedEntity.get("department"), retrievedEntity.get("capacity"), retrievedEntity.get("isActive"));
            log.info("  - 复杂对象: location={}", (String) retrievedEntity.get("location"));
            log.info("  - _properties不包含JSONB字段: factoryLocation和managerUserId已正确提取到直接属性");
        });
    }

    /**
     * 测试 save() 方法更新对象时的 JSONB 处理
     * 验证更新时是否保持正确的merge行为和字段提取
     */
    @Test
    @Order(6)
    public void testJsonbSaveUpdate() {
        log.info("=== 开始测试Save更新对象的JSONB处理 ===");

        TimerUtils.measureExecutionTime("JSONB Save更新测试", () -> {
            // 1. 先创建初始对象
            FactoryEntity entity = new FactoryEntity();
            String code = "SaveUpdateTest-" + dateCode;
            entity.setName(code);
            entity.setCode(code);
            entity.setRevId(revId);
            entity.setFactoryLocation("Beijing");
            entity.setManagerUserId(700L);
            entity.set("department", "Production");
            entity.set("shift", "day");
            entity.set("employees", 200);
            entity.set("equipment", List.of("CNC", "Lathe", "Mill"));

            ObjectService objectService = S.service(ObjectService.class);
            FactoryEntity savedEntity = objectService.save(entity);

            log.info("初始对象创建完成: ID={}", savedEntity.getId());

            // 2. 修改对象的部分字段后再次save
            savedEntity.setName("Updated-" + code);              // 普通列修改
            savedEntity.setFactoryLocation("Tianjin");           // JSONB字段修改
            savedEntity.set("department", "Quality Control");    // 动态属性修改
            savedEntity.set("certification", "ISO14001");        // 新增动态属性
            // 注意：shift, employees, equipment 没有修改，应该保留

            FactoryEntity updatedEntity = objectService.save(savedEntity);
            assertEquals(savedEntity.getId(), updatedEntity.getId()); // ID应该相同

            // 3. 从数据库重新查询验证
            FactoryEntity retrievedEntity = S.service(RevisionService.class)
                    .queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), code, revId, FactoryEntity.class));

            // 验证更新的字段
            assertEquals("Updated-" + code, retrievedEntity.getName());
            assertEquals("Tianjin", retrievedEntity.getFactoryLocation());
            assertEquals("Quality Control", retrievedEntity.get("department"));
            assertEquals("ISO14001", retrievedEntity.get("certification"));

            // 关键验证：未修改的字段应该保留
            assertEquals(Long.valueOf(700), retrievedEntity.getManagerUserId());  // JSONB字段保留
            assertEquals("day", retrievedEntity.get("shift"));                     // 动态属性保留
            assertEquals(Integer.valueOf(200), retrievedEntity.get("employees"));  // 动态属性保留
            assertEquals(List.of("CNC", "Lathe", "Mill"), retrievedEntity.get("equipment")); // 动态属性保留

            // 关键验证：@QuerySqlField(mapToDBColumn = false) 的字段不应该在_properties中
            Map<String, Object> properties = retrievedEntity.get_properties();
            assertFalse("factoryLocation should not be in _properties after update",
                    properties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties after update",
                    properties.containsKey("managerUserId"));

            // 验证版本递增
            assertTrue(retrievedEntity.get_version() > 1);

            log.info("Save更新测试验证通过:");
            log.info("  - 更新字段: name={}, factoryLocation={}, department={}",
                    retrievedEntity.getName(), retrievedEntity.getFactoryLocation(), retrievedEntity.get("department"));
            log.info("  - 新增字段: certification={}", (String) retrievedEntity.get("certification"));
            log.info("  - 保留字段: managerUserId={}, shift={}, employees={}, equipment={}",
                    retrievedEntity.getManagerUserId(), retrievedEntity.get("shift"),
                    retrievedEntity.get("employees"), retrievedEntity.get("equipment"));
            log.info("  - 版本号: {}", retrievedEntity.get_version());
            log.info("  - _properties正确处理: 不包含JSONB字段，只包含动态属性");
        });
    }

    /**
     * 测试JSONB字段的merge更新功能
     * 特别验证字段提取行为
     */
    @Test
    @Order(7)
    public void testJsonbMergeUpdate() {
        log.info("=== 开始测试JSONB Merge更新功能 ===");

        TimerUtils.measureExecutionTime("JSONB Merge更新测试", () -> {
            // 1. 创建初始数据 - 包含多个JSONB字段
            FactoryEntity entity = new FactoryEntity();
            String code = "MergeTest-" + dateCode;
            entity.setName(code);
            entity.setCode(code);
            entity.setRevId(revId);
            entity.setFactoryLocation("Shenzhen");
            entity.setManagerUserId(200L);

            // 添加额外的自定义属性
            entity.set("department", "Manufacturing");
            entity.set("capacity", 5000);
            entity.set("certification", List.of("ISO9001", "ISO14001"));
            entity.set("contactInfo", Map.of("phone", "13800138000", "email", "factory@company.com"));

            ObjectService objectService = S.service(ObjectService.class);
            objectService.save(entity);

            log.info("初始数据保存完成: {}", entity.getId());

            // 2. 验证初始数据
            FactoryEntity savedEntity = S.service(RevisionService.class)
                    .queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), code, revId, FactoryEntity.class));

            assertNotNull(savedEntity);
            assertEquals("Shenzhen", savedEntity.getFactoryLocation());
            assertEquals(Long.valueOf(200), savedEntity.getManagerUserId());
            assertEquals("Manufacturing", savedEntity.get("department"));
            assertEquals(Integer.valueOf(5000), savedEntity.get("capacity"));

            // 验证初始状态的字段提取
            Map<String, Object> initialProperties = savedEntity.get_properties();
            assertFalse("factoryLocation should not be in _properties initially",
                    initialProperties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties initially",
                    initialProperties.containsKey("managerUserId"));
            assertTrue("department should be in _properties",
                    initialProperties.containsKey("department"));

            log.info("初始数据验证通过: factoryLocation={}, managerUserId={}, department={}, capacity={}",
                    savedEntity.getFactoryLocation(), savedEntity.getManagerUserId(),
                    savedEntity.get("department"), savedEntity.get("capacity"));

            // 3. 使用ObjectService.update进行部分更新
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("factoryLocation", "Guangzhou");  // 更新现有JSONB字段
            updateData.put("capacity", 8000);                // 更新现有JSONB字段
            updateData.put("country", "China");              // 添加新的JSONB字段
            updateData.put("established", "2020-01-01");     // 添加新的JSONB字段

            ModelObject updatedResult = objectService.update(savedEntity.getId(), savedEntity.get_version(), updateData);
            assertNotNull(updatedResult);

            log.info("部分更新完成: {}", updatedResult.getId());

            // 4. 验证更新后的数据 - 关键：验证merge行为和字段提取
            FactoryEntity updatedEntity = S.service(RevisionService.class)
                    .queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), code, revId, FactoryEntity.class));

            assertNotNull(updatedEntity);

            // 验证更新的字段
            assertEquals("Guangzhou", updatedEntity.getFactoryLocation());
            assertEquals(Long.valueOf(200), updatedEntity.getManagerUserId()); // 应该保持不变
            assertEquals(Integer.valueOf(8000), updatedEntity.get("capacity"));
            assertEquals("China", updatedEntity.get("country"));
            assertEquals("2020-01-01", updatedEntity.get("established"));

            // 关键验证：未更新的字段应该保留
            assertEquals("Manufacturing", updatedEntity.get("department"));  // 应该保留
            assertNotNull(updatedEntity.get("certification"));               // 应该保留
            assertNotNull(updatedEntity.get("contactInfo"));                 // 应该保留

            // 关键验证：@QuerySqlField(mapToDBColumn = false) 的字段仍然不在_properties中
            Map<String, Object> finalProperties = updatedEntity.get_properties();
            assertFalse("factoryLocation should not be in _properties after merge update",
                    finalProperties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties after merge update",
                    finalProperties.containsKey("managerUserId"));

            // 验证动态属性仍在_properties中
            assertTrue("department should still be in _properties",
                    finalProperties.containsKey("department"));
            assertTrue("capacity should still be in _properties",
                    finalProperties.containsKey("capacity"));
            assertTrue("country should be in _properties",
                    finalProperties.containsKey("country"));
            assertTrue("established should be in _properties",
                    finalProperties.containsKey("established"));

            log.info("Merge更新验证通过:");
            log.info("  - 更新字段: factoryLocation={}, capacity={}",
                    updatedEntity.getFactoryLocation(), updatedEntity.get("capacity"));
            log.info("  - 新增字段: country={}, established={}",
                    updatedEntity.get("country"), updatedEntity.get("established"));
            log.info("  - 保留字段: department={}, managerUserId={}",
                    updatedEntity.get("department"), updatedEntity.getManagerUserId());
            log.info("  - 版本号: {} -> {}", savedEntity.get_version(), updatedEntity.get_version());
            log.info("  - 字段提取正确: JSONB字段在直接属性中，动态属性在_properties中");
        });
    }

    /**
     * 测试 Object 类型属性的 JSON 序列化
     * 包括 Map 和自定义对象（CheckoutInfo）
     */
    @Test
    @Order(11)
    public void testObjectTypeJsonSerialization() {
        log.info("=== 开始测试 Object 类型属性的 JSON 序列化 ===");

        TimerUtils.measureExecutionTime("Object 类型 JSON 序列化测试", () -> {
            // 1. 使用 DSL 创建类型定义
            String dsl = """
                    create type com.example.CADComponentEmbeddingComplexObject extends io.emop.model.cad.CADComponent {
                        attribute cidProps: Object {
                            required: true
                            description: "分类信息"
                        }
                        attribute checkoutInfo: Object {
                            required: true
                            description: "签出信息"
                        }
                        schema: SAMPLE
                        tableName: CAD_Component_Embedding_Complex_Object
                    } if not exists
                    """;

            try {
                Object result = S.service(DSLExecutionService.class).execute(dsl);
                assertNotNull(result);
                log.info("创建类型定义结果: {}", result);
            } catch (Exception e) {
                log.warn("类型可能已存在，跳过创建: {}", e.getMessage());
            }

            // 2. 创建测试对象
            String code = "CAD-OBJ-" + dateCode;
            CADComponent cadComponent = new CADComponent("CADComponentEmbeddingComplexObject");

            cadComponent.setCode(code);
            cadComponent.setRevId(revId);
            cadComponent.setName("Test CAD Component with Complex Objects");
            cadComponent.setComponentType("ASM");

            // 2.1 设置 cidProps 为 Map
            Map<String, Object> cidProps = new HashMap<>();
            cidProps.put("category", "Mechanical");
            cidProps.put("subCategory", "Fastener");
            cidProps.put("material", "Steel");
            cidProps.put("weight", 0.5);
            cidProps.put("dimensions", Map.of(
                    "length", 10.5,
                    "width", 5.2,
                    "height", 3.1
            ));
            cidProps.put("certifications", List.of("ISO9001", "RoHS", "CE"));
            cadComponent.set("cidProps", cidProps);

            // 2.2 设置 checkoutInfo 为自定义对象
            CheckoutInfo checkoutInfo = CheckoutInfo.createCheckout("Testing object serialization", 60);
            cadComponent.set("checkoutInfo", checkoutInfo);

            log.info("保存前对象状态:");
            log.info("  - cidProps (Map): {}", (Object) cadComponent.get("cidProps"));
            log.info("  - checkoutInfo (CheckoutInfo): {}", (Object) cadComponent.get("checkoutInfo"));

            // 3. 保存对象
            ObjectService objectService = S.service(ObjectService.class);
            ModelObject savedObject = objectService.save(cadComponent);
            assertNotNull(savedObject.getId());
            log.info("对象已保存，ID: {}", savedObject.getId());

            // 3.1 确认数据库是JSONB格式而不是简单的字符串
            List<List<?>> result = S.service(NativeSqlService.class).executeNativeQuery(
                                "select cidprops->'category' as category from sample.cad_component_embedding_complex_object where id=?",
                                savedObject.getId());
            assertEquals("\"Mechanical\"", result.get(0).get(0));

            // 4. 从数据库重新查询验证
            ModelObject retrievedObject = new ObjectRef<>(savedObject.getId()).unbox();
            assertNotNull(retrievedObject);

            // 4.1 验证 Map 类型的 cidProps
            Object retrievedCidProps = retrievedObject.get("cidProps");
            assertNotNull("cidProps should not be null", retrievedCidProps);
            assertTrue("cidProps should be a Map", retrievedCidProps instanceof Map);

            Map<String, Object> retrievedCidPropsMap = (Map<String, Object>) retrievedCidProps;
            assertEquals("Mechanical", retrievedCidPropsMap.get("category"));
            assertEquals("Fastener", retrievedCidPropsMap.get("subCategory"));
            assertEquals("Steel", retrievedCidPropsMap.get("material"));
            assertEquals(0.5, ((Number) retrievedCidPropsMap.get("weight")).doubleValue(), 0.001);

            // 验证嵌套 Map
            Map<String, Object> dimensions = (Map<String, Object>) retrievedCidPropsMap.get("dimensions");
            assertNotNull("dimensions should not be null", dimensions);
            assertEquals(10.5, ((Number) dimensions.get("length")).doubleValue(), 0.001);
            assertEquals(5.2, ((Number) dimensions.get("width")).doubleValue(), 0.001);
            assertEquals(3.1, ((Number) dimensions.get("height")).doubleValue(), 0.001);

            // 验证 List
            List<String> certifications = (List<String>) retrievedCidPropsMap.get("certifications");
            assertNotNull("certifications should not be null", certifications);
            assertEquals(3, certifications.size());
            assertTrue(certifications.contains("ISO9001"));
            assertTrue(certifications.contains("RoHS"));
            assertTrue(certifications.contains("CE"));

            // 4.2 验证自定义对象 checkoutInfo
            Object retrievedCheckoutInfo = retrievedObject.get("checkoutInfo");
            assertNotNull("checkoutInfo should not be null", retrievedCheckoutInfo);
            assertTrue("checkoutInfo should be a CheckoutInfo",
                    retrievedCheckoutInfo instanceof CheckoutInfo);

            CheckoutInfo checkoutInfoMap =  (CheckoutInfo) retrievedCheckoutInfo;
            assertNotNull("checkedoutByUserId should not be null",
                    checkoutInfoMap.getCheckedoutByUserId());
            assertEquals("Testing object serialization", checkoutInfoMap.getComment());
            assertEquals(60, ((Number) checkoutInfoMap.getExpiryMinutes()).intValue());
            assertNotNull("checkoutDate should not be null", checkoutInfoMap.getCheckoutDate());

            log.info("查询验证通过:");
            log.info("  - cidProps 正确序列化为 JSON: {}", retrievedCidProps);
            log.info("  - checkoutInfo 正确序列化为 JSON: {}", retrievedCheckoutInfo);

            // 5. 测试更新操作
            Map<String, Object> updateData = new HashMap<>();

            // 更新 cidProps
            Map<String, Object> updatedCidProps = new HashMap<>(cidProps);
            updatedCidProps.put("material", "Aluminum");
            updatedCidProps.put("supplier", "ACME Corp");
            updateData.put("cidProps", updatedCidProps);

            // 更新 checkoutInfo
            CheckoutInfo updatedCheckoutInfo = CheckoutInfo.createCheckout("Updated checkout", 120);
            updateData.put("checkoutInfo", updatedCheckoutInfo);

            objectService.update(savedObject.getId(), savedObject.get("_version"), updateData);

            // 5.1 确认数据库是JSONB格式而不是简单的字符串
            result = S.service(NativeSqlService.class).executeNativeQuery(
                                "select cidprops->'material' as category from sample.cad_component_embedding_complex_object where id=?",
                                savedObject.getId());
            assertEquals("\"Aluminum\"", result.get(0).get(0));

            // 6. 验证更新后的数据
            ModelObject updatedObject = new ObjectRef<>(savedObject.getId()).unbox();

            Map<String, Object> finalCidProps = updatedObject.get("cidProps", Map.class);
            assertEquals("Aluminum", finalCidProps.get("material"));
            assertEquals("ACME Corp", finalCidProps.get("supplier"));
            assertEquals("Mechanical", finalCidProps.get("category")); // 应该保留

            CheckoutInfo finalCheckoutInfo = updatedObject.get("checkoutInfo", CheckoutInfo.class);
            assertEquals("Updated checkout", finalCheckoutInfo.getComment());
            assertEquals(120, finalCheckoutInfo.getExpiryMinutes());

            log.info("更新验证通过:");
            log.info("  - cidProps 更新后: {}", finalCidProps);
            log.info("  - checkoutInfo 更新后: {}", finalCheckoutInfo);

            // 7. 测试 fastUpdate
            Map<String, Object> fastUpdateData = new HashMap<>();
            Map<String, Object> fastCidProps = new HashMap<>();
            fastCidProps.put("status", "active");
            fastCidProps.put("lastModified", System.currentTimeMillis());
            fastUpdateData.put("cidProps", fastCidProps);

            objectService.fastUpdate(savedObject.getId(), fastUpdateData);
            result = S.service(NativeSqlService.class).executeNativeQuery(
                                "select cidprops->'status' as category from sample.cad_component_embedding_complex_object where id=?",
                                savedObject.getId());
            assertEquals("\"active\"", result.get(0).get(0));

            ModelObject fastUpdatedObject = new ObjectRef<>(savedObject.getId()).unbox();
            Map<String, Object> fastFinalCidProps = fastUpdatedObject.get("cidProps", Map.class);
            assertEquals("active", fastFinalCidProps.get("status"));
            assertNotNull(fastFinalCidProps.get("lastModified"));

            log.info("FastUpdate 验证通过:");
            log.info("  - cidProps 快速更新后: {}", fastFinalCidProps);

            log.info("=== Object 类型 JSON 序列化测试全部通过 ===");
        });
    }

    /**
     * 测试复杂的JSONB场景
     * 包括空值处理、类型转换、嵌套对象等
     */
    @Test
    @Order(12)
    public void testJsonbComplexScenarios() {
        log.info("=== 开始测试JSONB复杂场景 ===");

        TimerUtils.measureExecutionTime("JSONB复杂场景测试", () -> {
            // 1. 测试空值和null处理
            FactoryEntity entity = new FactoryEntity();
            String code = "ComplexTest-" + dateCode;
            entity.setName(code);
            entity.setCode(code);
            entity.setRevId(revId);
            entity.setFactoryLocation("Dalian");
            entity.setManagerUserId(800L);

            // 设置包含null值的动态属性
            entity.set("nullField", null);
            entity.set("emptyString", "");
            entity.set("zeroNumber", 0);
            entity.set("falseBoolean", false);
            entity.set("emptyList", List.of());
            entity.set("emptyMap", Map.of());

            ObjectService objectService = S.service(ObjectService.class);
            FactoryEntity savedEntity = objectService.save(entity);

            // 验证null和空值的正确处理
            FactoryEntity retrieved = S.service(RevisionService.class)
                    .queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), code, revId, FactoryEntity.class));

            assertNull(retrieved.get("nullField"));
            assertEquals("", retrieved.get("emptyString"));
            assertEquals(Integer.valueOf(0), retrieved.get("zeroNumber"));
            assertEquals(Boolean.FALSE, retrieved.get("falseBoolean"));
            assertEquals(List.of(), retrieved.get("emptyList"));
            assertEquals(Map.of(), retrieved.get("emptyMap"));

            // 验证JSONB字段正确提取
            Map<String, Object> properties = retrieved.get_properties();
            assertFalse("factoryLocation should not be in _properties",
                    properties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties",
                    properties.containsKey("managerUserId"));
            assertEquals("Dalian", retrieved.getFactoryLocation());
            assertEquals(Long.valueOf(800), retrieved.getManagerUserId());

            // 2. 测试更新时的null值处理 - 使用update()方法
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("nullField", "notNullAnymore");  // null -> 有值
            updateData.put("newNullField", null);           // 新增null字段
            updateData.put("emptyString", "notEmpty");      // 空字符串 -> 有值

            ModelObject updated = objectService.update(retrieved.getId(), retrieved.get_version(), updateData);

            FactoryEntity finalRetrieved = S.service(RevisionService.class)
                    .queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), code, revId, FactoryEntity.class));

            // 验证null值更新
            assertEquals("notNullAnymore", finalRetrieved.get("nullField"));
            assertNull(finalRetrieved.get("newNullField"));
            assertEquals("notEmpty", finalRetrieved.get("emptyString"));

            // 验证其他字段保留
            assertEquals(Integer.valueOf(0), finalRetrieved.get("zeroNumber"));
            assertEquals(Boolean.FALSE, finalRetrieved.get("falseBoolean"));
            assertEquals(List.of(), finalRetrieved.get("emptyList"));
            assertEquals(Map.of(), finalRetrieved.get("emptyMap"));

            // 验证JSONB字段仍然正确提取
            Map<String, Object> finalProperties = finalRetrieved.get_properties();
            assertFalse("factoryLocation should not be in _properties after complex update",
                    finalProperties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties after complex update",
                    finalProperties.containsKey("managerUserId"));
            assertEquals("Dalian", finalRetrieved.getFactoryLocation());
            assertEquals(Long.valueOf(800), finalRetrieved.getManagerUserId());

            log.info("复杂场景测试验证通过:");
            log.info("  - null值处理: nullField={}, newNullField={}",
                    finalRetrieved.get("nullField"), finalRetrieved.get("newNullField"));
            log.info("  - 空值保留: zeroNumber={}, falseBoolean={}, emptyList={}, emptyMap={}",
                    finalRetrieved.get("zeroNumber"), finalRetrieved.get("falseBoolean"),
                    finalRetrieved.get("emptyList"), finalRetrieved.get("emptyMap"));
            log.info("  - JSONB字段提取: factoryLocation={}, managerUserId={}",
                    finalRetrieved.getFactoryLocation(), finalRetrieved.getManagerUserId());
        });
    }

    /**
     * 测试ObjectService.fastUpdate方法的JSONB更新和字段提取
     */
    @Test
    @Order(10)
    public void testJsonbFastUpdate() {
        log.info("=== 开始测试JSONB FastUpdate功能 ===");

        TimerUtils.measureExecutionTime("JSONB FastUpdate测试", () -> {
            // 创建测试数据
            FactoryEntity entity = new FactoryEntity();
            String code = "FastUpdateTest-" + dateCode;
            entity.setName(code);
            entity.setCode(code);
            entity.setRevId(revId);
            entity.setFactoryLocation("Tianjin");
            entity.setManagerUserId(500L);
            entity.set("priority", "high");
            entity.set("budget", 1000000);
            entity.set("tags", List.of("production", "automotive"));

            ObjectService objectService = S.service(ObjectService.class);
            entity = objectService.save(entity);

            // 使用fastUpdate（跳过版本控制）
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("factoryLocation", "Wuhan");       // JSONB字段
            updateData.put("priority", "medium");             // JSONB字段
            updateData.put("region", "Central");              // 新增JSONB字段
            updateData.put("name", "FastUpdated-" + code);    // 普通列

            Map<String, Object> affected = objectService.fastUpdate(entity.getId(), updateData);
            assertFalse(affected.isEmpty());

            // 验证结果
            FactoryEntity updated = S.service(RevisionService.class)
                    .queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), code, revId, FactoryEntity.class));

            // 验证更新的字段
            assertEquals("FastUpdated-" + code, updated.getName());  // 普通列
            assertEquals("Wuhan", updated.getFactoryLocation());     // JSONB字段
            assertEquals("medium", updated.get("priority"));         // JSONB字段
            assertEquals("Central", updated.get("region"));          // 新增JSONB字段

            // 验证保留的字段（merge行为）
            assertEquals(code, updated.getCode());                   // 普通列保持不变
            assertEquals(Long.valueOf(500), updated.getManagerUserId());  // JSONB字段保持不变
            assertEquals(Integer.valueOf(1000000), updated.get("budget"));  // JSONB字段保持不变
            assertEquals(List.of("production", "automotive"), updated.get("tags"));  // JSONB字段保持不变

            // 关键验证：@QuerySqlField(mapToDBColumn = false) 的字段不在_properties中
            Map<String, Object> properties = updated.get_properties();
            assertFalse("factoryLocation should not be in _properties after fastUpdate",
                    properties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties after fastUpdate",
                    properties.containsKey("managerUserId"));

            // 验证动态属性在_properties中
            assertTrue("priority should be in _properties",
                    properties.containsKey("priority"));
            assertTrue("region should be in _properties",
                    properties.containsKey("region"));
            assertTrue("budget should be in _properties",
                    properties.containsKey("budget"));
            assertTrue("tags should be in _properties",
                    properties.containsKey("tags"));

            // 验证版本号递增（即使是fastUpdate也会处理版本）
            assertTrue(updated.get_version() > entity.get_version());

            log.info("FastUpdate测试验证通过:");
            log.info("  - 普通列更新: name={}", updated.getName());
            log.info("  - JSONB字段更新: factoryLocation={}, priority={}, region={}",
                    updated.getFactoryLocation(), updated.get("priority"), updated.get("region"));
            log.info("  - 保留字段: managerUserId={}, budget={}, tags={}",
                    updated.getManagerUserId(), updated.get("budget"), updated.get("tags"));
            log.info("  - 版本号: {} -> {}", entity.get_version(), updated.get_version());
            log.info("  - 字段提取正确: JSONB字段提取到直接属性，动态属性保留在_properties中");
        });
    }

    /**
     * 测试只更新单个JSONB字段的情况
     */
    @Test
    @Order(8)
    public void testJsonbPartialUpdate() {
        log.info("=== 开始测试JSONB单字段更新 ===");

        TimerUtils.measureExecutionTime("JSONB单字段更新测试", () -> {
            // 创建测试数据
            FactoryEntity entity = new FactoryEntity();
            String code = "PartialTest-" + dateCode;
            entity.setName(code);
            entity.setCode(code);
            entity.setRevId(revId);
            entity.setFactoryLocation("Beijing");
            entity.setManagerUserId(300L);
            entity.set("status", "active");
            entity.set("employees", 150);
            entity.set("products", List.of("ProductA", "ProductB", "ProductC"));

            entity = S.service(ObjectService.class).save(entity);

            // 只更新一个字段
            ObjectService objectService = S.service(ObjectService.class);
            Map<String, Object> updateData = Map.of("employees", 180);

            ModelObject updatedResult = objectService.update(entity.getId(), entity.get_version(), updateData);
            assertNotNull(updatedResult);

            // 验证结果
            FactoryEntity updated = S.service(RevisionService.class)
                    .queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), code, revId, FactoryEntity.class));

            // 验证更新的字段
            assertEquals(Integer.valueOf(180), updated.get("employees"));

            // 验证其他字段保持不变
            assertEquals("Beijing", updated.getFactoryLocation());
            assertEquals(Long.valueOf(300), updated.getManagerUserId());
            assertEquals("active", updated.get("status"));
            assertEquals(List.of("ProductA", "ProductB", "ProductC"), updated.get("products"));

            // 验证JSONB字段正确提取
            Map<String, Object> properties = updated.get_properties();
            assertFalse("factoryLocation should not be in _properties",
                    properties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties",
                    properties.containsKey("managerUserId"));

            // 验证动态属性在_properties中
            assertTrue("employees should be in _properties",
                    properties.containsKey("employees"));
            assertTrue("status should be in _properties",
                    properties.containsKey("status"));
            assertTrue("products should be in _properties",
                    properties.containsKey("products"));

            log.info("单字段更新验证通过: employees更新为180，其他字段保持不变，字段提取正确");
        });
    }

    /**
     * 测试混合字段更新（普通列 + JSONB字段）和字段提取
     */
    @Test
    @Order(9)
    public void testJsonbMixedFieldUpdate() {
        log.info("=== 开始测试混合字段更新 ===");

        TimerUtils.measureExecutionTime("混合字段更新测试", () -> {
            // 创建测试数据
            FactoryEntity entity = new FactoryEntity();
            String code = "MixedTest-" + dateCode;
            entity.setName(code);
            entity.setCode(code);
            entity.setRevId(revId);
            entity.setFactoryLocation("Hangzhou");
            entity.setManagerUserId(400L);
            entity.set("shift", "day");
            entity.set("quality", "A");

            entity = S.service(ObjectService.class).save(entity);

            // 同时更新普通列和JSONB字段
            ObjectService objectService = S.service(ObjectService.class);
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("name", "Updated-" + code);          // 普通列
            updateData.put("factoryLocation", "Suzhou");        // JSONB字段
            updateData.put("shift", "night");                   // JSONB字段
            updateData.put("overtime", true);                   // 新增JSONB字段

            ModelObject updatedResult = objectService.update(entity.getId(), entity.get_version(), updateData);
            assertNotNull(updatedResult);

            // 验证结果
            FactoryEntity updated = S.service(RevisionService.class)
                    .queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), code, revId, FactoryEntity.class));

            // 验证普通列更新
            assertEquals("Updated-" + code, updated.getName());

            // 验证JSONB字段更新
            assertEquals("Suzhou", updated.getFactoryLocation());
            assertEquals("night", updated.get("shift"));
            assertEquals(Boolean.TRUE, updated.get("overtime"));

            // 验证保留的字段
            assertEquals(code, updated.getCode());  // 普通列保持不变
            assertEquals(Long.valueOf(400), updated.getManagerUserId());  // JSONB字段保持不变
            assertEquals("A", updated.get("quality"));  // JSONB字段保持不变

            // 关键验证：字段提取行为
            Map<String, Object> properties = updated.get_properties();
            assertFalse("factoryLocation should not be in _properties",
                    properties.containsKey("factoryLocation"));
            assertFalse("managerUserId should not be in _properties",
                    properties.containsKey("managerUserId"));

            // 动态属性应该在_properties中
            assertTrue("shift should be in _properties",
                    properties.containsKey("shift"));
            assertTrue("quality should be in _properties",
                    properties.containsKey("quality"));
            assertTrue("overtime should be in _properties",
                    properties.containsKey("overtime"));

            log.info("混合字段更新验证通过:");
            log.info("  - 普通列更新: name={}", updated.getName());
            log.info("  - JSONB字段更新: factoryLocation={}, shift={}, overtime={}",
                    updated.getFactoryLocation(), updated.get("shift"), updated.get("overtime"));
            log.info("  - 保留字段: code={}, managerUserId={}, quality={}",
                    updated.getCode(), updated.getManagerUserId(), updated.get("quality"));
            log.info("  - 字段提取正确: 标注字段在直接属性，动态属性在_properties");
        });
    }

    @Test
    @Order(4)
    public void queryByCustomProperty() {
        FactoryEntity entity = new FactoryEntity();
        String code = "FactoryEntity-" + dateCode + "--1";
        entity.setName(code);
        entity.setCode(code);
        entity.setRevId(revId);
        entity.setFactoryLocation("Zhuhai");
        entity.setManagerUserId(100l);
        S.service(ObjectService.class).save(entity);

        // 测试JSONB字段查询
        List<FactoryEntity> entities = Q.result(FactoryEntity.class)
                .where("code=? AND CAST(_properties->>'managerUserId' AS INT) > ?", code, 99)
                .query();
        log.info("query result: {}", entities);
        assertEquals(1, entities.size());
        assertEquals((Long) 100l, (Long) entities.get(0).getManagerUserId());
        assertEquals("Zhuhai", entities.get(0).getFactoryLocation());

        entities = Q.result(FactoryEntity.class)
                .where("code=? and CAST(_properties->>'managerUserId' AS INT) >?", code, 101)
                .query();
        assertEquals(0, entities.size());

        entities = Q.result(FactoryEntity.class)
                .where("code=? AND CAST(_properties->>'managerUserId' AS INT) > ? and CAST(_properties->>'factoryLocation' AS TEXT)=?",
                        code, 99, "Zhuhai")
                .query();
        assertEquals(1, entities.size());

        // 测试SQL原生查询
        List<List<?>> result = Q.result(FactoryEntity.class)
                .sql("select id, CASE _properties->>'factoryLocation' WHEN 'Zhuhai' THEN 'GuangDong' ELSE 'Other' END AS province from common.Item_Revision where code=?", code)
                .queryRaw();
        assertTrue(result.size() > 0);
        assertEquals(2, result.get(0).size());
        assertEquals("GuangDong", result.get(0).get(1));

        log.info("自定义属性查询测试通过");
    }

    //新class对象，新table存储
    @Test
    @Order(2)
    public void newClassAndNewTable() {
        TimerUtils.measureExecutionTime("批量创建 " + batchSize + " UserLocationEntity对象", batchSaveUserLocationEntity(batchSize));
        RevisionService revisionService = S.service(RevisionService.class);
        TimerUtils.measureExecutionTime("查询UserLocationEntity对象", () -> {
            UserLocationEntity entity = revisionService.queryRevision(new CriteriaByCodeAndRevId<>(UserLocationEntity.class.getName(), "UserLocationEntity-" + dateCode + "-1", revId));
            log.info("query result: {}", entity);
            assertNotNull(entity);
            assertEquals("Zhuhai-1", entity.getCity());
            assertEquals("Guangdong", entity.getCountry());
        });
    }

    //新class对象，现有table存储
    @Test
    @Order(1)
    public void newClassAndExistingTable() {
        TimerUtils.measureExecutionTime("批量创建 " + batchSize + " FactoryEntity对象", batchSaveFactoryEntity(batchSize));
        RevisionService revisionService = S.service(RevisionService.class);
        TimerUtils.measureExecutionTime("查询FactoryEntity对象", () -> {
            FactoryEntity entity = revisionService.queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), "FactoryEntity-" + dateCode + "-1", revId, FactoryEntity.class));
            log.info("query result: {}", entity);
            assertNotNull(entity);
            assertEquals("Zhuhai-1", entity.getFactoryLocation());
            assertEquals(Long.valueOf("1"), entity.getManagerUserId());
        });
    }

    //现有class对象，现有table存储，增加新的属性
    @Test
    @Order(3)
    public void existingClassAndExistingTable() {
        TimerUtils.measureExecutionTime("批量创建 " + batchSize + " ItemRevision对象", () -> {
            List<ItemRevision> data = IntStream.rangeClosed(1, batchSize).mapToObj(idx -> {
                ItemRevision rev = ItemRevision.newModel("C-" + dateCode + "-" + idx, revId);
                rev.setName("新增自定义属性-" + dateCode + "-" + idx);
                rev.set("managerUserId", Long.valueOf("2"));
                rev.set("managerId", Long.valueOf("1"));
                rev.set("managerTitles", List.of("dep1 manager", "dep2 manager"));
                return rev;
            }).collect(Collectors.toList());
            S.service(ObjectService.class).saveAll(data);
        });
        TimerUtils.measureExecutionTime("查询FactoryEntity对象", () -> {
            ItemRevision entity = S.service(RevisionService.class).queryRevision(new CriteriaByCodeAndRevId<>(FactoryEntity.class.getName(), "C-" + dateCode + "-1", revId));
            assertTrue(entity instanceof FactoryEntity factoryEntity);
            log.info("query result: {}", entity);
            assertNotNull(entity);
            assertEquals(Long.valueOf("2"), ((FactoryEntity) entity).getManagerUserId());
            //由于存在 jsonb 中, deserialize 回来会是 int
            assertEquals(Integer.valueOf("1"), entity.get("managerId"));
            assertEquals(List.of("dep1 manager", "dep2 manager"), entity.get("managerTitles"));
        });
    }

    private static Runnable batchSaveFactoryEntity(int count) {
        return new Runnable() {
            @Override
            public void run() {
                List<FactoryEntity> data = IntStream.rangeClosed(1, count).mapToObj(idx -> {
                    FactoryEntity entity = new FactoryEntity();
                    entity.setName("FactoryEntity-" + dateCode + "-" + idx);
                    entity.setCode("FactoryEntity-" + dateCode + "-" + idx);
                    entity.setRevId(revId);
                    entity.setFactoryLocation("Zhuhai-" + idx);
                    entity.setManagerUserId(1l);
                    return entity;
                }).collect(Collectors.toList());
                S.service(ObjectService.class).saveAll(data);
            }
        };
    }

    private static Runnable batchSaveUserLocationEntity(int count) {
        return new Runnable() {
            @Override
            public void run() {
                List<UserLocationEntity> data = IntStream.rangeClosed(1, count).mapToObj(idx -> {
                    UserLocationEntity entity = new UserLocationEntity();
                    entity.setName("UserLocationEntity-" + dateCode + "-" + idx);
                    entity.setCode("UserLocationEntity-" + dateCode + "-" + idx);
                    entity.setRevId(revId);
                    entity.setCity("Zhuhai-" + idx);
                    entity.setCountry("Guangdong");
                    entity.setUserId(Long.valueOf(idx));
                    return entity;
                }).collect(Collectors.toList());
                S.service(ObjectService.class).saveAll(data);
            }
        };
    }
}