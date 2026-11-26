package com.checkout.payment.gateway.component;

import java.nio.file.Path;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Starts the Mountebank {@code bank_simulator} and the {@code payment_gateway} Docker containers on
 * a shared Docker network once for the Cucumber component test suite.
 */
public final class ContainerEnvironment {

  private static final int BANK_PORT = 8080;
  private static final int GATEWAY_PORT = 8090;

  private static Network network;
  private static GenericContainer<?> bankSimulator;
  private static GenericContainer<?> paymentGateway;

  private ContainerEnvironment() {
  }

  /** Starts containers if not already running and returns the gateway base URL. */
  public static synchronized String startAndGetGatewayBaseUrl() {
    if (paymentGateway == null) {
      network = Network.newNetwork();

      bankSimulator = new GenericContainer<>(DockerImageName.parse("bbyars/mountebank:2.8.1"))
          .withNetwork(network)
          .withNetworkAliases("bank_simulator")
          .withExposedPorts(BANK_PORT)
          .withCopyFileToContainer(
              MountableFile.forHostPath(Path.of("imposters/bank_simulator.ejs")),
              "/imposters/bank_simulator.ejs")
          .withCommand("--configfile /imposters/bank_simulator.ejs --allowInjection")
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));
      bankSimulator.start();

      ImageFromDockerfile gatewayImage = new ImageFromDockerfile(
          "payment-gateway-component-test",
          false)
          .withFileFromPath("Dockerfile", Path.of("Dockerfile"))
          .withFileFromPath("gradlew", Path.of("gradlew"))
          .withFileFromPath("settings.gradle", Path.of("settings.gradle"))
          .withFileFromPath("build.gradle", Path.of("build.gradle"))
          .withFileFromPath("gradle", Path.of("gradle"))
          .withFileFromPath("src", Path.of("src"));

      paymentGateway = new GenericContainer<>(gatewayImage)
          .withNetwork(network)
          .withExposedPorts(GATEWAY_PORT)
          .withEnv("BANK_SIMULATOR_BASE_URL", "http://bank_simulator:" + BANK_PORT)
          .dependsOn(bankSimulator)
          .waitingFor(Wait.forHttp("/v3/api-docs")
              .forStatusCode(200)
              .withStartupTimeout(Duration.ofSeconds(60)));
      paymentGateway.start();

      Runtime.getRuntime().addShutdownHook(new Thread(ContainerEnvironment::stop));
    }
    return "http://" + paymentGateway.getHost() + ":" + paymentGateway.getMappedPort(GATEWAY_PORT);
  }

  /** Stops both containers and closes the shared Docker network. */
  public static synchronized void stop() {
    if (paymentGateway != null) {
      paymentGateway.stop();
      paymentGateway = null;
    }
    if (bankSimulator != null) {
      bankSimulator.stop();
      bankSimulator = null;
    }
    if (network != null) {
      network.close();
      network = null;
    }
  }
}
