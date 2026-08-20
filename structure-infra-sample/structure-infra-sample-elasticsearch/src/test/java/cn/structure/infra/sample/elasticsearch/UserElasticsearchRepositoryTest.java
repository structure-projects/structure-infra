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

package cn.structure.infra.sample.elasticsearch;

import cn.structure.infra.sample.domain.repository.UserRepository;
import cn.structure.infra.sample.elasticsearch.config.ElasticsearchTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = ElasticsearchTestConfig.class)
@ActiveProfiles("es-test")
@DisplayName("Elasticsearch 仓储测试")
class UserElasticsearchRepositoryTest {

    @Autowired(required = false)
    private UserRepository userRepository;

    @Test
    @DisplayName("测试 Elasticsearch 仓储注入")
    void testElasticsearchRepositoryInjection() {
        assertNotNull(userRepository, "Elasticsearch 仓储应该被成功注入");
        System.out.println("✓ Elasticsearch 仓储注入成功: " + userRepository.getClass().getName());
    }

    @Test
    @DisplayName("测试 Elasticsearch Delegate 类型")
    void testElasticsearchDelegateType() {
        assertNotNull(userRepository, "Elasticsearch 仓储应该被注入");
        System.out.println("✓ Elasticsearch 仓储实现类: " + userRepository.getClass().getName());
    }
}
