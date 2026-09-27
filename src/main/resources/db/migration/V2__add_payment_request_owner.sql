-- Each payment request belongs to the client (the company's system) that created it.
-- Idempotency-Keys become unique per client: two companies may use the same key independently.
-- NOT NULL works here because the table is still empty (not released yet). On a table with data:
-- add the column nullable, backfill it, then set NOT NULL.
ALTER TABLE payment_request ADD COLUMN owner_client_id VARCHAR(64) NOT NULL;

ALTER TABLE payment_request DROP CONSTRAINT uq_payment_request_idempotency_key;
ALTER TABLE payment_request
    ADD CONSTRAINT uq_payment_request_owner_idempotency_key UNIQUE (owner_client_id, idempotency_key);