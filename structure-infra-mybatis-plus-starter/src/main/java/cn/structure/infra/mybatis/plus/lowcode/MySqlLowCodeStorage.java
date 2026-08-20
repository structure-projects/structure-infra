/*
Copyright 2023 Structure Projects

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	`http://www.apache.org/licenses/LICENSE-2.0`

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.structure.infra.mybatis.plus.lowcode;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.lowcode.model.AutoFillType;
import cn.structure.infra.lowcode.model.FieldSchema;
import cn.structure.infra.lowcode.model.FieldType;
import cn.structure.infra.lowcode.model.ResourceSchema;
import cn.structure.infra.lowcode.repository.LowCodeStorage;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * MySQL/H2 低代码仓储实现（MyBatis SqlSession 版）
 * <p>
 * 基于 MyBatis SqlSession 的关系型数据库低代码存储实现，
 * 通过动态注册 MappedStatement 的方式执行 SQL，
 * 确保 SQL 能够经过 MyBatis 拦截器链（分页插件、数据权限、SQL 监控等）。
 * <p>
 * 核心特性：
 * <ul>
 *   <li>拦截器支持：SQL 经过 MyBatis 拦截器链，兼容所有 MyBatis 插件</li>
 *   <li>明确列名查询：使用 schema 中的字段列表替代 SELECT *，提升性能</li>
 *   <li>多方言适配：建表、分页等 SQL 根据数据库类型自动适配</li>
 *   <li>自动建表：根据 ResourceSchema 自动生成 DDL 语句</li>
 *   <li>主键自增：支持自增主键，插入后自动回填 ID</li>
 *   <li>自动填充：支持创建时间、更新时间自动填充</li>
 *   <li>动态 SQL：根据查询条件动态生成 WHERE 子句</li>
 * </ul>
 *
 * @author chuck
 * @version 1.0.0
 * @since 2026/6/29
 */
@Slf4j
public class MySqlLowCodeStorage implements LowCodeStorage {

    private final ResourceSchema schema;
    private final SqlSessionFactory sqlSessionFactory;
    private final Configuration configuration;
    private final DatabaseDialect dialect;
    private final String namespace;
    private final AtomicLong statementIdCounter = new AtomicLong(0);
    private final XMLLanguageDriver languageDriver;

    /**
     * 构造函数
     *
     * @param schema             资源 schema 定义
     * @param sqlSessionFactory  MyBatis SqlSessionFactory
     */
    public MySqlLowCodeStorage(ResourceSchema schema, SqlSessionFactory sqlSessionFactory) {
        this.schema = schema;
        this.sqlSessionFactory = sqlSessionFactory;
        this.configuration = sqlSessionFactory.getConfiguration();
        this.dialect = detectDialect();
        this.namespace = "lowcode." + schema.getResourceName();
        this.languageDriver = new XMLLanguageDriver();
    }

