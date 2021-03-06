/*
 * Copyright 2014-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.cli;

import static com.facebook.buck.util.MoreStringsForTests.equalToIgnoringPlatformNewlines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.json.ObjectMappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

/** Verifies that {@code buck build --build-report} works as intended. */
public class BuildReportIntegrationTest {
  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Rule public TemporaryPaths tmpFolderForBuildReport = new TemporaryPaths();

  @Test
  public void testBuildReportForSuccessfulBuild() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "build_report", tmp).setUp();

    Path buildReport = tmpFolderForBuildReport.getRoot().resolve("build-report.txt");
    workspace
        .runBuckBuild(
            "--build-report",
            buildReport.toAbsolutePath().toString(),
            "//:rule_with_output",
            "//:rule_without_output")
        .assertSuccess();

    assertTrue(Files.exists(buildReport));
    String buildReportContents =
        new String(Files.readAllBytes(buildReport), Charsets.UTF_8).replace("\r\n", "\n");
    assertThat(
        buildReportContents,
        equalToIgnoringPlatformNewlines(
            workspace.getFileContents("expected_successful_build_report.json")));
  }

  @Test
  public void testBuildReportWithFailure() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "build_report", tmp).setUp();

    Path buildReport = tmpFolderForBuildReport.getRoot().resolve("build-report.txt");
    workspace
        .runBuckBuild(
            "--build-report",
            buildReport.toAbsolutePath().toString(),
            "//:rule_with_output",
            "//:failing_rule")
        .assertFailure();

    TestUtils.assertBuildReport(workspace, tmp, buildReport, "expected_failed_build_report.json");
  }

  @Test
  public void testCompilerErrorIsIncluded() throws IOException {
    assumeThat(Platform.detect(), Matchers.not(Platform.WINDOWS));
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "build_report", tmp).setUp();

    Path buildReport = tmpFolderForBuildReport.getRoot().resolve("build-report.txt");
    workspace
        .runBuckBuild(
            "--build-report", buildReport.toAbsolutePath().toString(), "//:failing_c_rule")
        .assertFailure();

    assertTrue(Files.exists(buildReport));
    String buildReportContents =
        new String(Files.readAllBytes(buildReport), Charsets.UTF_8).replace("\r\n", "\n");
    assertThat(buildReportContents, Matchers.containsString("stderr: failure.c"));
    assertThat(buildReportContents, Matchers.containsString("failure.c:2:3"));
  }

  @Test
  public void testOutputPathRelativeToRootCell() throws IOException {
    assumeThat(Platform.detect(), Matchers.not(Platform.WINDOWS));
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenarioWithoutDefaultCell(
            this, "build_report_with_cells", tmp);
    workspace.setUp();

    Path cell1Root = workspace.getPath("cell1");
    Path buildReport = tmpFolderForBuildReport.getRoot().resolve("build-report.txt");

    ProcessResult buildResult =
        workspace.runBuckCommand(
            cell1Root,
            "build",
            "--build-report",
            buildReport.toAbsolutePath().toString(),
            "cell2//:bar");
    buildResult.assertSuccess();

    assertTrue(Files.exists(buildReport));
    JsonNode reportRoot = ObjectMappers.READER.readTree(ObjectMappers.createParser(buildReport));
    assertEquals(
        "buck-out/cells/cell2/gen/bar/bar.txt",
        reportRoot.get("results").get("cell2//:bar").get("output").textValue());
  }
}
