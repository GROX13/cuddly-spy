package org.ikigaidigital

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * Hexagonal boundaries. The timedeposit sub-packages are still empty, so those rules allow an empty
 * `should` until code arrives; the legacy classes are checked from day one.
 */
class ArchitectureTest {

    private val classes: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("org.ikigaidigital")

    private val frameworkAndAdapters = arrayOf("org.springframework..", "jakarta.persistence..", "..adapter..")

    @Test
    fun `should keep the domain free of frameworks and adapters`() {
        noClasses().that().resideInAPackage("..timedeposit.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(*frameworkAndAdapters)
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `should keep the legacy calculator free of frameworks and adapters`() {
        noClasses().that().belongToAnyOf(TimeDeposit::class.java, TimeDepositCalculator::class.java)
            .should().dependOnClassesThat().resideInAnyPackage(*frameworkAndAdapters)
            .check(classes)
    }

    @Test
    fun `should keep the application layer independent of adapters`() {
        noClasses().that().resideInAPackage("..timedeposit.application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `should keep the web adapter independent of the persistence adapter`() {
        noClasses().that().resideInAPackage("..adapter.web..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.persistence..")
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `should keep the persistence adapter independent of the web adapter`() {
        noClasses().that().resideInAPackage("..adapter.persistence..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.web..")
            .allowEmptyShould(true)
            .check(classes)
    }
}
