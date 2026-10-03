package com.readenglish.evaluation;

import com.readenglish.common.api.ApiException;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TencentSoeEvaluationGateway implements OralEvaluationGateway {
  private final String appId;
  private final String secretId;
  private final String secretKey;
  private final boolean enabled;
  private final ObjectMapper mapper;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public TencentSoeEvaluationGateway(
      @Value("${TENCENT_SOE_APP_ID:}") String appId,
      @Value("${TENCENT_SOE_SECRET_ID:}") String secretId,
      @Value("${TENCENT_SOE_SECRET_KEY:}") String secretKey,
      @Value("${TENCENT_SOE_ENABLED:false}") boolean enabled,
      ObjectMapper mapper) {
    this.appId = appId;
    this.secretId = secretId;
    this.secretKey = secretKey;
    this.enabled = enabled;
    this.mapper = mapper;
  }

  @Override
  public boolean available() {
    return enabled && appId.matches("[0-9]+") && !secretId.isBlank() && !secretKey.isBlank();
  }

  @Override
  public EvaluationModels.Score evaluate(String word, byte[] wav) {
    if (!available()) throw failure("EVALUATION_NOT_CONFIGURED");
    String voiceId = UUID.randomUUID().toString();
    Receiver receiver = new Receiver(mapper, voiceId);
    WebSocket socket = null;
    CompletableFuture<WebSocket> connecting = null;
    try {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
      connecting =
          client
              .newWebSocketBuilder()
              .connectTimeout(Duration.ofSeconds(5))
              .buildAsync(
                  signedUri(
                      word,
                      voiceId,
                      Instant.now().getEpochSecond(),
                      new SecureRandom().nextInt(1, Integer.MAX_VALUE)),
                  receiver);
      socket = connecting.get(5, TimeUnit.SECONDS);
      receiver.ready.get(remaining(deadline), TimeUnit.NANOSECONDS);
      // rec_mode=1 accepts the entire recording as one binary message after authentication.
      socket.sendBinary(ByteBuffer.wrap(wav), true).get(remaining(deadline), TimeUnit.NANOSECONDS);
      socket.sendText("{\"type\":\"end\"}", true).get(remaining(deadline), TimeUnit.NANOSECONDS);
      return receiver.finished.get(remaining(deadline), TimeUnit.NANOSECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw failure("EVALUATION_INTERRUPTED");
    } catch (TimeoutException ex) {
      throw failure("EVALUATION_TIMEOUT");
    } catch (Exception ex) {
      Throwable cause = ex;
      while (cause.getCause() != null && !(cause instanceof ApiException)) cause = cause.getCause();
      if (cause instanceof ApiException api) throw api;
      throw failure("EVALUATION_UNAVAILABLE");
    } finally {
      if (socket != null) socket.abort();
      else if (connecting != null) connecting.thenAccept(WebSocket::abort);
    }
  }

  URI signedUri(String word, String voiceId, long timestamp, int nonce) {
    Map<String, String> parameters = new TreeMap<>();
    parameters.put("secretid", secretId);
    parameters.put("timestamp", Long.toString(timestamp));
    parameters.put("expired", Long.toString(timestamp + 300));
    parameters.put("nonce", Integer.toString(nonce));
    parameters.put("server_engine_type", "16k_en");
    parameters.put("voice_id", voiceId);
    parameters.put("voice_format", "1");
    parameters.put("text_mode", "0");
    parameters.put("ref_text", word);
    parameters.put("eval_mode", "0");
    parameters.put("score_coeff", "1.5");
    parameters.put("sentence_info_enabled", "1");
    parameters.put("rec_mode", "1");
    String path = "soe.cloud.tencent.com/soe/api/" + appId;
    String query =
        parameters.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
      parameters.put(
          "signature",
          Base64.getEncoder()
              .encodeToString(mac.doFinal((path + "?" + query).getBytes(StandardCharsets.UTF_8))));
    } catch (Exception ex) {
      throw failure("EVALUATION_NOT_CONFIGURED");
    }
    return URI.create(
        "wss://"
            + path
            + "?"
            + parameters.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&")));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static long remaining(long deadline) throws TimeoutException {
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) throw new TimeoutException();
    return remaining;
  }

  @PreDestroy
  void close() {
    client.shutdownNow();
  }

  static final class Receiver implements WebSocket.Listener {
    final CompletableFuture<Void> ready = new CompletableFuture<>();
    final CompletableFuture<EvaluationModels.Score> finished = new CompletableFuture<>();
    private final ObjectMapper mapper;
    private final String voiceId;
    private final StringBuilder buffer = new StringBuilder();
    private JsonNode latest;

    Receiver(ObjectMapper mapper, String voiceId) {
      this.mapper = mapper;
      this.voiceId = voiceId;
    }

    @Override
    public void onOpen(WebSocket socket) {
      socket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
      try {
        if (buffer.length() + data.length() > 131072) throw failure("EVALUATION_INVALID_RESPONSE");
        buffer.append(data);
        if (last) {
          accept(mapper.readTree(buffer.toString()));
          buffer.setLength(0);
        }
      } catch (Exception ex) {
        reject(ex instanceof ApiException api ? api : failure("EVALUATION_INVALID_RESPONSE"));
        socket.abort();
      }
      socket.request(1);
      return null;
    }

    void accept(JsonNode message) {
      if (!message.path("code").isNumber()) throw failure("EVALUATION_INVALID_RESPONSE");
      int code = message.path("code").asInt();
      if (code != 0)
        throw failure(
            switch (code) {
              case 4002, 4003 -> "EVALUATION_NOT_CONFIGURED";
              case 4004, 4005 -> "EVALUATION_QUOTA_EXHAUSTED";
              case 4006 -> "EVALUATION_BUSY";
              case 4007, 4107 -> "INVALID_AUDIO";
              case 4105, 4108 -> "AUDIO_NO_SPEECH";
              case 4102, 4103, 4104, 4110, 4111, 4113, 4114, 4115 -> "EVALUATION_UNSUPPORTED_WORD";
              default -> "EVALUATION_UNAVAILABLE";
            });
      if (!voiceId.equals(message.path("voice_id").asText()))
        throw failure("EVALUATION_INVALID_RESPONSE");
      if (message.hasNonNull("result")) {
        JsonNode result = message.get("result");
        if (result.isString()) result = mapper.readTree(result.asText());
        if (!result.isObject()) throw failure("EVALUATION_INVALID_RESPONSE");
        latest = result;
      }
      ready.complete(null);
      if (message.path("final").asInt() == 1) finished.complete(normalize(latest, voiceId));
    }

    @Override
    public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
      if (!finished.isDone()) reject(failure("EVALUATION_UNAVAILABLE"));
      return null;
    }

    @Override
    public void onError(WebSocket socket, Throwable error) {
      reject(failure("EVALUATION_UNAVAILABLE"));
    }

    private void reject(ApiException error) {
      ready.completeExceptionally(error);
      finished.completeExceptionally(error);
    }
  }

  static EvaluationModels.Score normalize(JsonNode result, String voiceId) {
    if (result == null) throw failure("EVALUATION_INVALID_RESPONSE");
    Double accuracy = score(result.get("PronAccuracy"));
    if (accuracy == null) throw failure("EVALUATION_INVALID_RESPONSE");
    var phonemes = new ArrayList<EvaluationModels.Phoneme>();
    for (JsonNode word : result.path("Words")) {
      JsonNode phones = word.has("PhoneInfo") ? word.path("PhoneInfo") : word.path("PhoneInfos");
      for (JsonNode phone : phones) {
        if (phonemes.size() >= 64) break;
        phonemes.add(
            new EvaluationModels.Phoneme(
                phone.path("ReferencePhone").asText(""),
                phone.path("Phone").asText(""),
                score(phone.get("PronAccuracy")),
                bool(phone.get("Stress")),
                bool(phone.get("DetectedStress"))));
      }
    }
    return new EvaluationModels.Score(
        score(result.get("SuggestedScore")), accuracy, phonemes, voiceId);
  }

  private static Boolean bool(JsonNode value) {
    return value != null && value.isBoolean() ? value.asBoolean() : null;
  }

  private static Double score(JsonNode value) {
    if (value == null || !value.isNumber()) return null;
    double number = value.asDouble();
    if (!Double.isFinite(number) || number < -1 || number > 100) return null;
    return Math.round(Math.max(0, number) * 10) / 10.0;
  }

  private static ApiException failure(String code) {
    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, "发音评测暂时不可用，请稍后重新录制");
  }
}
