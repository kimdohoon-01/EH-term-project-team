CREATE TABLE IF NOT EXISTS menu_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    price INT NOT NULL CHECK (price > 0),
    stock BIGINT NOT NULL CHECK (stock >= 0),
    active BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS stock_reservations (
    order_id VARCHAR(64) PRIMARY KEY,
    status VARCHAR(12) NOT NULL CHECK (status IN ('RESERVED', 'RELEASED'))
);

CREATE TABLE IF NOT EXISTS stock_reservation_lines (
    order_id VARCHAR(64) NOT NULL,
    menu_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price INT NOT NULL CHECK (unit_price > 0),
    PRIMARY KEY (order_id, menu_id),
    FOREIGN KEY (order_id) REFERENCES stock_reservations(order_id),
    FOREIGN KEY (menu_id) REFERENCES menu_items(id)
);
