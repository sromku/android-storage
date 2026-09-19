# Photos & Media - Feature Plan

**Positioning: a power viewer + cloud browser + inspector.** Not a Google/Apple Photos
replacement. We do not chase albums, face grouping, or a full editing suite. Instead we win on the
three things those apps cannot or will not do:

1. **Open anything, at any size** - RAW, HEIC/AVIF, 10-bit HDR, PSD/TIFF, gigapixel panoramas and
   multi-GB video, without choking.
2. **Show the internals** - deep EXIF/XMP/ICC/MakerNotes, codec and color details, provenance, and
   the raw bytes underneath. The stuff every other gallery hides.
3. **Your files everywhere** - local plus Dropbox / Google Drive / OneDrive, browsed side by side
   with the same inspector, and previewed without a full download.

This complements the system gallery rather than replacing it, which is why non-technical users can
adopt it (it "just opens the file the other app couldn't") while power users get the depth.

## How features are graded

- **Reach** - how many users benefit. `H` most users · `M` many · `L` niche / power users.
- **Diff** - how far it beats Google/Apple Photos. `H` they can't/won't · `M` we do it better · `L` parity.
- **Effort** - `S` a few days · `M` 1-2 weeks · `L` 3-5 weeks · `XL` 6+ weeks.
- **Tier** - **P0** first-release core · **P1** fast follow · **P2** later · **P3** opportunistic.

Grading leans on the chosen positioning and on the stated goal of *unlocking non-technical users*, so
"opens a file nobody else can" and "one-tap privacy" score high even when niche in raw numbers.

---

## A. The power viewer

| # | Feature | Reach | Diff | Effort | Tier | Notes |
|---|---------|:---:|:---:|:---:|:---:|-------|
| 1 | **Media grid + viewer shell** - all images/video in a folder or on device; day/month/year zoom, fast scrubber, swipe, pinch-zoom | H | L | M | **P0** | The entry surface. Reuses the disk scanner; opens what the browser dispatches. |
| 2 | **HEIC/HEIF/AVIF + 10-bit HDR**, wide-gamut, color-managed rendering | H | M | M | **P0** | Many phones shoot HEIC; system viewers still stumble on AVIF/HDR. |
| 3 | **RAW decode + preview** (CR2/CR3, NEF, ARW, DNG, RAF, ORF, ProRAW) - embedded JPEG first, then demosaic | M | H | L | **P0** | Flagship "opens what others can't". Start with the embedded preview for instant display. |
| 4 | **Tiled / streaming viewer for huge files** - gigapixel images and multi-GB video via region/pyramid decode, no OOM | M | H | L | **P1** | The technical crown jewel. See notes. |
| 5 | **Pro video player** - frame-step, thumbnail scrub, HDR, loop, speed | H | M | M | **P0** | Media3/ExoPlayer already in the app. |
| 6 | **Zoom to 1:1 / loupe / live histogram** overlay | M | H | S | **P1** | Cheap once the viewer exists; very "pro". |
| 7 | **Animated formats** - GIF, APNG, animated WebP | M | L | S | **P1** | Table stakes, easy. |
| 8 | **Motion Photo / Live Photo** playback (extract embedded video) | M | M | S | **P2** | Detect the trailer/MP4 offset and play it. |
| 9 | **Panorama / 360** (equirectangular) viewer | L | M | M | **P3** | Niche but delightful. |

## B. The inspector superpower

| # | Feature | Reach | Diff | Effort | Tier | Notes |
|---|---------|:---:|:---:|:---:|:---:|-------|
| 10 | **Deep metadata panel** - EXIF, XMP, IPTC, ICC profile, camera **MakerNotes**, GPS | M | H | M | **P0** | The core differentiator - nobody shows this to a normal user. |
| 11 | **"What this photo reveals about you"** privacy report - GPS on a map, device, exact timestamps, editing software | H | H | S | **P1** | On-brand, shareable, non-technical-friendly. Reuses panel 10. |
| 12 | **Video stream inspector** - container, codecs, bitrate, GOP, resolution, HDR metadata, tracks | M | H | M | **P1** | `MediaExtractor`/`MediaFormat` + Media3. |
| 13 | **Raw bytes / thumbnails** - open any media in the hex inspector; extract embedded thumbnails/previews | L | H | S | **P1** | Already have the hex viewer; just wire media into it. |
| 14 | **Content provenance** - C2PA "Content Credentials", AI-generated markers, edit history | M | H | L | **P2** | Timely; growing across camera/AI ecosystems. |
| 15 | **Color / quality inspector** - color space, bit depth, chroma subsampling, quantization tables, estimated JPEG quality | L | H | M | **P2** | Deeply "pro"; unique. |

## C. Cloud browser (Dropbox / Drive / OneDrive)

| # | Feature | Reach | Diff | Effort | Tier | Notes |
|---|---------|:---:|:---:|:---:|:---:|-------|
| 16 | **Connect a cloud account** (OAuth) - Google Drive, Dropbox, OneDrive | H | M | L | **P1** | Headline. Start with one provider, add the rest behind one interface. |
| 17 | **Browse cloud as folders**, unified with local storage in the same browser | H | M | M | **P1** | The cloud becomes just another "volume". |
| 18 | **Stream-preview cloud media without full download** (HTTP range) + thumbnails + selective download | H | H | L | **P1** | Photos won't inspect your Drive originals; we will. Pairs with #4 (tiled over range). |
| 19 | **Same inspector on cloud files** - metadata, RAW, huge-file tiling over range requests | M | H | M | **P2** | The inspector superpower, but on cloud originals. |
| 20 | **Back up / sync a folder to a cloud target**; mark for offline | M | L | M | **P2** | Utility; parity with cloud apps. |

