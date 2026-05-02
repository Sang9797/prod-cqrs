import React, { useState, useEffect } from 'react';
import axios from 'axios';
import JsonViewer from '../components/JsonViewer';

const CQRS_METRICS = [
  'orders_placed_total',
  'orders_confirmed_total',
  'orders_cancelled_total',
  'inventory_reservations_total',
  'inventory_releases_total',
  'inventory_adjustments_total',
];

function extractValue(metricData) {
  if (!metricData || !metricData.measurements) return '—';
  const m = metricData.measurements.find((m) => m.statistic === 'COUNT' || m.statistic === 'VALUE');
  return m ? m.value : metricData.measurements[0]?.value ?? '—';
}

export default function Metrics() {
  const [metricNames, setMetricNames] = useState([]);
  const [loadingNames, setLoadingNames] = useState(false);
  const [selected, setSelected] = useState(null);
  const [selectedData, setSelectedData] = useState(null);
  const [cqrsData, setCqrsData] = useState({});

  useEffect(() => {
    loadNames();
    loadCqrsMetrics();
  }, []);

  async function loadNames() {
    setLoadingNames(true);
    try {
      const res = await axios.get('/actuator/metrics');
      setMetricNames(res.data.names || []);
    } catch {
      setMetricNames([]);
    } finally {
      setLoadingNames(false);
    }
  }

  async function loadCqrsMetrics() {
    const results = {};
    await Promise.all(
      CQRS_METRICS.map(async (name) => {
        try {
          const res = await axios.get(`/actuator/metrics/${name}`);
          results[name] = res.data;
        } catch {
          results[name] = null;
        }
      })
    );
    setCqrsData(results);
  }

  async function selectMetric(name) {
    setSelected(name);
    setSelectedData(null);
    try {
      const res = await axios.get(`/actuator/metrics/${name}`);
      setSelectedData(res.data);
    } catch (err) {
      setSelectedData({ error: err.response?.data || err.message });
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Metrics</h1>
      <p className="text-xs text-slate-500">
        Actuator is on port 9090. The Vite proxy forwards <code className="bg-slate-800 px-1 rounded">/actuator</code> → <code className="bg-slate-800 px-1 rounded">http://localhost:9090</code>.
      </p>

      <div>
        <h2 className="font-semibold text-white mb-3">CQRS Business Metrics</h2>
        <button onClick={loadCqrsMetrics} className="btn-secondary text-xs mb-3">Refresh</button>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {CQRS_METRICS.map((name) => (
            <div key={name} className="card text-center">
              <p className="text-xs text-slate-400 mb-1 break-all">{name}</p>
              <p className="text-2xl font-bold text-indigo-400">
                {cqrsData[name] ? extractValue(cqrsData[name]) : '—'}
              </p>
            </div>
          ))}
        </div>
      </div>

      <div>
        <h2 className="font-semibold text-white mb-3">All Metrics</h2>
        {loadingNames && <p className="text-slate-400 text-sm">Loading…</p>}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-1 card overflow-y-auto max-h-96">
            {metricNames.length === 0 && !loadingNames && (
              <p className="text-slate-500 text-xs">No metrics available. Is actuator running on port 9090?</p>
            )}
            <ul className="space-y-0.5">
              {metricNames.map((name) => (
                <li key={name}>
                  <button
                    onClick={() => selectMetric(name)}
                    className={`w-full text-left text-xs px-2 py-1.5 rounded transition-colors ${
                      selected === name ? 'bg-indigo-600 text-white' : 'text-slate-300 hover:bg-slate-700'
                    }`}
                  >
                    {name}
                  </button>
                </li>
              ))}
            </ul>
          </div>
          <div className="lg:col-span-2 card overflow-auto max-h-96">
            {selectedData ? (
              <JsonViewer data={selectedData} />
            ) : (
              <p className="text-slate-500 text-sm">Select a metric to view details.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
