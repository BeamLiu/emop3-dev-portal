package io.emop.integrationtest.usecase.common;

import io.emop.integrationtest.domain.SampleDataset;
import io.emop.integrationtest.domain.SampleDocument;
import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.ObjectRef;
import io.emop.model.common.UserContext;
import io.emop.model.metadata.*;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.AssociateRelationService;
import io.emop.service.api.dsl.DSLExecutionService;
import io.emop.service.api.metadata.MetadataService;
import io.emop.service.api.relation.RelationType;
import io.emop.service.api.storage.StorageService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.emop.integrationtest.util.Assertion.*;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DSLExecutionTest {

    private String bucketPath = "temp:testcase" + System.currentTimeMillis();

    static {
        System.setProperty("minio.endpoint", "http://storage-${EMOP_DOMAIN}:9000");
        System.setProperty("minio.accessKey", "minioadmin");
        System.setProperty("minio.secretKey", "EmopIs2Fun!");
    }

    @BeforeAll
    public void setupAll() {
        UserContext.setCurrentUser(new UserContext(100l, List.of("admin")));
        UserContext.ensureCurrent().byPass(() -> {
            testDeleteType();
            testSetupImportTestTypes();
        });
    }

    @Test
    @Order(1)
    public void testValueDomainDSL() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testValueDomainDSLImpl();
            });
        });
    }

    @Test
    @Order(2)
    public void testCreateType() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testCreateTypeImpl();
            });
        });
    }

    @Test
    @Order(3)
    public void testRelationshipDefinitions() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testRelationshipDefinitionsImpl();
            });
        });
    }

    @Test
    @Order(4)
    public void testUpdateType() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testUpdateTypeImpl();
            });
        });
    }

    @Test
    @Order(5)
    public void testCreateObject() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                Long id = testCreateObjectImpl();
                testDescCommand(id);
            });
        });
    }

    @Test
    @Order(6)
    public void testCreateObjectWithIfNotExists() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testCreateObjectWithIfNotExistsImpl();
            });
        });
    }

    @Test
    @Order(7)
    public void testUpdateObject() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testUpdateObjectImpl();
            });
        });
    }

    @Test
    @Order(8)
    public void testDeleteObject() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testDeleteObjectImpl();
            });
        });
    }

    @Test
    @Order(9)
    public void testUpdateTypeWithMultilang() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testUpdateTypeWithMultilangImpl();
            });
        });
    }

    @Test
    @Order(10)
    public void testUpdateTypeWithAttributeMultilang() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testUpdateTypeWithAttributeMultilangImpl();
            });
        });
    }

    @Test
    @Order(11)
    public void testUpdateObjectWithMultilang() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testUpdateObjectWithMultilangImpl();
            });
        });
    }

    @Test
    @Order(12)
    public void testDocumentRelations() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testDocumentRelationsImpl();
            });
        });
    }

    @Test
    @Order(13)
    public void testPlainImport() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testPlainImportImpl();
            });
        });
    }

    @Test
    @Order(14)
    public void testTreeImport() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testTreeImportImpl();
            });
        });
    }

    @Test
    @Order(15)
    public void testImportWithExistence() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testImportWithExistenceImpl();
            });
        });
    }

    @Test
    @Order(16)
    public void testImportWithXpath() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testImportWithXpathImpl();
            });
        });
    }

    @Test
    @Order(17)
    public void testImportWithTypeResolver() {
        UserContext.runAsSystem(() -> {
            UserContext.ensureCurrent().byPass(() -> {
                testImportWithTypeResolverImpl();
            });
        });
    }

    // ================ 实现方法（保持原有逻辑不变）================

    private void testDeleteType() {
        try {
            Object result = S.service(DSLExecutionService.class).execute("delete type user.VIPCustomer");
            assertNotNull(result);
            log.info("dsl result: " + result);
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }
        try {
            Object result = S.service(DSLExecutionService.class).execute("delete type sample.TaxDataset");
            assertNotNull(result);
            log.info("dsl result: " + result);
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }
        try {
            S.service(DSLExecutionService.class).execute("delete object Customer where code='CUS-1'");
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }
        try {
            Object result = S.service(DSLExecutionService.class).execute("delete type user.Customer");
            assertNotNull(result);
            log.info("dsl result: " + result);
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }
        try {
            Object result = S.service(DSLExecutionService.class).execute("delete type test.Department");
            assertNotNull(result);
            log.info("dsl result: " + result);
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }
        try {
            Object result = S.service(DSLExecutionService.class).execute("delete type test.Employee");
            assertNotNull(result);
            log.info("dsl result: " + result);
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }
    }

    private void testRelationshipDefinitionsImpl() {
        String dsl = """
                create type test.Employee extends GenericModelObject {
                    attribute code: String {
                        required: true
                    }
                    attribute name: String
                    attribute departmentId: Long
                    -> SampleDocument[] as documents
                    schema: SAMPLE
                    tableName: EMPLOYEE
                }
                                
                create type test.Department extends GenericModelObject {
                    attribute code: String {
                        required: true
                    }
                    attribute name: String
                    -> Employee[] as employees {
                        foreignKey: departmentId
                    }
                    schema: SAMPLE
                    tableName: DEPARTMENT
                }
                """;

        Object result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Create types result: " + result);

        // Verify structural relationship
        TypeDefinition deptType = S.service(MetadataService.class).getTypeDefinitionByName("test.Department");
        AttributeDefinition employeesAttr = deptType.getAttribute("employees");
        assertTrue(employeesAttr.checkIsStructuralRelation());
        assertEquals("departmentId", employeesAttr.asStructuralRelation().getForeignKeyField());

        // Verify associate relationship
        TypeDefinition empType = S.service(MetadataService.class).getTypeDefinitionByName("test.Employee");
        AttributeDefinition docsAttr = empType.getAttribute("documents");
        assertTrue(docsAttr.checkIsAssociateRelation());
    }

    public void testValueDomainDSLImpl() {
        String dsl = """
                create type sample.TaxDataset extends SampleDataset {
                    attribute status: String {
                        valueDomain: enum
                    }
                    attribute province: String {
                        valueDomain: cascade(province, city)
                    }
                    attribute city: String {
                        valueDomain: cascade(province, city)
                    }
                    -> SampleMaterial as reference
                    -> SampleDataset[] as associateRelation
                    schema: sample
                    tableName: TAX_DATASET
                }
                """;

        Object result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        assertEquals("[Type 'sample.TaxDataset' created successfully.]", result.toString());

        TypeDefinition typeDef = S.service(MetadataService.class).getTypeDefinitionByName("sample.TaxDataset");
        assertNotNull(typeDef);

        // Verify enum value domain
        AttributeDefinition statusAttr = typeDef.getAttribute("status");
        assertTrue(statusAttr.checkIsEnum());

        // Verify cascade value domain
        AttributeDefinition provinceAttr = typeDef.getAttribute("province");
        assertTrue(provinceAttr.checkIsCascade());
        assertEquals(Arrays.asList("province", "city"), provinceAttr.asCascade().getCascadeAttributePaths());

        // Verify relationship value domain
        AttributeDefinition attr = typeDef.getAttribute("associateRelation");
        assertTrue(attr.checkIsAssociateRelation());
        assertEquals("associateRelation", attr.asAssociateRelation().getRelationType());
        assertEquals(Types.list(Types.simple("io.emop.integrationtest.domain.SampleDataset")), attr.getType());
        assertEquals(Types.list(Types.simple("io.emop.integrationtest.domain.SampleDataset")), attr.asAssociateRelation().getTargetType());
    }

    private void testCreateTypeImpl() {
        String dsl = """
                create type user.Customer extends ItemRevision {
                  attribute name: java.lang.String {
                    description: "Customer's full name"
                    persistent: true
                    required: true
                  }
                  attribute email: java.lang.String {
                    description: "Customer's email address"
                    persistent: true
                    required: true
                  }
                  attribute age: java.lang.Integer {
                    description: "Customer's age"
                    persistent: true
                    required: false
                    defaultValue: 10
                  }
                  tableName: Customer
                  schema: sample
                }
                """;

        try {
            Object result = S.service(DSLExecutionService.class).execute(dsl);
            assertNotNull(result);
            log.info("testCreateType dsl result: " + result);
            assertEquals("[Type 'user.Customer' created successfully.]", result.toString());
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }
        // Add more specific assertions based on your implementation

        dsl = """
                create type user.VIPCustomer extends Customer {
                  attribute vipCode: java.lang.String {
                    description: "Customer's VIP card num"
                    required: true
                  }
                  schema: sample
                  tableName: VIPCustomer
                }
                """;

        try {
            Object result = S.service(DSLExecutionService.class).execute(dsl);
            assertNotNull(result);
            log.info("testCreateType dsl result: " + result);
            assertEquals("[Type 'user.VIPCustomer' created successfully.]", result.toString());
        } catch (Throwable e) {
            log.warn(e.getMessage());
        }
    }

    private void testUpdateTypeImpl() {
        String dsl = """
                update type Customer {
                  attribute isMale: boolean {
                    description: "Customer's gender"
                    persistent: true
                    required: true
                  }
                  icon: "customer.svg"
                }
                """;

        Object result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("testUpdateType dsl result: " + result);
        assertEquals("[Type 'user.Customer' updated successfully.]", result.toString());


        dsl = """
                show type Customer
                """;
        result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("show dsl result: " + result);
        assertTrue(result.toString().contains("customer.svg"));
        S.service(MetadataService.class).reloadTypeDefinitions();
    }

    private Long testCreateObjectImpl() {
        String dsl = """
                create object Customer {
                  name: "John Doe",
                  email: "john.doe@example.com",
                  age: 30,
                  isMale: true,
                  code: "CUS-1",
                  revId: "A"
                }
                """;

        Object result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("testCreateObject dsl result: " + result);
        // Add more specific assertions based on your implementation
        Long id = extractID(result.toString());
        ItemRevision rev = Q.result(ItemRevision.class).objectType("Customer").where("id=?", id).first();
        assertEquals("John Doe", rev.get("name"));
        assertEquals("john.doe@example.com", rev.get("email"));
        assertEquals(30, rev.get("age"));
        assertEquals(true, rev.get("isMale"));
        assertEquals("CUS-1", rev.get("code"));
        assertEquals("A", rev.get("revId"));
        return id;
    }

    private Long extractID(String logString) {
        // 使用正则表达式匹配 ID 值
        Pattern pattern = Pattern.compile("ID: (-?\\d+)");
        Matcher matcher = pattern.matcher(logString);

        if (matcher.find()) {
            String idString = matcher.group(1);
            return Long.parseLong(idString);
        } else {
            throw new IllegalArgumentException("No ID found in the given string");
        }
    }

    private void testUpdateObjectImpl() {
        String dsl = """
                update object Customer where name = 'John Doe' {
                  age: 32,
                }
                """;

        Object result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("testUpdateObject dsl result: " + result);
        // Add more specific assertions based on your implementation
        assertTrue(result.toString().contains("[Updated 1 object(s) of type 'user.Customer'"));
        Q.result(ItemRevision.class).objectType("Customer").where("name = ?", "John Doe").query().stream().forEach(rev -> {
            assertEquals("John Doe", rev.get("name"));
            assertEquals("john.doe@example.com", rev.get("email"));
            assertEquals(32, rev.get("age"));
            assertEquals(true, rev.get("isMale"));
        });
    }

    private void testDeleteObjectImpl() {
        String dsl = """
                delete object Customer where email = 'john.doe@example.com' LIMIT 1"
                """;

        Object result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("testDeleteObject dsl result: " + result);
        // Add more specific assertions based on your implementation
        assertEquals("[Deleted 1 object(s) of type 'user.Customer']", result.toString());
    }

    private void testDescCommand(Long id) {
        String dsl = "show type Customer";
        Object result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("dsl result: \n" + result);

        dsl = "show object " + id;
        result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("dsl result: \n" + result);
    }

    private void testUpdateTypeWithMultilangImpl() {
        String dsl = "update type Customer {\n" +
                "    multilang {\n" +
                "        name.zh_CN: \"客户\"\n" +
                "        name.en_US: \"Customer\"\n" +
                "        description.zh_CN: \"客户信息\"\n" +
                "        description.en_US: \"Customer Information\"\n" +
                "    }\n" +
                "}";

        Object result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("testUpdateTypeWithMultilang dsl result: \n" + result);
        S.service(MetadataService.class).reloadTypeDefinitions();
        TypeDefinition df = S.service(MetadataService.class).retrieveFullTypeDefinition("Customer");
        assertEquals("[Type 'user.Customer' updated successfully.]", result.toString());
        assertEquals("客户", df.get_multiLang().get("name.zh_CN"));
        assertEquals("Customer", df.get_multiLang().get("name.en_US"));
        assertEquals("客户信息", df.get_multiLang().get("description.zh_CN"));
        assertEquals("Customer Information", df.get_multiLang().get("description.en_US"));

    }

    private void testUpdateTypeWithAttributeMultilangImpl() {
        String dsl = "update type Customer {\n" +
                "    attribute status: java.lang.String {\n" +
                "        multilang {\n" +
                "            name.zh_CN: \"状态\"\n" +
                "            name.en_US: \"Status\"\n" +
                "            description.zh_CN: \"客户状态\"\n" +
                "            description.en_US: \"Customer Status\"\n" +
                "        }\n" +
                "    }\n" +
                "}";

        Object result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("testUpdateTypeWithAttributeMultilang dsl result: \n" + result);
        S.service(MetadataService.class).reloadTypeDefinitions();
        TypeDefinition df = S.service(MetadataService.class).retrieveFullTypeDefinition("Customer");

        assertEquals("[Type 'user.Customer' updated successfully.]", result.toString());
        assertEquals("状态", df.getAttribute("status").get_multiLang().get("name.zh_CN"));
        assertEquals("Status", df.getAttribute("status").get_multiLang().get("name.en_US"));
        assertEquals("客户状态", df.getAttribute("status").get_multiLang().get("description.zh_CN"));
        assertEquals("Customer Status", df.getAttribute("status").get_multiLang().get("description.en_US"));
    }

    private void testUpdateObjectWithMultilangImpl() {

        String dsl = """
                create object io.emop.model.metadata.ValueDomainData {
                  code: "Code-123",
                  value: "Code-123-value",
                  domainType: ENUM,
                }
                """;

        Object result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("testUpdateObjectWithMultilang dsl result: " + result);
        // Add more specific assertions based on your implementation
        Long id = extractID(result.toString());

        dsl = "update object io.emop.model.metadata.ValueDomainData where id = " + id + " {\n" +
                "    name.zh_CN: \"张三\",\n" +
                "    name.en_US: \"Zhang San\",\n" +
                "    description.zh_CN: \"重要客户\",\n" +
                "    description.en_US: \"Important Customer\",\n" +
                "    status: \"ACTIVE\"\n" +
                "}";

        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("testUpdateObjectWithMultilang dsl result: \n" + result);
//        waitForAWhile(); //等待cache同步，防止拿到旧數據
        ModelObject object = new ObjectRef(id).unbox();

        assertTrue(result.toString().contains("[Updated 1 object(s) of type 'io.emop.model.metadata.ValueDomainData'"));
        assertTrue(object instanceof ValueDomainData);
        ValueDomainData data = (ValueDomainData) object;
        assertEquals("张三", data.get_multiLang().get("name", Locale.CHINA));
        assertEquals("Zhang San", data.get_multiLang().get("name", Locale.US));
        assertEquals("重要客户", data.get_multiLang().get("description", Locale.CHINA));
        assertEquals("Important Customer", data.get_multiLang().get("description", Locale.US));
        assertEquals("ACTIVE", object.get("status"));

    }

    private void testDocumentRelationsImpl() {
        // 1. 创建根文件夹
        String dsl = """
                create object SampleFolder {
                    name: "Root Folder",
                    description: "Root folder for testing"
                }
                """;
        Object result = S.service(DSLExecutionService.class).execute(dsl);
        Long rootFolderId = extractID(result.toString());

        // 2. 创建子文件夹
        dsl = """
                create object SampleFolder {
                    name: "Sub Folder",
                    description: "Sub folder for testing"
                }
                """;
        result = S.service(DSLExecutionService.class).execute(dsl);
        Long subFolderId = extractID(result.toString());

        // 3. 创建Dataset
        dsl = """
                create object SampleDataset {
                    `type`: "TEST",
                    code: "DS-001",
                    name: "Test Dataset"
                }
                """;
        result = S.service(DSLExecutionService.class).execute(dsl);
        Long datasetId = extractID(result.toString());

        // 4. 创建Document
        String createDocTemplate = """
                create object SampleDocument {
                    name: "%s",
                    fileSize: 1024,
                    fileType: "FILE",
                    path: "%s",
                    checksum: "%s"
                }
                """;
        dsl = String.format(createDocTemplate,
                "Test Document", "/test/doc1.txt", "abc123");
        result = S.service(DSLExecutionService.class).execute(dsl);
        Long docId = extractID(result.toString());

        // 5. 创建关系
        // 5.1 将子文件夹添加到根文件夹
        dsl = String.format("relation Folder(%d) -> SampleFolder(%d) as content",
                rootFolderId, subFolderId);
        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Create folder relation result: " + result);

        // 5.2 将Dataset添加到根文件夹
        dsl = String.format("relation SampleFolder(%d) -> SampleDataset(%d) as content",
                rootFolderId, datasetId);
        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Create dataset relation result: " + result);

        // 5.3 将Document添加到SampleDataset
        dsl = String.format("relation SampleDataset(%d) -> SampleDocument(%d) as content",
                datasetId, docId);
        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Create document relation result: " + result);

        // 6. 查询关系
        // 6.1 查询根文件夹的内容
        dsl = String.format("show object SampleFolder(%d).content", rootFolderId);
        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Root folder content: " + result);
        assertTrue(result.toString().contains("Sub Folder"));
        assertTrue(result.toString().contains("Test Dataset"));

        // 6.2 查询Dataset的内容
        dsl = String.format("show object SampleDataset(%d).content", datasetId);
        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Dataset content: " + result);
        assertTrue(result.toString().contains("Test Document"));

        // 7. 批量创建关系测试
        // 7.1 创建更多Document
        dsl = String.format(createDocTemplate,
                "Test Document 2", datasetId, "/test/doc2.txt", "def456");
        result = S.service(DSLExecutionService.class).execute(dsl);
        Long doc2Id = extractID(result.toString());

        dsl = String.format(createDocTemplate,
                "Test Document 3", datasetId, "/test/doc3.txt", "ghi789");
        result = S.service(DSLExecutionService.class).execute(dsl);
        Long doc3Id = extractID(result.toString());

        // 7.2 批量添加Document到SampleDataset
        dsl = String.format("""
                relation SampleDataset(%d) {
                    -> [SampleDocument(%d), SampleDocument(%d)] as content
                }
                """, datasetId, doc2Id, doc3Id);
        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Batch create document relations result: " + result);

        // 8. 删除关系测试
        // 8.1 删除特定关系
        dsl = String.format("remove relation between SampleDataset(%d) and SampleDocument(%d)",
                datasetId, doc2Id);
        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Remove specific relation result: " + result);

        // 8.2 删除某类型的所有关系
        dsl = String.format("remove relation content from SampleDataset(%d)", datasetId);
        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Remove all content relations result: " + result);

        // 验证关系已被删除
        dsl = String.format("show object SampleDataset(%d).content", datasetId);
        result = S.service(DSLExecutionService.class).execute(dsl);
        log.info("Dataset content after removal: " + result);
        assertFalse(result.toString().contains("Test Document"));
    }

    private void testSetupImportTestTypes() {
        // Create test types for import
        String createSupplierType = """
                create type sample.Supplier extends GenericModelObject {
                    attribute code: String {
                        required: true
                    }
                    attribute contact: String
                    schema: sample
                    tableName: Supplier
                    multilang {
                        name.zh_CN: "供应商"
                        name.en_US: "Supplier"
                    }
                    businessUniqueKeys: [code]
                }
                """;
        String createPartType = """
                create type sample.Part extends ItemRevision {
                    attribute quantity: Integer
                    -> sample.Supplier[] as associateRelation
                    schema: sample
                    tableName: PART
                    multilang {
                        name.zh_CN: "部件"
                        name.en_US: "Part"
                        description.zh_CN: "部件信息"
                        description.en_US: "Part Information"
                    }
                }
                """;

        String createAssemblyType = """
                create type sample.Assembly extends ItemRevision {
                    attribute quantity: Integer
                    attribute weight: Float
                    attribute parentId: Long
                    -> sample.Assembly[] as children{
                        foreignKey: parentId
                    }
                    schema: sample
                    tableName: ASSEMBLY
                    multilang {
                        name.zh_CN: "组装件"
                        name.en_US: "Assembly"
                        description.zh_CN: "组装件信息"
                        description.en_US: "Assembly Information"
                    }
                }
                """;

        DSLExecutionService dslService = S.service(DSLExecutionService.class);
        try {
            Object result = dslService.execute(createSupplierType);
            assertNotNull(result);
            log.info("Create Supplier type result: " + result);
        } catch (Exception e) {
            log.warn("Error create types: " + e.getMessage() + ", skip it");
        }
        try {
            Object result = dslService.execute(createPartType);
            assertNotNull(result);
            log.info("Create Part type result: " + result);
        } catch (Exception e) {
            log.warn("Error create types: " + e.getMessage() + ", skip it");
        }
        try {
            Object result = dslService.execute(createAssemblyType);
            assertNotNull(result);
            log.info("Create Assembly type result: " + result);
        } catch (Exception e) {
            log.warn("Error create types: " + e.getMessage() + ", skip it");
        }
    }

    private void testPlainImportImpl() {
        // Create test CSV data
        String csvData = """
                编码,版本,名称,数量
                P001,A,Test Part 1,10
                P002,A,Test Part 2,20
                P003,A,Test Part 3,30
                """;

        // Save CSV data to a temporary file
        try {
            S.service(StorageService.class).upload(bucketPath, "parts.csv", csvData.getBytes(), true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test CSV file", e);
        }

        String removeDSL = """
                delete object Part where code in ('P001', 'P002', 'P003')
                """;
        S.service(DSLExecutionService.class).execute(removeDSL);

        String importDsl = """
                import Part "%s/parts.csv" {
                    column "编码" -> code
                    column "版本" -> revId
                    column "名称" -> name
                    column "数量" -> quantity
                }
                """;
        importDsl = String.format(importDsl, bucketPath);
        Object result = S.service(DSLExecutionService.class).execute(importDsl);
        assertNotNull(result);
        log.info("Plain import result: " + result);

        // Verify imported data
        List<ItemRevision> parts = Q.result(ItemRevision.class)
                .objectType("Part")
                .where("code in ('P001', 'P002', 'P003')")
                .query();

        assertEquals(3, parts.size());
        parts.forEach(part -> {
            assertNotNull(part.get("name"));
            assertNotNull(part.get("code"));
            assertTrue(part.get("quantity") instanceof Integer);
        });
    }

    private void testTreeImportImpl() {
        // Create test CSV data for tree structure
        String csvData = """
                编码,版本,数量,名称,重量,父项编码,父项版本
                A001,1.0,1,UA001,1.1,,
                A002,1.0,2,UA002,2.2,A001,1.0
                A003,1.0,3,UA003,2.3,A001,1.0
                A004,1.0,4,UA004,4.4,A001,1.0
                """;

        try {
            S.service(StorageService.class).upload(bucketPath, "assembly.csv", csvData.getBytes(), true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test CSV file", e);
        }

        String importDsl = """
                import tree Assembly "%s/assembly.csv" {
                    parent ["父项编码", "父项版本"] -> ["编码", "版本"]
                    column "编码" -> code
                    column "版本" -> revId
                    column "数量" -> quantity
                    column "名称" -> name
                    column "重量" -> weight
                }
                """;
        ModelObject parent = Q.<ModelObject>objectType("Assembly").where("code = 'A001'").first();
        if (parent != null) {
            S.service(AssociateRelationService.class).removeRelations(parent, RelationType.children);
            Q.<ModelObject>objectType("Assembly").where("code like 'A00%'").delete();
        }
        importDsl = String.format(importDsl, bucketPath);
        Object result = S.service(DSLExecutionService.class).execute(importDsl);
        assertNotNull(result);
        log.info("Tree import result: " + result);

        // Verify imported data
        List<ItemRevision> assemblies = Q.result(ItemRevision.class)
                .objectType("Assembly")
                .where("code in ('A001', 'A002', 'A003', 'A004')")
                .query();

        assertEquals(4, assemblies.size());

        // Verify parent-child relationships
        ItemRevision root = assemblies.stream()
                .filter(a -> a.get("code").equals("A001"))
                .findFirst()
                .orElse(null);
        assertNotNull(root);
        assertEquals(1.1f, root.get("weight"));
        assertEquals(3, root.get("children", List.class).size());
        assertTrue(root.get("children").toString().contains("UA002"));

        ItemRevision child = assemblies.stream()
                .filter(a -> a.get("code").equals("A002"))
                .findFirst()
                .orElse(null);
        assertNotNull(child);
        assertEquals("UA002", child.get("name"));
        assertEquals(2.2f, child.get("weight"));
        assertEquals(0, child.get("children", List.class).size());
    }

    private void testImportWithXpathImpl() {
        String code1 = "P1-" + System.currentTimeMillis();
        String code2 = "P2-" + System.currentTimeMillis();
        String supplierCode1 = "SUP001-" + System.currentTimeMillis();
        String supplierCode2 = "SUP002-" + System.currentTimeMillis();

        // Create test CSV data with complex structure
        String csvData = "编码,名称,版本,供应商代码,联系人手机,供应商名称\n"
                + code1 + ",UP010,A01," + supplierCode1 + ",13800138000,ALT001\n"
                + code2 + ",UP011,A01," + supplierCode2 + ",13900139000,ALT002";

        try {
            S.service(StorageService.class).upload(bucketPath, "parts_complex.csv", csvData.getBytes(), true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test CSV file", e);
        }

        String importDsl = String.format("""
                import Part "%s/parts_complex.csv" {
                    column "编码" -> code
                    column "名称" -> name
                    column "版本" -> revId
                    column "供应商代码" -> "associateRelation/code"
                    column "供应商名称" -> "associateRelation/name"
                    column "联系人手机" -> "associateRelation/contact"
                } with {
                    allowXPathAutoCreate: true
                }
                """, bucketPath);
        try {
            Object result = S.service(DSLExecutionService.class).execute(importDsl);
            fail("not reachable");
        } catch (Exception e) {
        }

        importDsl = String.format("""
                import Part "%s/parts_complex.csv" {
                    column "编码" -> code
                    column "名称" -> name
                    column "版本" -> revId
                    column "供应商代码" -> "associateRelation/code"
                    column "供应商名称" -> "associateRelation/name"
                    column "联系人手机" -> "associateRelation/contact"
                } with {
                    existence: update
                    allowXPathAutoCreate: true,
                }
                """, bucketPath);
        Object result = S.service(DSLExecutionService.class).execute(importDsl);
        log.info("XPath import result: " + result);
        // Get initial supplier count
        long initialSupplierCount = Q.objectType("Supplier").where("code in (?, ?)", supplierCode1, supplierCode2).count();
        assertEquals(2l, initialSupplierCount);

        // Verify imported data with complex structure
        List<ItemRevision> parts = Q.result(ItemRevision.class)
                .objectType("Part")
                .where("code in (?, ?)", code1, code2)
                .query();

        assertEquals(2, parts.size());
        parts.forEach(part -> {
            assertNotNull(part.get("code"));

            // Verify nested structure created by xpath
            List<ModelObject> associateRelation = (List<ModelObject>) part.get("associateRelation");
            assertNotNull(associateRelation);
            assertEquals(1, associateRelation.size());

            ModelObject supplier = associateRelation.get(0);
            assertTrue(List.of("13800138000", "13900139000").contains((String) supplier.get("contact")));
            assertTrue(List.of("ALT001", "ALT002").contains((String) supplier.get("name")));
        });

        // Run import again - this shouldn't create duplicate suppliers
        result = S.service(DSLExecutionService.class).execute(importDsl);
        long duplicateSupplierCount = Q.objectType("Supplier").where("code in (?, ?)", supplierCode1, supplierCode2).count();
        assertEquals(2l, duplicateSupplierCount);
    }

    private void testImportWithExistenceImpl() {
        Q.<ModelObject>objectType("Part").where("code like 'P00%'").delete();
        // Test data setup
        String initialCsvData = """
                编码,版本,名称,数量
                P001,A01,Test Part 1,10
                P002,A01,Test Part 2,20
                P003,A01,Test Part 3,30
                """;

        String updateCsvData = """
                编码,版本,名称,数量
                P001,A01,Updated Part 1,15
                P002,A01,Updated Part 2,25
                P004,A01,New Part 4,40
                """;

        try {
            // Save initial CSV data
            S.service(StorageService.class).upload(bucketPath, "parts_initial.csv", initialCsvData.getBytes(), true);
            // Save update CSV data
            S.service(StorageService.class).upload(bucketPath, "parts_update.csv", updateCsvData.getBytes(), true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test CSV files", e);
        }

        // 1. First import - create initial data
        String initialImportDsl = """
                import Part "%s/parts_initial.csv" {
                    column "编码" -> code
                    column "版本" -> revId
                    column "名称" -> name
                    column "数量" -> quantity
                }
                """;

        initialImportDsl = String.format(initialImportDsl, bucketPath);
        Object result = S.service(DSLExecutionService.class).execute(initialImportDsl);
        assertNotNull(result);
        log.info("Initial import result: " + result);

        // Verify initial import
        List<ItemRevision> initialParts = Q.result(ItemRevision.class)
                .objectType("Part")
                .where("code in ('P001', 'P002', 'P003')")
                .query();
        assertEquals(3, initialParts.size());

        // 2. Test skip action - should skip existing records and only import new ones
        String skipImportDsl = """
                import Part "%s/parts_update.csv" {
                    column "编码" -> code
                    column "名称" -> name
                    column "版本" -> revId
                    column "数量" -> quantity
                } with {
                    existence: skip
                }
                """;

        skipImportDsl = String.format(skipImportDsl, bucketPath);
        result = S.service(DSLExecutionService.class).execute(skipImportDsl);
        assertNotNull(result);
        log.info("Skip import result: " + result);

        // Verify skip behavior
        List<ItemRevision> partsAfterSkip = Q.result(ItemRevision.class)
                .objectType("Part")
                .where("code in ('P001', 'P002', 'P003', 'P004')")
                .query();

        assertEquals(4, partsAfterSkip.size());
        // Verify original records weren't updated
        partsAfterSkip.stream()
                .filter(p -> "P001".equals(p.get("code")))
                .forEach(p -> {
                    assertEquals("Test Part 1", p.get("name"));
                    assertEquals(10, p.get("quantity"));
                });

        // 3. Test update action - should update existing records
        String updateImportDsl = """
                import Part "%s/parts_update.csv" {
                    column "编码" -> code
                    column "名称" -> name
                    column "版本" -> revId
                    column "数量" -> quantity
                } with {
                    existence: update
                }
                """;
        updateImportDsl = String.format(updateImportDsl, bucketPath);
        result = S.service(DSLExecutionService.class).execute(updateImportDsl);
        assertNotNull(result);
        log.info("Update import result: " + result);

        // Verify update behavior
        List<ItemRevision> partsAfterUpdate = Q.result(ItemRevision.class)
                .objectType("Part")
                .where("code in ('P001', 'P002')")
                .query();

        partsAfterUpdate.forEach(part -> {
            if ("P001".equals(part.get("code"))) {
                assertEquals("Updated Part 1", part.get("name"));
                assertEquals(15, part.get("quantity"));
            } else if ("P002".equals(part.get("code"))) {
                assertEquals("Updated Part 2", part.get("name"));
                assertEquals(25, part.get("quantity"));
            }
        });

        // 4. Test error action - should throw error when encountering existing records
        String errorImportDsl = """
                import Part "%s/parts_update.csv" {
                    column "编码" -> code
                    column "名称" -> name
                    column "版本" -> revId
                    column "数量" -> quantity
                } with {
                    existence: error
                }
                """;
        errorImportDsl = String.format(errorImportDsl, bucketPath);
        try {
            S.service(DSLExecutionService.class).execute(errorImportDsl);
            fail("Expected an exception for duplicate records");
        } catch (RuntimeException e) {
            String fullMsg = getFullStackTrace(e);
            // Verify error is thrown for existing records
            assertTrue(fullMsg.contains("already exists") ||
                    fullMsg.contains("duplicate") ||
                    fullMsg.contains("existing record"));
            log.info("Expected error received: " + e.getMessage());
        }

        // 5. Test multiple existence check fields
        String multiFieldImportDsl = """
                import Part "%s/parts_update.csv" {
                    column "编码" -> code
                    column "名称" -> name
                    column "版本" -> revId
                    column "数量" -> quantity
                } with {
                    existence: skip
                }
                """;
        multiFieldImportDsl = String.format(multiFieldImportDsl, bucketPath);
        result = S.service(DSLExecutionService.class).execute(multiFieldImportDsl);
        assertNotNull(result);
        log.info("Multi-field existence check result: " + result);

        // Clean up test data
        try {
            S.service(DSLExecutionService.class).execute("delete object Part where code in ('P001', 'P002', 'P003', 'P004')");
        } catch (Exception e) {
            log.warn("Error cleaning up test data: " + e.getMessage());
        }
    }

    private void testCreateObjectWithIfNotExistsImpl() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        // 1. 测试基础的 if not exists 功能
        String dsl = """
                create object Customer {
                    name: "Test Customer",
                    email: "test@example.com",
                    age: 25,
                    code: "TEST-001-%s",
                    revId: "A"
                } if not exists (code='TEST-001-%s')
                """;
        dsl = dsl.formatted(timestamp, timestamp);
        // 第一次创建应该成功
        Object result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("First create with if not exists result: " + result);
        assertTrue(result.toString().contains("created with ID:"));

        Long firstId = extractID(result.toString());

        // 第二次执行相同的语句应该跳过创建
        result = S.service(DSLExecutionService.class).execute(dsl);
        assertNotNull(result);
        log.info("Second create with if not exists result: " + result);
        assertTrue(result.toString().contains("already exists"));
        assertTrue(result.toString().contains("creation skipped"));
        assertTrue(result.toString().contains("ID: " + firstId));

        // 2. 测试复杂条件
        String complexConditionDsl = """
                create object Customer {
                    name: "Another Customer",
                    email: "another@example.com", 
                    age: 30,
                    code: "TEST-002-%s",
                    revId: "A"
                } if not exists (code='TEST-002-%s' and revId='A')
                """;
        complexConditionDsl = complexConditionDsl.formatted(timestamp, timestamp);
        result = S.service(DSLExecutionService.class).execute(complexConditionDsl);
        log.info("Complex condition create result: " + result);
        assertTrue(result.toString().contains("created with ID:"));

        // 再次执行应该跳过
        result = S.service(DSLExecutionService.class).execute(complexConditionDsl);
        log.info("Complex condition duplicate result: " + result);
        assertTrue(result.toString().contains("already exists"));

        // 3. 测试不同条件的创建（应该成功）
        String differentConditionDsl = """
                create object Customer {
                    name: "Third Customer",
                    email: "third@example.com",
                    age: 35,
                    code: "TEST-002-%s",
                    revId: "B"
                } if not exists (code='TEST-002-%s' and revId='B')
                """;
        differentConditionDsl = differentConditionDsl.formatted(timestamp, timestamp);
        result = S.service(DSLExecutionService.class).execute(differentConditionDsl);
        log.info("Different condition create result: " + result);
        assertTrue(result.toString().contains("created with ID:"));

        // 4. 测试没有 if not exists 的普通创建（应该创建重复对象）
        String normalCreateDsl = """
                create object Customer {
                    name: "Duplicate Customer",
                    email: "duplicate@example.com",
                    age: 40,
                    code: "TEST-001-%s",
                    revId: "A"
                }
                """;

        assertException(() -> {
            S.service(DSLExecutionService.class).execute(normalCreateDsl.formatted(timestamp));
        });
        log.info("Normal create (duplicate) result: " + result);
        assertTrue(result.toString().contains("created with ID:"));

        // 5. 测试多语言对象的 if not exists
        String multiLangDsl = """
                create object io.emop.model.metadata.ValueDomainData {
                    attributePath: "ML-CODE-001-%s",
                    value: "ML-VALUE-001",
                    domainType: ENUM
                } if not exists (attributePath='ML-CODE-001-%s')
                """;
        multiLangDsl = multiLangDsl.formatted(timestamp, timestamp);
        result = S.service(DSLExecutionService.class).execute(multiLangDsl);
        log.info("Multilang create result: " + result);
        assertTrue(result.toString().contains("created with ID:"));

        // 再次创建应该跳过
        result = S.service(DSLExecutionService.class).execute(multiLangDsl);
        log.info("Multilang duplicate result: " + result);
        assertTrue(result.toString().contains("already exists"));

        // 6. 清理测试数据
        try {
            S.service(DSLExecutionService.class).execute("delete object Customer where code in ('TEST-001-%s', 'TEST-002-%s')".formatted(timestamp, timestamp));
            S.service(DSLExecutionService.class).execute("delete object io.emop.model.metadata.ValueDomainData where attributePath='ML-CODE-001-%s'".formatted(timestamp));
        } catch (Exception e) {
            log.warn("Error cleaning up test data: " + e.getMessage());
        }
    }

    private void testImportWithTypeResolverImpl() {
        String code1 = "P1-" + System.currentTimeMillis();
        String code2 = "P2-" + System.currentTimeMillis();
        String supplierCode1 = "SUP001-" + System.currentTimeMillis();
        String supplierCode2 = "SUP002-" + System.currentTimeMillis();

        // Create test CSV data with complex structure
        String csvData = "编码,名称,版本,供应商代码,联系人手机,供应商名称\n"
                + code1 + ",UP010,A01," + supplierCode1 + ",13800138000,ALT001\n"
                + code2 + ",UP011,A01," + supplierCode2 + ",13900139000,ALT002";

        try {
            S.service(StorageService.class).upload("/", "parts_complex.csv", csvData.getBytes(), true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test CSV file", e);
        }

        String importDsl = String.format("""
                import Part "%s/parts_complex.csv" {
                    column "编码" -> code
                    column "名称" -> name
                    column "版本" -> revId
                    column "供应商代码" -> "associateRelation/code"
                    column "供应商名称" -> "associateRelation/name"
                    column "联系人手机" -> "associateRelation/contact"
                } with {
                    allowXPathAutoCreate: true
                    typeResolver: ""\"
                        if(xpath!=null){
                            if(xpath.startsWith('associateRelation')){
                                return 'Supplier'
                            }
                        } else {
                            if(data['code'].startsWith('P1-')){
                                return 'Part'
                            } else {
                                return 'Part'
                            }
                        }
                    ""\"
                }
                """, bucketPath);
        Object result = S.service(DSLExecutionService.class).execute(importDsl);
        log.info("XPath import ({},{}) result: {}", code1, code2, result);
    }

    public static String getFullStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    @SneakyThrows
    private static void waitForAWhile() {
        Thread.sleep(100);
    }
}
