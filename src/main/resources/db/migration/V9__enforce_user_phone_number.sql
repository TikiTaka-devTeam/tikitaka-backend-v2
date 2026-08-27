DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE phone_number IS NULL) THEN
        RAISE EXCEPTION 'Cannot enforce users.phone_number NOT NULL: NULL values exist';
    END IF;

    IF EXISTS (
        SELECT regexp_replace(phone_number, '[^0-9]', '', 'g')
        FROM users
        GROUP BY regexp_replace(phone_number, '[^0-9]', '', 'g')
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot normalize users.phone_number: duplicate normalized values exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM users
        WHERE regexp_replace(phone_number, '[^0-9]', '', 'g') !~ '^01[016789][0-9]{7,8}$'
    ) THEN
        RAISE EXCEPTION 'Cannot normalize users.phone_number: invalid phone numbers exist';
    END IF;
END
$$;

UPDATE users
SET phone_number = regexp_replace(phone_number, '[^0-9]', '', 'g')
WHERE phone_number <> regexp_replace(phone_number, '[^0-9]', '', 'g');

ALTER TABLE users
    ALTER COLUMN phone_number TYPE VARCHAR(11),
    ALTER COLUMN phone_number SET NOT NULL,
    ADD CONSTRAINT ck_users_phone_number_format
        CHECK (phone_number ~ '^01[016789][0-9]{7,8}$');
