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
- **v0.4（UI 改版）**：
  - `ui/theme/PresetTheme.kt`：五套自研主题（樱花粉/海洋蓝/春绿/秋橙/极简，全量 token 无灰底）；
    `Theme.kt` 重写：深浅三态 + 动态取色（默认开，对齐 rikkahub）+ 纯黑模式
  - `SettingsStore.kt` + `SettingsScreen.kt`：外观（主题/动态取色/纯黑色卡网格）、
    浏览（默认排序）、数据（清缓存）、关于（版本/开源/分享/免责）
  - `SakuTopAppBar`（透明融合顶栏）/ `SortMenuButton`（排序下拉，删横向 chips）/
    `SettingsGroup`（分组圆角卡片 + 彩色圆底图标）；`ShortNavigationBar` 底栏
  - insets 双层叠加修复（顶部空白大根因）；卡片底改 surfaceContainerLow
  - 截图归档 `docs/screenshots/17~24`；material3 1.4.0 的 MaterialExpressiveTheme
    仍 internal 暂不可用（已记录）

## 开放风险

- v0.4 待驾驶员真机走查（重点：整体观感、主题切换、设置页、排序下拉）
- 登录态下 REST 的 cookie 认证（X-WP-Nonce）路径未定 → 影响点赞/评论/签到/收藏
- 详情页偶发 "timeout"（站点侧慢），已有重试；频繁出现则改分步加载
- zibll 升级会导致 golink/页面解析/滑块编码失效 → 需重新抽样

## 下一道门

**驾驶员真机走查 v0.4 UI**（主题五套、深浅切换、设置页、排序下拉、顶部留白）。
通过后进入 **P6 剩余社交功能**：点赞、发表评论、签到、收藏 —— 先解决登录 cookie
在 REST/写操作通道的验证（X-WP-Nonce）；用户中心（我的页完善）与更多原生功能同批推进。

## 一句续接

读取总设计案、活动蓝图和本文件，从「下一道门」继续，不重开项目。
