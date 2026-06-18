# Project NEITH — fashion-ERP layer for FTM (in ftm-garments)

**NEITH** (Egyptian goddess of weaving & creation) is FTM's fashion-ERP domain model, built **on-prem on OFBiz** (no Microsoft/cloud), informed by evaluating K3 Fashion. It weaves MERN (enquiry) + OFBiz (system-of-record) + Lumen (BI) around one shared **style → colour → size** vocabulary, plus a **critical-path** (Time-&-Action) tracker. Full design: `~/Downloads/ftm-ofbiz-style-matrix-criticalpath-design.md`.

## Phase 1 — Style / Colour / Size variant matrix (DONE: demo data)
File: `data/NeithStyleMatrixDemoData.xml`. Uses **native OFBiz** product modelling — no new entities:
- **Style = virtual `Product`** (`STYLE_CHINO01`, `isVirtual=Y`).
- **Each Colour×Size = a variant `Product`** (`isVariant=Y`) linked by `ProductAssoc(PRODUCT_VARIANT)`.
- Axes via `ProductFeature` (types `COLOR`, `SIZE`, `FIT`): colours Navy/Black/Khaki × sizes W32/W34/W36 = **9 variant SKUs**. Selectable features on the virtual define the matrix; standard features on each variant pin its cell.
- Linked to the existing **`FtmStyleNumber`** record (`CHINO-01`, season `SS26`) — season/buyer live there.

### Load (on ofbiz-dev, from the ofbiz-framework root)
```
./gradlew "ofbiz --load-data file=plugins/ftm-garments/data/NeithStyleMatrixDemoData.xml"
```
### Verify
Catalog Manager → Products → `STYLE_CHINO01` → **Variants** tab → the 3×3 grid of SKUs. (New styles can then use Catalog Manager's *Quick Add Variants* from the same COLOR/SIZE features.)

## Next phases (per the design doc)
2. **Size curve** order entry — custom `FtmSizeCurve`/`FtmSizeCurveItem` + a service to expand a curve×qty into per-variant order lines; wire the MERN enquiry to it.
3. **Season/Collection** reporting in Lumen (season already on `FtmStyleNumber`; add `ProductCategory` collections if needed).
4. **Critical-path / Time-&-Action** on OFBiz `WorkEffort` (template → per-order milestones, dependencies, owners, RAG) — surfaced via the Ops Assistant, tied to ISO/Audex procedures.
5. BOM + per-style costing → MERN quoting.
