package com.readenglish.integration.wechat;

import com.readenglish.integration.wechat.WechatLoginGateway.WechatIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app.integration.wechat",
    name = "fake-enabled",
    havingValue = "true")
public class FakeWechatLoginGateway implements WechatLoginGateway {

  @Override
  public WechatIdentity exchange(String code) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(code.trim().getBytes(StandardCharsets.UTF_8));
      return new WechatIdentity("local_" + HexFormat.of().formatHex(digest, 0, 12), null);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 不可用", exception);
    }
  }
}
