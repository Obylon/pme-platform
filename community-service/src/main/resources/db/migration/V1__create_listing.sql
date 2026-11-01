CREATE TABLE listing (
    id                UUID PRIMARY KEY,
    title             VARCHAR(120)  NOT NULL,
    description       VARCHAR(2000),
    category          VARCHAR(20)   NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    owner_id          VARCHAR(64)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    moderation_reason VARCHAR(500),
    moderated_by      VARCHAR(64),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE listing_keyword (
    listing_id UUID        NOT NULL REFERENCES listing (id) ON DELETE CASCADE,
    keyword    VARCHAR(30) NOT NULL,
    PRIMARY KEY (listing_id, keyword)
);

CREATE INDEX idx_listing_status_category_created ON listing (status, category, created_at DESC);
CREATE INDEX idx_listing_owner ON listing (owner_id);
