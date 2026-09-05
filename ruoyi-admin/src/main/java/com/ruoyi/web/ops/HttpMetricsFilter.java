package com.ruoyi.web.ops;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs after the canonical correlation filter and before authentication. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+1)
public class HttpMetricsFilter extends OncePerRequestFilter
{
    private final HttpWindowMetrics metrics;
    public HttpMetricsFilter(HttpWindowMetrics metrics) {this.metrics=metrics;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException
    {
        long started=System.nanoTime();boolean failed=false;
        try {chain.doFilter(request,response);}
        catch(IOException|ServletException|RuntimeException failure) {failed=true;throw failure;}
        finally {
            Object matched=request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            metrics.record(matched instanceof String template?template:"OTHER",failed?500:response.getStatus(),(System.nanoTime()-started)/1_000_000d);
        }
    }
}
