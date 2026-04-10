# FTM Garments ERP — User Guide

## Apache OFBiz for FTM Production Workflow

**System**: Apache OFBiz (Trunk) at http://192.168.30.102:8080
**For**: FTM Staff at HQ, JJ1, JJ2, JJ3, Durban
**Updated**: 10 April 2026

-----

## Getting Started

**Login**: Open browser → `http://192.168.30.102:8080/accounting/control/main`
Username: your staff username | Password: your assigned password

**Navigation bar** (top): Party | Catalog | Facility | Order | Accounting | Manufacturing | FTM WiFi Enrollment | FTM Garments

**WiFi Access**: Enroll your device at `http://192.168.30.102:8080/ftm-wifi/control/main` (IT staff only)

-----

## FTM Workflow in OFBiz

### Step 1 — Register a Buyer (Party Manager)

When a new buyer/customer relationship begins:

1. Go to **Party** → **Create New** → **Party Group**
1. Fill in: Company Name, Tax ID, contact details
1. Add **Role**: Customer
1. Add **Contact**: email, phone, address (Shipping + Billing)

**OFBiz path**: `Party → Party Manager → Create New Party`
**Key field**: Party ID (e.g. `BUYER-PRIMARK`) — use uppercase, no spaces

-----

### Step 2 — Create a Style/Product (Catalog)

Each garment style is a **Product** in OFBiz linked to a **FtmStyleNumber**:

1. Go to **Catalog** → **Products** → **Create New Product**
1. Fill in:
- Product ID: e.g. `FTM-PANT-2026-001`
- Internal Name: e.g. `Ladies Casual Pant Navy 2026`
- Product Type: `Finished Good`
- Category: `CASUAL_PANTS` or `CASUAL_SHIRTS`
1. Go to **FTM Garments** tab → **Add Style Number**
1. Fill in:
- Style Number: buyer’s style code (e.g. `PK-26-LADY-001`)
- Buyer: `PRIMARK`
- Product Type: `PANTS`
- Season: `SS2026`
- Status: `SAMPLING`

**Note**: Style Number is FTM’s internal reference linking the buyer’s style code to our OFBiz product record.

-----

### Step 3 — Build the Bill of Materials (Catalog → BOM)

For each style, define what fabric and trims are needed per piece:

1. Go to **Catalog** → **Products** → find your garment product
1. Click **Bill of Materials** tab
1. Add components:
- **Fabric**: Product ID e.g. `FAB-COTTON-TWILL-NAVY`, Quantity: `1.8m`, Scrap: `15%`
- **Main Label**: Product ID e.g. `TRIM-LABEL-FTM`, Quantity: `1`
- **Size Label**: Product ID e.g. `TRIM-LABEL-SIZE`, Quantity: `1`
- **Button**: Product ID e.g. `TRIM-BTN-METAL-20MM`, Quantity: `3`
- **Thread**: Product ID e.g. `TRIM-THREAD-NAVY`, Quantity: `50m`
1. Association Type: **Manufactured Component**

**Critical for Ladies’ Trousers**: Add fabric with **same dye lot** notes in the BOM component description to flag color matching requirements.

-----

### Step 4 — Sales Order (Order Manager)

When buyer confirms an order:

1. Go to **Order** → **Create Order** → **Sales Order**
1. Select Customer (Party): your buyer
1. Add Order Items:
- Product: your style product
- Quantity: ordered pieces
- Delivery Date: requested ship date
1. Add Shipping Address (buyer’s warehouse)
1. **Approve** the order

**OFBiz path**: `Order → Order Manager → Sales Orders → Create New Order`
**Key**: Order ID auto-generated (e.g. `WS10001`) — use this to track through production

-----

### Step 5 — Purchase Orders (Order Manager)

After BOM is confirmed, raise purchase orders for fabric and trims:

1. Go to **Order** → **Create Order** → **Purchase Order**
1. Select Supplier (Party): fabric mill or trim supplier
1. Add items from your BOM:
- Fabric quantity = (order quantity × 1.8m) + 15% scrap
- Each trim type as separate line
1. Set delivery date (must arrive before cutting date)
1. **Approve** the purchase order

**Key**: Link purchase order to sales order via **Order Item Association** for traceability.

-----

### Step 6 — MRP / Material Requirements (Manufacturing)

OFBiz can calculate what to order based on sales orders and current stock:

1. Go to **Manufacturing** → **MRP** → **Run MRP**
1. Select Facility: `HQ` or `JJ1`/`JJ2`/`JJ3`
1. Review proposed orders — OFBiz will suggest purchase orders based on BOM and existing inventory
1. Convert to actual purchase orders if needed

**Note**: MRP works best once inventory receiving is set up properly in Step 8.

-----

### Step 7 — Production Run (Manufacturing)

When fabric and trims are received, start production:

