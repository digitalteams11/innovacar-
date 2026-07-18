CREATE TABLE IF NOT EXISTS contact_requests (
    id BIGSERIAL PRIMARY KEY,
    request_number VARCHAR(20) UNIQUE,
    subject VARCHAR(255) NOT NULL,
    message VARCHAR(5000) NOT NULL,
    category VARCHAR(30),
    requester_name VARCHAR(255),
    requester_email VARCHAR(255) NOT NULL,
    requester_phone VARCHAR(50),
    destination_email VARCHAR(255),
    email_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    converted_ticket_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_contact_requests_status ON contact_requests(status);
CREATE INDEX IF NOT EXISTS idx_contact_requests_created_at ON contact_requests(created_at);
