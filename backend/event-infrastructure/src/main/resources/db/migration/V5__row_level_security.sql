-- V5__row_level_security.sql
-- Enable row-level security on tenant-sensitive tables

-- Orders table: enforce customer_id isolation
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

CREATE POLICY order_tenant_isolation ON orders
    FOR ALL
    TO application_user
    USING (customer_id = current_setting('app.current_tenant_id', true));

-- Payments table: enforce tenant isolation via order ownership
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;

CREATE POLICY payment_tenant_isolation ON payments
    FOR ALL
    TO application_user
    USING (order_id IN (SELECT id FROM orders WHERE customer_id = current_setting('app.current_tenant_id', true)));

-- Grant permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON orders TO application_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON payments TO application_user;
GRANT USAGE, SELECT ON SEQUENCE orders_id_seq TO application_user;
GRANT USAGE, SELECT ON SEQUENCE payments_id_seq TO application_user;