## D. Organize (light - stay minimal)

| # | Feature | Reach | Diff | Effort | Tier | Notes |
|---|---------|:---:|:---:|:---:|:---:|-------|
| 21 | **On-device search** - filename, folder, date, and **EXIF** (camera, lens, ISO, focal length) | H | M | M | **P1** | EXIF search is a genuine differentiator. |
| 22 | **Smart views** - by camera, by type (RAW / video / screenshots), by date, largest, geotagged | M | M | S | **P1** | Reuses disk-usage and insights grouping. |
| 23 | **Duplicate & near-duplicate finder** (perceptual hash) | H | M | M | **P1** | Extends Storage Insights to images specifically. |
| 24 | **Map view** of geotagged media + timeline | M | M | M | **P2** | Needs a map tile source. |
| 25 | **Favorites + simple tags/collections** (local, no server) | M | L | M | **P2** | Keep it lightweight; not albums-with-sync. |

## E. Share, convert & privacy

| # | Feature | Reach | Diff | Effort | Tier | Notes |
|---|---------|:---:|:---:|:---:|:---:|-------|
| 26 | **Share with EXIF/GPS strip** toggle | H | H | S | **P0** | Buried in Photos; we make it one tap. Strong hook. |
| 27 | **Convert** - HEIC->JPG, RAW->DNG/JPG, AVIF<->JPG, batch | M | M | M | **P1** | People constantly need HEIC->JPG. |
| 28 | **Batch rename / resize / strip-or-keep metadata** | M | M | M | **P2** | Power utility. |
| 29 | **Locked / private folder** (biometric) | M | L | M | **P2** | Common gallery feature; keep simple. |
| 30 | **Redact / blur** faces or regions before sharing | M | M | M | **P3** | Privacy-forward, pairs with #26. |

---

## Recommended first cut (P0) - shipped

A shippable "power viewer" that already feels magical and differentiated. All P0 items are built:

- [x] **#1 Media grid + viewer shell** - Photos tab, day-grouped adaptive grid, swipeable pager with pinch/double-tap zoom
- [x] **#2 HEIC/AVIF/HDR rendering** - via Coil + the platform image decoder
- [x] **#3 RAW preview** - embedded-JPEG-first Coil fetcher with a full-decode fallback (DNG/CR2/CR3/NEF/ARW/RW2/RAF/ORF/...)
- [x] **#5 Pro video player** - reuses the ExoPlayer PlayerView (controls, seek, shell-staging)
- [x] **#10 Deep metadata panel** - grouped File/Image/Camera/Location, humanized EXIF, GPS-to-Maps, expandable full-tag dump, video track facts
- [x] **#26 Share with EXIF/GPS strip** - one-tap "Share without metadata", verified to remove all EXIF

That set says, in one sentence, *"it opens the files your gallery can't and shows you everything about
them - then lets you share them safely."* Non-technical users get "finally, something that opens my
RAW / HEIC / huge file"; power users get the metadata depth.

**P1 progress (built):**
- [x] Organize - smart-view chips (All/Photos/Videos/RAW/Screenshots) + on-device EXIF search (#21, #22)
- [x] Pro viewer - histogram overlay, "what this reveals" privacy report, video stream inspector, animated formats (#6, #7, #11, #12)
- [x] Convert + dedupe - HEIC/RAW/AVIF -> JPG, perceptual-hash duplicate finder (#23, #27)
- [~] Cloud connect + browse + stream-preview (#16-#18) - Dropbox foundation built (PKCE OAuth, browse, stream-preview); needs a Dropbox app key in DropboxConfig.APP_KEY to connect
- [ ] Tiled huge-file viewer (#4)

## Technical notes (the hard parts)

- **RAW (#3):** every RAW holds a full-size embedded JPEG - show that instantly, decode/demosaic in
  the background for the "true" render. Candidate libs: LibRaw (NDK), or Android's `ImageDecoder`
  for DNG. Start with embedded-preview + DNG, widen coverage later.
- **Huge files (#4):** never decode whole. For images use region decoding
  (`BitmapRegionDecoder`) over a tile pyramid; for TIFF/PSD/gigapixel use tiled readers. For video,
  stream and seek (Media3) rather than load. This is what prevents the OOM crashes other galleries
  hit, and it works the same over a cloud **range request**, which is why #4 and #18 share an engine.
- **Cloud (#16-#19):** one internal `MediaSource` interface with `list / stat / rangeRead / thumbnail`
  so local, Drive, Dropbox and OneDrive all look identical to the viewer and inspector. Range reads
  are what let us preview a 2GB Drive video or tile a gigapixel Drive image without downloading it.
- **Metadata (#10-#15):** `ExifInterface` + an XMP/ICC/MakerNotes parser + `MediaExtractor` for
  video. The hex viewer and ELF-style entropy tooling already exist to fall back on.
- **Agent API:** every capability here should also be exposed as REST/MCP ops (e.g. `media_metadata`,
  `media_convert`, `cloud_list`) so an agent can drive it too - consistent with the rest of the app.

## Deliberately out of scope

To protect the positioning, we do **not** build: cloud-synced albums, face/person grouping, a
full non-destructive editing suite, social/sharing feeds, or "assistant"-style auto-creations. If a
few of those ever matter, they come much later and only if they don't dilute the viewer + inspector +
cloud story.
