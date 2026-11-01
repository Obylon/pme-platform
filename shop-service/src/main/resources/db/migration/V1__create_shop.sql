CREATE TABLE product (
    id          UUID PRIMARY KEY,
    sku         VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    category    VARCHAR(30)  NOT NULL,
    price_cents BIGINT       NOT NULL CHECK (price_cents >= 0),
    stock       INTEGER      NOT NULL CHECK (stock >= 0),
    exclusive   BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_product_active_category ON product (active, category);

CREATE TABLE cart_item (
    id         UUID PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL,
    product_id UUID        NOT NULL REFERENCES product (id),
    quantity   INTEGER     NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT uk_cart_user_product UNIQUE (user_id, product_id)
);

CREATE TABLE customer_order (
    id                UUID PRIMARY KEY,
    user_id           VARCHAR(64) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    total_cents       BIGINT      NOT NULL,
    payment_reference VARCHAR(80) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_order_user_created ON customer_order (user_id, created_at DESC);

CREATE TABLE order_line (
    order_id         UUID         NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    product_id       UUID         NOT NULL,
    product_name     VARCHAR(150) NOT NULL,
    unit_price_cents BIGINT       NOT NULL,
    quantity         INTEGER      NOT NULL
);
CREATE INDEX idx_order_line_order ON order_line (order_id);

CREATE TABLE subscription (
    id         UUID PRIMARY KEY,
    user_id    VARCHAR(64) NOT NULL UNIQUE,
    plan       VARCHAR(10) NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0
);
