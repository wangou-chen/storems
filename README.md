# storems

云原生技术实践课程项目，基于 Spring Boot 与 Spring Cloud 构建的简易商城微服务示例。目前包含服务注册与发现、商品查询、库存出入库、服务间 Feign 调用、Gateway 路由以及简单的统一鉴权。

> 本项目用于课程学习与微服务实践。当前鉴权仅判断请求参数 `token=1`，不适合直接用于生产环境。

## 技术栈

- JDK 8
- Maven 3.x
- MySQL 8.0.x
- Spring Boot 2.0.9.RELEASE
- Spring Cloud Finchley.SR2
- Spring Cloud Netflix Eureka
- Spring Cloud OpenFeign
- Spring Cloud Gateway
- MyBatis
- Hystrix

## 项目架构

```text
客户端
  │
  ▼
gateway-service :9999
  ├── /product/**   ──► product-client :8018 ──Feign──► product-service :8010 ──► tb_product
  └── /inventory/** ──► inventory-service :8020 ──Feign──► product-service :8010
                                                       └──► tb_inventory

所有服务通过 eureka-service :8888 完成注册与发现。
```

## 模块说明

| 模块 | 服务名 | 端口 | 作用 |
| --- | --- | ---: | --- |
| `eureka-service` | `eureka-server` | 8888 | Eureka 注册中心 |
| `product-service` | `product-service` | 8010 | 商品数据查询和库存数量更新，连接 `tb_product` |
| `product-client` | `product-client` | 8018 | 商品服务调用入口，通过 Feign 调用 `product-service` |
| `inventory-service` | `inventory-service` | 8020 | 入库、出库和库存流水查询，连接 `tb_inventory`，并通过 Feign 调用 `product-service` |
| `gateway-service` | `gateway-service` | 9999 | 对外统一入口、服务路由和简单 token 校验 |

项目目录：

```text
storems/
├── eureka-service/
├── gateway-service/
├── inventory-service/
├── product-client/
├── product-service/
├── sql/
│   └── init.sql
├── pom.xml
└── README.md
```

## 已实现功能

- 基于 Eureka 的服务注册与发现
- 查询全部商品、按 ID 查询商品
- 商品表新增 `stock` 库存字段
- `product-service` 提供库存更新接口
- 商品入库和出库
- 出库前校验商品是否存在、数量是否合法以及库存是否充足
- 出入库成功后写入 `inventory_record` 流水表
- `inventory-service` 通过 OpenFeign 调用 `product-service`
- Gateway 配置商品服务与库存服务路由
- Gateway 全局过滤器校验 `token=1`
- `product-client` 的 Feign/Hystrix 降级示例

## 环境要求

请先安装并确认以下环境可用：

| 软件 | 要求 |
| --- | --- |
| 操作系统 | Windows（本文以 Windows + IntelliJ IDEA 为例） |
| JDK | JDK 8 |
| Maven | Maven 3.x |
| MySQL | MySQL 8.0.x，默认端口 3306 |
| IDE | IntelliJ IDEA |

确认 Java 和 Maven 版本：

```powershell
java -version
mvn -version
```

`mvn -version` 显示的 Java 版本也应为 1.8。

## 获取并导入项目

```powershell
git clone https://github.com/wangou-chen/storems.git
cd storems
```

在 IntelliJ IDEA 中选择 **Open**，打开项目根目录下的 `pom.xml`，并以 Maven 项目导入。等待 Maven 下载全部依赖后再启动服务。

如 IDEA 使用的 JDK 不是 8，请检查：

- Project SDK
- Project language level
- Maven Runner JRE
- 各模块的 SDK

## 初始化数据库

仓库已经提供 `sql/init.sql`。首次运行前必须执行该文件，它会创建：

- `tb_product.product`：商品及当前库存
- `tb_inventory.inventory_record`：出入库流水

### 方法一：命令行导入

在项目根目录执行：

```powershell
mysql -u root -p < sql/init.sql
```

如果 PowerShell 当前版本不支持上述重定向，可在 MySQL 客户端中执行：

```sql
source D:/你的项目路径/storems/sql/init.sql;
```

