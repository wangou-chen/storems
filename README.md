# storems · 仓序仓库管理系统

一个用于课程实践的 Java 微服务仓库管理系统，包含可视化网页、用户注册登录、管理员用户管理、商品查询、库存出入库与流水追溯。

本文以 **Windows + IDEA + MySQL 8 + JDK 8** 为例，从空数据库开始运行。数据库和前后端均在本机运行，无需 Docker 或 Kubernetes。

## 1. 准备环境

| 软件 | 要求 |
| --- | --- |
| JDK | **8**，IDEA 的 Project SDK、Maven Runner 和运行配置均使用 JDK 8 |
| Maven | 3.x（本项目使用过 3.9.10），可使用 IDEA 配置的 Maven |
| MySQL | **8.0**，安装并启动 MySQL Server，默认 3306 端口 |
| Node.js | **22.12 或更高版本**，已验证 22.17 |
| npm | 随 Node.js 安装 |
| Git / IDEA | 用于获取代码和运行服务 |

PowerShell 检查：

```powershell
java -version
mvn -version
mysql --version
node --version
npm.cmd --version
```

`mvn -version` 中的 Java 版本也必须为 1.8。命令找不到时，将对应软件的 bin 目录加入 PATH，或使用 IDEA / MySQL 安装目录里的工具。JAVA_HOME 应指向 JDK 根目录，不是 bin、java.exe 或 JRE。

## 2. 获取项目并导入

```powershell
git clone --branch feat/inventory-service https://github.com/syt22/storems.git
cd storems
```

当前完整代码位于 `feat/inventory-service` 分支；如果已经合并到默认分支，可省略 `--branch feat/inventory-service`。上游参考仓库为 `wangou-chen/storems`，可能不包含本项目新增功能。

IDEA → Open → 选择仓库根目录的 `pom.xml`，按 Maven 项目导入。重新加载 Maven，等待依赖下载完成。在 Settings → Build, Execution, Deployment → Compiler → Annotation Processors 中启用注解处理，供已有 Lombok 实体和控制器使用。

```text
storems/
├── eureka-service/       注册中心
├── gateway-service/      网关与登录凭证校验
├── product-service/      商品数据库服务
├── product-client/       商品调用服务与降级处理
├── inventory-service/    库存业务与流水
├── user-service/         注册、登录、管理员与用户管理
├── frontend/             Vue 3 + Element Plus 网页
├── sql/init.sql          首次运行：一次创建全部三个数据库
├── sql/user.sql          可选：仅初始化用户库
└── pom.xml               Maven 父工程
```

## 3. 初始化数据库

**推荐使用 MySQL 命令行的 SOURCE，避免 Windows PowerShell 不支持 `<` 输入重定向的问题。**

先在 PowerShell 登录 MySQL（输入安装 MySQL 时设置的密码）：

```powershell
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -P 3306 -u root -p
```

看到 `mysql>` 后执行，将路径换成你自己的仓库路径，使用正斜杠：

```sql
SOURCE D:/Users/s1816/Desktop/云原生技术实践/storems/sql/init.sql;
```

也可以在 MySQL Workbench / Navicat 中打开 UTF-8 编码的 `sql/init.sql`，执行整个文件。执行路径带空格遇到问题时，优先使用数据库工具导入文件。

初始化结果：

| 数据库 | 表 | 内容 |
| --- | --- | --- |
| tb_product | product | 商品、价格、当前库存 |
| tb_inventory | inventory_record | 入库和出库历史 |
| tb_user | sys_user | 用户、密码哈希、角色、启用和逻辑删除状态 |
| tb_user | user_token | Token 摘要、用户 ID 和过期时间 |

在 MySQL 验证：

```sql
SHOW TABLES FROM tb_product;
SHOW TABLES FROM tb_inventory;
SHOW TABLES FROM tb_user;
SELECT id, product_name, price, stock FROM tb_product.product;
```

空库首次初始化包含上衣、裤子、毛衣、帽子、鞋共 5 种商品，库存依次为 100、80、50、30、60，共 **320 件**。流水表和用户表此时可以为空。示例商品使用 UTF-8 字节字面量，减少 Windows 终端编码造成的中文乱码。

