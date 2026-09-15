package com.readenglish.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiContractTests {

  @LocalServerPort private int port;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void healthUsesResponseEnvelopeAndReturnsRequestId() throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/api/v1/health"))
            .header(RequestIdFilter.HEADER_NAME, "client-provided-value")
            .GET()
            .build();

    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(body.path("code").stringValue()).isEqualTo("OK");
      assertThat(body.path("message").stringValue()).isEqualTo("success");
      assertThat(body.path("data").path("status").stringValue()).isEqualTo("UP");
      assertThat(body.path("requestId").stringValue()).isNotBlank();
      assertThat(response.headers().firstValue(RequestIdFilter.HEADER_NAME))
          .contains(body.path("requestId").stringValue());
      assertThat(body.path("requestId").stringValue()).isNotEqualTo("client-provided-value");
    }
  }
}
