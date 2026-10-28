package com.ieum.grocery

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class GroceryApplicationTest : PostgresTestSupport() {

	// ddl-auto: validate 라서 엔티티와 schema.sql 이 어긋나면 컨텍스트가 뜨지 않는다.
	@Test
	fun `엔티티가 스키마와 일치한다`() {
	}
}
