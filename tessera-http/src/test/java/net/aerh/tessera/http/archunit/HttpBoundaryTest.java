package net.aerh.tessera.http.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Within {@code tessera-http}, only classes in the {@code bootstrap} subpackage may import
 * {@code net.aerh.tessera.core..}. The module currently ships only two
 * {@code package-info.java} files so the rule passes trivially; once actual handlers land,
 * this rule becomes the gate that keeps the HTTP layer thin.
 */
class HttpBoundaryTest {

    private final JavaClasses httpClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("net.aerh.tessera.http");

    @Test
    void only_bootstrap_package_may_import_tessera_core() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("net.aerh.tessera.http..")
                .and().resideOutsideOfPackage("net.aerh.tessera.http.bootstrap..")
                .should().dependOnClassesThat().resideInAPackage("net.aerh.tessera.core..");
        rule.check(httpClasses);
    }
}
