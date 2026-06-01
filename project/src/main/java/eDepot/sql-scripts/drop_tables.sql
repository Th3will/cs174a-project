SET SERVEROUTPUT ON;

BEGIN
    DBMS_OUTPUT.PUT_LINE('Deleting all eDepot tables');
END;
/

-- Drop child tables first so foreign keys do not block the drop.
-- CASCADE CONSTRAINTS handles any remaining dependencies; the exception
-- handler ignores ORA-00942 (table does not exist) so the script can be
-- safely re-run on a partially-dropped schema.
BEGIN
    FOR t IN (
        SELECT 'eDepot_Replenishment_Line'  AS name FROM dual UNION ALL
        SELECT 'eDepot_Notice_Line'              FROM dual UNION ALL
        SELECT 'eDepot_Replenishment_Order'      FROM dual UNION ALL
        SELECT 'eDepot_Shipping_Notice'          FROM dual UNION ALL
        SELECT 'eDepot_Warehouse_Item'           FROM dual UNION ALL
        SELECT 'eDepot_Location'                 FROM dual UNION ALL
        SELECT 'eDepot_Manufacturer'             FROM dual
    ) LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP TABLE ' || t.name || ' CASCADE CONSTRAINTS';
            DBMS_OUTPUT.PUT_LINE('Dropped ' || t.name);
        EXCEPTION
            WHEN OTHERS THEN
                IF SQLCODE = -942 THEN
                    DBMS_OUTPUT.PUT_LINE('Skipped ' || t.name || ' (does not exist)');
                ELSE
                    RAISE;
                END IF;
        END;
    END LOOP;
END;
/

BEGIN
    DBMS_OUTPUT.PUT_LINE('All eDepot tables deleted');
END;
/

COMMIT;
