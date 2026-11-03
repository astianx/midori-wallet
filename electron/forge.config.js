import appxmanifest from './support/appxmanifest.js';
import fs from 'node:fs/promises';
import path from 'node:path';
import setLanguages from 'electron-packager-languages';
const schemes = [
  'midoriwallet',
  ...(await import('./dist/schemes.js')).default.map((item) => item.scheme),
];
import { languages } from '../web/src/lib/i18n/languages.js';
const pkg = JSON.parse(await fs.readFile('./package.json'));

const { VITE_DISTRIBUTION } = process.env;

if (!['appx', 'appx-dev', 'mac', 'mas', 'mas-dev', 'snap', 'flatpak'].includes(VITE_DISTRIBUTION)) {
  throw new Error(`Unsupported distribution: '${VITE_DISTRIBUTION}'`);
}

let buildVersion = pkg.version;

if (VITE_DISTRIBUTION === 'mas' && process.env.GITHUB_RUN_NUMBER) {
  buildVersion = `1.1.${process.env.GITHUB_RUN_NUMBER}`;
}

const appxVersion = `${pkg.version}.${VITE_DISTRIBUTION === 'appx' ? '0' : (process.env.GITHUB_RUN_NUMBER || '0')}`;

const protocols = {
  name: pkg.productName,
  schemes,
};
const appxPackageName = VITE_DISTRIBUTION === 'appx' ? 'MidoriWallet' : 'MidoriWalletDev';

