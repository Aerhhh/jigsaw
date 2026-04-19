package net.aerh.tessera.core.engine;

import net.aerh.tessera.api.exception.RegistryException;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.api.generator.Generator;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.core.generator.CoreRenderRequest;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the  +  contract on {@link StartupCheck}: reflective iteration over
 * {@link CoreRenderRequest#getPermittedSubclasses()} catches any permits-subtype without
 * a registered generator and throws {@link RegistryException} naming every missing type.
 */
class StartupCheckTest {

    /**
     * Minimal stub - only the dispatch-discriminator shape matters; render() is never called
     * by StartupCheck, which only inspects the keyset.
     */
    private static Generator<CoreRenderRequest, GeneratorResult> stubGenerator() {
        return new Generator<>() {
            @Override
            public GeneratorResult render(CoreRenderRequest request, GenerationContext context) {
                return null;
            }

            @Override
            public Class<CoreRenderRequest> inputType() {
                return CoreRenderRequest.class;
            }

            @Override
            public Class<GeneratorResult> outputType() {
                return GeneratorResult.class;
            }
        };
    }

    @Test
    void passes_when_all_six_permitted_subtypes_are_registered() {
        Map<Class<? extends CoreRenderRequest>, Generator<?, ?>> registered = new HashMap<>();
        Generator<CoreRenderRequest, GeneratorResult> stub = stubGenerator();
        for (Class<?> permitted : CoreRenderRequest.class.getPermittedSubclasses()) {
            @SuppressWarnings("unchecked")
            Class<? extends CoreRenderRequest> p = (Class<? extends CoreRenderRequest>) permitted;
            registered.put(p, stub);
        }
        assertThatCode(() -> StartupCheck.verifyAllCoreRequestsHaveGenerators(registered))
                .doesNotThrowAnyException();
    }

    @Test
    void throws_RegistryException_when_mapping_is_empty() {
        Map<Class<? extends CoreRenderRequest>, Generator<?, ?>> partial = new HashMap<>();
        assertThatThrownBy(() -> StartupCheck.verifyAllCoreRequestsHaveGenerators(partial))
                .isInstanceOf(RegistryException.class)
                .hasMessageContaining("No GeneratorFactory registered for built-in request types");
    }

    @Test
    void message_and_context_list_every_missing_type() {
        Map<Class<? extends CoreRenderRequest>, Generator<?, ?>> partial = new HashMap<>();
        try {
            StartupCheck.verifyAllCoreRequestsHaveGenerators(partial);
            org.junit.jupiter.api.Assertions.fail("expected RegistryException");
        } catch (RegistryException ex) {
            // Message names each of the 6 permits subtypes.
            assertThat(ex.getMessage()).contains("ItemRequest");
            assertThat(ex.getMessage()).contains("TooltipRequest");
            assertThat(ex.getMessage()).contains("InventoryRequest");
            assertThat(ex.getMessage()).contains("PlayerHeadRequest");
            assertThat(ex.getMessage()).contains("PlayerModelRequest");
            assertThat(ex.getMessage()).contains("CompositeRequest");
            // Context carries the structured missing-list for JSON logging.
            assertThat(ex.getContext()).containsKey("missing");
            @SuppressWarnings("unchecked")
            List<String> missing = (List<String>) ex.getContext().get("missing");
            assertThat(missing).hasSize(CoreRenderRequest.class.getPermittedSubclasses().length);
        }
    }

    @Test
    void rejects_null_map() {
        assertThatThrownBy(() -> StartupCheck.verifyAllCoreRequestsHaveGenerators(null))
                .isInstanceOf(NullPointerException.class);
    }
}
