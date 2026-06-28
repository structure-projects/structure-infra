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

@Slf4j
public class MybatisPlusRepositoryDelegate<T, ID> implements RepositoryDelegate<T, ID> {

    protected BaseMapper<T> baseMapper;
    protected Class<T> entityClass;
    protected String idFieldName;

    public MybatisPlusRepositoryDelegate() {
    }

    public MybatisPlusRepositoryDelegate(BaseMapper<T> baseMapper, Class<T> entityClass) {
        this(baseMapper, entityClass, "id");
    }

    public MybatisPlusRepositoryDelegate(BaseMapper<T> baseMapper, Class<T> entityClass, String idFieldName) {
        this.baseMapper = baseMapper;
        this.entityClass = entityClass;
        this.idFieldName = idFieldName;
        log.info("MybatisPlusRepositoryDelegate initialized for entity: {}", entityClass.getSimpleName());
    }

    public void setBaseMapper(BaseMapper<T> baseMapper) {
        this.baseMapper = baseMapper;
    }

    public void setEntityClass(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    public void setIdFieldName(String idFieldName) {
        this.idFieldName = idFieldName;
    }

    @Override
    public T save(T entity) {
        if (entity == null) {
            return null;
        }
        ID id = getIdValue(entity);
        if (id == null) {
            baseMapper.insert(entity);
        } else {
            baseMapper.updateById(entity);
        }
        log.debug("Saved entity: id={}, entity={}", id, entity);
        return entity;
    }

    @Override
    public void removeById(ID id) {
        if (id != null) {
            baseMapper.deleteById((Serializable) id);
            log.debug("Removed entity: id={}", id);
        }
    }

    @Override
    public T findById(ID id) {
        if (id == null) {
            return null;
        }
        T entity = baseMapper.selectById((Serializable) id);
        log.debug("Find by id: id={}, found={}", id, entity != null);
        return entity;
    }

    @Override
    public T queryById(ID id) {
        return findById(id);
    }

    @Override
    public Optional<T> queryByIdOptional(ID id) {
        return Optional.ofNullable(queryById(id));
    }

    @Override
    public T queryOne(T condition) {
        if (condition == null) {
            return null;
        }
        QueryWrapper<T> queryWrapper = buildQueryWrapper(condition);
        List<T> results = baseMapper.selectList(queryWrapper);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Optional<T> queryOneOptional(T condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<T> queryList(T condition) {
        if (condition == null) {
            return baseMapper.selectList(null);
        }
        QueryWrapper<T> queryWrapper = buildQueryWrapper(condition);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        long pageNum = reqPage.getPage() != null ? reqPage.getPage() : 1;
        long pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        Page<T> page = new Page<>(pageNum, pageSize);
        IPage<T> result = baseMapper.selectPage(page, null);

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

    private QueryWrapper<T> buildQueryWrapper(T condition) {
        QueryWrapper<T> queryWrapper = new QueryWrapper<>();
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

    private Field findIdField(Class<?> clazz) {
        try {
            Field field = clazz.getDeclaredField(idFieldName);
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

    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        entities.forEach(baseMapper::insert);
        return entities;
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
    public List<T> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return baseMapper.selectBatchIds(ids.stream()
                .map(id -> (Serializable) id)
                .toList());
    }

    @Override
    public long count(T condition) {
        if (condition == null) {
            return baseMapper.selectCount(null);
        }
        QueryWrapper<T> queryWrapper = buildQueryWrapper(condition);
        return baseMapper.selectCount(queryWrapper);
    }

    @Override
    public boolean exists(T condition) {
        return count(condition) > 0;
    }
}
