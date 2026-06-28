package cn.structure.infra.jpa.repository;

import cn.structure.infra.annotations.DelegateFor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof JpaRepositoryDelegate) {
            JpaRepositoryDelegate delegate = (JpaRepositoryDelegate) bean;
            
            EntityManager entityManager = getEntityManager();
            if (entityManager != null) {
                delegate.setEntityManager(entityManager);
                log.info("Injected EntityManager into JpaRepositoryDelegate: {}", beanName);
            } else {
                log.warn("No EntityManager available to inject into JpaRepositoryDelegate: {}", beanName);
            }

            DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
            if (annotation != null && annotation.po() != void.class) {
                delegate.setEntityClass(annotation.po());
                log.info("Injected entityClass {} into JpaRepositoryDelegate: {}", annotation.po().getSimpleName(), beanName);
            }
        }
        return bean;
    }

    private EntityManager getEntityManager() {
        try {
            Object bean = applicationContext.getBean("entityManager");
            if (bean instanceof EntityManager) {
                return (EntityManager) bean;
            }
        } catch (Exception e) {
            log.debug("entityManager bean not found by name");
        }

        try {
            EntityManagerFactory factory = applicationContext.getBean(EntityManagerFactory.class);
            if (factory != null) {
                return factory.createEntityManager();
            }
        } catch (Exception e) {
            log.debug("EntityManagerFactory bean not found");
        }

        return null;
    }
}