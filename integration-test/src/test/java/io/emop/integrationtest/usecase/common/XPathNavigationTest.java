package io.emop.integrationtest.usecase.common;

import io.emop.model.common.ModelObject;
import io.emop.model.common.ObjectRef;
import io.emop.model.common.Settable;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleMaterial;
import io.emop.service.S;
import io.emop.service.api.dsl.DSLExecutionService;
import io.emop.service.api.data.XPathCreationContext;
import io.emop.service.api.data.XPathCreationContext.*;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.emop.integrationtest.util.Assertion.*;

@Slf4j
public class XPathNavigationTest implements Runnable {
    private transient DSLExecutionService dslService;
    private transient DataProvider globalProvider;

    @Override
    public void run() {
        dslService = S.service(DSLExecutionService.class);
        setupRequiredAttributesProvider();

        log.info("Testing XPath navigation and creation");
        final String date = String.valueOf(System.currentTimeMillis());
        Long productId = setupTestData(date);
        testXPathNavigation(productId);
        testXPathCreation(productId);
        testXPathErrors(productId);
        testXPathWithRequiredAttributes(productId);
        log.info("XPathNavigationTest is done");
    }

    @FunctionalInterface
    interface SerializableFunction<T, R> extends Function<T, R>, Serializable {
    }

