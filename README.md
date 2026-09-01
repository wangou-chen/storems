# storems
lab
云原生技术实践课程项目 | SpringCloud 微服务
JDK：**1.8**
数据库：**MySQL 8.0.x**
执行下面 SQL 脚本，创建商品业务数据库 `tb_product`，包含商品表与测试数据
```sql
CREATE DATABASE IF NOT EXISTS `tb_product`
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_0900_ai_ci;
USE `tb_product`;
CREATE TABLE IF NOT EXISTS `product` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `product_name` VARCHAR(100) DEFAULT NULL COMMENT '商品名称',
    `price` DOUBLE DEFAULT NULL COMMENT '商品价格',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB
  AUTO_INCREMENT=5
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
INSERT INTO `product` (`product_name`, `price`)
VALUES
    ('上衣', 100.00),
    ('裤子', 50.00),
    ('毛衣', 200.00),
    ('帽子', 30.00),
    ('鞋', 200.00);
SELECT * FROM `product`;
```