package com.cleanroute.security;
import jakarta.servlet.*; import jakarta.servlet.http.*; import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import java.io.IOException; import java.util.List;
@Component public class JwtAuthFilter extends OncePerRequestFilter {
 private final JwtService jwt; public JwtAuthFilter(JwtService jwt){this.jwt=jwt;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
  String header=req.getHeader("Authorization"); if(header!=null&&header.startsWith("Bearer ")) try { var id=jwt.subject(header.substring(7)); var auth=new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))); SecurityContextHolder.getContext().setAuthentication(auth); } catch(RuntimeException ignored) { SecurityContextHolder.clearContext(); }
  chain.doFilter(req,res);
 }
}
