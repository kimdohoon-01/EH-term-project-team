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
