# Forkray

Forkray is an unofficial fork of [v2rayNG](https://github.com/2dust/v2rayNG),
the V2Ray client for Android with the [Xray core](https://github.com/XTLS/Xray-core).
It follows every upstream release and keeps its features and settings, but is
built so that anyone can check that the published app comes from the published
source.

It is not affiliated with v2rayNG or its author. Report problems with Forkray
here, not upstream.

[简体中文](#简体中文)

## Why a fork

v2rayNG's source is public, but its releases have not been verifiable against
it: the Xray core is downloaded as a prebuilt library, and in the past several
releases pointed at the same commit despite different changes. For that reason
F-Droid and IzzyOnDroid do not carry it. Forkray changes how the app is built
and distributed, not what it does:

- **All native code and data is built from source** on public CI, including
  the Xray core and the routing databases; none of it is downloaded prebuilt.
  Java and Kotlin libraries come from Maven repositories, as for any Android
  app.
- **Reproducible.** Each release publishes `build-manifest.txt` with the exact
  sources, toolchain and SHA-256 of every native library and APK, so a rebuild
  can be compared byte for byte ([docs/reproducible-build.md](../docs/reproducible-build.md)).
- **A released version never changes.** Once a version's APKs are published,
  the release workflow refuses to build that version again, even from a moved
  tag.
- **Freely licensed routing data.** `geoip.dat` and `geosite.dat` are built from
  plain-text sources in [forkray-geodata](https://github.com/AcideFluorhydrique/forkray-geodata):
  v2fly/domain-list-community, gfwlist, china-operator-ip, DB-IP Lite and the
  address ranges operators publish. No MaxMind data. IP Geolocation by
  [DB-IP](https://db-ip.com).
- **No in-app update check and no promotion link.** Your F-Droid client handles
  updates.
- **Its own application ID** (`io.github.acidefluorhydrique.v2rayng`), so it
  installs alongside v2rayNG instead of replacing it.

## Install

Android 7.0 or later.

**F-Droid repository** (recommended; you get updates). Add this repository in
your F-Droid client (or Droid-ify, Neo Store, ...):

```
https://acidefluorhydrique.github.io/v2rayNG/repo?fingerprint=8EC908D8D3686A29969433F0E528F78B74D5ADCC59C699B9D7569341F5215346
```

**Or download an APK** from [Releases](https://github.com/AcideFluorhydrique/v2rayNG/releases).
Pick `arm64-v8a` for almost any phone from the last several years.

## Verify

The APKs are signed with this certificate; `apksigner verify --print-certs` must
print it:

```
SHA-256: 561d282e03be3557d09073fc317fcc2f4d3231fa26da662e290222f4b34f78d9
```

To check that an APK was built from the source it claims, rebuild it as
described in [docs/reproducible-build.md](../docs/reproducible-build.md) and
compare with that release's `build-manifest.txt`.

## Using it

Forkray works like v2rayNG: see the [v2rayNG wiki](https://github.com/2dust/v2rayNG/wiki).
Configurations move between the two with *Backup & Restore*.

## Maintenance

[docs/maintenance.md](../docs/maintenance.md) describes how upstream releases
are merged and published. The upstream README is kept unchanged in
[README.md](../README.md) so that upstream changes merge cleanly.

## License

[GPL-3.0](../LICENSE), like v2rayNG. The routing data keeps the licences of its
sources, listed in the app under *About → Open-source licenses* and in
[forkray-geodata](https://github.com/AcideFluorhydrique/forkray-geodata).

---

## 简体中文

Forkray 是 [v2rayNG](https://github.com/2dust/v2rayNG) 的非官方分支，一个基于
[Xray 核心](https://github.com/XTLS/Xray-core)的 Android 代理客户端。它跟随上游的每个版本，
保留全部功能和设置；不同之处在于构建方式：任何人都可以验证发布的 app 确实来自公开的源代码。

本项目与 v2rayNG 及其作者没有关联。Forkray 的问题请在这里反馈，不要提交到上游。

### 为什么要分支

v2rayNG 的源代码是公开的，但它的发布版本无法与源代码对照验证：Xray 核心是以预编译库的形式下载的，
过去也曾有多个版本指向同一个 commit，而更新日志却列出了不同的改动。因此 F-Droid 和 IzzyOnDroid
都没有收录它。Forkray 改变的是构建和分发方式，而不是 app 的功能：

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

### 安装

需要 Android 7.0 或更高版本。

**F-Droid 仓库**（推荐，可以自动获得更新）：在 F-Droid 客户端（或 Droid-ify、Neo Store 等）中添加：

```
https://acidefluorhydrique.github.io/v2rayNG/repo?fingerprint=8EC908D8D3686A29969433F0E528F78B74D5ADCC59C699B9D7569341F5215346
```

**或者**从 [Releases](https://github.com/AcideFluorhydrique/v2rayNG/releases) 下载 APK。
近几年的手机基本都选 `arm64-v8a`。

### 验证

APK 使用以下证书签名，`apksigner verify --print-certs` 必须输出：

```
SHA-256: 561d282e03be3557d09073fc317fcc2f4d3231fa26da662e290222f4b34f78d9
```

要验证 APK 确实是从它声明的源代码构建的，请按照
[docs/reproducible-build.md](../docs/reproducible-build.md) 重新构建，并与该版本的
`build-manifest.txt` 比对。

### 使用

用法与 v2rayNG 相同，请参阅 [v2rayNG wiki](https://github.com/2dust/v2rayNG/wiki)。
两者之间可以通过“备份 & 还原”迁移配置。

### 许可证

与 v2rayNG 相同，采用 [GPL-3.0](../LICENSE)。路由数据保留其来源的许可证，
列在 app 的“关于 → 开源许可证”中，以及 [forkray-geodata](https://github.com/AcideFluorhydrique/forkray-geodata)。
