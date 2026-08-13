package com.wms.repository.market;

import com.wms.model.entity.market.MarketOrderLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketOrderLogRepository extends JpaRepository<MarketOrderLog, Long> {
    List<MarketOrderLog> findByOrderIdOrderByIdAsc(Long orderId);
}
