CREATE TABLE credencial_instalacion (
  restaurante_id uuid NOT NULL REFERENCES restaurante(id),
  instalacion_id uuid NOT NULL REFERENCES instalacion(id),
  token_sha256 varchar(64) NOT NULL,
  activa boolean NOT NULL DEFAULT true,
  creada_en timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(restaurante_id,instalacion_id),
  UNIQUE(instalacion_id)
);
