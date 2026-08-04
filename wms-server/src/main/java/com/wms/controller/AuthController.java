package com.wms.controller;
import com.wms.common.*; import com.wms.dto.LoginRequest; import com.wms.dto.UserRequest; import com.wms.dto.WxLoginRequest; import com.wms.dto.WxBindRequest; import com.wms.model.entity.UserAccount; import com.wms.repository.UserAccountRepository; import com.wms.security.*; import com.wms.service.WechatService; import jakarta.validation.Valid; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/auth") public class AuthController {
  private static final Logger log = LoggerFactory.getLogger(AuthController.class);
  private final UserAccountRepository users; private final PasswordEncoder encoder; private final TokenService tokens; private final WechatService wechat;
  public AuthController(UserAccountRepository users,PasswordEncoder encoder,TokenService tokens,WechatService wechat){this.users=users;this.encoder=encoder;this.tokens=tokens;this.wechat=wechat;}
 @PostMapping("/login") public ApiResponse<Map<String,Object>> login(@Valid @RequestBody LoginRequest request){UserAccount user=users.findByUsername(request.username().trim()).orElseThrow(()->new BusinessException("用户名或密码错误"));if(!Boolean.TRUE.equals(user.getEnabled())||!encoder.matches(request.password(),user.getPassword()))throw new BusinessException("用户名或密码错误");String token=tokens.issue(user);log.info("登录成功: username={}, role={}", user.getUsername(), user.getRole());return ApiResponse.ok("登录成功",view(user,token));}

  @PostMapping("/wx-login") public ApiResponse<Map<String,Object>> wxLogin(@Valid @RequestBody WxLoginRequest request){
    String openid = wechat.getOpenid(request.code());
    UserAccount user = users.findByOpenid(openid).orElse(null);
    if (user != null) {
      if (!Boolean.TRUE.equals(user.getEnabled())) throw new BusinessException("账号已禁用");
      String token = tokens.issue(user);
      log.info("微信登录成功: username={}, role={}", user.getUsername(), user.getRole());
      return ApiResponse.ok("登录成功", view(user, token));
    }
    return ApiResponse.ok(Map.of("needBind", true, "openid", openid));
  }

  @PostMapping("/wx-bind") public ApiResponse<Map<String,Object>> wxBind(@Valid @RequestBody WxBindRequest request){
    if (users.findByOpenid(request.openid()).isPresent()) throw new BusinessException("该微信已绑定其他账号");
    UserAccount user = users.findByUsername(request.username().trim()).orElseThrow(()->new BusinessException("用户名或密码错误"));
    if (!Boolean.TRUE.equals(user.getEnabled()) || !encoder.matches(request.password(), user.getPassword())) throw new BusinessException("用户名或密码错误");
    user.setOpenid(request.openid());
    users.save(user);
    String token = tokens.issue(user);
    log.info("微信绑定登录: username={}, role={}", user.getUsername(), user.getRole());
    return ApiResponse.ok("绑定成功", view(user, token));
  }
  @GetMapping("/me") public ApiResponse<Map<String,Object>> me(){Object p=SecurityContextHolder.getContext().getAuthentication().getPrincipal();if(!(p instanceof TokenService.Principal principal))throw new BusinessException("登录状态已失效");return ApiResponse.ok(Map.of("username",principal.username(),"role",principal.role(),"displayName",principal.displayName()==null?principal.username():principal.displayName(),"permissions",principal.permissions()));}
  @GetMapping("/permissions") public ApiResponse<Map<String,Object>> permissions(){return ApiResponse.ok(Map.of("all",RolePermissions.all(),"roles",Map.of("ADMIN",RolePermissions.forRole("ADMIN"),"WAREHOUSE",RolePermissions.forRole("WAREHOUSE"))));}
  @GetMapping("/users") @PreAuthorize("hasAuthority('user:manage')") public ApiResponse<List<Map<String,Object>>> users(){SecurityUtils.require(Permissions.USER_MANAGE);return ApiResponse.ok(users.findAll().stream().map(u->userView(u)).toList());}
  @PostMapping("/users") @PreAuthorize("hasAuthority('user:manage')") public ApiResponse<Map<String,Object>> createUser(@Valid @RequestBody UserRequest request){SecurityUtils.require(Permissions.USER_MANAGE);if(users.findByUsername(request.username().trim()).isPresent())throw new BusinessException("用户名已存在");if(request.password()==null||request.password().length()<6)throw new BusinessException("密码至少 6 位");UserAccount u=new UserAccount();u.setUsername(request.username().trim());u.setPassword(encoder.encode(request.password()));u.setDisplayName(request.displayName());u.setRole(request.role());u.setEnabled(request.enabled()==null||request.enabled());return ApiResponse.ok("用户创建成功",userView(users.save(u)));}
  @PutMapping("/users/{id}") @PreAuthorize("hasAuthority('user:manage')") public ApiResponse<Map<String,Object>> updateUser(@PathVariable Long id,@Valid @RequestBody UserRequest request){SecurityUtils.require(Permissions.USER_MANAGE);UserAccount u=users.findById(id).orElseThrow(()->new BusinessException("用户不存在"));u.setDisplayName(request.displayName());u.setRole(request.role());u.setEnabled(request.enabled()==null||request.enabled());if(request.password()!=null&&!request.password().isBlank()){if(request.password().length()<6)throw new BusinessException("密码至少 6 位");u.setPassword(encoder.encode(request.password()));}return ApiResponse.ok("用户更新成功",userView(users.save(u)));}
 @PostMapping("/logout") public ApiResponse<Void> logout(@RequestHeader(value="Authorization",required=false) String auth){if(auth!=null&&auth.startsWith("Bearer ")){tokens.revoke(auth.substring(7));log.info("用户退出登录");}return ApiResponse.ok("已退出登录",null);}
  private void ensureAdmin(){SecurityUtils.require(Permissions.USER_MANAGE);}
  private Map<String,Object> userView(UserAccount u){return Map.of("id",u.getId(),"username",u.getUsername(),"displayName",u.getDisplayName()==null?u.getUsername():u.getDisplayName(),"role",u.getRole(),"enabled",u.getEnabled());}
  private Map<String,Object> view(UserAccount u,String token){Map<String,Object> m=new LinkedHashMap<>();m.put("token",token);m.put("username",u.getUsername());m.put("displayName",u.getDisplayName());m.put("role",u.getRole());m.put("expiresIn",43200);m.put("permissions",RolePermissions.forRole(u.getRole()));return m;}
}
