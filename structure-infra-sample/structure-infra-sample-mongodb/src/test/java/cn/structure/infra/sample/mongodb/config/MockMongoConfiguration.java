package cn.structure.infra.sample.mongodb.config;

import org.bson.Document;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@TestConfiguration
public class MockMongoConfiguration {

    private final Map<Class<?>, Map<Long, Object>> dataStore = new HashMap<>();
    private long idGenerator = 1;

    private final Map<String, Map<Object, Document>> docCollections = new ConcurrentHashMap<>();
    private final AtomicLong docIdGenerator = new AtomicLong(1);
    private final List<String> insertCalls = new ArrayList<>();

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
        docCollections.clear();
        docIdGenerator.set(1);
        insertCalls.clear();
    }

    public List<String> getInsertCalls() {
        return insertCalls;
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

        // ---------- Document 版本 API（低代码仓储使用） ----------

        // collectionExists
        when(template.collectionExists(anyString())).thenAnswer(invocation -> {
            String collectionName = invocation.getArgument(0);
            return docCollections.containsKey(collectionName);
        });

        // createCollection
        doAnswer(invocation -> {
            String collectionName = invocation.getArgument(0);
            docCollections.putIfAbsent(collectionName, new LinkedHashMap<>());
            return null;
        }).when(template).createCollection(anyString());

        // dropCollection
        doAnswer(invocation -> {
            String collectionName = invocation.getArgument(0);
            docCollections.remove(collectionName);
            return null;
        }).when(template).dropCollection(anyString());

        // indexOps
        IndexOperations indexOps = mock(IndexOperations.class);
        when(indexOps.ensureIndex(any(Index.class))).thenReturn("");
        when(template.indexOps(anyString())).thenReturn(indexOps);

        // findOne with collectionName
        when(template.findOne(any(Query.class), any(Class.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(2);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            List<Document> allDocs = new ArrayList<>(collection.values());
            List<Document> filtered = filterDocsByQuery(query, allDocs);
            return filtered.isEmpty() ? null : filtered.get(0);
        });

        // find with collectionName
        when(template.find(any(Query.class), any(Class.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(2);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return new ArrayList<Document>();
            }
            List<Document> allDocs = new ArrayList<>(collection.values());
            return filterDocsByQuery(query, allDocs);
        });

        // insert with collectionName
        when(template.insert(any(Document.class), anyString())).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            insertCalls.add("insert(Document, String): " + collectionName);
            Map<Object, Document> collection = docCollections.computeIfAbsent(collectionName, k -> new LinkedHashMap<>());

            String idField = "id";
            if (!doc.containsKey(idField) || doc.get(idField) == null) {
                doc.put(idField, docIdGenerator.getAndIncrement());
            }
            Object id = doc.get(idField);
            collection.put(id, doc);
            return doc;
        });

        // save with collectionName
        when(template.save(any(Document.class), anyString())).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, Document> collection = docCollections.computeIfAbsent(collectionName, k -> new LinkedHashMap<>());

            String idField = "id";
            if (!doc.containsKey(idField) || doc.get(idField) == null) {
                doc.put(idField, docIdGenerator.getAndIncrement());
            }
            Object id = doc.get(idField);
            collection.put(id, doc);
            return doc;
        });

        // updateFirst with collectionName
        when(template.updateFirst(any(Query.class), any(Update.class), anyString())).thenAnswer(invocation -> {
            String collectionName = invocation.getArgument(2);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            Document firstDoc = collection.values().iterator().next();
            Update update = invocation.getArgument(1);
            Map<String, Object> updates = extractDocUpdateValues(update);
            firstDoc.putAll(updates);
            return null;
        });

        // remove with query and collectionName
        doAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return null;
            }
            List<Document> allDocs = new ArrayList<>(collection.values());
            List<Document> filtered = filterDocsByQuery(query, allDocs);
            for (Document doc : filtered) {
                Object id = doc.get("id");
                if (id != null) {
                    collection.remove(id);
                }
            }
            return null;
        }).when(template).remove(any(Query.class), anyString());

        // count with collectionName
        when(template.count(any(Query.class), anyString())).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            String collectionName = invocation.getArgument(1);
            Map<Object, Document> collection = docCollections.get(collectionName);
            if (collection == null || collection.isEmpty()) {
                return 0L;
            }
            List<Document> allDocs = new ArrayList<>(collection.values());
            List<Document> filtered = filterDocsByQuery(query, allDocs);
            return (long) filtered.size();
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

    /**
     * 提取 Update 对象中的更新值（Document 版本）
     */
    private Map<String, Object> extractDocUpdateValues(Update update) {
        Map<String, Object> result = new HashMap<>();
        try {
            Field updatesField = Update.class.getDeclaredField("updates");
            updatesField.setAccessible(true);
            Object updates = updatesField.get(update);
            if (updates instanceof List) {
                for (Object u : (List<?>) updates) {
                    try {
                        Field keyField = u.getClass().getDeclaredField("key");
                        Field valueField = u.getClass().getDeclaredField("value");
                        keyField.setAccessible(true);
                        valueField.setAccessible(true);
                        String key = (String) keyField.get(u);
                        Object value = valueField.get(u);
                        result.put(key, value);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /**
     * 根据 Query 条件过滤 Document 列表
     */
    private List<Document> filterDocsByQuery(Query query, List<Document> docs) {
        try {
            Field criteriaField = Query.class.getDeclaredField("criteria");
            criteriaField.setAccessible(true);
            Object criteriaObj = criteriaField.get(query);

            if (criteriaObj instanceof Criteria criteria) {
                List<Map.Entry<String, Object>> conditions = extractDocConditions(criteria);
                return docs.stream()
                        .filter(doc -> matchesDocConditions(doc, conditions))
                        .collect(Collectors.toList());
            }
        } catch (Exception ignored) {
        }
        return docs;
    }

    /**
     * 从 Criteria 中提取查询条件
     */
    private List<Map.Entry<String, Object>> extractDocConditions(Criteria criteria) {
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

    /**
     * 判断 Document 是否匹配查询条件
     */
    private boolean matchesDocConditions(Document doc, List<Map.Entry<String, Object>> conditions) {
        for (Map.Entry<String, Object> condition : conditions) {
            String fieldName = condition.getKey();
            Object expectedValue = condition.getValue();

            Object actualValue = doc.get(fieldName);
            if (actualValue == null || !actualValue.equals(expectedValue)) {
                return false;
            }
        }
        return true;
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
