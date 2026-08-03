package us.leaf3stones.grpc_test

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.security.authentication.CachingUserDetailsService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserCache
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component


@Configuration
class CachedSecUserDetailsService {
    @Bean
    fun template(connectionFactory: RedisConnectionFactory) = RedisTemplate<String, String>().apply {
        this.connectionFactory = connectionFactory
        defaultSerializer = StringRedisSerializer.UTF_8
        afterPropertiesSet()
    }

    @Bean
    fun cachedUserDetailsService(
        secUserDetailsService: SecUserDetailsService,
        redisUserCache: RedisUserCache
    ): UserDetailsService {
        val cachingUserDetailsService = CachingUserDetailsService(secUserDetailsService)
        cachingUserDetailsService.userCache = redisUserCache
        return cachingUserDetailsService
    }
}


@Component
class RedisUserCache(private val template: RedisTemplate<String, String>) : UserCache {
    override fun getUserFromCache(username: String): UserDetails? {
        val roles = template.opsForValue().get(username) ?: return null
        val grantedAuthorities = roles.split(",").filter { it.isNotEmpty() }.map { SimpleGrantedAuthority(it) }
        return User.withUsername(username).password("").authorities(grantedAuthorities).build()
    }

    override fun putUserInCache(user: UserDetails) {
        template.opsForValue().set(user.username, user.authorities.joinToString(",") { it.authority ?: "" })
    }

    override fun removeUserFromCache(username: String) {
        template.delete(username)
    }
}
