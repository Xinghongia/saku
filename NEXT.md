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
- 登录：`/user-sign?tab=signin` → `admin-ajax.php` `action=user_signin`；滑块实测未强制
- 本机工具链：JDK 21（`D:\Java`）、SDK（`D:\AndroidStudiozy`）、Gradle 9.3.1、代理 7897

## 最近证据

- v0.1（P1–P4）：构建 / 42 项单测 / 模拟器 + 真机主路径 / 驾驶员验收
- **v0.2（P5 数据补全 + P6 UI 重构）**：
  - `core`：`SiteApi`（卡片/封面兜底/徽章/多级分类/标签/作者/统计/排序/分页）、
    `CategoryTree`（二级分类树）、`ContentParser` 内链、`WpApi.comments`
  - 单测：**62 项全绿**（新增 SiteApi、CategoryTree、正文内链用例；样本来自 .probe 脱敏副本）
  - `app`：Navigation Compose 底部导航（首页/分类/搜索/我的）、Material 3 深浅色 + 动态取色、
    新卡片、分类树、详情页（作者/统计/面包屑/标签/内链/评论）、排序 chips + 分页 + 跳页
  - 真机截图：`docs/screenshots/10-ui-home.png` ~ `14-ui-profile.png`

## 开放风险

- 登录态下 REST 的 cookie 认证（X-WP-Nonce）路径未定 → 影响点赞/评论/签到/收藏；待驾驶员账号验证
- 登录响应 JSON 结构未经真机验证（宽松解析）
- 详情页偶发 "timeout"（站点侧慢），已有重试；频繁出现则改分步加载
- zibll 升级会导致 golink/页面解析失效 → 需重新抽样

## 下一道门

**等驾驶员确认 v0.2**（装机试用 + 反馈；特别是新 UI 观感与卡片信息是否符合预期）。
确认后进入 **P6 剩余社交功能**：点赞、发表评论、签到、收藏 —— 先解决登录态的 REST/写操作通道。

## 一句续接

读取总设计案、活动蓝图和本文件，从「下一道门」继续，不重开项目。
