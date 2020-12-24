package com.github.savkk.allure;

import io.qameta.allure.AllureId;
import io.qameta.allure.ee.filter.EnvTestPlanProvider;
import io.qameta.allure.ee.filter.FileTestPlanProvider;
import io.qameta.allure.ee.filter.TestPlan;
import io.qameta.allure.ee.filter.TestPlanTest;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.testng.IAlterSuiteListener;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class TestPlanFilter implements IAlterSuiteListener {
    private static final String SUITE_NAME = "Allure EE Suite";
    private static final String TEST_PACKAGE_ENV_KEY = "TEST_PACKAGE";

    @Override
    public void alter(List<XmlSuite> suites) {
        Optional<TestPlan> fileTestPlanProvider = new FileTestPlanProvider().get();
        Optional<TestPlan> envTestPlanProvider = new EnvTestPlanProvider().get();
        List<TestMethod> methodsForRun = null;
        if (fileTestPlanProvider.isPresent()) {
            methodsForRun = getTestMethodsForRun(fileTestPlanProvider.get().getTests());
        } else if (envTestPlanProvider.isPresent()) {
            methodsForRun = getTestMethodsForRun(envTestPlanProvider.get().getTests());

        }

        if (methodsForRun == null || methodsForRun.isEmpty()) {
            return;
        }
        suites.clear();
        suites.add(getVirtualSuite(SUITE_NAME, methodsForRun));
    }

    /**
     * Метод подготовки вируального набора тестов
     *
     * @param suiteName     - название набора
     * @param methodsForRun - список тестовых методов
     * @return - виртуальный набор тестов
     */
    private static XmlSuite getVirtualSuite(String suiteName, List<TestMethod> methodsForRun) {
        XmlSuite suite = new XmlSuite();
        suite.setName(suiteName);
        suite.setAllowReturnValues(true);


        methodsForRun = methodsForRun.stream()
                .sorted(Comparator.comparing(TestMethod::getClassName))
                .collect(Collectors.toList());

        List<XmlClass> classes = new ArrayList<>();
        XmlClass xmlClass = null;
        List<XmlInclude> methods = null;
        for (TestMethod testMethod : methodsForRun) {
            if (xmlClass == null || !xmlClass.getName().equals(testMethod.getClassName())) {
                xmlClass = new XmlClass(testMethod.getClassName());
                classes.add(xmlClass);
                methods = new ArrayList<>();
                xmlClass.setIncludedMethods(methods);
            }
            methods.add(new XmlInclude(testMethod.getMethodName()));
        }

        XmlTest test = new XmlTest(suite);
        test.setXmlClasses(classes);
        return suite;
    }


    /**
     * @param testsForRun - набор id тестов из TestRail
     * @return - список тестовых методов, входящих в набор из TestRail
     */
    private static List<TestMethod> getTestMethodsForRun(List<TestPlanTest> testsForRun) {
        Set<Integer> testPlanTestSet = testsForRun.stream()
                .map(TestPlanTest::getId)
                .map(Integer::valueOf)
                .collect(Collectors.toSet());
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage(System.getenv(TEST_PACKAGE_ENV_KEY)))
                .setScanners(new MethodAnnotationsScanner()));
        Set<Method> methodsAnnotatedWith = reflections.getMethodsAnnotatedWith(AllureId.class);

        ArrayList<TestMethod> testMethods = new ArrayList<>();

        methodsAnnotatedWith.forEach(method -> testMethods.add(new TestMethod
                (Integer.parseInt(method.getAnnotation(AllureId.class).value()),
                        method.getDeclaringClass().getName(),
                        method.getName())));

        return testMethods.stream().filter(testMethod -> testPlanTestSet.contains(testMethod.getCaseId())).collect(Collectors.toList());
    }
}
