CREATE TABLE fiat_orders (
 id uuid PRIMARY KEY,
 wallet_id uuid NOT NULL REFERENCES wallets(id),
 account_id uuid NOT NULL REFERENCES chain_accounts(id),
 idempotency_key uuid NOT NULL,
 provider text NOT NULL DEFAULT 'transak' CHECK(provider='transak'),
 kind text NOT NULL CHECK(kind IN ('buy','sell')),
 status text NOT NULL DEFAULT 'created' CHECK(status IN ('created','awaiting_payment','processing','completed','failed','refunded','expired')),
 fiat_currency text NOT NULL CHECK(fiat_currency ~ '^[A-Z]{3}$'),
 crypto_currency text NOT NULL CHECK(crypto_currency IN ('USDC','USDT')),
 network text NOT NULL DEFAULT 'base' CHECK(network='base'),
 wallet_address text NOT NULL CHECK(wallet_address ~ '^0x[0-9a-f]{40}$'),
 fiat_amount numeric(18,2) CHECK(fiat_amount IS NULL OR fiat_amount>0),
 crypto_amount numeric(36,18) CHECK(crypto_amount IS NULL OR crypto_amount>0),
 payment_method text,
 provider_order_id text,
 transaction_hash text,
 failure_reason text,
 raw jsonb,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 updated_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 UNIQUE(wallet_id,idempotency_key),
 UNIQUE(provider,provider_order_id)
);
CREATE INDEX fiat_orders_account_idx ON fiat_orders(account_id,created_at DESC,id);
CREATE INDEX fiat_orders_wallet_idx ON fiat_orders(wallet_id);
CREATE INDEX fiat_orders_pending_idx ON fiat_orders(updated_at) WHERE status NOT IN ('completed','failed','refunded','expired');
