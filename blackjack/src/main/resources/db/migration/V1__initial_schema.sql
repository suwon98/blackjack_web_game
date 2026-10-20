CREATE TABLE member (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 version BIGINT,
 username VARCHAR(50) UNIQUE,
 password VARCHAR(100),
 nickname VARCHAR(30) NOT NULL UNIQUE,
 role ENUM('GUEST','USER','ADMIN') NOT NULL,
 money BIGINT NOT NULL CHECK (money >= 0),
 provider VARCHAR(255), provider_id VARCHAR(255),
 created_at DATETIME(6), modified_at DATETIME(6)
);
CREATE TABLE room_snapshot (id VARCHAR(64) PRIMARY KEY, payload LONGTEXT NOT NULL, version BIGINT);
CREATE TABLE refresh_session (username VARCHAR(50) PRIMARY KEY, token_hash VARCHAR(64) NOT NULL, session_id VARCHAR(64) NOT NULL, expires BIGINT NOT NULL, version BIGINT);
CREATE TABLE round_receipt (
 id VARCHAR(160) PRIMARY KEY, username VARCHAR(50) NOT NULL, room_id VARCHAR(64) NOT NULL,
 round_number BIGINT NOT NULL, stake BIGINT NOT NULL, payout BIGINT NOT NULL, balance BIGINT NOT NULL, created_at BIGINT NOT NULL,
 INDEX receipt_user_created (username,created_at)
);
