CREATE TABLE spark_accounts (
 wallet_id uuid NOT NULL REFERENCES wallets(id),
 account_id uuid PRIMARY KEY REFERENCES chain_accounts(id),
 address text NOT NULL CHECK(address ~ '^spark1[ac-hj-np-z02-9]+$'),
 deposit_address text NOT NULL CHECK(length(deposit_address) BETWEEN 14 AND 100),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
REVOKE ALL ON spark_accounts FROM PUBLIC;
ALTER TABLE exchange_orders ADD COLUMN spark_deposit_id uuid;
