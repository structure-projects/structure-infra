package cn.structure.infra.stream.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StreamRouteHandler {

    @AliasFor("eventType")
    String value() default "";

    @AliasFor("value")
    String eventType() default "";

    String businessType() default "";

    String condition() default "";

}
