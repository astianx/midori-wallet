CREATE TABLE bitcoin_accounts (
 wallet_id uuid NOT NULL REFERENCES wallets(id),
 account_id uuid PRIMARY KEY REFERENCES chain_accounts(id),
 address text NOT NULL CHECK(address ~ '^bc1q[ac-hj-np-z02-9]{38}$'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
REVOKE ALL ON bitcoin_accounts FROM PUBLIC;
ALTER TABLE exchange_orders ADD COLUMN bitcoin_deposit_hash text CHECK(bitcoin_deposit_hash ~ '^[a-f0-9]{64}$');
ALTER TABLE fiat_orders DROP CONSTRAINT fiat_orders_crypto_currency_check;
ALTER TABLE fiat_orders DROP CONSTRAINT fiat_orders_network_check;
ALTER TABLE fiat_orders DROP CONSTRAINT fiat_orders_wallet_address_check;
ALTER TABLE fiat_orders ADD CONSTRAINT fiat_orders_crypto_currency_check CHECK(crypto_currency IN ('USDC','USDT','BTC'));
ALTER TABLE fiat_orders ADD CONSTRAINT fiat_orders_network_check CHECK(network IN ('base','bitcoin'));
ALTER TABLE fiat_orders ADD CONSTRAINT fiat_orders_wallet_address_check CHECK(
 (network='base' AND crypto_currency IN ('USDC','USDT') AND wallet_address ~ '^0x[0-9a-f]{40}$') OR
 (network='bitcoin' AND crypto_currency='BTC' AND wallet_address ~ '^bc1q[ac-hj-np-z02-9]{38}$')
);
