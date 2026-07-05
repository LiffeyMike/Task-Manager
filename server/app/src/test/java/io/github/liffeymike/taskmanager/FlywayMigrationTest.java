package io.github.liffeymike.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class FlywayMigrationTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  void baselineMigrationApplied() {
    Integer applied = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) from flyway_schema_history WHERE success = true", Integer.class);
    assertThat(applied).isGreaterThanOrEqualTo(1);
  }

}