也可以使用 IDEA Database、MySQL Workbench 或 Navicat 打开并运行 `sql/init.sql`。

### 初始化 SQL

当前 `sql/init.sql` 的核心内容如下：

```sql
CREATE DATABASE IF NOT EXISTS `tb_product`;
USE `tb_product`;

CREATE TABLE IF NOT EXISTS `product` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `product_name` VARCHAR(100) DEFAULT NULL COMMENT '商品名称',
    `price` DOUBLE(15,3) DEFAULT NULL COMMENT '商品价格',
    `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
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
```

> 注意：`CREATE DATABASE` 和 `CREATE TABLE` 可以重复执行，但示例 `INSERT` 会重复插入商品数据。数据库已经初始化后，不要再次执行插入部分；需要完全重置时，可先手动删除两个数据库再重新运行脚本。

## 配置说明

`product-service/src/main/resources/application.yml` 和 `inventory-service/src/main/resources/application.yml` 默认使用：

```yaml
spring:
  datasource:
    username: root
    password: 123456
```

如果本机 MySQL 用户名、密码、端口或时区不同，请修改两个服务各自的数据库连接配置。

默认数据库地址分别为：

```text
jdbc:mysql://127.0.0.1:3306/tb_product
jdbc:mysql://127.0.0.1:3306/tb_inventory
```

所有业务服务和网关默认连接以下 Eureka 地址：

```text
http://localhost:8888/eureka
```

## 编译项目

在项目根目录执行：

```powershell
mvn clean package -DskipTests
```

也可以直接在 IDEA 的 Maven 工具窗口中执行根项目的 `clean` 和 `package`。

## 启动顺序

建议严格按照以下顺序启动：

1. 启动 MySQL，并完成数据库初始化。
2. 启动 `eureka-service`。
3. 打开 <http://localhost:8888>，确认 Eureka 页面可访问。
4. 启动 `product-service`。
5. 启动 `product-client`。
6. 启动 `inventory-service`。
7. 回到 Eureka 页面，确认上述服务已经注册。
8. 最后启动 `gateway-service`。

在 IDEA 中分别运行各模块的启动类：

```text
EurekaServiceApplication
ProductServiceApplication
ProductClientApplication
InventoryServiceApplication
GatewayServiceApplication
```

如果网关提示 `Unable to find instance for product-client` 或 `Unable to find instance for inventory-service`，通常是网关启动时对应服务尚未注册。请确认 Eureka 页面中能够看到服务，随后重启网关。

## Gateway 鉴权说明

Gateway 的全局过滤器会检查 URL 查询参数 `token`，当前只有以下值可以通过：

```text
token=1
```

未携带 token 或值不为 `1` 时，网关返回 HTTP `401 Unauthorized`。

这是课程项目中的临时鉴权方式。测试经 Gateway 暴露的任何接口时，都需要添加 `?token=1`；如果接口本身已有查询参数，则添加 `&token=1`。

## 接口测试

以下命令适用于 Windows PowerShell，均通过 Gateway 的 `9999` 端口访问。

### 1. 查询全部商品

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:9999/product/queryAllProduct?token=1"
```

也可以直接在浏览器访问：

```text
http://localhost:9999/product/queryAllProduct?token=1
```

### 2. 按 ID 查询商品

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:9999/product/findByProductId/1?token=1"
```

### 3. 查询库存流水

按当前仓库的 Gateway 配置，库存路由未使用 `StripPrefix`，而 `InventoryController` 自身已有 `/inventory` 前缀，因此经网关访问时路径中需要出现两次 `inventory`：

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:9999/inventory/inventory/records?token=1"
```

### 4. 商品入库

以下请求将商品 `1` 入库 `10` 件，并记录操作人：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:9999/inventory/inventory/inbound?productId=1&quantity=10&operator=zhangsan&token=1"
```

成功响应：

```text
success
```

### 5. 商品出库

以下请求将商品 `1` 出库 `5` 件：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:9999/inventory/inventory/outbound?productId=1&quantity=5&operator=zhangsan&token=1"
```

成功响应：

```text
success
```

### 6. 测试库存不足

