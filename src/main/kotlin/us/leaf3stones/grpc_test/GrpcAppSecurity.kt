package us.leaf3stones.grpc_test

import io.grpc.Metadata
import io.jsonwebtoken.Jwts
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.grpc.server.GlobalServerInterceptor
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor
import org.springframework.grpc.server.security.GrpcSecurity
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import us.leaf3stones.grpc_test.AuthService.Companion.signingKey

@Configuration
class GrpcAppSecurity {
    @Bean
    fun jwtAuthenticationProvider(): AuthenticationProvider {
        return object : AuthenticationProvider {
            override fun authenticate(authentication: Authentication): Authentication {
                // We already validated the JWT inside the extractor.
                // We can just return the token to tell Spring it is fully authenticated.
                return authentication
            }

            override fun supports(authentication: Class<*>): Boolean {
                // Tell Spring this provider handles our custom token
                return JwtAuthenticationToken::class.java.isAssignableFrom(authentication)
            }
        }
    }


    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    // 1. Keep your In-Memory Users for the Login phase
    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService {
        val userA = User.withUsername("userA")
            .password(passwordEncoder.encode("password"))
            .authorities("ROLE_USER")
            .build()

        val adminA = User.withUsername("adminA")
            .password(passwordEncoder.encode("password"))
            .authorities("ROLE_ADMIN", "ROLE_USER")
            .build()

        return InMemoryUserDetailsManager(userA, adminA)
    }

    // 2. Configure gRPC Security for JWT Extraction
    @Bean
    @GlobalServerInterceptor
    fun grpcSecurityFilterChain(grpc: GrpcSecurity): AuthenticationProcessInterceptor {
        return grpc
            .authenticationExtractor { metadata, _, _ ->
                val authKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
                val authHeader = metadata.get(authKey)

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    val token = authHeader.substring(7)
                    try {
                        val claims = Jwts.parser()
                            .verifyWith(signingKey)
                            .build()
                            .parseSignedClaims(token)
                            .payload

                        val username = claims.subject

                        val rolesString = claims.get("roles", String::class.java) ?: ""
                        val authorities = rolesString.split(",")
                            .filter { it.isNotBlank() }
                            .map { SimpleGrantedAuthority(it) }

                        // Returning with authorities marks it as TRUE (authenticated)
                        return@authenticationExtractor JwtAuthenticationToken(username, authorities)
                    } catch (_: Exception) {
                        // Token is expired, malformed, or has an invalid signature
                        return@authenticationExtractor null
                    }
                }
                null // Unauthenticated (no token or wrong prefix)
            }
            .authorizeRequests { requests ->
                requests
                    .methods("Simple/StreamHello").hasAuthority("ROLE_ADMIN")
                    .methods("Simple/SayHello").hasAuthority("ROLE_USER")
                    // The login endpoint MUST be accessible without a JWT!
                    .methods("AuthService/*").permitAll()
                    .methods("grpc.reflection.v1.ServerReflection/ServerReflectionInfo").permitAll()
                    .allRequests().denyAll()
            }
            .build()
    }
}