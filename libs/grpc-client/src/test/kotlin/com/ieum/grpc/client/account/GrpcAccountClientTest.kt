package com.ieum.grpc.client.account

import com.ieum.proto.account.v1.AccountInternalServiceGrpc
import com.ieum.proto.account.v1.GetNicknamesRequest
import com.ieum.proto.account.v1.GetNicknamesResponse
import com.ieum.web.exception.IeumException
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.grpc.client.ChannelBuilderOptions
import org.springframework.grpc.client.GrpcChannelFactory
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GrpcAccountClientTest {

	private val mom = UUID.randomUUID()
	private val dad = UUID.randomUUID()
	private val name = InProcessServerBuilder.generateName()
	private var server: Server? = null
	private var channel: ManagedChannel? = null

	@AfterEach
	fun tearDown() {
		channel?.shutdownNow()
		server?.shutdownNow()
	}

	// account 응답을 흉내 내는 in-process 서버에 붙은 클라이언트를 만든다.
	private fun clientRespondingWith(
		respond: (GetNicknamesRequest, StreamObserver<GetNicknamesResponse>) -> Unit,
	): GrpcAccountClient {
		server = InProcessServerBuilder.forName(name)
			.addService(object : AccountInternalServiceGrpc.AccountInternalServiceImplBase() {
				override fun getNicknames(request: GetNicknamesRequest, observer: StreamObserver<GetNicknamesResponse>) {
					respond(request, observer)
				}
			})
			.build()
			.start()
		val channel = InProcessChannelBuilder.forName(name).build().also { channel = it }

		return GrpcAccountClient(object : GrpcChannelFactory {
			override fun supports(target: String) = true
			override fun createChannel(target: String, options: ChannelBuilderOptions) = channel
		})
	}

	@Test
	fun `닉네임을 계정 UUID 기준으로 돌려준다`() {
		var asked = listOf<String>()
		val client = clientRespondingWith { request, observer ->
			asked = request.accountIdsList
			observer.onNext(GetNicknamesResponse.newBuilder().putNicknames(mom.toString(), "엄마").build())
			observer.onCompleted()
		}

		assertEquals(mapOf(mom to "엄마"), client.getNicknames(listOf(mom, dad)))
		assertEquals(listOf(mom.toString(), dad.toString()), asked)
	}

	@Test
	fun `빈 목록은 account를 부르지 않는다`() {
		var called = false
		val client = clientRespondingWith { _, observer ->
			called = true
			observer.onCompleted()
		}

		assertEquals(emptyMap(), client.getNicknames(emptyList()))
		assertFalse(called)
	}

	@Test
	fun `account 오류면 503`() {
		val client = clientRespondingWith { _, observer -> observer.onError(Status.UNAVAILABLE.asRuntimeException()) }

		val e = assertFailsWith<IeumException> { client.getNicknames(listOf(mom)) }
		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.errorCode.status)
	}
}
