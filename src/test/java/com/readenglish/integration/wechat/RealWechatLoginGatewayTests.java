package com.readenglish.integration.wechat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.readenglish.common.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RealWechatLoginGatewayTests {

  private HttpServer server;
  private String lastQuery = "";

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void exchangesCodeForOpenid() throws Exception {
    var gateway = gatewayReturning("{\"openid\":\"openid-123\",\"unionid\":\"union-456\"}");

    var identity = gateway.exchange("temporary-code");

    assertThat(identity.openid()).isEqualTo("openid-123");
    assertThat(identity.unionid()).isEqualTo("union-456");
    assertThat(lastQuery).contains("appid=app-id");
    assertThat(lastQuery).contains("grant_type=authorization_code");
    assertThat(lastQuery).contains("js_code=temporary-code");
  }

  @Test
  void mapsWechatErrorToBusinessError() throws Exception {
    var gateway =
        gatewayReturning("{\"errcode\":40029,\"errmsg\":\"invalid code, rid: test-request\"}");

    assertThatThrownBy(() -> gateway.exchange("bad-code"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> {
              assertThat(exception.getCode()).isEqualTo("WECHAT_LOGIN_FAILED");
              assertThat(exception.getStatus().value()).isEqualTo(401);
            });
  }

  @Test
  void rejectsMissingSecret() {
    var gateway =
        new RealWechatLoginGateway(
            "app-id",
            "",
            "http://127.0.0.1/not-called",
            java.time.Duration.ofSeconds(1),
            new ObjectMapper());

    assertThatThrownBy(() -> gateway.exchange("temporary-code"))
        .isInstanceOfSatisfying(
            ApiException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("WECHAT_LOGIN_FAILED"));
  }

  private RealWechatLoginGateway gatewayReturning(String responseBody) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/sns/jscode2session",
        exchange -> {
          lastQuery = exchange.getRequestURI().getRawQuery();
          byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    return new RealWechatLoginGateway(
        "app-id",
        "app-secret",
        "http://127.0.0.1:" + server.getAddress().getPort() + "/sns/jscode2session",
        java.time.Duration.ofSeconds(1),
        new ObjectMapper());
  }
}
