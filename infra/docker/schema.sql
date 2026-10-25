-- Schema per service. Cross-service access is blocked by schema privileges.
CREATE SCHEMA account;
CREATE SCHEMA family;
CREATE SCHEMA planner;
CREATE SCHEMA shopping;
CREATE SCHEMA expense;
CREATE SCHEMA notification;

CREATE TABLE account.account
(
	account_id uuid PRIMARY KEY,
	email      varchar(255) NOT NULL UNIQUE,
	nickname   varchar(255) NOT NULL,
	password   varchar(255) NOT NULL,
	birth_date date         NOT NULL,
	created_at timestamp(6) NOT NULL,
	updated_at timestamp(6),
	is_deleted boolean      NOT NULL DEFAULT false
);

CREATE TABLE family.family
(
	family_id  uuid PRIMARY KEY,
	name       varchar(255) NOT NULL,
	created_at timestamp(6) NOT NULL,
	updated_at timestamp(6),
	is_deleted boolean      NOT NULL DEFAULT false
);

CREATE TABLE family.family_member
(
	family_member_id uuid PRIMARY KEY,
	family_id        uuid         NOT NULL REFERENCES family.family (family_id),
	account_id       uuid         NOT NULL,
	role             varchar(20)  NOT NULL,
	created_at       timestamp(6) NOT NULL,
	updated_at       timestamp(6),
	is_deleted       boolean      NOT NULL DEFAULT false
);

-- 내보낸 구성원(soft delete)이 다시 가입할 수 있도록 삭제되지 않은 행에만 건다.
CREATE UNIQUE INDEX uk_family_member_family_id_account_id
	ON family.family_member (family_id, account_id)
	WHERE is_deleted = false;

-- 내 가족 목록 조회용
CREATE INDEX idx_family_member_account_id ON family.family_member (account_id);
