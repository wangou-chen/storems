-- MySQL 8. Run with a UTF-8 connection. No existing rows are deleted or reset.
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS tb_product DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tb_product;
CREATE TABLE IF NOT EXISTS product (
    id INT NOT NULL AUTO_INCREMENT,
    product_name VARCHAR(100) DEFAULT NULL,
    price DOUBLE(15,3) DEFAULT NULL,
    stock INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Upgrade the original product table if stock is absent.
SET @storems_stock_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = 'tb_product' AND table_name = 'product' AND column_name = 'stock') = 0,
    'ALTER TABLE tb_product.product ADD COLUMN stock INT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE storems_stock_stmt FROM @storems_stock_sql;
EXECUTE storems_stock_stmt;
DEALLOCATE PREPARE storems_stock_stmt;

-- Seed only an empty product table. Re-running does not reset stock.
INSERT INTO product (product_name, price, stock)
SELECT seed.product_name, seed.price, seed.stock
FROM (
    SELECT CONVERT(0xE4B88AE8A1A3 USING utf8mb4) AS product_name, 100.00 AS price, 100 AS stock
    UNION ALL SELECT CONVERT(0xE8A3A4E5AD90 USING utf8mb4), 50.00, 80
    UNION ALL SELECT CONVERT(0xE6AF9BE8A1A3 USING utf8mb4), 200.00, 50
    UNION ALL SELECT CONVERT(0xE5B8BDE5AD90 USING utf8mb4), 30.00, 30
    UNION ALL SELECT CONVERT(0xE99E8B USING utf8mb4), 200.00, 60
) seed
WHERE NOT EXISTS (SELECT 1 FROM product);

CREATE DATABASE IF NOT EXISTS tb_inventory DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tb_inventory;
CREATE TABLE IF NOT EXISTS inventory_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    operator VARCHAR(100) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- The user schema below is also available separately in sql/user.sql.
CREATE DATABASE IF NOT EXISTS tb_user
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE tb_user;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'OPERATOR',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    active_username VARCHAR(50) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN username ELSE NULL END) STORED,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_live_username (active_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_token (
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    PRIMARY KEY (token_hash),
    KEY idx_user_token_user_id (user_id),
    KEY idx_user_token_expires_at (expires_at),
    CONSTRAINT fk_user_token_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AdminInitializer creates admin / adminadmin on the first user-service startup.
-- Existing user schemas are upgraded by AdminInitializer on startup.
