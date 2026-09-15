package com.readenglish.integration.wechat;

import com.readenglish.common.api.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app.integration.wechat",
    name = "fake-enabled",
    havingValue = "false",
    matchIfMissing = true)
public class UnavailableWechatLoginGateway implements WechatLoginGateway {

  @Override
  public WechatIdentity exchange(String code) {
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_LOGIN_FAILED", "微信登录暂未配置");
  }
}
