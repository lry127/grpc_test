package us.leaf3stones.grpc_test

import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.Id


@Entity
class SecUser(
    @Id
    @Column(columnDefinition = "VARCHAR(255) COLLATE utf8mb4_bin")
    var subject: String,

    var password: String,

    @ElementCollection
    var roles: Set<String>
)

