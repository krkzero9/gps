CREATE TABLE payment_request (
                                 id                    UUID           PRIMARY KEY,
                                 idempotency_key       VARCHAR(64)    NOT NULL,
                                 request_hash          VARCHAR(64)    NOT NULL,
                                 recipient_name        VARCHAR(70)    NOT NULL,
                                 recipient_iban        VARCHAR(34)    NOT NULL,
                                 amount                NUMERIC(19,2)  NOT NULL,
                                 currency              VARCHAR(3)     NOT NULL,
                                 purpose               VARCHAR(140)   NOT NULL,
                                 status                VARCHAR(16)    NOT NULL,
                                 settlement_reference  VARCHAR(64),
                                 created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
                                 settled_at            TIMESTAMP WITH TIME ZONE,

                                 CONSTRAINT uq_payment_request_idempotency_key UNIQUE (idempotency_key),
                                 CONSTRAINT ck_payment_request_status  CHECK (status IN ('OPEN', 'SETTLED')),
                                 CONSTRAINT ck_payment_request_amount  CHECK (amount > 0),
                                 CONSTRAINT ck_payment_request_settled CHECK (
                                     (status = 'OPEN'    AND settled_at IS NULL     AND settlement_reference IS NULL) OR
                                     (status = 'SETTLED' AND settled_at IS NOT NULL AND settlement_reference IS NOT NULL))
);