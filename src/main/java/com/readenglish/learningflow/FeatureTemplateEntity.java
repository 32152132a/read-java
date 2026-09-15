package com.readenglish.learningflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "feature_templates")
public class FeatureTemplateEntity {

  @Id private String code;

  @Column(nullable = false)
  private String name;

  @Column(name = "short_name", nullable = false)
  private String shortName;

  @Column(nullable = false)
  private String route;

  @Column(nullable = false)
  private String icon;

  @Column(nullable = false)
  private boolean enabled;

  @Column(nullable = false)
  private boolean repeatable;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected FeatureTemplateEntity() {}

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getShortName() {
    return shortName;
  }

  public String getRoute() {
    return route;
  }

  public String getIcon() {
    return icon;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isRepeatable() {
    return repeatable;
  }
}
