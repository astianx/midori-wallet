class Updater {
  supported = false;
  state = 'disabled';

  launchCheckForUpdates() { return false; }
  checkForUpdates() { return Promise.resolve('disabled'); }
  quitAndInstall() { return false; }
}

export default new Updater();
