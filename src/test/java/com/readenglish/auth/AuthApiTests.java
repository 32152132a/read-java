package com.readenglish.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.readenglish.common.api.RequestIdFilter;
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
class AuthApiTests {

  @LocalServerPort private int port;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void devLoginIssuesTokenThatCanAccessCurrentUser() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var loginResponse =
          client.send(
              request("/api/v1/auth/dev/login")
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"永庆\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      JsonNode loginBody = objectMapper.readTree(loginResponse.body());
      String accessToken = loginBody.path("data").path("accessToken").stringValue();

      assertThat(loginResponse.statusCode()).isEqualTo(200);
      assertThat(accessToken).isNotBlank();
      assertThat(loginBody.path("data").path("tokenType").stringValue()).isEqualTo("Bearer");

      var meResponse =
          client.send(
              request("/api/v1/users/me")
                  .header("Authorization", "Bearer " + accessToken)
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      JsonNode meBody = objectMapper.readTree(meResponse.body());

      assertThat(meResponse.statusCode()).isEqualTo(200);
      assertThat(meBody.path("data").path("id").stringValue()).isEqualTo("dev-user");
      assertThat(meBody.path("data").path("nickname").stringValue()).isEqualTo("永庆");
      assertThat(meBody.path("data").path("accentPreference").stringValue()).isEqualTo("US");
    }
  }

  @Test
  void protectedEndpointRejectsMissingTokenWithApiEnvelope() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              request("/api/v1/users/me").GET().build(), HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(body.path("code").stringValue()).isEqualTo("AUTH_TOKEN_INVALID");
      assertThat(body.path("requestId").stringValue()).isNotBlank();
      assertThat(response.headers().firstValue(RequestIdFilter.HEADER_NAME))
          .contains(body.path("requestId").stringValue());
    }
  }

  @Test
  void devLoginValidatesNickname() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              request("/api/v1/auth/dev/login")
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());

      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(body.path("code").stringValue()).isEqualTo("VALIDATION_ERROR");
      assertThat(body.path("message").stringValue()).isEqualTo("昵称不能为空");
    }
  }

  @Test
  void unsupportedAccentReturnsStableBusinessError() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var loginResponse =
          client.send(
              request("/api/v1/auth/dev/login")
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString("{\"nickname\":\"永庆\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      String accessToken =
          objectMapper
              .readTree(loginResponse.body())
              .path("data")
              .path("accessToken")
              .stringValue();

      var response =
          client.send(
              request("/api/v1/users/me/preferences")
                  .header("Authorization", "Bearer " + accessToken)
                  .header("Content-Type", "application/json")
                  .method(
                      "PATCH", HttpRequest.BodyPublishers.ofString("{\"accentPreference\":\"UK\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());

      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(body.path("code").stringValue()).isEqualTo("PREFERENCE_NOT_SUPPORTED");
      assertThat(body.path("data").isNull()).isTrue();
    }
  }

  @Test
  void openApiDocumentIsPublic() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(request("/v3/api-docs").GET().build(), HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("永庆发音学习 API");
    }
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path));
  }
}
