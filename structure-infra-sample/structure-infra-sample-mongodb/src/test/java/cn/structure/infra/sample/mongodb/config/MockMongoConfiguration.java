package cn.structure.infra.sample.mongodb.config;

import cn.structure.infra.sample.infra.po.UserPO;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class MockMongoConfiguration {

    private final Map<Long, UserPO> dataStore = new HashMap<>();
    private long idGenerator = 1;

    @Bean
    @Primary
    public MongoTemplate mongoTemplate() {
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

        when(template.find(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            return filterByQuery(query);
        });

        when(template.remove(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            List<UserPO> toRemove = filterByQuery(query);
            toRemove.forEach(po -> dataStore.remove(po.getId()));
            return null;
        });

        when(template.count(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            return (long) filterByQuery(query).size();
        });

        return template;
    }

    private List<UserPO> filterByQuery(Query query) {
        List<UserPO> result = new ArrayList<>(dataStore.values());

        if (query != null && query.getQueryObject() != null) {
            org.bson.Document queryDoc = query.getQueryObject();
            if (queryDoc.containsKey("username")) {
                Object usernameValue = queryDoc.get("username");
                result = result.stream()
                        .filter(po -> usernameValue.equals(po.getUsername()))
                        .toList();
            }
            if (queryDoc.containsKey("age")) {
                Object ageValue = queryDoc.get("age");
                Integer ageInt = ageValue instanceof Integer ? (Integer) ageValue : Integer.parseInt(ageValue.toString());
                result = result.stream()
                        .filter(po -> ageInt.equals(po.getAge()))
                        .toList();
            }
            if (queryDoc.containsKey("email")) {
                Object emailValue = queryDoc.get("email");
                result = result.stream()
                        .filter(po -> emailValue.equals(po.getEmail()))
                        .toList();
            }
        }

        return result;
    }
}
