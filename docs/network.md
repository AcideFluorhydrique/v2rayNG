# What Forkray connects to, and how

This lists every connection the app makes on its own behalf, when, and whether it
goes through your proxy. Traffic from other apps through the VPN is not covered:
that is the proxy doing its job.

## The one rule that matters

The app's own traffic never enters its VPN tunnel: `CoreVpnService` excludes the
app's own package, or the tunnel would loop back into itself. So a request from
the app goes through the proxy only if it is sent to the core's local HTTP proxy
on `127.0.0.1` explicitly. Anything else leaves the device directly, from your
real IP address.

Upstream v2rayNG falls back to such a direct connection without telling you
whenever a request through the proxy fails, including scheduled background
subscription updates while you are disconnected. Forkray does not
([`FetchRoutePolicy`](../V2rayNG/app/src/main/java/com/v2ray/ang/handler/FetchRoutePolicy.kt),
enabled by the F-Droid flavor's `STRICT_PROXY_FETCH`):

| Situation | Forkray | Upstream v2rayNG |
| --- | --- | --- |
| You start it, proxy running | Through the proxy only. If that fails, you are asked whether to retry directly. | Through the proxy, then directly without asking |
| You start it, proxy not running | Directly, and a message says so | Directly, without saying so |
| Scheduled in the background, proxy not running | Skipped until next time | Directly, without saying so |

"Proxy running" means something accepts connections on the local proxy port;
the app checks that just before each request.

## Connections

| What | Destination | When | Route |
| --- | --- | --- | --- |
| Subscription update | your subscription URLs | when you update, and on the schedule you set per subscription | the rule above |
| Geo files download | the URLs in *Asset files* (by default GitHub releases) | when you tap download | the rule above |
| Per-app proxy list | `raw.githubusercontent.com` (2dust/androidpackagenamelist) | when you choose automatic app selection | the rule above; without it the bundled list is used |
| IP address check | `api.ip.sb`, or the URL you set | after a connection test | through the proxy only, never directly |
| Connection tests | your proxy servers, and `www.gstatic.com/generate_204` through them | when you test | directly to your servers (that is what is measured) |
| Server address lookup | your system DNS, for your servers' domain names | when connecting, unless *Outbound domain resolution* is set to *Do not resolve* (the default resolves) | directly |
| Certificate fingerprint | the server being edited | when you fetch it in the server editor | directly |
| Browser Dialer | your proxy servers | only with the Browser Dialer transport | directly (it is the transport) |
| WebDAV backup | the server you configure | when you back up or restore | directly, to the server you chose |
| Links in *About* and elsewhere | GitHub, Telegram, the wiki | only when you tap them, in your browser | your browser's route |

The app does not check for updates (your F-Droid client does), has no
analytics, and has no promotion link. The licences page is read from inside the
app.
