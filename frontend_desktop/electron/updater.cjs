const { app, ipcMain, shell } = require('electron');
const https = require('https');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { createLogger } = require('./logger.cjs');

// Same allowlist as the backend's DesktopReleaseValidator.isAllowedDownloadUrl
// (server/src/main/java/com/carrental/service/DesktopReleaseValidator.java) —
// kept in sync deliberately so a compromised/misconfigured backend response
// can never make the desktop app fetch an installer from an arbitrary host,
// even though the backend already validates this at publish time.
const ALLOWED_DOWNLOAD_HOSTS = [
  'github.com',
  'objects.githubusercontent.com',
  'release-assets.githubusercontent.com',
  'r2.cloudflarestorage.com',
  's3.amazonaws.com',
  'api.innovacar.app',
];

const CHANNELS = new Set(['STABLE', 'BETA']);
const CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000; // 4 hours
const STARTUP_CHECK_DELAY_MS = 15 * 1000; // let the app finish loading first

function isAllowedDownloadUrl(rawUrl) {
  try {
    const url = new URL(rawUrl);
    if (url.protocol !== 'https:') return false;
    const host = url.hostname.toLowerCase();
    return ALLOWED_DOWNLOAD_HOSTS.some((allowed) => host === allowed || host.endsWith(`.${allowed}`));
  } catch {
    return false;
  }
}

/** Strict MAJOR.MINOR.PATCH comparison — matches the backend's semver validation.
 * Returns >0 if `a` is newer than `b`, 0 if equal, <0 if older. Anything that
 * doesn't parse as three numeric parts is treated as not-newer (fail safe: never
 * offer an update we can't confidently compare). */
function compareSemver(a, b) {
  const parse = (v) => {
    const core = String(v).split('-')[0];
    const parts = core.split('.').map((n) => Number.parseInt(n, 10));
    if (parts.length !== 3 || parts.some((n) => Number.isNaN(n))) return null;
    return parts;
  };
  const pa = parse(a);
  const pb = parse(b);
  if (!pa || !pb) return 0;
  for (let i = 0; i < 3; i++) {
    if (pa[i] !== pb[i]) return pa[i] - pb[i];
  }
  return 0;
}

function sanitizeFileName(name) {
  const base = path.basename(String(name || 'Innovacar-Setup.exe'));
  const cleaned = base.replace(/[^A-Za-z0-9._-]/g, '_');
  return cleaned.endsWith('.exe') ? cleaned : `${cleaned}.exe`;
}

class UpdateSettingsStore {
  constructor(userDataPath) {
    this.filePath = path.join(userDataPath, 'update-settings.json');
  }

  read() {
    try {
      const raw = fs.readFileSync(this.filePath, 'utf8');
      const parsed = JSON.parse(raw);
      return {
        channel: CHANNELS.has(parsed.channel) ? parsed.channel : 'STABLE',
        dismissedVersion: typeof parsed.dismissedVersion === 'string' ? parsed.dismissedVersion : null,
      };
    } catch {
      return { channel: 'STABLE', dismissedVersion: null };
    }
  }

  write(next) {
    try {
      fs.writeFileSync(this.filePath, JSON.stringify(next), 'utf8');
    } catch {
      // non-fatal — worst case the channel preference resets to STABLE next launch
    }
  }
}

function apiBaseUrl() {
  const configured = process.env.INNOVACAR_API_URL;
  return configured && configured.startsWith('https://') ? configured : 'https://api.innovacar.app';
}

function httpsGetJson(url) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { headers: { Accept: 'application/json' } }, (res) => {
      if (res.statusCode && res.statusCode >= 400) {
        res.resume();
        reject(new Error(`HTTP ${res.statusCode}`));
        return;
      }
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => {
        try {
          resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
        } catch (err) {
          reject(err);
        }
      });
    });
    req.on('error', reject);
    req.setTimeout(10000, () => req.destroy(new Error('Request timed out')));
  });
}

/**
 * Wires the desktop auto-update lifecycle: check -> download (with SHA-256
 * verification against the value the backend recorded at publish time) ->
 * hand off to the NSIS installer -> quit so it can replace the running app.
 * No electron-updater dependency — the existing PublicDesktopReleaseController
 * (backend-managed releases, not GitHub's own release feed) is the source of
 * truth, so a small purpose-built client is simpler and lets us verify the
 * exact sha256 Super Admin recorded rather than trusting a generic provider's
 * own metadata format.
 */
class DesktopUpdater {
  constructor(getMainWindow) {
    this.getMainWindow = getMainWindow;
    this.log = createLogger(app.getPath('userData'), 'updater.log');
    this.settings = new UpdateSettingsStore(app.getPath('userData'));
    this.currentRelease = null; // last release metadata returned by a successful check
    this.downloadedFilePath = null;
    this.downloading = false;
  }

  send(type, payload) {
    const win = this.getMainWindow();
    if (win && !win.isDestroyed()) {
      win.webContents.send('updater:event', { type, ...payload });
    }
  }

  async check(channelOverride) {
    const channel = CHANNELS.has(channelOverride) ? channelOverride : this.settings.read().channel;
    this.send('checking', {});
    try {
      const url = `${apiBaseUrl()}/api/public/desktop/releases/latest?platform=WINDOWS&arch=X64&channel=${channel}`;
      const data = await httpsGetJson(url);
      if (!data || data.available !== true) {
        this.log.info(`check: no published release for channel=${channel}`);
        this.send('not-available', { channel });
        return { available: false };
      }
      const currentVersion = app.getVersion();
      const isNewer = compareSemver(data.version, currentVersion) > 0;
      this.log.info(`check: channel=${channel} latest=${data.version} current=${currentVersion} newer=${isNewer}`);
      if (!isNewer) {
        this.send('not-available', { channel, version: data.version });
        return { available: false };
      }
      this.currentRelease = data;
      this.send('available', { release: data });
      return { available: true, release: data };
    } catch (err) {
      this.log.error(`check failed: ${err.message}`);
      this.send('error', { message: 'Could not reach the update service.' });
      return { available: false, error: err.message };
    }
  }

