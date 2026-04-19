package net.aerh.tessera.api.exception;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link UnsupportedMinecraftVersionException} message contract: it must
 * contain the requested version, the registered version list, a JitPack coordinate
 * snippet, and a programmatic-registration code snippet. Also verifies the invariant
 * that the exception is unchecked and extends {@link TesseraException}.
 */
class UnsupportedMinecraftVersionExceptionTest {

    @Test
    void messageContainsRequestedVersionAndRegisteredListAndJitpackExample() {
        UnsupportedMinecraftVersionException ex = new UnsupportedMinecraftVersionException(
                "27.0.0",
                Set.of("26.1.2", "26.1.1"),
                "1.0.0-SNAPSHOT");

        String msg = ex.getMessage();
        assertThat(msg).contains("\"27.0.0\"");
        assertThat(msg).contains("26.1.2");
        assertThat(msg).contains("26.1.1");
        assertThat(msg).contains("com.github.Aerhhh.tessera");
        assertThat(msg).contains("tessera-assets-27.0.0");
        assertThat(msg).contains("1.0.0-SNAPSHOT");
        assertThat(msg).contains("Engine.builder()");
        assertThat(msg).contains("assetProvider(");
    }

    @Test
    void messageHandlesEmptyRegisteredSet() {
        UnsupportedMinecraftVersionException ex = new UnsupportedMinecraftVersionException(
                "27.0.0", List.of(), "1.0.0-SNAPSHOT");
        assertThat(ex.getMessage()).contains("(none");
    }

    @Test
    void contextMapIsPopulated() {
        UnsupportedMinecraftVersionException ex = new UnsupportedMinecraftVersionException(
                "27.0.0", Set.of("26.1.2"), "1.0.0-SNAPSHOT");
        assertThat(ex.getContext()).containsEntry("requested", "27.0.0");
        assertThat(ex.getContext()).containsKey("registered");
        assertThat(ex.getContext()).containsEntry("tesseraVersion", "1.0.0-SNAPSHOT");
    }

    @Test
    void isUncheckedAndExtendsTesseraException() {
        UnsupportedMinecraftVersionException ex = new UnsupportedMinecraftVersionException(
                "27.0.0", Set.of(), "1.0.0-SNAPSHOT");
        assertThat(ex).isInstanceOf(TesseraException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
