/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.karaf.itest.blueprint;

import org.drools.karaf.itest.AbstractKarafIntegrationTest;
import org.drools.karaf.itest.util.KieScannerTestUtils;
import org.drools.karaf.itest.util.TimerUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieSession;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.Constants;

import javax.inject.Inject;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.streamBundle;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.keepRuntimeFolder;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;
import static org.ops4j.pax.tinybundles.core.TinyBundles.bundle;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class KieBlueprintImportScannerIntegrationTest extends AbstractKarafIntegrationTest {

    private static final String BLUEPRINT_XML_LOCATION = "/org/drools/karaf/itest/blueprint/kie-import-scanner-blueprint.xml";
    private static final ReleaseId RELEASE_ID = KieServices.Factory.get().newReleaseId("org.drools.karaf.itest", "import-scanner-test-jar", "1.0-SNAPSHOT");

    private KieScannerTestUtils kieScannerTestUtils = new KieScannerTestUtils();

    @Inject
    KieBase kieBase;

    @Inject
    KieScanner kieScanner;

    @Before
    public void setUp() {
        kieScannerTestUtils.setUp();
        kieScannerTestUtils.createAndInstallKJar(RELEASE_ID, "rule 1");
    }

    @After
    public void tearDown() {
        kieScannerTestUtils.tearDown();
    }

    @Test
    public void testKieBaseUpdatedByScanNow() {
        Assert.assertNotNull(kieBase);

        KieSession kieSession = kieBase.newKieSession();
        kieScannerTestUtils.checkKSession(true, kieSession, "rule 1");

        // update kJar
        kieScannerTestUtils.createAndInstallKJar(RELEASE_ID, "rule 2");
        kieScanner.scanNow();

        kieSession = kieBase.newKieSession();
        kieScannerTestUtils.checkKSession(true, kieSession, "rule 2");
    }

    @Test
    public void testKieBaseUpdatedByTimer() throws InterruptedException {
        Assert.assertNotNull(kieBase);

        KieSession kieSession = kieBase.newKieSession();
        kieScannerTestUtils.checkKSession(true, kieSession, "rule 1");

        // update kJar
        kieScannerTestUtils.createAndInstallKJar(RELEASE_ID, "rule 2");
        TimerUtils.sleepMillis(2000);

        kieSession = kieBase.newKieSession();
        kieScannerTestUtils.checkKSession(true, kieSession, "rule 2");
    }

    @Configuration
    public static Option[] configure() {
        return new Option[]{
                // Install Karaf Container
                getKarafDistributionOption(),

                // It is really nice if the container sticks around after the test so you can check the contents
                // of the data directory when things go wrong.
                keepRuntimeFolder(),
                // Don't bother with local console output as it just ends up cluttering the logs
                configureConsole().ignoreLocalConsole(),
                // Force the log level to INFO so we have more details during the test.  It defaults to WARN.
                logLevel(LogLevelOption.LogLevel.INFO),

                // Option to be used to do remote debugging
                // debugConfiguration("5005", true),

                // Load Kie-Aries-Blueprint
                loadKieFeatures("drools-module", "kie-ci", "kie-aries-blueprint"),

                // Load domain model classes
                wrappedBundle(mavenBundle().groupId("org.drools").artifactId("drools-karaf-itests-domain-model").versionAsInProject()),

                // utility classes from Drools-core test jar
                //wrappedBundle(mavenBundle().groupId("org.drools").artifactId("drools-core").versionAsInProject().type("test-jar")),

                // Create a bundle with META-INF/spring/kie-beans.xml - this should be processed automatically by Spring
                streamBundle(bundle()
                        .add("OSGI-INF/blueprint/kie-scanner-import-blueprint.xml",
                                KieBlueprintDependencyKarafIntegrationTest.class.getResource(BLUEPRINT_XML_LOCATION))
                        .set(Constants.BUNDLE_SYMBOLICNAME, "Test-Blueprint-Bundle")
                        .set(Constants.IMPORT_PACKAGE, "org.kie.aries.blueprint," +
                                "org.osgi.service.blueprint.container," +
                                "org.drools.karaf.itest.blueprint.domain," +
                                "org.kie.aries.blueprint.factorybeans," +
                                "org.kie.aries.blueprint.helpers," +
                                "org.kie.api," +
                                "org.kie.api.runtime," +
                                "org.kie.api.builder," +
                                "*")
                        .set(Constants.EXPORT_PACKAGE, "org.drools.karaf.itest.blueprint.domain")
                        .set(Constants.BUNDLE_SYMBOLICNAME, "Test-Blueprint-Bundle")
                        .build()).start()

        };
    }
}
