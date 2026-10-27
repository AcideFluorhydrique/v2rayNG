#!/usr/bin/env python3
"""Replace fdroidserver's generated repo/index.html with this repository's own page.

The default page names only the main address. This one also lists the mirrors,
which matter most to the people who cannot reach the main one, and what the
repository currently offers. Everything on it is read from the signed index
`fdroid update` has just written (index-v2.json), so it cannot drift from what
is published; only the signing key's fingerprint, which the index does not
carry, is taken from the page fdroidserver generated.

The same file is published to every mirror, so it uses relative links for the
files and loads nothing from other sites.

Every text is written in English and in Simplified Chinese. English shows by
default; a button switches to Chinese and the choice is remembered in the
browser. Without JavaScript the page stays in English.

Usage: make-landing-page.py <repo directory>   (after `fdroid update`)
"""

import datetime
import html
import json
import os
import re
import sys
from urllib.parse import urlsplit

ABI_ORDER = ['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86']
ABI_NOTE = {
    'arm64-v8a': ('64-bit ARM: nearly every phone', '64 位 ARM：几乎所有手机'),
    'armeabi-v7a': ('32-bit ARM: older phones', '32 位 ARM：较旧的手机'),
    'x86_64': ('64-bit x86: emulators, some tablets', '64 位 x86：模拟器、部分平板'),
    'x86': ('32-bit x86', '32 位 x86'),
}


def host_label(url):
    """A short name for where an address is served from."""
    parts = urlsplit(url)
    host = parts.netloc.lower()
    if host.endswith('github.io'):
        return 'GitHub Pages'
    if host.endswith('codeberg.page'):
        return 'Codeberg Pages'
    if host == 'ray.arielx.is-a.dev':
        return 'Codeberg Pages, own domain'
    if host.endswith('gitlab.io'):
        return 'GitLab Pages'
    if host == 'gitlab.com' and '/-/raw/' in parts.path:
        return 'GitLab (raw files)'
    if host.endswith('itcouldbewor.se'):
        return '0xacab Pages'
    if host == '0xacab.org' and '/-/raw/' in parts.path:
        return '0xacab (raw files)'
    if host == 'raw.githubusercontent.com':
        return 'GitHub (raw files)'
    return host


def t(en, zh):
    """Inline text in both languages; CSS shows the selected one."""
    return f'<span class="l-en">{en}</span><span class="l-zh" lang="zh-CN">{zh}</span>'


def para(en, zh):
    """A paragraph in both languages."""
    return f'<p class="l-en">{en}</p>\n<p class="l-zh" lang="zh-CN">{zh}</p>'


def text(value):
    """The en-US text of a localized index field."""
    if isinstance(value, dict):
        return value.get('en-US') or next(iter(value.values()), '')
    return value or ''


def spaced(fingerprint):
    return ' '.join(fingerprint[i:i + 2] for i in range(0, len(fingerprint), 2))


def size(n):
    return f'{n / 1024 / 1024:.1f} MB'


