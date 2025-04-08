/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.integrationtests.tomcat;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;

class MemoryLeakTest {

  private static final Logger logger = Logger.getLogger(MemoryLeakTest.class.getName());

  private static final int port = 8080;

  private static String getContextPath() {
    return "";
  }

  private static Tomcat tomcatServer;

  public static void main(String[] args)
      throws IOException, LifecycleException, InterruptedException {
    tomcatServer = new Tomcat();
    File baseDir =
        new File(
            "/Users/jberg/code/open-telemetry/opentelemetry-java/integration-tests/tomcat/base");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());
    tomcatServer.setPort(port);
    tomcatServer.getConnector();

    tomcatServer.addContext(getContextPath(), new File(".").getAbsolutePath());

    tomcatServer.start();

    test();
  }

  private MemoryLeakTest() {}

  private static void test() throws InterruptedException, IOException, LifecycleException {

    tomcatServer.addWebapp(
        "/path",
        "/Users/jberg/code/open-telemetry/opentelemetry-java/integration-tests/tomcat-app/build/libs/opentelemetry-tomcat-app-1.50.0-SNAPSHOT.war");

    executeRequests("/path/hello");

    tomcatServer.stop();
    tomcatServer.destroy();
    //    for (int i = 0; i < 2; i++) {
    //      String path = "/path_" + i;
    //      TestServlet testServlet = addTestServletAndExecuteRequest(i, path);
    //      executeRequests(path);
    //      removeServlet(testServlet, path);
    //
    //      Thread.sleep(1000);
    //    }
  }

  //  private TestServlet addTestServletAndExecuteRequest(int i, String path) {
  //    TestServlet testServlet = new TestServlet();
  //    Tomcat.addServlet(servletContext, "testServlet" + i, testServlet);
  //    servletContext.addServletMappingDecoded(path, "testServlet" + i);
  //    return testServlet;
  //  }
  //
  //  private void removeServlet(TestServlet testServlet, String path) {
  //    servletContext.removeServletMapping(path);
  //    testServlet.destroy();
  //  }

  private static void executeRequests(String path) throws InterruptedException, IOException {
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    HttpClient client = HttpClient.newBuilder().executor(executorService).build();

    for (int i = 0; i < 3; i++) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder()
                  .GET()
                  .uri(URI.create("http://localhost:8080/" + path))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      logger.info(response.statusCode() + ": " + response.body());
      Thread.sleep(100);
    }

    executorService.shutdown();
  }
}
