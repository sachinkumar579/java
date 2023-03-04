package com.ratelimiter.config;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;

@Configuration
public class RateLimitingConfig {
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterTest() {
        FilterRegistrationBean<RateLimitFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new RateLimitFilter());
        registrationBean.addUrlPatterns("/events");
        return registrationBean;
    }
}

class RateLimitFilter implements Filter {
	private static final long REQS_PER_MIN            = 20;
    private static final long REQS_IN_15_MINS_WINDOW  = 5;
    private static final Duration DURATION_1MIN       = Duration.ofMinutes(1);
 	private static final Duration DURATION_15SECS     = Duration.ofSeconds(15);
 	
	private final Map<String, Bucket> eventsBucket = new HashMap<>();
	
    public RateLimitFilter() {
        IntStream.range(1, 10).forEach(eventId ->this.eventsBucket.computeIfAbsent(String.valueOf(eventId), this::newBucket));
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
    	HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
        
        ConsumptionProbe probe = this.eventsBucket.get(servletRequest.getParameter("id")).tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
        	httpResponse.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            httpResponse.addHeader(HttpHeaders.RETRY_AFTER, String.valueOf(waitForRefill)); 
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.getWriter().write("You have exhausted your API Request Quota");
            httpResponse.getWriter().flush();
        }
    }
    
    // Multiple limits can be set on the same bucket. Here we allow 20 requests per min and 5 requests in a 15 secs window
    private Bucket newBucket(String eventId) {	    	
		return Bucket.builder()
				.addLimit(Bandwidth.classic(REQS_PER_MIN, Refill.intervally(REQS_PER_MIN, DURATION_1MIN )))
	            .addLimit(Bandwidth.classic(REQS_IN_15_MINS_WINDOW, Refill.intervally(REQS_IN_15_MINS_WINDOW, DURATION_15SECS)))
	            .build();
    }
}