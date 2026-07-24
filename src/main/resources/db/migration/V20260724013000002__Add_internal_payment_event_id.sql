DO $$
BEGIN
    IF upper(current_schema()) LIKE '%\_ADMIN' ESCAPE '\'
       AND to_regclass('payment_event_inbox') IS NOT NULL THEN
        ALTER TABLE payment_event_inbox
            ADD COLUMN IF NOT EXISTS id uuid DEFAULT gen_random_uuid();

        UPDATE payment_event_inbox SET id = gen_random_uuid() WHERE id IS NULL;

        ALTER TABLE payment_event_inbox
            ALTER COLUMN id SET NOT NULL,
            ALTER COLUMN id DROP DEFAULT;

        IF EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'payment_event_inbox'::regclass
              AND contype = 'p'
              AND conname = 'payment_event_inbox_pkey'
        ) THEN
            ALTER TABLE payment_event_inbox DROP CONSTRAINT payment_event_inbox_pkey;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'payment_event_inbox'::regclass
              AND contype = 'p'
        ) THEN
            ALTER TABLE payment_event_inbox
                ADD CONSTRAINT payment_event_inbox_pkey PRIMARY KEY (id);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'payment_event_inbox'::regclass
              AND conname = 'uk_payment_event_inbox_payment_id'
        ) THEN
            ALTER TABLE payment_event_inbox
                ADD CONSTRAINT uk_payment_event_inbox_payment_id UNIQUE (payment_id);
        END IF;
    END IF;
END $$;
