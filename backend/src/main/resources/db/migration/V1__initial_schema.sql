CREATE TABLE restaurante (
  id uuid PRIMARY KEY,
  nombre varchar(160) NOT NULL,
  activo boolean NOT NULL DEFAULT true,
  creado_en timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE instalacion (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  nombre varchar(120) NOT NULL,
  creada_en timestamptz NOT NULL DEFAULT now(),
  UNIQUE (restaurante_id, nombre)
);
CREATE TABLE empleado (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  nombre varchar(120) NOT NULL,
  rol varchar(24) NOT NULL CHECK (rol IN ('MESERO','COCINA','ADMINISTRADOR','DOMICILIARIO')),
  pin_hash varchar(255) NOT NULL,
  activo boolean NOT NULL DEFAULT true,
  creado_en timestamptz NOT NULL DEFAULT now(),
  UNIQUE (restaurante_id, nombre)
);
CREATE TABLE mesa (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  numero integer NOT NULL CHECK (numero > 0),
  activa boolean NOT NULL DEFAULT true,
  version bigint NOT NULL DEFAULT 0,
  UNIQUE (restaurante_id, numero)
);
CREATE TABLE categoria (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  nombre varchar(100) NOT NULL,
  activa boolean NOT NULL DEFAULT true,
  UNIQUE (restaurante_id, nombre)
);
CREATE TABLE producto (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  categoria_id uuid NOT NULL REFERENCES categoria(id),
  nombre varchar(160) NOT NULL,
  descripcion varchar(500),
  precio numeric(12,2) NOT NULL CHECK (precio >= 0),
  disponible boolean NOT NULL DEFAULT true,
  activo boolean NOT NULL DEFAULT true,
  version bigint NOT NULL DEFAULT 0,
  creado_en timestamptz NOT NULL DEFAULT now(),
  actualizado_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_producto_restaurante ON producto(restaurante_id, activo);
CREATE TABLE pedido (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  empleado_id uuid NOT NULL REFERENCES empleado(id),
  mesa_id uuid REFERENCES mesa(id),
  tipo varchar(16) NOT NULL CHECK (tipo IN ('MESA','DOMICILIO')),
  estado varchar(24) NOT NULL CHECK (estado IN ('BORRADOR','CONFIRMADO','EN_PREPARACION','LISTO','ENTREGADO','CANCELADO')),
  destino varchar(500),
  observaciones varchar(1000),
  total numeric(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  version bigint NOT NULL DEFAULT 0,
  creado_en timestamptz NOT NULL DEFAULT now(),
  actualizado_en timestamptz NOT NULL DEFAULT now(),
  CHECK ((tipo = 'MESA' AND mesa_id IS NOT NULL) OR (tipo = 'DOMICILIO' AND mesa_id IS NULL))
);
CREATE INDEX idx_pedido_restaurante_estado ON pedido(restaurante_id, estado, creado_en DESC);
CREATE TABLE detalle_pedido (
  id uuid PRIMARY KEY,
  pedido_id uuid NOT NULL REFERENCES pedido(id),
  producto_id uuid NOT NULL REFERENCES producto(id),
  nombre_snapshot varchar(160) NOT NULL,
  precio_snapshot numeric(12,2) NOT NULL CHECK (precio_snapshot >= 0),
  cantidad integer NOT NULL CHECK (cantidad > 0),
  observaciones varchar(500)
);
CREATE TABLE historial_estado_pedido (
  id uuid PRIMARY KEY,
  pedido_id uuid NOT NULL REFERENCES pedido(id),
  empleado_id uuid NOT NULL REFERENCES empleado(id),
  estado_anterior varchar(24),
  estado_nuevo varchar(24) NOT NULL,
  motivo varchar(500),
  creado_en timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE asignacion_domiciliario (
  id uuid PRIMARY KEY,
  pedido_id uuid NOT NULL REFERENCES pedido(id),
  empleado_id uuid NOT NULL REFERENCES empleado(id),
  asignado_por uuid NOT NULL REFERENCES empleado(id),
  asignado_en timestamptz NOT NULL DEFAULT now(),
  finalizado_en timestamptz
);
CREATE TABLE evento_outbox (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  instalacion_id uuid NOT NULL REFERENCES instalacion(id),
  tipo varchar(100) NOT NULL,
  entidad_id uuid NOT NULL,
  secuencia bigint NOT NULL,
  payload jsonb NOT NULL,
  estado varchar(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','SINCRONIZADO','CONFLICTO')),
  intentos integer NOT NULL DEFAULT 0 CHECK (intentos >= 0),
  ultimo_error varchar(1000),
  creado_en timestamptz NOT NULL DEFAULT now(),
  UNIQUE (instalacion_id, entidad_id, secuencia)
);
CREATE INDEX idx_outbox_pendiente ON evento_outbox(creado_en) WHERE estado = 'PENDIENTE';
CREATE TABLE evento_inbox (
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  evento_id uuid NOT NULL,
  recibido_en timestamptz NOT NULL DEFAULT now(),
  resultado varchar(20) NOT NULL,
  PRIMARY KEY (restaurante_id, evento_id)
);
CREATE TABLE trabajo_impresion (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  pedido_id uuid NOT NULL REFERENCES pedido(id),
  estacion varchar(100) NOT NULL,
  estado varchar(28) NOT NULL CHECK (estado IN ('PENDIENTE','ENVIADO','CONFIRMADO','FALLIDO','PENDIENTE_VERIFICACION')),
  intentos integer NOT NULL DEFAULT 0,
  ultimo_error varchar(1000),
  creado_en timestamptz NOT NULL DEFAULT now(),
  actualizado_en timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE registro_auditoria (
  id uuid PRIMARY KEY,
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  empleado_id uuid REFERENCES empleado(id),
  accion varchar(120) NOT NULL,
  recurso_id uuid,
  detalles jsonb NOT NULL DEFAULT '{}'::jsonb,
  creado_en timestamptz NOT NULL DEFAULT now()
);
