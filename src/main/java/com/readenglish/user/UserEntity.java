package com.readenglish.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserEntity {

  @Id private String id;

  @Column(name = "wechat_openid", unique = true)
  private String wechatOpenid;

  private String unionid;

  @Column(nullable = false, length = 40)
  private String nickname;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Column(nullable = false, length = 8)
  private String accent;

  protected UserEntity() {}

  public UserEntity(String id, String nickname) {
    this.id = id;
    this.nickname = nickname;
    this.accent = "US";
  }

  public UserEntity(String id, String wechatOpenid, String unionid, String nickname) {
    this(id, nickname);
    this.wechatOpenid = wechatOpenid;
    this.unionid = unionid;
  }

  public String getId() {
    return id;
  }

  public String getNickname() {
    return nickname;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public String getAccent() {
    return accent;
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }

  public void updateAccent(String accent) {
    this.accent = accent;
  }
}
