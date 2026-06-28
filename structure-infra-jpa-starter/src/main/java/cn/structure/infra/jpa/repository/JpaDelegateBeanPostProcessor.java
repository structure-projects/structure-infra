package cn.structure.infra.jpa.repository;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@Slf4j
public class JpaDelegateBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof JpaRepositoryDelegate) {
            JpaRepositoryDelegate<?, ?> delegate = (JpaRepositoryDelegate<?, ?>) bean;
            try {
                EntityManager entityManager = applicationContext.getBean(EntityManager.class);
                delegate.setEntityManager(entityManager);
                log.info("Injected EntityManager into JpaRepositoryDelegate: {}", beanName);
            } catch (Exception e) {
                log.warn("Failed to inject EntityManager into JpaRepositoryDelegate {}: {}", beanName, e.getMessage());
            }
        }
        return bean;
    }
}