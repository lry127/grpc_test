package us.leaf3stones.grpc_test

import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Service
import us.leaf3stones.grpc_test.proto.HelloReply
import us.leaf3stones.grpc_test.proto.HelloRequest
import us.leaf3stones.grpc_test.proto.SimpleGrpcKt
import kotlin.time.Duration.Companion.milliseconds


@Service
internal class GrpcServerService : SimpleGrpcKt.SimpleCoroutineImplBase() {

    override suspend fun sayHello(request: HelloRequest): HelloReply {
        log.info("Hello ${request.name}")

        // Note: In gRPC, it is generally better to throw StatusRuntimeException
        // rather than standard exceptions so the client receives the proper gRPC status code.
        require(!request.name.startsWith("error")) { "Bad name: ${request.name}" }

        if (request.name.startsWith("internal")) {
            throw StatusRuntimeException(Status.INTERNAL.withDescription("Internal error occurred"))
        }

        return HelloReply.newBuilder()
            .setMessage("Hello ==> ${request.name}")
            .build()
    }

    override fun streamHello(request: HelloRequest): Flow<HelloReply> = flow {
        log.info("Hello ${request.name}")

        for (count in 0 until 10) {
            val reply = HelloReply.newBuilder()
                .setMessage("Hello($count) ==> ${request.name}")
                .build()

            emit(reply) // Replaces responseObserver.onNext()

            delay(1000L.milliseconds) // Replaces Thread.sleep() - this suspends without blocking the underlying thread
        }
        // flow {} builder automatically handles completion, no need for onCompleted()
    }

    companion object {
        private val log: Log = LogFactory.getLog(GrpcServerService::class.java)
    }
}