package com.readenglish.evaluation;

import com.readenglish.common.api.ApiException;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import org.springframework.http.HttpStatus;

final class EvaluationAudio {
  private EvaluationAudio() {}

  static byte[] decode(String encoded) {
    if (encoded == null || encoded.length() > 230000) throw invalid();
    byte[] wav;
    try {
      wav = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException ex) {
      throw invalid();
    }
    if (wav.length < 44 || wav.length > 170000) throw invalid();
    ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
    if (header.getInt(0) != 0x46464952
        || header.getInt(8) != 0x45564157
        || Integer.toUnsignedLong(header.getInt(4)) != wav.length - 8L) throw invalid();
    try (var stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
      AudioFormat format = stream.getFormat();
      if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())
          || format.getSampleRate() != 16000
          || format.getSampleSizeInBits() != 16
          || format.getChannels() != 1
          || format.isBigEndian()
          || format.getFrameSize() != 2) {
        throw invalid();
      }
      byte[] pcm = stream.readAllBytes();
      if (pcm.length < 9600
          || pcm.length > 160000
          || pcm.length % 2 != 0
          || stream.getFrameLength() * 2 != pcm.length) throw invalid();
      long energy = 0;
      ByteBuffer samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
      while (samples.hasRemaining()) {
        int sample = samples.getShort();
        energy += (long) sample * sample;
      }
      if (Math.sqrt((double) energy / (pcm.length / 2)) < 40) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_SILENT", "录音声音太小，请靠近麦克风重新录制");
      }
      return wav;
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw invalid();
    }
  }

  private static ApiException invalid() {
    return new ApiException(
        HttpStatus.BAD_REQUEST, "INVALID_AUDIO", "请上传 0.3 至 5 秒、16kHz、16bit 单声道 WAV 录音");
  }
}
