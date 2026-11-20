package dev.aymen.etaye;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "dev.aymen.etaye", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureTest {

    @ArchTest
    public static final ArchRule noCyclesBetweenModules =
            slices().matching("dev.aymen.etaye.(*)..")
                    .should().beFreeOfCycles();

    @ArchTest
    public static final ArchRule controllersNeverCallRepositories =
            noClasses().that().resideInAPackage("..web..")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository");

    @ArchTest
    public static final ArchRule auditShouldNotDependOnRecordsDomain =
            noClasses().that().resideInAPackage("..audit..")
                    .should().dependOnClassesThat().resideInAPackage("..records.domain..");

    @ArchTest
    public static final ArchRule communicationsShouldNotDependOnRecordsDomain =
            noClasses().that().resideInAPackage("..communications..")
                    .should().dependOnClassesThat().resideInAPackage("..records.domain..");

    @ArchTest
    public static final ArchRule practicesShouldNotDependOnRecords =
            noClasses().that().resideInAPackage("..practices..")
                    .should().dependOnClassesThat().resideInAPackage("..records..");

    @ArchTest
    public static final ArchRule servicesNeverDependOnWeb =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..web..");

    @ArchTest
    public static final ArchRule auditModuleInternalClassesArePrivate =
            classes().that().resideInAPackage("dev.aymen.etaye.audit..")
                    .and().resideOutsideOfPackages("dev.aymen.etaye.audit.service..", "dev.aymen.etaye.audit.dto..")
                    .should().onlyHaveDependentClassesThat().resideInAnyPackage("dev.aymen.etaye.audit..")
                    .allowEmptyShould(false);

    @ArchTest
    public static final ArchRule communicationsModuleInternalClassesArePrivate =
            classes().that().resideInAPackage("dev.aymen.etaye.communications..")
                    .and().resideOutsideOfPackages("dev.aymen.etaye.communications.service..", "dev.aymen.etaye.communications.dto..")
                    .should().onlyHaveDependentClassesThat().resideInAnyPackage("dev.aymen.etaye.communications..")
                    .allowEmptyShould(false);

    @ArchTest
    public static final ArchRule identityModuleInternalClassesArePrivate =
            classes().that().resideInAPackage("dev.aymen.etaye.identity..")
                    .and().resideOutsideOfPackages("dev.aymen.etaye.identity.service..", "dev.aymen.etaye.identity.dto..")
                    .should().onlyHaveDependentClassesThat().resideInAnyPackage("dev.aymen.etaye.identity..")
                    .allowEmptyShould(false);

    @ArchTest
    public static final ArchRule practicesModuleInternalClassesArePrivate =
            classes().that().resideInAPackage("dev.aymen.etaye.practices..")
                    .and().resideOutsideOfPackages("dev.aymen.etaye.practices.service..", "dev.aymen.etaye.practices.dto..")
                    .should().onlyHaveDependentClassesThat().resideInAnyPackage("dev.aymen.etaye.practices..")
                    .allowEmptyShould(false);

    @ArchTest
    public static final ArchRule recordsModuleInternalClassesArePrivate =
            classes().that().resideInAPackage("dev.aymen.etaye.records..")
                    .and().resideOutsideOfPackages("dev.aymen.etaye.records.service..", "dev.aymen.etaye.records.dto..")
                    .should().onlyHaveDependentClassesThat().resideInAnyPackage("dev.aymen.etaye.records..")
                    .allowEmptyShould(false);
}
