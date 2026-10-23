-- F-05: bảng users (docs/02-ky-thuat/database.md mục 3.1). Thay thế V1/V2 placeholder (decisions D-14).
CREATE TABLE users (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(100) NOT NULL,
    full_name        VARCHAR(100) NOT NULL,
    phone            VARCHAR(20),
    address          VARCHAR(500),
    role             VARCHAR(30)  NOT NULL DEFAULT 'CUSTOMER',
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    otp_code_hash    VARCHAR(100),
    otp_purpose      VARCHAR(30),
    otp_expires_at   TIMESTAMPTZ,
    otp_attempts     INT          NOT NULL DEFAULT 0,
    otp_last_sent_at TIMESTAMPTZ,
    token_version    INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'EMPLOYEE', 'ADMIN')),
    CONSTRAINT chk_users_otp_purpose CHECK (otp_purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);
