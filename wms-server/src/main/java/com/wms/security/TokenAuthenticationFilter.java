package com.wms.security;
import jakarta.servlet.*; import jakarta.servlet.http.*; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import java.io.IOException; import java.util.*;
@Component public class TokenAuthenticationFilter extends OncePerRequestFilter {
 private final TokenService tokens; public TokenAuthenticationFilter(TokenService tokens){this.tokens=tokens;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException { String auth=request.getHeader("Authorization"); if(auth!=null&&auth.startsWith("Bearer "))tokens.resolve(auth.substring(7)).ifPresent(p->{var a=new UsernamePasswordAuthenticationToken(p,null,List.of(new SimpleGrantedAuthority("ROLE_"+p.role())));SecurityContextHolder.getContext().setAuthentication(a);});chain.doFilter(request,response); }
}
