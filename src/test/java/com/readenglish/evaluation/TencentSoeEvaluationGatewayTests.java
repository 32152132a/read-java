package com.readenglish.evaluation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.readenglish.common.api.ApiException;
import java.net.URLDecoder;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TencentSoeEvaluationGatewayTests {
  final ObjectMapper mapper = new ObjectMapper();

  @Test
  void signatureCoversSortedRawParametersAndEscapesSpecialCharacters() throws Exception {
    var gateway = new TencentSoeEvaluationGateway("12345", "secret-id", "test-key", true, mapper);
    var uri = gateway.signedUri("can't", "voice-1", 1700000000, 1234);
    var values = new TreeMap<String, String>();
    Arrays.stream(uri.getRawQuery().split("&"))
        .forEach(
            item -> {
              var pair = item.split("=", 2);
              values.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            });
    String signature = values.remove("signature");
    assertThat(uri.getScheme()).isEqualTo("wss");
    assertThat(values)
        .containsEntry("rec_mode", "1")
        .containsEntry("voice_format", "1")
        .containsEntry("ref_text", "can't")
        .containsEntry("server_engine_type", "16k_en");
    // Independent fixture generated with the documented HMAC-SHA1 protocol.
    assertThat(signature).isEqualTo("9u+cRqDOHFAHUnzTX9fjBWFzeRM=");
    gateway.close();
  }

  @Test
  void fragmentedIntermediateResultSurvivesEmptyFinalMessage() {
    var receiver = new TencentSoeEvaluationGateway.Receiver(mapper, "voice");
    var socket = mock(WebSocket.class);
    receiver.onOpen(socket);
    receiver.onText(socket, "{\"code\":0,\"voice_id\":\"voice\"}", true);
    assertThat(receiver.ready).isCompleted();
    receiver.onText(socket, "{\"code\":0,\"voice_id\":\"voice\",\"result\":", false);
    receiver.onText(
        socket,
        "{\"PronAccuracy\":83.25,\"SuggestedScore\":81,\"Words\":[{\"PhoneInfo\":[{\"Phone\":\"hh\",\"PronAccuracy\":-1}]}]}}",
        true);
    assertThat(receiver.finished).isNotDone();
    receiver.onText(socket, "{\"code\":0,\"voice_id\":\"voice\",\"final\":1}", true);
    var score = receiver.finished.join();
    assertThat(score.accuracy()).isEqualTo(83.3);
    assertThat(score.phonemes().getFirst().accuracy()).isZero();
    assertThat(score.phonemes().getFirst().expectedStress()).isNull();
  }

  @Test
  void supportsJsonEncodedResultButDoesNotInventMissingScores() {
    var receiver = new TencentSoeEvaluationGateway.Receiver(mapper, "voice");
    receiver.accept(
        mapper.readTree(
            "{\"code\":0,\"voice_id\":\"voice\",\"result\":\"{\\\"PronAccuracy\\\":90}\",\"final\":1}"));
    assertThat(receiver.finished.join().overall()).isNull();
    assertThatThrownBy(() -> TencentSoeEvaluationGateway.normalize(mapper.readTree("{}"), "voice"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void errorsEarlyCloseAndOversizedMessagesCompleteWaiters() {
    var receiver = new TencentSoeEvaluationGateway.Receiver(mapper, "voice");
    var socket = mock(WebSocket.class);
    receiver.onText(socket, "{\"code\":4002,\"message\":\"secret supplier detail\"}", true);
    assertThat(receiver.ready).isCompletedExceptionally();
    assertThatThrownBy(receiver.finished::join)
        .hasRootCauseInstanceOf(ApiException.class)
        .satisfies(
            ex -> assertThat(ex.getCause().getMessage()).doesNotContain("secret supplier detail"));
    var closed = new TencentSoeEvaluationGateway.Receiver(mapper, "voice");
    closed.onClose(socket, 1000, "");
    assertThat(closed.finished).isCompletedExceptionally();
    var large = new TencentSoeEvaluationGateway.Receiver(mapper, "voice");
    large.onText(socket, "x".repeat(131073), false);
    assertThat(large.finished).isCompletedExceptionally();
    verify(socket, atLeastOnce()).abort();
  }

  @Test
  void missingCredentialsCannotProduceFakeScores() {
    var gateway = new TencentSoeEvaluationGateway("", "", "", false, mapper);
    assertThat(gateway.available()).isFalse();
    assertThatThrownBy(() -> gateway.evaluate("hello", new byte[0]))
        .isInstanceOf(ApiException.class);
    gateway.close();
  }
}
