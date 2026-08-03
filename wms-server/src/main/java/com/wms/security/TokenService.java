package com.wms.security;

import com.wms.model.entity.UserAccount;
import org.springframework.stereotype.Service;
import java.time.*; import java.util.*; import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {
    public record Principal(String username,String role,String displayName,Set<String> permissions) {}
    private record Session(Principal principal, Instant expiresAt) {}
    private final Map<String,Session> sessions = new ConcurrentHashMap<>();
    public String issue(UserAccount user){ cleanup(); String token=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""); sessions.put(token,new Session(new Principal(user.getUsername(),user.getRole(),user.getDisplayName(),RolePermissions.forRole(user.getRole())),Instant.now().plus(Duration.ofHours(12)))); return token; }
    public Optional<Principal> resolve(String token){ Session session=sessions.get(token); if(session==null||session.expiresAt().isBefore(Instant.now())){sessions.remove(token);return Optional.empty();}return Optional.of(session.principal()); }
    public void revoke(String token){sessions.remove(token);} private void cleanup(){sessions.entrySet().removeIf(e->e.getValue().expiresAt().isBefore(Instant.now()));}
}
