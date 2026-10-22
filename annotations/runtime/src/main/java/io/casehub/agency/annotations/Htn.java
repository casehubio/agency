package io.casehub.agency.annotations;

import io.casehub.agency.orchestration.decomposition.StaticDecomposition;
import io.casehub.engine.plan.DecompositionStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Htn {

    String name() default "";

    Class<? extends DecompositionStrategy> decomposition() default StaticDecomposition.class;
}
