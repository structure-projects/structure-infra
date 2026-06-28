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

    @Override
    public void initialize() {
        String tableName = schema.getTableName();
        try (SqlSession session = sqlSessionFactory.openSession()) {
            Connection con = session.getConnection();
            try (Statement stmt = con.createStatement()) {
                stmt.execute(buildCreateTableSql());
                log.info("LowCode table initialized: {}", tableName);
            } catch (SQLException e) {
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

        if (!pkFields.isEmpty()) {
            sql.append(", PRIMARY KEY (").append(String.join(", ", pkFields)).append(")");
        }

        if (dialect == DatabaseDialect.MYSQL) {
            for (String uk : uniqueFields) {
                sql.append(", UNIQUE KEY uk_").append(uk).append(" (").append(uk).append(")");
            }
            for (String idx : indexFields) {
                sql.append(", KEY idx_").append(idx).append(" (").append(idx).append(")");
            }
            sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } else {
            for (String uk : uniqueFields) {
                sql.append(", CONSTRAINT uk_").append(uk).append(" UNIQUE (").append(uk).append(")");
            }
            for (String idx : indexFields) {
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
        return switch (type) {
            case STRING -> "VARCHAR(" + field.getLength() + ")";
            case LONG -> "BIGINT";
            case INTEGER -> "INT";
            case BOOLEAN -> dialect == DatabaseDialect.MYSQL ? "TINYINT(1)" : "BOOLEAN";
            case DECIMAL -> "DECIMAL(" + field.getPrecision() + "," + field.getScale() + ")";
            case DATETIME -> dialect == DatabaseDialect.MYSQL ? "DATETIME" : "TIMESTAMP";
            case DATE -> "DATE";
            case TEXT -> "TEXT";
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

    @Override
    public Map<String, Object> save(Map<String, Object> data) {
        Map<String, Object> rowData = new LinkedHashMap<>(data);
        fillAutoFields(rowData, AutoFillType.CREATE);
        fillAutoFields(rowData, AutoFillType.CREATE_UPDATE);

        String idField = schema.getIdFieldName();
        boolean hasId = rowData.containsKey(idField) && rowData.get(idField) != null;

        if (hasId) {
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
            Object key = executeInsertWithGeneratedKey(jdbcSql, jdbcParamList);
            if (key != null) {
                data.put(idField.getName(), key);
            }
        } else {
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

        fillAutoFields(data, AutoFillType.UPDATE);
        fillAutoFields(data, AutoFillType.CREATE_UPDATE);

        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            if (fieldName.equals(idFieldName)) {
                params.put(fieldName, entry.getValue());
                continue;
            }
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

    @Override
    public void removeById(Object id) {
        String idFieldName = schema.getIdFieldName();
        String sql = "DELETE FROM " + schema.getTableName() + " WHERE " + idFieldName + " = #{id}";
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        executeUpdate(sql, params, SqlCommandType.DELETE);
    }

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

    @Override
    public Map<String, Object> queryById(Object id) {
        return findById(id);
    }

    @Override
    public Map<String, Object> queryOne(Map<String, Object> queryParams) {
        List<Map<String, Object>> list = queryList(queryParams);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Optional<Map<String, Object>> queryOneOptional(Map<String, Object> queryParams) {
        return Optional.ofNullable(queryOne(queryParams));
    }

    @Override
    public List<Map<String, Object>> queryList(Map<String, Object> queryParams) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(buildSelectColumns()).append(" FROM ").append(schema.getTableName());
        Map<String, Object> params = new HashMap<>();

        if (queryParams != null && !queryParams.isEmpty()) {
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

    @Override
    public ResPage<Map<String, Object>> queryPage(ReqPage reqPage) {
        long pageNum = reqPage.getPage() != null ? reqPage.getPage() : 1;
        long pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        long total = count(null);
        ResPage<Map<String, Object>> page = new ResPage<>();
        page.setCurrent(pageNum);
        page.setSize(pageSize);
        page.setTotal(total);

        if (total == 0) {
            page.setRecords(Collections.emptyList());
            page.setPages(0L);
            return page;
        }

        long pages = total / pageSize + (total % pageSize == 0 ? 0 : 1);
        page.setPages(pages);

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

    @Override
    public void removeBatchByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String idFieldName = schema.getIdFieldName();
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

    @Override
    public List<Map<String, Object>> listByIds(List<Object> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String idFieldName = schema.getIdFieldName();
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

    @Override
    public long count(Map<String, Object> queryParams) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM ").append(schema.getTableName());
        Map<String, Object> params = new HashMap<>();

        if (queryParams != null && !queryParams.isEmpty()) {
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

        String statementId = nextStatementId("count");
        try {
            registerCountStatement(statementId, sql.toString());
            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                Object result = session.selectOne(statementId, params);
                if (result instanceof Number) {
                    return ((Number) result).longValue();
                }
                return 0L;
            }
        } finally {
            configuration.getMappedStatements().remove(statementId);
        }
    }

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
            if (field.getAutoFill() == fillType) {
                String name = field.getName();
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