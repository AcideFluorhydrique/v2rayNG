# Forkray

[English](/.github/README.md) | [中文](/.github/README.zh.md)

Forkray is an unofficial fork of [v2rayNG](https://github.com/2dust/v2rayNG),
the V2Ray client for Android with the [Xray core](https://github.com/XTLS/Xray-core).
It follows every upstream release and keeps its features and settings, but is
built so that anyone can check that the published app comes from the published
source.

It is not affiliated with v2rayNG or its author. Report problems with Forkray
here, not upstream.

## Why a fork

v2rayNG's source is public, but its releases have not been verifiable against
it: the Xray core is downloaded as a prebuilt library, and in the past several
releases pointed at the same commit despite different changes. For that reason
F-Droid and IzzyOnDroid [do not carry it](https://gitlab.com/fdroid/rfp/-/work_items/1168#note_939164767). Forkray changes how the app is built
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

Android 7.0 or later. Every source below ships the same APK, signed with the
certificate under [Verify](#verify), so you can switch between them without
reinstalling.

<p>
  <!-- Add once the official F-Droid repository has published a Forkray release
       signed with the certificate under Verify:
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

**Forkray's F-Droid repository** (recommended; your F-Droid client keeps the
app updated, and each release appears there as soon as it is published). Add it
in your F-Droid client (or Droid-ify, Neo Store, ...):

```
https://acidefluorhydrique.github.io/v2rayNG/repo?fingerprint=8EC908D8D3686A29969433F0E528F78B74D5ADCC59C699B9D7569341F5215346
```

**Or download an APK** from [Releases](https://github.com/AcideFluorhydrique/v2rayNG/releases),
by hand or through Obtainium. Pick `arm64-v8a` for almost any phone from the
last several years.

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
