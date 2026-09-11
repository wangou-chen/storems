CREATE DATABASE IF NOT EXISTS `tb_product`;
USE `tb_product`;

CREATE TABLE IF NOT EXISTS `product` (
                                         `id` int NOT NULL AUTO_INCREMENT,
                                         `product_name` varchar(100) DEFAULT NULL COMMENT '商品名称',
    `price` double(15,3) DEFAULT NULL COMMENT '商品价格',
    `stock` int NOT NULL DEFAULT 0 COMMENT '库存数量',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `product` (`product_name`, `price`, `stock`) VALUES
                                                             ('上衣', 100.00, 100),
                                                             ('裤子', 50.00, 80),
                                                             ('毛衣', 200.00, 50),
                                                             ('帽子', 30.00, 30),
                                                             ('鞋', 200.00, 60);


CREATE DATABASE IF NOT EXISTS `tb_inventory`;
USE `tb_inventory`;

CREATE TABLE IF NOT EXISTS `inventory_record` (
                                                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                                                  `product_id` BIGINT NOT NULL COMMENT '商品ID',
                                                  `type` VARCHAR(20) NOT NULL COMMENT 'INBOUND 或 OUTBOUND',
    `quantity` INT NOT NULL COMMENT '入库/出库数量',
    `operator` VARCHAR(100) DEFAULT NULL COMMENT '操作人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;