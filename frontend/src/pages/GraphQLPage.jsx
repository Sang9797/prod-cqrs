import React, { useState } from 'react';
import { gqlRequest } from '../api/graphqlClient';
import JsonViewer from '../components/JsonViewer';

const PRESETS = {
  inventoryReport: {
    query: `query {
  inventoryReport(minStock: 0, page: 0, pageSize: 20) {
    sku
    productName
    warehouseName
    region
    quantityAvailable
    quantityReserved
    quantityFree
  }
}`,
    variables: '{}',
  },
  productStock: {
    query: `query ProductStock($productId: ID!) {
  productStock(productId: $productId) {
    warehouseName
    region
    quantityAvailable
    quantityReserved
    quantityFree
  }
}`,
    variables: '{\n  "productId": "PROD-001"\n}',
  },
  lowStock: {
    query: `query {
  lowStock(threshold: 10, limit: 20) {
    productName
    warehouseName
    quantityAvailable
    quantityReserved
  }
}`,
    variables: '{}',
  },
  reserveInventory: {
    query: `mutation Reserve($input: ReserveInput!) {
  reserveInventory(input: $input)
}`,
    variables: '{\n  "input": {\n    "productId": "PROD-001",\n    "warehouseId": "WH-001",\n    "quantity": 5,\n    "orderId": "ORDER-001"\n  }\n}',
  },
  releaseInventory: {
    query: `mutation Release($input: ReleaseInput!) {
  releaseInventory(input: $input)
}`,
    variables: '{\n  "input": {\n    "productId": "PROD-001",\n    "warehouseId": "WH-001",\n    "quantity": 5,\n    "orderId": "ORDER-001"\n  }\n}',
  },
  adjustInventory: {
    query: `mutation Adjust($input: AdjustInput!) {
  adjustInventory(input: $input)
}`,
    variables: '{\n  "input": {\n    "productId": "PROD-001",\n    "warehouseId": "WH-001",\n    "delta": 10,\n    "reason": "Restock"\n  }\n}',
  },
};

export default function GraphQLPage() {
  const [query, setQuery] = useState(PRESETS.inventoryReport.query);
  const [variables, setVariables] = useState(PRESETS.inventoryReport.variables);
  const [result, setResult] = useState(null);
  const [status, setStatus] = useState(null);
  const [timeTaken, setTimeTaken] = useState(null);
  const [loading, setLoading] = useState(false);

  function applyPreset(name) {
    setQuery(PRESETS[name].query);
    setVariables(PRESETS[name].variables);
  }

  async function runQuery() {
    let vars = {};
    try {
      vars = variables.trim() ? JSON.parse(variables) : {};
    } catch {
      setResult({ error: 'Invalid JSON in variables' });
      return;
    }
    setLoading(true);
    const start = Date.now();
    try {
      const res = await gqlRequest(query, vars);
      setStatus(res.status);
      setTimeTaken(Date.now() - start);
      setResult(res.data);
    } catch (err) {
      setStatus(err.response?.status || 0);
      setTimeTaken(Date.now() - start);
      setResult(err.response?.data || { error: err.message });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-white mb-6">GraphQL Playground</h1>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="space-y-4">
          <div className="card">
            <div className="flex flex-wrap gap-2 mb-3">
              {Object.keys(PRESETS).map((name) => (
                <button key={name} onClick={() => applyPreset(name)} className="btn-secondary text-xs px-2 py-1">
                  {name}
                </button>
              ))}
            </div>
            <div>
              <label className="text-xs text-slate-400 block mb-1">Query</label>
              <textarea
                className="input w-full font-mono text-xs"
                rows={14}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                spellCheck={false}
              />
            </div>
            <div className="mt-3">
              <label className="text-xs text-slate-400 block mb-1">Variables (JSON)</label>
              <textarea
                className="input w-full font-mono text-xs"
                rows={5}
                value={variables}
                onChange={(e) => setVariables(e.target.value)}
                spellCheck={false}
              />
            </div>
            <button onClick={runQuery} disabled={loading} className="btn-primary mt-3 w-full">
              {loading ? 'Running…' : 'Run Query'}
            </button>
          </div>
        </div>

        <div className="card space-y-3">
          {status != null && (
            <div className="flex gap-4 text-sm">
              <span className={`font-medium ${status >= 200 && status < 300 ? 'text-green-400' : 'text-red-400'}`}>
                HTTP {status}
              </span>
              {timeTaken != null && <span className="text-slate-400">{timeTaken}ms</span>}
            </div>
          )}
          <div className="overflow-auto max-h-[70vh]">
            {result ? <JsonViewer data={result} /> : (
              <p className="text-slate-500 text-sm">Run a query to see results here.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
