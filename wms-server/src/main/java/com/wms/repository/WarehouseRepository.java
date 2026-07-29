package com.wms.repository;

import com.wms.model.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    boolean existsByCode(String code);

    List<Warehouse> findByStatusTrueOrderByNameAsc();
}
