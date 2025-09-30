package io.emop.integrationtest.usecase.common;

import io.emop.model.common.ItemRevision;
import io.emop.model.common.ModelObject;
import io.emop.model.common.UserContext;
import io.emop.model.query.Q;
import io.emop.integrationtest.domain.SampleDocument;
import io.emop.integrationtest.domain.SampleMaterial;
import io.emop.integrationtest.domain.SampleTask;
import io.emop.service.S;
import io.emop.service.api.dataexchange.importer.*;
import io.emop.service.api.dataexchange.importer.ImportRequest.*;
import io.emop.service.api.dataexchange.importer.ImportResult.*;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.util.SerializationUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.*;

import static io.emop.integrationtest.util.Assertion.*;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataImportServiceTest {

    private DataImportService importService;
    private ObjectService objectService;

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100l, List.of("admin")));
        importService = S.service(DataImportService.class);
        objectService = S.service(ObjectService.class);
        // 清理测试数据
        Q.result(SampleTask.class).where("code like ?", "TSK%").delete();
        Q.result(SampleTask.class).where("code like ?", "TEST%").delete();
        Q.result(SampleDocument.class).where("code like ?", "DOC%").delete();
    }

    @Test
    @Order(1)
    public void testBasicTableImport() {
        // 生成唯一的物料编码前缀
        String baseCode = "MAT-" + System.currentTimeMillis();

        // 准备测试数据
        TableData tableData = TableData.builder()
                .headers(Arrays.asList("物料编号", "物料名称", "版本号", "参考物料"))
                .rows(Arrays.asList(
                        createMaterialRow(baseCode + "-1", "物料1", "A", null),
                        createMaterialRow(baseCode + "-2", "物料2", "A", baseCode + "-1"),
                        createMaterialRow(baseCode + "-3", "物料3", "A", baseCode + "-1")
                ))
                .build();

        // 创建导入请求
        ImportRequest request = ImportRequest.builder()
                .tableData(tableData)
                .targetType(SampleMaterial.class.getName())
                .columnMappings(Map.of(
                        "物料编号", List.of("code"),
                        "物料名称", List.of("name", "description"),
                        "版本号", List.of("revId")
                ))
                .build();

        byte[] serialized = SerializationUtil.FURY.get().serialize(request);
        System.out.println("序列化成功，数据长度: " + serialized.length);

        // 反序列化
        ImportRequest deserialized = (ImportRequest) SerializationUtil.FURY.get().deserialize(serialized);
        System.out.println("反序列化成功");

        // 执行导入
        ImportResult result = importService.importTable(request);

        // 验证导入结果
        assertEquals(ImportResult.ImportStatus.COMPLETED, result.getStatus());
        assertEquals(3, result.getTotalRows());
        assertEquals(3, result.getProcessedRows());
        assertTrue(result.getErrors().isEmpty());

        // 验证导入的数据
        List<SampleMaterial> materials = Q.result(SampleMaterial.class)
                .where("code like ?", baseCode + "%")
                .sortAsc("code")
                .query();

        assertEquals(3, materials.size());

        // 验证第一个物料
        SampleMaterial material1 = materials.get(0);
        assertEquals(baseCode + "-1", material1.getCode());
        assertEquals("物料1", material1.getName());
        assertEquals("物料1", material1.getDescription());
        assertEquals("A", material1.getRevId());

        // 验证第二个物料
        SampleMaterial material2 = materials.get(1);
        assertEquals(baseCode + "-2", material2.getCode());
        assertEquals("物料2", material2.getName());
        assertEquals("物料2", material2.getDescription());
        assertEquals("A", material2.getRevId());

        // 验证第三个物料
        SampleMaterial material3 = materials.get(2);
        assertEquals(baseCode + "-3", material3.getCode());
        assertEquals("物料3", material3.getName());
        assertEquals("物料3", material3.getDescription());
        assertEquals("A", material3.getRevId());
    }

    @Test
    @Order(2)
    public void testTreeImport() {
        // 准备测试数据
        TableData tableData = TableData.builder()
                .headers(Arrays.asList("任务编号", "任务名称", "上级任务编号"))
                .rows(Arrays.asList(
                        createTaskRow("TSK001", "主任务1", null),
                        createTaskRow("TSK002", "子任务1.1", "TSK001"),
                        createTaskRow("TSK003", "子任务1.2", "TSK001"),
                        createTaskRow("TSK004", "主任务2", null),
                        createTaskRow("TSK005", "子任务2.1", "TSK004")
                ))
                .build();

        // 创建树形导入请求
        TreeImportRequest request = TreeImportRequest.builder()
                .tableData(tableData)
                .targetType(SampleTask.class.getName())
                .config(ImportConfig.builder().relationType("childTasks").build())
                .simpleColumnMappings(Map.of(
                        "任务编号", "code",
                        "任务名称", "name",
                        "版本", "revId"
                ))
                .parentKeyMapping(ParentKeyMapping.builder()
                        .fromColumns(Collections.singletonList("上级任务编号"))
                        .toColumns(Collections.singletonList("任务编号"))
                        .build())
                .build();

        // 执行导入
        ImportResult result = importService.importTree(request);

        // 验证导入结果
        assertEquals(ImportStatus.COMPLETED, result.getStatus());
        assertEquals(5, result.getTotalRows());
        assertEquals(5, result.getProcessedRows());
        assertTrue(result.getErrors().isEmpty());

        // 验证树形结构
        List<SampleTask> rootTasks = Q.result(SampleTask.class)
                .where("code in (?, ?)", "TSK001", "TSK004")
                .sortAsc("code")
                .query();

        assertEquals(2, rootTasks.size());

        // 验证第一个树
        SampleTask root1 = rootTasks.get(0);
        assertEquals("TSK001", root1.getCode());
        List<SampleTask> children1 = (List<SampleTask>) root1.get("childTasks");
        assertNotNull(children1);
        assertEquals(2, children1.size());
        assertTrue(children1.stream().anyMatch(t -> "TSK002".equals(t.getCode())));
        assertTrue(children1.stream().anyMatch(t -> "TSK003".equals(t.getCode())));

        // 验证第二个树
        SampleTask root2 = rootTasks.get(1);
        assertEquals("TSK004", root2.getCode());
        List<SampleTask> children2 = (List<SampleTask>) root2.get("childTasks");
        assertNotNull(children2);
        assertEquals(1, children2.size());
        assertEquals("TSK005", children2.get(0).getCode());
    }

    @Test
    @Order(3)
    public void testDuplicateHandling() {
        // 先创建一个已存在的任务
        SampleTask existingTask = new SampleTask();
        existingTask.setCode("TSK001");
        existingTask.setName("原始任务");
        existingTask.setRevId("A");
        objectService.upsertByBusinessKey(existingTask);

        // 准备导入数据
        TableData tableData = TableData.builder()
                .headers(Arrays.asList("任务编号", "任务名称"))
                .rows(Arrays.asList(
                        createTaskRow("TSK001", "更新的任务名称", null),
                        createTaskRow("TSK002", "新任务", null)
                ))
                .build();

        // 测试跳过策略
        ImportRequest skipRequest = ImportRequest.builder()
                .tableData(tableData)
                .targetType(SampleTask.class.getName())
                .simpleColumnMappings(Map.of(
                        "任务编号", "code",
                        "任务名称", "name",
                        "版本", "revId"
                ))
                .config(ImportConfigModel.builder().existence(DuplicateStrategy.skip).build())
                .build();

        ImportResult skipResult = importService.importTable(skipRequest);
        assertEquals(2, skipResult.getTotalRows());
        assertEquals(2, skipResult.getProcessedRows());

        SampleTask skippedTask = Q.result(SampleTask.class)
                .where("code = ?", "TSK001")
                .first();
        assertEquals("原始任务", skippedTask.getName());

        // 测试更新策略
        ImportRequest updateRequest = ImportRequest.builder()
                .tableData(tableData)
                .targetType(SampleTask.class.getName())
                .simpleColumnMappings(Map.of(
                        "任务编号", "code",
                        "任务名称", "name",
                        "版本", "revId"
                ))
                .config(ImportConfigModel.builder().existence(DuplicateStrategy.update).build())
                .build();

        ImportResult updateResult = importService.importTable(updateRequest);
        assertEquals(2, updateResult.getTotalRows());
        assertEquals(2, updateResult.getProcessedRows());

        SampleTask updatedTask = Q.result(SampleTask.class)
                .where("code = ?", "TSK001")
                .first();
        assertEquals("更新的任务名称", updatedTask.getName());
    }

    @Test
    @Order(4)
    public void testImportWithXPath() {
        // 1. 准备测试数据
        TableData tableData = TableData.builder()
                .headers(Arrays.asList("任务编号", "任务名称", "任务版本", "文档编号", "文档名称", "文档路径"))
                .rows(Arrays.asList(
                        createRowWithDoc("TEST001", "任务1", "DOC001", "文档1", "/path/1"),
                        createRowWithDoc("TEST002", "任务2", "DOC002", "文档2", "/path/2")
                ))
                .build();

        // 2. 创建导入请求（带XPath配置）
        ImportRequest request = ImportRequest.builder()
                .tableData(tableData)
                .targetType(SampleTask.class.getName())
                .simpleColumnMappings(Map.of(
                        "任务编号", "code",
                        "任务名称", "name",
                        "任务版本", "revId",
                        "文档编号", "majorSpecificationDocs/code",
                        "文档名称", "majorSpecificationDocs/name",
                        "文档路径", "majorSpecificationDocs/path",
                        "文件大小", "majorSpecificationDocs/fileSize",
                        "文件类型", "majorSpecificationDocs/fileType",
                        "checksum", "majorSpecificationDocs/checksum"
                ))
                .config(ImportConfigModel.builder()
                        .allowXPathAutoCreate(true)
                        .build())
                .build();

        // 3. 执行导入
        ImportResult result = importService.importTable(request);

        // 4. 验证结果
        assertEquals(ImportStatus.COMPLETED, result.getStatus());
        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getProcessedRows());
        assertTrue(result.getErrors().isEmpty());

        // 5. 验证导入的数据和关联
        List<SampleTask> tasks = Q.result(SampleTask.class)
                .where("code like ?", "TEST%")
                .sortAsc("code")
                .query();

        assertEquals(2, tasks.size());

        // 验证文档关联
        SampleTask task1 = tasks.get(0);
        assertNotNull(task1.get("majorSpecificationDocs"));
        assertEquals("DOC001", task1.get("majorSpecificationDocs", ModelObject.class).get("code"));
        assertEquals("/path/1", task1.getMajorSpecificationDocs().getPath());

        SampleTask task2 = tasks.get(1);
        assertNotNull(task2.get("majorSpecificationDocs", ModelObject.class));
        assertEquals("DOC002", task2.getMajorSpecificationDocs().get("code"));
        assertEquals("/path/2", task2.getMajorSpecificationDocs().getPath());
    }

    @Test
    @Order(5)
    public void testImportWithTypeResolver() {
        // 1. 准备测试数据 - 包含不同类型的任务
        TableData tableData = TableData.builder()
                .headers(Arrays.asList("任务编号", "任务名称", "任务类型"))
                .rows(Arrays.asList(
                        createRowWithType("TEST011", "主任务", "GROUP"),
                        createRowWithType("TEST012", "子任务", "NORMAL")
                ))
                .build();

        // 2. 创建导入请求（带类型解析器）
        ImportRequest request = ImportRequest.builder()
                .tableData(tableData)
                .targetType(SampleTask.class.getName())
                .simpleColumnMappings(Map.of(
                        "任务编号", "code",
                        "任务名称", "name",
                        "版本", "revId",
                        "任务类型", "taskType"
                ))
                .config(ImportConfigModel.builder()
                        .typeResolverScript("data.get('taskType') == 'GROUP' ? 'ItemRevision' : 'SampleTask'")
                        .existence(DuplicateStrategy.update)
                        .build()
                )
                .build();

        // 3. 执行导入
        ImportResult result = importService.importTable(request);

        // 4. 验证结果
        assertEquals(ImportStatus.COMPLETED, result.getStatus());
        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getProcessedRows());
        assertTrue(result.getErrors().isEmpty());

        // 5. 验证不同类型的任务
        ItemRevision groupTask = Q.result(ItemRevision.class)
                .where("code = ?", "TEST011")
                .first();
        assertNotNull(groupTask);
        assertEquals(ItemRevision.class.getName(), groupTask.get_objectType());

        SampleTask normalTask = Q.result(SampleTask.class)
                .where("code = ?", "TEST012")
                .first();
        assertNotNull(normalTask);
        assertEquals(SampleTask.class.getName(), normalTask.get_objectType());
    }

    @Test
    @Order(6)
    public void testImportWithCustomDataMapping() {
        // 1. 准备测试数据 - 需要自定义转换的数据
        TableData tableData = TableData.builder()
                .headers(Arrays.asList("任务编号", "任务名称", "创建日期", "优先级"))
                .rows(Arrays.asList(
                        createRowWithExtra("TEST001", "任务1", "2023-01-01", "HIGH"),
                        createRowWithExtra("TEST002", "任务2", "2023-01-02", "LOW")
                ))
                .build();

        // 2. 创建导入请求（带自定义数据映射）
        ImportRequest request = ImportRequest.builder()
                .tableData(tableData)
                .targetType(SampleTask.class.getName())
                .simpleColumnMappings(Map.of(
                        "任务编号", "code",
                        "任务名称", "name",
                        "创建日期", "createDate",
                        "优先级", "priority",
                        "版本", "revId"
                ))
                .config(ImportConfigModel.builder()
                        .dataMappingScript("""
                                    import java.text.*;
                                    // 转换日期格式
                                    if (data.createDate) {
                                        data.createDate = new SimpleDateFormat("yyyy-MM-dd").parse(data.createDate)
                                    }
                                    // 转换优先级
                                    if (data.priority) {
                                        data.priority = data.priority == 'HIGH' ? 1 : 0
                                    }
                                    return data
                                """)
                        .existence(DuplicateStrategy.update)
                        .build())
                .build();

        // 3. 执行导入
        ImportResult result = importService.importTable(request);

        // 4. 验证结果
        assertEquals(ImportStatus.COMPLETED, result.getStatus());
        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getProcessedRows());
        assertTrue(result.getErrors().isEmpty());

        // 5. 验证数据转换结果
        List<SampleTask> tasks = Q.result(SampleTask.class)
                .where("code in (?,?)", "TEST001", "TEST002")
                .sortAsc("code")
                .query();

        assertEquals(2, tasks.size());

        SampleTask highPriorityTask = tasks.get(0);
        assertEquals(1, highPriorityTask.get("priority"));
        assertNotNull(highPriorityTask.get("createDate"));

        SampleTask lowPriorityTask = tasks.get(1);
        assertEquals(0, lowPriorityTask.get("priority"));
        assertNotNull(lowPriorityTask.get("createDate"));
    }

    // ================ 辅助方法（保持原有逻辑不变）================

    private Map<String, String> createRowWithDoc(String code, String name,
                                                 String docCode, String docName, String docPath) {
        Map<String, String> row = new HashMap<>();
        row.put("任务编号", code);
        row.put("任务名称", name);
        row.put("文档编号", docCode);
        row.put("文档名称", docName);
        row.put("文档路径", docPath);
        row.put("任务版本", "A");
        row.put("文件大小", String.valueOf(System.currentTimeMillis()));
        row.put("文件类型", "PDF");
        row.put("checksum", String.valueOf(System.currentTimeMillis()));
        return row;
    }

    private Map<String, String> createRowWithType(String code, String name, String type) {
        Map<String, String> row = new HashMap<>();
        row.put("任务编号", code);
        row.put("任务名称", name);
        row.put("任务类型", type);
        row.put("版本", "A");
        return row;
    }

    private Map<String, String> createRowWithExtra(String code, String name,
                                                   String date, String priority) {
        Map<String, String> row = new HashMap<>();
        row.put("任务编号", code);
        row.put("任务名称", name);
        row.put("创建日期", date);
        row.put("优先级", priority);
        row.put("版本", "A");
        return row;
    }

    private Map<String, String> createTaskRow(String code, String name, String parentCode) {
        Map<String, String> row = new HashMap<>();
        row.put("任务编号", code);
        row.put("任务名称", name);
        row.put("版本", "A");
        if (parentCode != null) {
            row.put("父任务编号", parentCode);
            row.put("上级任务编号", parentCode);
        }
        return row;
    }

    private Map<String, String> createMaterialRow(String code, String name, String revId, String refCode) {
        Map<String, String> row = new HashMap<>();
        row.put("物料编号", code);
        row.put("物料名称", name);
        row.put("版本号", revId);
        if (refCode != null) {
            row.put("参考物料", refCode);
        }
        return row;
    }
}
