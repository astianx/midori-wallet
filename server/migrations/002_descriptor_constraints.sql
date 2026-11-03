ALTER TABLE chain_accounts ADD CONSTRAINT chain_accounts_descriptor_fields CHECK (
  descriptor @> '{"schema":"midori.safe-account","version":1,"chainId":8453,"accountType":"safe-erc4337","threshold":1}'::jsonb
  AND descriptor->>'address' IS NOT NULL
  AND descriptor->>'ownerAddress' IS NOT NULL
  AND descriptor->>'index' IS NOT NULL
);
