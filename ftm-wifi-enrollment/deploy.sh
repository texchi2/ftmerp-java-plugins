#!/bin/bash
# deploy.sh — deploy ftm-wifi-enrollment from ltsp-rpi4b256 to rpitex
# Run from: /home/texchi/ofbiz/hot-deploy/
# Usage: bash deploy.sh

set -e

RPITEX="texchi@192.168.30.129"
REMOTE_DIR="/home/texchi/ofbiz/hot-deploy"
COMPONENT="ftm-wifi-enrollment"

echo "=== FTM WiFi Enrollment Deployment ==="
echo "Source:      $(pwd)/${COMPONENT}"
echo "Destination: ${RPITEX}:${REMOTE_DIR}/${COMPONENT}"
echo ""

# Step 1 — sync component files
echo "[1/4] Syncing component files..."
rsync -avz --delete \
    "${COMPONENT}/" \
    "${RPITEX}:${REMOTE_DIR}/${COMPONENT}/"

echo ""
echo "[2/4] Verifying entityengine.xml patch on rpitex..."
ssh ${RPITEX} "grep -c 'ftmEnrollmentDataSource' /home/texchi/ofbiz/framework/entity/config/entityengine.xml || echo 'PATCH NOT APPLIED'"

echo ""
echo "[3/4] Verifying PostgreSQL JDBC driver..."
ssh ${RPITEX} "ls /home/texchi/ofbiz/framework/entity/lib/jdbc/postgresql-*.jar 2>/dev/null || echo 'DRIVER MISSING'"

echo ""
echo "[4/4] Deployment complete."
echo ""
echo "Next steps on rpitex (192.168.30.129):"
echo "  1. Apply entityengine_patch.xml to entityengine.xml if not done"
echo "  2. Set ENROLLADMIN_PASS in entityengine.xml"
echo "  3. Run: cd /home/texchi/ofbiz && ./gradlew ofbiz &"
echo "  4. Access: https://rpitex:443/ftm-wifi/control/FindAuthorizedUsers"
echo ""
echo "Audit table SQL (run on 192.168.30.3 if not done):"
echo "  psql -h 192.168.30.3 -U enrolladmin -d ftm_enrollment -f"
echo "  /home/texchi/ofbiz/hot-deploy/ftm-wifi-enrollment/data/create_audit_table.sql"
