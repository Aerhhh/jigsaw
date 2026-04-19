package net.aerh.tessera.core.engine;

import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.GenerationFeedback;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preservation test for the generation-feedback wiring.
 *
 * <p>{@code GenerationContext} + {@code GenerationFeedback} ship as the feedback plumbing
 * surface; future work must not delete, rename, or regress that wiring. This test asserts
 * the API shape is still present and invocable: the consumer-supplied
 * {@link GenerationFeedback} is reachable via
 * {@link GenerationContext.Builder#feedback(GenerationFeedback)} and can be driven with both
 * message and ephemerality signals. Functional-level render-through-feedback assertions
 * live alongside the fluent-builder tests; this test's job is to prove the surface area is
 * still declarable and reachable.
 */
class GenerationFeedbackDebugTest {

    @Test
    void feedback_consumerReceivesMessagesAndEphemeralSignal() {
        List<String> messages = new ArrayList<>();
        List<Boolean> ephemeralSignals = new ArrayList<>();

        GenerationFeedback feedback = (msg, ephemeral) -> {
            messages.add(msg);
            ephemeralSignals.add(ephemeral);
        };

        GenerationContext context = GenerationContext.builder()
                .skipCache(false)
                .feedback(feedback)
                .build();

        assertThat(context).isNotNull();
        assertThat(context.feedback()).as("feedback must round-trip through the builder")
                .isSameAs(feedback);

        context.feedback().send("timing=42ms cacheStatus=HIT", false);
        context.feedback().send("effectTrace: glint,hover", true);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).contains("timing").contains("cacheStatus");
        assertThat(messages.get(1)).contains("effectTrace");
        assertThat(ephemeralSignals).containsExactly(false, true);
    }

    @Test
    void feedback_noopDiscardsSilently() {
        GenerationFeedback noop = GenerationFeedback.noop();
        assertThat(noop).isNotNull();
        noop.send("any message", true);
        noop.send("another", false);
        // No exception expected; feedback sink is a no-op.
    }

    @Test
    void defaults_isInstanceWithNoopFeedback() {
        GenerationContext defaults = GenerationContext.defaults();
        assertThat(defaults.skipCache()).isFalse();
        assertThat(defaults.feedback()).isNotNull();
        // No-op should not throw.
        defaults.feedback().send("smoke", true);
    }
}
