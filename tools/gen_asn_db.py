#!/usr/bin/env python3
"""
Build a compact offline IP->organisation database for the Network feature.

Input:  ip2asn-combined.tsv from https://iptoasn.com/ (public domain), columns:
        range_start  range_end  AS_number  country  AS_description
Output: app/src/main/assets/ip2asn.db

Packed layout (big-endian), gzipped:
  magic  "ASN1"
  u32 nameCount ; then per name: u16 byteLen + UTF-8 bytes  (index 0 == "" unknown)
  u32 v4Count   ; then per row: u32 startIp,  u32 nameIdx    (sorted asc, contiguous)
  u32 v6Count   ; then per row: 16B startIp,  u32 nameIdx    (sorted asc, contiguous)

Ranges are contiguous in the source (gaps are filled with "Not routed" rows), so
only the range start is stored; a lookup takes the last start <= ip.
"""
import gzip, ipaddress, struct, sys, os

SRC = sys.argv[1] if len(sys.argv) > 1 else "ip2asn.tsv"
OUT = sys.argv[2] if len(sys.argv) > 2 else "../app/src/main/assets/ip2asn.db"
MAXNAME = 48

def clean(desc, asn):
    if asn == "0" or not desc or desc.strip().lower() in ("not routed", "none"):
        return ""
    d = " ".join(desc.split())            # collapse whitespace
    d = d.strip(" :-")
    return d[:MAXNAME]

names = [""]                              # index 0 = unknown
name_idx = {"": 0}
v4 = []                                   # (start_int, nameIdx)
v6 = []                                   # (start_bytes, nameIdx)

with open(SRC, "r", encoding="utf-8", errors="replace") as f:
    for line in f:
        p = line.rstrip("\n").split("\t")
        if len(p) < 5:
            continue
        start, end, asn, cc, desc = p[0], p[1], p[2], p[3], p[4]
        nm = clean(desc, asn)
        idx = name_idx.get(nm)
        if idx is None:
            idx = len(names); names.append(nm); name_idx[nm] = idx
        try:
            ip = ipaddress.ip_address(start)
        except ValueError:
            continue
        if ip.version == 4:
            v4.append((int(ip), idx))
        else:
            v6.append((ip.packed, idx))

v4.sort(key=lambda x: x[0])
v6.sort(key=lambda x: x[0])

buf = bytearray()
buf += b"ASN1"
buf += struct.pack(">I", len(names))
for nm in names:
    b = nm.encode("utf-8")[:255*255]
    buf += struct.pack(">H", len(b)); buf += b
buf += struct.pack(">I", len(v4))
for s, idx in v4:
    buf += struct.pack(">II", s, idx)
buf += struct.pack(">I", len(v6))
for s, idx in v6:
    buf += s; buf += struct.pack(">I", idx)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with gzip.open(OUT, "wb", compresslevel=9) as g:
    g.write(buf)

print(f"names={len(names)} v4={len(v4)} v6={len(v6)} raw={len(buf)/1e6:.1f}MB gz={os.path.getsize(OUT)/1e6:.1f}MB")
