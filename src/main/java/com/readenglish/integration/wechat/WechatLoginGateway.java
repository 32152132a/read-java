package com.readenglish.integration.wechat;

public interface WechatLoginGateway {

  WechatIdentity exchange(String code);

  record WechatIdentity(String openid, String unionid) {}
}
