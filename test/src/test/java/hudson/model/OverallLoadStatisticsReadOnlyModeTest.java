/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc. and others
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hudson.security.HudsonPrivateSecurityRealm;
import jenkins.model.Jenkins;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Part of the JENKINS-12548 "Read-only system configuration browsing" epic (JEP-224).
 *
 * <p>Discovered while working on JENKINS-62431: gating the "Manage Jenkins &gt; Load Statistics"
 * page itself did not protect the raw data backing it. {@link Jenkins#overallLoad} and
 * {@link Jenkins#unlabeledLoad} are plain root-level fields with no permission check of their own
 * in {@link LoadStatistics#doGraph} / {@link LoadStatistics#getApi}, so {@code overallLoad/graph},
 * {@code overallLoad/api/json}, and the {@code unlabeledLoad} equivalents were reachable directly by
 * any user holding just {@link Jenkins#READ}, regardless of whether the page that links to them
 * requires {@link Jenkins#SYSTEM_READ}/{@link Jenkins#MANAGE}.
 *
 * <p>This mirrors an established core convention for informational-but-still-gated diagnostic data,
 * e.g. {@code hudson.diagnosis.MemoryUsageMonitor.MemoryGroup#doGraph} and
 * {@code jenkins.diagnosis.MemoryUsageMonitorAction#getHeap}, both of which already call
 * {@code Jenkins.get().checkAnyPermission(Jenkins.SYSTEM_READ, Jenkins.MANAGE)}.
 *
 * <p>Per-{@link Label}/{@link Computer} load statistics are deliberately left untouched: they have
 * always been reachable with just {@link Jenkins#READ} (see {@link Computer#getTarget()}), consistent
 * with viewing a node's own status page, so tightening them here would be a regression rather than a
 * fix. See {@code LoadStatisticsPerNodeAccessUnchangedTest} for that regression guard.
 */
@WithJenkins
class OverallLoadStatisticsReadOnlyModeTest {

    private JenkinsRule j;
    private HudsonPrivateSecurityRealm realm;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
    }

    @Test
    void systemReadViewerCanReadOverallLoadData() throws Exception {
        realm.createAccount("viewer", "viewer");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to("viewer"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("viewer", "viewer");

        Page api = wc.goTo("overallLoad/api/json", "application/json");
        assertEquals(200, api.getWebResponse().getStatusCode());

        Page graph = wc.goTo("overallLoad/graph?type=min", "image/png");
        assertEquals(200, graph.getWebResponse().getStatusCode());
    }

    @Test
    void manageViewerCanReadOverallLoadData() throws Exception {
        realm.createAccount("manager", "manager");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Jenkins.MANAGE).everywhere().to("manager"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("manager", "manager");

        Page api = wc.goTo("overallLoad/api/json", "application/json");
        assertEquals(200, api.getWebResponse().getStatusCode());
    }

    @Test
    void plainReadViewerCannotReadOverallLoadData() throws Exception {
        realm.createAccount("plainReader", "plainReader");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("plainReader"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("plainReader", "plainReader");

        FailingHttpStatusCodeException apiEx = assertThrows(FailingHttpStatusCodeException.class,
                () -> wc.goTo("overallLoad/api/json", "application/json"));
        assertEquals(403, apiEx.getStatusCode());

        FailingHttpStatusCodeException graphEx = assertThrows(FailingHttpStatusCodeException.class,
                () -> wc.goTo("overallLoad/graph?type=min", "image/png"));
        assertEquals(403, graphEx.getStatusCode());
    }

    @Test
    void plainReadViewerCannotReadUnlabeledLoadData() throws Exception {
        realm.createAccount("plainReader", "plainReader");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("plainReader"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("plainReader", "plainReader");

        FailingHttpStatusCodeException apiEx = assertThrows(FailingHttpStatusCodeException.class,
                () -> wc.goTo("unlabeledLoad/api/json", "application/json"));
        assertEquals(403, apiEx.getStatusCode());
    }

    @Test
    void systemReadViewerCanReadUnlabeledLoadData() throws Exception {
        realm.createAccount("viewer", "viewer");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Jenkins.SYSTEM_READ).everywhere().to("viewer"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("viewer", "viewer");

        Page api = wc.goTo("unlabeledLoad/api/json", "application/json");
        assertEquals(200, api.getWebResponse().getStatusCode());
    }
}
