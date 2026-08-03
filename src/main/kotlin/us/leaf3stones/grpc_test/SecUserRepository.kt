package us.leaf3stones.grpc_test

import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.data.repository.ListCrudRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service


interface SecUserRepository : ListCrudRepository<SecUser, Long> {
    fun findSecUserBySubject(subject: String): SecUser?
}

@Service
class SecUserService(private val secUserRepository: SecUserRepository, private val passwordEncoder: PasswordEncoder) {
    @Bean
    fun createDefault() = CommandLineRunner {
        if (secUserRepository.count() == 0L) {
            val user = createNormalUser("userA", "up")
            val admin = createAdmin("adminA", "ap")
            secUserRepository.saveAll(listOf(user, admin))
        }
    }

    fun createNormalUser(username: String, password: String): SecUser {
        return createSecUser(username, password, setOf("ROLE_USER"))
    }

    fun createAdmin(username: String, password: String): SecUser {
        return createSecUser(username, password, setOf("ROLE_USER", "ROLE_ADMIN"))
    }

    fun createSecUser(username: String, password: String, roles: Set<String>): SecUser {
        val user = SecUser(subject = username, password = passwordEncoder.encode(password)!!, roles = roles)
        secUserRepository.save(user)
        return user
    }

}
