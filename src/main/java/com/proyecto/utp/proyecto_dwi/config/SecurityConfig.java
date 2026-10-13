package com.proyecto.utp.proyecto_dwi.config;

import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate db) {
        return username -> {
            var rows = db.queryForList("SELECT usuario,password_hash,rol,activo FROM dbo.usuario WHERE usuario=?",
                    username);
            if (rows.isEmpty())
                throw new UsernameNotFoundException("Credenciales incorrectas");
            var u = rows.get(0);
            return User.withUsername((String) u.get("usuario")).password((String) u.get("password_hash"))
                    .roles((String) u.get("rol")).disabled(!Boolean.TRUE.equals(u.get("activo"))).build();
        };
    }

    private static void json(HttpServletResponse r, int status, String detail) throws IOException {
        r.setStatus(status);
        r.setContentType("application/problem+json;charset=UTF-8");
        r.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JdbcTemplate db) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/login.html", "/css/**", "/js/**", "/api/csrf", "/api/setup", "/api/setup/status",
                        "/error")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/productos/**", "/api/categorias", "/api/unidades")
                .authenticated()
                .requestMatchers("/api/productos/**", "/api/categorias", "/api/unidades").hasRole("ADMIN")
                .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/login.html").loginProcessingUrl("/api/login").usernameParameter("usuario")
                        .successHandler((q, r, a) -> {
                            db.update(
                                    "INSERT INTO dbo.auditoria(usuario_id,accion,entidad,resultado,descripcion) SELECT id,'INICIAR_SESION','usuario','EXITOSO','Inicio de sesión' FROM dbo.usuario WHERE usuario=?",
                                    a.getName());
                            r.setStatus(204);
                        })
                        .failureHandler((q, r, e) -> {
                            db.update(
                                    "INSERT INTO dbo.auditoria(usuario_id,accion,entidad,resultado,descripcion) VALUES((SELECT id FROM dbo.usuario WHERE usuario=?),'ACCESO_FALLIDO','usuario','RECHAZADO','Credenciales inválidas')",
                                    q.getParameter("usuario"));
                            json(r, 401, "Credenciales incorrectas o cuenta desactivada");
                        }))
                .logout(l -> l.logoutUrl("/api/logout").logoutSuccessHandler((q, r, a) -> r.setStatus(204)))
                .exceptionHandling(e -> e.authenticationEntryPoint((q, r, x) -> {
                    if (q.getRequestURI().startsWith("/api/"))
                        json(r, 401, "Inicia sesión para continuar");
                    else
                        r.sendRedirect("/login.html");
                }).accessDeniedHandler((q, r, x) -> json(r, 403, "No tienes permiso o la sesión debe actualizarse")));
        http.addFilterAfter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain chain)
                    throws ServletException, IOException {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                    var rows = db.queryForList("SELECT rol,activo FROM dbo.usuario WHERE usuario=?", auth.getName());
                    if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("activo"))) {
                        SecurityContextHolder.clearContext();
                        if (q.getSession(false) != null)
                            q.getSession(false).invalidate();
                        json(r, 401, "La cuenta fue desactivada");
                        return;
                    }
                    var refreshed = new UsernamePasswordAuthenticationToken(auth.getPrincipal(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + rows.get(0).get("rol"))));
                    SecurityContextHolder.getContext().setAuthentication(refreshed);
                }
                chain.doFilter(q, r);
            }
        }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
