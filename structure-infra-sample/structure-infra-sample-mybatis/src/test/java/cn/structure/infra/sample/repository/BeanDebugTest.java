package cn.structure.infra.sample.repository;

import cn.structure.infra.sample.config.MybatisOnlyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MybatisOnlyConfig.class)
class BeanDebugTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void listBeans() {
        String[] beanNames = context.getBeanDefinitionNames();
        System.out.println("=== All sample beans ===");
        for (String name : beanNames) {
            Object bean = context.getBean(name);
            String className = bean.getClass().getName();
            if (className.contains("cn.structure.infra.sample")) {
                System.out.println("  " + name + " -> " + className);
            }
        }
        System.out.println("=== End ===");
        System.out.println("Total beans: " + beanNames.length);
    }
}
