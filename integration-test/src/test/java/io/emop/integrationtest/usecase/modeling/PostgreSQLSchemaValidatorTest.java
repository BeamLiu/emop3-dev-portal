package io.emop.integrationtest.usecase.modeling;

import io.emop.model.metadata.AttributeDefinition;
import io.emop.model.metadata.TypeDefinition;
import io.emop.integrationtest.domain.TypeTestEntity;
import io.emop.service.S;
import io.emop.service.api.data.NativeSqlService;
import io.emop.service.api.metadata.MetadataService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL Schema 验证器
 * 专门用于验证 PostgreSQL 数据库中的 schema 和表结构是否正确
 */
@Slf4j
public class PostgreSQLSchemaValidatorTest {

    @Test
    void testPostgreSQLSchemaValidation() {
        log.info("=== PostgreSQL Schema 验证开始 ===");
        try {
            // 验证 TypeTestEntity 的 schema
            validateTypeTestEntitySchema();

            log.info("=== PostgreSQL Schema 验证成功完成 ===");
        } catch (Exception e) {
            log.error("❌ PostgreSQL Schema 验证失败", e);
            throw new RuntimeException("PostgreSQL Schema 验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证 TypeTestEntity 的 schema
     */
    private void validateTypeTestEntitySchema() {
        log.info("--- 验证 TypeTestEntity Schema ---");

        try {
            // 获取类型定义
            MetadataService metadataService = S.service(MetadataService.class);
            TypeDefinition typeDef = metadataService.retrieveFullTypeDefinition(TypeTestEntity.class.getName());

            assertNotNull("TypeDefinition为空", typeDef);

            String schemaName = typeDef.getSchema();
            String tableName = typeDef.getTableName();

            log.info("验证表: {}.{}", schemaName, tableName);

            // 验证表结构
            validateTableStructure(typeDef);

            log.info("✓ TypeTestEntity Schema 验证通过");

        } catch (Exception e) {
            log.error("TypeTestEntity Schema 验证失败", e);
            throw new RuntimeException("Schema 验证失败", e);
        }
    }

    /**
     * 验证表结构
     */
    private void validateTableStructure(TypeDefinition typeDef) throws SQLException {
        log.info("验证表结构...");

        String sql = """
                SELECT column_name, data_type, udt_name, is_nullable, column_default, 
                       character_maximum_length, numeric_precision, numeric_scale
                FROM information_schema.columns 
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;

        Map<String, ColumnInfo> actualColumns = new HashMap<>();

        List<List<?>> result = S.service(NativeSqlService.class).executeNativeQuery(sql,
                typeDef.getSchema().toLowerCase(), typeDef.getTableName().toLowerCase());

        result.forEach(rs -> {
            ColumnInfo columnInfo = new ColumnInfo();
            columnInfo.columnName = (String) rs.get(0);
            columnInfo.dataType = (String) rs.get(1);
            columnInfo.udtName = (String) rs.get(2);
            columnInfo.isNullable = "YES".equals(rs.get(3));
            columnInfo.columnDefault = (String) rs.get(4);
            columnInfo.maxLength = rs.get(5) == null ? -1 : Integer.parseInt(String.valueOf(rs.get(5)));
            columnInfo.precision = rs.get(6) == null ? -1 : Integer.parseInt(String.valueOf(rs.get(6)));
            columnInfo.scale = rs.get(7) == null ? -1 : Integer.parseInt(String.valueOf(rs.get(7)));

            actualColumns.put(columnInfo.columnName.toLowerCase(), columnInfo);
            log.debug("发现列: {} ({}) udt: {}", columnInfo.columnName, columnInfo.dataType, columnInfo.udtName);
        });

        // 验证必须的列存在
        assertTrue("缺少 ID 列", actualColumns.containsKey("id"));

        ColumnInfo idColumn = actualColumns.get("id");
        assertTrue("ID 列应该是 bigint 类型",
                "bigint".equals(idColumn.dataType) || "int8".equals(idColumn.udtName));

        // 验证所有持久化属性都有对应的列
        int expectedColumns = 0;
        int validatedColumns = 0;

        for (Map.Entry<String, AttributeDefinition> entry : typeDef.getAttributes().entrySet()) {
            AttributeDefinition attrDef = entry.getValue();
            if (attrDef.isPersistent()) {
                expectedColumns++;
                String columnName = entry.getKey().toLowerCase();

                if (actualColumns.containsKey(columnName)) {
                    ColumnInfo columnInfo = actualColumns.get(columnName);
                    validateColumnType(entry.getKey(), attrDef, columnInfo);
                    validatedColumns++;
                    log.debug("✓ 列验证通过: {}", columnName);
                } else if (!"id".equals(columnName)) {
                    log.warn("属性没有对应的列: {}", columnName);
                }
            }
        }

        // 验证具体的问题字段
        validateSpecificProblematicFields(actualColumns);

        log.info("✓ 表结构验证通过，期望列数: {}, 实际列数: {}, 验证列数: {}",
                expectedColumns, actualColumns.size(), validatedColumns);
    }

    /**
     * 验证列类型
     */
    private void validateColumnType(String attrName, AttributeDefinition attrDef, ColumnInfo columnInfo) {
        String javaType = attrDef.getType().getTypeName().toLowerCase();
        String dbType = columnInfo.dataType.toLowerCase();
        String udtName = columnInfo.udtName != null ? columnInfo.udtName.toLowerCase() : "";

        String expectedDbType = getExpectedDbType(javaType, attrDef);
        boolean typeMatches = isTypeCompatible(expectedDbType, dbType, udtName);

        if (!typeMatches) {
            throw new RuntimeException(String.format("类型映射可能不匹配: %s (%s) -> DB: %s (%s)",
                    attrName, javaType, dbType, udtName));
        } else {
            log.debug("✓ 类型映射正确: {} ({}) -> {}", attrName, javaType, dbType);
        }
    }

    /**
     * 获取期望的数据库类型
     */
    private String getExpectedDbType(String javaType, AttributeDefinition attrDef) {
        if (attrDef.isKeepBinary()) {
            return "bytea";
        }

        return switch (javaType) {
            // 基础数值类型
            case "java.lang.integer", "int" -> "integer";
            case "java.lang.long", "long" -> "bigint";
            case "java.lang.short", "short" -> "smallint";
            case "java.lang.byte", "byte" -> "smallint";
            case "java.lang.double", "double" -> "double precision";
            case "java.lang.float", "float" -> "real";
            case "java.lang.boolean", "boolean" -> "boolean";

            // 大数类型
            case "java.math.bigdecimal" -> "numeric";
            case "java.math.biginteger" -> "numeric";

            // 日期时间类型
            case "java.sql.date" -> "date";
            case "java.util.date" -> "timestamp";  // util.Date应该用timestamp
            case "java.sql.timestamp" -> "timestamp";
            case "java.time.localdatetime" -> "timestamp";
            case "java.time.localdate" -> "date";
            case "java.time.localtime" -> "time";
            case "java.time.instant" -> "timestamp";  // 或 timestamptz

            // 字符串和UUID
            case "java.lang.string", "string" -> "text";
            case "java.util.uuid" -> "uuid";

            // 二进制类型
            case "byte[]", "[b" -> "bytea";

            default -> {
                // 检查是否为枚举类型
                if (attrDef.getType().toJavaType().isEnum()) yield "text";
                // 复杂对象和集合类型
                yield "jsonb";
            }
        };
    }

    /**
     * 检查类型兼容性
     */
    private boolean isTypeCompatible(String expected, String actual, String udtName) {
        return switch (expected) {
            case "integer" -> "integer".equals(actual) || "int4".equals(udtName);
            case "bigint" -> "bigint".equals(actual) || "int8".equals(udtName);
            case "smallint" -> "smallint".equals(actual) || "int2".equals(udtName);
            case "double precision" -> "double precision".equals(actual) || "float8".equals(udtName);
            case "real" -> "real".equals(actual) || "float4".equals(udtName);
            case "boolean" -> "boolean".equals(actual) || "bool".equals(udtName);
            case "numeric" -> "numeric".equals(actual) || "decimal".equals(actual);
            case "timestamp" -> actual.contains("timestamp");
            case "date" -> "date".equals(actual);
            case "time" -> actual.contains("time");
            case "text" -> "text".equals(actual) || actual.contains("varchar") || actual.contains("char");
            case "uuid" -> "uuid".equals(actual) || "uuid".equals(udtName);
            case "bytea" -> "bytea".equals(actual) || "bytea".equals(udtName);
            case "jsonb" -> "jsonb".equals(actual) || "json".equals(actual);
            case "[b" -> "bytea".equals(actual);
            default -> expected.equals(actual);
        };
    }

    /**
     * 验证具体的问题字段
     */
    private void validateSpecificProblematicFields(Map<String, ColumnInfo> actualColumns) {
        log.info("--- 验证已知问题字段的类型映射 ---");

        // 验证应该是 BYTEA 的字段
        validateSpecificField(actualColumns, "binaryfield", "bytea",
                "二进制字段应该用BYTEA，当前使用JSONB会影响性能");

        // 验证应该是 SMALLINT 的字段
        validateSpecificField(actualColumns, "bytefield", "smallint",
                "Byte字段应该用SMALLINT，当前使用JSONB失去类型安全性");
        validateSpecificField(actualColumns, "shortfield", "smallint",
                "Short字段应该用SMALLINT，当前使用JSONB失去类型安全性");

        // 验证应该是 NUMERIC 的字段
        validateSpecificField(actualColumns, "bigintegerfield", "numeric",
                "BigInteger字段应该用NUMERIC，当前使用JSONB无法进行数值运算");

        // 验证应该是 TEXT 的枚举字段
        validateSpecificField(actualColumns, "statusfield", "text",
                "枚举字段应该用TEXT，当前使用JSONB无法建立约束");
        validateSpecificField(actualColumns, "priorityfield", "text",
                "枚举字段应该用TEXT，当前使用JSONB无法建立约束");

        log.info("--- 问题字段验证完成 ---");
    }

    /**
     * 验证特定字段的类型
     */
    private void validateSpecificField(Map<String, ColumnInfo> actualColumns,
                                       String fieldName, String expectedType, String message) {
        if (actualColumns.containsKey(fieldName)) {
            ColumnInfo columnInfo = actualColumns.get(fieldName);
            String actualType = columnInfo.dataType.toLowerCase();
            String udtName = columnInfo.udtName != null ? columnInfo.udtName.toLowerCase() : "";

            boolean isCorrect = isTypeCompatible(expectedType, actualType, udtName);

            if (isCorrect) {
                log.info("✅ {}: {} -> {} (正确)", fieldName, expectedType, actualType);
            } else {
                throw new RuntimeException(String.format("⚠️  %s: 期望 %s, 实际 %s - %s", fieldName, expectedType, actualType, message));
            }
        } else {
            throw new RuntimeException(String.format("⚠️  字段不存在: %s", fieldName));
        }
    }

    // 辅助类
    private static class ColumnInfo {
        String columnName;
        String dataType;
        String udtName;
        boolean isNullable;
        String columnDefault;
        int maxLength;
        int precision;
        int scale;

        @Override
        public String toString() {
            return String.format("%s (%s/%s)", columnName, dataType, udtName);
        }
    }

    // 简单的断言方法
    private void assertNotNull(String message, Object obj) {
        if (obj == null) {
            throw new AssertionError(message);
        }
    }

    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}