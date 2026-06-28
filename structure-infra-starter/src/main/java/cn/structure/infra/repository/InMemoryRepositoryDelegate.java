package cn.structure.infra.repository;

import cn.structure.common.vo.ReqPage;
import cn.structure.common.vo.ResPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内存版仓储委托实现
 * <p>
 * 基于 ConcurrentHashMap 实现的内存仓储，用于示例、测试和开发阶段
 * 支持基本的 CRUD、条件查询、分页查询
 *
 * @param <T>  实体类型
 * @param <ID> 主键类型
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@Slf4j
public class InMemoryRepositoryDelegate<T, ID> implements RepositoryDelegate<T, ID> {

    private final Map<ID, T> storage = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final Class<T> entityClass;
    private final String idFieldName;

    public InMemoryRepositoryDelegate(Class<T> entityClass) {
        this(entityClass, "id");
    }

    public InMemoryRepositoryDelegate(Class<T> entityClass, String idFieldName) {
        this.entityClass = entityClass;
        this.idFieldName = idFieldName;
        log.info("InMemoryRepositoryDelegate initialized for entity: {}", entityClass.getSimpleName());
    }

    @Override
    public T save(T entity) {
        if (entity == null) {
            return null;
        }
        ID id = getIdValue(entity);
        if (id == null) {
            id = generateId();
            setIdValue(entity, id);
        }
        storage.put(id, entity);
        log.debug("Saved entity: id={}, entity={}", id, entity);
        return entity;
    }

    @Override
    public void removeById(ID id) {
        if (id != null) {
            T removed = storage.remove(id);
            log.debug("Removed entity: id={}, removed={}", id, removed != null);
        }
    }

    @Override
    public T findById(ID id) {
        if (id == null) {
            return null;
        }
        T entity = storage.get(id);
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
        List<T> results = queryList(condition);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Optional<T> queryOneOptional(T condition) {
        return Optional.ofNullable(queryOne(condition));
    }

    @Override
    public List<T> queryList(T condition) {
        if (condition == null) {
            return new ArrayList<>(storage.values());
        }
        return storage.values().stream()
                .filter(entity -> matchesCondition(entity, condition))
                .collect(Collectors.toList());
    }

    @Override
    public ResPage<T> queryPage(ReqPage reqPage) {
        ResPage<T> page = new ResPage<>();
        List<T> allValues = new ArrayList<>(storage.values());
        long total = allValues.size();
        int pageNum = reqPage.getPage() != null ? reqPage.getPage() : 1;
        int pageSize = reqPage.getSize() != null ? reqPage.getSize() : 10;

        long pages = total > 0 ? (total + pageSize - 1) / pageSize : 0;
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allValues.size());

        List<T> records = (fromIndex >= allValues.size())
                ? List.of()
                : allValues.subList(fromIndex, toIndex);

        page.setCurrent((long) pageNum);
        page.setPages(pages);
        page.setSize((long) pageSize);
        page.setTotal(total);
        page.setRecords(records);

        log.debug("Query page: page={}, size={}, total={}, records={}",
                pageNum, pageSize, total, records.size());
        return page;
    }

    /**
     * 检查实体是否匹配条件
     * <p>
     * 通过反射比较非空字段的值
     */
    private boolean matchesCondition(T entity, T condition) {
        try {
            Field[] fields = getAllFields(entity.getClass());
            for (Field field : fields) {
                field.setAccessible(true);
                Object conditionValue = field.get(condition);
                if (conditionValue != null) {
                    Object entityValue = field.get(entity);
                    if (!conditionValue.equals(entityValue)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Error matching condition: {}", e.getMessage());
            return false;
        }
    }

    private Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
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

    private void setIdValue(T entity, ID id) {
        try {
            Field field = findIdField(entity.getClass());
            if (field != null) {
                field.setAccessible(true);
                if (field.getType() == Long.class || field.getType() == long.class) {
                    field.set(entity, id);
                } else if (field.getType() == Integer.class || field.getType() == int.class) {
                    field.set(entity, ((Number) id).intValue());
                } else if (field.getType() == String.class) {
                    field.set(entity, String.valueOf(id));
                } else {
                    field.set(entity, id);
                }
            }
        } catch (Exception e) {
            log.warn("Error setting id value: {}", e.getMessage());
        }
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

    @SuppressWarnings("unchecked")
    private ID generateId() {
        return (ID) Long.valueOf(idGenerator.getAndIncrement());
    }

    /**
     * 获取存储大小（用于测试）
     */
    public int size() {
        return storage.size();
    }

    /**
     * 清空存储（用于测试）
     */
    public void clear() {
        storage.clear();
        idGenerator.set(1);
    }

    @Override
    public List<T> saveBatch(List<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(this::save)
                .collect(Collectors.toList());
    }

    @Override
    public void removeBatchByIds(List<ID> ids) {
        if (ids != null) {
            ids.forEach(this::removeById);
        }
    }

    @Override
    public List<T> listByIds(List<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(this::findById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public long count(T condition) {
        if (condition == null) {
            return storage.size();
        }
        return storage.values().stream()
                .filter(entity -> matchesCondition(entity, condition))
                .count();
    }

    @Override
    public boolean exists(T condition) {
        return count(condition) > 0;
    }
}
