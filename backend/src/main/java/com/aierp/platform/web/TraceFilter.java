package com.aierp.platform.web;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.web.filter.OncePerRequestFilter;
public class TraceFilter extends OncePerRequestFilter {
    public static String id(HttpServletRequest request) {
        Object id=request.getAttribute("traceId");
        if(id==null) { id=UUID.randomUUID().toString(); request.setAttribute("traceId",id); }
        return id.toString();
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("X-Trace-Id",id(request)); chain.doFilter(request,response);
    }
}
