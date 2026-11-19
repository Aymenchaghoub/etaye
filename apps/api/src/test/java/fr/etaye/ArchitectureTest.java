package fr.etaye;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "fr.etaye", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureTest {

    @ArchTest
    public static final ArchRule communicationsShouldNotDependOnPatients =
            noClasses().that().resideInAPackage("..communications..")
                    .should().dependOnClassesThat().resideInAPackage("..patients..");

    @ArchTest
    public static final ArchRule practicesShouldNotDependOnPatients =
            noClasses().that().resideInAPackage("..practices..")
                    .should().dependOnClassesThat().resideInAPackage("..patients..");

    @ArchTest
    public static final ArchRule patientsShouldNotDependOnCommunications =
            noClasses().that().resideInAPackage("..patients..")
                    .should().dependOnClassesThat().resideInAPackage("..communications..");
}
