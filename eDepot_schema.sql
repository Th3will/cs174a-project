CREATE TABLE Manufacturer (
    mname VARCHAR2(20),
    PRIMARY KEY (mname)
);

CREATE TABLE Location (
    letter CHAR(1),
    num NUMBER(10),
    PRIMARY KEY (letter, num),

    -- letter for location is case insensitive, but standardize to uppercase
    CONSTRAINT chk_location_letter CHECK (REGEXP_LIKE(letter, '^[A-Z]$'))
);

CREATE TABLE Warehouse_Item (
    stock_num CHAR(7),
    mname VARCHAR2(20),
    model_num VARCHAR2(20),
    quantity NUMBER(10) DEFAULT 0,
    min_level NUMBER(10) NOT NULL,
    max_level NUMBER(10) NOT NULL,
    replenishment NUMBER(10) DEFAULT 0,
    loc_letter CHAR(1),
    loc_num NUMBER(10),
    
    PRIMARY KEY (stock_num),
    CONSTRAINT fk_wi_manufacturer FOREIGN KEY (mname) REFERENCES Manufacturer(mname),
    CONSTRAINT fk_wi_location FOREIGN KEY (loc_letter, loc_num) REFERENCES Location(letter, num),
    
    -- one product per location
    CONSTRAINT unq_location UNIQUE (loc_letter, loc_num),
    
    -- unique manufacturer/model combination
    CONSTRAINT unq_mname_model UNIQUE (mname, model_num),
    
    CONSTRAINT chk_wi_stock_num CHECK (REGEXP_LIKE(stock_num, '^[A-Z]{2}[0-9]{5}$')),
    CONSTRAINT chk_wi_qty CHECK (quantity >= 0),
    CONSTRAINT chk_wi_min CHECK (min_level >= 0),
    CONSTRAINT chk_wi_max CHECK (max_level >= 0),
    CONSTRAINT chk_wi_replenish CHECK (replenishment >= 0),
    CONSTRAINT chk_qty_limit CHECK (quantity <= max_level),
    CONSTRAINT chk_level_logic CHECK (max_level >= min_level)
);

CREATE TABLE Replenishment_Order (
    oid NUMBER(10),
    mname VARCHAR2(20),

    PRIMARY KEY (oid),
    CONSTRAINT fk_ro_manufacturer FOREIGN KEY (mname) REFERENCES Manufacturer(mname)
);

CREATE TABLE Shipping_Notice (
    snid NUMBER(10),
    shipping_company_name VARCHAR2(40),

    PRIMARY KEY (snid)
);

CREATE TABLE Replenishment_Line (
    oid NUMBER(10),
    stock_num CHAR(7),
    replenishment_quantity NUMBER(10) NOT NULL,
    
    PRIMARY KEY (oid, stock_num),
    CONSTRAINT fk_rl_order FOREIGN KEY (oid) REFERENCES Replenishment_Order(oid),
    CONSTRAINT fk_rl_item FOREIGN KEY (stock_num) REFERENCES Warehouse_Item(stock_num),
    
    CONSTRAINT chk_rl_qty CHECK (replenishment_quantity >= 0)
);

CREATE TABLE Notice_Line (
    snid NUMBER(10),
    stock_num CHAR(7),
    notice_quantity NUMBER(10) NOT NULL,
    
    PRIMARY KEY (snid, stock_num),
    CONSTRAINT fk_nl_notice FOREIGN KEY (snid) REFERENCES Shipping_Notice(snid),
    CONSTRAINT fk_nl_item FOREIGN KEY (stock_num) REFERENCES Warehouse_Item(stock_num),
    
    CONSTRAINT chk_nl_qty CHECK (notice_quantity >= 0)
);
