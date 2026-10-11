CREATE TABLE entidad_sincronizada (
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  entidad_id uuid NOT NULL,
  tipo_entidad varchar(30) NOT NULL CHECK (tipo_entidad IN ('PEDIDO','MESA','CATEGORIA','PRODUCTO','EMPLEADO')),
  tipo_evento varchar(100) NOT NULL,
  secuencia bigint NOT NULL CHECK (secuencia > 0),
  datos jsonb NOT NULL,
  actualizado_en timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(restaurante_id,entidad_id)
);
CREATE INDEX idx_entidad_sincronizada_tipo ON entidad_sincronizada(restaurante_id,tipo_entidad,actualizado_en DESC);
