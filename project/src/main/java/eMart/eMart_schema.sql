create table item (
   stock_num char(7),
   category  varchar2(20),
   price     number(10,2) not null,
   warranty  number(3) not null,
   model_num varchar2(20),
   mname     varchar2(20),
   primary key ( stock_num ),
   constraint fk_item_manufacturer foreign key ( mname )
      references manufacturer ( mname ),

    -- Constraint: XXnnnnn pattern
   constraint chk_stock_num check ( regexp_like ( stock_num,
                                                  '^[A-Z]{2}[0-9]{5}$' ) ),

    -- Constraint: Non-negative price and warranty
   constraint chk_item_price check ( price >= 0 ),
   constraint chk_warranty check ( warranty >= 0 )
);

create table compatible_with (
   orig_stock_num        char(7),
   replacement_stock_num char(7),
   primary key ( orig_stock_num,
                 replacement_stock_num ),
   constraint fk_comp_orig foreign key ( orig_stock_num )
      references item ( stock_num ),
   constraint fk_comp_repl foreign key ( replacement_stock_num )
      references item ( stock_num )
);


create table item_attribute (
   stock_num  char(7),
   attr_name  varchar2(20),
   attr_value number(10,2),
   attr_unit  varchar2(10),
   primary key ( stock_num,
                 attr_name ),
   constraint fk_attr_item foreign key ( stock_num )
      references item ( stock_num )
         on delete cascade
);

create table order_line (
   stock_num      char(7),
   ord_num        number(10),
   order_price    number(10,2) not null,
   order_quantity number(5) not null,
   primary key ( stock_num,
                 ord_num ),
   constraint fk_ol_item foreign key ( stock_num )
      references item ( stock_num ),
   constraint fk_ol_order foreign key ( ord_num )
      references order_table ( ord_num ),

    -- Constraint: Non-negative price and quantity
   constraint chk_order_line_price check ( order_price >= 0 ),
   constraint chk_order_line_qty check ( order_quantity > 0 )
);

create table order_table (
   ord_num      number(10),
   order_date   date default sysdate,
   total        number(10,2) not null,
   shipping_fee number(10,2),
   discount     number(10,2),
   cid          varchar2(20),
   primary key ( ord_num ),
   constraint fk_order_customer foreign key ( cid )
      references customer ( cid )
);

create table status (
   level_name   varchar2(20),
   threshold    number(10,2) not null,
   upgrade_threshold number(10,2) not null,
   shipping_fee number(5,2) not null,
   discount     number(4,2) not null,
   primary key ( level_name )
);

create table customer (
   cid         varchar2(20),
   first_name  varchar2(20),
   middle_name varchar2(20),
   last_name   varchar2(20),
   password    varchar2(20) not null,
   email       varchar2(40),
   address     varchar2(100),
   level_name  varchar2(20),
   primary key ( cid ),
   constraint fk_customer_status foreign key ( level_name )
      references status ( level_name )
);

create table manager (
    -- e for employee
   eid         varchar2(20),
   first_name  varchar2(20),
   middle_name varchar2(20),
   last_name   varchar2(20),
   password    varchar2(20) not null,
   email       varchar2(40),
   address     varchar2(100),
   primary key ( eid )
);