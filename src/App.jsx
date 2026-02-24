import { useEffect, useMemo, useState } from 'react'
import { getSummary, importCsv, initSchema } from './api'
import './styles.css'

const IMPORT_TYPES = ['demat_accounts', 'trades', 'cash_flows', 'income']

function App() {
  const [importType, setImportType] = useState('demat_accounts')
  const [status, setStatus] = useState('')
  const [summary, setSummary] = useState(null)

  const templateLinks = useMemo(
    () => [
      { label: 'Demat accounts template', file: '/templates/demat_accounts.csv' },
      { label: 'Trades template', file: '/templates/trades.csv' },
      { label: 'Cash flow template', file: '/templates/cash_flows.csv' },
      { label: 'Income template', file: '/templates/income.csv' }
    ],
    []
  )

  useEffect(() => {
    bootstrap()
  }, [])

  async function bootstrap() {
    try {
      await initSchema()
      await refreshSummary()
      setStatus('Connected to Java/Tomcat backend')
    } catch (error) {
      setStatus(`Backend connection failed: ${error.message}`)
    }
  }

  async function handleImport(event) {
    const file = event.target.files?.[0]
    if (!file) return

    try {
      const result = await importCsv(importType, file)
      setStatus(`Imported ${result.count} rows into ${result.type}`)
      await refreshSummary()
    } catch (error) {
      setStatus(`Import failed: ${error.message}`)
    }

    event.target.value = ''
  }

  async function refreshSummary() {
    const data = await getSummary()
    setSummary(data)
  }

  return (
    <div className="container">
      <h1>Stocks Portfolio Manager (React + Java/Tomcat)</h1>
      <p>Import CSV exports from Zoho Sheet and store data in your own Java server database.</p>

      <section className="card">
        <h2>Import Data</h2>
        <label>
          Dataset type
          <select value={importType} onChange={(e) => setImportType(e.target.value)}>
            {IMPORT_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </label>

        <input type="file" accept=".csv,text/csv" onChange={handleImport} />
        <div className="actions">
          <button onClick={refreshSummary}>Refresh summary</button>
          <button onClick={bootstrap}>Re-initialize backend schema</button>
        </div>
        {status && <p className="status">{status}</p>}
      </section>

      <section className="card">
        <h2>CSV Templates</h2>
        <ul>
          {templateLinks.map((item) => (
            <li key={item.file}>
              <a href={item.file} download>
                {item.label}
              </a>
            </li>
          ))}
        </ul>
      </section>

      <section className="card">
        <h2>Summary</h2>
        {!summary ? (
          <p>Loading…</p>
        ) : (
          <>
            <h3>Accounts ({summary.accounts?.length || 0})</h3>
            <ul>
              {(summary.accounts || []).map((a, idx) => (
                <li key={`${a.account_name}-${idx}`}>
                  {a.account_name} · {a.broker} · {a.account_number} · {a.owner_name} · {a.currency}
                </li>
              ))}
            </ul>

            <h3>Holdings ({summary.holdings?.length || 0})</h3>
            <ul>
              {(summary.holdings || []).map((h, idx) => (
                <li key={`${h.account_name}-${h.symbol}-${h.exchange}-${idx}`}>
                  {h.account_name} · {h.symbol} ({h.exchange}) · Qty: {Number(h.net_quantity).toFixed(4)} · Net Trade
                  Value: {Number(h.net_trade_value).toFixed(2)}
                </li>
              ))}
            </ul>

            <h3>Totals</h3>
            <ul>
              <li>Net funds added: {Number(summary.net_funds || 0).toFixed(2)}</li>
              <li>Total dividends: {Number(summary.dividends || 0).toFixed(2)}</li>
              <li>Total interest: {Number(summary.interest || 0).toFixed(2)}</li>
            </ul>
          </>
        )}
      </section>
    </div>
  )
}

export default App
