ALTER TABLE evm_transfers ADD COLUMN account_address text;
UPDATE evm_transfers SET account_address=lower(payment->>'from');
ALTER TABLE evm_transfers ALTER COLUMN account_address SET NOT NULL;
ALTER TABLE evm_transfers ADD CHECK(account_address ~ '^0x[0-9a-f]{40}$');
ALTER TABLE evm_transfers DROP CONSTRAINT evm_transfers_pkey;
ALTER TABLE evm_transfers ADD PRIMARY KEY(account_id,chain_id,account_address,nonce);
ALTER TABLE evm_transfers DROP CONSTRAINT evm_transfers_raw_transaction_check;
ALTER TABLE evm_transfers ADD CHECK(length(raw_transaction) <= 16384);
