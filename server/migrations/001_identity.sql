CREATE DOMAIN uint256 AS numeric
  CHECK (VALUE >= 0 AND VALUE < 115792089237316195423570985008687907853269984665640564039457584007913129639936 AND VALUE = trunc(VALUE));

CREATE TABLE wallets (
  id uuid PRIMARY KEY,
  public_key text NOT NULL UNIQUE CHECK (public_key ~ '^[A-Za-z0-9_-]{43}$'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE devices (
  id uuid PRIMARY KEY,
  wallet_id uuid NOT NULL REFERENCES wallets(id),
  public_key text NOT NULL UNIQUE CHECK (public_key ~ '^[A-Za-z0-9_-]{43}$'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  revoked_at timestamptz,
  UNIQUE (id, wallet_id)
);
CREATE INDEX devices_wallet_idx ON devices(wallet_id);
CREATE TABLE chain_accounts (
  id uuid PRIMARY KEY,
  wallet_id uuid NOT NULL REFERENCES wallets(id),
  chain_id integer NOT NULL CHECK (chain_id = 8453),
  address text NOT NULL CHECK (address ~ '^0x[0-9a-f]{40}$' AND address <> '0x0000000000000000000000000000000000000000'),
  owner_address text NOT NULL CHECK (owner_address ~ '^0x[0-9a-f]{40}$'),
  account_index integer NOT NULL CHECK (account_index >= 0),
  descriptor jsonb NOT NULL CHECK (
    jsonb_typeof(descriptor) = 'object' AND descriptor ?& ARRAY['schema','version','chainId','address','ownerAddress','index','accountType','threshold'] AND descriptor->>'schema' = 'midori.safe-account'
    AND descriptor->>'version' = '1' AND descriptor->>'chainId' = '8453'
    AND lower(descriptor->>'address') = address AND lower(descriptor->>'ownerAddress') = owner_address
    AND descriptor->>'index' = account_index::text AND descriptor->>'accountType' = 'safe-erc4337'
    AND descriptor->>'threshold' = '1'
  ),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (chain_id, address),
  UNIQUE (wallet_id, chain_id, account_index),
  UNIQUE (id, chain_id)
);
CREATE TABLE asset_registry (
  chain_id integer NOT NULL CHECK (chain_id = 8453),
  contract text NOT NULL CHECK (contract ~ '^0x[0-9a-f]{40}$'),
  symbol text NOT NULL CHECK (symbol IN ('USDT', 'USDC')),
  decimals smallint NOT NULL CHECK (decimals = 6),
  representation text NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  PRIMARY KEY (chain_id, contract)
);
INSERT INTO asset_registry(chain_id, contract, symbol, decimals, representation) VALUES
  (8453, '0x833589fcd6edb6e08f4c7c32d4f71b54bda02913', 'USDC', 6, 'Circle native'),
  (8453, '0xfde4c96c8593536e31f229ea8f37b2ada2699bb2', 'USDT', 6, 'bridged');
CREATE TABLE account_assets (
  account_id uuid NOT NULL,
  chain_id integer NOT NULL,
  contract text NOT NULL,
  balance_atomic uint256,
  observed_block numeric CHECK (observed_block >= 0 AND observed_block = trunc(observed_block)),
  PRIMARY KEY (account_id, chain_id, contract),
  FOREIGN KEY (account_id, chain_id) REFERENCES chain_accounts(id, chain_id),
  FOREIGN KEY (chain_id, contract) REFERENCES asset_registry(chain_id, contract),
  CHECK ((balance_atomic IS NULL) = (observed_block IS NULL))
);
CREATE INDEX account_assets_contract_idx ON account_assets(chain_id, contract);

CREATE TABLE auth_challenges (
  id uuid PRIMARY KEY,
  purpose text NOT NULL CHECK (purpose IN ('register', 'login', 'recover', 'webauthn-register', 'webauthn-login')),
  challenge_hash text NOT NULL CHECK (challenge_hash ~ '^[0-9a-f]{64}$'),
  context jsonb NOT NULL,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX auth_challenges_expiry_idx ON auth_challenges(expires_at);
CREATE TABLE sessions (
  id uuid PRIMARY KEY,
  wallet_id uuid NOT NULL,
  device_id uuid NOT NULL,
  token_hash text NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  FOREIGN KEY (device_id, wallet_id) REFERENCES devices(id, wallet_id)
);
CREATE INDEX sessions_device_idx ON sessions(device_id);
CREATE INDEX sessions_expiry_idx ON sessions(expires_at);
CREATE TABLE request_nonces (
  device_id uuid NOT NULL REFERENCES devices(id),
  nonce text NOT NULL CHECK (nonce ~ '^[A-Za-z0-9_-]{22,86}$'),
  fingerprint text NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (device_id, nonce)
);
CREATE INDEX request_nonces_expiry_idx ON request_nonces(expires_at);
CREATE TABLE auth_attempts (
  scope text PRIMARY KEY,
  attempts integer NOT NULL CHECK (attempts > 0),
  expires_at timestamptz NOT NULL
);
CREATE INDEX auth_attempts_expiry_idx ON auth_attempts(expires_at);
CREATE TABLE authenticators (
  credential_id text PRIMARY KEY CHECK (length(credential_id) BETWEEN 1 AND 2048),
  wallet_id uuid NOT NULL,
  device_id uuid NOT NULL,
  public_key bytea NOT NULL,
  counter bigint NOT NULL CHECK (counter BETWEEN 0 AND 4294967295),
  revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
  transports jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(transports) = 'array'),
  device_type text NOT NULL CHECK (device_type IN ('singleDevice', 'multiDevice')),
  backed_up boolean NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  FOREIGN KEY (device_id, wallet_id) REFERENCES devices(id, wallet_id)
);
CREATE INDEX authenticators_device_idx ON authenticators(device_id);
CREATE INDEX authenticators_wallet_idx ON authenticators(wallet_id);
CREATE TABLE encrypted_blobs (
  wallet_id uuid NOT NULL REFERENCES wallets(id),
  namespace text NOT NULL CHECK (namespace ~ '^[a-z][a-z0-9_-]{0,63}$'),
  ciphertext bytea,
  cipher_version integer NOT NULL CHECK (cipher_version = 1),
  revision bigint NOT NULL CHECK (revision > 0),
  deleted_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY (wallet_id, namespace),
  CHECK ((deleted_at IS NULL AND ciphertext IS NOT NULL AND octet_length(ciphertext) BETWEEN 28 AND 1048576) OR (deleted_at IS NOT NULL AND ciphertext IS NULL))
);
