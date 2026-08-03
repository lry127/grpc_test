package us.leaf3stones.grpc_test

import jakarta.persistence.*


@Entity
class SecUser(
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    var id: Long? = null,

    @Id
    @Column(columnDefinition = "VARCHAR(255) COLLATE utf8mb4_bin")
    var subject: String,

    var password: String,

    var roles: Set<String>
)

