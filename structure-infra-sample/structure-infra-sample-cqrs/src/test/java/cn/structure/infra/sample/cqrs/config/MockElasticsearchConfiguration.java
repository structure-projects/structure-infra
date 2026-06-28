package cn.structure.infra.sample.cqrs.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.query.Query;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mock Elasticsearch 配置
 * <p>
 * 用于测试环境中模拟 Elasticsearch 操作，避免依赖真实的 Elasticsearch 服务。
 * <p>
 * 模拟功能：
 * <ul>
 *   <li>save：保存文档，自动生成 ID</li>
 *   <li>get：根据 ID 获取文档</li>
 *   <li>delete：根据 ID 删除文档</li>
 *   <li>search：搜索文档，支持分页</li>
 *   <li>count：统计文档数量</li>
 * </ul>
 * <p>
 * 数据存储在内存中的 Map 结构，支持按 PO 类型分类存储。
 *
 * @author chuck
 * @version 1.0.1
 * @since 2026/6/28
 */
@TestConfiguration
public class MockElasticsearchConfiguration {

    /**
     * 内存数据存储，按 PO 类型分类
     */
    private final Map<Class<?>, Map<Long, Object>> dataStore = new HashMap<>();

    /**
     * ID 生成器
     */
    private long idGenerator = 1;

    /**
     * 重置数据存储
     * <p>
     * 用于测试方法间清理数据
     */
    public void reset() {
        dataStore.clear();
        idGenerator = 1;
    }

    /**
     * 创建 Mock 的 ElasticsearchTemplate
     * <p>
     * 模拟 Elasticsearch 的基本操作：
     * <ul>
     *   <li>保存文档（自动生成 ID）</li>
     *   <li>根据 ID 获取文档</li>
     *   <li>根据 ID 删除文档</li>
     *   <li>搜索文档（支持分页）</li>
     *   <li>统计文档数量</li>
     * </ul>
     *
     * @return Mock 的 ElasticsearchTemplate 实例
     */
    @Bean
    @Primary
    public ElasticsearchTemplate elasticsearchTemplate() {
        ElasticsearchTemplate template = mock(ElasticsearchTemplate.class);

        // 模拟 save 操作
        when(template.save(any(Object.class))).thenAnswer(invocation -> {
            Object po = invocation.getArgument(0);
            Class<?> poClass = po.getClass();

            Map<Long, Object> classStore = dataStore.computeIfAbsent(poClass, k -> new HashMap<>());

            try {
                Field idField = findIdField(poClass);
                if (idField != null) {
                    idField.setAccessible(true);
                    Object idValue = idField.get(po);
                    Long id = null;

                    if (idValue == null || (idValue instanceof Number && ((Number) idValue).longValue() == 0)) {
                        id = idGenerator++;
                        setIdValue(po, id);
                    } else if (idValue instanceof Long) {
                        id = (Long) idValue;
                    } else if (idValue instanceof String) {
                        id = Long.valueOf((String) idValue);
                    } else if (idValue instanceof Number) {
                        id = ((Number) idValue).longValue();
                    }

                    if (id != null) {
                        classStore.put(id, po);
                    }
                }
            } catch (Exception ignored) {
            }

            return po;
        });

        // 模拟 get 操作
        when(template.get(anyString(), any(Class.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);
            Map<Long, Object> classStore = dataStore.get(poClass);
            return classStore != null ? classStore.get(Long.valueOf(id)) : null;
        });

        // 模拟 delete 操作
        when(template.delete(anyString(), any(Class.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);
            Map<Long, Object> classStore = dataStore.get(poClass);
            if (classStore != null) {
                classStore.remove(Long.valueOf(id));
            }
            return id;
        });

        // 模拟 search 操作
        when(template.search(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);

            List<Object> allData = getAllData(poClass);

            Pageable pageable = query.getPageable();
            List<Object> pageData = new ArrayList<>(allData);
            if (pageable != null && pageable.isPaged()) {
                int pageNum = pageable.getPageNumber();
                int pageSize = pageable.getPageSize();
                int fromIndex = pageNum * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, allData.size());
                if (fromIndex >= allData.size()) {
                    pageData = new ArrayList<>();
                } else {
                    pageData = new ArrayList<>(allData.subList(fromIndex, toIndex));
                }
            }

            List<SearchHit<Object>> searchHits = new ArrayList<>();
            for (Object po : pageData) {
                SearchHit<Object> hit = mock(SearchHit.class);
                try {
                    Field idField = findIdField(poClass);
                    if (idField != null) {
                        idField.setAccessible(true);
                        Object idValue = idField.get(po);
                        when(hit.getId()).thenReturn(String.valueOf(idValue));
                    }
                } catch (Exception ignored) {
                }
                when(hit.getContent()).thenReturn(po);
                searchHits.add(hit);
            }

            return new SearchHitsImpl<>(
                    allData.size(),
                    TotalHitsRelation.EQUAL_TO,
                    0.0f,
                    Duration.ZERO,
                    null,
                    null,
                    searchHits,
                    null,
                    null,
                    null
            );
        });

        // 模拟 count 操作
        when(template.count(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Class<?> poClass = invocation.getArgument(1);
            Map<Long, Object> classStore = dataStore.get(poClass);
            return classStore != null ? (long) classStore.size() : 0L;
        });

        return template;
    }

    /**
     * 获取指定 PO 类的所有数据
     *
     * @param poClass PO 类
     * @return 数据列表
     */
    private List<Object> getAllData(Class<?> poClass) {
        Map<Long, Object> classStore = dataStore.get(poClass);
        return classStore != null ? new ArrayList<>(classStore.values()) : new ArrayList<>();
    }

    /**
     * 查找 ID 字段
     *
     * @param clazz 类
     * @return ID 字段
     */
    private Field findIdField(Class<?> clazz) {
        return findField(clazz, "id");
    }

    /**
     * 递归查找字段
     *
     * @param clazz     类
     * @param fieldName 字段名
     * @return 字段
     */
    private Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }

    /**
     * 设置 ID 值
     *
     * @param po  PO 对象
     * @param id  ID 值
     * @throws Exception 异常
     */
    private void setIdValue(Object po, Long id) throws Exception {
        Field field = findIdField(po.getClass());
        if (field != null) {
            field.setAccessible(true);
            if (field.getType() == Long.class || field.getType() == long.class) {
                field.set(po, id);
            } else if (field.getType() == Integer.class || field.getType() == int.class) {
                field.set(po, id.intValue());
            } else if (field.getType() == String.class) {
                field.set(po, String.valueOf(id));
            } else {
                field.set(po, id);
            }
        }
    }
}