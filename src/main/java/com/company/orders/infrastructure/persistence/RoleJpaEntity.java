package com.company.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class RoleJpaEntity {

  @Id
  @Column(name = "role_id")
  private String roleId;

  @Column(nullable = false, unique = true)
  private String name;

  private String description;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<PermissionJpaEntity> permissions = new HashSet<>();

  protected RoleJpaEntity() {}

  public String getRoleId() {
    return roleId;
  }

  public String getName() {
    return name;
  }

  public Set<PermissionJpaEntity> getPermissions() {
    return permissions;
  }
}
