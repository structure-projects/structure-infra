package cn.structure.infra.mybatis.plus.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.repository.RepositoryDelegate;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MyBatis Plus 的 RepositoryDelegate 适配实现
 * <p>
 * 该类是 RepositoryDelegate SPI 在 MyBatis Plus 存储类型下的标准实现，委托
 * {@link BaseMapper} 完成单表的 CRUD 操作。它在仓储框架中扮演"具体存储适配层"的角色：
 * <ul>
 *   <li>上层由 {@code RepositoryFacade} 统一暴露给业务方，本类不直接面向业务</li>
 *   <li>当用户未提供自定义 Delegate 时，由 {@link MybatisPlusDelegateFactory} 自动创建本类实例</li>
 *   <li>当用户提供自定义 Delegate 子类时，由 {@link MybatisPlusDelegateBeanPostProcessor}
 *       在 Bean 初始化后自动注入 BaseMapper 与实体类型</li>
 * </ul>
 * <p>
 * 实现说明：
 * <ul>
 *   <li>查询条件通过反射读取实体非空字段，按"等值匹配"组装 {@link QueryWrapper}，并将驼峰字段名
 *       转为下划线列名以匹配数据库列</li>
 *   <li>ID 字段名默认为 "id"，可通过构造器或 setter 自定义</li>
 *   <li>save 方法根据 ID 是否为空自动区分 insert / update</li>
 *   <li>分页委托给 MyBatis Plus 的 {@link Page}，由分页拦截器生成方言相关 SQL</li>
 * </ul>
 *
 * @param <T>  实体（PO）类型
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class MybatisPlusRepositoryDelegate<T, ID> implements RepositoryDelegate<T, ID> {

    /** 底层 MyBatis Plus Mapper，由工厂或 BeanPostProcessor 注入 */
    protected BaseMapper<T> baseMapper;
    /** PO 实体类型，用于反射读取字段 */
    protected Class<T> entityClass;
    /** 主键字段名，默认 "id"，可被子类覆盖 */
    protected String idFieldName;

    /**
     * 默认构造器，用于用户自定义子类场景。
     * <p>
     * 创建后由 {@link MybatisPlusDelegateBeanPostProcessor} 通过 setter 注入依赖。
     */
    public MybatisPlusRepositoryDelegate() {
    }

    /**
     * 以默认主键字段名 "id" 构造 Delegate。
     *
     * @param baseMapper  MyBatis Plus 的 BaseMapper，承担实际 CRUD
     * @param entityClass PO 实体类型
     */
    public MybatisPlusRepositoryDelegate(BaseMapper<T> baseMapper, Class<T> entityClass) {
        this(baseMapper, entityClass, "id");
    }

    /**
     * 全参构造器，工厂自动创建场景使用。
     *
     * @param baseMapper  MyBatis Plus 的 BaseMapper
     * @param entityClass PO 实体类型
     * @param idFieldName 主键字段名（用于反射读取 ID 值）
     */
    public MybatisPlusRepositoryDelegate(BaseMapper<T> baseMapper, Class<T> entityClass, String idFieldName) {
        this.baseMapper = baseMapper;
        this.entityClass = entityClass;
        this.idFieldName = idFieldName;
        log.info("MybatisPlusRepositoryDelegate initialized for entity: {}", entityClass.getSimpleName());
    }

    /**
     * 注入 BaseMapper，供 BeanPostProcessor 在自定义子类上调用。
     *
     * @param baseMapper MyBatis Plus 的 BaseMapper
     */
    public void setBaseMapper(BaseMapper<T> baseMapper) {
        this.baseMapper = baseMapper;
    }

    /**
     * 注入 PO 实体类型，供 BeanPostProcessor 在自定义子类上调用。
     *
     * @param entityClass PO 实体类型
     */
    public void setEntityClass(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    /**
     * 设置主键字段名，供自定义子类覆盖默认 "id"。
     *
     * @param idFieldName 主键字段名
     */
    public void setIdFieldName(String idFieldName) {
        this.idFieldName = idFieldName;
    }

    /**
     * 保存或更新实体。
     * <p>
     * 根据 ID 字段值是否为空自动选择策略：ID 为空执行 insert，否则执行 updateById。
     *
     * @param entity 实体对象，为 null 时直接返回 null
     * @return 保存后的实体（与入参同一引用）
     */
    @Override
    public T save(T entity) {
        if (entity == null) {
            return null;
        }
        // 反射读取主键值，决定走新增还是更新分支
        ID id = getIdValue(entity);
        if (id == null) {
            baseMapper.insert(entity);
        } else {
            baseMapper.updateById(entity);
        }
        log.debug("Saved entity: id={}, entity={}", id, entity);
        return entity;
    }

    /**
     * 根据主键删除记录。
     *
     * @param id 主键值，为 null 时不执行任何操作
     */
    @Override
    public void removeById(ID id) {
        if (id != null) {
            baseMapper.deleteById((Serializable) id);
            log.debug("Removed entity: id={}", id);
        }
    }

    /**
     * 根据主键查询实体。
     *
     * @param id 主键值，为 null 时返回 null
     * @return 实体对象，未找到时返回 null
     */
    @Override
    public T findById(ID id) {
        if (id == null) {
            return null;
        }
        T entity = baseMapper.selectById((Serializable) id);
        log.debug("Find by id: id={}, found={}", id, entity != null);
        return entity;
    }

    /**
     * 根据主键查询（与 findById 等价，语义上用于"读模型"）。
     *
     * @param id 主键值
     * @return 实体对象，未找到时返回 null
     */
    @Override
    public T queryById(ID id) {
        return findById(id);
    }

    /**
     * 根据主键查询并以 {@link Optional} 包装返回，避免空指针。
     *
     * @param id 主键值
     * @return 包含实体的 Optional，未找到时为 {@link Optional#empty()}
     */
    @Override
    public Optional<T> queryByIdOptional(ID id) {
        return Optional.ofNullable(queryById(id));
    }

    /**
     * 根据非空字段等值匹配查询单条记录。
     * <p>
     * 将条件对象非空字段组装为 {@link QueryWrapper}，取结果集第一条；多于一条时仅返回首条。
     *
     * @param condition 查询条件对象，为 null 时返回 null
     * @return 首条匹配记录，无匹配时返回 null
     */
    @Override
    public T queryOne(T condition) {
        if (condition == null) {
            return null;
        }
        QueryWrapper<T> queryWrapper = buildQueryWrapper(condition);
        List<T> results = baseMapper.selectList(queryWrapper);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 根据条件查询单条记录，并以 {@link Optional} 包装返回。
     *
     * @param condition 查询条件对象
     * @return 包含首条匹配记录的 Optional
     */
    @Override
    public Optional<T> queryOneOptional(T condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    /**
     * 根据条件查询列表。
     * <p>
     * 条件为 null 时等价于全表查询；否则按非空字段等值匹配。
     *
     * @param condition 查询条件对象，可为 null
     * @return 匹配的实体列表，无匹配时返回空列表
     */
    @Override
    public List<T> queryList(T condition) {
        if (condition == null) {
            return baseMapper.selectList(null);
        }
        QueryWrapper<T> queryWrapper = buildQueryWrapper(condition);
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 分页查询。
     * <p>
     * 委托 MyBatis Plus 的 {@link Page} 执行分页，实际分页 SQL 由分页拦截器按方言生成。
     *
     * @param reqPage 分页请求（页码、每页大小，为 null 时取默认 1/10）
     * @return 分页结果，包含当前页、总页数、总条数、当前页记录
     */
    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        // 页码与每页大小兜底，避免 NPE
        long pageNum = reqPage.getPage() != null ? reqPage.getPage() : 1;
        long pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        Page<T> page = new Page<>(pageNum, pageSize);
        IPage<T> result = baseMapper.selectPage(page, null);

        // 将 MyBatis Plus 分页结果转写为统一 ResPage
        ResPage<T> resPage = new ResPage<>();
        resPage.setCurrent(result.getCurrent());
        resPage.setPages(result.getPages());
        resPage.setSize(result.getSize());
        resPage.setTotal(result.getTotal());
        resPage.setRecords(result.getRecords());

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum, pageSize, result.getTotal(), result.getRecords().size());
        return resPage;
    }

    /**
     * 根据条件对象的非空字段构建等值查询 {@link QueryWrapper}。
     * <p>
     * 反射读取所有字段（含父类），将驼峰字段名转为下划线列名后拼接 eq 条件。
     *
     * @param condition 条件对象
     * @return 已填充等值条件的 QueryWrapper
     */
    private QueryWrapper<T> buildQueryWrapper(T condition) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
        try {
            Field[] fields = getAllFields(condition.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(condition);
                if (value != null) {
                    // 字段名驼峰转下划线，以匹配数据库列名
                    queryWrapper.eq(camelToUnderline(field.getName()), value);
                }
            }
        } catch (Exception e) {
            log.warn("Error building query wrapper: {}", e.getMessage());
        }
        return queryWrapper;
    }

    /**
     * 收集类及其所有父类（直到 Object）的声明字段。
     *
     * @param clazz 起始类
     * @return 全部字段数组
     */
    private Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    /**
     * 反射读取实体的主键字段值。
     *
     * @param entity 实体对象
     * @return 主键值，无法读取时返回 null
     */
    @SuppressWarnings("unchecked")
    private ID getIdValue(T entity) {
        try {
            Field field = findIdField(entity.getClass());
            if (field != null) {
                field.setAccessible(true);
                return (ID) field.get(entity);
            }
        } catch (Exception e) {
            log.warn("Error getting id value: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 沿继承链递归查找主键字段。
     *
     * @param clazz 起始类
     * @return 主键 Field，未找到返回 null
     */
    private Field findIdField(Class<?> clazz) {
        try {
            Field field = clazz.getDeclaredField(idFieldName);
            return field;
        } catch (NoSuchFieldException e) {
            // 当前类未声明 ID 字段，继续向父类递归
            if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
                return findIdField(clazz.getSuperclass());
            }
            return null;
        }
    }

    /**
     * 驼峰命名转下划线命名（如 userName → user_name），用于对齐数据库列名。
     *
     * @param param 原始字段名
     * @return 下划线命名，入参为空时返回空字符串
     */
    private String camelToUnderline(String param) {
        if (param == null || "".equals(param.trim())) {
            return "";
        }
        int len = param.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = param.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append("_");
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 批量保存实体（逐条 insert）。
     *
     * @param entities 实体列表，为 null 或空时返回空列表
     * @return 入参列表引用（已写入数据库）
     */
    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        entities.forEach(baseMapper::insert);
        return entities;
    }

    /**
     * 根据主键列表批量删除。
     *
     * @param ids 主键列表，为 null 或空时不执行任何操作
     */
    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null && !ids.isEmpty()) {
            baseMapper.deleteBatchIds(ids.stream()
                    .map(id -> (Serializable) id)
                    .toList());
        }
    }

    /**
     * 根据主键列表批量查询。
     *
     * @param ids 主键列表，为 null 或空时返回空列表
     * @return 匹配的实体列表
     */
    @Override
    public List<T> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectBatchIds(ids.stream()
                .map(id -> (Serializable) id)
                .toList());
    }

    /**
     * 按条件统计记录数。
     *
     * @param condition 条件对象，为 null 时统计全表
     * @return 匹配的记录数
     */
    @Override
    public long count(T condition) {
        if (condition == null) {
            return baseMapper.selectCount(null);
        }
        QueryWrapper<T> queryWrapper = buildQueryWrapper(condition);
        return baseMapper.selectCount(queryWrapper);
    }

    /**
     * 判断是否存在匹配条件的记录。
     *
     * @param condition 条件对象
     * @return 存在返回 true，否则 false
     */
    @Override
    public boolean exists(T condition) {
        return count(condition) > 0;
    }
}
