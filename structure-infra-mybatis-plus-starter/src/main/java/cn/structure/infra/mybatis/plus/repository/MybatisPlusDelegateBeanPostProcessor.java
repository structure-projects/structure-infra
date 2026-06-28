package cn.structure.infra.mybatis.plus.repository;

import cn.structure.infra.annotations.DelegateFor;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@Slf4j
public class MybatisPlusDelegateBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof MybatisPlusRepositoryDelegate) {
            MybatisPlusRepositoryDelegate delegate = (MybatisPlusRepositoryDelegate) bean;
            DelegateFor annotation = bean.getClass().getAnnotation(DelegateFor.class);
            if (annotation != null && annotation.po() != void.class) {
                try {
                    Object mapper = findMapperByPoClass(annotation.po());
                    if (mapper != null) {
                        delegate.setBaseMapper((BaseMapper) mapper);
                        delegate.setEntityClass(annotation.po());
                        log.info("Injected BaseMapper into MybatisPlusRepositoryDelegate: {}", beanName);
                    } else {
                        log.warn("No BaseMapper found for PO class {} in MybatisPlusRepositoryDelegate {}", annotation.po().getSimpleName(), beanName);
                    }
                } catch (Exception e) {
                    log.warn("Failed to inject BaseMapper into MybatisPlusRepositoryDelegate {}: {}", beanName, e.getMessage());
                }
            }
        }
        return bean;
    }

    private Object findMapperByPoClass(Class<?> poClass) {
        String poClassName = poClass.getName();
        String mapperClassName = poClassName.replace(".po.", ".mapper.")
                .replace("PO", "Mapper");
        try {
            Class<?> mapperClass = Class.forName(mapperClassName);
            return applicationContext.getBean(mapperClass);
        } catch (ClassNotFoundException e) {
            log.debug("Mapper class not found: {}", mapperClassName);
        } catch (Exception e) {
            log.debug("Failed to get mapper bean: {}", e.getMessage());
        }
        
        String simpleMapperName = poClass.getSimpleName().replace("PO", "Mapper");
        try {
            return applicationContext.getBean(simpleMapperName);
        } catch (Exception e) {
            log.debug("Failed to get mapper by name: {}", simpleMapperName);
        }
        
        return null;
    }
}