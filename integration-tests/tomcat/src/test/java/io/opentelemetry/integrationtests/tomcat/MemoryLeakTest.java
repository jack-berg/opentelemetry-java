package io.opentelemetry.integrationtests.tomcat;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryLeakTest {

  private int port = 8080;

  private String getContextPath() {
    return "";
  }

  private Tomcat tomcatServer;
  private Context servletContext;

  @BeforeEach
  void setup() throws IOException, LifecycleException {
    tomcatServer = new Tomcat();
    File baseDir = new File(
        "/Users/jberg/code/open-telemetry/opentelemetry-java/integration-tests/tomcat/base");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());
    tomcatServer.setPort(port);
    tomcatServer.getConnector();

    servletContext =
        tomcatServer.addContext(getContextPath(), new File(".").getAbsolutePath());

    tomcatServer.start();
  }

  @AfterEach
  void cleanup() throws LifecycleException {
    tomcatServer.stop();
    tomcatServer.destroy();
  }

  @Test
  void test() throws InterruptedException, IOException {

    for (int i = 0; i < 2; i++) {
      String path = "/path_" + i;
      TestServlet testServlet = addTestServletAndExecuteRequest(i, path);
      executeRequests(path);
      removeServlet(testServlet, path);

      Thread.sleep(1000);
    }
  }

  private TestServlet addTestServletAndExecuteRequest(int i, String path) {
    TestServlet testServlet = new TestServlet();
    Tomcat.addServlet(servletContext, "testServlet" + i, testServlet);
    servletContext.addServletMappingDecoded(path, "testServlet" + i);
    return testServlet;
  }

  private void removeServlet(TestServlet testServlet, String path) {
    servletContext.removeServletMapping(path);
    testServlet.destroy();
  }

  private void executeRequests(String path) throws InterruptedException, IOException {
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    HttpClient client = HttpClient.newBuilder()
        .executor(executorService)
        .build();

    for (int i = 0; i < 3; i++) {
      HttpResponse<String> response = client.send(
          HttpRequest.newBuilder().GET().uri(URI.create("http://localhost:8080/" + path)).build(),
          HttpResponse.BodyHandlers.ofString());
      System.out.println(response.statusCode() + ": " + response.body());
      Thread.sleep(100);
    }

    executorService.shutdown();
  }
}
