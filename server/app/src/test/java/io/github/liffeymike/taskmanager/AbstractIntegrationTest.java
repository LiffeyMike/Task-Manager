package io.github.liffeymike.taskmanager;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for full-context integration tests. Uses the Testcontainers "singleton container"
 * pattern: one Postgres container is started once for the whole test JVM and shared by every
 * subclass, wired into Spring via {@code @ServiceConnection}.
 *
 * <p>It is started in a static initializer (deliberately NOT via {@code @Testcontainers}/
 * {@code @Container}) so its lifecycle is not tied to any single test class — otherwise the shared
 * container would be stopped after the first class finishes, breaking the rest. Testcontainers' Ryuk
 * reaper stops it when the JVM exits. This gives every {@code @SpringBootTest} subclass a real
 * database, which is required now that JPA + Flyway are on the classpath.
 */
abstract class AbstractIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  static {
    postgres.start();
  }
}
