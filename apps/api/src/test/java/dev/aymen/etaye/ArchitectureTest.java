package dev.aymen.etaye;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
    public static final ArchRule auditDomainAndRepositoryArePrivate =
            noClasses().that().resideOutsideOfPackage("dev.aymen.etaye.audit..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "dev.aymen.etaye.audit.domain..",
                            "dev.aymen.etaye.audit.repository.."
                    ).as("Classes outside the audit module must not depend on its domain or repository packages; only service and dto are allowed");

    @ArchTest
    public static final ArchRule communicationsDomainAndRepositoryArePrivate =
            noClasses().that().resideOutsideOfPackage("dev.aymen.etaye.communications..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "dev.aymen.etaye.communications.domain..",
                            "dev.aymen.etaye.communications.repository.."
                    ).as("Classes outside the communications module must not depend on its domain or repository packages; only service and dto are allowed");

    @ArchTest
    public static final ArchRule identityDomainAndRepositoryArePrivate =
            noClasses().that().resideOutsideOfPackage("dev.aymen.etaye.identity..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "dev.aymen.etaye.identity.domain..",
                            "dev.aymen.etaye.identity.repository.."
                    ).as("Classes outside the identity module must not depend on its domain or repository packages; only service and dto are allowed");

    @ArchTest
    public static final ArchRule practicesDomainAndRepositoryArePrivate =
            noClasses().that().resideOutsideOfPackage("dev.aymen.etaye.practices..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "dev.aymen.etaye.practices.domain..",
                            "dev.aymen.etaye.practices.repository.."
                    ).as("Classes outside the practices module must not depend on its domain or repository packages; only service and dto are allowed");

    @ArchTest
    public static final ArchRule recordsDomainAndRepositoryArePrivate =
            noClasses().that().resideOutsideOfPackage("dev.aymen.etaye.records..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "dev.aymen.etaye.records.domain..",
                            "dev.aymen.etaye.records.repository.."
                    ).as("Classes outside the records module must not depend on its domain or repository packages; only service and dto are allowed");
}
