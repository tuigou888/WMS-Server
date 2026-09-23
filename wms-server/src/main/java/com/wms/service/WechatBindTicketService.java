package com.wms.service;

import com.wms.model.entity.WechatBindTicket;
import com.wms.repository.WechatBindTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/** 微信绑定票据持久化到数据库，服务重启或切换实例后仍可完成一次性绑定。 */
@Service
public class WechatBindTicketService {
    public static final Duration TTL = Duration.ofMinutes(5);
    private final SecureRandom random = new SecureRandom();
    private final WechatBindTicketRepository tickets;

    public WechatBindTicketService(WechatBindTicketRepository tickets) { this.tickets = tickets; }

    @Transactional
    public String issue(String openid) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        WechatBindTicket ticket = new WechatBindTicket();
        ticket.setTicketHash(hash(value));
        ticket.setOpenid(openid);
        ticket.setExpiresAt(LocalDateTime.now().plus(TTL));
        tickets.save(ticket);
        return value;
    }

    @Transactional
    public String peek(String value) { return get(value, false); }

    @Transactional
    public String consume(String value) { return get(value, true); }

    private String get(String value, boolean consume) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("微信绑定凭据不能为空");
        String ticketHash = hash(value);
        WechatBindTicket ticket = (consume ? tickets.findForUpdateByTicketHash(ticketHash) : tickets.findByTicketHash(ticketHash)).orElse(null);
        if (ticket == null || ticket.getExpiresAt().isBefore(LocalDateTime.now())) {
            if (ticket != null) tickets.delete(ticket);
            throw new IllegalArgumentException("微信绑定凭据已失效，请重新登录");
        }
        if (consume) tickets.delete(ticket);
        return ticket.getOpenid();
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 不可用", e); }
    }
}
