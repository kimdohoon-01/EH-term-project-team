-- Dohoon: user, order, notification databases only.

CREATE DATABASE IF NOT EXISTS orderandgo_user CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE orderandgo_user;
CREATE TABLE IF NOT EXISTS app_users (
 id VARCHAR(36) PRIMARY KEY, email VARCHAR(120) UNIQUE NOT NULL,
 name VARCHAR(30) NOT NULL, password_hash VARCHAR(100) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS sessions (
 token_hash VARCHAR(64) PRIMARY KEY, user_id VARCHAR(36) NOT NULL,
 expires_at BIGINT NOT NULL, FOREIGN KEY (user_id) REFERENCES app_users(id)
) ENGINE=InnoDB;

CREATE DATABASE IF NOT EXISTS orderandgo_order CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE orderandgo_order;
CREATE TABLE IF NOT EXISTS cafe_orders (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36) NOT NULL,
 request_key VARCHAR(100) NOT NULL, request_fingerprint VARCHAR(2000) NOT NULL,
 payment_method VARCHAR(30) NOT NULL, status VARCHAR(30) NOT NULL,
 total INT NOT NULL DEFAULT 0, message VARCHAR(300) NOT NULL,
 created_at VARCHAR(40) NOT NULL, notification_sent BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE(user_id,request_key), INDEX orders_user_idx(user_id,created_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS order_lines (
 order_id VARCHAR(36) NOT NULL, menu_id VARCHAR(50) NOT NULL,
 name VARCHAR(100) NOT NULL DEFAULT '', price INT NOT NULL DEFAULT 0, quantity INT NOT NULL,
 PRIMARY KEY(order_id,menu_id), FOREIGN KEY (order_id) REFERENCES cafe_orders(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_backends (
 order_id VARCHAR(36) PRIMARY KEY, backend VARCHAR(30) NOT NULL,
 FOREIGN KEY (order_id) REFERENCES cafe_orders(id)
);

CREATE DATABASE IF NOT EXISTS orderandgo_notification CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
USE orderandgo_notification;
CREATE TABLE IF NOT EXISTS notifications (
 id VARCHAR(36) PRIMARY KEY, event_id VARCHAR(100) UNIQUE NOT NULL, user_id VARCHAR(36) NOT NULL,
 order_id VARCHAR(36) NOT NULL, title VARCHAR(100) NOT NULL, message VARCHAR(500) NOT NULL,
 is_read BOOLEAN NOT NULL DEFAULT FALSE, created_at VARCHAR(40) NOT NULL, INDEX notification_user_idx(user_id,created_at)
) ENGINE=InnoDB;

