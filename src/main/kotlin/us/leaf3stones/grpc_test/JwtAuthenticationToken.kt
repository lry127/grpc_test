package us.leaf3stones.grpc_test

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority

class JwtAuthenticationToken(
    private val principal: String,
    authorities: Collection<GrantedAuthority>
) : AbstractAuthenticationToken(authorities) {

    init {
        // Mark this token as trusted/authenticated right away
        super.setAuthenticated(true) 
    }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): Any = principal
}