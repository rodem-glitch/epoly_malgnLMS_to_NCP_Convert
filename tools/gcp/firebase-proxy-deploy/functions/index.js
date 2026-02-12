const { onRequest } = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const http = require("http");
const https = require("https");
const { URL } = require("url");

const TARGET = "http://34.64.207.10";
const targetUrl = new URL(TARGET);
const upstreamClient = targetUrl.protocol === "https:" ? https : http;

function parseCookieHeader(cookieHeader) {
  const result = {};
  if (!cookieHeader || typeof cookieHeader !== "string") return result;

  cookieHeader.split(";").forEach((segment) => {
    const part = segment.trim();
    if (!part) return;
    const eq = part.indexOf("=");
    if (eq <= 0) return;
    const name = part.substring(0, eq).trim();
    const value = part.substring(eq + 1);
    if (!name) return;
    result[name] = value;
  });

  return result;
}

function toBase64Url(text) {
  return Buffer.from(text, "utf8")
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function fromBase64Url(text) {
  const normalized = text.replace(/-/g, "+").replace(/_/g, "/");
  const padding = normalized.length % 4;
  const base64 = padding === 0 ? normalized : normalized + "=".repeat(4 - padding);
  return Buffer.from(base64, "base64").toString("utf8");
}

function decodeSessionBundle(rawValue) {
  if (!rawValue) return {};
  try {
    const decoded = fromBase64Url(rawValue);
    const parsed = JSON.parse(decoded);
    if (!parsed || typeof parsed !== "object") return {};
    return parsed;
  } catch (err) {
    logger.error("session_bundle_decode_error", { message: err?.message });
    return {};
  }
}

function encodeSessionBundle(bundle) {
  const keys = Object.keys(bundle || {});
  if (keys.length === 0) return "";
  return toBase64Url(JSON.stringify(bundle));
}

function shouldBridgeCookie(cookieName) {
  if (!cookieName) return false;
  return cookieName === "JSESSIONID" || cookieName.startsWith("MLMS");
}

function parseSetCookieHeader(rawSetCookie) {
  if (!rawSetCookie || typeof rawSetCookie !== "string") return null;

  const firstSemicolon = rawSetCookie.indexOf(";");
  const pair = firstSemicolon > -1 ? rawSetCookie.substring(0, firstSemicolon) : rawSetCookie;
  const eq = pair.indexOf("=");
  if (eq <= 0) return null;

  const name = pair.substring(0, eq).trim();
  const value = pair.substring(eq + 1);
  if (!name) return null;

  const lower = rawSetCookie.toLowerCase();
  const removed = lower.includes("max-age=0") || lower.includes("expires=thu, 01 jan 1970");
  return { name, value, removed };
}

function toCookieHeader(cookies) {
  const keys = Object.keys(cookies || {});
  if (keys.length === 0) return "";
  return keys.map((name) => `${name}=${cookies[name]}`).join("; ");
}

function buildUpstreamHeaders(req, incomingHost) {
  const headers = { ...req.headers };
  delete headers["content-length"];
  delete headers["host"];

  // 왜: Firebase Hosting -> Functions 프록시에서는 일반 쿠키가 누락될 수 있어
  // __session 번들에 담긴 세션 쿠키를 복원해 레거시 로그인(MLMS/JSESSIONID)을 유지합니다.
  const incomingCookieMap = parseCookieHeader(req.headers.cookie || "");
  const sessionBundle = decodeSessionBundle(incomingCookieMap.__session || "");
  delete incomingCookieMap.__session;

  const restoredCookieMap = { ...incomingCookieMap };
  Object.keys(sessionBundle).forEach((cookieName) => {
    restoredCookieMap[cookieName] = sessionBundle[cookieName];
  });

  const mergedCookie = toCookieHeader(restoredCookieMap);
  if (mergedCookie) headers.cookie = mergedCookie;
  else delete headers.cookie;

  headers.host = targetUrl.host;
  headers["x-forwarded-host"] = incomingHost;
  headers["x-forwarded-proto"] = "https";
  return { headers, sessionBundle };
}

function buildSessionSetCookie(bundle) {
  const encoded = encodeSessionBundle(bundle);
  if (!encoded) return "__session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax";
  return `__session=${encoded}; Path=/; Secure; HttpOnly; SameSite=Lax`;
}

exports.vmproxy = onRequest(
  {
    region: "asia-northeast3",
    timeoutSeconds: 540,
    memory: "512MiB",
  },
  async (req, res) => {
    try {
      const upstreamPath = req.originalUrl || req.url || "/";
      const method = (req.method || "GET").toUpperCase();
      const incomingHost = req.headers["x-forwarded-host"] || req.headers.host || "epoly-kopo.web.app";
      const { headers, sessionBundle } = buildUpstreamHeaders(req, incomingHost);

      await new Promise((resolve, reject) => {
        const upstreamReq = upstreamClient.request(
          {
            protocol: targetUrl.protocol,
            hostname: targetUrl.hostname,
            port: targetUrl.port || (targetUrl.protocol === "https:" ? 443 : 80),
            method,
            path: upstreamPath,
            headers,
          },
          (upstreamRes) => {
            const nextBundle = { ...sessionBundle };
            const upstreamSetCookie = upstreamRes.headers["set-cookie"];
            const setCookieList = Array.isArray(upstreamSetCookie)
              ? upstreamSetCookie
              : upstreamSetCookie
                ? [upstreamSetCookie]
                : [];

            setCookieList.forEach((rawSetCookie) => {
              const parsed = parseSetCookieHeader(rawSetCookie);
              if (!parsed || !shouldBridgeCookie(parsed.name)) return;
              if (parsed.removed || !parsed.value) delete nextBundle[parsed.name];
              else nextBundle[parsed.name] = parsed.value;
            });

            Object.entries(upstreamRes.headers).forEach(([key, value]) => {
              const lower = key.toLowerCase();
              if (lower === "set-cookie" || lower === "transfer-encoding" || lower === "content-length") return;
              if (value === undefined) return;

              if (lower === "content-type" && typeof value === "string") {
                // 왜: 레거시 정적 HTML이 US-ASCII로 내려오면 브라우저 탭 제목 한글이 깨질 수 있어
                // web.app 경유 응답에서는 UTF-8을 명시해 인코딩 깨짐을 방지합니다.
                if (value.toLowerCase().startsWith("text/html")) {
                  const rewrittenType = value.replace(/charset=us-ascii/i, "charset=utf-8");
                  if (!/charset=/i.test(rewrittenType)) {
                    res.setHeader("content-type", `${rewrittenType}; charset=utf-8`);
                  } else {
                    res.setHeader("content-type", rewrittenType);
                  }
                  return;
                }
              }

              if (lower === "location" && typeof value === "string") {
                const rewritten = value.replace(/^https?:\/\/34\.64\.207\.10(?::\d+)?/i, `https://${incomingHost}`);
                res.setHeader("location", rewritten);
                return;
              }

              res.setHeader(key, value);
            });

            const responseCookies = [...setCookieList, buildSessionSetCookie(nextBundle)];
            res.setHeader("set-cookie", responseCookies);
            res.status(upstreamRes.statusCode || 502);

            upstreamRes.on("error", reject);
            upstreamRes.pipe(res);
            upstreamRes.on("end", resolve);
          },
        );

        upstreamReq.setTimeout(120000, () => {
          upstreamReq.destroy(new Error("upstream_timeout"));
        });
        upstreamReq.on("error", reject);

        const hasBody = method !== "GET" && method !== "HEAD";
        if (hasBody && req.rawBody && req.rawBody.length > 0) {
          upstreamReq.write(req.rawBody);
          upstreamReq.end();
          return;
        }
        if (hasBody) {
          req.pipe(upstreamReq);
          return;
        }
        upstreamReq.end();
      });
    } catch (err) {
      logger.error("proxy_error", {
        method: req?.method,
        url: req?.originalUrl || req?.url,
        message: err?.message,
      });
      res.status(502).send("Proxy upstream error");
    }
  }
);
