-- Teléfono opcional de un asistente añadido a mano (sin cuenta), para que el
-- admin que lo dio de alta pueda localizarlo.
ALTER TABLE asistencia_evento ADD COLUMN telefono VARCHAR(20);
