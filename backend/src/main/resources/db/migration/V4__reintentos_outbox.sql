ALTER TABLE evento_outbox ADD COLUMN siguiente_intento timestamptz NOT NULL DEFAULT now();
CREATE INDEX idx_outbox_reintento ON evento_outbox(siguiente_intento,creado_en) WHERE estado='PENDIENTE';
