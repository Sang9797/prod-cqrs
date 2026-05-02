package com.company.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class PermissionJpaEntity {

  @Id
  @Column(name = "permission_id")
  private String permissionId;

  @Column(nullable = false, unique = true)
  private String name;

  private String description;

  protected PermissionJpaEntity() {}

  public String getPermissionId() {
    return permissionId;
  }

  public String getName() {
    return name;
  }
}
