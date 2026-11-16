CREATE TABLE receipt_shares (
  id uuid PRIMARY KEY,
  wallet_id uuid NOT NULL REFERENCES wallets(id),
  account_id uuid NOT NULL REFERENCES chain_accounts(id),
  source text NOT NULL CHECK (source IN ('payment','ramp')),
  reference text NOT NULL CHECK (length(reference) BETWEEN 1 AND 80),
  token_hash text NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
  payload jsonb NOT NULL CHECK (jsonb_typeof(payload)='object' AND octet_length(payload::text)<=16384),
  payload_hash text NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX receipt_shares_wallet_idx ON receipt_shares(wallet_id,created_at);
-- Published snapshots do not change when an operation progresses or reorganizes.
CREATE FUNCTION receipt_snapshot_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'A published receipt cannot be changed' USING ERRCODE='23514'; END $$;
CREATE TRIGGER receipt_shares_immutable BEFORE UPDATE ON receipt_shares FOR EACH ROW EXECUTE FUNCTION receipt_snapshot_immutable();
