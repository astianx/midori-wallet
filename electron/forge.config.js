import fs from 'node:fs';
const pkg = JSON.parse(fs.readFileSync(new URL('./package.json',import.meta.url),'utf8'));
export default {
  packagerConfig: {
    appVersion: pkg.version, appBundleId: 'org.astian.miwallet',
    executableName: 'midori-wallet', icon: 'resources/icon', asar: true,
    appCategoryType: 'public.app-category.finance',
    protocols: [{ name: 'Midori Wallet', schemes: ['midoriwallet','ethereum'] }],
    ignore: [/^\/(?!electron\.js|package\.json|lib|dist|resources|node_modules)/, /\.map$/, /\/node_modules\/.*\/(test|tests|docs|example|examples)\//],
    extendInfo: { LSMinimumSystemVersion: '12.0', NSCameraUsageDescription: 'Scan a payment QR code' },
    osxSign: false, osxNotarize: undefined,
  },
  makers: [
    { name: '@electron-forge/maker-zip', platforms: ['darwin','linux','win32'] },
    { name: '@electron-forge/maker-deb', platforms: ['linux'], config: { options: { name: 'midori-wallet', maintainer: 'Midori Wallet', homepage: 'https://astian.org/midori-wallet/', categories: ['Office','Finance'] } } },
    { name: '@electron-forge/maker-squirrel', platforms: ['win32'], config: { name: 'midori_wallet', authors: 'Midori Wallet', description: 'Midori Wallet' } },
    { name: '@electron-forge/maker-dmg', platforms: ['darwin'], config: { name: 'Midori Wallet', icon: 'resources/icon.icns' } },
  ],
  publishers: [],
};
