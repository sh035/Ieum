package com.ieum.account

import com.ieum.account.domain.entity.Account
import com.ieum.account.repository.AccountRepository
import com.ieum.proto.account.v1.AccountInternalServiceGrpc
import com.ieum.proto.account.v1.GetNicknamesRequest
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.grpc.client.GrpcChannelFactory
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@AutoConfigureTestGrpcTransport
class AccountInternalGrpcControllerTest @Autowired constructor(
	channels: GrpcChannelFactory,
	private val accountRepository: AccountRepository,
) : PostgresRedisTestSupport() {

	private val stub = AccountInternalServiceGrpc.newBlockingStub(channels.createChannel("account"))

	@BeforeEach
	fun clean() {
		accountRepository.deleteAll()
	}

	private fun saveAccount(email: String, nickname: String): UUID =
		requireNotNull(
			accountRepository.save(
				Account(email = email, password = "hashed", nickname = nickname, birthDate = LocalDate.of(1995, 3, 21)),
			).accountId,
		)

	private fun getNicknames(vararg accountIds: Any) =
		stub.getNicknames(GetNicknamesRequest.newBuilder().addAllAccountIds(accountIds.map { it.toString() }).build()).nicknamesMap

	@Test
	fun `계정별 닉네임을 돌려준다`() {
		val mom = saveAccount("mom@ieum.com", "엄마")
		val dad = saveAccount("dad@ieum.com", "아빠")

		assertEquals(mapOf(mom.toString() to "엄마", dad.toString() to "아빠"), getNicknames(mom, dad))
	}

	@Test
	fun `없거나 탈퇴한 계정은 빠진다`() {
		val mom = saveAccount("mom@ieum.com", "엄마")
		val left = accountRepository.save(
			Account(email = "left@ieum.com", password = "hashed", nickname = "떠난", birthDate = LocalDate.of(1990, 1, 1)),
		).apply { delete() }
		accountRepository.save(left)

		assertEquals(mapOf(mom.toString() to "엄마"), getNicknames(mom, requireNotNull(left.accountId), UUID.randomUUID()))
	}

	@Test
	fun `UUID 형식이 아니면 INVALID_ARGUMENT`() {
		val e = assertFailsWith<StatusRuntimeException> { getNicknames("not-a-uuid") }

		assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
	}
}
