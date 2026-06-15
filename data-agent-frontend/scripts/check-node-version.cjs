'use strict';

const fs = require('fs');
const path = require('path');

const MIN_MAJOR = 18;
const major = parseInt(process.version.slice(1).split('.')[0], 10);
const ok = major >= MIN_MAJOR;

// #region agent log
try {
  const logLine =
    JSON.stringify({
      sessionId: '886f81',
      runId: 'post-fix',
      hypothesisId: 'H1',
      location: 'check-node-version.cjs',
      message: 'node version check',
      data: { version: process.version, execPath: process.execPath, major, ok, minMajor: MIN_MAJOR },
      timestamp: Date.now(),
    }) + '\n';
  fs.appendFileSync(path.join(__dirname, '..', '..', 'debug-886f81.log'), logLine);
} catch (_) {
  /* ignore logging errors */
}
// #endregion

if (!ok) {
  console.error('');
  console.error('[DataAgent] Node.js version too old for Vite 5.');
  console.error(`  Current : ${process.version} (${process.execPath})`);
  console.error(`  Required: >= v${MIN_MAJOR}.0.0 (recommend Node 20 LTS or 22 LTS)`);
  console.error('');
  console.error('Fix: install Node.js from https://nodejs.org/ and ensure `node -v` and `npm -v`');
  console.error('     come from the same installation (run `where node` / `where npm` to verify).');
  console.error('');
  process.exit(1);
}
