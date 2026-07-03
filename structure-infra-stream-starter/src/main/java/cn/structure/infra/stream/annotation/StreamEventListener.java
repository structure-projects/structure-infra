package cn.structure.infra.stream.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StreamEventListener {

    @AliasFor("bindingName")
    String value() default "";

    @AliasFor("value")
    String bindingName() default "";

    String destination() default "";

    String group() default "";

    String contentType() default "application/json";

    Class<?> eventType() default Object.class;

    String consumerPrefix() default "consumer";

    String producerPrefix() default "producer";

    String condition() default "";

}
