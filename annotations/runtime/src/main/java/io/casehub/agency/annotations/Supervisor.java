package io.casehub.agency.annotations;

import io.casehub.agency.orchestration.aggregation.AggregationStrategy;
import io.casehub.agency.orchestration.aggregation.PassThrough;
import io.casehub.agency.orchestration.decomposition.IdentityDecomposition;
import io.casehub.agency.orchestration.routing.FirstMatchRouting;
import io.casehub.agency.orchestration.routing.RoutingStrategy;
import io.casehub.engine.plan.DecompositionStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Supervisor {

    String name() default "";

    int maxIterations() default 10;

    Class<? extends RoutingStrategy> routing() default FirstMatchRouting.class;

    Class<? extends DecompositionStrategy> decomposition() default IdentityDecomposition.class;

    Class<? extends AggregationStrategy> aggregation() default PassThrough.class;
}
