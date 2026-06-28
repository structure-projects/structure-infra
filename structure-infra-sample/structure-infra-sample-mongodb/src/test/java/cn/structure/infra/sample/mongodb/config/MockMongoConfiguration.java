package cn.structure.infra.sample.mongodb.config;

import cn.structure.infra.sample.infra.po.UserPO;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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

    private final Map<Long, UserPO> dataStore = new HashMap<>();
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
        when(converter.getMappingContext()).thenReturn((org.springframework.data.mapping.context.MappingContext) mappingContext);
        return converter;
    }

    private MongoTemplate createMongoTemplate() {
        MongoTemplate template = mock(MongoTemplate.class);

        when(template.save(any(UserPO.class))).thenAnswer(invocation -> {
            UserPO po = invocation.getArgument(0);
            if (po.getId() == null || po.getId() == 0) {
                po.setId(idGenerator++);
            }
            dataStore.put(po.getId(), po);
            return po;
        });

        when(template.findById(any(Long.class), any(Class.class))).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return dataStore.get(id);
        });

        when(template.findById(anyString(), any(Class.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return dataStore.get(Long.valueOf(id));
        });

        when(template.findOne(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            List<UserPO> results = filterByQuery(query, new ArrayList<>(dataStore.values()));
            return results.isEmpty() ? null : results.get(0);
        });

        when(template.findAll(any(Class.class))).thenAnswer(invocation -> {
            return new ArrayList<>(dataStore.values());
        });

        when(template.find(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            return filterByQuery(query, new ArrayList<>(dataStore.values()));
        });

        when(template.remove(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            List<UserPO> results = filterByQuery(query, new ArrayList<>(dataStore.values()));
            results.forEach(po -> dataStore.remove(po.getId()));
            return null;
        });

        when(template.count(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            return (long) dataStore.size();
        });

        when(template.getConverter()).thenReturn(converter);

        return template;
    }

    private List<UserPO> filterByQuery(Query query, List<UserPO> data) {
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

    private boolean matchesConditions(UserPO po, List<Map.Entry<String, Object>> conditions) {
        for (Map.Entry<String, Object> condition : conditions) {
            String fieldName = condition.getKey();
            Object expectedValue = condition.getValue();

            try {
                Field field = UserPO.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object actualValue = field.get(po);

                if (!expectedValue.equals(actualValue)) {
                    return false;
                }
            } catch (Exception ignored) {
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
