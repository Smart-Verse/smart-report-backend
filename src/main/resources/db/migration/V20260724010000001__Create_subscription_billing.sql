DO $$
BEGIN
    IF upper(current_schema()) LIKE '%\_ADMIN' ESCAPE '\' THEN
        ALTER TABLE tenant_subscription
            ADD COLUMN IF NOT EXISTS billing_cycle integer,
            ADD COLUMN IF NOT EXISTS expires_at timestamp;

        CREATE TABLE IF NOT EXISTS billing_discount (
            id uuid PRIMARY KEY,
            billing_cycle integer NOT NULL UNIQUE,
            months integer NOT NULL CHECK (months > 0),
            discount_percentage numeric(5,2) NOT NULL CHECK (discount_percentage BETWEEN 0 AND 100),
            active boolean NOT NULL DEFAULT true
        );

        CREATE TABLE IF NOT EXISTS subscription_payment (
            id uuid PRIMARY KEY,
            tenant varchar(120) NOT NULL,
            plan_code varchar(40) NOT NULL,
            billing_cycle integer NOT NULL,
            months integer NOT NULL,
            base_amount_cents integer NOT NULL,
            discount_percentage numeric(5,2) NOT NULL,
            amount_cents integer NOT NULL,
            status integer NOT NULL,
            order_nsu varchar(255),
            transaction_nsu varchar(255),
            created_at timestamp NOT NULL,
            updated_at timestamp NOT NULL,
            paid_at timestamp,
            coverage_start_at timestamp,
            coverage_end_at timestamp
        );

        CREATE INDEX IF NOT EXISTS idx_subscription_payment_tenant_created
            ON subscription_payment (tenant, created_at DESC);
        CREATE INDEX IF NOT EXISTS idx_subscription_payment_tenant_coverage
            ON subscription_payment (tenant, coverage_start_at, coverage_end_at);

        CREATE TABLE IF NOT EXISTS payment_event_inbox (
            payment_id uuid PRIMARY KEY,
            client_id uuid NOT NULL,
            order_nsu varchar(255) NOT NULL,
            transaction_nsu varchar(255) NOT NULL,
            status varchar(30) NOT NULL,
            amount integer NOT NULL,
            paid_amount integer NOT NULL,
            paid_at timestamp NOT NULL,
            failure_reason varchar(1000),
            received_at timestamp NOT NULL,
            processed_at timestamp
        );

        INSERT INTO billing_discount (id, billing_cycle, months, discount_percentage, active)
        VALUES
            ('20000000-0000-0000-0000-000000000001', 0, 1, 0.00, true),
            ('20000000-0000-0000-0000-000000000002', 1, 3, 10.00, true),
            ('20000000-0000-0000-0000-000000000003', 2, 6, 15.00, true)
        ON CONFLICT (billing_cycle) DO UPDATE SET
            months = EXCLUDED.months,
            discount_percentage = EXCLUDED.discount_percentage,
            active = EXCLUDED.active;
    END IF;
END $$;
