package cn.structure.infra.mybatis.plus.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import cn.structure.infra.repository.GenericTypeResolver;
import cn.structure.infra.repository.RepositoryDelegate;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import jakarta.persistence.Id;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
 *   <li>ID 字段名通过 PO 类的 {@link Id} 注解自动识别，默认为 "id"</li>
 *   <li>save 方法根据 ID 是否为空自动区分 insert / update</li>
 *   <li>分页委托给 MyBatis Plus 的 {@link Page}，由分页拦截器生成方言相关 SQL</li>
 * </ul>
 * <p>
 * 注意：Entity ↔ PO 转换在此层完成，Facade 层只操作领域实体。
 *
 * @param <E>  领域实体类型（Entity）
 * @param <P>  持久化对象类型（PO）
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.3
 * @since 2026/6/28
 */
@Slf4j
public class MybatisPlusRepositoryDelegate<E, P, ID> implements RepositoryDelegate<E, ID> {

    @Autowired(required = false)
    protected BaseMapper<P> baseMapper;
    private volatile Class<E> entityClass;
    private volatile Class<P> poClass;
    private volatile Class<ID> idClass;
    private volatile String idFieldName;

    public MybatisPlusRepositoryDelegate() {
    }

    @Override
    public Class<E> getEntityClass() {
        if (entityClass == null) {
            synchronized (this) {
                if (entityClass == null) {
                    entityClass = resolveEntityClass();
                    log.debug("Resolved entityClass: {}", entityClass != null ? entityClass.getSimpleName() : "null");
                }
            }
        }
        return entityClass;
    }

    @Override
    public Class<?> getPoClass() {
        if (poClass == null) {
            synchronized (this) {
                if (poClass == null) {
                    poClass = resolvePoClass();
                    log.debug("Resolved poClass: {}", poClass != null ? poClass.getSimpleName() : "null");
                }
            }
        }
        return poClass;
    }

    @Override
    public Class<ID> getIdClass() {
        if (idClass == null) {
            synchronized (this) {
                if (idClass == null) {
                    idClass = resolveIdClass();
                    log.debug("Resolved idClass: {}", idClass != null ? idClass.getSimpleName() : "null");
                }
            }
        }
        return idClass;
    }

    @Override
    public String getIdFieldName() {
        if (idFieldName == null) {
            synchronized (this) {
                if (idFieldName == null) {
                    idFieldName = resolveIdFieldName();
                    log.debug("Resolved idFieldName: {}", idFieldName);
                }
            }
        }
        return idFieldName;
    }

    @SuppressWarnings("unchecked")
    private Class<E> resolveEntityClass() {
        try {
            return (Class<E>) GenericTypeResolver.resolveEntityClass(getClass());
        } catch (Exception e) {
            log.warn("Cannot resolve entityClass: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Class<P> resolvePoClass() {
        try {
            return (Class<P>) GenericTypeResolver.resolvePoClass(getClass());
        } catch (Exception e) {
            log.warn("Cannot resolve poClass: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Class<ID> resolveIdClass() {
        try {
            return (Class<ID>) GenericTypeResolver.resolveIdClass(getClass());
        } catch (Exception e) {
            log.warn("Cannot resolve idClass: {}", e.getMessage());
            return null;
        }
    }

    private String resolveIdFieldName() {
        Class<?> poType = getPoClass();
        if (poType != null) {
            Field idField = findFieldWithAnnotation(poType, Id.class);
            if (idField != null) {
                return idField.getName();
            }
        }
        return "id";
    }

    private Field findFieldWithAnnotation(Class<?> clazz, Class<?> annotationClass) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent((Class<? extends java.lang.annotation.Annotation>) annotationClass)) {
                return field;
            }
        }
        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            return findFieldWithAnnotation(clazz.getSuperclass(), annotationClass);
        }
        return null;
    }

    @Override
    public E save(E entity) {
        if (entity == null) {
            return null;
        }
        P po = toPo(entity);
        ID id = getIdValue(po);
        if (id == null) {
            baseMapper.insert(po);
        } else {
            baseMapper.updateById(po);
        }
        log.debug("Saved entity: id={}, entity={}", id, entity);
        return toEntity(po);
    }

    @Override
    public void removeById(ID id) {
        if (id != null) {
            baseMapper.deleteById((Serializable) id);
            log.debug("Removed entity: id={}", id);
        }
    }

