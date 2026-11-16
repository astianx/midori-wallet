import fs from 'node:fs';
import { execFileSync } from 'node:child_process';
import { setTimeout } from 'node:timers/promises';

export async function createDmg({ name, folder, destination }, { run = execFileSync, sleep = setTimeout } = {}) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    try {
      run('hdiutil', ['create', '-volname', name, '-srcfolder', folder, '-fs', 'APFS', '-ov', '-format', 'UDZO', destination], {
        encoding: 'utf8', timeout: 120000, stdio: ['ignore', 'pipe', 'pipe'],
      });
      run('hdiutil', ['verify', destination], { encoding: 'utf8', timeout: 120000, stdio: ['ignore', 'pipe', 'pipe'] });
      return;
    } catch (error) {
      fs.rmSync(destination, { force: true });
      const detail = `${error.stderr || ''}\n${error.stdout || ''}`;
      if (attempt === 3 || !/Resource busy/i.test(detail)) throw error;
      console.warn(`DMG resource busy; retry ${attempt}/2 in ${attempt * 2}s`);
      await sleep(attempt * 2000);
    }
  }
}
