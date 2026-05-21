package me.seroperson.reload.live.gradle

import io.grpc.CallOptions
import io.grpc.Grpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.MethodDescriptor
import io.grpc.TlsChannelCredentials
import io.grpc.reflection.v1alpha.ServerReflectionGrpc
import io.grpc.reflection.v1alpha.ServerReflectionRequest
import io.grpc.reflection.v1alpha.ServerReflectionResponse
import io.grpc.stub.ClientCalls
import io.grpc.stub.StreamObserver
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.testkit.runner.GradleRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

abstract class LiveReloadTestBase {
    private val client = OkHttpClient()

    /** Hard cap on a single verify-after-reload poll loop. */
    val reloadTimeoutMillis: Long = 60_000L
    private val pollIntervalMillis: Long = 500L

    fun initGradleRunner(
        command: String,
        projectDir: File,
        env: Map<String, String> = mapOf(),
    ): GradleRunner {
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withGradleVersion("8.14.3")
        runner.withPluginClasspath()
        runner.withProjectDir(projectDir)
        runner.withEnvironment(
            mapOf("GRADLE_OPTS" to "--add-opens=java.base/java.nio=ALL-UNNAMED") + env,
        )
        runner.withArguments(
            command,
            "--info",
            "--watch-fs",
            "--stacktrace",
            "-Dorg.gradle.vfs.verbose=true",
            "-Dorg.gradle.native=true",
        )
        return runner
    }

