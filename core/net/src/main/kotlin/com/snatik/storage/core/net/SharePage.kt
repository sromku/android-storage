package com.snatik.storage.core.net

import java.io.File

/** The page any browser (iPhone, Mac, ...) gets when the phone shares photos out: a gallery to view and download. */
internal object SharePage {

    const val gone: String = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Share ended</title>
<style>
:root{--bg:#f4f6f3;--card:#fff;--ink:#1a201d;--muted:#5d6864;--line:#dce2de}
@media(prefers-color-scheme:dark){:root{--bg:#12160f;--card:#1a1f1b;--ink:#e3e8e4;--muted:#98a49e;--line:#2c3531}}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:520px;margin:0 auto;padding:64px 20px}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:28px}
h1{font-size:22px;margin:0 0 8px}p{color:var(--muted);margin:0}
</style></head><body><main><div class="card">
<h1>This share has ended</h1><p>Ask for a fresh link from the Storage app on the phone.</p>
</div></main></body></html>"""

    fun html(deviceName: String, token: String, files: List<File>): String {
        val totalBytes = files.sumOf { runCatching { it.length() }.getOrDefault(0L) }
        val cells = files.mapIndexed { i, f ->
            """<figure class="cell">
  <a class="thumb" href="/s/$token/view/$i" target="_blank"><img loading="lazy" src="/s/$token/view/$i" alt="${escape(f.name)}"></a>
  <figcaption><span class="name">${escape(f.name)}</span><a class="dl" href="/s/$token/get/$i" download>Save</a></figcaption>
</figure>"""
        }.joinToString("\n")
        return """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${files.size} from ${escape(deviceName)}</title>
<style>
:root{--bg:#f4f6f3;--card:#fff;--ink:#1a201d;--muted:#5d6864;--line:#dce2de;--accent:#2c6e5b;--accent-2:#245b4b;--soft:#e7f0eb}
@media(prefers-color-scheme:dark){:root{--bg:#12160f;--card:#1a1f1b;--ink:#e3e8e4;--muted:#98a49e;--line:#2c3531;--accent:#6fbfa3;--accent-2:#8fd3ba;--soft:#1f2f29}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased}
main{max-width:860px;margin:0 auto;padding:40px 16px 72px}
.head{display:flex;align-items:center;gap:14px;flex-wrap:wrap;margin:0 0 20px}
h1{font-size:22px;font-weight:700;letter-spacing:-.01em;margin:0}
.sub{color:var(--muted);font-size:14px;margin:2px 0 0}
.all{margin-left:auto;background:var(--accent);color:#fff;padding:11px 20px;border-radius:999px;font-weight:600;font-size:15px;text-decoration:none;box-shadow:0 2px 0 var(--accent-2)}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:12px}
.cell{margin:0;background:var(--card);border:1px solid var(--line);border-radius:14px;overflow:hidden}
.thumb{display:block;aspect-ratio:1;background:var(--soft)}
.thumb img{width:100%;height:100%;object-fit:cover;display:block}
figcaption{display:flex;align-items:center;gap:8px;padding:9px 11px}
.name{flex:1;min-width:0;font-size:12.5px;color:var(--muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.dl{flex:none;color:var(--accent-2);font-weight:600;font-size:13px;text-decoration:none}
.tip{color:var(--muted);font-size:13px;margin:22px 2px 0;text-align:center}
</style></head><body><main>
<div class="head">
  <div>
    <h1>${files.size} photo${if (files.size == 1) "" else "s"}</h1>
    <p class="sub">from ${escape(deviceName)} &middot; ${human(totalBytes)}</p>
  </div>
  ${if (files.size > 1) """<a class="all" href="/s/$token/all.zip">Download all</a>""" else ""}
</div>
<div class="grid">
$cells
</div>
<p class="tip">Tap a photo to open it, or Save to download. On iPhone, saved photos appear in Files.</p>
</main></body></html>"""
    }

    private fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB")
        var v = bytes / 1024.0
        var u = 0
        while (v >= 1024 && u < units.lastIndex) { v /= 1024; u++ }
        return "%.1f %s".format(v, units[u])
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
