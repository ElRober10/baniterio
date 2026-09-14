-- Faltaba en el catálogo de refrescos de la ficha de San Miguel.
INSERT INTO bebida (tipo, nombre, estado) VALUES
  ('REFRESCO', 'Coca-Cola Zero Zero', 'ACEPTADA')
ON CONFLICT DO NOTHING;
