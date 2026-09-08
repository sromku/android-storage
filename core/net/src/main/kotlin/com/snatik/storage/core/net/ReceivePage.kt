package com.snatik.storage.core.net

/** The page a laptop browser gets: drop files, watch them upload, see what arrived. */
internal object ReceivePage {

    const val unauthorized: String = """<!doctype html><html><body style="font-family:system-ui;padding:40px;color:#333">
<h2>Pairing code needed</h2><p>Open the link shown in the Storage app on the phone, it carries the code.</p></body></html>"""

    fun html(deviceName: String, code: String): String = """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Send to ${escape(deviceName)}</title>
<style>
:root{--bg:#f4f6f3;--ink:#1a201d;--muted:#5d6864;--line:#d6dcd8;--accent:#2c6e5b;--soft:#e2efe9}
@media(prefers-color-scheme:dark){:root{--bg:#131715;--ink:#e3e8e4;--muted:#98a49e;--line:#2c3531;--accent:#6fbfa3;--soft:#1f2f29}}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.5 system-ui,-apple-system,Segoe UI,sans-serif}
main{max-width:720px;margin:0 auto;padding:40px 24px}
h1{font-size:28px;margin:0 0 4px}.sub{color:var(--muted);margin:0 0 24px}
#drop{border:2px dashed var(--line);border-radius:16px;padding:48px 24px;text-align:center;transition:.15s;cursor:pointer}
#drop.over{border-color:var(--accent);background:var(--soft)}
#drop input{display:none}
.btn{display:inline-block;background:var(--accent);color:#fff;padding:10px 18px;border-radius:999px;font-weight:600;margin-top:12px}
ul{list-style:none;padding:0;margin:24px 0 0}li{display:flex;gap:12px;align-items:center;padding:10px 0;border-bottom:1px solid var(--line)}
.name{flex:1;font-family:ui-monospace,Menlo,monospace;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.bar{height:6px;background:var(--line);border-radius:3px;width:160px;overflow:hidden}.bar i{display:block;height:100%;background:var(--accent);width:0}
.ok{color:var(--accent);font-size:13px}.err{color:#b4552b;font-size:13px}
code{background:var(--soft);padding:2px 6px;border-radius:4px}
</style></head><body><main>
<h1>Send to ${escape(deviceName)}</h1>
<p class="sub">Files land in <code>Download/Storage Received</code> on the phone. Pairing code <code>$code</code>.</p>
<label id="drop"><input type="file" multiple><div>Drop files here or click to choose</div><span class="btn">Choose files</span></label>
<ul id="list"></ul>
<script>
const code=new URLSearchParams(location.search).get('code')||'$code';
const drop=document.getElementById('drop'),input=drop.querySelector('input'),list=document.getElementById('list');
['dragenter','dragover'].forEach(e=>drop.addEventListener(e,ev=>{ev.preventDefault();drop.classList.add('over')}));
['dragleave','drop'].forEach(e=>drop.addEventListener(e,ev=>{ev.preventDefault();drop.classList.remove('over')}));
drop.addEventListener('drop',ev=>send(ev.dataTransfer.files));
input.addEventListener('change',()=>send(input.files));
function send(files){for(const f of files)upload(f)}
function upload(file){
  const li=document.createElement('li');li.innerHTML='<span class="name"></span><span class="bar"><i></i></span><span class="status"></span>';
  li.querySelector('.name').textContent=file.name;list.prepend(li);
  const bar=li.querySelector('i'),status=li.querySelector('.status');
  const xhr=new XMLHttpRequest();
  xhr.open('PUT','/api/upload/'+encodeURIComponent(file.name)+'?code='+encodeURIComponent(code));
  xhr.setRequestHeader('X-From','browser');
  xhr.upload.onprogress=e=>{if(e.lengthComputable)bar.style.width=(100*e.loaded/e.total)+'%'};
  xhr.onload=()=>{if(xhr.status<300){bar.style.width='100%';status.className='ok';status.textContent='received'}else{status.className='err';status.textContent='failed '+xhr.status}};
  xhr.onerror=()=>{status.className='err';status.textContent='failed'};
  xhr.send(file);
}
</script></main></body></html>"""

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
