CREATE TABLE Item (
    stock_num CHAR(7),
    category VARCHAR2(20),
    price NUMBER(10, 2) NOT NULL,
    warranty NUMBER(3) NOT NULL,
    model_num VARCHAR2(20),
    mname VARCHAR2(20),

PRIMARY KEY (stock_num),
CONSTRAINT fk_item_manufacturer FOREIGN KEY (mname) REFERENCES Manufacturer(mname),

    -- Constraint: XXnnnnn pattern
    CONSTRAINT chk_stock_num CHECK (REGEXP_LIKE(stock_num, '^[A-Z]{2}[0-9]{5}$')),

    -- Constraint: Non-negative price and warranty
    CONSTRAINT chk_item_price CHECK (price >= 0),
    CONSTRAINT chk_warranty CHECK (warranty >= 0)
);

CREATE TABLE Compatible_With (
    orig_stock_num CHAR(7),
    replacement_stock_num CHAR(7),
    PRIMARY KEY (orig_stock_num, replacement_stock_num),
    CONSTRAINT fk_comp_orig FOREIGN KEY (orig_stock_num) REFERENCES Item(stock_num),
    CONSTRAINT fk_comp_repl FOREIGN KEY (replacement_stock_num) REFERENCES Item(stock_num)
);


CREATE TABLE Item_Attribute (
    stock_num CHAR(7),
    attr_name VARCHAR2(20),
    attr_value NUMBER(10,2),
    attr_unit VARCHAR2(10),
    PRIMARY KEY (stock_num, attr_name),
    CONSTRAINT fk_attr_item FOREIGN KEY (stock_num) REFERENCES Item(stock_num) ON DELETE CASCADE
);

CREATE TABLE Order_Line (
    stock_num CHAR(7),
    ord_num NUMBER(10),
    order_price NUMBER(10, 2) NOT NULL,
    order_quantity NUMBER(5) NOT NULL,

    PRIMARY KEY (stock_num, ord_num),
    CONSTRAINT fk_ol_item FOREIGN KEY (stock_num) REFERENCES Item(stock_num),
    CONSTRAINT fk_ol_order FOREIGN KEY (ord_num) REFERENCES Order_Table(ord_num),

    -- Constraint: Non-negative price and quantity
    CONSTRAINT chk_order_line_price CHECK (order_price >= 0),
    CONSTRAINT chk_order_line_qty CHECK (order_quantity > 0)
);

CREATE TABLE Order_Table ( 
    ord_num NUMBER(10),
    order_date DATE DEFAULT SYSDATE,
    total NUMBER(10, 2) NOT NULL,
    shipping_fee NUMBER(10, 2),
    discount NUMBER(10, 2),
    cid VARCHAR2(20),

PRIMARY KEY (ord_num),
    CONSTRAINT fk_order_customer FOREIGN KEY (cid) REFERENCES Customer(cid)
);

CREATE TABLE Status (
    level_name VARCHAR2(20),
    threshold NUMBER(10, 2) NOT NULL,
    shipping_fee NUMBER(5, 2) NOT NULL,
    discount NUMBER(4, 2) NOT NULL,

PRIMARY KEY (level_name)
);

CREATE TABLE Customer (
    cid VARCHAR2(20),
first_name VARCHAR2(20),
middle_name VARCHAR2(20),
last_name VARCHAR2(20),
    password VARCHAR2(20) NOT NULL,
    email VARCHAR2(40),
    address VARCHAR2(100),
    level_name VARCHAR2(20),

PRIMARY KEY (cid),
    CONSTRAINT fk_customer_status FOREIGN KEY (level_name) REFERENCES Status(level_name)
);
