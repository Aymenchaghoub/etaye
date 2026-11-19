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
    public static final ArchRule recordsShouldNotDependOnCommunications =
            noClasses().that().resideInAPackage("..records..")
                    .should().dependOnClassesThat().resideInAPackage("..communications..");
}
