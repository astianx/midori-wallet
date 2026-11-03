import fs from 'node:fs';
const pkg = JSON.parse(fs.readFileSync(new URL('./package.json',import.meta.url),'utf8'));
const beta = process.env.MIDORI_BUILD_PROFILE !== 'release';
const appName = beta ? 'Midori Wallet Beta' : 'Midori Wallet';
const executable = beta ? 'midori-wallet-beta' : 'midori-wallet';
export default {
  packagerConfig: {
    name: appName, appVersion: pkg.version, appBundleId: beta ? 'org.astian.miwallet.beta' : 'org.astian.miwallet',
    executableName: executable, icon: 'resources/icon', asar: true,
    appCategoryType: 'public.app-category.finance',
    protocols: [{ name: appName, schemes: beta ? ['midoriwallet-beta'] : ['midoriwallet','ethereum'] }],
    ignore: [/^\/(?!electron\.js|package\.json|lib|dist|resources|node_modules)/, /\.map$/, /\/node_modules\/.*\/(test|tests|docs|example|examples)\//],
    extendInfo: { LSMinimumSystemVersion: '12.0', NSCameraUsageDescription: 'Scan a payment QR code' },
    osxSign: false, osxNotarize: undefined,
  },
  makers: [
    { name: '@electron-forge/maker-zip', platforms: ['darwin','linux','win32'] },
    ...(process.env.MIDORI_DESKTOP_FORMAT === 'zip' ? [] : [{ name: '@electron-forge/maker-deb', platforms: ['linux'], config: { options: { name: executable, maintainer: 'Midori Wallet', homepage: 'https://astian.org/midori-wallet/', categories: ['Office','Finance'] } } }]),
    { name: '@electron-forge/maker-squirrel', platforms: ['win32'], config: { name: beta ? 'midori_wallet_beta' : 'midori_wallet', authors: 'Midori Wallet', description: 'Midori Wallet' } },
  ],
  publishers: [],
};
