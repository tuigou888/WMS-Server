package com.wms.repository.market;

import com.wms.model.entity.market.MarketCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketCustomerRepository extends JpaRepository<MarketCustomer, Long> {
    Optional<MarketCustomer> findFirstByUserIdAndDefaultFlagTrue(Long userId);
    List<MarketCustomer> findByUserIdOrderByIdDesc(Long userId);
    long countByUserId(Long userId);
}
