# FTM IT/ERP Department Projects 2026
## Reorganized by Business Department

**AI Chief:** Dr. Chi Li-Hsing
**Core Engineers:** Kona (Nkhosikhona Dudlu), Makabongwe Mthomia, Nombulelo Simelane, Bongani Mlotshwa
**Date:** May 2026

---

## ENGINEERING TEAM

| Developer | Primary Focus |
|-----------|---------------|
| **Kona (Nkhosikhona Dudlu)** | Network, Security, HDAO, Attendance |
| **Makabongwe Mthomia** | Production, Training, Stockroom |
| **Nombulelo Simelane** | Production QC, Education, Panel Checking, IT Security |
| **Bongani Mlotshwa** | Warehouse, Purchasing, Fleet, IT Assets |

*Additional contributors: Lindokuhle Makhanya, Nothando Mdlisi, Phikule Maseko, Sibongalanhlanhla Mamlia, Thabo Dlamini, Mbongiseni Nkambule*

---

## 1. PRODUCTION DEPARTMENT

### 1A. Production Dashboard System — Operational
**Owner:** Makabongwe, Nombulelo, Phikule | **Status:** Operational
Modules live: Panel Cutting/Bundling, I.E. Tracking, Panel Feed In/Output, Mainboard Output, QC Alterations, Sewing Line Recuts, Delivery Schedule Executive Board.

### 1B. Production Dashboard — Additional Modules (Development)
**Owner:** Makabongwe, Lindokuhle | **Status:** Awaiting handover to production area
Modules: Sewing Line First Five Operations, Finishing Incoming & Pressing Board, Laser & Laundry Production.
**NB: Production Targets & Efficiency Tracking — Phase 2 (requirements analysis) still in progress.**

### 1C. Panel Checking System — Fabric QC (Phase 5 of 8)
**Owner:** Nombulelo | **Est. start:** 30 Jun 2026 | **Status:** Testing
Records and monitors fabric panels after bundling, before sewing line dispatch.

**QR Code / Vision enhancement:**
Each panel bundle receives a unique QR code at cutting. The checking station scans to log pass/fail automatically — replacing handwritten records. Future: computer vision (camera + local CV model on rpitex) can assist human inspectors in flagging visible fabric defects, reducing eye-strain errors on long shifts.

### 1D. Machine Technology Downtime Management System (Planned)
**Owner:** Phikule, Lindokuhle | **Status:** Planned
Barcode-based tracking for all production machines. Technicians scan machine QR code to log downtime events, fault codes, and repair actions. Integrates with Production Dashboard for real-time efficiency reporting.

---

## 2. WAREHOUSE & SUPPLY CHAIN DEPARTMENT

### 2A. Barcode Scanning System — Garments Warehouse (Phase 5 of 8)
**Owner:** Bongani, Thabo, Sibongalanhlanhla | **Est. start:** 1 Jun 2026 | **Status:** Field testing
Scanning for receiving, storage, and dispatch of garments inventory.

**Barcode / QR / RFID — Safe Delivery Strategy:**
- **QR Code (immediate):** Each garment carton gets a unique QR label at dispatch. Scan confirms correct SKU, quantity, and destination. Scan log = audit trail for every carton leaving the warehouse.
- **RFID passive UHF (Phase 2, planned):** Tags on cartons enable bulk scanning at dock doors without line-of-sight. Gate readers confirm every carton passing the exit point. Discrepancy between expected vs. scanned triggers instant alert.
- **Chain of custody:** Warehouse dispatch scan → vehicle loading scan → customer delivery scan. Any gap in the chain generates an automatic alert. Directly addresses product missing / stolen / lost event prevention.
- Integrates with Fleet Management (3A) to correlate vehicle ID with cargo manifest.

### 2B. Property & Warehouses Management System (Phase 4 of 8)
**Owner:** Phikule, Bongani | **Est. start:** 1 Jun 2026 | **Status:** Development
Total stock levels, raw item receiving, distribution to departments/factories, borrowed equipment management.

### 2C. Garments Stockroom Inventory Management System (Planned)
**Owner:** Makabongwe | **Status:** Planned
Tracks defective garments in Garments Stockroom. Integrates with QC data from Production Dashboard and Barcode system.

---

## 3. FLEET & LOGISTICS DEPARTMENT

### 3A. Fleet Management & Vehicle Monitoring System (Phase 2 of 8)
**Owner:** Nothando | **Est. start:** 29 Jul 2026 | **Status:** System architecture and feature definition
Manages all company vehicles across all factories: usage history, registration, trip logs, assigned drivers.

**Preventing product missing events:**
- **Trip manifest:** Each vehicle departure linked to cargo manifest from Barcode system (2A). Driver scans cargo QR at loading; system records expected contents per trip.
- **GPS tracking:** Real-time vehicle location via GPS module (SIM-based, MTN Eswatini). Alerts if vehicle deviates from planned route or makes unscheduled stops.
- **Delivery confirmation:** Customer delivery scan closes the manifest loop. Undelivered or misdelivered cartons flagged automatically.
- **Driver accountability:** Trip log with timestamps, route deviation records, and cargo integrity status. Full audit trail for any missing goods investigation.
- **HDAO integration:** Confirms driver identity via attendance system (4A) at trip start.

---

## 4. PURCHASING DEPARTMENT

### 4A. Online Purchasing Application System (Phase 4 of 8)
**Owner:** Bongani, Lindokuhle | **Est. start:** 30 Jun 2026 | **Status:** Development
Electronic purchase request and approval workflow; replacing paper-based purchase requisition forms.

