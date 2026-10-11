CREATE TABLE pedido_sincronizado (
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  pedido_id uuid NOT NULL,
  secuencia bigint NOT NULL CHECK (secuencia > 0),
  tipo_evento varchar(100) NOT NULL,
  estado varchar(24) NOT NULL CHECK (estado IN ('BORRADOR','CONFIRMADO','EN_PREPARACION','LISTO','ENTREGADO','CANCELADO')),
  datos jsonb NOT NULL,
  actualizado_en timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (restaurante_id,pedido_id)
);
CREATE INDEX idx_pedido_sincronizado_estado ON pedido_sincronizado(restaurante_id,estado,actualizado_en DESC);

CREATE TABLE historial_evento_sincronizado (
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  evento_id uuid NOT NULL,
  pedido_id uuid NOT NULL,
  secuencia bigint NOT NULL CHECK (secuencia > 0),
  tipo_evento varchar(100) NOT NULL,
  datos jsonb NOT NULL,
  aplicado boolean NOT NULL,
  motivo varchar(500),
  recibido_en timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (restaurante_id,evento_id),
  FOREIGN KEY (restaurante_id,evento_id) REFERENCES evento_inbox(restaurante_id,evento_id)
);
CREATE INDEX idx_historial_evento_pedido ON historial_evento_sincronizado(restaurante_id,pedido_id,secuencia);

CREATE TABLE estacion_impresion (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  codigo varchar(100) NOT NULL,
  nombre varchar(120) NOT NULL,
  activa boolean NOT NULL DEFAULT true,
  creado_en timestamptz NOT NULL DEFAULT now(),
  UNIQUE(restaurante_id,codigo)
);
CREATE TABLE impresora (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  estacion_id uuid NOT NULL REFERENCES estacion_impresion(id),
  nombre varchar(120) NOT NULL,
  fabricante varchar(120),
  modelo varchar(120),
  adaptador varchar(80) NOT NULL DEFAULT 'SIMULADO',
  sistema varchar(120),
  activa boolean NOT NULL DEFAULT true,
  creada_en timestamptz NOT NULL DEFAULT now(),
  actualizada_en timestamptz NOT NULL DEFAULT now(),
  UNIQUE(restaurante_id,estacion_id,nombre)
);
