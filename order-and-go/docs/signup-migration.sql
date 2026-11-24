-- Existing members are preserved. Run in HeidiSQL as DB administrator.
USE orderandgo_user;

CREATE TABLE IF NOT EXISTS user_addresses (
 user_id VARCHAR(36) PRIMARY KEY, address VARCHAR(200) NOT NULL,
 address_detail VARCHAR(100) NOT NULL DEFAULT '',
 FOREIGN KEY (user_id) REFERENCES app_users(id)
);
