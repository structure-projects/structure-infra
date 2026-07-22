package cn.structure.infra.annotations;

import cn.structure.infra.repository.RepositoryType;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepositoryTypeAnnotation {

    RepositoryType value();
}