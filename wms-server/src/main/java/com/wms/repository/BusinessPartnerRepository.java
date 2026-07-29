package com.wms.repository;
import com.wms.model.entity.BusinessPartner; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner,Long> { boolean existsByCode(String code); List<BusinessPartner> findByTypeInAndEnabledTrueOrderByName(List<String> types); }
