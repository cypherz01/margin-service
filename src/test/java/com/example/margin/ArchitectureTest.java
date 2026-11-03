package com.example.margin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.springframework.web.bind.annotation.RestController;

/**
 * Enforces the ports-and-adapters structure described in the README, so it cannot erode one convenient
 * import at a time. A violation fails the build with the offending class and dependency.
 */
@AnalyzeClasses(packages = "com.example.margin", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String[] KAFKA = {"org.apache.kafka..", "org.springframework.kafka.."};

    @ArchTest
    static final ArchRule domainIsPlainJava = classes()
            .that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("..domain..", "java..")
            .because("the domain model must not depend on frameworks, transport or persistence");

    @ArchTest
    static final ArchRule marginModelIsIndependentOfDeliveryAndInfrastructure = noClasses()
            .that().resideInAPackage("..calculation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..api..", "..service..", "..messaging..", "..marketdata..",
                    "org.springframework.web..", "org.apache.kafka..", "org.springframework.kafka..")
            .because("the margin model is a pure function of the portfolio and its parameters");

    @ArchTest
    static final ArchRule serviceUsesPortsNotAdapters = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "org.springframework.web..")
            .orShould().dependOnClassesThat().resideInAnyPackage(KAFKA)
            .orShould().dependOnClassesThat().haveSimpleName("KafkaMarginCallPublisher")
            .because("the service talks to Kafka only through the MarginCallPublisher port");

    @ArchTest
    static final ArchRule kafkaIsConfinedToTheMessagingAdapter = noClasses()
            .that().resideOutsideOfPackages("..messaging..", "..config..")
            .should().dependOnClassesThat().resideInAnyPackage(KAFKA);

    @ArchTest
    static final ArchRule marketDataVendorIsHiddenBehindItsPort = noClasses()
            .that().resideOutsideOfPackages("..marketdata..", "..config..")
            .should().dependOnClassesThat().haveSimpleName("FinnhubQuoteProvider")
            .because("callers depend on QuoteProvider so the vendor can be swapped");

    @ArchTest
    static final ArchRule nothingDependsOnTheWebLayer = noClasses()
            .that().resideOutsideOfPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule controllersLiveInTheApiPackage = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule packagesAreFreeOfCycles = slices()
            .matching("com.example.margin.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule noFieldInjection = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule noSystemOutOrErr = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule noJavaUtilLogging = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule noGenericExceptions = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
}
