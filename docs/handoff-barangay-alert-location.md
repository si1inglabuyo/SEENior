# Handoff — barangay dashboard shows the home address, not the alert's real location

**Cross-lane note.** This describes a bug whose fix lives entirely in the **dashboard lane**
(`barangay-dashboard/**`, `backend/app/api/routes/barangay.py`, `backend/app/schemas/barangay.py`
— see `barangay-dashboard/CLAUDE.md` §1). It is written by the Android/backend lane so the
dashboard lane's context is complete. **No migration, no `db/models.py` change, no change to any
other backend file is needed** — the data already exists and is already correct.

---

## Symptom

When an SOS (or any) alert reaches the barangay dashboard, the "Last Known Location" panel shows
the senior's **registered home address**, not where the phone actually was when the alert fired.

## This is a display gap, not a capture bug — the precise location is already in the cloud

Verified against the live database. Two SOS tests:

| alert | `alerts.location_cluster_id` | decodes to | reality |
|---|---|---|---|
| #92 (SOS) | `wdw574372` | 14.64673, 121.07107 | the phone's real position at alert time (Quezon City) |
| registered address | — | ~14.51, 121.056 | Taguig — **~15 km away** |

The senior app captures the GPS fix, encodes it as a **precision-9 geohash (~5 m cell)**, and
uploads it — for an SOS it is even in the original `POST /alerts` body. The **family app already
reads this field** (`AlertOut.location_cluster_id` → `AlertDto.locationClusterId`) and plots a
pin. The barangay dashboard is the only surface that drops it.

## Root cause (three parts, all in the dashboard lane)

1. **`backend/app/schemas/barangay.py` — `BarangayAlertOut`** has no `location_cluster_id` field,
   so the API never sends it.
2. **`backend/app/api/routes/barangay.py` — `_alert_out()`** never populates it.
3. **`barangay-dashboard/src/components/AlertDetailsModal.jsx`** — `AlertDetailsModal` already
   passes `alert.location_cluster_id` to `<LocationPreview>`, so today it is always `undefined`.
   And `LocationPreview` itself renders a **fake SVG street grid** plus the address text; even
   given the geohash it only prints `Location cluster: <hash>` as a string — it never plots the
   real point on a map.

## Important context — the location is NOT anonymous anymore

`LocationPreview`'s current copy ("anonymous location cluster", "Live GPS isn't stored", "no
coordinates") and the comment at the top of `AlertDetailsModal.jsx` are **stale**. Root
`CLAUDE.md` §11 was deliberately reversed on 2026-08-31:

- The stored value is a **precise** precision-9 geohash (~5 m), finer than a phone's own GPS
  error. It identifies a place.
- It is held lawfully under **RA 10173 §12(c) (vital interests)** — an active emergency, shared
  only with the senior's own linked barangay — **not** through anonymisation.
- **Do not call it "anonymous", "de-identified", or "a cluster" in the UI, the paper, or the
  slides.** A panelist who checks the database will catch that. The DB column is still *named*
  `location_cluster_id` for historical reasons only.
- Older alerts (raised before 2026-08-31) may carry ~150 m precision-7 geohashes. Read the
  precision off the cell length and draw accordingly: a **pin** for a modern ~5 m cell, a
  **square/circle** for the old wide ones. Do not collapse them into one shape. (The Android
  app does exactly this in `AlertLocationMap.kt` — worth copying the logic.)

## What to change

1. **`BarangayAlertOut`** — add `location_cluster_id: str | None`.
2. **`_alert_out()`** — add `location_cluster_id=alert.location_cluster_id`.
3. **`LocationPreview` / `AlertDetailsModal.jsx`** — decode the geohash → lat/lon and show the
   real location:
   - Best: a real map with a pin (Leaflet + OSM tiles), or an OSM static-map image.
   - Minimum: an "Open in Maps" link, e.g.
     `https://www.openstreetmap.org/?mlat=<lat>&mlon=<lon>#map=18/<lat>/<lon>`
   - Pin for a cell ≲ 30 m across; box for wider (older) cells.
   - **Keep the registered address shown too**, as a secondary line — the responder still needs
     the street name to read out.
4. **Update the copy** — this is the senior's precise position at the moment the alert fired,
   captured once, shared lawfully under RA 10173 §12(c). Not anonymous, not a cluster.

## Geohash decoding

Standard geohash, base32 alphabet `0123456789bcdefghjkmnpqrstuvwxyz` (no a/i/l/o), length-agnostic
decode. Reference implementation already in the repo:
`android-app/SEENior/app/src/main/java/com/pup/seenior/location/Geohash.kt` (`decode()` returns
the cell bounds; `android-app/.../ui/family/AlertLocationMap.kt` shows pin-vs-box rendering). In
JS use the `latlon-geohash` or `ngeohash` npm package, or a ~15-line decoder.

Quick check: `wdw574372` must decode to roughly **14.6467, 121.0711**.

## Caution

Per `barangay-dashboard/CLAUDE.md` §4 the dashboard talks to the **production** backend and DB by
default. Reading alerts to test the map is fine; do not click Acknowledge/Resolve on live
incidents the other lane is testing against.