export default {
  packagerConfig: {
    appVersion: pkg.version,
    buildVersion,
    //asar: true,
    icon: 'resources/icon',
    executableName: ['appx', 'appx-dev'].includes(VITE_DISTRIBUTION) ? pkg.productName : pkg.executableName,
    ignore: [
      /README.md/i,
      /HISTORY.md/i,
      /CHANGELOG.md/i,
      '^/(?!electron.js|package.json|lib|dist|resources|node_modules)',
      ['appx', 'appx-dev', 'snap', 'flatpak'].includes(VITE_DISTRIBUTION) ? '^/resources/(?!64x64.png)' : '^/resources',
      'Makefile',
      '.editorconfig',
      '.gitignore',
      '.gitattributes',
      '.nvmrc',
      '.npmignore',
      '.nycrc',
      '.jsfmtrc',
      '.eslintrc',
      '.eslintignore',
      '.github',
      '.coveralls.yml',
      '.istanbul.yml',
      '.jscs.json',
      'Thumbs.db',
    ],
    appBundleId: 'com.coinspace.wallet',
    appCategoryType: 'public.app-category.finance',
    extendInfo: {
      LSMinimumSystemVersion: '11.0',
      NSCameraUsageDescription: 'This app uses the camera to scan QR codes.',
      ...(['mas', 'mas-dev'].includes(VITE_DISTRIBUTION) ? {} : {
        NSLocationUsageDescription: 'Turn on location services to send or receive coins with people around you.',
      }),
    },
    osxSign: process.env.MIDORI_SIGN_ARTIFACTS === 'true' ? {
      type: VITE_DISTRIBUTION === 'mas-dev' ? 'development' : 'distribution',
      provisioningProfile: ['mas', 'mas-dev'].includes(VITE_DISTRIBUTION) ? 'embedded.provisionprofile' : undefined,
      optionsForFile(filePath) {
        if (VITE_DISTRIBUTION === 'mac') {
          let entitlements = 'resources/entitlements.mac.plist';
          if (filePath.includes('(Plugin).app')) {
            entitlements = 'resources/entitlements.mac.plugin.plist';
          } else if (filePath.includes('(GPU).app')) {
            entitlements = 'resources/entitlements.mac.gpu.plist';
          } else if (filePath.includes('(Renderer).app')) {
            entitlements = 'resources/entitlements.mac.renderer.plist';
          }
          return {
            hardenedRuntime: true,
            entitlements,
          };
        }
        if (['mas', 'mas-dev'].includes(VITE_DISTRIBUTION)) {
          const entitlements = filePath.includes('.app/')
            ? 'resources/entitlements.mas.inherit.plist'
            : 'resources/entitlements.mas.plist';
          return {
            hardenedRuntime: false,
            entitlements,
          };
        }
      },
    } : undefined,
    osxNotarize: undefined,
    protocols,
    afterCopy: [
      ...(['mac', 'mas', 'mas-dev'].includes(VITE_DISTRIBUTION) ? [setLanguages(languages.map((lang) => {
        return lang.value.replace('-', '_').replace(/_[a-z]+/, s => s.toUpperCase());
      }))] : []),
      ...(['appx', 'appx-dev'].includes(VITE_DISTRIBUTION) ? [appxmanifest({
        packageVersion: appxVersion,
        identityName: VITE_DISTRIBUTION === 'appx' ? process.env.APPX_IDENTITY : pkg.executableName,
        packageName: appxPackageName,
        packageDescription: pkg.description,
        //packageDisplayName: process.env.APPX_PACKAGE_NAME,
        packageDisplayName: pkg.productName,
        publisherName: VITE_DISTRIBUTION === 'appx' ? process.env.APPX_PUBLISHER : process.env.APPX_PUBLISHER_DEV,
        publisherDisplayName: process.env.APPX_PUBLISHER_NAME,
        packageExecutable: `app\\${pkg.productName}.exe`,
        languages: languages.map((lang) => {
          // https://docs.microsoft.com/en-us/windows/uwp/publish/supported-languages
          switch (lang.value) {
            case 'sr':
              return 'sr-Latn';
            default:
              return lang.value;
          }
        }),
        protocols,
      })] : []),
    ],
  },
  makers: [
    VITE_DISTRIBUTION === 'appx' && {
      name: '@electron-forge/maker-appx',
      config: {
        packageName: appxPackageName,
        packageVersion: appxVersion,
        identityName: process.env.APPX_IDENTITY,
        publisher: process.env.APPX_PUBLISHER,
        assets: 'resources/appx',
        manifest: 'resources/appxmanifest.xml',
        makePri: true,
      },
    },
    VITE_DISTRIBUTION === 'appx-dev' && {
      name: '@electron-forge/maker-appx',
      config: {
        packageName: appxPackageName,
        packageVersion: appxVersion,
        identityName: pkg.name,
        publisher: process.env.APPX_PUBLISHER_DEV,
        devCert: 'resources/certificate.pfx',
        certPass: process.env.CERTIFICATE_SELFSIGN_WIN_PASSWORD,
        assets: 'resources/appx',
        manifest: 'resources/appxmanifest.xml',
        makePri: true,
      },
    },
    {
      name: '@electron-forge/maker-zip',
      platforms: [
        'darwin',
      ],
    },
    {
      name: '@electron-forge/maker-dmg',
      platforms: [
        'darwin',
      ],
      config: {
        name: pkg.productName,
        title: `${pkg.productName} ${pkg.version}`,
        icon: 'resources/icon.icns',
        background: 'resources/background.tiff',
        contents: (opts) => {
          return [{
            x: 130, y: 220, type: 'file', path: opts.appPath,
          }, {
            x: 410, y: 220, type: 'link', path: '/Applications',
          }];
        },
        additionalDMGOptions: {
          window: {
            position: {
              x: 400,
              y: 100,
            },
            size: {
              width: 540,
              height: 380,
            },
          },
        },
      },
    },
    {
      name: '@electron-forge/maker-pkg',
      platforms: [
        'mas',
      ],
      config: {
        name: `${pkg.productName}-${pkg.version}${VITE_DISTRIBUTION === 'mas-dev' ? '-dev' : ''}`,
      },
    },
    VITE_DISTRIBUTION === 'snap' && {
      name: './support/snap.js',
      config: {
        linux: {
          icon: 'resources/icon.icns',
          executableName: pkg.executableName,
        },
        snapcraft: {
          base: 'core24',
          artifactName: `${pkg.executableName}-${pkg.version}.snap`,

          core24: {
            summary: pkg.description,
            category: 'Office;Finance',
            plugs: ['default', 'browser-support', 'u2f-devices'],
            stagePackages: ['default'],
            // mesa-2404 GPU rendering is broken on Ubuntu 26.04
            executableArgs: ['--disable-gpu'],
            useLXD: true,
          },
        },
        protocols,
        publish: 'never',
      },
    },
    VITE_DISTRIBUTION === 'flatpak' && {
      name: '@electron-forge/maker-flatpak',
      config: {
        options: {
          id: 'space.coin.wallet',
          bin: pkg.executableName,
          productName: pkg.productName,
          genericName: 'Wallet',
          description: pkg.description,
          categories: ['Office', 'Finance'],
          icon: {
            scalable: 'resources/icon.svg',
          },
          runtimeVersion: '24.08',
          // Available versions: https://github.com/flathub/org.electronjs.Electron2.BaseApp/
          baseVersion: '24.08',
          modules: [{
            name: 'zypak',
            sources: [{
              type: 'git',
              url: 'https://github.com/refi64/zypak',
              tag: 'v2024.01.17',
            }],
          }],
          finishArgs: [
            // X Rendering
            '--socket=x11', '--socket=wayland', '--share=ipc',
            // OpenGL
            '--device=dri',
            // Audio output
            '--socket=pulseaudio',
            // Read/write home directory access
            '--filesystem=home',
            // Chromium uses a socket in tmp for its singleton check
            '--env=TMPDIR=/var/tmp',
            // Allow communication with network
            '--share=network',
            // System notifications with libnotify
            '--talk-name=org.freedesktop.Notifications',
            // USB and webcam
            '--device=all',
          ],
          mimeType: schemes.map((scheme) => `x-scheme-handler/${scheme}`),
        },
        rename(dest/*, src*/) {
          return path.join(dest, `${pkg.productName}.flatpak`);
        },
      },
    },
  ].filter(item => !!item),
  publishers: [],
};
