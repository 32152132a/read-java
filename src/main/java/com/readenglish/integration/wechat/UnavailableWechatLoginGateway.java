package com.readenglish.integration.wechat;

import com.readenglish.common.api.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(WechatLoginGateway.class)
public class UnavailableWechatLoginGateway implements WechatLoginGateway {

  @Override
  public WechatIdentity exchange(String code) {
    throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_LOGIN_FAILED", "微信登录暂未配置");
  }
}
