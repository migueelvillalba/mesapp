ALTER TABLE empleado ADD COLUMN version bigint NOT NULL DEFAULT 1;
ALTER TABLE categoria ADD COLUMN version bigint NOT NULL DEFAULT 1;
CREATE INDEX idx_producto_categoria_restaurante ON producto(restaurante_id,categoria_id,activo);
