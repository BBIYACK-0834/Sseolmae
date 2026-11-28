CREATE TABLE IF NOT EXISTS venue_user (
    id UUID PRIMARY KEY,
    provider VARCHAR(24) NOT NULL,
    subject VARCHAR(128) NOT NULL,
    UNIQUE (provider, subject)
);
CREATE TABLE IF NOT EXISTS local_credential (
    user_id UUID PRIMARY KEY REFERENCES venue_user(id),
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL
);
CREATE TABLE IF NOT EXISTS visit_slot (
    id UUID PRIMARY KEY,
    ticket_type VARCHAR(20) NOT NULL CHECK (ticket_type IN ('MEDIA_ART','SNOW_SLED','COMBO')),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    price INTEGER NOT NULL CHECK (price > 0),
    UNIQUE(ticket_type, starts_at)
);
CREATE TABLE IF NOT EXISTS booking (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES venue_user(id),
    slot_id UUID NOT NULL REFERENCES visit_slot(id),
    request_id UUID NOT NULL,
    adults INTEGER NOT NULL CHECK (adults >= 0),
    children INTEGER NOT NULL CHECK (children >= 0),
    amount INTEGER NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','PROCESSING','CONFIRMED','CANCELLED','EXPIRED')),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(user_id,request_id),
    CHECK(adults + children BETWEEN 1 AND 20)
);
CREATE INDEX IF NOT EXISTS booking_slot_status ON booking(slot_id, status, expires_at);
CREATE TABLE IF NOT EXISTS payment (
    booking_id UUID PRIMARY KEY REFERENCES booking(id),
    payment_key VARCHAR(200) NOT NULL UNIQUE,
    approved_at TIMESTAMP WITH TIME ZONE
);

ALTER TABLE payment ADD COLUMN IF NOT EXISTS next_check_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE visit_slot ADD COLUMN IF NOT EXISTS sale_open BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE visit_slot ADD COLUMN IF NOT EXISTS child_price INTEGER CHECK(child_price>0);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS admitted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE booking ADD COLUMN IF NOT EXISTS admitted_by UUID REFERENCES venue_user(id);
ALTER TABLE booking ADD COLUMN IF NOT EXISTS refund_requested BOOLEAN DEFAULT FALSE NOT NULL;
