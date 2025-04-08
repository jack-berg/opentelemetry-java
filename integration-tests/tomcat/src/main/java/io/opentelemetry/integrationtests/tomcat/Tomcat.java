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

class Tomcat {

  private static final Logger logger = Logger.getLogger(Tomcat.class.getName());

  public static void main(String[] args)
      throws IOException, LifecycleException, InterruptedException {
    org.apache.catalina.startup.Tomcat tomcatServer = new org.apache.catalina.startup.Tomcat();
    File baseDir =
        new File(
            "/Users/jberg/code/open-telemetry/opentelemetry-java/integration-tests/tomcat/base");
    tomcatServer.setBaseDir(baseDir.getAbsolutePath());
    tomcatServer.setPort(8080);
    tomcatServer.getConnector();

    tomcatServer.addContext("", new File(".").getAbsolutePath());

    tomcatServer.start();

    tomcatServer.addWebapp(
        "/path",
        "/Users/jberg/code/open-telemetry/opentelemetry-java/integration-tests/tomcat-app/build/libs/opentelemetry-tomcat-app-1.50.0-SNAPSHOT.war");

    String path = "/path/hello";

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

    tomcatServer.stop();
    tomcatServer.destroy();
  }

  private Tomcat() {}
}
