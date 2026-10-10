ALTER TABLE evento_inbox ADD COLUMN payload jsonb NOT NULL DEFAULT '{}'::jsonb;
CREATE UNIQUE INDEX uq_pedido_mesa_activo ON pedido(mesa_id)
  WHERE tipo = 'MESA' AND estado IN ('CONFIRMADO','EN_PREPARACION','LISTO');
CREATE INDEX idx_asignacion_activa ON asignacion_domiciliario(empleado_id,pedido_id) WHERE finalizado_en IS NULL;
