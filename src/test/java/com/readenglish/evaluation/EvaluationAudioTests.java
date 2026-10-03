package com.readenglish.evaluation;

import static org.assertj.core.api.Assertions.*;

import com.readenglish.common.api.ApiException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class EvaluationAudioTests {
  static byte[] wav(int milliseconds, int amplitude) {
    int size = milliseconds * 32;
    ByteBuffer b = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
    b.putInt(0x46464952).putInt(36 + size).putInt(0x45564157);
    b.putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1);
    b.putInt(16000).putInt(32000).putShort((short) 2).putShort((short) 16);
    b.putInt(0x61746164).putInt(size);
    for (int i = 0; i < size / 2; i++) b.putShort((short) (Math.sin(i * 0.2) * amplitude));
    return b.array();
  }

  static String encoded(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  @Test
  void acceptsValidShortPcmWav() {
    byte[] bytes = wav(1000, 3000);
    assertThat(EvaluationAudio.decode(encoded(bytes))).isEqualTo(bytes);
  }

  @Test
  void rejectsEmptyForgedTruncatedAndWrongSampleRateAudio() {
    assertThatThrownBy(() -> EvaluationAudio.decode("")).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> EvaluationAudio.decode("not base64!"))
        .isInstanceOf(ApiException.class);
    byte[] truncated = java.util.Arrays.copyOf(wav(1000, 3000), 100);
    assertThatThrownBy(() -> EvaluationAudio.decode(encoded(truncated)))
        .isInstanceOf(ApiException.class);
    byte[] wrongRate = wav(1000, 3000);
    ByteBuffer.wrap(wrongRate).order(ByteOrder.LITTLE_ENDIAN).putInt(24, 44100);
    assertThatThrownBy(() -> EvaluationAudio.decode(encoded(wrongRate)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsSilenceTooShortAndTooLong() {
    assertThatThrownBy(() -> EvaluationAudio.decode(encoded(wav(1000, 0))))
        .isInstanceOfSatisfying(
            ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("AUDIO_SILENT"));
    assertThatThrownBy(() -> EvaluationAudio.decode(encoded(wav(100, 3000))))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> EvaluationAudio.decode(encoded(wav(5010, 3000))))
        .isInstanceOf(ApiException.class);
  }
}
