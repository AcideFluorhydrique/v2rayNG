# Forkray

[English](/.github/README.md) | [中文](/.github/README.zh.md)

Forkray 是 [v2rayNG](https://github.com/2dust/v2rayNG) 的非官方分支，一个基于
[Xray 核心](https://github.com/XTLS/Xray-core)的 Android 代理客户端。它跟随上游的每个版本，
保留全部功能和设置；不同之处在于构建方式：任何人都可以验证发布的 app 确实来自公开的源代码。

本项目与 v2rayNG 及其作者没有关联。Forkray 的问题请在这里反馈，不要提交到上游。

## 为什么要分支

v2rayNG 的源代码是公开的，但它的发布版本无法与源代码对照验证：Xray 核心是以预编译库的形式下载的，
过去也曾有多个版本指向同一个 commit，而更新日志却列出了不同的改动。因此 F-Droid 和 IzzyOnDroid
都[没有收录它](https://gitlab.com/fdroid/rfp/-/work_items/1168#note_939164767)。Forkray 改变的是构建和分发方式，而不是 app 的功能：

- **原生代码和数据全部从源代码构建**：在公开的 CI 上完成，包括 Xray 核心和路由数据库，不下载任何预编译版本。
  Java 和 Kotlin 库与其他 Android app 一样，来自 Maven 仓库。
- **可复现**：每个版本都附带 `build-manifest.txt`，记录确切的源代码版本、工具链，以及每个原生库和
  APK 的 SHA-256，可以逐字节比对（[docs/reproducible-build.md](../docs/reproducible-build.md)）。
- **已发布的版本永不改变**：一个版本的 APK 发布后，发布流程会拒绝再次构建该版本，即使 tag 被移动。
- **自由授权的路由数据**：`geoip.dat` 和 `geosite.dat` 由
  [forkray-geodata](https://github.com/AcideFluorhydrique/forkray-geodata) 中的纯文本数据生成，
  来源为 v2fly/domain-list-community、gfwlist、china-operator-ip、DB-IP Lite 以及各运营商公布的
  IP 段，不使用 MaxMind 数据。IP 地理位置数据由 [DB-IP](https://db-ip.com) 提供。
- **没有应用内更新检查，也没有推广链接**：更新由 F-Droid 客户端负责。
- **独立的应用 ID**（`io.github.acidefluorhydrique.v2rayng`）：可以与 v2rayNG 同时安装，互不覆盖。

## 安装

需要 Android 7.0 或更高版本。以下所有来源提供的都是同一个 APK，使用[验证](#验证)一节中的证书签名，
可以随时在它们之间切换，无需重新安装。

<p>
  <!-- 等官方 F-Droid 仓库发布了用「验证」一节中的证书签名的 Forkray 版本后再启用：
  <a href="https://f-droid.org/packages/io.github.acidefluorhydrique.v2rayng/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="70" />
  </a>
  -->
  <a href="https://github.com/AcideFluorhydrique/v2rayNG/releases">
    <img src="https://raw.githubusercontent.com/mmlado/GapSign/main/assets/badges/badge_github.png" alt="Get it on GitHub" height="70" />
  </a>
  <a href="https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/AcideFluorhydrique/v2rayNG">
    <img src="https://raw.githubusercontent.com/mmlado/GapSign/main/assets/badges/badge_obtainium.png" alt="Get it on Obtainium" height="70" />
  </a>
</p>

**Forkray 的 F-Droid 仓库**（推荐：F-Droid 客户端会自动更新，每个版本一发布就能获取）。
在 [F-Droid 客户端](https://cloudflare.f-droid.org/zh_Hans/packages/org.fdroid.fdroid/)（或 Droid-ify、Neo Store 等）中添加：

```
https://acidefluorhydrique.github.io/v2rayNG/repo?fingerprint=8EC908D8D3686A29969433F0E528F78B74D5ADCC59C699B9D7569341F5215346
```

**或者**从 [Releases](https://github.com/AcideFluorhydrique/v2rayNG/releases/) 下载 APK，
手动下载或通过 [Obtainium](https://wiki.obtainium.imranr.dev/zh/#installation) 都可以。近几年的手机基本都选 `arm64-v8a`。

## 验证

APK 使用以下证书签名，`apksigner verify --print-certs` 必须输出：

```
SHA-256: 561d282e03be3557d09073fc317fcc2f4d3231fa26da662e290222f4b34f78d9
```

要验证 APK 确实是从它声明的源代码构建的，请按照
[docs/reproducible-build.md](../docs/reproducible-build.md) 重新构建，并与该版本的
`build-manifest.txt` 比对。

## 使用

用法与 v2rayNG 相同，请参阅 [v2rayNG wiki](https://github.com/2dust/v2rayNG/wiki)。
两者之间可以通过“备份 & 还原”迁移配置。

## 维护

[docs/maintenance.md](../docs/maintenance.md) 说明了如何合并和发布上游版本。上游的 README 原样保留在
[README.md](../README.md) 中，以便上游的改动能够顺利合并。

## 许可证

与 v2rayNG 相同，采用 [GPL-3.0](../LICENSE)。路由数据保留其来源的许可证，
列在 app 的“关于 → 开源许可证”中，以及 [forkray-geodata](https://github.com/AcideFluorhydrique/forkray-geodata)。