**脚本可顺序重复执行：**不删除数据库、不清空流水、不覆盖密码或库存；仅当商品表为空时插入示例商品，不重复插入。已有商品表缺少 stock 时会补充该列，旧商品库存默认 0，不会凭空补货。请不要并发执行初始化脚本。

`init.sql` 已包含 `user.sql` 的建表内容，首次使用只需执行 `init.sql`。仅补用户库时才单独执行 `user.sql`。`CREATE TABLE IF NOT EXISTS` 不会自动重建旧表，旧用户表的字段和索引升级由下一步用户服务启动时完成。

## 4. 配置数据库连接

需要修改以下 **三个文件**，不是只改一个：

- `product-service/src/main/resources/application.yml` → tb_product
- `inventory-service/src/main/resources/application.yml` → tb_inventory
- `user-service/src/main/resources/application.yml` → tb_user

仓库默认 MySQL 用户为 root、密码为 `123456`。如果你本机密码不同，请分别替换三个文件的 password；密码建议加双引号。示例（用户服务）：

```yaml
spring:
  application:
    name: user-service
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/tb_user?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: root
    password: "你的MySQL密码"
    driver-class-name: com.mysql.jdbc.Driver
```

只修改对应 datasource 配置，保留各文件原有端口、服务名及 Eureka 配置。如果 MySQL 不是 3306 或不在本机，同时调整三个 URL。不要把三个服务都连接到 tb_user。

课程本地演示可以使用 root。使用独立数据库账号时，初始化需要建库、建表权限；业务需要 SELECT/INSERT/UPDATE/DELETE；用户服务启动升级旧表还需要 ALTER/INDEX 权限。不要提交个人数据库密码。

## 5. 用户表升级与默认管理员

首次启动 user-service 会自动：

1. 为旧 sys_user 表补充 role、enabled、deleted、active_username 字段。
2. 创建仅对未删除用户名生效的唯一索引，移除旧 username 或 `(username, deleted)` 唯一索引。
3. 若没有未删除的 admin 账号，创建默认管理员，并使用 BCrypt 保存密码哈希。

| 默认网站管理员 | 值 |
| --- | --- |
| 用户名 | **admin** |
| 密码 | **adminadmin** |

这是**网站账号**，与 MySQL 的 root 密码无关。重启不会重置管理员密码，也不会覆盖已有数据。如存在同名普通账号，服务会明确报错，需人工处理冲突，不会自动将其升级为管理员。请等用户服务完全启动、无错误后再登录。已有数据库建议先备份，首次升级时只启动一个 user-service 实例。

管理员可创建操作员、启用/禁用、重置密码、删除用户。公开注册一律创建操作员；操作员不能调用管理员接口。禁止禁用管理员，禁止删除管理员或自己。

删除前需二次确认；删除是逻辑删除，历史流水保留。**未删除用户名（包括禁用账号）不能重复注册，已删除用户名可重新注册**，新账号使用新 ID。禁用、删除或重置密码会撤销该账号全部 Token；退出只撤销当前 Token。默认管理员可在用户管理页重置自己的密码，随后重新登录。

## 6. 编译并启动 Java 服务

仓库根目录执行：

```powershell
mvn clean package -DskipTests
```

第一次需要联网下载依赖。该命令只编译打包 Java，不构建网页。看到 BUILD SUCCESS 后在 IDEA 中按下表顺序启动，已运行的服务不要重复启动。

| 顺序 | 模块 | 启动类 | 端口 |
| --- | --- | --- | --- |
| 1 | eureka-service | EurekaServiceApplication | 8888 |
| 2 | product-service | ProductServiceApplication | 8010 |
| 3 | product-client | ProductClientApplication | 8018 |
| 4 | user-service | UserServiceApplication | 8030 |
| 5 | inventory-service | InventoryServiceApplication | 8020 |
| 6 | gateway-service | GatewayServiceApplication | 9999 |

