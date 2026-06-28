package cn.structure.infra.elasticsearch.repository;

import cn.structure.infra.annotations.DelegateFor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

@Slf4j
public class ElasticsearchDelegateBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ElasticsearchRepositoryDelegate) {
            ElasticsearchRepositoryDelegate delegate = (ElasticsearchRepositoryDelegate) bean;
            try {
                ElasticsearchOperations elasticsearchOperations = applicationContext.getBean(ElasticsearchOperations.class);
                delegate.setElasticsearchOperations(elasticsearchOperations);
                
                DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
                if (annotation != null && annotation.po() != void.class) {
                    delegate.setEntityClass(annotation.po());
                }
                
                log.info("Injected ElasticsearchOperations into ElasticsearchRepositoryDelegate: {}", beanName);
            } catch (Exception e) {
                log.warn("Failed to inject ElasticsearchOperations into ElasticsearchRepositoryDelegate {}: {}", beanName, e.getMessage());
            }
        }
        return bean;
    }
}