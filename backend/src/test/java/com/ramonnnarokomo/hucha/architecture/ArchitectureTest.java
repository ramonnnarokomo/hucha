package com.ramonnnarokomo.hucha.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.math.BigDecimal;

import com.ramonnnarokomo.hucha.account.Account;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Reglas de arquitectura que se comprueban en cada build. */
@AnalyzeClasses(packages = "com.ramonnnarokomo.hucha", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Los módulos (paquetes de primer nivel) no pueden depender unos de otros en círculo. */
    @ArchTest
    static final ArchRule noCyclesBetweenModules = slices()
            .matching("com.ramonnnarokomo.hucha.(*)..")
            .should().beFreeOfCycles();

    /** El saldo solo cambia a través del libro mayor. */
    @ArchTest
    static final ArchRule onlyTheLedgerChangesBalances = noClasses()
            .that().resideOutsideOfPackage("..ledger..")
            .should().callMethod(Account.class, "applyPosting", BigDecimal.class);

    /** Los controladores hablan con servicios, nunca directamente con repositorios. */
    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");

    /** El dominio no conoce la capa web. */
    @ArchTest
    static final ArchRule entitiesDoNotDependOnWeb = noClasses()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().dependOnClassesThat().resideInAPackage("org.springframework.web..");
}
