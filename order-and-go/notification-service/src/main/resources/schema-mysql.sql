CREATE TABLE IF NOT EXISTS notifications (
 id VARCHAR(36) PRIMARY KEY, event_id VARCHAR(100) UNIQUE NOT NULL, user_id VARCHAR(36) NOT NULL,
 order_id VARCHAR(36) NOT NULL, title VARCHAR(100) NOT NULL, message VARCHAR(500) NOT NULL,
 is_read BOOLEAN NOT NULL DEFAULT FALSE, created_at VARCHAR(40) NOT NULL, INDEX notification_user_idx(user_id,created_at)
) ENGINE=InnoDB;
