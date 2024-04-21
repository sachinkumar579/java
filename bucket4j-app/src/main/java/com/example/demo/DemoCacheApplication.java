package com.example.demo;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.bucket4j.BandwidthBuilder;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.jedis.cas.JedisBasedProxyManager;
import redis.clients.jedis.JedisPool;

@SpringBootApplication
public class DemoCacheApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoCacheApplication.class, args);
	}

}

@RestController
class Controller {

	@Autowired
	private ProxyManager<String> proxyManager;

	private static final long REQS_IN_15_MINS_WINDOW = 5;
	private static final Duration DURATION_15SECS = Duration.ofSeconds(15);

	@GetMapping("/test")
	public void test() {

		Bucket bucket = proxyManager.builder().build("test",
				() -> BucketConfiguration.builder().addLimit(BandwidthBuilder.builder().capacity(REQS_IN_15_MINS_WINDOW)
						.refillIntervally(REQS_IN_15_MINS_WINDOW, DURATION_15SECS).build()).build());

		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			// the limit is not exceeded
			System.out.println("remaining tokens" + probe.getRemainingTokens());
		} else {
			System.out.println("not available");
		}
	}
}

@Component
class RedisConfig {

	@Bean
	public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		return RedisCacheManager.create(connectionFactory);
	}

	@Bean
	ProxyManager<String> proxyManager(CacheManager cacheManager) {
		return JedisBasedProxyManager.builderFor(createJedisClient()).withKeyMapper(Mapper.STRING).build();
	}

	private JedisPool createJedisClient() {
		String url = "rediss://default:password@test.redis.cache.windows.net:6380";
		return new JedisPool(url);
	}
}