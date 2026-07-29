package com.wms.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "business_partners")
public class BusinessPartner extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 20) private String type; // SUPPLIER, CUSTOMER, BOTH
    private String contactName; private String phone; private String email; private String address;
    @Column(nullable=false) private Boolean enabled=true;
    @Column(length=1000) private String remark;
    public Long getId(){return id;} public String getCode(){return code;} public void setCode(String v){code=v;} public String getName(){return name;} public void setName(String v){name=v;}
    public String getType(){return type;} public void setType(String v){type=v;} public String getContactName(){return contactName;} public void setContactName(String v){contactName=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;} public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getAddress(){return address;} public void setAddress(String v){address=v;} public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean v){enabled=v;}
    public String getRemark(){return remark;} public void setRemark(String v){remark=v;}
}
