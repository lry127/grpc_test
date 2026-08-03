package us.leaf3stones.grpc_test

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.grpc.server.service.GrpcService
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import us.leaf3stones.grpc_test.proto.*
import java.util.*
import javax.crypto.SecretKey
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@GrpcService
class AuthService(
    @Qualifier("cachedUserDetailsService")
    private val cachedUserDetailsService: UserDetailsService,
    private val passwordEncoder: PasswordEncoder
) :
    AuthServiceGrpcKt.AuthServiceCoroutineImplBase() {
    companion object {
        private const val JWT_SECRET: String = "MySuperSecretKeyThatIsAtLeast32BytesLong"
        val signingKey: SecretKey = Keys.hmacShaKeyFor(JWT_SECRET.toByteArray())

        val accessTokenValidMillis = 15.minutes.inWholeMilliseconds
        val refreshTokenValidMillis = 90.days.inWholeMilliseconds

        private val refreshTokenLock = Mutex(false)

        private typealias ValueWithTimestamp = Pair<String, Long>

        private val refreshTokenMapper = mutableMapOf<String, ValueWithTimestamp>()

        private fun generateAccessToken(username: String, roles: String): ValueWithTimestamp {
            val expiryMillis = System.currentTimeMillis() + accessTokenValidMillis
            val accessToken = Jwts.builder().claim("roles", roles).expiration(Date(expiryMillis))
                .subject(username)
                .signWith(signingKey).compact()
            return Pair(accessToken, expiryMillis)
        }

        private suspend fun renewRefreshTokenInner(username: String): ValueWithTimestamp {
            val opaqueRefreshToken = UUID.randomUUID().toString()
            val expiryMillis = System.currentTimeMillis() + refreshTokenValidMillis
            val packedRefreshToken = Pair(opaqueRefreshToken, expiryMillis)
            try {
                refreshTokenLock.lock()
                refreshTokenMapper[username] = packedRefreshToken
            } finally {
                refreshTokenLock.unlock()
            }
            return packedRefreshToken
        }

        private suspend fun findRefreshToken(refreshToken: String): Pair<String, ValueWithTimestamp>? {
            return refreshTokenLock.withLock {
                val entry = refreshTokenMapper.entries.find {
                    it.value.first == refreshToken
                } ?: return null
                Pair(entry.key, entry.value)
            }
        }
    }


    override suspend fun login(request: LoginRequest): LoginResponse {
        val invalidCredentialException =
            StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("invalid username or password"))
        val user = try {
            cachedUserDetailsService.loadUserByUsername(request.username)
        } catch (_: UsernameNotFoundException) {
            throw invalidCredentialException
        }
        if (!passwordEncoder.matches(request.password, user.password)) {
            throw invalidCredentialException
        }
        val roles = user.authorities.joinToString(",")
        val accessToken = generateAccessToken(user.username, roles)
        val refreshToken = renewRefreshTokenInner(user.username)

        val resp = loginResponse {
            this.accessToken = accessToken {
                this.accessToken = accessToken.first
                this.expireMillis = accessToken.second
            }
            this.refreshToken = refreshToken {
                this.refreshToken = refreshToken.first
                this.expireMillis = refreshToken.second
            }
        }
        return resp
    }

    override suspend fun getAccessToken(request: GetAccessTokenRequest): GetAccessTokenResponse {
        val refreshTokenWithUsername = findRefreshToken(request.refreshToken)
        refreshTokenWithUsername
            ?: throw StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("refresh token not found"))
        val (username, refreshToken) = refreshTokenWithUsername

        if (refreshToken.second < System.currentTimeMillis()) {
            throw StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("refresh token already expired, call login() again"))
        }
        val newAccessToken = generateAccessToken(
            username,
            cachedUserDetailsService.loadUserByUsername(username).authorities.joinToString { "," })
        return getAccessTokenResponse {
            this.accessToken = accessToken {
                this.accessToken = newAccessToken.first
                this.expireMillis = newAccessToken.second
            }
        }
    }

    @SuppressWarnings("all")
    override suspend fun renewRefreshToken(request: RenewRefreshTokenRequest): RenewRefreshTokenResponse {
        val refreshTokenWithUsername = findRefreshToken(request.oldRefreshToken)
        val shutUpIntellijIdea = "refresh token not found"
        refreshTokenWithUsername ?: throw StatusRuntimeException(
            Status.UNAUTHENTICATED.withDescription(
                shutUpIntellijIdea
            )
        )
        val (username, refreshToken) = refreshTokenWithUsername

        if (refreshToken.second < System.currentTimeMillis()) {
            throw StatusRuntimeException(Status.INVALID_ARGUMENT.withDescription("refresh token already expired, call login() again"))
        }
        val newRefreshToken = renewRefreshTokenInner(username)
        val newAccessToken = generateAccessToken(
            username,
            cachedUserDetailsService.loadUserByUsername(username).authorities.joinToString { "," })

        return renewRefreshTokenResponse {
            this.refreshToken = refreshToken {
                this.refreshToken = newRefreshToken.first
                this.expireMillis = refreshToken.second
            }
            this.accessToken = accessToken {
                this.accessToken = newAccessToken.first
                this.expireMillis = newAccessToken.second
            }
        }

    }
}