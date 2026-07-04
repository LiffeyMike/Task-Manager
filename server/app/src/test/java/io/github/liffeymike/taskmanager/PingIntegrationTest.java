package io.github.liffeymike.taskmanager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureHttpGraphQlTester
class PingIntegrationTest {

  @Autowired
  HttpGraphQlTester graphQlTester;

  @Test
  void pingReturnsPong() {
    graphQlTester.document("{ ping }")
        .execute()
        .path("ping")
        .entity(String.class)
        .isEqualTo("pong");
  }
}
