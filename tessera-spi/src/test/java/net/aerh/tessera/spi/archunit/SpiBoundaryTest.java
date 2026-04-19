package net.aerh.tessera.spi.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 *  module-boundary rule for {@code tessera-spi}. The spi module must never reach
 * into {@code tessera-core}; it may depend on {@code tessera-api} only. Post-split:
 * exercises the 7 classical SPI classes relocated from tessera-core in plan.
 *
 * <p>Per amendment, {@code AssetProvider} lives in {@code tessera-api.assets},
 * not here, so this module ships exactly 7 SPI contracts:
 * {@code NbtFormatHandler}, {@code NbtFormatHandlerContext}, {@code DataRegistryProvider},
 * {@code EffectFactory}, {@code FontProviderFactory}, {@code GeneratorFactory},
 * {@code OverlayRendererFactory}.
 */
class SpiBoundaryTest {

    private final JavaClasses spiClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("net.aerh.tessera.spi");

    @Test
    void spi_does_not_depend_on_core() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("net.aerh.tessera.spi..")
                .should().dependOnClassesThat().resideInAPackage("net.aerh.tessera.core..");
        rule.check(spiClasses);
    }

    @Test
    void spi_has_at_least_seven_classical_SPIs() {
        // Seven classical SPIs live in this module: NbtFormatHandler, NbtFormatHandlerContext,
        // DataRegistryProvider, EffectFactory, FontProviderFactory, GeneratorFactory,
        // OverlayRendererFactory. AssetProvider is NOT in spi - it lives in
        // tessera-api.assets. Size guard catches accidental module-emptying.
        assertThat(spiClasses.size()).isGreaterThanOrEqualTo(7);
    }
}
