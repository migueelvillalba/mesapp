ALTER TABLE evento_inbox ADD COLUMN instalacion_id uuid REFERENCES instalacion(id);
ALTER TABLE evento_inbox ADD COLUMN tipo varchar(100);
ALTER TABLE evento_inbox ADD COLUMN entidad_id uuid;
ALTER TABLE evento_inbox ADD COLUMN secuencia bigint;
ALTER TABLE evento_inbox ADD COLUMN creado_en_cliente timestamptz;
