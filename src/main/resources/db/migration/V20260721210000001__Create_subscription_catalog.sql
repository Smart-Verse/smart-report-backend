CREATE TABLE IF NOT EXISTS subscription_plan (
    id uuid PRIMARY KEY,
    code varchar(40) NOT NULL UNIQUE,
    name varchar(80) NOT NULL,
    description varchar(255) NOT NULL,
    monthly_price numeric(12,2),
    api_monthly_limit integer,
    custom_plan boolean NOT NULL DEFAULT false,
    active boolean NOT NULL DEFAULT true,
    display_order integer NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tenant_subscription (
    id uuid PRIMARY KEY,
    tenant varchar(120) NOT NULL UNIQUE,
    plan_code varchar(40) NOT NULL,
    started_at timestamp NOT NULL
);

CREATE TABLE IF NOT EXISTS api_monthly_usage (
    id uuid PRIMARY KEY,
    tenant varchar(120) NOT NULL,
    period varchar(7) NOT NULL,
    amount integer NOT NULL DEFAULT 0,
    CONSTRAINT uk_api_monthly_usage UNIQUE (tenant, period)
);

INSERT INTO subscription_plan
(id, code, name, description, monthly_price, api_monthly_limit, custom_plan, active, display_order)
VALUES
('10000000-0000-0000-0000-000000000001', 'FREE', 'Free', 'Para começar e validar sua integração.', 0.00, 150, false, true, 1),
('10000000-0000-0000-0000-000000000002', 'STARTER', 'Starter', 'Mais capacidade para produtos em crescimento.', 19.99, 500, false, true, 2),
('10000000-0000-0000-0000-000000000003', 'PRO', 'Pro', 'Volume ampliado para operações recorrentes.', 39.99, 1200, false, true, 3),
('10000000-0000-0000-0000-000000000004', 'CUSTOM', 'Personalizado', 'Franquia e condições definidas conforme sua volumetria.', NULL, NULL, true, true, 4)
ON CONFLICT (code) DO NOTHING;
