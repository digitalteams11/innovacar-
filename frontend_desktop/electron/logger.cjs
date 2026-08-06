const fs = require('fs');
const path = require('path');

// Minimal file logger shared by main.cjs/updater.cjs. Deliberately dependency-free
// (no electron-log) since the only requirements are: land in userData/logs, rotate
// before the file gets large, and never throw (a logging failure must never crash
// the app). Never pass tokens/passwords/PII to log() — callers are responsible for
// that, same as the console.* calls this replaces.
const MAX_LOG_BYTES = 2 * 1024 * 1024; // 2MB before rotating to .1

function createLogger(userDataPath, fileName) {
  const dir = path.join(userDataPath, 'logs');
  const filePath = path.join(dir, fileName);

  function ensureDir() {
    try {
      fs.mkdirSync(dir, { recursive: true });
    } catch {
      // best-effort — if this fails, writes below will also fail and be swallowed
    }
  }

  function rotateIfNeeded() {
    try {
      const stat = fs.statSync(filePath);
      if (stat.size > MAX_LOG_BYTES) {
        const rotated = `${filePath}.1`;
        fs.rmSync(rotated, { force: true });
        fs.renameSync(filePath, rotated);
      }
    } catch {
      // file doesn't exist yet — nothing to rotate
    }
  }

  function write(level, message) {
    try {
      ensureDir();
      rotateIfNeeded();
      const line = `${new Date().toISOString()} [${level}] ${message}\n`;
      fs.appendFileSync(filePath, line, 'utf8');
    } catch {
      // logging must never throw into the caller
    }
  }

  return {
    dir,
    filePath,
    info: (message) => write('INFO', message),
    warn: (message) => write('WARN', message),
    error: (message) => write('ERROR', message),
  };
}

module.exports = { createLogger };
