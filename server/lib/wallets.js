import { randomUUID } from 'node:crypto';
import { verifyMessage } from 'ethers';
import { ApiError, exactKeys, requireValue, unauthorized } from './errors.js';
import { canonical, challengeMessage, publicKey, verifyEd25519, uuidPattern } from './crypto.js';
import { validateDescriptor } from './descriptor.js';
import { consumeChallenge, createSession, issueChallenge, lockDevice, lockWallet } from './repositories.js';

export function walletService(db, config) {
  return {
    async registrationOptions(body) {
      exactKeys(body, ['walletPublicKey', 'devicePublicKey', 'descriptor']);
      publicKey(body.walletPublicKey); publicKey(body.devicePublicKey); validateDescriptor(body.descriptor);
      requireValue(body.walletPublicKey !== body.devicePublicKey, 'Use distinct identity and device keys');
      return issueChallenge(db, config, 'register', body);
    },
    async register(body) {
      exactKeys(body, ['id', 'challenge', 'walletSignature', 'deviceSignature', 'ownerSignature']);
      const result = await db.transaction(async client => {
        const context = await consumeChallenge(client, body.id, 'register', body.challenge);
        if (!context) return null;
        const message = challengeMessage(config.publicOrigin, body.id, 'register', body.challenge, context);
        if (!verifyEd25519(context.walletPublicKey, message, body.walletSignature) || !verifyEd25519(context.devicePublicKey, message, body.deviceSignature)) return null;
        try { if (verifyMessage(message, body.ownerSignature) !== context.descriptor.ownerAddress) return null; } catch { return null; }
        validateDescriptor(context.descriptor);
        const walletId = randomUUID(); const deviceId = randomUUID(); const accountId = randomUUID();
        await client.query('INSERT INTO wallets(id,public_key) VALUES ($1,$2)', [walletId, context.walletPublicKey]);
        await client.query('INSERT INTO devices(id,wallet_id,public_key) VALUES ($1,$2,$3)', [deviceId, walletId, context.devicePublicKey]);
        await client.query('INSERT INTO chain_accounts(id,wallet_id,chain_id,address,owner_address,account_index,descriptor) VALUES ($1,$2,8453,$3,$4,$5,$6)', [accountId, walletId, context.descriptor.address.toLowerCase(), context.descriptor.ownerAddress.toLowerCase(), context.descriptor.index, context.descriptor]);
        await client.query('INSERT INTO account_assets(account_id,chain_id,contract) SELECT $1,chain_id,contract FROM asset_registry WHERE enabled', [accountId]);
        return { ...await createSession(client, config, { id: deviceId, wallet_id: walletId }), accountId };
      });
      if (!result) throw unauthorized();
      return result;
    },
    async loginOptions(body) {
      exactKeys(body, ['deviceId']); requireValue(uuidPattern.test(body.deviceId), 'Invalid device ID');
      await limitDevice(body.deviceId);
      return issueChallenge(db, config, 'login', { deviceId: body.deviceId });
    },
    async login(body) {
      exactKeys(body, ['id', 'challenge', 'signature']);
      const result = await db.transaction(async client => {
        const context = await consumeChallenge(client, body.id, 'login', body.challenge);
        if (!context) return null;
        const device = await lockDevice(client, context.deviceId);
        if (!device || !verifyEd25519(device.public_key, challengeMessage(config.publicOrigin, body.id, 'login', body.challenge, context), body.signature)) return null;
        return createSession(client, config, device);
      });
      if (!result) throw unauthorized();
      return result;
    },
    async recoveryOptions(body) {
      exactKeys(body, ['walletPublicKey', 'devicePublicKey']);
      publicKey(body.walletPublicKey); publicKey(body.devicePublicKey);
      requireValue(body.walletPublicKey !== body.devicePublicKey);
      return issueChallenge(db, config, 'recover', body);
    },
    async recover(body) {
      exactKeys(body, ['id', 'challenge', 'walletSignature', 'deviceSignature']);
      const result = await db.transaction(async client => {
        const context = await consumeChallenge(client, body.id, 'recover', body.challenge);
        if (!context) return null;
        const message = challengeMessage(config.publicOrigin, body.id, 'recover', body.challenge, context);
        if (!verifyEd25519(context.walletPublicKey, message, body.walletSignature) || !verifyEd25519(context.devicePublicKey, message, body.deviceSignature)) return null;
        const { rows } = await client.query('SELECT id FROM wallets WHERE public_key=$1', [context.walletPublicKey]);
        if (!rows.length) return null;
        const walletId = rows[0].id;
        await lockWallet(client, walletId);
        const { rows: devices } = await client.query('SELECT id,wallet_id,revoked_at FROM devices WHERE public_key=$1 FOR UPDATE', [context.devicePublicKey]);
        if (devices.length) {
          const device = devices[0];
          if (device.wallet_id !== walletId || device.revoked_at) return null;
          return createSession(client, config, device);
        }
        await checkDeviceLimit(client, walletId, config.maxDevices);
        const deviceId = randomUUID();
        await client.query('INSERT INTO devices(id,wallet_id,public_key) VALUES ($1,$2,$3)', [deviceId, walletId, context.devicePublicKey]);
        return createSession(client, config, { id: deviceId, wallet_id: walletId });
      });
      if (!result) throw unauthorized();
      return result;
    },
  };

  async function limitDevice(deviceId) {
    const { limitAttempts } = await import('./repositories.js');
    await limitAttempts(db, `device:${deviceId}`, 10, 300);
  }
}

export async function checkDeviceLimit(client, walletId, max) {
  const { rows } = await client.query('SELECT count(*)::integer AS count FROM devices WHERE wallet_id=$1 AND revoked_at IS NULL', [walletId]);
  if (rows[0].count >= max) throw new ApiError(409, 'DEVICE_LIMIT', 'Device limit reached');
}

export function deviceEnrollmentMessage(walletId, devicePublicKey) {
  return `Midori device enrollment v1\n${canonical({ walletId, devicePublicKey })}`;
}
