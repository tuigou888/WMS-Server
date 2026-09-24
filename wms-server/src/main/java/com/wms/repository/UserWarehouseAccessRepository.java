package com.wms.repository;

import com.wms.model.entity.UserWarehouseAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserWarehouseAccessRepository extends JpaRepository<UserWarehouseAccess, Long> {
    @Query("select u.id from UserAccount u where u.username = :username")
    Optional<Long> findUserIdByUsername(@Param("username") String username);

    @Query("select a.warehouse.id from UserWarehouseAccess a where a.user.id = :userId order by a.warehouse.id")
    List<Long> findWarehouseIdsByUserId(@Param("userId") Long userId);

    @Query("select case when count(a) > 0 then true else false end from UserWarehouseAccess a where a.user.username = :username and a.warehouse.id = :warehouseId")
    boolean existsByUsernameAndWarehouseId(@Param("username") String username, @Param("warehouseId") Long warehouseId);

    @Query("select case when count(a) > 0 then true else false end from UserWarehouseAccess a where a.user.id = :userId and a.warehouse.id = :warehouseId")
    boolean existsByUserIdAndWarehouseId(@Param("userId") Long userId, @Param("warehouseId") Long warehouseId);

    void deleteByUser_Id(Long userId);
}
