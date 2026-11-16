package com.orderandgo.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalAccess extends OncePerRequestFilter {
    @Value("${app.internal-key:local-orderandgo-only}") String key;
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
            throws ServletException,IOException {
        if (req.getRequestURI().startsWith("/internal/")) {
            String supplied=req.getHeader("X-Internal-Key");
            if (supplied==null || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) {
                res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"내부 서비스 요청만 허용됩니다.\"}"); return;
            }
        }
        chain.doFilter(req,res);
    }
}
