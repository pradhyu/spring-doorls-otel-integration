CREATE TABLE IF NOT EXISTS member_tier (
    member_id VARCHAR(50) PRIMARY KEY,
    membership_tier VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS tier_discount (
    membership_tier VARCHAR(50) PRIMARY KEY,
    discount_percentage DOUBLE NOT NULL
);
