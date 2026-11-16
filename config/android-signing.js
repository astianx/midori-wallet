import fs from 'node:fs';
import path from 'node:path';

export function androidSigningConfig(env) {
  const names = ['ANDROID_KEYSTORE_PATH', 'ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_ALIAS', 'ANDROID_KEY_PASSWORD'];
  const missing = names.filter(name => !env[name]);
  if (missing.length === names.length && env.MIDORI_SIGN_ARTIFACTS !== 'true') return null;
  if (missing.length) throw new Error(`Missing Android signing configuration: ${missing.join(', ')}`);
  const keystore = path.resolve(env.ANDROID_KEYSTORE_PATH);
  if (!fs.existsSync(keystore) || !fs.statSync(keystore).isFile()) throw new Error('Android signing keystore is not a file');
  return {
    keystore, storePassword: env.ANDROID_KEYSTORE_PASSWORD,
    alias: env.ANDROID_KEY_ALIAS, password: env.ANDROID_KEY_PASSWORD, keystoreType: 'pkcs12',
  };
}
