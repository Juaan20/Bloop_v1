-- =====================================================================
-- V15 - Categoria "solo inventario" (SUPPLY): botellas de alcohol que no se venden enteras en la
-- barra; se descuentan por receta al vender un combinado o un chupito. No sale en ningun TPV.
-- (Las reservas VIP pasan a ser siempre por fiesta: lo controla el backend; las reservas antiguas
--  sin fiesta se conservan como historial, por eso session_id sigue admitiendo NULL.)
-- =====================================================================
ALTER TABLE product_categories DROP CONSTRAINT ck_product_categories_kind;
ALTER TABLE product_categories ADD CONSTRAINT ck_product_categories_kind
    CHECK (kind IN ('BAR', 'VIP_BOTTLE', 'SERVICE', 'OTHER', 'SUPPLY'));
