package com.wms.controller;
import com.wms.common.*; import com.wms.dto.LoginRequest; import com.wms.dto.UserRequest; import com.wms.dto.WxLoginRequest; import com.wms.dto.WxBindRequest; import com.wms.dto.WxRegistrationRequest; import com.wms.model.entity.UserAccount; import com.wms.repository.UserAccountRepository; import com.wms.security.*; import com.wms.service.LoginRateLimiter; import com.wms.service.WechatBindTicketService; import com.wms.service.WechatService; import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.dao.DataIntegrityViolationException; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/auth") public class AuthController {
  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private final UserAccountRepository users; private final PasswordEncoder encoder; private final TokenService tokens; private final WechatService wechat; private final LoginRateLimiter rateLimiter; private final WechatBindTicketService bindTickets;
  public AuthController(UserAccountRepository users,PasswordEncoder encoder,TokenService tokens,WechatService wechat,LoginRateLimiter rateLimiter,WechatBindTicketService bindTickets){this.users=users;this.encoder=encoder;this.tokens=tokens;this.wechat=wechat;this.rateLimiter=rateLimiter;this.bindTickets=bindTickets;}
 @PostMapping("/login") public ApiResponse<Map<String,Object>> login(@Valid @RequestBody LoginRequest request,HttpServletRequest http){String username=normalizeUsername(request.username());String key=rateKey(http,username);if(rateLimiter.isBlocked(key))throw new RateLimitedException("登录失败次数过多，请 "+LoginRateLimiter.WINDOW.toMinutes()+" 分钟后再试");UserAccount user=users.findByUsername(username).orElse(null);if(user==null||!Boolean.TRUE.equals(user.getEnabled())||!encoder.matches(request.password(),user.getPassword())){rateLimiter.recordFailure(key);throw new BusinessException("用户名或密码错误");}rateLimiter.reset(key);String token=tokens.issue(user);log.info("登录成功: username={}, role={}", user.getUsername(), user.getRole());return ApiResponse.ok("登录成功",view(user,token));}

  @PostMapping("/wx-login") public ApiResponse<Map<String,Object>> wxLogin(@Valid @RequestBody WxLoginRequest request){
    String openid = wechat.getOpenid(request.code());
    UserAccount user = users.findByOpenid(openid).orElse(null);
    if (user != null) {
      if (!Boolean.TRUE.equals(user.getEnabled())) throw new BusinessException("账号已禁用");
      String token = tokens.issue(user);
      log.info("微信登录成功: username={}, role={}", user.getUsername(), user.getRole());
      return ApiResponse.ok("登录成功", view(user, token));
    }
    return ApiResponse.ok(Map.of("needBind", true, "bindTicket", bindTickets.issue(openid), "expiresIn", WechatBindTicketService.TTL.toSeconds()));
  }

  @PostMapping("/wx-bind") public ApiResponse<Map<String,Object>> wxBind(@Valid @RequestBody WxBindRequest request,HttpServletRequest http){String username=normalizeUsername(request.username());String key=rateKey(http,username);if(rateLimiter.isBlocked(key))throw new RateLimitedException("操作次数过多，请 "+LoginRateLimiter.WINDOW.toMinutes()+" 分钟后再试");String openid=ticketOpenid(request.bindTicket());if(users.findByOpenid(openid).isPresent())throw new BusinessException("该微信已绑定其他账号");
    UserAccount user = users.findByUsername(username).orElse(null);
    if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !encoder.matches(request.password(), user.getPassword())) { rateLimiter.recordFailure(key); throw new BusinessException("用户名或密码错误"); }
    consumeTicket(request.bindTicket(),openid);
    rateLimiter.reset(key);
    user.setOpenid(openid);
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      // 并发绑定同一 openid 时唯一约束兜底，返回友好提示而非 500
      throw new BusinessException("该微信已绑定其他账号");
    }
    String token = tokens.issue(user);
    log.info("微信绑定登录: username={}, role={}", user.getUsername(), user.getRole());
    return ApiResponse.ok("绑定成功", view(user, token));
  }
  @PostMapping("/wx-register") public ApiResponse<Map<String,Object>> wxRegister(@Valid @RequestBody WxRegistrationRequest request,HttpServletRequest http){String username=normalizeUsername(request.username());String key=rateKey(http,username);if(rateLimiter.isBlocked(key))throw new RateLimitedException("操作次数过多，请 "+LoginRateLimiter.WINDOW.toMinutes()+" 分钟后再试");String openid=ticketOpenid(request.bindTicket());if(users.findByOpenid(openid).isPresent())throw new BusinessException("该微信已绑定其他账号");if(users.findByUsername(username).isPresent())throw new BusinessException("用户名已存在");UserAccount user=new UserAccount();user.setUsername(username);user.setPassword(encoder.encode(request.password()));user.setDisplayName(request.displayName()==null||request.displayName().isBlank()?username:request.displayName().trim());user.setRole("CUSTOMER");user.setEnabled(true);user.setOpenid(consumeTicket(request.bindTicket(),openid));try{users.saveAndFlush(user);}catch(DataIntegrityViolationException e){throw new BusinessException("用户名或微信账号已存在");}rateLimiter.reset(key);String token=tokens.issue(user);log.info("微信客户开户成功: username={}",user.getUsername());return ApiResponse.ok("注册成功",view(user,token));}
  @GetMapping("/me") public ApiResponse<Map<String,Object>> me(){Object p=SecurityContextHolder.getContext().getAuthentication().getPrincipal();if(!(p instanceof TokenService.Principal principal))throw new BusinessException("登录状态已失效");return ApiResponse.ok(Map.of("username",principal.username(),"role",principal.role(),"displayName",principal.displayName()==null?principal.username():principal.displayName(),"permissions",principal.permissions()));}
  @GetMapping("/permissions") public ApiResponse<Map<String,Object>> permissions(){return ApiResponse.ok(Map.of("all",RolePermissions.all(),"roles",RolePermissions.roles()));}
  @GetMapping("/users") @PreAuthorize("hasAuthority('user:manage')") public ApiResponse<List<Map<String,Object>>> users(){SecurityUtils.require(Permissions.USER_MANAGE);return ApiResponse.ok(users.findAll().stream().map(u->userView(u)).toList());}
 @PostMapping("/users") @PreAuthorize("hasAuthority('user:manage')") public ApiResponse<Map<String,Object>> createUser(@Valid @RequestBody UserRequest request){SecurityUtils.require(Permissions.USER_MANAGE);if(users.findByUsername(request.username().trim()).isPresent())throw new BusinessException("用户名已存在");if(request.password()==null||request.password().length()<6)throw new BusinessException("密码至少 6 位");UserAccount u=new UserAccount();u.setUsername(request.username().trim());u.setPassword(encoder.encode(request.password()));u.setDisplayName(request.displayName());u.setRole(validRole(request.role()));u.setEnabled(request.enabled()==null||request.enabled());return ApiResponse.ok("用户创建成功",userView(users.save(u)));}
 @PutMapping("/users/{id}") @PreAuthorize("hasAuthority('user:manage')") public ApiResponse<Map<String,Object>> updateUser(@PathVariable Long id,@Valid @RequestBody UserRequest request){SecurityUtils.require(Permissions.USER_MANAGE);UserAccount u=users.findById(id).orElseThrow(()->new BusinessException("用户不存在"));u.setDisplayName(request.displayName());u.setRole(validRole(request.role()));u.setEnabled(request.enabled()==null||request.enabled());if(request.password()!=null&&!request.password().isBlank()){if(request.password().length()<6)throw new BusinessException("密码至少 6 位");u.setPassword(encoder.encode(request.password()));}UserAccount saved=users.save(u);tokens.revokeByUsername(saved.getUsername());return ApiResponse.ok("用户更新成功",userView(saved));}
 @PostMapping("/logout") public ApiResponse<Void> logout(@RequestHeader(value="Authorization",required=false) String auth){if(auth!=null&&auth.startsWith("Bearer ")){tokens.revoke(auth.substring(7));log.info("用户退出登录");}return ApiResponse.ok("已退出登录",null);}
  private void ensureAdmin(){SecurityUtils.require(Permissions.USER_MANAGE);}
  /** 限速键：客户端 IP + 用户名，避免恶意锁定他人账号（同 IP 不同用户互不影响）。 */
  private String rateKey(HttpServletRequest http,String username){String ip=http==null?"unknown":clientIp(http);return ip+"|"+normalizeUsername(username).toLowerCase(Locale.ROOT);}
  private String clientIp(HttpServletRequest http){return http.getRemoteAddr()==null?"unknown":http.getRemoteAddr();}
  private String normalizeUsername(String username){if(username==null||username.trim().isBlank())throw new BusinessException("用户名不能为空");return username.trim();}
  private String validRole(String role){if(!RolePermissions.roles().containsKey(role))throw new BusinessException("角色不受支持");return role;}
  private String ticketOpenid(String ticket){try{return bindTickets.peek(ticket);}catch(IllegalArgumentException e){throw new BusinessException(e.getMessage());}}
  private String consumeTicket(String ticket,String expectedOpenid){try{String openid=bindTickets.consume(ticket);if(!Objects.equals(openid,expectedOpenid))throw new BusinessException("微信绑定凭据校验失败");return openid;}catch(IllegalArgumentException e){throw new BusinessException(e.getMessage());}}
  private Map<String,Object> userView(UserAccount u){return Map.of("id",u.getId(),"username",u.getUsername(),"displayName",u.getDisplayName()==null?u.getUsername():u.getDisplayName(),"role",u.getRole(),"enabled",u.getEnabled());}
  private Map<String,Object> view(UserAccount u,String token){Map<String,Object> m=new LinkedHashMap<>();m.put("token",token);m.put("username",u.getUsername());m.put("displayName",u.getDisplayName());m.put("role",u.getRole());m.put("expiresIn",43200);m.put("permissions",RolePermissions.forRole(u.getRole()));return m;}
}
