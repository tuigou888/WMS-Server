package com.wms.repository;

import com.wms.model.entity.WechatBindTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WechatBindTicketRepository extends JpaRepository<WechatBindTicket, Long> {
    Optional<WechatBindTicket> findByTicketHash(String ticketHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WechatBindTicket t where t.ticketHash = :ticketHash")
    Optional<WechatBindTicket> findForUpdateByTicketHash(@Param("ticketHash") String ticketHash);

    /** 清理已过期的绑定票据：peek/consume 只在碰到时惰性删除，从未访问的过期行会一直留下。 */
    @Modifying
    @Query("delete from WechatBindTicket t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);

    /** R4-11：分批清理取批次 id。 */
    @Query("select t.id from WechatBindTicket t where t.expiresAt < :now")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("delete from WechatBindTicket t where t.id in :ids")
    int deleteByIdIn(@Param("ids") java.util.Collection<Long> ids);
}
