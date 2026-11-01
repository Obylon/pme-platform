CREATE TABLE notification_preference (
    user_id    VARCHAR(64) PRIMARY KEY,
    enabled    BOOLEAN     NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE preference_category (
    user_id  VARCHAR(64) NOT NULL REFERENCES notification_preference (user_id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, category)
);

CREATE TABLE preference_keyword (
    user_id VARCHAR(64) NOT NULL REFERENCES notification_preference (user_id) ON DELETE CASCADE,
    keyword VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, keyword)
);

CREATE TABLE notification (
    id         UUID PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL,
    listing_id UUID         NOT NULL,
    message    VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    read_flag  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_notification_user_listing UNIQUE (user_id, listing_id)
);

CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
CREATE INDEX idx_preference_enabled ON notification_preference (enabled);
