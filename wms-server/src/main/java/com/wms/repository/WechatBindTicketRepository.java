package com.wms.repository;

import com.wms.model.entity.WechatBindTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WechatBindTicketRepository extends JpaRepository<WechatBindTicket, Long> {
    Optional<WechatBindTicket> findByTicketHash(String ticketHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WechatBindTicket t where t.ticketHash = :ticketHash")
    Optional<WechatBindTicket> findForUpdateByTicketHash(@Param("ticketHash") String ticketHash);
}
