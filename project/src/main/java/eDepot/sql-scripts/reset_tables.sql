SET SERVEROUTPUT ON;

BEGIN
    DBMS_OUTPUT.PUT_LINE('Clearing all tables of data');
END;
/

DELETE FROM eDepot_Replenishment_Line;
DELETE FROM eDepot_Notice_Line;
DELETE FROM eDepot_Replenishment_Order;
DELETE FROM eDepot_Shipping_Notice;
DELETE FROM eDepot_Warehouse_Item;
DELETE FROM eDepot_Location;
DELETE FROM eDepot_Manufacturer;

BEGIN
    DBMS_OUTPUT.PUT_LINE('All tables reset');
END;
/

COMMIT;