MySQL 必须先启动。打开 http://localhost:8888，等待上述服务注册为 UP。每个服务控制台应出现 Started ...，用户服务还需确认初始化无异常。服务刚启动时注册信息可能尚未刷新，可稍等后重试查询。

也可在各自独立终端运行对应 jar，例如：

```powershell
java -jar user-service/target/user-service.jar
```

其他模块同理，实际 jar 文件名以 target 目录为准。Eureka 是注册中心页面，9999 是 API 网关，都不是管理网页地址。

## 7. 启动网页

在另一个 PowerShell 中进入 **frontend** 目录：

```powershell
cd frontend
npm.cmd ci --offline=false
npm.cmd run dev
```

保持终端运行，在浏览器打开 **http://127.0.0.1:5173**。以 admin / adminadmin 登录即可看到“用户管理”；普通操作员只看到库存页面。网页开发服务器仅监听本机。

`package-lock.json` 已随源码提供，使用 npm ci 安装确定的依赖。没有 Node.js 时不能启动网页。开发请求使用 `/api` 前缀，Vite 自动转发到本机 9999 端口并移除前缀，无需改跨域配置。

前端构建与依赖检查（仍在 frontend 目录）：

```powershell
npm.cmd run test:api
npm.cmd run build
npm.cmd audit --offline=false
```

构建产物位于 `frontend/dist/`。超过 500 kB 的 chunk 提示是体积提醒，不代表构建失败。`npm audit` 必须在 frontend 目录运行，否则可能报 ENOLOCK。审计结果以本次实际输出为准。

## 8. 第一次验收

1. 管理员登录，确认商品共 5 种（已有数据库按实际数据）。
2. 商品库存页选上衣，记录当前库存，入库 10 再出库 10，最终应恢复原值。
3. 流水应增加两条，操作人自动为当前登录用户，不能由表单伪造。
4. 搜索商品、按类型和日期筛选流水，刷新浏览器应保持当前标签页的登录状态。
5. 用户管理创建临时账号，同名创建应失败；禁用后无法登录，启用后可重新登录。
6. 重置临时账号密码，旧密码失败、新密码成功。
7. 删除弹窗取消时用户仍保留；确认删除后列表隐藏，再次注册同名账号应成功；重复删除、重新注册也应成功。
8. 退出登录回到登录页。普通操作员不显示用户管理，也不能直接调用管理接口。

网页请求失败会显示错误，不把缺失数据当成库存 0。点击刷新重试；503 需检查对应 Java 服务日志。

## 9. API 与 PowerShell 排查

所有下列路径均通过 Gateway `http://localhost:9999` 访问。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /user/register | 匿名注册，JSON username/password |
| POST | /user/login | 匿名登录，返回随机 Token，有效期 7200 秒 |
| GET | /user/me | 当前用户 ID、用户名和角色 |
| POST | /user/logout | 撤销当前 Token，返回 204 |
| GET | /product/queryAllProduct | 商品列表 |
| GET | /product/findByProductId/{id} | 商品详情 |
| GET | /inventory/records | 库存流水 |
| POST | /inventory/inbound?productId=1&quantity=10 | 入库 |
| POST | /inventory/outbound?productId=1&quantity=10 | 出库 |
| GET / POST | /user/admin/users | 管理员查询 / 创建操作员 |
| PUT | /user/admin/users/{id}/enabled | 管理员设置状态，JSON enabled 布尔值 |
| PUT | /user/admin/users/{id}/password | 管理员重置密码，JSON password |
| DELETE | /user/admin/users/{id} | 管理员逻辑删除用户 |

除注册、登录外需 `Authorization: Bearer <token>`；旧 `token=1` 已无效。401 表示凭证无效、过期或账号不可用；403 表示权限不足；409 表示用户名冲突；503 表示服务暂不可用。库存接口部分业务错误仍返回 HTTP 200 加字符串，网页已做对应处理。

```powershell
$body = @{ username = "admin"; password = "adminadmin" } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "http://localhost:9999/user/login" -ContentType "application/json" -Body $body
$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod -Uri "http://localhost:9999/user/me" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:9999/product/queryAllProduct" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:9999/inventory/records" -Headers $headers
```

