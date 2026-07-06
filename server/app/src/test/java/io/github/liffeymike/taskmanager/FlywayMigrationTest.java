package io.github.liffeymike.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FlywayMigrationTest extends AbstractIntegrationTest {

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  void baselineMigrationApplied() {
    Integer applied = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) from flyway_schema_history WHERE success = true", Integer.class);
    assertThat(applied).isGreaterThanOrEqualTo(1);
  }

}
