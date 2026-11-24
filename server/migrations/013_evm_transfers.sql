CREATE TABLE evm_transfers (
  account_id uuid NOT NULL REFERENCES chain_accounts(id),
  network text NOT NULL,
  chain_id integer NOT NULL CHECK(chain_id IN (1,137,42161,43114)),
  nonce bigint NOT NULL CHECK(nonce >= 0),
  hash text NOT NULL CHECK(hash ~ '^0x[0-9a-f]{64}$'),
  contract text NOT NULL CHECK(contract ~ '^0x[0-9a-f]{40}$'),
  raw_transaction text NOT NULL CHECK(length(raw_transaction) <= 4096),
  payment jsonb NOT NULL,
  status text NOT NULL CHECK(status IN ('ambiguous','submitted','pending','confirmed','reverted')),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  PRIMARY KEY(account_id,chain_id,nonce),
  UNIQUE(chain_id,hash)
);
CREATE INDEX evm_transfers_pending ON evm_transfers(account_id,network) WHERE status NOT IN ('confirmed','reverted');
REVOKE ALL ON evm_transfers FROM PUBLIC;
