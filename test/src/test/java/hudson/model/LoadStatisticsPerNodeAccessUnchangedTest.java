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

import hudson.security.HudsonPrivateSecurityRealm;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Regression guard for JENKINS-62432: per-{@link Label}/{@link Computer} load statistics must
 * remain reachable with only {@link Jenkins#READ}, exactly as before. Only the root-level
 * {@link Jenkins#overallLoad}/{@link Jenkins#unlabeledLoad} instances (see
 * {@link OverallLoadStatisticsReadOnlyModeTest}) were tightened to require
 * {@link Jenkins#SYSTEM_READ}/{@link Jenkins#MANAGE} - {@link LoadStatistics#doGraph} and
 * {@link LoadStatistics#getApi} themselves were deliberately left unmodified so that the built-in
 * {@code label/&lt;name&gt;/load-statistics} and {@code computer/&lt;name&gt;/load-statistics} pages,
 * which have never required more than {@link Jenkins#READ} to view a node's own status, keep working
 * unchanged for ordinary users.
 */
@WithJenkins
class LoadStatisticsPerNodeAccessUnchangedTest {

    @Issue("JENKINS-62432")
    @Test
    void plainReadViewerCanStillReadLabelLoadStatisticsData(JenkinsRule j) throws Exception {
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("plainReader", "plainReader");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().to("plainReader"));

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.login("plainReader", "plainReader");

        Label builtIn = j.jenkins.getSelfLabel();
        Page api = wc.goTo("label/" + builtIn.getName() + "/loadStatistics/api/json", "application/json");
        assertEquals(200, api.getWebResponse().getStatusCode(),
                "a plain Overall/Read user must keep being able to view a label's own load statistics");
    }
}
