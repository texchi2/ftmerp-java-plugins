gh‑level overview**

The repository is a collection of **Apache OFBiz plugins** that extend an FTM‑ERP installation.  
Each plugin lives in its own top‑level folder, follows the standard OFBiz component layout, and is hot‑deployed into the OFBiz runtime (see the “Restart OFBiz” rules in CLAUDE.md).  
Key conventions enforced by OpenWolf:

* All source files are described in `.wolf/anatomy.md` (we consulted it before reading).  
* Groovy is used for all service implementations; Minilang has been removed (see the “Modern OFBiz Compatibility” note in the ftm‑garments README).  
* Entity definitions (`entitydef/*.xml`) and service definitions (`servicedef/*.xml`) are version‑controlled; any DB schema changes are applied via `./gradlew loadAll`.  
* After any edit, the appropriate entries in `.wolf/memory.md` and `.wolf/cerebrum.md` must be updated (OpenWolf’s learning loop).  

Below are the two plugins you asked about.

---

### 1️⃣ **ftm‑garments** – Garment‑manufacturing plugin

*Location*: `ftm-garments/` (see README lines 1‑40).  
*Purpose*: Provides ERP functionality specific to garment production – product catalog, BOMs for pants/shirts, manufacturing workflows, and custom services.  

**Key parts**

| Path                        | Role                                                                   |
| --------------------------- | ---------------------------------------------------------------------- |
| `build.gradle`              | Declares plugin dependencies; no Minilang.                             |
| `ofbiz-component.xml`       | Registers the component with OFBiz.                                    |
| `entitydef/entitymodel.xml` | Defines entities such as `Product`, `Bom`, `ManufacturingOrder`.       |
| `groovyScripts/`            | Groovy service implementations (e.g., `product/CreateProduct.groovy`). |
| `servicedef/services.xml`   | Maps service names to Groovy scripts (`engine="groovy"`).              |
| `webapp/ftm-garments/`      | UI screens, controllers, and JSPs for the garment UI.                  |
| `data/`                     | Seed and demo XML data for quick onboarding.                           |

**Modernization notes (from README lines 84‑99)**  
* Old Minilang services have been removed; new Groovy scripts are used.  
* Example Groovy service (`CreateProduct.groovy`) shows a typical pattern: obtain a `delegator`, construct a `GenericValue`, persist it, and return `success([...])`.  

**Typical workflow**  
1. Load the plugin (`./gradlew projects | grep ftm-garments`).  
2. Load demo data (`./gradlew "ofbiz --load-data readers=demo"`).  
3. Use OFBiz WebTools or the provided UI to manage garments, BOMs, and production orders.

---

### 2️⃣ **ftm‑wifi‑enrollment** – Wi‑Fi certificate enrollment management

*Location*: `ftm-wifi-enrollment/` (README lines 1‑190).  
*Purpose*: Manages **authorized Wi‑Fi users** in an OFBiz‑hosted component while the existing Flask MDM portal retains ownership of device records.  

**Architecture (README lines 9‑18)**  

```
OFBiz (rpitex) → ftm-wifi-enrollment component
   → ftmEnrollment delegator → ftmEnrollmentDataSource → PostgreSQL ftm_enrollment
        • authorized_users (managed here)
        • ftm_wifi_audit_log (audit table created by this component)
```

**Key parts**

| Path                                 | Role                                                                                                                             |
| ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------- |
| `ofbiz-component.xml`                | Registers the component.                                                                                                         |
| `entitydef/entitymodel_ftm_wifi.xml` | Defines `FtmAuthorizedUser` and `FtmWifiAuditLog` entities.                                                                      |
| `servicedef/services_ftm_wifi.xml`   | Six service definitions (create, update, deactivate, etc.).                                                                      |
| `groovyScripts/ftm/wifi/`            | Groovy implementations, each performing the DB work **via the `ftmEnrollment` delegator** (e.g., `CreateAuthorizedUser.groovy`). |
| `webapp/ftm-wifi/WEB-INF/`           | `controller.xml` and `web.xml` route UI requests (`/ftm-wifi/control/...`).                                                      |
| `widget/`                            | Screen and form XML definitions for the OFBiz UI (`FtmWifiScreens.xml`).                                                         |
| `data/GenerateSampleExcel.groovy`    | Helper script that produces a sample Excel file for bulk import.                                                                 |
| `README`                             | Detailed deployment checklist, datasource configuration, and troubleshooting table.                                              |

**Deployment highlights**

* A **PostgreSQL datasource** (`ftmEnrollmentDataSource`) is added to the main `entityengine.xml` (see README lines 55‑88).  
* All DB operations go through the **external delegator** `ftmEnrollment`; the default delegator is never used (security‑by‑separation).  
* The component is **hot‑deployable**: copy the directory into `/home/texchi/ofbiz/hot-deploy/` on the target server and restart OFBiz (see README lines 97‑113).  

**Security‑relevant constraints**

* Only the `authorized_users` table is mutable; other tables (`enrolled_devices`, `it_admins`) are read‑only and owned by the Flask portal.  
* Service implementations include audit logging (writes to `ftm_wifi_audit_log`).  
* Deactivation services add a reminder to revoke the user’s FreeRADIUS certificate manually – a deliberate out‑of‑band step (README line 178).  
* VLAN changes trigger a warning because they require device re‑enrollment (line 179).  

**Typical usage**

* List users: `GetAuthorizedUsers.groovy` (exposed via UI `/ftm-wifi/control/FindAuthorizedUsers`).  
* Create/update/deactivate a user: respective Groovy services, each appending an audit record.  
* Bulk import: run `ImportUsersFromExcel.groovy` (requires Apache POI JAR on the OFBiz classpath).  

---

### How the two plugins fit together

Both plugins are **self‑contained OFBiz components** that:

1. **Declare their own entities** (`entitydef/…`).  
2. **Expose services via Groovy** (`groovyScripts/…`).  
3. **Provide UI screens** (`webapp/…` and `widget/…`).  

The overall system (see `CLAUDE.md` and the OpenWolf rules) expects you to:

* Load each component with the standard OFBiz hot‑deploy process.  
* Run `./gradlew loadAll` after adding or modifying entity definitions.  
* Use the provided UI or WebTools to interact with the services.  
* Keep the OpenWolf learning files up‑to‑date (`.wolf/memory.md`, `.wolf/cerebrum.md`) after any change.

---

### Quick reference for you

* **ftm‑garments** – garment ERP core, Groovy services, no Minilang.  
* **ftm‑wifi‑enrollment** – Wi‑Fi user authorisation, external PostgreSQL delegator, audit logging, hot‑deployable.  

If you need deeper dives (e.g., exact service signatures, entity field lists, or UI screen XML), let me know which file you’d like to inspect next.
