CREATE TABLE exchange_quotes (
 id uuid PRIMARY KEY,
 wallet_id uuid NOT NULL REFERENCES wallets(id),
 account_id uuid NOT NULL REFERENCES chain_accounts(id),
 quote jsonb NOT NULL,
 expires_at timestamptz NOT NULL DEFAULT clock_timestamp()+interval '60 seconds'
);
CREATE INDEX exchange_quotes_owner_idx ON exchange_quotes(wallet_id,expires_at);
CREATE TABLE exchange_orders (
 id uuid PRIMARY KEY,
 wallet_id uuid NOT NULL REFERENCES wallets(id),
 account_id uuid NOT NULL REFERENCES chain_accounts(id),
 idempotency_key uuid NOT NULL,
 quote_id uuid NOT NULL UNIQUE REFERENCES exchange_quotes(id),
 payout_address text NOT NULL,
 payout_extra_id text,
 status text NOT NULL DEFAULT 'creating' CHECK(status IN ('creating','uncertain','waiting','confirming','exchanging','sending','finished','failed','refunded','hold','expired','overdue')),
 provider_id text UNIQUE,
 payin_address text,
 payment_intent_id uuid UNIQUE REFERENCES tx_intents(id),
 result jsonb,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(wallet_id,idempotency_key)
);
CREATE INDEX exchange_orders_owner_idx ON exchange_orders(wallet_id,account_id,created_at DESC);
REVOKE ALL ON exchange_quotes,exchange_orders FROM PUBLIC;
