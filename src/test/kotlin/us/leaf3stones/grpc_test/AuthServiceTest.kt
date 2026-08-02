package us.leaf3stones.grpc_test

import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import us.leaf3stones.grpc_test.proto.*

class AuthServiceTest {

    private lateinit var userDetailsService: UserDetailsService
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var authService: AuthService

    private val testUsername = "userA"
    private val rawPassword = "password"
    private val encodedPassword = "encodedPassword"

    @BeforeEach
    fun setup() {
        userDetailsService = mock()
        passwordEncoder = mock()
        authService = AuthService(userDetailsService, passwordEncoder)

        // Setup default happy-path mock behavior
        val mockUser = User.withUsername(testUsername)
            .password(encodedPassword)
            .authorities("ROLE_USER")
            .build()

        whenever(userDetailsService.loadUserByUsername(testUsername)).thenReturn(mockUser)
        whenever(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true)
    }

    @Test
    fun `login returns valid tokens on successful authentication`() = runTest {
        val request = loginRequest {
            username = testUsername
            password = rawPassword
        }

        val response = authService.login(request)

        assertNotNull(response.accessToken.accessToken)
        assertTrue(response.accessToken.expireMillis > System.currentTimeMillis())

        assertNotNull(response.refreshToken.refreshToken)
        assertTrue(response.refreshToken.expireMillis > System.currentTimeMillis())
    }

    @Test
    fun `login throws UNAUTHENTICATED when username is not found`() = runTest {
        whenever(userDetailsService.loadUserByUsername(any())).thenThrow(UsernameNotFoundException("Not found"))

        val request = loginRequest {
            username = "unknownUser"
            password = rawPassword
        }

        val exception = assertThrows<StatusRuntimeException> {
            authService.login(request)
        }

        assertEquals(Status.Code.UNAUTHENTICATED, exception.status.code)
        assertEquals("invalid username or password", exception.status.description)
    }

    @Test
    fun `login throws UNAUTHENTICATED when password does not match`() = runTest {
        whenever(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false)

        val request = loginRequest {
            username = testUsername
            password = rawPassword
        }

        val exception = assertThrows<StatusRuntimeException> {
            authService.login(request)
        }

        assertEquals(Status.Code.UNAUTHENTICATED, exception.status.code)
        assertEquals("invalid username or password", exception.status.description)
    }

    @Test
    fun `getAccessToken successfully generates new access token using valid refresh token`() = runTest {
        // 1. Perform login to populate the companion object's in-memory map
        val loginReq = loginRequest {
            username = testUsername
            password = rawPassword
        }
        val loginResponse = authService.login(loginReq)
        val validRefreshToken = loginResponse.refreshToken.refreshToken

        // 2. Request new access token
        val refreshReq = getAccessTokenRequest {
            refreshToken = validRefreshToken
        }
        val response = authService.getAccessToken(refreshReq)

        assertNotNull(response.accessToken.accessToken)
        assertTrue(response.accessToken.expireMillis > System.currentTimeMillis())
        // Ensure it's actually a new token (though timestamps might be identical if executed instantly,
        // the JWT signature/payload generation proves it ran successfully).
        assertNotEquals(loginResponse.accessToken.accessToken, response.accessToken.accessToken)
    }

    @Test
    fun `getAccessToken throws UNAUTHENTICATED for unknown refresh token`() = runTest {
        val refreshReq = getAccessTokenRequest {
            refreshToken = "fake-or-expired-token"
        }

        val exception = assertThrows<StatusRuntimeException> {
            authService.getAccessToken(refreshReq)
        }

        assertEquals(Status.Code.UNAUTHENTICATED, exception.status.code)
        assertEquals("refresh token not found", exception.status.description)
    }

    @Test
    fun `renewRefreshToken issues both new refresh and access tokens`() = runTest {
        // 1. Perform login to get initial tokens
        val loginReq = loginRequest {
            username = testUsername
            password = rawPassword
        }
        val loginResponse = authService.login(loginReq)
        val oldRefreshToken = loginResponse.refreshToken.refreshToken

        // 2. Renew both tokens
        val renewReq = renewRefreshTokenRequest {
            this.oldRefreshToken = oldRefreshToken
        }
        val renewResponse = authService.renewRefreshToken(renewReq)

        assertNotNull(renewResponse.accessToken.accessToken)
        assertNotNull(renewResponse.refreshToken.refreshToken)

        // The new refresh token should be entirely different from the old one
        assertNotEquals(oldRefreshToken, renewResponse.refreshToken.refreshToken)
    }

    @Test
    fun `renewRefreshToken throws UNAUTHENTICATED for unknown old refresh token`() = runTest {
        val renewReq = renewRefreshTokenRequest {
            oldRefreshToken = "invalid-token"
        }

        val exception = assertThrows<StatusRuntimeException> {
            authService.renewRefreshToken(renewReq)
        }

        assertEquals(Status.Code.UNAUTHENTICATED, exception.status.code)
        assertEquals("refresh token not found", exception.status.description) // The variable shutUpIntellijIdea's value
    }
}