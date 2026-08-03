package us.leaf3stones.grpc_test

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component

@Component
class SecUserDetailsService(private val secUserRepository: SecUserRepository) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val secUser = secUserRepository.findSecUserBySubject(username)
            ?: throw UsernameNotFoundException("can't find user with username: $username")
        return User.withUsername(secUser.subject).authorities(secUser.roles.map { SimpleGrantedAuthority(it) }).build()
    }
}
