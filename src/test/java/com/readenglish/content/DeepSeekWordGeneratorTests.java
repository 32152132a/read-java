package com.readenglish.content;

import static org.assertj.core.api.Assertions.*;

import com.readenglish.common.api.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DeepSeekWordGeneratorTests {
  @Test
  void providerRequestIsNonStreamingAndRejectsInvalidOrTruncatedOutput() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ContentConfig configs = new ContentConfig(mapper);
    AtomicReference<String> response = new AtomicReference<>();
    AtomicReference<String> request = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var generator =
          new DeepSeekWordGenerator(
              "test-key",
              "http://127.0.0.1:" + server.getAddress().getPort(),
              "test-model",
              mapper,
              configs);
      var result = mapper.createObjectNode();
      var choice = result.putArray("choices").addObject().put("finish_reason", "stop");
      var generatedConfig = configs.emptyWord("computer", "/test/", "电脑", "");
      var silentPart =
          generatedConfig
              .withArray("parts")
              .addObject()
              .put("letters", "computer")
              .put("tip", "静音片段测试");
      silentPart.putArray("segments");
      choice.putObject("message").put("content", generatedConfig.toString());
      response.set(result.toString());
      var generated = generator.generate("computer");
      assertThat(generated.path("word").asText()).isEqualTo("computer");
      assertThat(generated.path("ipa").asText()).isEqualTo("test");
      assertThat(generated.path("ipaSegments").get(0).path("text").asText()).isEqualTo("test");
      var body = mapper.readTree(request.get());
      assertThat(body.path("stream").asBoolean()).isFalse();
      assertThat(body.path("thinking").path("type").asText()).isEqualTo("disabled");
      assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
      choice.put("finish_reason", "length");
      response.set(result.toString());
      assertThatThrownBy(() -> generator.generate("computer")).isInstanceOf(ApiException.class);
      choice.put("finish_reason", "stop");
      choice.withObject("message").put("content", "{}");
      response.set(result.toString());
      assertThatThrownBy(() -> generator.generate("computer")).isInstanceOf(ApiException.class);
    } finally {
      server.stop(0);
    }
  }
}
