CREATE TABLE changelly_fiat_quotes (
 id uuid PRIMARY KEY,
 wallet_id uuid NOT NULL REFERENCES wallets(id),
 account_id uuid NOT NULL REFERENCES chain_accounts(id),
 quote jsonb NOT NULL,
 expires_at timestamptz NOT NULL DEFAULT clock_timestamp() + interval '90 seconds'
);
CREATE TABLE changelly_fiat_orders (
 id uuid PRIMARY KEY,
 wallet_id uuid NOT NULL REFERENCES wallets(id),
 account_id uuid NOT NULL REFERENCES chain_accounts(id),
 quote_id uuid NOT NULL REFERENCES changelly_fiat_quotes(id),
 idempotency_key uuid NOT NULL,
 environment text NOT NULL CHECK(environment IN ('STAGING','PRODUCTION')),
 status text NOT NULL DEFAULT 'creating' CHECK(status IN ('creating','ambiguous','created','awaiting_payment','processing','completed','failed','refunded','expired')),
 provider_id text,
 checkout_url text,
 result jsonb,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(wallet_id,idempotency_key)
);
CREATE INDEX changelly_fiat_orders_pending ON changelly_fiat_orders(updated_at) WHERE status IN ('creating','ambiguous','created','awaiting_payment','processing');
CREATE UNIQUE INDEX changelly_fiat_orders_uncertain ON changelly_fiat_orders(wallet_id,account_id) WHERE status IN ('creating','ambiguous');
REVOKE ALL ON changelly_fiat_quotes,changelly_fiat_orders FROM PUBLIC;
