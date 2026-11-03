import { ApiError, exactKeys, notFound, requireValue } from './errors.js';
import { decodeBase64 } from './crypto.js';

export function validateNamespace(namespace) {
  requireValue(/^[a-z][a-z0-9_-]{0,63}$/.test(namespace), 'Invalid blob namespace');
}

function revision(value) {
  requireValue(typeof value === 'string' && /^(0|[1-9][0-9]{0,18})$/.test(value) && BigInt(value) < 9223372036854775807n, 'Expected revision must be an integer string');
  return value;
}

function present(row) {
  return { namespace: row.namespace, revision: row.revision, cipherVersion: row.cipher_version, ciphertext: row.ciphertext?.toString('base64url') ?? null, deleted: !!row.deleted_at, updatedAt: row.updated_at };
}

export async function readBlob(client, walletId, namespace) {
  validateNamespace(namespace);
  const { rows } = await client.query('SELECT * FROM encrypted_blobs WHERE wallet_id=$1 AND namespace=$2', [walletId, namespace]);
  if (!rows.length) throw notFound();
  return present(rows[0]);
}

export async function writeBlob(client, walletId, namespace, body, remove = false) {
  validateNamespace(namespace);
  exactKeys(body, remove ? ['expectedRevision'] : ['expectedRevision', 'cipherVersion', 'ciphertext']);
  revision(body.expectedRevision);
  if (!remove) requireValue(body.cipherVersion === 1, 'Unsupported cipher envelope version');
  const ciphertext = remove ? null : decodeBase64(body.ciphertext, 28, 1048576);
  const deleted = remove ? new Date() : null;
  let result;
  if (body.expectedRevision === '0') {
    requireValue(!remove, 'Cannot delete a missing blob');
    result = await client.query(`INSERT INTO encrypted_blobs(wallet_id,namespace,ciphertext,cipher_version,revision) VALUES ($1,$2,$3,1,1)
      ON CONFLICT DO NOTHING RETURNING *`, [walletId, namespace, ciphertext]);
  } else {
    result = await client.query(`UPDATE encrypted_blobs SET ciphertext=$3,cipher_version=1,revision=revision+1,deleted_at=$4,updated_at=clock_timestamp()
      WHERE wallet_id=$1 AND namespace=$2 AND revision=$5 RETURNING *`, [walletId, namespace, ciphertext, deleted, body.expectedRevision]);
  }
  if (!result.rowCount) {
    const current = await client.query('SELECT revision FROM encrypted_blobs WHERE wallet_id=$1 AND namespace=$2', [walletId, namespace]);
    throw new ApiError(409, 'REVISION_CONFLICT', 'Encrypted data changed; read the current revision before retrying', { currentRevision: current.rows[0]?.revision || '0' });
  }
  return present(result.rows[0]);
}