请求一个明显大于当前库存的出库数量：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:9999/inventory/inventory/outbound?productId=1&quantity=999999&operator=zhangsan&token=1"
```

预期响应：

```text
insufficient stock
```

库存不足时不会更新商品库存，也不会新增出库流水。

### 7. 测试鉴权失败

```powershell
try {
    Invoke-WebRequest -Uri "http://localhost:9999/product/queryAllProduct"
} catch {
    $_.Exception.Response.StatusCode.value__
}
```

预期状态码：

```text
401
```

### 直接访问库存服务（排查问题时使用）

绕过 Gateway 后不需要 `token=1`：

```text
GET  http://localhost:8020/inventory/records
POST http://localhost:8020/inventory/inbound?productId=1&quantity=10&operator=zhangsan
POST http://localhost:8020/inventory/outbound?productId=1&quantity=5&operator=zhangsan
```

## 简化库存网关地址（建议）

如果希望 README 和前端统一使用更自然的 `/inventory/records`、`/inventory/inbound` 和 `/inventory/outbound`，请为库存路由添加 `StripPrefix=1`：

```yaml
- id: inventory-service
  uri: lb://inventory-service
  filters:
    - StripPrefix=1
  predicates:
    - Path=/inventory/**
```

修改并重启 `gateway-service` 后，库存接口可改为：

```text
GET  http://localhost:9999/inventory/records?token=1
POST http://localhost:9999/inventory/inbound?productId=1&quantity=10&operator=zhangsan&token=1
POST http://localhost:9999/inventory/outbound?productId=1&quantity=5&operator=zhangsan&token=1
```

## 常见问题

### 服务未出现在 Eureka 中

- 确认 `eureka-service` 已启动并能访问 <http://localhost:8888>。
- 确认各服务的 `defaultZone` 指向 `http://localhost:8888/eureka`。
- 等待数秒后刷新 Eureka 页面。
- 确认对应端口没有被其他程序占用。

### 数据库连接失败

- 确认 MySQL 服务已启动并监听 3306 端口。
- 确认 `tb_product` 和 `tb_inventory` 已创建。
- 根据本机环境修改两个服务的数据库用户名和密码。
- 确认 MySQL 允许当前用户从 `127.0.0.1` 登录。

### 接口返回 401

确认请求经过 Gateway 时携带了 `token=1`。直接访问业务服务端口时不经过该过滤器。

### 库存接口返回 404

当前代码经 Gateway 访问库存服务时需要使用 `/inventory/inventory/...`。如需使用 `/inventory/...`，请按照上文为库存路由添加 `StripPrefix=1`。

## 当前实现说明

- `inventory-service` 先调用 `product-service` 更新库存，再写入库存流水。目前两个数据库之间没有分布式事务；如果更新库存成功后写流水失败，两边数据可能不一致。
- 当前库存更新方式为“先读取、再计算、再覆盖”，并发出入库时可能发生库存覆盖或超卖。
- 接口目前以字符串表示结果，尚未统一响应结构和异常状态码。
- 数据库账号密码直接写在本地配置文件中，仅适用于学习和本地开发。
- Gateway 的 `token=1` 是演示鉴权，不代表真实用户身份。

## TODO

- 新增 `user-service`，实现用户注册、登录和真正的身份认证
- 使用 JWT 或 Spring Security 替代固定的 `token=1`
- 增加角色与接口权限控制
- 统一 API 响应结构、参数校验和全局异常处理
- 为库存扣减增加数据库原子更新或乐观锁，避免并发超卖
- 引入事务消息、Saga 或其他方案保证跨服务数据一致性
- 为 Feign 调用增加超时、重试、熔断与明确的降级响应
- 使用环境变量或配置中心管理数据库账号等配置
- 增加单元测试、集成测试和接口文档
- 增加 Docker Compose，一键启动 MySQL 和全部服务
- 统一并简化 Gateway 路由前缀

## 说明

本项目为教学示例，重点展示 Spring Cloud 微服务之间的注册发现、网关转发和服务调用流程。部署到生产环境前，还需要补充安全、事务一致性、并发控制、可观测性和自动化部署等能力。
