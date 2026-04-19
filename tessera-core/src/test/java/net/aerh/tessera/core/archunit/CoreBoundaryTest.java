package net.aerh.tessera.core.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Internal structural rules for {@code tessera-core}.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@code only_core_image_may_implement_animated_encoder} - per Issue 3 to 3A (
 *       de-sealed {@code AnimatedEncoder} when it moved to tessera-api), this ArchUnit rule
 *       is the equivalent enforcement: only classes in {@code net.aerh.tessera.core.image}
 *       may implement {@code net.aerh.tessera.api.image.AnimatedEncoder}.</li>
 *   <li>{@code core_does_not_depend_on_http} - core must not reach into the HTTP adapter.</li>
 * </ul>
 *
 * <p>Reactor-wide Graphics2DFactory enforcement lives in
 * {@link Graphics2DFactoryArchTest} .
 */
class CoreBoundaryTest {

    private final JavaClasses coreClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("net.aerh.tessera");

    /**
     * Per Issue 3 to 3A: replaces the sealed-interface protection on {@code AnimatedEncoder}
     * that was dropped (moved to {@code tessera-api.image} and de-sealed because
     * cross-module sealing in the unnamed module requires identical packages). This ArchUnit
     * rule is the equivalent enforcement: only classes in
     * {@code net.aerh.tessera.core.image.*} may implement {@code AnimatedEncoder}.
     */
    @Test
    void only_core_image_may_implement_animated_encoder() {
        ArchRule rule = classes()
                .that().implement("net.aerh.tessera.api.image.AnimatedEncoder")
                .should().resideInAPackage("net.aerh.tessera.core.image..");
        rule.check(coreClasses);
    }

    @Test
    void core_does_not_depend_on_http() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("net.aerh.tessera.core..")
                .should().dependOnClassesThat().resideInAPackage("net.aerh.tessera.http..");
        rule.check(coreClasses);
    }
}
