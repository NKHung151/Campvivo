-- V7 (N4-06): chat hỗ trợ khách <-> nhân viên — docs/02-ky-thuat/database.md mục 3.19 (D-05), index mục 3.23.
-- user_id LUÔN là khách (chủ hội thoại), kể cả tin do nhân viên gửi (is_from_staff = true).
-- Bảng notifications (3.20) KHÔNG nằm trong file này: thuộc N4-03.

CREATE TABLE support_messages (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users (id),
    content       VARCHAR(2000) NOT NULL,
    is_from_staff BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_support_messages_content CHECK (length(btrim(content)) > 0)
);

CREATE INDEX idx_support_messages_user_id_created_at ON support_messages (user_id, created_at);
