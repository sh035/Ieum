package com.ieum.grpc.client

import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.junit.jupiter.api.AfterEach
import org.springframework.grpc.client.ChannelBuilderOptions
import org.springframework.grpc.client.GrpcChannelFactory

// 상대 서비스를 흉내 내는 in-process 서버를 띄우고, 거기에 붙는 채널 팩토리를 만든다.
abstract class InProcessGrpcTestSupport {

	private val name = InProcessServerBuilder.generateName()
	private var server: Server? = null
	private var channel: ManagedChannel? = null

	protected fun channelsFor(service: BindableService): GrpcChannelFactory {
		server = InProcessServerBuilder.forName(name).addService(service).build().start()
		val channel = InProcessChannelBuilder.forName(name).build().also { channel = it }

		return object : GrpcChannelFactory {
			override fun supports(target: String) = true
			override fun createChannel(target: String, options: ChannelBuilderOptions) = channel
		}
	}

	protected fun stopServer() {
		server?.shutdownNow()
	}

	@AfterEach
	fun tearDown() {
		channel?.shutdownNow()
		server?.shutdownNow()
	}
}
