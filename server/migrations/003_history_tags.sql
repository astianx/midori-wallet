CREATE TABLE chain_events (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  chain_id integer NOT NULL,
  contract text NOT NULL,
  transaction_hash text NOT NULL CHECK (transaction_hash ~ '^0x[0-9a-f]{64}$'),
  log_index integer NOT NULL CHECK (log_index >= 0),
  block_number bigint NOT NULL CHECK (block_number >= 0),
  block_hash text NOT NULL CHECK (block_hash ~ '^0x[0-9a-f]{64}$'),
  sender text NOT NULL CHECK (sender ~ '^0x[0-9a-f]{40}$'),
  recipient text NOT NULL CHECK (recipient ~ '^0x[0-9a-f]{40}$'),
  amount_atomic uint256 NOT NULL,
  occurred_at timestamptz NOT NULL,
  canonical boolean NOT NULL DEFAULT true,
  receipt jsonb NOT NULL,
  FOREIGN KEY (chain_id,contract) REFERENCES asset_registry(chain_id,contract),
  UNIQUE (chain_id,transaction_hash,log_index)
);
CREATE INDEX chain_events_order_idx ON chain_events(block_number DESC,log_index DESC,id DESC);
CREATE INDEX chain_events_contract_idx ON chain_events(chain_id,contract,block_number);
CREATE TABLE event_participants (
  account_id uuid NOT NULL REFERENCES chain_accounts(id),
  event_id bigint NOT NULL REFERENCES chain_events(id),
  incoming boolean NOT NULL,
  outgoing boolean NOT NULL,
  PRIMARY KEY (account_id,event_id),
  CHECK (incoming OR outgoing)
);
CREATE INDEX event_participants_event_idx ON event_participants(event_id);
CREATE TABLE index_cursors (
  account_id uuid PRIMARY KEY REFERENCES chain_accounts(id),
  next_block bigint NOT NULL CHECK (next_block >= 0),
  backfill_next bigint NOT NULL CHECK (backfill_next >= 0),
  backfill_end bigint NOT NULL CHECK (backfill_end >= 0),
  last_head bigint NOT NULL CHECK (last_head >= 0),
  revision bigint NOT NULL DEFAULT 1,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE index_checkpoints (
  account_id uuid NOT NULL REFERENCES chain_accounts(id),
  block_number bigint NOT NULL CHECK (block_number >= 0),
  block_hash text NOT NULL CHECK (block_hash ~ '^0x[0-9a-f]{64}$'),
  PRIMARY KEY (account_id,block_number)
);
CREATE TABLE wallet_tags (
  alias text PRIMARY KEY CHECK (alias ~ '^[a-z0-9][a-z0-9-]{0,62}$'),
  wallet_id uuid NOT NULL UNIQUE REFERENCES wallets(id),
  account_id uuid NOT NULL REFERENCES chain_accounts(id),
  revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
  active boolean NOT NULL DEFAULT true,
  updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX wallet_tags_account_idx ON wallet_tags(account_id);
CREATE TABLE tag_reservations (
  alias text PRIMARY KEY CHECK (alias ~ '^[a-z0-9][a-z0-9-]{0,62}$'),
  wallet_id uuid NOT NULL REFERENCES wallets(id),
  reserved_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX tag_reservations_wallet_idx ON tag_reservations(wallet_id);
CREATE TABLE tag_challenges (
  id uuid PRIMARY KEY,
  wallet_id uuid NOT NULL REFERENCES wallets(id),
  account_id uuid NOT NULL REFERENCES chain_accounts(id),
  alias text NOT NULL,
  message text NOT NULL,
  expires_at timestamptz NOT NULL,
  consumed_at timestamptz
);
CREATE INDEX tag_challenges_expiry_idx ON tag_challenges(expires_at);
CREATE TABLE tag_resolutions (
  id uuid PRIMARY KEY,
  wallet_id uuid NOT NULL REFERENCES wallets(id),
  alias text NOT NULL,
  revision bigint NOT NULL,
  chain_id integer NOT NULL CHECK (chain_id=8453),
  address text NOT NULL CHECK (address ~ '^0x[0-9a-f]{40}$'),
  expires_at timestamptz NOT NULL
);
CREATE INDEX tag_resolutions_expiry_idx ON tag_resolutions(expires_at);
CREATE INDEX tag_resolutions_wallet_idx ON tag_resolutions(wallet_id);
