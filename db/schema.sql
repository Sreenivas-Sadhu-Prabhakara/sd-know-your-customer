-- Know Your Customer service domain — Postgres schema.
-- READY TO HYDRATE, NOT YET WIRED (see platform-infra/postgres/hydrate.sh).

CREATE TABLE IF NOT EXISTS kyc_assessment (
    assessment_id     VARCHAR(44)  PRIMARY KEY,
    customer_reference VARCHAR(64) NOT NULL,
    account_ref       VARCHAR(40),
    country_code      CHAR(2),
    documents         TEXT         NOT NULL DEFAULT '',   -- comma-joined document types
    status            VARCHAR(10)  NOT NULL CHECK (status IN ('APPROVED','REJECTED','REFERRED')),
    reasons           TEXT         NOT NULL,
    manually_decided  BOOLEAN      NOT NULL DEFAULT FALSE,
    callback_url      VARCHAR(400),
    assessed_at       TIMESTAMPTZ  NOT NULL,
    decided_at        TIMESTAMPTZ,
    -- a manual decision must record when it happened:
    CONSTRAINT manual_has_timestamp CHECK (NOT manually_decided OR decided_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_kyc_customer ON kyc_assessment (customer_reference);
CREATE INDEX IF NOT EXISTS idx_kyc_status   ON kyc_assessment (status);

CREATE TABLE IF NOT EXISTS watchlist_entry (
    customer_reference VARCHAR(64) PRIMARY KEY,
    added_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
