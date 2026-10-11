ALTER TABLE pedido
  ADD COLUMN modalidad_servicio varchar(20) NOT NULL DEFAULT 'SERVIRSE';

ALTER TABLE pedido
  ADD CONSTRAINT ck_pedido_modalidad_servicio
  CHECK (modalidad_servicio IN ('SERVIRSE', 'PARA_LLEVAR', 'DOMICILIO'));

UPDATE pedido
SET modalidad_servicio = 'DOMICILIO'
WHERE tipo = 'DOMICILIO';
