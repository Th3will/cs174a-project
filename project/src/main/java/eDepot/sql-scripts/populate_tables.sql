SET SERVEROUTPUT ON;

BEGIN
    DBMS_OUTPUT.PUT_LINE('Populating tables with sample data');
END;
/

INSERT ALL
    INTO eDepot_Manufacturer VALUES ('Canon')
    INTO eDepot_Manufacturer VALUES ('Dell')
    INTO eDepot_Manufacturer VALUES ('Envision')
    INTO eDepot_Manufacturer VALUES ('HP')
    INTO eDepot_Manufacturer VALUES ('McAfee')
    INTO eDepot_Manufacturer VALUES ('Oracle')
    INTO eDepot_Manufacturer VALUES ('Samsung')
    INTO eDepot_Manufacturer VALUES ('Symantec')
    INTO eDepot_Manufacturer VALUES ('eMachines')
SELECT * FROM DUAL;

INSERT ALL
    INTO eDepot_Location VALUES ('A', 7)
    INTO eDepot_Location VALUES ('A', 9)
    INTO eDepot_Location VALUES ('B', 52)
    INTO eDepot_Location VALUES ('C', 13)
    INTO eDepot_Location VALUES ('C', 27)
    INTO eDepot_Location VALUES ('D', 3)
    INTO eDepot_Location VALUES ('D', 15)
    INTO eDepot_Location VALUES ('D', 27)
    INTO eDepot_Location VALUES ('E', 7)
    INTO eDepot_Location VALUES ('F', 3)
    INTO eDepot_Location VALUES ('F', 9)
SELECT * FROM DUAL;

INSERT ALL
    INTO eDepot_Warehouse_Item VALUES ('AA00101', 'HP', 'A6111', 2, 1, 2, 0, 'A', 9)
    INTO eDepot_Warehouse_Item VALUES ('AA00201', 'Dell', 'B420', 3, 2, 5, 0, 'A', 7)
    INTO eDepot_Warehouse_Item VALUES ('AA00202', 'eMachines', 'C3958', 4, 2, 5, 0, 'B', 52)
    INTO eDepot_Warehouse_Item VALUES ('AA00301', 'Envision', 'D720', 4, 3, 6, 0, 'C', 27)
    INTO eDepot_Warehouse_Item VALUES ('AA00302', 'Samsung', 'E712', 5, 3, 6, 0, 'C', 13)
    INTO eDepot_Warehouse_Item VALUES ('AA00401', 'Symantec', 'F2005', 7, 5, 9, 0, 'D', 27)
    INTO eDepot_Warehouse_Item VALUES ('AA00402', 'McAfee', 'G2005', 7, 5, 9, 0, 'D', 15)
    INTO eDepot_Warehouse_Item VALUES ('AA00403', 'Oracle', 'H26', 7, 5, 9, 0, 'D', 3)
    INTO eDepot_Warehouse_Item VALUES ('AA00501', 'HP', 'J1320', 3, 2, 4, 0, 'E', 7)
    INTO eDepot_Warehouse_Item VALUES ('AA00601', 'HP', 'K435', 3, 2, 5, 0, 'F', 9)
    INTO eDepot_Warehouse_Item VALUES ('AA00602', 'Canon', 'L738', 3, 2, 5, 0, 'F', 3)
SELECT * FROM DUAL;

BEGIN
    DBMS_OUTPUT.PUT_LINE('All sample data inserted into tables');
END;
/

COMMIT;