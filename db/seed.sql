-- Sample data for local exploration after hydration. Idempotent.
INSERT INTO watchlist_entry (customer_reference) VALUES
    ('C-SANCTIONED-DEMO')
ON CONFLICT (customer_reference) DO NOTHING;

INSERT INTO kyc_assessment (assessment_id, customer_reference, account_ref, country_code,
                            documents, status, reasons, manually_decided, callback_url,
                            assessed_at, decided_at)
VALUES
    ('KYC-SEED-0001', 'C-1001', 'CA-SEED-0001', 'IN', 'ID,ADDRESS', 'APPROVED', 'CLEAN',
        FALSE, NULL, now() - interval '2 days', NULL),
    ('KYC-SEED-0002', 'C-EDD-1', 'CA-SEED-0002', 'IR', 'ID,ADDRESS', 'APPROVED',
        'HIGH_RISK_COUNTRY:IR,ANALYST:EDD completed', TRUE, NULL,
        now() - interval '1 day', now() - interval '20 hours'),
    ('KYC-SEED-0003', 'C-SANCTIONED-DEMO', NULL, 'IN', 'ID,ADDRESS', 'REJECTED', 'WATCHLIST_HIT',
        FALSE, NULL, now() - interval '3 hours', NULL)
ON CONFLICT (assessment_id) DO NOTHING;
