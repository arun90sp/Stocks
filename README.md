# Stocks Portfolio Manager (React + Java/Tomcat)

This project stores your portfolio in your **own server-side database** using Java on Tomcat.

It supports:
- Multiple demat accounts
- Buy/sell transactions
- Fund add/withdraw statements
- Dividend and interest credits
- CSV import from Zoho Sheet

## Architecture
- Frontend: React + Vite
- Backend: Java Servlet (Tomcat WAR)
- Database: H2 file database (server-side persistent)

---

## 1) Prerequisites (your machine)

Install these first:
- **JDK 17+**
- **Maven 3.9+**
- **Tomcat 10.1+** (Jakarta Servlet 6 compatible)
- **Node.js 18+** and npm
- **curl** (for API testing)

Quick check:

```bash
java -version
mvn -version
node -v
npm -v
curl --version
```

---

## 2) Build backend WAR

```bash
cd server
mvn clean package
```

Expected artifact:
- `server/target/stocks-backend.war`

---

## 3) Deploy to Tomcat

Copy WAR into Tomcat `webapps/` and start Tomcat.

### Linux/macOS example

```bash
cp server/target/stocks-backend.war "$CATALINA_HOME/webapps/"
"$CATALINA_HOME/bin/startup.sh"
```

### Windows example (PowerShell)

```powershell
Copy-Item .\server\target\stocks-backend.war "$env:CATALINA_HOME\webapps\"
& "$env:CATALINA_HOME\bin\startup.bat"
```

After deploy, backend base URL should be:
- `http://localhost:8080/stocks-backend/api`

---

## 4) Test backend APIs first (recommended)

### Option A: one-command smoke test

From repo root:

```bash
./scripts/smoke_test_api.sh
```

Custom API URL:

```bash
./scripts/smoke_test_api.sh http://localhost:8080/stocks-backend/api
```

### Option B: manual curl checks

```bash
curl -X POST http://localhost:8080/stocks-backend/api/init
curl -X POST -H "Content-Type: text/plain" --data-binary @public/templates/demat_accounts.csv http://localhost:8080/stocks-backend/api/import/demat_accounts
curl -X POST -H "Content-Type: text/plain" --data-binary @public/templates/trades.csv http://localhost:8080/stocks-backend/api/import/trades
curl -X POST -H "Content-Type: text/plain" --data-binary @public/templates/cash_flows.csv http://localhost:8080/stocks-backend/api/import/cash_flows
curl -X POST -H "Content-Type: text/plain" --data-binary @public/templates/income.csv http://localhost:8080/stocks-backend/api/import/income
curl http://localhost:8080/stocks-backend/api/summary
```

---

## 5) Run frontend

From project root:

```bash
npm install
npm run dev
```

By default frontend calls:
- `http://localhost:8080/stocks-backend/api`

If backend is on another host/port, create `.env` in project root:

```bash
VITE_API_BASE=http://your-host:8080/stocks-backend/api
```

Open frontend URL shown by Vite (typically `http://localhost:5173`).

---

## 6) End-to-end test flow

1. Start Tomcat and verify `/api/init` returns JSON.
2. Start React frontend (`npm run dev`).
3. In UI, choose dataset type and upload each CSV from Zoho export.
4. Verify Summary section updates:
   - Accounts list
   - Holdings
   - Totals (net funds/dividends/interest)

---

## API Endpoints
- `POST /api/init` — initialize schema
- `POST /api/import/{type}` — import CSV text (`demat_accounts`, `trades`, `cash_flows`, `income`)
- `GET /api/summary` — portfolio summary

---

## CSV templates
- `public/templates/demat_accounts.csv`
- `public/templates/trades.csv`
- `public/templates/cash_flows.csv`
- `public/templates/income.csv`

---

## Common troubleshooting

- **Tomcat 404 for `/stocks-backend`**: WAR not deployed correctly; check `webapps/` and Tomcat logs.
- **CORS error in browser**: ensure backend is running and reachable at `VITE_API_BASE`.
- **Date parse errors**: CSV dates must be `YYYY-MM-DD`.
- **Import failures**: verify header names match template exactly.
