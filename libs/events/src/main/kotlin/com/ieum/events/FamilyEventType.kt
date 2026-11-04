package com.ieum.events

// 다른 서비스가 가족 데이터를 정리하도록 family 가 outbox 로 보내는 이벤트다.
enum class FamilyEventType {
	FAMILY_MEMBER_LEFT,
	FAMILY_DELETED,
}
