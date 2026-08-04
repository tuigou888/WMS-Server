package com.wms.repository;
import com.wms.model.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface LocationRepository extends JpaRepository<Location,Long> {
 Optional<Location> findByWarehouseIdAndCode(Long warehouseId,String code);
 @Query("select l from Location l join fetch l.warehouse where l.warehouse.id = :warehouseId") List<Location> findByWarehouseId(Long warehouseId);
 @Query("select l from Location l join fetch l.warehouse") List<Location> findAllWithWarehouse();
 /** 原子插入库位（并发扫码入库新建同一库位时只有一个成功），配合 (warehouse_id,code) 唯一约束，兼容 MySQL / H2(MODE=MySQL)。 */
 @Modifying
 @Query(value = "insert into locations (warehouse_id, code, status, created_at, updated_at) "
         + "values (:warehouseId, :code, 1, now(), now()) on duplicate key update id = id", nativeQuery = true)
 void insertIfAbsent(@Param("warehouseId") Long warehouseId, @Param("code") String code);
}
