CREATE TABLE contact_messages
(
    contact_messages_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    uuid                UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                VARCHAR(150) NOT NULL,
    email               VARCHAR(254) NOT NULL,
    phone               VARCHAR(30),
    subject             VARCHAR(200) NOT NULL,
    message             TEXT         NOT NULL,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    status              VARCHAR(50)  NOT NULL DEFAULT 'new'
                            CHECK (status IN ('new', 'read', 'archived')),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID
);

CREATE INDEX idx_contact_messages_is_active ON contact_messages (is_active) WHERE is_active;
CREATE INDEX idx_contact_messages_status    ON contact_messages (status);

CREATE TRIGGER trg_contact_messages_updated_at
    BEFORE UPDATE ON contact_messages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