**Strategic integration:**
This is the bridge to the Apache OFBiz Sales Order → BOM → Purchase Order workflow. Phase 1: standalone web app for internal requisitions. Phase 2: connects to OFBiz ftm-garments plugin for automated PO generation from BOM requirements — completing the core ERP workflow that SIMIS could not deliver.


## 5. HR / ADMIN DEPARTMENT (HDAO)

### 5A. FTM HDAO System — Attendance & Clocking (Phase 3 of 8)
**Owner:** Mbongiseni Nkambule | **Est. start:** 25 Jun 2026 | **Status:** Development
Integration with employee clocking system; mainboard clone database for attendance tracking.

### 5B. Integrated HDAO & Smart Attendance System (Planned)
**Owner:** Mbongiseni Nkambule | **Status:** Planned
Centralised platform combining internal communication, attendance clocking, and management reporting for senior staff.

### 5C. Task Management System (Planned)
**Owner:** Bongani Mlotshwa | **Status:** Planned
Centralised digital platform tracking and approving all production-related requisitions in real-time; replaces paper-based workflows.

---

## 6. EDUCATION & TRAINING DEPARTMENT

### 6A. Educational Training Room Booking System — Operational
**Owner:** Makabongwe | **Status:** Fully operational
Records all company meeting room and training room bookings.

### 6B. Video Operational Meetings & Trainings Management System (Development)
**Owner:** Phikule | **Status:** Functionality testing with Video Editing team
Records edited and raw video inventory; generates progress and monitoring reports.

### 6C. Computer Education System — Operational
**Owner:** Nombulelo | **Status:** Fully operational
Centralised course and e-training candidate records, assessments and testing data for all levels.

---

## 7. IT DEPARTMENT (INTERNAL)

### 7A. IT Inventory Management System — Operational
**Owner:** Nothando, Bongani, Sibongalanhlanhla, Thabo | **Status:** Fully operational
Tracks IT equipment/assets, computer records, hardware management, printer repairs, technical drawings.

### 7B. IT Security Policies Development & Enforcement System (Planned)
**Owner:** Nombulelo Simelane | **Status:** Planned
Enhances security for all computer systems, data, and network infrastructure.

**Links directly to existing FTM infrastructure:**
- pfSense firewall (Suricata IDS/IPS, VLAN segmentation)
- EAP-TLS WiFi certificate policy (Smallstep CA + FreeRADIUS)
- WireGuard VPN key management (tun_wg0, 10.68.7.0/24)
- ZeroTier overlay network access control
- Git security practices in OFBiz development (Dependabot, CodeQL, password rotation)

### 7C. Secure Score Reporting System (Planned)
**Owner:** Nothando | **Status:** Planned
Real-time internal IT communication platform for production score reporting.

---

## 8. ISO 9001 / QUALITY MANAGEMENT (CROSS-DEPARTMENT)

### 8A. ISO Management System — AI-Powered via llm-wiki (Priority)
**Owner:** Dr. Chi Li-Hsing | **Developers:** Kona (infrastructure), Nombulelo (document conversion)
**Status:** Planned — **recommended for immediate prioritization**

Implementation via the **llm-wiki** platform:

- **Document modernization:** All FTM ISO 9001 documents (MS Word .docx on Google Sites) → Markdown/HTML in Git repository. Version control, diff tracking, full audit history of every change.
- **pgvector RAG knowledge base:** Documents embedded using nomic-embed-text (MacStudio Ollama) and stored in ftmerp PostgreSQL pgvector extension. Semantic search: *"What is the corrective action procedure for a fabric defect?"* retrieves the exact SOP clause.
- **Local LLM assistant:** Ollama llama3.3:70b on FTM network (no cloud exposure). Staff query the knowledge base in plain language; AI answers are grounded in actual FTM ISO documents only.
- **ISO 9001 audit support:** Cross-references SOP documents against recorded practices, flags gaps, generates audit checklists for recertification preparation.
- **Role-based access:** Department-level access (HDAD, FAD, AAD, MAD, TDRD) enforced by existing pfSense/EAP-TLS/VPN identity infrastructure — same certificates that govern WiFi access govern document access.
- **OFBiz integration (Phase 10):** OFBiz can log quality events (defect records, corrective actions, supplier evaluations) directly into the ISO document system via Apache Camel — creating a closed-loop ISO 9001 quality workflow.

---

## BARCODE / QR CODE / RFID — CROSS-SYSTEM SUMMARY

| Technology | FTM Use Case | Systems | Timeline |
|------------|-------------|---------|----------|
| **QR Code** | Panel bundle ID, garment carton dispatch, machine downtime logging | Panel Checking (1C), Warehouse (2A), Machine Downtime (1D) | Immediate |
| **Barcode 1D** | Garment SKU scanning at receiving/dispatch; IT asset tagging | Warehouse (2A), IT Inventory (7A) | Operational |
| **RFID UHF passive** | Bulk dock-door scanning; gate exit confirmation; vehicle cargo verification | Warehouse (2A), Fleet (3A), Property (2B) | Phase 2 — after QR proves out |

**Safe delivery chain:**
Panel QR at cutting → Panel Checking scan → Sewing line → Finishing → Warehouse QR/RFID → Vehicle loading scan (Fleet 3A) → Customer delivery scan → Manifest closed.

Any gap = automatic alert. ISO 9001-compliant delivery record. Full chain of custody for any missing goods investigation.

---

## SYSTEM DEVELOPMENT PHASES KEY

| Phase | Description |
|-------|-------------|
| 1 | Planning |
| 2 | Requirements Analysis |
| 3 | System Design |
| 4 | Development |
| 5 | Testing (UAT, Integration, User Acceptance) |
| 6 | Deployment |
| 7 | Maintenance & Support |
| 8 | Retirement / Migration |

---

*FTM Garments Swaziland (Pty) Ltd — May 2026*

