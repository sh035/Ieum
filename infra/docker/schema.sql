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
