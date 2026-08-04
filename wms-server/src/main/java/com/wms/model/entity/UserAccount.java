package com.wms.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_accounts")
public class UserAccount extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String username;
    @Column(nullable = false) private String password;
    @Column(nullable = false, length = 30) private String role = "WAREHOUSE";
    @Column(nullable = false) private Boolean enabled = true;
    private String displayName;
    @Column(unique = true, length = 64) private String openid;
    public Long getId(){return id;} public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getPassword(){return password;} public void setPassword(String v){password=v;} public String getRole(){return role;} public void setRole(String v){role=v;}
    public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean v){enabled=v;} public String getDisplayName(){return displayName;} public void setDisplayName(String v){displayName=v;}
    public String getOpenid(){return openid;} public void setOpenid(String v){openid=v;}
}
