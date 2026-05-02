-- ============================================================
-- V4 — Users, Roles, Permissions
-- ============================================================

CREATE TABLE IF NOT EXISTS permissions (
    permission_id   VARCHAR(36)   NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    description     VARCHAR(255),
    CONSTRAINT pk_permissions    PRIMARY KEY (permission_id),
    CONSTRAINT uq_permission_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS roles (
    role_id         VARCHAR(36)   NOT NULL,
    name            VARCHAR(50)   NOT NULL,
    description     VARCHAR(255),
    CONSTRAINT pk_roles     PRIMARY KEY (role_id),
    CONSTRAINT uq_role_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id         VARCHAR(36)   NOT NULL,
    permission_id   VARCHAR(36)   NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role          FOREIGN KEY (role_id)
        REFERENCES roles(role_id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission    FOREIGN KEY (permission_id)
        REFERENCES permissions(permission_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS users (
    user_id         VARCHAR(36)   NOT NULL,
    username        VARCHAR(100)  NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    email           VARCHAR(255),
    enabled         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_users      PRIMARY KEY (user_id),
    CONSTRAINT uq_username   UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id         VARCHAR(36)   NOT NULL,
    role_id         VARCHAR(36)   NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user    FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role    FOREIGN KEY (role_id)
        REFERENCES roles(role_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_users_username    ON users(username);
CREATE INDEX IF NOT EXISTS idx_user_roles_user   ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_role_perms_role   ON role_permissions(role_id);

-- ── Seed permissions ─────────────────────────────────────────────
INSERT INTO permissions (permission_id, name, description) VALUES
    ('perm-inventory-read',  'INVENTORY_READ',  'Read inventory data'),
    ('perm-inventory-write', 'INVENTORY_WRITE', 'Reserve / release / adjust inventory'),
    ('perm-inventory-price', 'INVENTORY_PRICE', 'View unit prices and financial fields'),
    ('perm-order-read',      'ORDER_READ',      'Read order data'),
    ('perm-order-write',     'ORDER_WRITE',     'Place, confirm and cancel orders');

-- ── Seed roles ───────────────────────────────────────────────────
INSERT INTO roles (role_id, name, description) VALUES
    ('role-admin', 'ROLE_ADMIN', 'Full access including financial fields'),
    ('role-user',  'ROLE_USER',  'Standard read/write access, no pricing data');

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('role-admin', 'perm-inventory-read'),
    ('role-admin', 'perm-inventory-write'),
    ('role-admin', 'perm-inventory-price'),
    ('role-admin', 'perm-order-read'),
    ('role-admin', 'perm-order-write'),
    ('role-user',  'perm-inventory-read'),
    ('role-user',  'perm-inventory-write'),
    ('role-user',  'perm-order-read'),
    ('role-user',  'perm-order-write');

-- ── Seed users ───────────────────────────────────────────────────
-- Passwords are BCrypt of 'adminpass' and 'userpass' respectively.
-- Override via the user management API in production.
INSERT INTO users (user_id, username, password_hash, email) VALUES
    ('user-admin', 'admin', '$2a$12$YSMoS5mHWNFMNJPAqxoAx.A6RMdl22b1FRn7yM02aSdN9iuXvRvEy', 'admin@example.com'),
    ('user-john',  'john',  '$2a$12$n4kYICpQdXmBG9yVRHgcluDyUf6W6KWPSyXm8VIUEj5I8dxPLT96u', 'john@example.com');

INSERT INTO user_roles (user_id, role_id) VALUES
    ('user-admin', 'role-admin'),
    ('user-john',  'role-user');
