package com.snatik.storage.core.net

/** The page a laptop browser gets: drop files, watch them upload, see what arrived. */
internal object ReceivePage {

    const val unauthorized: String = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Pairing code needed</title>
<style>
:root{--bg:#f4f6f3;--card:#fff;--ink:#1a201d;--muted:#5d6864;--line:#dce2de;--accent:#2c6e5b;--soft:#e7f0eb}
@media(prefers-color-scheme:dark){:root{--bg:#12160f;--card:#1a1f1b;--ink:#e3e8e4;--muted:#98a49e;--line:#2c3531;--accent:#6fbfa3;--soft:#1f2f29}}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased}
main{max-width:520px;margin:0 auto;padding:64px 20px}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:28px;box-shadow:0 1px 2px rgba(0,0,0,.04)}
h1{font-size:22px;font-weight:700;margin:0 0 8px;letter-spacing:-.01em}
p{color:var(--muted);margin:0}
</style></head><body><main><div class="card">
<h1>Pairing code needed</h1>
<p>Open the link shown on the phone in the Storage app — it carries the code that unlocks this page.</p>
</div></main></body></html>"""

    fun html(deviceName: String, code: String): String = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Send to ${escape(deviceName)}</title>
<style>
:root{--bg:#f4f6f3;--card:#fff;--ink:#1a201d;--muted:#5d6864;--line:#dce2de;--accent:#2c6e5b;--accent-2:#245b4b;--soft:#e7f0eb;--err:#b4552b}
@media(prefers-color-scheme:dark){:root{--bg:#12160f;--card:#1a1f1b;--ink:#e3e8e4;--muted:#98a49e;--line:#2c3531;--accent:#6fbfa3;--accent-2:#8fd3ba;--soft:#1f2f29;--err:#e0966f}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.55 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-font-smoothing:antialiased}
main{max-width:600px;margin:0 auto;padding:56px 20px 80px}
.head{margin:0 0 20px}
h1{font-size:26px;font-weight:700;letter-spacing:-.02em;margin:0 0 8px}
.sub{color:var(--muted);margin:0;font-size:15px}
.chip{background:var(--soft);color:var(--accent-2);font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.86em;padding:2px 7px;border-radius:6px;white-space:nowrap}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;box-shadow:0 1px 2px rgba(0,0,0,.04)}
#drop{display:block;width:100%;border:2px dashed var(--line);border-radius:16px;padding:40px 24px;text-align:center;cursor:pointer;transition:border-color .15s,background .15s;background:var(--card)}
#drop:hover{border-color:var(--accent)}
#drop.over{border-color:var(--accent);background:var(--soft)}
#drop input{display:none}
#drop svg{width:40px;height:40px;color:var(--accent);opacity:.9}
#drop .hint{color:var(--muted);margin:12px 0 16px;font-size:15px}
.btn{display:inline-block;background:var(--accent);color:#fff;padding:11px 22px;border-radius:999px;font-weight:600;font-size:15px;box-shadow:0 2px 0 var(--accent-2)}
#list{list-style:none;padding:0;margin:20px 0 0;display:grid;gap:10px}
.item{display:flex;gap:12px;align-items:center;padding:14px 16px}
.name{flex:1;min-width:0;font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:13.5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.right{display:flex;align-items:center;gap:10px;flex:none}
.bar{height:6px;background:var(--line);border-radius:3px;width:120px;overflow:hidden}
.bar i{display:block;height:100%;width:0;background:var(--accent);border-radius:3px;transition:width .12s}
.status{font-size:12.5px;color:var(--muted);min-width:56px;text-align:right}
.status.ok{color:var(--accent-2)}
.status.err{color:var(--err)}
@media(max-width:440px){.bar{width:80px}}
</style></head><body><main>
<div class="head">
  <h1>Send to ${escape(deviceName)}</h1>
  <p class="sub">Files land in <span class="chip">Download/Storage&nbsp;Received</span> on the phone. Pairing code <span class="chip">$code</span>.</p>
</div>
<label id="drop" class="card">
  <input type="file" multiple>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4M12 4l-4 4M12 4l4 4"/><path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/></svg>
  <div class="hint">Drop files here, or click to choose</div>
  <span class="btn">Choose files</span>
</label>
<ul id="list"></ul>
<script>
const code=new URLSearchParams(location.search).get('code')||'$code';
const drop=document.getElementById('drop'),input=drop.querySelector('input'),list=document.getElementById('list');
['dragenter','dragover'].forEach(e=>drop.addEventListener(e,ev=>{ev.preventDefault();drop.classList.add('over')}));
['dragleave','drop'].forEach(e=>drop.addEventListener(e,ev=>{ev.preventDefault();drop.classList.remove('over')}));
drop.addEventListener('drop',ev=>send(ev.dataTransfer.files));
input.addEventListener('change',()=>{send(input.files);input.value=''});
function send(files){for(const f of files)upload(f)}
function upload(file){
  const li=document.createElement('li');li.className='item card';
  li.innerHTML='<span class="name"></span><span class="right"><span class="bar"><i></i></span><span class="status">0%</span></span>';
  li.querySelector('.name').textContent=file.name;list.prepend(li);
  const bar=li.querySelector('i'),status=li.querySelector('.status');
  const xhr=new XMLHttpRequest();
  xhr.open('PUT','/api/upload/'+encodeURIComponent(file.name)+'?code='+encodeURIComponent(code));
  xhr.setRequestHeader('X-From','browser');
  xhr.upload.onprogress=e=>{if(e.lengthComputable){const p=Math.round(100*e.loaded/e.total);bar.style.width=p+'%';status.textContent=p+'%'}};
  xhr.onload=()=>{if(xhr.status<300){bar.style.width='100%';status.className='status ok';status.textContent='received'}else{status.className='status err';status.textContent='failed '+xhr.status}};
  xhr.onerror=()=>{status.className='status err';status.textContent='failed'};
  xhr.send(file);
}
</script></main></body></html>"""

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
