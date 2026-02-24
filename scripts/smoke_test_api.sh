#!/usr/bin/env bash
set -euo pipefail

API_BASE="${1:-http://localhost:8080/stocks-backend/api}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE_DIR="$ROOT_DIR/public/templates"

echo "[1/6] Init schema"
curl -fsS -X POST "$API_BASE/init"
echo

echo "[2/6] Import demat_accounts"
curl -fsS -X POST -H 'Content-Type: text/plain' --data-binary @"$TEMPLATE_DIR/demat_accounts.csv" "$API_BASE/import/demat_accounts"
echo

echo "[3/6] Import trades"
curl -fsS -X POST -H 'Content-Type: text/plain' --data-binary @"$TEMPLATE_DIR/trades.csv" "$API_BASE/import/trades"
echo

echo "[4/6] Import cash_flows"
curl -fsS -X POST -H 'Content-Type: text/plain' --data-binary @"$TEMPLATE_DIR/cash_flows.csv" "$API_BASE/import/cash_flows"
echo

echo "[5/6] Import income"
curl -fsS -X POST -H 'Content-Type: text/plain' --data-binary @"$TEMPLATE_DIR/income.csv" "$API_BASE/import/income"
echo

echo "[6/6] Fetch summary"
curl -fsS "$API_BASE/summary"
echo

echo "Smoke test completed successfully against: $API_BASE"
