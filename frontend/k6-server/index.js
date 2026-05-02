const express = require('express');
const cors = require('cors');
const { spawn } = require('child_process');
const path = require('path');

const app = express();
app.use(cors());
app.use(express.json());

const TEST_FILES = {
  load: '../../k6/load-test.js',
  stress: '../../k6/stress-test.js',
  inventory: '../../k6/inventory-load-test.js',
};

let job = {
  status: 'idle',
  output: [],
  exitCode: null,
  process: null,
};

app.post('/api/k6/run', (req, res) => {
  if (job.status === 'running') {
    return res.status(409).json({ error: 'A test is already running' });
  }

  const { testType = 'load', baseUrl = 'http://localhost:8080', username = 'admin', password = 'admin123' } = req.body;
  const testFile = TEST_FILES[testType];
  if (!testFile) {
    return res.status(400).json({ error: 'Invalid testType' });
  }

  const scriptPath = path.resolve(__dirname, testFile);
  job = { status: 'running', output: [], exitCode: null, process: null };

  const env = {
    ...process.env,
    BASE_URL: baseUrl,
    K6_USERNAME: username,
    K6_PASSWORD: password,
  };

  const proc = spawn('k6', ['run', scriptPath], { env });
  job.process = proc;

  proc.stdout.on('data', (data) => {
    const lines = data.toString().split('\n');
    lines.forEach((l) => { if (l.trim()) job.output.push(l); });
  });

  proc.stderr.on('data', (data) => {
    const lines = data.toString().split('\n');
    lines.forEach((l) => { if (l.trim()) job.output.push(l); });
  });

  proc.on('close', (code) => {
    job.status = code === 0 ? 'complete' : 'error';
    job.exitCode = code;
    job.process = null;
  });

  proc.on('error', (err) => {
    job.status = 'error';
    job.output.push(`Failed to start k6: ${err.message}`);
    job.process = null;
  });

  res.json({ started: true });
});

app.get('/api/k6/status', (req, res) => {
  res.json({
    status: job.status,
    output: job.output,
    exitCode: job.exitCode,
  });
});

app.post('/api/k6/stop', (req, res) => {
  if (job.process) {
    job.process.kill('SIGTERM');
    job.status = 'idle';
    job.process = null;
  }
  res.json({ stopped: true });
});

const PORT = 3001;
app.listen(PORT, () => {
  console.log(`k6-server listening on http://localhost:${PORT}`);
});
