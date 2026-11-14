CREATE TABLE transak_tokens (
 environment text PRIMARY KEY CHECK(environment IN ('STAGING','PRODUCTION')),
 api_key_hash text NOT NULL CHECK(api_key_hash ~ '^[0-9a-f]{64}$'),
 access_token text NOT NULL,
 expires_at bigint NOT NULL
);
REVOKE ALL ON transak_tokens FROM PUBLIC;
ALTER TABLE fiat_orders ALTER COLUMN fiat_amount TYPE numeric(20,4);
ALTER TABLE fiat_orders ADD COLUMN environment text NOT NULL DEFAULT 'STAGING' CHECK(environment IN ('STAGING','PRODUCTION'));
