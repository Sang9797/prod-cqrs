import React, { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';

const TEST_OPTIONS = [
  { value: 'load', label: 'Load Test', file: 'k6/load-test.js' },
  { value: 'stress', label: 'Stress Test', file: 'k6/stress-test.js' },
  { value: 'inventory', label: 'Inventory Load Test', file: 'k6/inventory-load-test.js' },
];

export default function LoadTest() {
  const [config, setConfig] = useState({
    baseUrl: 'http://localhost:8080',
    username: 'admin',
    password: 'admin123',
    testType: 'load',
    maxVus: 50,
    duration: '5m',
  });
  const [status, setStatus] = useState('idle');
  const [output, setOutput] = useState([]);
  const [exitCode, setExitCode] = useState(null);
  const [polling, setPolling] = useState(false);
  const logRef = useRef(null);
  const pollRef = useRef(null);

  function set(field, value) {
    setConfig((p) => ({ ...p, [field]: value }));
  }

  const selectedTest = TEST_OPTIONS.find((t) => t.value === config.testType);

  const generatedCmd = `K6_USERNAME=${config.username} K6_PASSWORD=${config.password} BASE_URL=${config.baseUrl} \\
  k6 run --vus ${config.maxVus} --duration ${config.duration} ${selectedTest?.file}`;

  useEffect(() => {
    if (logRef.current) {
      logRef.current.scrollTop = logRef.current.scrollHeight;
    }
  }, [output]);

  function startPolling() {
    if (pollRef.current) return;
    setPolling(true);
    pollRef.current = setInterval(async () => {
      try {
        const res = await axios.get('http://localhost:3001/api/k6/status');
        const data = res.data;
        setOutput(data.output || []);
        setStatus(data.status);
        if (data.status === 'complete' || data.status === 'error') {
          setExitCode(data.exitCode);
          stopPolling();
        }
      } catch {
        stopPolling();
        setStatus('error');
      }
    }, 2000);
  }

  function stopPolling() {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
    setPolling(false);
  }

  async function runTest() {
    setOutput([]);
    setExitCode(null);
    setStatus('running');
    try {
      await axios.post('http://localhost:3001/api/k6/run', {
        testType: config.testType,
        baseUrl: config.baseUrl,
        username: config.username,
        password: config.password,
      });
      startPolling();
    } catch (err) {
      setStatus('error');
      setOutput([err.message || 'Failed to start test. Is k6-server running?']);
    }
  }

  async function stopTest() {
    stopPolling();
    try {
      await axios.post('http://localhost:3001/api/k6/stop');
    } catch {}
    setStatus('idle');
  }

  const statusColor = {
    idle: 'text-slate-400',
    running: 'text-yellow-400',
    complete: 'text-green-400',
    error: 'text-red-400',
  }[status] || 'text-slate-400';

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Load Test</h1>

      <div className="card space-y-4">
        <h2 className="font-semibold text-white">Test Configuration</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          <div>
            <label className="text-xs text-slate-400 block mb-1">Base URL</label>
            <input className="input w-full" value={config.baseUrl} onChange={(e) => set('baseUrl', e.target.value)} />
          </div>
          <div>
            <label className="text-xs text-slate-400 block mb-1">Username</label>
            <input className="input w-full" value={config.username} onChange={(e) => set('username', e.target.value)} />
          </div>
          <div>
            <label className="text-xs text-slate-400 block mb-1">Password</label>
            <input type="password" className="input w-full" value={config.password} onChange={(e) => set('password', e.target.value)} />
          </div>
          <div>
            <label className="text-xs text-slate-400 block mb-1">Test Type</label>
            <select className="input w-full" value={config.testType} onChange={(e) => set('testType', e.target.value)}>
              {TEST_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
            </select>
          </div>
          <div>
            <label className="text-xs text-slate-400 block mb-1">Max VUs ({config.maxVus})</label>
            <input type="range" min="10" max="500" value={config.maxVus} onChange={(e) => set('maxVus', Number(e.target.value))} className="w-full accent-indigo-500" />
          </div>
          <div>
            <label className="text-xs text-slate-400 block mb-1">Duration</label>
            <input className="input w-full" value={config.duration} onChange={(e) => set('duration', e.target.value)} />
          </div>
        </div>
      </div>

      <div className="card space-y-3">
        <h2 className="font-semibold text-white">Generated Command</h2>
        <SyntaxHighlighter language="bash" style={vscDarkPlus} customStyle={{ margin: 0, borderRadius: '0.375rem', fontSize: '0.8rem' }}>
          {generatedCmd}
        </SyntaxHighlighter>
        <p className="text-xs text-slate-500">Run this manually from the repo root if k6-server is not running.</p>
      </div>

      <div className="card space-y-4">
        <div className="flex items-center gap-4">
          <h2 className="font-semibold text-white">Runner</h2>
          <span className={`text-sm font-medium capitalize ${statusColor}`}>● {status}</span>
          {exitCode != null && <span className="text-xs text-slate-400">exit code: {exitCode}</span>}
        </div>
        <div className="flex gap-3">
          <button onClick={runTest} disabled={status === 'running'} className="btn-primary">Run Test</button>
          {status === 'running' && (
            <button onClick={stopTest} className="btn-danger">Stop Test</button>
          )}
        </div>
        <div
          ref={logRef}
          className="bg-slate-950 rounded-md p-4 h-72 overflow-y-auto font-mono text-xs text-slate-300 border border-slate-700"
        >
          {output.length === 0 ? (
            <span className="text-slate-600">No output yet. Click Run Test to start.</span>
          ) : (
            output.map((line, i) => <div key={i}>{line}</div>)
          )}
        </div>
        <p className="text-xs text-slate-500">
          Note: k6-server must be running. Start with: <code className="bg-slate-700 px-1 rounded">cd frontend/k6-server &amp;&amp; node index.js</code>
        </p>
      </div>
    </div>
  );
}
