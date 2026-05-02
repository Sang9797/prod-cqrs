-- Seeded automatically by Spring Boot for H2 (test profile).
-- BCrypt of 'testpass' for testuser and admin.
INSERT INTO permissions (permission_id, name) VALUES
    ('perm-inv-read',   'INVENTORY_READ'),
    ('perm-inv-write',  'INVENTORY_WRITE'),
    ('perm-inv-price',  'INVENTORY_PRICE'),
    ('perm-ord-read',   'ORDER_READ'),
    ('perm-ord-write',  'ORDER_WRITE');

INSERT INTO roles (role_id, name) VALUES
    ('role-admin', 'ROLE_ADMIN'),
    ('role-user',  'ROLE_USER');

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('role-admin', 'perm-inv-read'),  ('role-admin', 'perm-inv-write'),
    ('role-admin', 'perm-inv-price'), ('role-admin', 'perm-ord-read'),
    ('role-admin', 'perm-ord-write'),
    ('role-user',  'perm-inv-read'),  ('role-user',  'perm-inv-write'),
    ('role-user',  'perm-ord-read'),  ('role-user',  'perm-ord-write');

INSERT INTO users (user_id, username, password_hash, email, enabled) VALUES
    ('user-admin', 'admin',    '$2b$12$GdvgXiTXY2S5CuJJ76MQkuMsttHx147WwVvGAy2LqtUI3Z3/U4nPu', 'admin@test.com', true),
    ('user-test',  'testuser', '$2b$12$xb2Qyrjb94yO8hNDOKk6EOn07IDtrL0pPzJ/pRubymt2In9WDlp5C', 'test@test.com',  true);

INSERT INTO user_roles (user_id, role_id) VALUES
    ('user-admin', 'role-admin'),
    ('user-test',  'role-user');
