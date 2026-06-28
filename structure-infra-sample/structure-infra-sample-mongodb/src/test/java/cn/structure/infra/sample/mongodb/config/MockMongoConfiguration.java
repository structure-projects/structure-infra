package cn.structure.infra.sample.mongodb.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class MockMongoConfiguration {

    private final Map<Class<?>, Map<Long, Object>> dataStore = new HashMap<>();
    private long idGenerator = 1;

    private final MongoMappingContext mappingContext = new MongoMappingContext();
    private final MongoConverter converter;
    private final MongoTemplate template;

    public MockMongoConfiguration() {
        this.converter = createMongoConverter();
        this.template = createMongoTemplate();
    }

    public void reset() {
        dataStore.clear();
        idGenerator = 1;
    }

    private MongoConverter createMongoConverter() {
        MappingMongoConverter converter = mock(MappingMongoConverter.class);
        when(converter.getMappingContext()).thenReturn((MappingContext) mappingContext);
        return converter;
    }

    private MongoTemplate createMongoTemplate() {
        MongoTemplate template = mock(MongoTemplate.class);

        when(template.save(any())).thenAnswer(invocation -> {
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

        when(template.findById(any(Long.class), any(Class.class))).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);
            Map<Long, Object> classStore = dataStore.get(poClass);
            return classStore != null ? classStore.get(id) : null;
        });

        when(template.findById(anyString(), any(Class.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);
            Map<Long, Object> classStore = dataStore.get(poClass);
            return classStore != null ? classStore.get(Long.valueOf(id)) : null;
        });

        when(template.findOne(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);
            List<Object> allData = getAllData(poClass);
            List<Object> results = filterByQuery(query, allData);
            return results.isEmpty() ? null : results.get(0);
        });

        when(template.findAll(any(Class.class))).thenAnswer(invocation -> {
            Class<?> poClass = invocation.getArgument(0);
            return new ArrayList<>(getAllData(poClass));
        });

        when(template.find(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);
            List<Object> allData = getAllData(poClass);
            return filterByQuery(query, allData);
        });

        when(template.remove(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            Class<?> poClass = invocation.getArgument(1);
            List<Object> allData = getAllData(poClass);
            List<Object> results = filterByQuery(query, allData);
            
            Map<Long, Object> classStore = dataStore.get(poClass);
            if (classStore != null) {
                for (Object po : results) {
                    try {
                        Field idField = findIdField(poClass);
                        if (idField != null) {
                            idField.setAccessible(true);
                            Object idValue = idField.get(po);
                            Long id = null;
                            if (idValue instanceof Long) {
                                id = (Long) idValue;
                            } else if (idValue instanceof String) {
                                id = Long.valueOf((String) idValue);
                            } else if (idValue instanceof Number) {
                                id = ((Number) idValue).longValue();
                            }
                            if (id != null) {
                                classStore.remove(id);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            return null;
        });

        when(template.count(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Class<?> poClass = invocation.getArgument(1);
            Map<Long, Object> classStore = dataStore.get(poClass);
            return classStore != null ? (long) classStore.size() : 0L;
        });

        when(template.getConverter()).thenReturn(converter);

        return template;
    }

    private List<Object> getAllData(Class<?> poClass) {
        Map<Long, Object> classStore = dataStore.get(poClass);
        return classStore != null ? new ArrayList<>(classStore.values()) : new ArrayList<>();
    }

    private List<Object> filterByQuery(Query query, List<Object> data) {
        try {
            Field criteriaField = Query.class.getDeclaredField("criteria");
            criteriaField.setAccessible(true);
            Object criteriaObj = criteriaField.get(query);

            if (criteriaObj instanceof Criteria criteria) {
                List<Map.Entry<String, Object>> conditions = extractConditions(criteria);
                return data.stream()
                        .filter(po -> matchesConditions(po, conditions))
                        .collect(Collectors.toList());
            }
        } catch (Exception ignored) {
        }
        return data;
    }

    private List<Map.Entry<String, Object>> extractConditions(Criteria criteria) {
        List<Map.Entry<String, Object>> conditions = new ArrayList<>();
        try {
            Field keyField = Criteria.class.getDeclaredField("key");
            Field valueField = Criteria.class.getDeclaredField("value");
            keyField.setAccessible(true);
            valueField.setAccessible(true);

            Object key = keyField.get(criteria);
            Object value = valueField.get(criteria);

            if (key != null && value != null) {
                conditions.add(Map.entry(key.toString(), value));
            }
        } catch (Exception ignored) {
        }
        return conditions;
    }

    private boolean matchesConditions(Object po, List<Map.Entry<String, Object>> conditions) {
        for (Map.Entry<String, Object> condition : conditions) {
            String fieldName = condition.getKey();
            Object expectedValue = condition.getValue();

            try {
                Field field = findField(po.getClass(), fieldName);
                if (field != null) {
                    field.setAccessible(true);
                    Object actualValue = field.get(po);

                    if (!expectedValue.equals(actualValue)) {
                        return false;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return true;
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

    private Field findIdField(Class<?> clazz) {
        return findField(clazz, "id");
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

    @Bean
    @Primary
    public MongoDatabaseFactory mongoDatabaseFactory() {
        return mock(MongoDatabaseFactory.class);
    }

    @Bean
    @Primary
    public MongoMappingContext mongoMappingContext() {
        return mappingContext;
    }

    @Bean
    @Primary
    public MongoConverter mongoConverter() {
        return converter;
    }

    @Bean
    @Primary
    public MongoTemplate mongoTemplate() {
        return template;
    }
}
