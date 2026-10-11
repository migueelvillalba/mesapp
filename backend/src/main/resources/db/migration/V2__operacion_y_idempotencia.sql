ALTER TABLE pedido ADD COLUMN idempotency_key varchar(120);
CREATE UNIQUE INDEX uq_pedido_idempotencia ON pedido(restaurante_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
ALTER TABLE trabajo_impresion ADD COLUMN idempotency_key varchar(120);
CREATE UNIQUE INDEX uq_trabajo_impresion_idempotencia ON trabajo_impresion(restaurante_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
ALTER TABLE evento_inbox ADD COLUMN payload_hash varchar(64);
CREATE TABLE IF NOT EXISTS intentos_pin (
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  empleado_id uuid NOT NULL REFERENCES empleado(id),
  intentos integer NOT NULL DEFAULT 0,
  bloqueado_hasta timestamptz,
  actualizado_en timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (restaurante_id, empleado_id)
);