库存路由保留 `/inventory` 前缀，不添加 StripPrefix。商品 `/product` 前缀由网关删除。内部 `/internal/auth/validate` 不配置网关公开路由。`POST /inventory/record` 已移除，流水仅由库存业务生成；出入库的 operator 来自登录身份，旧 operator 参数不再采信。

## 10. 常见问题

| 现象 | 排查 |
| --- | --- |
| 数据库连接失败 / Access denied | 确认 MySQL 在运行，三个 datasource 的密码、端口和库名均正确 |
| Unknown database / table does not exist | 先完整执行 sql/init.sql，再启动用户服务 |
| 用户表 ALTER 权限不足 | 为用户服务数据库账号提供升级所需 ALTER/INDEX 权限 |
| admin 登录失败 | 确认用户服务初始化成功；已有管理员密码不会因重启恢复默认 |
| JAVA_HOME 错误 | 指向 JDK 8 根目录，并检查 Maven Runner JRE |
| Lombok getter 或构造器编译错误 | IDEA 启用注解处理并重新加载 Maven |
| 端口已被占用 | 停止同模块的旧实例后重启，勿重复运行 |
| 页面首次出现 503 | 查看 Eureka 注册和商品/网关日志，等注册信息更新后刷新；持续失败时检查具体服务 |
| 页面提示身份服务不可用 | 确认 user-service 正常，库存服务出入库会调用用户服务 |
| 页面能打开但没有数据 | 保持 Vite 终端开启，确认 Gateway 9999 可达及全部业务服务运行 |
| Token 过期或被撤销 | 重新登录；已禁用或删除账号不能通过重新登录恢复 |
| npm audit 报 ENOLOCK | cd 到 storems/frontend，而不是 storems 根目录 |
| 内存不足 | 降低各 Java 运行配置堆内存，例如 -Xms64m -Xmx256m，关闭无关应用 |

## 11. 测试、部署交接与已知限制

Java 测试在仓库根目录运行 `mvn test`（首次需要联网获取测试插件）。前端请求测试在 frontend 运行 `npm.cmd run test:api`。可选浏览器模拟接口测试：

```powershell
# frontend 目录
npx.cmd playwright install chromium
npm.cmd test
```

浏览器测试使用模拟 API，不能替代真实数据库联调。人工验收按第 8 节执行。

生产部署由部署同学处理：使用 `npm run build` 的 dist，Web 服务将同源 `/api/` 反向代理到 Gateway 并移除前缀，保留 Authorization 头；仅开放网页和网关，业务端口仅内部可达。`npm run preview` 不包含开发代理。不要在容器中把 localhost 当成其他容器的地址，需调整所有 MySQL/Eureka 地址。更多网页部署配置见 [frontend/README.md](frontend/README.md)。

当前限制：库存为先读后覆盖，并发时可能丢失更新；商品库存更新和流水插入没有分布式事务；流水只记录操作人用户名，同名重新注册后不能只凭名字区分历史账号；列表在前端筛选分页，适合课程演示规模；未定时清理过期 Token；正在处理中且已认证的请求不会因随后撤销 Token 被追溯取消。

## 12. 提交与推送

务必提交 `frontend/package-lock.json`、全部源码、sql 和 README。不要提交 node_modules、dist、target、个人数据库密码、IDEA 本机数据源文件或日志。

```powershell
# 仓库根目录
git status
git diff --check
git diff --stat
```

检查并选择需要的文件提交。已有仓库历史跟踪了部分 `.idea` 文件，忽略规则不会自动取消跟踪；不要把本机 IDE 配置改动混入业务提交。当前功能分支推送示例：

```powershell
git add README.md .gitignore pom.xml sql frontend user-service inventory-service product-client product-service gateway-service eureka-service
git diff --cached --stat
git commit -m "feat: 完善仓库管理网页、用户管理及初始化文档"
git push -u origin feat/inventory-service
```

提交前核对实际分支名和远程仓库，示例不会替你执行 push。
