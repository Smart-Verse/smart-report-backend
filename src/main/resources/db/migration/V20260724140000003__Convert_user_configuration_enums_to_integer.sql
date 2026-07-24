DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = current_schema()
           AND table_name = 'user_configuration'
           AND column_name = 'theme'
           AND data_type NOT IN ('integer', 'smallint', 'bigint')
    ) THEN
        ALTER TABLE user_configuration
            ALTER COLUMN theme TYPE integer
            USING CASE upper(theme::text)
                WHEN 'DARK' THEN 0
                WHEN 'LIGHT' THEN 1
                ELSE theme::integer
            END;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = current_schema()
           AND table_name = 'user_configuration'
           AND column_name = 'lang'
           AND data_type NOT IN ('integer', 'smallint', 'bigint')
    ) THEN
        ALTER TABLE user_configuration
            ALTER COLUMN lang TYPE integer
            USING CASE upper(lang::text)
                WHEN 'PORTUGUESE' THEN 0
                WHEN 'ENGLISH' THEN 1
                WHEN 'SPANISH' THEN 2
                ELSE lang::integer
            END;
    END IF;
END $$;