  async download() {
    const release = this.currentRelease;
    if (!release) throw new Error('No update has been checked yet');
    if (this.downloading) throw new Error('Download already in progress');
    if (!isAllowedDownloadUrl(release.downloadUrl)) {
      this.log.error(`download blocked: disallowed host in ${release.downloadUrl}`);
      this.send('error', { message: 'Update download was blocked (untrusted host).' });
      throw new Error('Disallowed download host');
    }

    this.downloading = true;
    const fileName = sanitizeFileName(release.fileName);
    const destPath = path.join(app.getPath('temp'), `innovacar-update-${release.version}-${fileName}`);

    try {
      await this.streamDownload(release.downloadUrl, destPath, release);
      if (release.sha256) {
        const actualHash = await sha256File(destPath);
        if (actualHash.toLowerCase() !== String(release.sha256).toLowerCase()) {
          fs.rmSync(destPath, { force: true });
          this.log.error(`download: checksum mismatch expected=${release.sha256} actual=${actualHash}`);
          this.send('error', { message: 'Downloaded update failed integrity verification.' });
          throw new Error('Checksum mismatch');
        }
        this.log.info('download: checksum verified');
      }
      this.downloadedFilePath = destPath;
      this.send('downloaded', { release, filePath: destPath });
      return { filePath: destPath };
    } finally {
      this.downloading = false;
    }
  }

  streamDownload(url, destPath, release) {
    return new Promise((resolve, reject) => {
      const file = fs.createWriteStream(destPath);
      const totalBytes = Number(release.fileSizeBytes) || 0;
      let receivedBytes = 0;
      let lastEmit = 0;

      const req = https.get(url, (res) => {
        if (res.statusCode && res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          // Follow a single redirect (release CDNs commonly 302 to a signed asset URL)
          // and re-validate the redirect target against the same host allowlist.
          file.close();
          fs.rmSync(destPath, { force: true });
          if (!isAllowedDownloadUrl(res.headers.location)) {
            reject(new Error('Redirected to a disallowed host'));
            return;
          }
          this.streamDownload(res.headers.location, destPath, release).then(resolve, reject);
          return;
        }
        if (res.statusCode && res.statusCode >= 400) {
          file.close();
          fs.rmSync(destPath, { force: true });
          reject(new Error(`HTTP ${res.statusCode}`));
          return;
        }
        res.on('data', (chunk) => {
          receivedBytes += chunk.length;
          const now = Date.now();
          if (now - lastEmit > 200) {
            lastEmit = now;
            this.send('progress', {
              receivedBytes,
              totalBytes,
              percent: totalBytes > 0 ? Math.min(100, Math.round((receivedBytes / totalBytes) * 100)) : null,
            });
          }
        });
        res.pipe(file);
        file.on('finish', () => {
          file.close(() => resolve());
        });
      });
      req.on('error', (err) => {
        file.close();
        fs.rmSync(destPath, { force: true });
        reject(err);
      });
      file.on('error', (err) => {
        fs.rmSync(destPath, { force: true });
        reject(err);
      });
    });
  }

  /** Launches the (already checksum-verified) NSIS installer and quits so it can
   * replace the running app's files. The installer isn't run silently — this
   * build's NSIS config sets oneClick:false so the user can still choose/confirm
   * the install directory — the user sees the normal installer UI. */
  async install() {
    if (!this.downloadedFilePath || !fs.existsSync(this.downloadedFilePath)) {
      throw new Error('No downloaded update to install');
    }
    this.log.info(`install: launching ${this.downloadedFilePath}`);
    await shell.openPath(this.downloadedFilePath);
    app.quit();
  }

  getChannel() {
    return this.settings.read().channel;
  }

  setChannel(channel) {
    if (!CHANNELS.has(channel)) throw new Error('Invalid channel');
    const current = this.settings.read();
    this.settings.write({ ...current, channel });
  }

  dismiss(version) {
    const current = this.settings.read();
    this.settings.write({ ...current, dismissedVersion: version });
  }

  isDismissed(version) {
    return this.settings.read().dismissedVersion === version;
  }
}

function sha256File(filePath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(filePath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('error', reject);
    stream.on('end', () => resolve(hash.digest('hex')));
  });
}

/** Registers IPC handlers and kicks off the startup/periodic check timers.
 * Called once from main.cjs after the main window exists. */
function initUpdater(getMainWindow) {
  const updater = new DesktopUpdater(getMainWindow);

  ipcMain.handle('updater:get-version', () => app.getVersion());
  ipcMain.handle('updater:get-channel', () => updater.getChannel());
  ipcMain.handle('updater:set-channel', (_event, channel) => {
    updater.setChannel(channel);
    return updater.getChannel();
  });
  ipcMain.handle('updater:check', (_event, channel) => updater.check(channel));
  ipcMain.handle('updater:download', () => updater.download());
  ipcMain.handle('updater:install', () => updater.install());
  ipcMain.handle('updater:dismiss', (_event, version) => {
    updater.dismiss(version);
    return true;
  });
  ipcMain.handle('updater:get-logs-dir', () => updater.log.dir);

  setTimeout(() => updater.check().catch(() => {}), STARTUP_CHECK_DELAY_MS);
  setInterval(() => updater.check().catch(() => {}), CHECK_INTERVAL_MS);

  return updater;
}

module.exports = { initUpdater, compareSemver, isAllowedDownloadUrl };
