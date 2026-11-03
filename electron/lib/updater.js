import { app, dialog, shell } from 'electron';
import { createGitHubUpdates, compareVersions } from '../dist/update-core.js';
import { MIDORI_UPDATE_REPOSITORY, MIDORI_UPDATE_CHANNEL, MIDORI_UPDATE_PUBLIC_KEY, MIDORI_BUILD_PROFILE } from './constants.js';

class Updater {
  state = 'disabled';
  #source = createGitHubUpdates({ env: { MIDORI_UPDATE_REPOSITORY, MIDORI_UPDATE_CHANNEL, MIDORI_UPDATE_PUBLIC_KEY } });
  supported = this.#source.enabled && MIDORI_UPDATE_CHANNEL === (MIDORI_BUILD_PROFILE === 'release' ? 'stable' : 'beta');
  #checking = false;
  launchCheckForUpdates() { return false; }
  async checkForUpdates() {
    if (!this.supported || this.#checking) return this.state;
    this.#checking = true;
    this.state = 'checking-for-update';
    try {
      const update = await this.#source.getUpdate(process.platform, process.arch);
      if (!update || compareVersions(update.version, app.getVersion()) <= 0) { this.state = 'update-not-available'; return this.state; }
      this.state = 'update-available';
      const result = await dialog.showMessageBox({ type: 'info', title: 'Midori Wallet update', message: `Midori Wallet ${update.version}`, detail: `Verified release manifest (${update.channel})\nSHA-256: ${update.sha256}\nDownload the installer from the Midori GitHub repository.`, buttons: ['Cancel', 'Open download'], defaultId: 0, cancelId: 0 });
      if (result.response === 1) await shell.openExternal(update.url);
    } catch { this.state = 'error'; }
    finally { this.#checking = false; }
    return this.state;
  }
  quitAndInstall() { return false; }
}
export default new Updater();