def render(index, fingerprint):
    e = html.escape
    repo = index['repo']
    name = text(repo['name'])
    mirrors = repo.get('mirrors') or [{'url': repo['address'], 'isPrimary': True}]
    primary = next((m['url'] for m in mirrors if m.get('isPrimary')), repo['address'])
    others = [m['url'] for m in mirrors if m['url'] != primary]
    updated = datetime.datetime.fromtimestamp(repo['timestamp'] / 1000, datetime.timezone.utc)

    def add_link(url):
        return f'https://fdroid.link/#{url}?fingerprint={fingerprint}'

    def address(url, label):
        return (
            '<li>'
            f'<span class="where">{label}</span>'
            f'<code>{e(url)}</code>'
            f'<a class="add" href="{e(add_link(url))}">{t('Add to F-Droid', '添加到 F-Droid')}</a>'
            '</li>'
        )

    package_sections = []
    signers = set()
    for package_id, package in sorted(index['packages'].items()):
        meta = package['metadata']
        by_version = {}
        for version in package['versions'].values():
            manifest = version['manifest']
            by_version.setdefault((manifest['versionName'], version['added']), []).append(version)
            signers.update(manifest.get('signer', {}).get('sha256', []))
        # Newest first; versions of one release are added within the same run.
        releases = {}
        for (version_name, added), versions in by_version.items():
            releases.setdefault(version_name, []).extend(versions)
        ordered = sorted(
            releases.items(),
            key=lambda item: max(v['manifest']['versionCode'] for v in item[1]),
            reverse=True,
        )
        rows = []
        for version_name, versions in ordered:
            def abi_rank(version):
                abi = (version['manifest'].get('nativecode') or ['?'])[0]
                return ABI_ORDER.index(abi) if abi in ABI_ORDER else len(ABI_ORDER)

            for i, version in enumerate(sorted(versions, key=abi_rank)):
                manifest = version['manifest']
                abi = ', '.join(manifest.get('nativecode') or ['all'])
                note_en, note_zh = ABI_NOTE.get(abi, ('', ''))
                file = version['file']
                rows.append(
                    '<tr>'
                    + (f'<th rowspan="{len(versions)}" scope="rowgroup">{e(version_name)}</th>' if i == 0 else '')
                    + f'<td><a href="{e(file["name"].lstrip("/"))}">{e(abi)}</a>'
                    f'<span class="note">{t(e(note_en), e(note_zh))}</span></td>'
                    f'<td class="num">{size(file["size"])}</td>'
                    f'<td><code class="hash">{e(file["sha256"])}</code></td>'
                    '</tr>'
                )
        links = []
        if meta.get('sourceCode'):
            links.append(f'<a href="{e(meta["sourceCode"])}">{t('Source code', '源代码')}</a>')
        if meta.get('issueTracker'):
            links.append(f'<a href="{e(meta["issueTracker"])}">{t('Report a problem', '反馈问题')}</a>')
        package_sections.append(
            f'<h3>{e(text(meta.get("name")) or package_id)} <span class="id">{e(package_id)}</span></h3>'
            f'<p>{e(text(meta.get("summary")))}</p>'
            '<div class="scroll"><table>'
            f'<thead><tr><th>{t('Version', '版本')}</th><th>{t('Build', '架构')}</th>'
            f'<th>{t('Size', '大小')}</th><th>SHA-256</th></tr></thead>'
            f'<tbody>{"".join(rows)}</tbody></table></div>'
            + (f'<p class="links">{" · ".join(links)}</p>' if links else '')
        )

    mirror_block = ''
    if others:
        mirror_block = (
            f'<h2>{t('Mirrors', '镜像')}</h2>'
            + para(
                'The same repository on other hosts, for when the main address is slow or blocked. '
                'Add any one that your device can reach. A mirror needs no extra trust: your F-Droid client '
                'accepts an index only if it is signed with the key below, and once it has the repository it '
                'learns of every address here and switches between them by itself.',
                '同一个仓库在其他站点上的副本，供主地址缓慢或无法访问时使用。添加任意一个你的设备能连上的'
                '地址即可。镜像不需要额外信任：F-Droid 客户端只接受用下方密钥签名的索引；添加仓库之后，客户端会'
                '自动得知这里列出的全部地址，并在它们之间自行切换。',
            )
            + f'<ul class="addresses">{"".join(address(u, e(host_label(u))) for u in others)}</ul>'
        )

    signer_block = ''
    if signers:
        signer_block = (
            f'<h2>{t('APK signing certificate', 'APK 签名证书')}</h2>'
            + para(
                'Every APK here is signed with this certificate (SHA-256); '
                '<code>apksigner verify --print-certs</code> prints it.',
                '这里的每个 APK 都使用此证书签名（SHA-256），'
                '可用 <code>apksigner verify --print-certs</code> 核对。',
            )
            + ''.join(f'<pre>{e(s)}</pre>' for s in sorted(signers))
        )

    title_en = f'{name} F-Droid repository'
    title_zh = f'{name} 的 F-Droid 仓库'
    stamp = updated.strftime('%Y-%m-%d %H:%M')
    main_label = f"{e(host_label(primary))} · {t('main address', '主地址')}"

    return f'''<!DOCTYPE html>
<!-- Generated by fdroid-repo/make-landing-page.py from the signed index; do not edit. -->
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="index,nofollow">
<title>{e(title_en)}</title>
<link rel="icon" type="image/png" href="icons/icon.png">
<style>
:root {{ color-scheme: light dark; --fg: #1b1f24; --muted: #5b6570; --bg: #ffffff; --panel: #f3f5f7; --line: #d5dae0; --link: #0b5fbf; }}
@media (prefers-color-scheme: dark) {{ :root {{ --fg: #e6e9ed; --muted: #9aa5b1; --bg: #14171a; --panel: #1e2327; --line: #343b43; --link: #7db8ff; }} }}
* {{ box-sizing: border-box; }}
body {{ margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans", "Noto Sans CJK SC", "PingFang SC", "Microsoft YaHei", sans-serif; }}
main {{ max-width: 52rem; margin: 0 auto; padding: 1.5rem 1rem 3rem; }}
header {{ display: flex; gap: 1rem; align-items: center; }}
header img {{ width: 56px; height: 56px; border-radius: 12px; }}
h1 {{ font-size: 1.5rem; line-height: 1.3; margin: 0; }}
h2 {{ font-size: 1.15rem; margin: 2.2rem 0 .6rem; padding-top: 1rem; border-top: 1px solid var(--line); }}
h3 {{ font-size: 1rem; margin: 1.2rem 0 .2rem; }}
p {{ margin: .5rem 0; overflow-wrap: anywhere; }}
html:not(.zh) .l-zh, html.zh .l-en {{ display: none !important; }}
.lang {{ margin-left: auto; align-self: flex-start; flex-shrink: 0; white-space: nowrap; font: inherit; font-size: .875rem; color: var(--fg); background: var(--panel); border: 1px solid var(--line); border-radius: 999px; padding: .3rem .9rem; cursor: pointer; }}
.lang:hover {{ border-color: var(--link); }}
.lang:focus-visible {{ outline: 2px solid var(--link); outline-offset: 2px; }}
a {{ color: var(--link); }}
code, pre {{ font-family: ui-monospace, "SFMono-Regular", Menlo, Consolas, "Liberation Mono", monospace; font-size: .875rem; }}
pre {{ background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: .7rem .8rem; margin: .5rem 0; white-space: pre-wrap; word-break: break-all; }}
.addresses {{ list-style: none; padding: 0; margin: .6rem 0; display: grid; gap: .6rem; }}
.addresses li {{ background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: .7rem .8rem; display: grid; gap: .25rem; }}
.addresses code {{ word-break: break-all; font-weight: 600; }}
.where, .note, .id {{ color: var(--muted); font-size: .8125rem; font-weight: 400; }}
.note {{ display: block; }}
.add {{ font-size: .875rem; }}
.primary {{ display: grid; grid-template-columns: 1fr auto; gap: 1rem; align-items: center; }}
.primary img {{ width: 112px; height: 112px; background: #fff; padding: 4px; border-radius: 6px; image-rendering: pixelated; }}
@media (max-width: 30rem) {{ .primary {{ grid-template-columns: 1fr; }} }}
.scroll {{ overflow-x: auto; }}
table {{ border-collapse: collapse; width: 100%; min-width: 40rem; font-size: .875rem; }}
th, td {{ text-align: left; vertical-align: top; padding: .45rem .6rem; border-bottom: 1px solid var(--line); }}
thead th {{ color: var(--muted); font-weight: 600; white-space: nowrap; }}
.num {{ white-space: nowrap; font-variant-numeric: tabular-nums; }}
.hash {{ font-size: .75rem; word-break: break-all; }}
.links {{ font-size: .875rem; }}
footer {{ margin-top: 2.5rem; color: var(--muted); font-size: .8125rem; }}
</style>
</head>
<body>
<main>
<header>
<img src="icons/icon.png" alt="">
<h1>{t(e(title_en), e(title_zh))}</h1>
<button class="lang" id="lang" type="button" hidden>中文</button>
</header>

{para(e(text(repo.get('description'))), f'{e(name)} 是 v2rayNG 的分支，全部在 GitHub Actions 上从源代码构建。每个版本都附带构建清单，列出确切的源代码、工具链和校验和，可以重新构建并逐字节比对。')}

<h2>{t('Add the repository', '添加仓库')}</h2>
{para(f'In your F-Droid client (F-Droid, Droid-ify, Neo Store…), add this address, or scan the QR code. Then install and update {e(name)} from there.', f'在 F-Droid 客户端（F-Droid、Droid-ify、Neo Store 等）中添加下面的地址，或扫描二维码，之后即可在客户端里安装和更新 {e(name)}。')}
<div class="primary">
<ul class="addresses">{address(primary, main_label)}</ul>
<a href="index.png"><img src="index.png" alt="QR code of the main address" data-alt-zh="主地址的二维码"></a>
</div>
{mirror_block}
<h2>{t('Repository signing key', '仓库签名密钥')}</h2>
{para("The SHA-256 fingerprint of the key that signs this repository's index, on every address above. The links above already carry it; compare it by hand if you type an address in yourself.", '为本仓库索引签名的密钥的 SHA-256 指纹，以上所有地址均相同。上面的链接已包含该指纹；如果手动输入地址，请自行核对。')}
<pre>{e(spaced(fingerprint))}</pre>

<h2>{t('Available now', '当前提供')}</h2>
{para('Your F-Droid client picks the right build by itself. The files are also here to download directly from this address.', 'F-Droid 客户端会自动选择合适的架构。也可以直接从当前地址下载以下文件。')}
{''.join(package_sections)}
{signer_block}
<footer>
<p>{t(f'Index of {stamp} UTC', f'索引生成于 {stamp} UTC')}</p>
</footer>
</main>
<script>
// Language switch: English by default, the choice is kept in this browser.
(function () {{
  var root = document.documentElement, button = document.getElementById('lang');
  var qr = document.querySelector('img[data-alt-zh]'), qrEn = qr && qr.alt;
  function show(zh) {{
    root.classList.toggle('zh', zh);
    root.lang = zh ? 'zh-CN' : 'en';
    document.title = zh ? {json.dumps(title_zh)} : {json.dumps(title_en)};
    button.textContent = zh ? 'English' : '中文';
    button.setAttribute('aria-label', zh ? 'Switch to English' : '切换到中文');
    if (qr) qr.alt = zh ? qr.getAttribute('data-alt-zh') : qrEn;
    try {{ localStorage.setItem('lang', zh ? 'zh' : 'en'); }} catch (err) {{}}
  }}
  var saved = null;
  try {{ saved = localStorage.getItem('lang'); }} catch (err) {{}}
  show(saved === 'zh');
  button.hidden = false;
  button.addEventListener('click', function () {{ show(!root.classList.contains('zh')); }});
}})();
</script>
</body>
</html>
'''


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    repo_dir = sys.argv[1]
    page = os.path.join(repo_dir, 'index.html')
    with open(os.path.join(repo_dir, 'index-v2.json'), encoding='utf-8') as fp:
        index = json.load(fp)
    with open(page, encoding='utf-8') as fp:
        generated = fp.read()
    found = set(re.findall(r'fingerprint=([0-9A-Fa-f]{64})', generated))
    if len(found) != 1:
        sys.exit(f'{page}: expected one signing key fingerprint in the page fdroid update wrote, found {len(found)}')
    with open(page, 'w', encoding='utf-8', newline='\n') as fp:
        fp.write(render(index, found.pop().upper()))


if __name__ == '__main__':
    main()