    private void setupRequiredAttributesProvider() {
        globalProvider = new DefaultDataProvider()
                .register("specification/specification", new SerializableFunction<String, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(String path) {
                        return Map.of(
                                "code", "SPEC-" + System.currentTimeMillis(),
                                "revId", "A",
                                "name", "Auto Created Specs" + System.currentTimeMillis(),
                                "strength", 100
                        );
                    }
                })
                .register("specification", new SerializableFunction<String, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(String path) {
                        return Map.of(
                                "code", "ATTR-" + System.currentTimeMillis(),
                                "revId", "A",
                                "name", "Auto Created Attributes" + System.currentTimeMillis(),
                                "color", "blue"
                        );
                    }
                })
                .register("material", new SerializableFunction<String, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(String path) {
                        return Map.of(
                                "code", "MAT-" + System.currentTimeMillis(),
                                "revId", "A",
                                "name", "Auto Created Material" + System.currentTimeMillis()
                        );
                    }
                });
    }

    private Long setupTestData(String date) {
        // 1. 创建产品相关的类型
        String createTypesDsl = """
                create type sample.ProductSpecs extends GenericModelObject {
                    attribute strength: Integer
                    attribute weight: Double
                }
                                
                create type sample.ProductAttributes extends GenericModelObject {
                    attribute color: String
                    -> ProductSpecs as specification
                }
                                
                create type sample.ProductX extends ItemRevision {
                    -> ProductAttributes as specification
                    -> SampleMaterial as material
                }
                """;

        try {
            Object result = dslService.execute(createTypesDsl);
            log.info("Create types result: " + result);
        } catch (Exception e) {
            log.info("types are already created, " + e.getMessage());
        }

        // 2. 创建产品实例
        String createProductDsl = String.format("""
                create object ProductX {
                    code: "P%s",
                    revId: "A",
                    name: "Test Product"
                }
                """, date);

        Object result = dslService.execute(createProductDsl);
        log.info("Create product result: " + result);

        return extractID(result.toString());
    }

    private void testXPathNavigation(Long productId) {
        ModelObject product = new ObjectRef(productId).unbox();

        // 1. 测试基本导航和设置 - 使用allowCreate来创建路径上的必要对象
        ModelObjectTargetInfo info = product.ensureXPathTarget("specification/color",
                XPathCreationContext.allowCreateAndUpdate(globalProvider));
        assertEquals("[blue]", new ObjectRef(productId).unbox().get("specification/color").toString());

        // 2. 测试多级路径导航
        info = product.ensureXPathTarget("specification/specification/strength",
                XPathCreationContext.allowCreateAndUpdate(globalProvider));
        assertEquals("[100]", new ObjectRef(productId).unbox().get("specification/specification/strength").toString());

        // 3. 测试材料关系 - 先创建材料，再进行导航
        String createMaterialDsl = """
                create object SampleMaterial {
                    code: "M-001",
                    name: "Test Material",
                    revId: "A"
                }
                """;
        Q.result(SampleMaterial.class).where("code=?", "M-001").delete();
        Object result = dslService.execute(createMaterialDsl);
        Long materialId = extractID(result.toString());

        String createRelationDsl = String.format("""
                relation Product(%d) -> SampleMaterial(%d) as material
                """, productId, materialId);
        dslService.execute(createRelationDsl);

        // 现在导航到已存在的材料，不需要创建
        info = product.ensureXPathTarget("material[1]/name",
                XPathCreationContext.disallowCreateNorUpdate());
        assertEquals("Test Material", info.getTargetObjects().iterator().next().get("name"));
    }

    private void testXPathCreation(Long productId) {
        ModelObject product = new ObjectRef(productId).unbox();

        // 1. 测试自动创建带必填属性的对象
        DataProvider customProvider = new DefaultDataProvider()
                .register("specification/specification", new SerializableFunction<String, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(String path) {
                        return Map.of(
                                "code", "SPEC-" + System.currentTimeMillis(),
                                "revId", "A",
                                "weight", 75.5,
                                "strength", 150
                        );
                    }
                });
        // 2. 测试在已存在对象上设置新属性和更新属性
        ModelObjectTargetInfo info = product.ensureXPathTarget("specification/specification/weight",
                XPathCreationContext.allowCreateAndUpdate(customProvider));
        assertEquals("[75.5]", product.get("specification/specification/weight").toString());
        product.ensureXPathTarget("specification/specification/strength",
                XPathCreationContext.allowCreateAndUpdate(customProvider));
        assertEquals("[150]", product.get("specification/specification/strength").toString());
    }

    private void testXPathWithRequiredAttributes(Long productId) {
        ModelObject product = new ObjectRef(productId).unbox();

        // 1. 测试缺少必填属性时的错误
        DataProvider emptyProvider = new DefaultDataProvider();
        assertException(new Runnable() {
            @Override
            public void run() {
                product.ensureXPathTarget("specification/specification/newField",
                        XPathCreationContext.allowCreateAndUpdate(emptyProvider));
            }
        });

        // 2. 测试使用动态属性生成
        DataProvider dynamicProvider = new DefaultDataProvider()
                .register("specification/specification", new SerializableFunction<String, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(String path) {
                        return Map.of(
                                "code", "SPEC-" + path.hashCode(),
                                "revId", "A",
                                "name", "Dynamic Specs for " + path,
                                "material", "Dynamic Material for " + path
                        );
                    }
                });

        ModelObjectTargetInfo info = product.ensureXPathTarget("specification/specification/*",
                XPathCreationContext.allowCreateAndUpdate(dynamicProvider));
        ((Settable) info.getTargetObjects().iterator().next()).set(info.getAttributeName(), "test");

        // 验证动态生成的必填属性
        assertEquals("[Dynamic Material for specification/specification]",
                new ObjectRef(productId).unbox().get("specification/specification/material").toString());

        assertEquals("[Dynamic Specs for specification/specification]",
                new ObjectRef(productId).unbox().get("specification/specification/name").toString());

        // 3. 测试继承关系中的必填属性处理
        String createChildSpecsDsl = """
                create type sample.AdvancedProductSpecs extends ProductSpecs {
                    attribute additionalInfo: String {
                        required: true
                    }
                    schema: sample
                    tableName: ADVANCED_PRODUCT_SPECS
                }
                """;
        try {
            dslService.execute(createChildSpecsDsl);
        } catch (Exception e) {
            log.info("skip type creation: " + e.getMessage());
        }

        // 使用包含子类特有必填属性的提供者
        DataProvider advancedProvider = new DefaultDataProvider()
                .register("specification/specification", new SerializableFunction<String, Map<String, Object>>() {
                    @Override
                    public Map<String, Object> apply(String path) {
                        return Map.of(
                                "code", "ADV-SPEC-" + System.currentTimeMillis(),
                                "revId", "A",
                                "name", "Advanced Specs" + System.currentTimeMillis(),
                                "material", "Advanced Material",
                                "additionalInfo", "Extra Info"
                        );
                    }
                });

        try {
            info = product.ensureXPathTarget("specification/specification/advancedField",
                    XPathCreationContext.allowCreateAndUpdate(advancedProvider));
            fail("not reachable");
        } catch (Exception e) {
            e.getMessage().contains("please define required data used to create instance of sample.ProductSpecs");
        }
    }

    private void testXPathErrors(Long productId) {
        ModelObject product = new ObjectRef(productId).unbox();

        // 1. 测试绝对路径
        assertException(new Runnable() {
            @Override
            public void run() {
                product.ensureXPathTarget("/specification/color",
                        XPathCreationContext.allowCreateAndUpdate(globalProvider));
            }
        });

        // 2. 测试禁止创建时访问不存在的路径
        assertException(new Runnable() {
            @Override
            public void run() {
                product.ensureXPathTarget("nonexistent/path",
                        XPathCreationContext.disallowCreateNorUpdate());
            }
        });

        // 3. 测试无效的数组索引
        assertException(new Runnable() {
            @Override
            public void run() {
                product.ensureXPathTarget("materials[99]/name",
                        XPathCreationContext.allowCreateAndUpdate(globalProvider));
            }
        });

        // 4. 测试不支持的属性类型
        assertException(new Runnable() {
            @Override
            public void run() {
                product.ensureXPathTarget("name/invalid",
                        XPathCreationContext.allowCreateAndUpdate(globalProvider));
            }
        });

        // 5. 测试循环引用的错误处理
        assertException(new Runnable() {
            @Override
            public void run() {
                product.ensureXPathTarget("specification/specification/attrs/loop",
                        XPathCreationContext.allowCreateAndUpdate(globalProvider));
            }
        });
    }

    private Long extractID(String logString) {
        Pattern pattern = Pattern.compile("ID: (-?\\d+)");
        Matcher matcher = pattern.matcher(logString);
        if (matcher.find()) {
            String idString = matcher.group(1);
            return Long.parseLong(idString);
        }
        throw new IllegalArgumentException("No ID found in the given string");
    }
}