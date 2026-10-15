---
handoff_schema: tavernweave/next/v1
project_id: saku-android-client
status: implementation
updated: 2026-10-15
---

# NEXT · sakuraxy 第三方安卓客户端

## 当前权威

- 总设计案：`总设计案.md`（含功能对照表：以网站功能为基准，逐项对照，不砍内容）
- 活动蓝图：`项目实现步骤蓝图.md`（BP-ROOT，P1–P6 已完成，P7 待启）
- 执行期持久权威蓝图预算：`0`
- 临时问题支线：仅真实问题触发，最多一层/同时一条，关闭后回归父步骤

## 已确认事实

- 站点 = WordPress + zibll v9.1；**双通道**：REST（结构化）+ 页面 HTML 解析（主题数据）
- 下载链：`?golink=<base64>&nonce=` → 跳转页 `location.href` = 真实网盘地址
- 登录：`/user-sign?tab=signin` → `admin-ajax.php` `action=user_signin`；**滑块验证为强制**
  （服务端校验拖动位置与缺口对齐，容差 <8px，实测 6px 过 / 50px 拒）；
  客户端已复刻网页流程（`SliderCaptcha`，数据全部来自用户真实拖动，不绕过），真机登录成功
- 本机工具链：JDK 21（`D:\Java`）、SDK（`D:\AndroidStudiozy`）、Gradle 9.3.1、代理 7897

## 最近证据

- v0.1（P1–P4）：构建 / 42 项单测 / 模拟器 + 真机主路径 / 驾驶员验收
- **v0.2（P5 数据补全 + P6 UI 重构）**：
  - `core`：`SiteApi`（卡片/封面兜底/徽章/多级分类/标签/作者/统计/排序/分页）、
    `CategoryTree`（二级分类树）、`ContentParser` 内链、`WpApi.comments`
  - `app`：Navigation Compose 底部导航（首页/分类/搜索/我的）、Material 3 深浅色 + 动态取色、
    新卡片、分类树、详情页（作者/统计/面包屑/标签/内链/评论）、排序 chips + 分页 + 跳页
  - 真机截图：`docs/screenshots/10-ui-home.png` ~ `14-ui-profile.png`
  - 驾驶员已确认（"ok，没问题拉"）
- **v0.3（登录滑块验证）**：
  - `core`：`SliderCaptcha`（出题/编码/坐标数学/凭据换取，按 zibll 前端算法 1:1 复刻）、
    `AuthApi.signIn(captcha=)`；单测 **76 项全绿**
  - `app`：`SliderCaptchaDialog`（Compose 拼图 + 真实拖动轨迹）、登录成功自动返回、
    「我的」页按登录态显示入口
  - 真机登录成功（截图 `15-login-success.png`、`16-profile-logged-in.png`）；
    **驾驶员已验收**：退出登录 → 重新登录成功 → 自动返回（含滑块弹窗全流程）

## 开放风险

- 登录态下 REST 的 cookie 认证（X-WP-Nonce）路径未定 → 影响点赞/评论/签到/收藏
- 详情页偶发 "timeout"（站点侧慢），已有重试；频繁出现则改分步加载
- zibll 升级会导致 golink/页面解析/滑块编码失效 → 需重新抽样

## 下一道门

**v0.3 已由驾驶员验收（2026-10-15 晚）**。进入 **P6 剩余 + 扩张清单**（驾驶员指定 2026-10-16 晚继续）：
1. 用户中心（我的页完善：账号信息、签到、收藏入口）
2. 设置页面（新页面：主题、缓存、关于等）
3. 升级版 UI（按驾驶员审美迭代）
4. 更多更详细的网站原生功能（社交写操作：点赞、评论、签到、收藏 —— 先解决 X-WP-Nonce 通道）

## 一句续接

读取总设计案、活动蓝图和本文件，从「下一道门」继续，不重开项目。
