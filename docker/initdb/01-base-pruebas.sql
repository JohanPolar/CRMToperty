-- Base separada para las pruebas: verifican conteos exactos y no deben
-- chocar con los datos que deja la aplicación en crm_toperty.
-- Postgres solo ejecuta este archivo al crear el volumen por primera vez.
CREATE DATABASE crm_toperty_test OWNER crm_user;
