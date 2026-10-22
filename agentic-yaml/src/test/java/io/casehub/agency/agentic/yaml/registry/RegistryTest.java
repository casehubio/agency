package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.activation.MaxIterationsGuard;
import io.casehub.agency.agentic.activation.OnExplicitDispatch;
import io.casehub.agency.agentic.aggregation.CollectAll;
import io.casehub.agency.agentic.aggregation.MajorityVote;
import io.casehub.agency.agentic.aggregation.PassThrough;
import io.casehub.agency.agentic.decomposition.CapabilityDependencyDecomposition;
import io.casehub.agency.agentic.decomposition.IdentityDecomposition;
import io.casehub.agency.agentic.judgment.AlwaysYield;
import io.casehub.agency.agentic.judgment.CallerStrategy;
import io.casehub.agency.agentic.judgment.IterationBased;
import io.casehub.agency.agentic.judgment.NeverYield;
import io.casehub.agency.agentic.routing.RoundRobinRouting;
import io.casehub.agency.agentic.routing.SelectAllRouting;
import io.casehub.agency.agentic.routing.SequentialRouting;
import io.casehub.agency.agentic.termination.MaxIterationsTermination;
import io.casehub.agency.agentic.yaml.spec.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistryTest {

    @Nested
    class RoutingTests {
        private final RoutingStrategyRegistry registry = new RoutingStrategyRegistry();

        @Test
        void roundRobin() {
            var result = registry.resolve(new RoutingSpec.RoundRobin(), null);
            assertThat(result).isInstanceOf(RoundRobinRouting.class);
        }

        @Test
        void sequential() {
            var result = registry.resolve(new RoutingSpec.Sequential(), null);
            assertThat(result).isInstanceOf(SequentialRouting.class);
        }

        @Test
        void selectAll() {
            var result = registry.resolve(new RoutingSpec.SelectAll(), null);
            assertThat(result).isInstanceOf(SelectAllRouting.class);
        }

        @Test
        void firstMatchWithoutGuard() {
            var result = registry.resolve(new RoutingSpec.FirstMatch(null), null);
            assertThat(result).isNotNull();
        }
    }

    @Nested
    class TerminationTests {
        private final TerminationConditionRegistry registry = new TerminationConditionRegistry();

        @Test
        void maxIterations() {
            var result = registry.resolve(new TerminationSpec.MaxIterations(10), null);
            assertThat(result).isInstanceOf(MaxIterationsTermination.class);
        }

        @Test
        void singlePass() {
            var result = registry.resolve(new TerminationSpec.SinglePass(), null);
            assertThat(result).isInstanceOf(MaxIterationsTermination.class);
        }
    }

    @Nested
    class AggregationTests {
        private final AggregationStrategyRegistry registry = new AggregationStrategyRegistry();

        @Test
        void passThrough() {
            var result = registry.resolve(new AggregationSpec.PassThrough());
            assertThat(result).isInstanceOf(PassThrough.class);
        }

        @Test
        void collectAll() {
            var result = registry.resolve(new AggregationSpec.CollectAll());
            assertThat(result).isInstanceOf(CollectAll.class);
        }

        @Test
        void majorityVote() {
            var result = registry.resolve(new AggregationSpec.MajorityVote());
            assertThat(result).isInstanceOf(MajorityVote.class);
        }
    }

    @Nested
    class ActivationTests {
        private final ActivationRuleRegistry registry = new ActivationRuleRegistry();

        @Test
        void onDispatch() {
            var result = registry.resolve(new ActivationSpec.OnDispatch());
            assertThat(result).isInstanceOf(OnExplicitDispatch.class);
        }

        @Test
        void maxIterationsGuard() {
            var result = registry.resolve(new ActivationSpec.MaxIterationsGuard(5));
            assertThat(result).isInstanceOf(MaxIterationsGuard.class);
        }
    }

    @Nested
    class DecompositionTests {
        private final DecompositionStrategyRegistry registry = new DecompositionStrategyRegistry();

        @Test
        void identity() {
            var result = registry.resolve(new DecompositionSpec.Identity(), null);
            assertThat(result).isInstanceOf(IdentityDecomposition.class);
        }

        @Test
        void goap() {
            var result = registry.resolve(new DecompositionSpec.Goap(), null);
            assertThat(result).isInstanceOf(CapabilityDependencyDecomposition.class);
        }

        @Test
        void capabilityDependency() {
            var result = registry.resolve(new DecompositionSpec.CapabilityDependency(), null);
            assertThat(result).isInstanceOf(CapabilityDependencyDecomposition.class);
        }
    }

    @Nested
    class JudgmentTriggerTests {
        private final JudgmentTriggerRegistry registry = new JudgmentTriggerRegistry();

        @Test
        void alwaysYield() {
            var result = registry.resolve(new JudgmentSpec.TriggerSpec.AlwaysYield(), null);
            assertThat(result).isInstanceOf(AlwaysYield.class);
        }

        @Test
        void neverYield() {
            var result = registry.resolve(new JudgmentSpec.TriggerSpec.NeverYield(), null);
            assertThat(result).isInstanceOf(NeverYield.class);
        }

        @Test
        void iterationBased() {
            var result = registry.resolve(new JudgmentSpec.TriggerSpec.IterationBased(3), null);
            assertThat(result).isInstanceOf(IterationBased.class);
        }
    }

    @Nested
    class CallerStrategyTests {
        private final CallerStrategyRegistry registry = new CallerStrategyRegistry();

        @Test
        void single() {
            var result = registry.resolve(new JudgmentSpec.CallerSpec.Single("judge-1"));
            assertThat(result).isInstanceOf(CallerStrategy.Single.class);
            assertThat(((CallerStrategy.Single) result).caller().id()).isEqualTo("judge-1");
        }

        @Test
        void fanOut() {
            var result = registry.resolve(
                    new JudgmentSpec.CallerSpec.FanOut(
                            List.of("judge-1", "judge-2"),
                            new JudgmentSpec.AgreementSpec.Majority()));
            assertThat(result).isInstanceOf(CallerStrategy.FanOut.class);
            assertThat(((CallerStrategy.FanOut) result).callers()).hasSize(2);
        }

        @Test
        void escalationChain() {
            var result = registry.resolve(
                    new JudgmentSpec.CallerSpec.EscalationChain(List.of("junior", "senior")));
            assertThat(result).isInstanceOf(CallerStrategy.EscalationChain.class);
            assertThat(((CallerStrategy.EscalationChain) result).callers()).hasSize(2);
        }
    }

    @Nested
    class FallbackExtensibility {

        @Test
        void routingFallbackOverridesUnsupportedType() {
            var registry = new RoutingStrategyRegistry();
            var customRouting = new RoundRobinRouting<>();
            registry.registerFallback(spec -> {
                if (spec instanceof RoutingSpec.LlmSelected) return customRouting;
                return null;
            });
            var result = registry.resolve(new RoutingSpec.LlmSelected(), null);
            assertThat(result).isSameAs(customRouting);
        }

        @Test
        void routingFallbackReturnsNullFallsThroughToBuiltIn() {
            var registry = new RoutingStrategyRegistry();
            registry.registerFallback(spec -> null);
            var result = registry.resolve(new RoutingSpec.RoundRobin(), null);
            assertThat(result).isInstanceOf(RoundRobinRouting.class);
        }

        @Test
        void routingWithoutFallbackThrowsOnUnsupported() {
            var registry = new RoutingStrategyRegistry();
            assertThatThrownBy(() -> registry.resolve(new RoutingSpec.LlmSelected(), null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void aggregationFallbackOverridesUnsupportedType() {
            var registry = new AggregationStrategyRegistry();
            var customAggregation = new CollectAll<>();
            registry.registerFallback(spec -> {
                if (spec instanceof AggregationSpec.Auction) return customAggregation;
                return null;
            });
            var result = registry.resolve(new AggregationSpec.Auction("english"));
            assertThat(result).isSameAs(customAggregation);
        }

        @Test
        void terminationFallbackOverridesUnsupportedType() {
            var registry = new TerminationConditionRegistry();
            var customTermination = new MaxIterationsTermination<>(99);
            registry.registerFallback(spec -> {
                if (spec instanceof TerminationSpec.Convergence) return customTermination;
                return null;
            });
            var result = registry.resolve(new TerminationSpec.Convergence(0.8), null);
            assertThat(result).isSameAs(customTermination);
        }
    }
}
