package org.ikigaidigital

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * The core (domain and application) is plain Kotlin: `TimeDepositService` is created in
 * [ApplicationConfiguration], never annotated as a Spring bean (ADR 0011, ADR 0016). The one exception is the
 * application layer's transaction boundary, `@Transactional` (ADR 0014). The domain rule overlaps
 * ArchitectureTest's, but states the Spring-free intent for the whole core in one place.
 */
class CoreIsSpringFreeArchitectureTest {

    private val classes: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("org.ikigaidigital")

    @Test
    fun `should keep the application layer free of Spring except transaction annotations`() {
        noClasses().that().resideInAPackage("..timedeposit.application..")
            .should().dependOnClassesThat(
                resideInAPackage("org.springframework..")
                    .and(resideInAPackage("org.springframework.transaction.annotation..").negate())
            )
            .check(classes)
    }

    @Test
    fun `should keep the domain free of Spring`() {
        noClasses().that().resideInAPackage("..timedeposit.domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .check(classes)
    }
}
