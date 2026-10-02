package com.readenglish.integration.wechat;

import com.readenglish.common.api.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnExpression(
    "'${app.integration.wechat.fake-enabled:false}' == 'false' && '${app.integration.wechat.app-id:}' != ''")
public class RealWechatLoginGateway implements WechatLoginGateway {

  private static final Logger log = LoggerFactory.getLogger(RealWechatLoginGateway.class);

  private final String appId;
  private final String appSecret;
  private final String endpoint;
  private final Duration timeout;
  private final ObjectMapper objectMapper;
  private final HttpClient client;

  public RealWechatLoginGateway(
      @Value("${app.integration.wechat.app-id}") String appId,
      @Value("${app.integration.wechat.app-secret:}") String appSecret,
      @Value(
              "${app.integration.wechat.code2-session-url:https://api.weixin.qq.com/sns/jscode2session}")
          String endpoint,
      @Value("${app.integration.wechat.timeout:PT5S}") Duration timeout,
      ObjectMapper objectMapper) {
    this.appId = appId.trim();
    this.appSecret = appSecret.trim();
    this.endpoint = endpoint;
    this.timeout = timeout;
    this.objectMapper = objectMapper;
    this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  @Override
  public WechatIdentity exchange(String code) {
    if (appId.isBlank() || appSecret.isBlank()) {
      throw loginFailed("微信登录暂未配置");
    }

    try {
      var request = HttpRequest.newBuilder(buildUri(code)).timeout(timeout).GET().build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        log.warn("Wechat code2Session failed with HTTP status {}", response.statusCode());
        throw loginFailed("微信登录失败，请稍后重试");
      }
      return parse(response.body());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw loginFailed("微信登录失败，请稍后重试");
    } catch (IOException exception) {
      log.warn("Wechat code2Session request failed: {}", diagnostic(exception));
      throw loginFailed("微信登录失败，请稍后重试");
    }
  }

  private URI buildUri(String code) {
    String separator = endpoint.contains("?") ? "&" : "?";
    String query =
        "appid="
            + encode(appId)
            + "&secret="
            + encode(appSecret)
            + "&js_code="
            + encode(code)
            + "&grant_type=authorization_code";
    return URI.create(endpoint + separator + query);
  }

  private WechatIdentity parse(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode errcodeNode = root.path("errcode");
      if (!errcodeNode.isMissingNode() && errcodeNode.intValue() != 0) {
        int errcode = errcodeNode.intValue();
        log.warn("Wechat code2Session rejected code, errcode={}", errcode);
        throw loginFailed("微信登录失败，请重新登录");
      }
      String openid = root.path("openid").asText("").trim();
      if (openid.isBlank()) {
        log.warn("Wechat code2Session response missed openid");
        throw loginFailed("微信登录失败，请稍后重试");
      }
      String unionid = root.path("unionid").asText("").trim();
      return new WechatIdentity(openid, unionid.isBlank() ? null : unionid);
    } catch (ApiException exception) {
      throw exception;
    } catch (Exception exception) {
      log.warn("Wechat code2Session returned invalid response: {}", diagnostic(exception));
      throw loginFailed("微信登录失败，请稍后重试");
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String diagnostic(Exception exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
    String singleLine = message.replaceAll("[\\r\\n]+", " ");
    return singleLine.substring(0, Math.min(singleLine.length(), 160));
  }

  private static ApiException loginFailed(String message) {
    return new ApiException(HttpStatus.UNAUTHORIZED, "WECHAT_LOGIN_FAILED", message);
  }
}
