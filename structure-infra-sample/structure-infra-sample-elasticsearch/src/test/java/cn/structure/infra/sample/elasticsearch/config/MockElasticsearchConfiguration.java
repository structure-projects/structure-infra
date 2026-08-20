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

package cn.structure.infra.sample.elasticsearch.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
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

@TestConfiguration
public class MockElasticsearchConfiguration {

    private final Map<Class<?>, Map<Long, Object>> dataStore = new HashMap<>();
    private long idGenerator = 1;

    public void reset() {
        dataStore.clear();
        idGenerator = 1;
    }

    @Bean
    @Primary
    public ElasticsearchTemplate elasticsearchTemplate() {
        ElasticsearchTemplate template = mock(ElasticsearchTemplate.class);

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

        when(template.get(anyString(), any(Class.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);
            Map<Long, Object> classStore = dataStore.get(poClass);
            return classStore != null ? classStore.get(Long.valueOf(id)) : null;
        });

        when(template.delete(anyString(), any(Class.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);
            Map<Long, Object> classStore = dataStore.get(poClass);
            if (classStore != null) {
                classStore.remove(Long.valueOf(id));
            }
            return id;
        });

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

        when(template.count(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Class<?> poClass = invocation.getArgument(1);
            Map<Long, Object> classStore = dataStore.get(poClass);
            return classStore != null ? (long) classStore.size() : 0L;
        });

        return template;
    }

    private List<Object> getAllData(Class<?> poClass) {
        Map<Long, Object> classStore = dataStore.get(poClass);
        return classStore != null ? new ArrayList<>(classStore.values()) : new ArrayList<>();
    }

    private Field findIdField(Class<?> clazz) {
        return findField(clazz, "id");
    }

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
