# 仓序 · 仓库管理网页

提供登录、注册、库存概览、商品库存、出入库、流水筛选和分页、当前用户、退出登录，以及管理员用户管理。

## 管理员与账号管理

重启 user-service 后自动为旧用户表补充 `role`、`enabled` 字段，已有账号保持为启用的操作员。数据库账号需具有 ALTER 权限。首次启动如不存在 `admin`，创建默认管理员 **admin / adminadmin**，密码以 BCrypt 哈希保存。后续重启不会重置密码或覆盖账号；若已有同名普通账号则启动报错，需人工处理冲突，不会自动提权。

以 admin 登录后，左侧显示“用户管理”：支持用户名搜索、分页、创建操作员、启用/禁用和重置密码。公开注册和管理员创建的账号均为操作员，客户端不能指定管理员角色。所有 `/user/admin/users` 接口均由后端验证 ADMIN 权限，普通用户直接调用返回 403。

禁用用户会撤销其所有 Token，恢复启用后必须重新登录；重置密码也撤销所有 Token。管理员可以重置自己的密码，保存后退出登录。管理员账号禁止禁用和删除，不提供角色编辑。已在处理中且完成认证的请求不受后续撤销操作追溯取消。

删除用户需要二次确认，使用逻辑删除并撤销全部 Token。已删除账号从列表隐藏，用户名可重新注册，新账号使用新 ID；未删除账号（包括禁用账号）的用户名不可重复。禁止删除管理员或自己。历史库存流水保留，目前流水仅存用户名，同名新旧账号的历史操作不能仅凭用户名区分。

重启 user-service 自动增加 `deleted` 和生成列 `active_username`，建立仅对未删除账号生效的唯一索引 `uk_sys_user_live_username`，再移除旧的用户名索引及 `(username, deleted)` 索引。已删除记录的生成列为 NULL，允许反复删除并重新注册同名账号；此迁移不删除历史记录。请只在用户服务启动完成后测试。

删除验收：创建临时操作员 → 再次同名创建应返回 409 → 删除弹窗取消后账号仍在 → 确认删除后旧 Token 返回 401 → 再次同名注册成功且 ID 不同 → 再删除、再注册仍成功。

验收：以 admin 登录创建测试操作员 → 用新账号登录 → 管理员禁用 → 新账号请求返回 401 → 启用后重新登录 → 重置密码 → 旧密码失败、新密码成功。普通账号不显示管理菜单且无法调用管理 API。

## 本地运行

环境：Node.js 22.12 或更高版本，npm。Java 服务继续使用 JDK 8。

先启动 MySQL，再在 IDEA 中启动 Eureka、product-service、product-client、user-service、inventory-service 和 gateway-service。已有用户表和 Token 表无需重新初始化。从零运行只需执行 `sql/init.sql`，其中已包含 `sql/user.sql` 的用户库建表内容。完整准备步骤见仓库根目录 README。

本次后端更新需要重启 **UserServiceApplication** 和 **InventoryServiceApplication**。如果之前的网关认证及商品降级修复尚未加载，也要重启对应服务。

在此目录打开 PowerShell：

```powershell
npm ci --offline=false
npm run dev
```

浏览器访问 `http://127.0.0.1:5173`。开发服务器仅监听本机。前端通过 `/api` 请求，由 Vite 转发到 `http://127.0.0.1:9999` 并移除 `/api` 前缀；无需修改后端 CORS。

仓库提供 `package-lock.json`，其他机器使用 `npm ci --offline=false` 按锁定版本安装。修改依赖时才使用 npm install，并将更新的锁文件一起提交。

可使用之前创建的测试账号登录，也可从页面注册新账号。不要在代码中写入固定密码。

## 新增与调整的接口

| 请求 | 作用 |
| --- | --- |
| `GET /user/me` | 根据 Bearer Token 返回 userId 和 username，不返回密码信息 |
| `POST /user/logout` | 撤销当前 Token，成功返回 204，不影响其他登录会话 |
| `POST /inventory/inbound?productId=1&quantity=10` | 入库，操作人来自用户服务确认的登录身份 |
| `POST /inventory/outbound?productId=1&quantity=10` | 出库，操作人来自用户服务确认的登录身份 |

上述请求均需 `Authorization: Bearer <token>`。旧 `operator` 查询参数不再作为操作人来源。库存服务使用 Feign 调用 `/user/me`，因此出入库要求 user-service 可用。`POST /inventory/record` 已移除，避免绕过库存操作伪造流水；流水仅由成功出入库生成。

Token 存储在当前标签页的 sessionStorage，页面刷新后通过 `/user/me` 恢复身份。401 清除登录状态并返回登录页；503 显示服务异常，不将查询失败当作库存 0。退出失败时页面提示重试，不假装已经撤销凭证。

## 浏览器验收

1. 注册新用户；重复注册应显示用户名已存在。
2. 错误密码显示错误；正确密码进入库存概览。
3. 商品页搜索名称或 ID，核对库存与数据库一致。
4. 入库 10 件，确认库存增加；出库 10 件，确认库存恢复。出库必须二次确认。
5. 检查流水新增两条，操作人为登录用户名。数量 0、负数、超出显示库存的出库不应提交；后端的库存不足错误也应显示。
6. 按类型、日期和操作人筛选流水，查看分页。
7. 刷新浏览器，登录状态应保留；退出后再使用旧 Token 请求业务接口应返回 401。
8. Token 过期时操作自动回到登录页；服务停机时显示错误，不显示虚假库存。恢复服务后点击刷新。
9. 缩小到手机宽度，导航和登录表单可用，宽表格允许在表格区域横向滚动。

统计基于已加载的全部商品与流水；日期按浏览器本地时区显示。搜索、日期筛选、分页均在前端完成，适合课程演示规模。金额仅显示，不参与财务计算。

## 自动化验证

无需第三方依赖即可运行请求层测试：

```powershell
npm run test:api
```

安装依赖和 Playwright Chromium 后运行浏览器测试：

```powershell
npx playwright install chromium
npm test
npm run build
```

浏览器自动化测试使用模拟 API，验证交互、401/503、出入库请求及移动端布局，**不能替代真实 MySQL 和微服务联调**。依赖下载受阻时不能视为构建或浏览器测试通过。

## 交给部署同学

`npm run build` 生成 `dist/`，部署到静态 Web 服务。浏览器统一访问同源 `/api/`；反向代理到 Gateway 时移除该前缀，例如：

```nginx
location / {
    try_files $uri $uri/ /index.html;
}

location /api/ {
    proxy_pass http://gateway-service:9999/;
    proxy_set_header Host $host;
    proxy_set_header Authorization $http_authorization;
}
```

将上游地址调整为实际 Gateway 地址。`vite preview` 不会使用开发代理，生产验收需配置上述反向代理。业务服务端口应仅供内部访问，外部只开放前端和网关。正式环境通过 HTTPS 访问。

仍存在的后端限制：库存采用读后覆盖更新，未处理并发冲突；库存更新与流水写入没有跨服务事务。网页禁用重复提交只是交互保护，不代表解决这些后端一致性问题。
