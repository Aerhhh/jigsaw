package net.aerh.tessera.api.assets;

import net.aerh.tessera.api.assets.EulaGate;
import net.aerh.tessera.api.exception.TesseraEulaNotAcceptedException;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearEnvironmentVariable;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetEnvironmentVariable;
import org.junitpioneer.jupiter.SetSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EulaGateTest {

    @Test
    @ClearEnvironmentVariable(key = "TESSERA_ACCEPT_MOJANG_EULA")
    @ClearSystemProperty(key = "tessera.accept.mojang.eula")
    void builderFlagTrueReturnsSilently() {
        assertThatCode(() -> EulaGate.requireEulaAcceptance(true)).doesNotThrowAnyException();
    }

    @Test
    @SetEnvironmentVariable(key = "TESSERA_ACCEPT_MOJANG_EULA", value = "true")
    @ClearSystemProperty(key = "tessera.accept.mojang.eula")
    void envVarAcceptsEvenWhenBuilderFlagFalse() {
        assertThatCode(() -> EulaGate.requireEulaAcceptance(false)).doesNotThrowAnyException();
    }

    @Test
    @ClearEnvironmentVariable(key = "TESSERA_ACCEPT_MOJANG_EULA")
    @SetSystemProperty(key = "tessera.accept.mojang.eula", value = "true")
    void sysPropAcceptsEvenWhenBuilderFlagFalse() {
        assertThatCode(() -> EulaGate.requireEulaAcceptance(false)).doesNotThrowAnyException();
    }

    @Test
    @ClearEnvironmentVariable(key = "TESSERA_ACCEPT_MOJANG_EULA")
    @ClearSystemProperty(key = "tessera.accept.mojang.eula")
    void noneSetThrowsWithAllThreeAcceptancePathNames() {
        assertThatThrownBy(() -> EulaGate.requireEulaAcceptance(false))
                .isInstanceOf(TesseraEulaNotAcceptedException.class)
                .satisfies(ex -> {
                    String msg = ex.getMessage();
                    assertThat(msg).contains("acceptMojangEula");
                    assertThat(msg).contains("TESSERA_ACCEPT_MOJANG_EULA");
                    assertThat(msg).contains("tessera.accept.mojang.eula");
                });
    }
}
