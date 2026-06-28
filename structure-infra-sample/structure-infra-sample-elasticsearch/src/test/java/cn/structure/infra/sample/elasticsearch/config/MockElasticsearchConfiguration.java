package cn.structure.infra.sample.elasticsearch.config;

import cn.structure.infra.sample.infra.po.UserPO;
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

    private final Map<Long, UserPO> dataStore = new HashMap<>();
    private long idGenerator = 1;

    @Bean
    @Primary
    public ElasticsearchTemplate elasticsearchTemplate() {
        ElasticsearchTemplate template = mock(ElasticsearchTemplate.class);

        when(template.save(any(UserPO.class))).thenAnswer(invocation -> {
            UserPO po = invocation.getArgument(0);
            if (po.getId() == null || po.getId() == 0) {
                po.setId(idGenerator++);
            }
            dataStore.put(po.getId(), po);
            return po;
        });

        when(template.get(anyString(), any(Class.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return dataStore.get(Long.valueOf(id));
        });

        when(template.delete(anyString(), any(Class.class))).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            dataStore.remove(Long.valueOf(id));
            return id;
        });

        when(template.search(any(Query.class), any(Class.class))).thenAnswer(invocation -> {
            Query query = invocation.getArgument(0);
            List<UserPO> allData = new ArrayList<>(dataStore.values());

            Pageable pageable = query.getPageable();
            List<UserPO> pageData = new ArrayList<>(allData);
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

            List<SearchHit<UserPO>> searchHits = new ArrayList<>();
            for (UserPO po : pageData) {
                SearchHit<UserPO> hit = mock(SearchHit.class);
                when(hit.getId()).thenReturn(String.valueOf(po.getId()));
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
            return (long) dataStore.size();
        });

        return template;
    }
}