    @Override
    public E findById(ID id) {
        if (id == null) {
            return null;
        }
        P po = baseMapper.selectById((Serializable) id);
        log.debug("Find by id: id={}, found={}", id, po != null);
        return toEntity(po);
    }

    @Override
    public E queryById(ID id) {
        return findById(id);
    }

    @Override
    public Optional<E> queryByIdOptional(ID id) {
        return Optional.ofNullable(queryById(id));
    }

    @Override
    public E queryOne(E condition) {
        if (condition == null) {
            return null;
        }
        P poCondition = toPo(condition);
        QueryWrapper<P> queryWrapper = buildQueryWrapper(poCondition);
        List<P> results = baseMapper.selectList(queryWrapper);
        P po = results.isEmpty() ? null : results.get(0);
        return toEntity(po);
    }

    @Override
    public Optional<E> queryOneOptional(E condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<E> queryList(E condition) {
        if (condition == null) {
            List<P> poList = baseMapper.selectList(null);
            return toEntityList(poList);
        }
        P poCondition = toPo(condition);
        QueryWrapper<P> queryWrapper = buildQueryWrapper(poCondition);
        List<P> poList = baseMapper.selectList(queryWrapper);
        return toEntityList(poList);
    }

    @Override
    public ResPage<E> queryPage(ReqPage reqPage) {
        long pageNum = reqPage.getPage() != null ? reqPage.getPage() : 1;
        long pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        Page<P> page = new Page<>(pageNum, pageSize);
        IPage<P> result = baseMapper.selectPage(page, null);

        ResPage<E> resPage = new ResPage<>();
        resPage.setCurrent(result.getCurrent());
        resPage.setPages(result.getPages());
        resPage.setSize(result.getSize());
        resPage.setTotal(result.getTotal());
        resPage.setRecords(toEntityList(result.getRecords()));

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum, pageSize, result.getTotal(), result.getRecords().size());
        return resPage;
    }

    @Override
    public List<E> saveBatch(List<E> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<P> poList = toPoList(entities);
        poList.forEach(baseMapper::insert);
        return toEntityList(poList);
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null && !ids.isEmpty()) {
            baseMapper.deleteBatchIds(ids.stream()
                    .map(id -> (Serializable) id)
                    .toList());
        }
    }

    @Override
    public List<E> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<P> poList = baseMapper.selectBatchIds(ids.stream()
                .map(id -> (Serializable) id)
                .toList());
        return toEntityList(poList);
    }

    @Override
    public long count(E condition) {
        if (condition == null) {
            return baseMapper.selectCount(null);
        }
        P poCondition = toPo(condition);
        QueryWrapper<P> queryWrapper = buildQueryWrapper(poCondition);
        return baseMapper.selectCount(queryWrapper);
    }

    @Override
    public boolean exists(E condition) {
        return count(condition) > 0;
    }

    private QueryWrapper<P> buildQueryWrapper(P condition) {
        QueryWrapper<P> queryWrapper = new QueryWrapper<>();
        try {
            Field[] fields = getAllFields(condition.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(condition);
                if (value != null) {
                    queryWrapper.eq(camelToUnderline(field.getName()), value);
                }
            }
        } catch (Exception e) {
            log.warn("Error building query wrapper: {}", e.getMessage());
        }
        return queryWrapper;
    }

    private Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new java.util.ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }

    @SuppressWarnings("unchecked")
    private ID getIdValue(P po) {
        try {
            Field field = findIdField(po.getClass());
            if (field != null) {
                field.setAccessible(true);
                return (ID) field.get(po);
            }
        } catch (Exception e) {
            log.warn("Error getting id value: {}", e.getMessage());
        }
        return null;
    }

    private Field findIdField(Class<?> clazz) {
        try {
            Field field = clazz.getDeclaredField(getIdFieldName());
            return field;
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
                return findIdField(clazz.getSuperclass());
            }
            return null;
        }
    }

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

    protected E toEntity(P po) {
        if (po == null) {
            return null;
        }
        try {
            E entity = getEntityClass().getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(po, entity);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert PO to entity", e);
        }
    }

    protected List<E> toEntityList(List<P> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    protected P toPo(E entity) {
        if (entity == null) {
            return null;
        }
        try {
            P po = ((Class<P>) getPoClass()).getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(entity, po);
            return po;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert entity to PO", e);
        }
    }

    protected List<P> toPoList(List<E> entityList) {
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList.stream()
                .map(this::toPo)
                .collect(Collectors.toList());
    }
}