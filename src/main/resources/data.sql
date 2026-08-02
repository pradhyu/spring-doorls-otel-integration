-- Clear existing data
DELETE FROM member_tier;
DELETE FROM tier_discount;

-- Insert reference data
INSERT INTO member_tier (member_id, membership_tier) VALUES ('M101', 'GOLD');
INSERT INTO member_tier (member_id, membership_tier) VALUES ('M102', 'PLATINUM');
INSERT INTO member_tier (member_id, membership_tier) VALUES ('M103', 'SILVER');
INSERT INTO member_tier (member_id, membership_tier) VALUES ('M104', 'REGULAR');

INSERT INTO tier_discount (membership_tier, discount_percentage) VALUES ('GOLD', 15.0);
INSERT INTO tier_discount (membership_tier, discount_percentage) VALUES ('PLATINUM', 20.0);
INSERT INTO tier_discount (membership_tier, discount_percentage) VALUES ('SILVER', 8.0);
INSERT INTO tier_discount (membership_tier, discount_percentage) VALUES ('REGULAR', 0.0);
