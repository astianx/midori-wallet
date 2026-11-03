import {
  generateAuthenticationOptions, generateRegistrationOptions,
  verifyAuthenticationResponse, verifyRegistrationResponse,
} from '@simplewebauthn/server';
import { exactKeys, requireValue, unauthorized, ApiError } from './errors.js';
import { activePrincipal, consumeChallenge, createSession, issueChallenge, limitAttempts, lockDevice, lockWallet } from './repositories.js';
import { uuidPattern } from './crypto.js';

export function webauthnService(db, config) {
  return {
    async registrationOptions(client, principal) {
      const { rows } = await client.query('SELECT credential_id FROM authenticators WHERE wallet_id=$1 AND revoked_at IS NULL', [principal.wallet_id]);
      if (rows.length >= config.maxAuthenticators) throw new ApiError(409, 'AUTHENTICATOR_LIMIT', 'Authenticator limit reached');
      const options = await generateRegistrationOptions({ rpName: 'Midori Wallet', rpID: config.rpID, userID: new TextEncoder().encode(principal.wallet_id), userName: principal.wallet_id, attestationType: 'none', supportedAlgorithmIDs: [-7, -8, -257], authenticatorSelection: { residentKey: 'preferred', userVerification: 'required' }, excludeCredentials: rows.map(row => ({ id: row.credential_id })) });
      const challenge = await issueChallenge(client, config, 'webauthn-register', { deviceId: principal.device_id, walletId: principal.wallet_id }, options.challenge);
      return { ...challenge, options };
    },
    async register(principal, body) {
      exactKeys(body, ['id', 'challenge', 'response']);
      const result = await db.transaction(async client => {
        await activePrincipal(client, principal);
        const context = await consumeChallenge(client, body.id, 'webauthn-register', body.challenge);
        if (!context || context.deviceId !== principal.device_id || context.walletId !== principal.wallet_id) return false;
        await lockWallet(client, principal.wallet_id);
        const count = await client.query('SELECT count(*)::integer AS count FROM authenticators WHERE wallet_id=$1 AND revoked_at IS NULL', [principal.wallet_id]);
        if (count.rows[0].count >= config.maxAuthenticators) return false;
        let verification;
        try { verification = await verifyRegistrationResponse({ response: body.response, expectedChallenge: body.challenge, expectedOrigin: config.allowedOrigins, expectedRPID: config.rpID, requireUserVerification: true }); } catch { return false; }
        if (!verification.verified) return false;
        const { credential, credentialDeviceType, credentialBackedUp } = verification.registrationInfo;
        await client.query('INSERT INTO authenticators(credential_id,wallet_id,device_id,public_key,counter,transports,device_type,backed_up) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)', [credential.id, principal.wallet_id, principal.device_id, Buffer.from(credential.publicKey), credential.counter, JSON.stringify(credential.transports || []), credentialDeviceType, credentialBackedUp]);
        return true;
      });
      if (!result) throw unauthorized();
      return { registered: true };
    },
    async loginOptions(body) {
      exactKeys(body, ['deviceId']); requireValue(uuidPattern.test(body.deviceId));
      await limitAttempts(db, `webauthn:${body.deviceId}`, 10, 300);
      const { rows } = await db.query(`SELECT a.* FROM authenticators a JOIN devices d ON d.id=a.device_id WHERE a.device_id=$1 AND a.revoked_at IS NULL AND d.revoked_at IS NULL`, [body.deviceId]);
      if (!rows.length) throw unauthorized();
      const options = await generateAuthenticationOptions({ rpID: config.rpID, userVerification: 'required', allowCredentials: rows.map(row => ({ id: row.credential_id, transports: row.transports })) });
      const revisions = Object.fromEntries(rows.map(row => [row.credential_id, row.revision]));
      const challenge = await issueChallenge(db, config, 'webauthn-login', { deviceId: body.deviceId, revisions }, options.challenge);
      return { ...challenge, options };
    },
    async login(body) {
      exactKeys(body, ['id', 'challenge', 'response']);
      const result = await db.transaction(async client => {
        const context = await consumeChallenge(client, body.id, 'webauthn-login', body.challenge);
        if (!context) return null;
        const device = await lockDevice(client, context.deviceId);
        if (!device || !body.response?.id || !Object.hasOwn(context.revisions, body.response.id)) return null;
        const { rows } = await client.query('SELECT * FROM authenticators WHERE credential_id=$1 AND device_id=$2 AND revoked_at IS NULL FOR UPDATE', [body.response.id, device.id]);
        const credential = rows[0];
        if (!credential || credential.revision !== context.revisions[credential.credential_id]) return null;
        let verification;
        try { verification = await verifyAuthenticationResponse({ response: body.response, expectedChallenge: body.challenge, expectedOrigin: config.allowedOrigins, expectedRPID: config.rpID, requireUserVerification: true, credential: { id: credential.credential_id, publicKey: new Uint8Array(credential.public_key), counter: Number(credential.counter), transports: credential.transports } }); } catch { return null; }
        if (!verification.verified) return null;
        const { newCounter, credentialBackedUp } = verification.authenticationInfo;
        const updated = await client.query(`UPDATE authenticators SET counter=$2,revision=revision+1,backed_up=$3 WHERE credential_id=$1 AND revision=$4 AND counter=$5 AND revoked_at IS NULL RETURNING credential_id`, [credential.credential_id, newCounter, credentialBackedUp, credential.revision, credential.counter]);
        if (!updated.rowCount) return null;
        return createSession(client, config, device);
      });
      if (!result) throw unauthorized();
      return result;
    },
  };
}
