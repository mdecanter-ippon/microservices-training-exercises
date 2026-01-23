-- Demo users for distributed tracing tests
-- These UUIDs are referenced in Bruno requests

INSERT INTO users (id, email, first_name, last_name, role, status, created_at, updated_at)
SELECT '11111111-1111-1111-1111-111111111111', 'alice@dornach.com', 'Alice', 'Martin', 'EMPLOYEE', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = '11111111-1111-1111-1111-111111111111');

INSERT INTO users (id, email, first_name, last_name, role, status, created_at, updated_at)
SELECT '22222222-2222-2222-2222-222222222222', 'bob@dornach.com', 'Bob', 'Smith', 'ADMIN', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = '22222222-2222-2222-2222-222222222222');

INSERT INTO users (id, email, first_name, last_name, role, status, created_at, updated_at)
SELECT '33333333-3333-3333-3333-333333333333', 'charlie@dornach.com', 'Charlie', 'Brown', 'MANAGER', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = '33333333-3333-3333-3333-333333333333');
