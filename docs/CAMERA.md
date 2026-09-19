# Camera sync (Sony) - exploration

Goal: pull photos off a Sony camera (you have an A1 II / ILCE-1M2) into Storage Studio,
matching what Sony's **Creators' App** does but with a nicer, more stable UI - and feeding straight
into our gallery, full-res RAW viewer, inspector, dedupe, and convert.

## How Sony wireless connectivity works

Sony bodies expose a few transports; the app-facing ones are:

- **PTP/IP** (Picture Transfer Protocol over IP). The camera runs a PTP/IP **server on TCP 15740**
  once wireless transfer is enabled, discoverable over the LAN via **SSDP** (UDP multicast
  `239.255.255.250:1900`). This is the standard "browse the card and pull images" path Creators'
  App and third-party tools (Alpha Linker, sony-pm-alt + libgphoto2) use. Sony layers a proprietary
  extension on ISO-PTP; pairing needs a 16-byte **PTP-GUID** the camera has to recognize.
- **Built-in FTP/FTPS client** (present on the pro bodies incl. A1 II). The *camera* pushes images
  to an FTP server you point it at - IP, port, folder, credentials. Rock-solid, standard, and it
  keeps going in the background.
- **USB PTP/MTP** (wired). Android USB-host + MTP; fastest for bulk, but tethered.
- **Bluetooth LE** for pairing, wake, remote shutter, and location tagging (not bulk transfer).

## Options, ranked for "great UI + stable"

| Approach | Stability | UX | Effort | Notes |
|---|---|---|---|---|
| **A. FTP push (camera -> phone)** | Very high | One-time camera FTP setup, then automatic | **M** | App runs an embedded FTP/FTPS server; camera transfers selected/all shots to it. Arguably steadier than Creators' App. |
| **B. PTP/IP pull (phone -> camera)** | Medium | Seamless "connect + browse the card" | **H** | SSDP discover + PTP/IP session on 15740 + GUID pairing. Most like Creators' App; parts are reverse-engineered and newer bodies may differ. |
| **C. USB MTP (wired)** | High | Plug in, import | **M** | Android MTP host; great for bulk offload, but a cable. |
| **D. BLE pairing/wake** | n/a | Adjunct to A/B | **M** | Auto-wake + geotag; not a transfer path by itself. |

## Recommended path

Start with **A (FTP receive)** - it is the fastest route to something you can actually test with
your A1 II, and it is the *most stable* option (a plain standard protocol, no reverse-engineering,
survives backgrounding). Then add **B (PTP/IP browse)** for the seamless "no camera config, just
browse the card" experience, reusing our tiled full-res viewer and inspector. **C (USB)** is a
strong fast-bulk follow-up.

## Phased plan

- **Phase 1 - FTP receive (testable now).** Embedded FTP/FTPS server in the app + a "Camera" screen
  that shows the listen address (phone IP:port), a live list of incoming files, and drops them into
  a `Camera Sync` album that flows into the gallery/RAW viewer. You set the A1 II's FTP transfer
  target to the phone (over the phone's hotspot or a shared Wi-Fi). Handles JPEG/HEIF/RAW.
- **Phase 2 - PTP/IP browse + pull.** SSDP discovery, PTP/IP client (Kotlin, or port libgphoto2 via
  the NDK - C++ is in-scope for this repo), GUID pairing, enumerate the card, thumbnail grid,
  selective/bulk download with resume. "Connect to camera" with no camera-side config.
- **Phase 3 - USB MTP import.** Android USB-host MTP for wired bulk offload.
- **Phase 4 - BLE** auto-wake + geotag, and remote shutter if wanted.

## Android implementation notes

- FTP server: a small embedded FTPS server (e.g. Apache FTPServer / a compact Kotlin impl), bound to
  the Wi-Fi interface; write straight into MediaStore (`Pictures/Storage Studio/Camera`).
- Networking needs a foreground service for reliability during transfer; Wi-Fi lock to keep the
  radio up; the app already holds INTERNET.
- PTP/IP: implement the init/handshake packets + object-info/object-data over a TCP socket, or bind
  libgphoto2 through JNI. GUID pairing is the fiddly bit; a first pass can require a one-time USB
  pairing step (sony-guid-setter approach) or PlayMemories-style GUID.
- Everything lands in MediaStore so the existing gallery, RAW full-res viewer, deep inspector,
  duplicate finder, and convert-to-JPG all work on camera imports for free.

## What needs you

- The A1 II's FTP transfer settings (Phase 1) - I will show the exact server address to enter.
- Testing on the real camera at each phase; the emulator/host can't stand in for the camera.

## References

- [sony-pm-alt - PlayMemories-free PTP/IP transfer](https://github.com/falk0069/sony-pm-alt)
- [Sony ISO-PTP remote-control extension (press)](https://www.sony.eu/presscentre/sony-enables-remote-control-of-a-wide-camera-range-through-iso-ptp-protocol-through-proprietary-extension)
- [Sony Camera Remote API wrapper](https://github.com/kota65535/sony_camera_remote_api)
