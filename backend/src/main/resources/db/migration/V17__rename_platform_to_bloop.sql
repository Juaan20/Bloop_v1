-- =====================================================================
-- V17 - La plataforma pasa a llamarse Bloop (antes VenueFlow).
-- V2 no se edita (Flyway valida su checksum): se corrige aqui el unico dato que llevaba el nombre.
-- =====================================================================
UPDATE roles SET description = 'Administrador de la plataforma Bloop' WHERE code = 'SUPER_ADMIN';
