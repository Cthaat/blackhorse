package com.ruoyi.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.ruoyi")
class LabLayerArchitectureTest
{
    @ArchTest
    static final ArchRule labControllersMustNotDependOnMappers = noClasses()
            .that().resideInAPackage("com.ruoyi.web.controller.lab..")
            .should().dependOnClassesThat().resideInAnyPackage("..mapper..")
            .as("lab controllers must not depend on mapper classes")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule quartzMustNotDependOnLabMappers = noClasses()
            .that().resideInAPackage("com.ruoyi.quartz..")
            .should().dependOnClassesThat().resideInAPackage("com.ruoyi.lab.mapper..")
            .as("quartz classes must not depend on lab mappers");

    @ArchTest
    static final ArchRule labServicesMustNotDependOnWebControllers = noClasses()
            .that().resideInAPackage("com.ruoyi.lab.service..")
            .should().dependOnClassesThat().resideInAPackage("com.ruoyi.web.controller..")
            .as("lab services must not depend on web controllers")
            .allowEmptyShould(true);
}
