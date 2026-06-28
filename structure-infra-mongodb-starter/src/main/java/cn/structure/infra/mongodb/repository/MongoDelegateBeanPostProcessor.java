package cn.structure.infra.mongodb.repository;

import cn.structure.infra.annotations.DelegateFor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
public class MongoDelegateBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MongoRepositoryDelegate) {
            MongoRepositoryDelegate delegate = (MongoRepositoryDelegate) bean;
            try {
                MongoTemplate mongoTemplate = applicationContext.getBean(MongoTemplate.class);
                delegate.setMongoTemplate(mongoTemplate);
                
                DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
                if (annotation != null && annotation.po() != void.class) {
                    delegate.setEntityClass(annotation.po());
                }
                
                log.info("Injected MongoTemplate into MongoRepositoryDelegate: {}", beanName);
            } catch (Exception e) {
                log.warn("Failed to inject MongoTemplate into MongoRepositoryDelegate {}: {}", beanName, e.getMessage());
            }
        }
        return bean;
    }
}