1. Go to **Manufacturing** → **Production Runs** → **Create Production Run**
1. Select:
- Product: your garment style
- Quantity: production quantity
- Facility: `JJ1`, `JJ2`, or `JJ3`
- Start Date / End Date
1. Add **Routing Tasks**:
- Task 1: Cutting (Facility: Cutting Room)
- Task 2: Sewing Line A / B
- Task 3: Finishing / Pressing
- Task 4: QC Inspection
- Task 5: Packing
1. **Confirm** the production run

**OFBiz path**: `Manufacturing → Production Runs → Create`

-----

### Step 8 — Receive Inventory (Facility)

When fabric/trims arrive from suppliers:

1. Go to **Facility** → **Shipments** → **Find Shipments** → locate your purchase order shipment
1. Click **Receive Shipment**
1. Enter actual quantities received
1. OFBiz creates **Inventory Items** automatically

**For Fabric**: Note the **Dye Lot Number** in the lot ID field — critical for color matching in ladies’ trousers.

-----

### Step 9 — QC Inspection (Manufacturing)

During and after production:

1. Go to **Manufacturing** → **Production Runs** → find your run
1. Click on **QC Task**
1. Record:
- Pieces inspected
- Defects found (type, quantity)
- Pass/Fail status
1. If fail: create a **Rework Work Effort** for defective pieces

-----

### Step 10 — Packing and Shipment (Facility)

When production is complete:

1. Go to **Facility** → **Shipments** → **Create Outbound Shipment**
1. Link to Sales Order
1. Add: carton quantities, gross weight, shipping marks
1. Print **Packing List**
1. Update status to **Shipped**

-----

### Step 11 — Invoice and Payment (Accounting)

After shipment:

1. Go to **Accounting** → **Invoices** → **Sales Invoices** → **Create Invoice**
1. Link to Sales Order
1. OFBiz auto-populates line items from order
1. Set payment terms (e.g. 30/60/90 days)
1. Send invoice to buyer
1. When payment received: **Accounting** → **Payments** → **Receive Payment** → apply to invoice

-----

## FTM WiFi Enrollment (IT Admin Only)

**Access**: `http://192.168.30.102:8080/ftm-wifi/control/main`

|Action           |How                                                  |
|-----------------|-----------------------------------------------------|
|View all staff   |User List tab                                        |
|Add new staff    |Add User tab                                         |
|Import bulk staff|Import Excel tab (download template first)           |
|Export to CSV    |Click “Export CSV” on User List                      |
|Deactivate staff |Click “Deactivate” on User List (reversible)         |
|Delete staff     |Edit user → “Delete User permanently” (popup confirm)|
|Activate staff   |Click “Activate” on User List (for deactivated users)|

**WiFi Tiers**:

- **FTM-Staff (VLAN10)**: Management, IT — full network access
- **FTM-Staff2 (VLAN20)**: Production, HR, Finance — standard access

-----

## Common OFBiz Navigation Reference

|Task                 |Menu Path                                         |
|---------------------|--------------------------------------------------|
|Find a party         |Party → Party Manager → Find                      |
|Create product       |Catalog → Products → Create                       |
|View BOM             |Catalog → Products → [product] → Bill of Materials|
|Create sales order   |Order → Create Order → Sales Order                |
|Create PO            |Order → Create Order → Purchase Order             |
|Check inventory      |Facility → Inventory → Current Inventory          |
|Create production run|Manufacturing → Production Runs → Create          |
|View GL accounts     |Accounting → GL Accounts                          |
|Run reports          |Accounting → Reports                              |
|Admin/WebTools       |WebTools → Entity Data Maintenance (IT only)      |

-----

## Key Concepts

**Party**: Any person or organization — buyer, supplier, employee, carrier. Everything in OFBiz revolves around parties.

**Product**: A garment style. Linked to a style number, BOM, pricing, and categories.

**ProductAssoc (BOM)**: Bill of Materials — links finished garment to its components (fabric, trims). Type = `MANUF_COMPONENT`.

**WorkEffort**: Any task or production activity — cutting, sewing, QC, packing. Production Runs are groups of WorkEfforts.

**Facility**: A physical location — HQ warehouse, JJ1/JJ2/JJ3 factories, Durban office.

**OrderHeader**: A sales order or purchase order. OrderItems are the line items.

**Invoice**: A billing document linked to an order. Paid via Payment records.

-----

## Support

- **IT issues / WiFi enrollment**: Dr. Jamal Tex (IT Head), ext. IT
- **OFBiz system issues**: IT Department, `jamal.tex` on FTM-Staff WiFi
- **Official OFBiz documentation**: https://ofbiz.apache.org/developers.html
- **User Manual**: https://nightlies.apache.org/ofbiz/trunk/ofbiz/html5/user-manual.html
