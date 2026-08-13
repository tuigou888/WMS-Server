package com.wms.model.entity.market;

import com.wms.model.entity.AuditableEntity;
import com.wms.model.entity.UserAccount;
import jakarta.persistence.*;

/**
 * 商城客户收货人档案：一小程序登录用户可有多个收货地址（1:N）。
 * 与 user_accounts 关联，不直接复用 BusinessPartner（后者是 B 端合作伙伴，零售收货人档案独立维护）。
 */
@Entity
@Table(name = "market_customer")
public class MarketCustomer extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_account_id")
    private UserAccount user;

    @Column(nullable = false, length = 60) private String name;
    @Column(nullable = false, length = 20) private String phone;
    @Column(nullable = false, length = 200) private String address;
    @Column(nullable = false) private Boolean defaultFlag = false;
    @Column(length = 200) private String remark;

    public MarketCustomer() {}
    public MarketCustomer(UserAccount user, String name, String phone, String address) {
        this.user = user; this.name = name; this.phone = phone; this.address = address;
    }

    public Long getId() { return id; }
    public UserAccount getUser() { return user; } public void setUser(UserAccount user) { this.user = user; }
    public String getName() { return name; } public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; } public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; } public void setAddress(String address) { this.address = address; }
    public Boolean getDefaultFlag() { return defaultFlag; } public void setDefaultFlag(Boolean defaultFlag) { this.defaultFlag = defaultFlag == null ? false : defaultFlag; }
    public String getRemark() { return remark; } public void setRemark(String remark) { this.remark = remark; }
}