    /** Poll an action until it returns true, the build dies, or the deadline elapses. */
    private fun pollUntil(
        label: String,
        isBuildRunning: AtomicBoolean,
        timeoutMillis: Long = reloadTimeoutMillis,
        attempt: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (!isBuildRunning.get()) {
                return false
            }
            try {
                if (attempt()) {
                    return true
                }
            } catch (ex: Exception) {
                println("$label exception: ${ex.message}")
            }
            Thread.sleep(pollIntervalMillis)
        }
        println("$label timed out after ${timeoutMillis}ms")
        return false
    }

    fun runUntil(
        isBuildRunning: AtomicBoolean,
        url: String,
        expectedStatus: Int,
        expectedBody: String,
        timeoutMillis: Long = reloadTimeoutMillis,
    ): Boolean =
        pollUntil("HTTP $url", isBuildRunning, timeoutMillis) {
            val request: Request = Request.Builder().url(url).build()
            val (code, body) =
                client.newCall(request).execute().use { response ->
                    response.code to response.body.string()
                }
            println("Requesting $url, got $code and $body")
            expectedStatus == code && expectedBody == body
        }

    /**
     * Makes a unary GRPC call using generic byte array marshalling. This allows testing GRPC servers
     * without generated code.
     */
    fun grpcCall(
        host: String,
        port: Int,
        serviceName: String,
        methodName: String,
        request: ByteArray,
    ): ByteArray {
        val channel: ManagedChannel =
            ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
        try {
            val methodDescriptor =
                MethodDescriptor
                    .newBuilder<ByteArray, ByteArray>()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("$serviceName/$methodName")
                    .setRequestMarshaller(ByteArrayMarshaller())
                    .setResponseMarshaller(ByteArrayMarshaller())
                    .build()

            return ClientCalls.blockingUnaryCall(channel, methodDescriptor, CallOptions.DEFAULT, request)
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    /** Runs GRPC call until expected response is received. */
    fun runGrpcUntil(
        isBuildRunning: AtomicBoolean,
        host: String,
        port: Int,
        serviceName: String,
        methodName: String,
        request: ByteArray,
        expectedResponse: ByteArray,
    ): Boolean =
        pollUntil("GRPC $serviceName/$methodName", isBuildRunning) {
            val response = grpcCall(host, port, serviceName, methodName, request)
            println("GRPC call to $serviceName/$methodName, got ${response.contentToString()}")
            response.contentEquals(expectedResponse)
        }

    /** Makes a server-streaming GRPC call, collecting all response messages until the stream ends. */
    fun serverStreamingCall(
        host: String,
        port: Int,
        serviceName: String,
        methodName: String,
        request: ByteArray,
    ): List<ByteArray> {
        val channel: ManagedChannel =
            ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
        try {
            val methodDescriptor =
                MethodDescriptor
                    .newBuilder<ByteArray, ByteArray>()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName("$serviceName/$methodName")
                    .setRequestMarshaller(ByteArrayMarshaller())
                    .setResponseMarshaller(ByteArrayMarshaller())
                    .build()

            val iterator =
                ClientCalls.blockingServerStreamingCall(
                    channel,
                    methodDescriptor,
                    CallOptions.DEFAULT,
                    request,
                )
            val collected = mutableListOf<ByteArray>()
            iterator.forEachRemaining { collected.add(it) }
            return collected
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    /** Runs a server-streaming GRPC call until the collected sequence equals the expected one. */
    fun runServerStreamingUntil(
        isBuildRunning: AtomicBoolean,
        host: String,
        port: Int,
        serviceName: String,
        methodName: String,
        request: ByteArray,
        expected: List<ByteArray>,
    ): Boolean =
        pollUntil("GRPC streaming $serviceName/$methodName", isBuildRunning) {
            val collected = serverStreamingCall(host, port, serviceName, methodName, request)
            val matched =
                collected.size == expected.size &&
                    collected.zip(expected).all { (a, b) -> a.contentEquals(b) }
            if (!matched) {
                println(
                    "Streaming response did not match yet, got ${collected.map { String(it) }}, " +
                        "expected ${expected.map { String(it) }}",
                )
            }
            matched
        }

    /** Makes a unary gRPC call over TLS, trusting the provided cert file. */
    fun grpcTlsCall(
        host: String,
        port: Int,
        serviceName: String,
        methodName: String,
        request: ByteArray,
        trust: File,
    ): ByteArray {
        val credentials = TlsChannelCredentials.newBuilder().trustManager(trust).build()
        val channel: ManagedChannel = Grpc.newChannelBuilderForAddress(host, port, credentials).build()
        try {
            val methodDescriptor =
                MethodDescriptor
                    .newBuilder<ByteArray, ByteArray>()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("$serviceName/$methodName")
                    .setRequestMarshaller(ByteArrayMarshaller())
                    .setResponseMarshaller(ByteArrayMarshaller())
                    .build()

            return ClientCalls.blockingUnaryCall(channel, methodDescriptor, CallOptions.DEFAULT, request)
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    /** Runs a TLS unary GRPC call until expected response is received. */
    fun runGrpcTlsUntil(
        isBuildRunning: AtomicBoolean,
        host: String,
        port: Int,
        serviceName: String,
        methodName: String,
        request: ByteArray,
        expectedResponse: ByteArray,
        trust: File,
    ): Boolean =
        pollUntil("TLS GRPC $serviceName/$methodName", isBuildRunning) {
            val response = grpcTlsCall(host, port, serviceName, methodName, request, trust)
            val matched = response.contentEquals(expectedResponse)
            if (!matched) {
                println("TLS GRPC mismatch, got ${String(response)}")
            }
            matched
        }

    /** Bidi-streaming reflection list-services call through the proxy. */
    fun reflectionListServices(
        host: String,
        port: Int,
    ): Set<String> {
        val channel: ManagedChannel =
            ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
        try {
            val future = CompletableFuture<ServerReflectionResponse>()
            val responseObserver =
                object : StreamObserver<ServerReflectionResponse> {
                    override fun onNext(value: ServerReflectionResponse) {
                        future.complete(value)
                    }

                    override fun onError(t: Throwable) {
                        future.completeExceptionally(t)
                    }

                    override fun onCompleted() {}
                }
            val requestObserver =
                ServerReflectionGrpc.newStub(channel).serverReflectionInfo(responseObserver)
            requestObserver.onNext(ServerReflectionRequest.newBuilder().setListServices("").build())
            requestObserver.onCompleted()
            return future
                .get(10, TimeUnit.SECONDS)
                .listServicesResponse
                .serviceList
                .map { it.name }
                .toSet()
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    /** Retries reflection list-services until the expected service shows up. */
    fun runReflectionUntil(
        isBuildRunning: AtomicBoolean,
        host: String,
        port: Int,
        expectedService: String,
    ): Boolean =
        pollUntil("reflection list services", isBuildRunning) {
            val services = reflectionListServices(host, port)
            val matched = expectedService in services
            if (!matched) {
                println("Reflection services so far: $services")
            }
            matched
        }

    /** Simple marshaller for byte arrays - allows generic GRPC calls without proto. */
    private class ByteArrayMarshaller : MethodDescriptor.Marshaller<ByteArray> {
        override fun stream(value: ByteArray): InputStream = ByteArrayInputStream(value)

        override fun parse(stream: InputStream): ByteArray = stream.readBytes()
    }
}