    /**
     * 检测数据库方言
     * <p>
     * 通过 Connection.getMetaData().getDatabaseProductName()
     * 自动识别数据库类型，用于方言适配。
     *
     * @return 数据库方言
     */
    private DatabaseDialect detectDialect() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection con = session.getConnection();
            String productName = con.getMetaData().getDatabaseProductName();
            if (productName == null) {
                return DatabaseDialect.MYSQL;
            }
            String lowerName = productName.toLowerCase();
            if (lowerName.contains("h2")) {
                return DatabaseDialect.H2;
            }
            if (lowerName.contains("oracle")) {
                return DatabaseDialect.ORACLE;
            }
            if (lowerName.contains("postgresql")) {
                return DatabaseDialect.POSTGRESQL;
            }
            if (lowerName.contains("sql server") || lowerName.contains("microsoft")) {
                return DatabaseDialect.SQL_SERVER;
            }
            return DatabaseDialect.MYSQL;
        } catch (Exception e) {
            log.warn("Failed to detect database dialect, defaulting to MySQL: {}", e.getMessage());
            return DatabaseDialect.MYSQL;
        }
    }

    /**
     * 初始化存储结构：根据 schema 自动执行建表 DDL。
     * <p>
     * 通过 JDBC Statement 直接执行方言相关的 CREATE TABLE 语句；若表已存在则忽略异常，
     * 仅打印告警日志，保证幂等。
     */
    @Override
    public void initialize() {
        String tableName = schema.getTableName();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection con = session.getConnection();
            try (Statement stmt = con.createStatement()) {
                // 直接执行自动生成的建表 DDL（含主键、唯一约束、索引）
                stmt.execute(buildCreateTableSql());
                log.info("LowCode table initialized: {}", tableName);
            } catch (SQLException e) {
                // 表已存在或其他 DDL 异常均视为幂等成功，仅告警
                log.warn("Failed to initialize table {} (may already exist): {}", tableName, e.getMessage());
            }
        }
    }

    /**
     * 构建建表 SQL 语句
     * <p>
     * 根据 schema 中的字段定义自动生成 DDL 语句，
     * 自动处理主键、唯一约束、索引等，根据数据库方言适配语法。
     *
     * @return 建表 SQL
     */
    private String buildCreateTableSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(schema.getTableName()).append(" (");

        List<String> columnDefs = new ArrayList<>();
        List<String> pkFields = new ArrayList<>();
        List<String> indexFields = new ArrayList<>();
        List<String> uniqueFields = new ArrayList<>();

        for (FieldSchema field : schema.getFields().values()) {
            StringBuilder colDef = new StringBuilder();
            colDef.append(field.getName()).append(" ");
            colDef.append(mapFieldType(field));

            if (field.isPrimaryKey()) {
                pkFields.add(field.getName());
                if (field.isAutoIncrement()) {
                    colDef.append(" AUTO_INCREMENT");
                }
            }
            if (!field.isNullable()) {
                colDef.append(" NOT NULL");
            }
            if (field.getDefaultValue() != null) {
                colDef.append(" DEFAULT '").append(field.getDefaultValue()).append("'");
            }
            if (field.isUnique()) {
                uniqueFields.add(field.getName());
            }
            if (field.isIndex()) {
                indexFields.add(field.getName());
            }
            columnDefs.add(colDef.toString());
        }

        sql.append(String.join(", ", columnDefs));

        // 主键约束：所有方言通用
        if (!pkFields.isEmpty()) {
            sql.append(", PRIMARY KEY (").append(String.join(", ", pkFields)).append(")");
        }

        // 索引与唯一约束按方言适配：MySQL 在建表语句内联声明；其他方言之索引需单独 CREATE INDEX
        if (dialect == DatabaseDialect.MYSQL) {
            // MySQL：UNIQUE KEY / KEY 内联到 CREATE TABLE
            for (String uk : uniqueFields) {
                sql.append(", UNIQUE KEY uk_").append(uk).append(" (").append(uk).append(")");
            }
            for (String idx : indexFields) {
                sql.append(", KEY idx_").append(idx).append(" (").append(idx).append(")");
            }
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } else {
            // 非 MySQL：唯一约束用 CONSTRAINT 内联，索引需用单独的 CREATE INDEX 语句
            for (String uk : uniqueFields) {
                sql.append(", CONSTRAINT uk_").append(uk).append(" UNIQUE (").append(uk).append(")");
            }
            for (String idx : indexFields) {
                // 拼接独立的 CREATE INDEX 语句（与建表语句以分号分隔）
                sql.append("); ");
                sql.append("CREATE INDEX IF NOT EXISTS idx_").append(idx)
                        .append(" ON ").append(schema.getTableName()).append(" (").append(idx).append(")");
                return sql.toString();
            }
            sql.append(")");
        }

        return sql.toString();
    }

    /**
     * 将字段类型映射为 SQL 类型（按数据库方言适配）
     *
     * @param field 字段定义
     * @return SQL 类型字符串
     */
    private String mapFieldType(FieldSchema field) {
        FieldType type = field.getType();
        // 按方言适配：BOOLEAN/DATETIME/JSON 在 MySQL 与其他方言间存在差异
        return switch (type) {
            case STRING -> "VARCHAR(" + field.getLength() + ")";
            case LONG -> "BIGINT";
            case INTEGER -> "INT";
            // MySQL 用 TINYINT(1) 表示布尔，H2/PG 等使用原生 BOOLEAN
            case BOOLEAN -> dialect == DatabaseDialect.MYSQL ? "TINYINT(1)" : "BOOLEAN";
            case DECIMAL -> "DECIMAL(" + field.getPrecision() + "," + field.getScale() + ")";
            // MySQL 用 DATETIME，其他方言用 TIMESTAMP
            case DATETIME -> dialect == DatabaseDialect.MYSQL ? "DATETIME" : "TIMESTAMP";
            case DATE -> "DATE";
            case TEXT -> "TEXT";
            // MySQL 原生 JSON 类型，其他方言退化为 TEXT
            case JSON -> dialect == DatabaseDialect.MYSQL ? "JSON" : "TEXT";
            default -> "VARCHAR(255)";
        };
    }

    /**
     * 构建查询列名列表（替代 SELECT *）
     * <p>
     * 根据 schema 中的字段定义生成明确的列名列表，
     * 避免查询不必要的列，提升性能并减少数据传输。
     *
     * @return 列名列表字符串
     */
    private String buildSelectColumns() {
        return schema.getFields().values().stream()
                .map(FieldSchema::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * 规范化查询结果的 Map 的 key 为小写
     * <p>
     * H2、Oracle 等数据库返回的列名可能是大写的，
     * 需要统一转换为小写以便与 schema 中的字段名匹配。
     *
     * @param row 原始查询结果行
     * @return 规范化后的 Map
     */
    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            normalized.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        return normalized;
    }

    /**
     * 规范化查询结果列表
     *
     * @param rows 原始查询结果列表
     * @return 规范化后的列表
     */
    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows != null ? rows : Collections.emptyList();
        }
        return rows.stream()
                .map(this::normalizeRow)
                .collect(Collectors.toList());
    }

    /**
     * 生成唯一的 MappedStatement ID
     *
     * @param prefix 前缀
     * @return 唯一的 statement ID
     */
    private String nextStatementId(String prefix) {
        return namespace + "." + prefix + "_" + statementIdCounter.incrementAndGet();
    }

    /**
     * 动态注册 MappedStatement 并执行 SELECT 查询
     * <p>
     * 通过动态注册 MappedStatement 的方式，使 SQL 能够经过 MyBatis 拦截器链，
     * 支持分页插件、数据权限、SQL 监控等所有 MyBatis 插件。
     *
     * @param sql    SQL 语句（使用 #{paramName} 格式的命名参数）
     * @param params 参数 Map
     * @return 查询结果列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeSelect(String sql, Map<String, Object> params) {
        String statementId = nextStatementId("select");
        try {
            registerSelectStatement(statementId, sql);
            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                List<Map<String, Object>> results = session.selectList(statementId, params);
                return normalizeRows(results);
            }
        } finally {
            configuration.getMappedStatements().remove(statementId);
        }
    }

    /**
     * 动态注册 MappedStatement 并执行 INSERT/UPDATE/DELETE
     * <p>
     * 通过动态注册 MappedStatement 的方式，使 SQL 能够经过 MyBatis 拦截器链。
     *
     * @param sql    SQL 语句（使用 #{paramName} 格式的命名参数）
     * @param params 参数 Map
     * @param type   SQL 命令类型
     * @return 影响的行数
     */
    private int executeUpdate(String sql, Map<String, Object> params, SqlCommandType type) {
        String statementId = nextStatementId(type.name().toLowerCase());
        try {
            registerUpdateStatement(statementId, sql, type);
            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                return session.update(statementId, params);
            }
        } finally {
            configuration.getMappedStatements().remove(statementId);
        }
    }

    /**
     * 注册 SELECT 类型的 MappedStatement
     * <p>
     * 配置 ResultMap 为 Map 类型，使查询结果以 Map 形式返回。
     *
     * @param statementId 语句 ID
     * @param sql         SQL 语句
     */
    private void registerSelectStatement(String statementId, String sql) {
        if (configuration.hasStatement(statementId)) {
            return;
        }

        org.apache.ibatis.mapping.SqlSource sqlSource = languageDriver.createSqlSource(
                configuration, "<script>" + sql + "</script>", Map.class);

        ResultMap resultMap = new ResultMap.Builder(
                configuration,
                statementId + "-Inline",
                Map.class,
                new ArrayList<ResultMapping>(),
                true
        ).build();
        configuration.addResultMap(resultMap);

        MappedStatement.Builder builder = new MappedStatement.Builder(
                configuration, statementId, sqlSource, SqlCommandType.SELECT);
        builder.resultMaps(Collections.singletonList(resultMap));
        builder.resource(namespace + ".dynamic");

        configuration.addMappedStatement(builder.build());
    }

    /**
     * 注册 COUNT 查询类型的 MappedStatement（返回 Long 类型）
     *
     * @param statementId 语句 ID
     * @param sql         SQL 语句
     */
    private void registerCountStatement(String statementId, String sql) {
        if (configuration.hasStatement(statementId)) {
            return;
        }

        org.apache.ibatis.mapping.SqlSource sqlSource = languageDriver.createSqlSource(
                configuration, "<script>" + sql + "</script>", Map.class);

        ResultMap resultMap = new ResultMap.Builder(
                configuration,
                statementId + "-Inline",
                Long.class,
                new ArrayList<ResultMapping>(),
                true
        ).build();
        configuration.addResultMap(resultMap);

        MappedStatement.Builder builder = new MappedStatement.Builder(
                configuration, statementId, sqlSource, SqlCommandType.SELECT);
        builder.resultMaps(Collections.singletonList(resultMap));
        builder.resource(namespace + ".dynamic");

        configuration.addMappedStatement(builder.build());
    }

    /**
     * 注册 INSERT/UPDATE/DELETE 类型的 MappedStatement
     *
     * @param statementId 语句 ID
     * @param sql         SQL 语句
     * @param type        SQL 命令类型
     */
    private void registerUpdateStatement(String statementId, String sql, SqlCommandType type) {
        if (configuration.hasStatement(statementId)) {
            return;
        }

        org.apache.ibatis.mapping.SqlSource sqlSource = languageDriver.createSqlSource(
                configuration, "<script>" + sql + "</script>", Map.class);

        MappedStatement.Builder builder = new MappedStatement.Builder(
                configuration, statementId, sqlSource, type);
        builder.resource(namespace + ".dynamic");

        configuration.addMappedStatement(builder.build());
    }

    /**
     * 执行带自增主键的 INSERT（获取生成的主键）
     * <p>
     * 由于需要获取自增主键，使用 JDBC 原生方式执行。
     * 注意：此方法的 SQL 不经过 MyBatis 拦截器链。
     *
     * @param sql    SQL 语句（使用 ? 占位符）
     * @param params 参数列表（按顺序）
     * @return 生成的主键
     */
    private Object executeInsertWithGeneratedKey(String sql, List<Object> params) {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection con = session.getConnection();
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int idx = 1;
                for (Object val : params) {
                    ps.setObject(idx++, val);
                }
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getObject(1);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Failed to execute insert with generated key: {}", e.getMessage());
            throw new RuntimeException("Insert failed", e);
        }
        return null;
    }

    /**
     * 保存或更新一条记录（以 Map 形式）。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>拷贝入参，避免污染调用方 Map</li>
     *   <li>按 {@link AutoFillType#CREATE} 与 {@link AutoFillType#CREATE_UPDATE} 自动填充时间字段</li>
     *   <li>根据主键是否存在且库里已有同 ID 记录，决定走 doUpdate 或 doInsert</li>
     * </ol>
     *
     * @param data 数据 Map，键为字段名、值为字段值
     * @return 保存后的完整数据（含自动生成的主键、自动填充字段）
     */
    @Override
    public Map<String, Object> save(Map<String, Object> data) {
        // 拷贝一份，避免污染调用方传入的 Map
        Map<String, Object> rowData = new LinkedHashMap<>(data);
        // 创建场景填充：CREATE_TIME 等
        fillAutoFields(rowData, AutoFillType.CREATE);
        // 创建/更新双重填充：CREATE_UPDATE 字段
        fillAutoFields(rowData, AutoFillType.CREATE_UPDATE);

        String idField = schema.getIdFieldName();
        boolean hasId = rowData.containsKey(idField) && rowData.get(idField) != null;

        if (hasId) {
            // 已带主键时先查库，存在则更新、不存在则插入
            Map<String, Object> existing = findById(rowData.get(idField));
            if (existing != null) {
                return doUpdate(rowData);
            }
        }
        return doInsert(rowData);
    }

    /**
     * 执行插入操作
     * <p>
     * 自增主键场景：使用 JDBC 原生方式获取生成的主键（不经过拦截器）
     * 非自增主键：使用 MyBatis SqlSession 执行（经过拦截器）
     *
     * @param data 数据
     * @return 插入后的数据（包含自动生成的主键）
     */
    private Map<String, Object> doInsert(Map<String, Object> data) {
        StringBuilder columns = new StringBuilder();
        StringBuilder jdbcPlaceholders = new StringBuilder();
        StringBuilder mybatisPlaceholders = new StringBuilder();
        Map<String, Object> namedParams = new HashMap<>();
        List<Object> jdbcParamList = new ArrayList<>();

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            if (schema.getField(fieldName) == null) {
                continue;
            }
            FieldSchema field = schema.getField(fieldName);
            if (field.isAutoIncrement() && entry.getValue() == null) {
                continue;
            }
            if (!first) {
                columns.append(", ");
                jdbcPlaceholders.append(", ");
                mybatisPlaceholders.append(", ");
            }
            columns.append(fieldName);
            jdbcPlaceholders.append("?");
            mybatisPlaceholders.append("#{").append(fieldName).append("}");
            namedParams.put(fieldName, entry.getValue());
            jdbcParamList.add(entry.getValue());
            first = false;
        }

        String mybatisSql = "INSERT INTO " + schema.getTableName() + " (" + columns + ") VALUES (" + mybatisPlaceholders + ")";
        String jdbcSql = "INSERT INTO " + schema.getTableName() + " (" + columns + ") VALUES (" + jdbcPlaceholders + ")";

        FieldSchema idField = schema.getIdField();
        if (idField != null && idField.isAutoIncrement()) {
            // 自增主键场景：走 JDBC 原生 PreparedStatement 以获取 RETURN_GENERATED_KEYS（不经过拦截器）
            Object key = executeInsertWithGeneratedKey(jdbcSql, jdbcParamList);
            if (key != null) {
                data.put(idField.getName(), key);
            }
        } else {
            // 非自增主键场景：走 MyBatis SqlSession，SQL 经过拦截器链
            executeUpdate(mybatisSql, namedParams, SqlCommandType.INSERT);
        }

        return findById(data.get(schema.getIdFieldName()));
    }

    /**
     * 执行更新操作
     * <p>
     * 根据主键更新记录，自动填充更新时间字段。
     * 使用 MyBatis SqlSession 执行，SQL 经过拦截器链。
     *
     * @param data 数据（必须包含主键）
     * @return 更新后的数据
     */
    private Map<String, Object> doUpdate(Map<String, Object> data) {
        StringBuilder setClause = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        String idFieldName = schema.getIdFieldName();

        // 更新场景填充：UPDATE_TIME 等（覆盖旧值由调用方决定，此处 putIfAbsent 仅在未显式设置时填充）
        fillAutoFields(data, AutoFillType.UPDATE);
        fillAutoFields(data, AutoFillType.CREATE_UPDATE);

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            // 主键字段不进入 SET 子句，仅作为 WHERE 条件
            if (fieldName.equals(idFieldName)) {
                params.put(fieldName, entry.getValue());
                continue;
            }
            // schema 外字段忽略，避免无效列
            if (schema.getField(fieldName) == null) {
                continue;
            }
            if (!first) {
                setClause.append(", ");
            }
            setClause.append(fieldName).append(" = #{").append(fieldName).append("}");
            params.put(fieldName, entry.getValue());
            first = false;
        }

        String sql = "UPDATE " + schema.getTableName() + " SET " + setClause +
                " WHERE " + idFieldName + " = #{" + idFieldName + "}";
        executeUpdate(sql, params, SqlCommandType.UPDATE);

        return findById(data.get(idFieldName));
    }

    /**
     * 根据主键删除记录。
     * <p>
     * 通过动态 MappedStatement 执行 DELETE，SQL 经过 MyBatis 拦截器链。
     *
     * @param id 主键值
     */
    @Override
    public void removeById(Object id) {
        String idFieldName = schema.getIdFieldName();
        String sql = "DELETE FROM " + schema.getTableName() + " WHERE " + idFieldName + " = #{id}";
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        executeUpdate(sql, params, SqlCommandType.DELETE);
    }

    /**
     * 根据主键查询单条记录。
     * <p>
     * 使用 schema 中明确列名替代 SELECT *，结果以 Map 形式返回。
     *
     * @param id 主键值
     * @return 数据 Map，未找到时返回 null
     */
    @Override
    public Map<String, Object> findById(Object id) {
        String idFieldName = schema.getIdFieldName();
        String sql = "SELECT " + buildSelectColumns() + " FROM " + schema.getTableName() +
                " WHERE " + idFieldName + " = #{id}";
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        List<Map<String, Object>> results = executeSelect(sql, params);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 根据主键查询（与 findById 等价，语义上用于"读模型"）。
     *
     * @param id 主键值
     * @return 数据 Map，未找到时返回 null
     */
    @Override
    public Map<String, Object> queryById(Object id) {
        return findById(id);
    }

    /**
     * 根据条件查询单条记录，取结果集首条。
     *
     * @param queryParams 查询条件 Map，键为字段名、值为等值匹配值
     * @return 首条匹配记录，无匹配时返回 null
     */
    @Override
    public Map<String, Object> queryOne(Map<String, Object> queryParams) {
        List<Map<String, Object>> list = queryList(queryParams);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 根据条件查询单条记录，并以 {@link Optional} 包装返回。
     *
     * @param queryParams 查询条件 Map
     * @return 包含首条匹配记录的 Optional
     */
    @Override
    public Optional<Map<String, Object>> queryOneOptional(Map<String, Object> queryParams) {
        return Optional.ofNullable(queryOne(queryParams));
    }

    /**
     * 根据条件等值匹配查询列表。
     * <p>
     * 仅 schema 中存在的字段才会进入 WHERE 子句，使用 AND 连接的等值条件。
     *
     * @param queryParams 查询条件 Map，为 null 或空时等价于全表查询
     * @return 匹配记录列表，无匹配时返回空列表
     */
    @Override
    public List<Map<String, Object>> queryList(Map<String, Object> queryParams) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(buildSelectColumns()).append(" FROM ").append(schema.getTableName());
        Map<String, Object> params = new HashMap<>();

        if (queryParams != null && !queryParams.isEmpty()) {
            // 动态拼接 WHERE 子句：仅 schema 内字段参与，AND 连接等值条件
            StringBuilder where = new StringBuilder(" WHERE ");
            boolean first = true;
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                String fieldName = entry.getKey();
                if (schema.getField(fieldName) == null) {
                    continue;
                }
                if (!first) {
                    where.append(" AND ");
                }
                where.append(fieldName).append(" = #{").append(fieldName).append("}");
                params.put(fieldName, entry.getValue());
                first = false;
            }
            if (!first) {
                sql.append(where);
            }
        }

        return executeSelect(sql.toString(), params);
    }

    /**
     * 分页查询。
     * <p>
     * 先 COUNT 总数，再按方言生成分页 SQL 取当前页记录。
     * total 为 0 时直接返回空记录，避免无意义查询。
     *
     * @param reqPage 分页请求（页码、每页大小，为 null 时取默认 1/10）
     * @return 分页结果，含当前页、总页数、总条数、当前页记录
     */
    @Override
    public ResPage<Map<String, Object>> queryPage(ReqPage reqPage) {
        // 页码与每页大小兜底
        long pageNum = reqPage.getPage() != null ? reqPage.getPage() : 1;
        long pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        long total = count(null);
        ResPage<Map<String, Object>> page = new ResPage<>();
        page.setCurrent(pageNum);
        page.setSize(pageSize);
        page.setTotal(total);

        if (total == 0) {
            // 无数据时直接返回，避免执行无意义查询
            page.setRecords(Collections.emptyList());
            page.setPages(0L);
            return page;
        }

        // 计算总页数（向上取整）
        long pages = total / pageSize + (total % pageSize == 0 ? 0 : 1);
        page.setPages(pages);

        // 按方言生成分页 SQL（MySQL LIMIT、Oracle ROWNUM、PG/SQL Server OFFSET FETCH）
        String baseSql = "SELECT " + buildSelectColumns() + " FROM " + schema.getTableName();
        String paginationSql = buildPaginationSql(baseSql, pageNum, pageSize);

        List<Map<String, Object>> records = executeSelect(paginationSql, new HashMap<>());
        page.setRecords(records);

        return page;
    }

    /**
     * 构建分页 SQL（根据数据库方言适配）
     * <p>
     * 支持的分页语法：
     * <ul>
     *   <li>MySQL / H2：LIMIT ... OFFSET ...</li>
     *   <li>Oracle：ROWNUM 嵌套子查询</li>
     *   <li>PostgreSQL / SQL Server：OFFSET ... FETCH NEXT ... ONLY（SQL:2008 标准）</li>
     * </ul>
     *
     * @param baseSql  基础查询 SQL
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页大小
     * @return 分页 SQL
     */
    private String buildPaginationSql(String baseSql, long pageNum, long pageSize) {
        long offset = (pageNum - 1) * pageSize;
        switch (dialect) {
            case MYSQL, H2 -> {
                return baseSql + " LIMIT " + pageSize + " OFFSET " + offset;
            }
            case ORACLE -> {
                return "SELECT * FROM (SELECT ROWNUM rn, t.* FROM (" + baseSql + ") t WHERE ROWNUM <= "
                        + (offset + pageSize) + ") WHERE rn > " + offset;
            }
            default -> {
                return baseSql + " OFFSET " + offset + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY";
            }
        }
    }

    /**
     * 批量保存记录（逐条调用 save）。
     * <p>
     * 未使用批量 INSERT，每条记录独立处理自动填充与 upsert 判断。
     *
     * @param dataList 数据列表，为 null 或空时返回空列表
     * @return 保存后的数据列表（含自动生成的主键与自动填充字段）
     */
    @Override
    public List<Map<String, Object>> saveBatch(List<Map<String, Object>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> data : dataList) {
            result.add(save(data));
        }
        return result;
    }

    /**
     * 根据主键列表批量删除。
     * <p>
     * 通过 IN 子句一次性删除，每个 ID 用独立命名参数（id_0、id_1、…）。
     *
     * @param ids 主键列表，为 null 或空时不执行任何操作
     */
    @Override
    public void removeBatchByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String idFieldName = schema.getIdFieldName();
        // 拼接 IN 子句的命名参数占位符：#{id_0}, #{id_1}, ...
        StringBuilder placeholders = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            String paramName = "id_" + i;
            placeholders.append("#{").append(paramName).append("}");
            params.put(paramName, ids.get(i));
        }
        String sql = "DELETE FROM " + schema.getTableName() + " WHERE " + idFieldName + " IN (" + placeholders + ")";
        executeUpdate(sql, params, SqlCommandType.DELETE);
    }

    /**
     * 根据主键列表批量查询。
     * <p>
     * 通过 IN 子句一次性查询，使用 schema 中的明确列名替代 SELECT *。
     *
     * @param ids 主键列表，为 null 或空时返回空列表
     * @return 匹配记录列表
     */
    @Override
    public List<Map<String, Object>> listByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String idFieldName = schema.getIdFieldName();
        // 拼接 IN 子句的命名参数占位符：#{id_0}, #{id_1}, ...
        StringBuilder placeholders = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                placeholders.append(", ");
            }
            String paramName = "id_" + i;
            placeholders.append("#{").append(paramName).append("}");
            params.put(paramName, ids.get(i));
        }
        String sql = "SELECT " + buildSelectColumns() + " FROM " + schema.getTableName() +
                " WHERE " + idFieldName + " IN (" + placeholders + ")";
        return executeSelect(sql, params);
    }

    /**
     * 按条件统计记录数。
     * <p>
     * 通过动态注册返回 Long 的 COUNT MappedStatement 执行；结果兼容 {@link Number} 类型，
     * 统一转为 long 返回。
     *
     * @param queryParams 查询条件 Map，为 null 或空时统计全表
     * @return 匹配的记录数
     */
    @Override
    public long count(Map<String, Object> queryParams) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(schema.getTableName());
        Map<String, Object> params = new HashMap<>();

        if (queryParams != null && !queryParams.isEmpty()) {
            // 动态拼接 WHERE 子句：仅 schema 内字段参与，AND 连接等值条件
            StringBuilder where = new StringBuilder(" WHERE ");
            boolean first = true;
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                String fieldName = entry.getKey();
                if (schema.getField(fieldName) == null) {
                    continue;
                }
                if (!first) {
                    where.append(" AND ");
                }
                where.append(fieldName).append(" = #{").append(fieldName).append("}");
                params.put(fieldName, entry.getValue());
                first = false;
            }
            if (!first) {
                sql.append(where);
            }
        }

        // COUNT 走专用 Long ResultMap 的 MappedStatement
        String statementId = nextStatementId("count");
        try {
            registerCountStatement(statementId, sql.toString());
            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                Object result = session.selectOne(statementId, params);
                // 兼容不同驱动返回的 Number 子类（Long/BigInteger 等）
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                }
                return 0L;
            }
        } finally {
            configuration.getMappedStatements().remove(statementId);
        }
    }

    /**
     * 判断是否存在匹配条件的记录。
     *
     * @param queryParams 查询条件 Map
     * @return 存在返回 true，否则 false
     */
    @Override
    public boolean exists(Map<String, Object> queryParams) {
        return count(queryParams) > 0;
    }

    /**
     * 填充自动字段
     * <p>
     * 根据字段的 autoFill 策略，自动填充创建时间、更新时间等字段。
     * 使用 putIfAbsent 确保用户显式设置的值不会被覆盖。
     *
     * @param data     数据
     * @param fillType 填充类型
     */
    private void fillAutoFields(Map<String, Object> data, AutoFillType fillType) {
        LocalDateTime now = LocalDateTime.now();
        for (FieldSchema field : schema.getFields().values()) {
            // 仅处理与当前填充类型匹配的字段（CREATE / UPDATE / CREATE_UPDATE）
            if (field.getAutoFill() == fillType) {
                String name = field.getName();
                // 按字段类型生成对应的时间值：DATETIME 用 LocalDateTime，DATE 用 LocalDate
                switch (field.getType()) {
                    case DATETIME -> data.putIfAbsent(name, now);
                    case DATE -> data.putIfAbsent(name, now.toLocalDate());
                    default -> {
                    }
                }
            }
        }
    }

    /**
     * 数据库方言枚举
     * <p>
     * 用于适配不同数据库的 SQL 语法差异，
     * 如建表语句、分页语句、数据类型等。
     */
    private enum DatabaseDialect {
        /** MySQL */
        MYSQL,
        /** H2 内存数据库 */
        H2,
        /** Oracle */
        ORACLE,
        /** SQL Server */
        SQL_SERVER,
        /** PostgreSQL */
        POSTGRESQL
    }
}