package com.wms.repository;

import com.wms.model.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    @Query("select l from OperationLog l where "
            + "(:username is null or l.username = :username) "
            + "and (:action is null or l.action = :action) "
            + "and (:result is null or l.result = :result) "
            + "and (:from is null or l.operationAt >= :from) "
            + "and (:to is null or l.operationAt <= :to) ")
    Page<OperationLog> searchPage(@Param("username") String username,
                                  @Param("action") String action,
                                  @Param("result") String result,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to,
                                  Pageable pageable);

    /** 归档扫描：按主键游标向前推进，不能用 offset 翻页（删除已归档行后 offset 会漏掉后移的数据）。 */
    @Query("select l from OperationLog l where l.id > :afterId and l.operationAt < :before order by l.id asc")
    List<OperationLog> findArchiveBatch(@Param("afterId") Long afterId,
                                        @Param("before") LocalDateTime before,
                                        Pageable pageable);
}