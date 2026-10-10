ALTER TABLE trabajo_impresion ADD COLUMN reclamo_expira_en timestamptz;
ALTER TABLE trabajo_impresion ADD COLUMN claim_token uuid;
CREATE INDEX idx_trabajo_impresion_reclamo ON trabajo_impresion(reclamo_expira_en)
  WHERE estado='ENVIADO' AND reclamo_expira_en IS NOT NULL;
