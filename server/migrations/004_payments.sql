CREATE TABLE tx_intents (
 id uuid PRIMARY KEY,
 wallet_id uuid NOT NULL REFERENCES wallets(id),
 account_id uuid NOT NULL REFERENCES chain_accounts(id),
 idempotency_key uuid NOT NULL,
 fingerprint text NOT NULL CHECK(fingerprint ~ '^[0-9a-f]{64}$'),
 chain_id integer NOT NULL CHECK(chain_id=8453),
 contract text NOT NULL,
 amount_atomic uint256 NOT NULL CHECK(amount_atomic>0),
 sender text NOT NULL,
 recipient text NOT NULL CHECK(recipient ~ '^0x[0-9a-f]{40}$'),
 recipient_kind text NOT NULL CHECK(recipient_kind IN ('address','midori_tag')),
 resolution_id uuid REFERENCES tag_resolutions(id) ON DELETE SET NULL,
 recipient_tag text,
 tag_revision bigint,
 fee_mode text NOT NULL CHECK(fee_mode IN ('token','sponsored')),
 fee_token text,
 requested_fee_cap uint256 NOT NULL,
 quote jsonb,
 quote_digest text,
 status text NOT NULL DEFAULT 'preparing' CHECK(status IN ('preparing','quoted','authorized','submitted','ambiguous','included','confirmed','reverted','reorganized','expired','cancelled','rejected')),
 failure_code text,
 expires_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 FOREIGN KEY(chain_id,contract) REFERENCES asset_registry(chain_id,contract),
 FOREIGN KEY(chain_id,fee_token) REFERENCES asset_registry(chain_id,contract),
 UNIQUE(wallet_id,idempotency_key),
 CHECK((fee_mode='token' AND fee_token IS NOT NULL AND requested_fee_cap>0) OR (fee_mode='sponsored' AND fee_token IS NULL AND requested_fee_cap=0))
);
CREATE INDEX tx_intents_account_idx ON tx_intents(account_id,created_at DESC,id);
CREATE INDEX tx_intents_resolution_idx ON tx_intents(resolution_id);
CREATE INDEX tx_intents_wallet_idx ON tx_intents(wallet_id);
CREATE UNIQUE INDEX tx_intents_active_account_idx ON tx_intents(account_id) WHERE status IN ('preparing','quoted','authorized','submitted','ambiguous','included','reorganized');
CREATE TABLE user_operations (
 intent_id uuid PRIMARY KEY REFERENCES tx_intents(id),
 user_operation_hash text NOT NULL UNIQUE CHECK(user_operation_hash ~ '^0x[0-9a-f]{64}$'),
 operation jsonb NOT NULL,
 nonce uint256 NOT NULL,
 submitted_block bigint NOT NULL,
 attempted_at timestamptz,
 transaction_hash text,
 block_number bigint,
 block_hash text,
 success boolean,
 receipt jsonb,
 actual_fee_atomic uint256,
 actual_native_atomic uint256,
 payment_log_index integer,
 gas_log_indexes integer[] NOT NULL DEFAULT '{}',
 accounting_state text NOT NULL DEFAULT 'pending' CHECK(accounting_state IN ('pending','settled','review')),
 search_next_block bigint,
 search_checkpoint_block bigint,
 search_checkpoint_hash text,
 expiry_observed_block bigint,
 checked_at timestamptz
);
CREATE INDEX user_operations_transaction_idx ON user_operations(transaction_hash);
CREATE INDEX user_operations_check_idx ON user_operations(checked_at);
CREATE TABLE payment_outbox (
 intent_id uuid PRIMARY KEY REFERENCES user_operations(intent_id),
 lease_until timestamptz,
 completed_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX payment_outbox_pending_idx ON payment_outbox(created_at) WHERE completed_at IS NULL;
CREATE TABLE sponsor_budgets (
 day date PRIMARY KEY,
 limit_atomic uint256 NOT NULL,
 reserved_atomic uint256 NOT NULL DEFAULT 0,
 spent_atomic uint256 NOT NULL DEFAULT 0
);
CREATE TABLE sponsor_reservations (
 intent_id uuid PRIMARY KEY REFERENCES tx_intents(id),
 budget_day date NOT NULL REFERENCES sponsor_budgets(day),
 max_native_atomic uint256 NOT NULL,
 actual_native_atomic uint256,
 status text NOT NULL CHECK(status IN ('held','settled','released')),
 block_hash text,
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX sponsor_reservations_day_idx ON sponsor_reservations(budget_day);
ALTER TABLE chain_events ADD COLUMN intent_id uuid REFERENCES tx_intents(id);
ALTER TABLE chain_events ADD COLUMN event_kind text NOT NULL DEFAULT 'external' CHECK(event_kind IN ('external','payment','gas'));
CREATE INDEX chain_events_intent_idx ON chain_events(intent_id);
