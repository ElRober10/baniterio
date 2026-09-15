-- Peña demo pública: cualquiera puede entrar con el teléfono 666666666 /
-- contraseña 123456 (ver AuthService.TELEFONO_DEMO) para navegar toda la app
-- con datos FICTICIOS, sin poder modificar nada (lo corta JwtAuthenticationFilter).
-- Todo lo de aquí cuelga de la peña 'demo', totalmente separada de 'baniterio':
-- es una línea roja de privacidad que el demo nunca vea datos reales.
-- No editar tras aplicarse: Flyway valida el checksum. Cambios → nueva migración.

-- 1) La peña demo. Idempotente por slug.
INSERT INTO pena (nombre, slug, activa)
VALUES ('Peña Demo', 'demo', TRUE)
ON CONFLICT (slug) DO NOTHING;

-- 2) Usuarios ficticios (22, con nombres inventados) + la cuenta demo pública.
--    password_hash de los 22 ficticios: hash BCrypt válido cualquiera (no pueden
--    loguearse, solo se ven en los listados). El de la cuenta demo es "123456".
--    Si el teléfono 666666666 ya existiera de antes en esta base (p. ej. una
--    cuenta local de pruebas), el INSERT se salta por el conflicto de teléfono
--    y esa cuenta preexistente simplemente se suma como miembro de la demo
--    (paso 3); PenaPilotoService decide qué peña le corresponde por el claim
--    `esDemo` del JWT, no por cuál membresía tenga primero.
WITH gente(telefono, email, nombre, apellidos, mote, rol, avatar, sobre_mi) AS (
  VALUES
    ('699999901','laura.gomez@demo.baniterio.local','Laura','Gómez Pardo',NULL,'ADMIN','01_chica','Lleva las cuentas de la peña demo.'),
    ('699999902','ivan.fernandez@demo.baniterio.local','Iván','Fernández Cano',NULL,'ADMIN','01_chico',NULL),
    ('699999903','sara.molina@demo.baniterio.local','Sara','Molina Ortiz',NULL,'MIEMBRO','02_chica',NULL),
    ('699999904','pablo.vidal@demo.baniterio.local','Pablo','Vidal Reyes',NULL,'MIEMBRO','02_chico',NULL),
    ('699999905','cristina.nunez@demo.baniterio.local','Cristina','Núñez Blanco',NULL,'MIEMBRO','03_chica',NULL),
    ('699999906','alvaro.jimenez@demo.baniterio.local','Álvaro','Jiménez Rubio','Álvarito','MIEMBRO','03_chico',NULL),
    ('699999907','marta.serrano@demo.baniterio.local','Marta','Serrano Delgado',NULL,'MIEMBRO','04_chica',NULL),
    ('699999908','hugo.dominguez@demo.baniterio.local','Hugo','Domínguez Flores',NULL,'MIEMBRO','04_chico',NULL),
    ('699999909','elena.castillo@demo.baniterio.local','Elena','Castillo Vargas',NULL,'MIEMBRO','05_chica','Siempre trae la música.'),
    ('699999910','raul.ortega@demo.baniterio.local','Raúl','Ortega Campos',NULL,'MIEMBRO','05_chico',NULL),
    ('699999911','beatriz.moreno@demo.baniterio.local','Beatriz','Moreno Silva',NULL,'MIEMBRO','06_chica',NULL),
    ('699999912','adrian.guerrero@demo.baniterio.local','Adrián','Guerrero Lozano',NULL,'MIEMBRO','06_chico',NULL),
    ('699999913','silvia.ramos@demo.baniterio.local','Silvia','Ramos Bravo',NULL,'MIEMBRO','07_chica',NULL),
    ('699999914','oscar.herrera@demo.baniterio.local','Óscar','Herrera Soto',NULL,'MIEMBRO','07_chico',NULL),
    ('699999915','patricia.vega@demo.baniterio.local','Patricia','Vega Cortés',NULL,'MIEMBRO','08_chica',NULL),
    ('699999916','fernando.aguilar@demo.baniterio.local','Fernando','Aguilar Nieto',NULL,'MIEMBRO','08_chico',NULL),
    ('699999917','rocio.pascual@demo.baniterio.local','Rocío','Pascual Galán',NULL,'MIEMBRO','09_chica',NULL),
    ('699999918','daniel.cabrera@demo.baniterio.local','Daniel','Cabrera Ríos',NULL,'MIEMBRO','09_chico',NULL),
    ('699999919','alicia.santos@demo.baniterio.local','Alicia','Santos Marín',NULL,'MIEMBRO','10_chica',NULL),
    ('699999920','jorge.redondo@demo.baniterio.local','Jorge','Redondo Esteban',NULL,'MIEMBRO','10_chico',NULL),
    ('699999921','noelia.carrasco@demo.baniterio.local','Noelia','Carrasco Vidal',NULL,'MIEMBRO','11_chica',NULL),
    ('699999922','victor.lozano@demo.baniterio.local','Víctor','Lozano Peral',NULL,'MIEMBRO','11_chico',NULL),
    ('666666666','demo@baniterio.local','Cuenta','Demo','Demo','MIEMBRO','12_chico','Cuenta pública de demostración: puedes mirarlo todo, pero no se guarda nada.')
)
INSERT INTO usuario (telefono, email, password_hash, nombre, apellidos, mote, es_superadmin, activo)
SELECT g.telefono, g.email,
       CASE WHEN g.telefono = '666666666'
            THEN '$2b$10$Y5kUd4Ae7pTPH/gjLPtXK.DJTlGQcfF3dECMuBPEMZUDZtPW.lUSm' -- 123456
            ELSE '$2a$10$AU/hjjwzQKMK.4vwkqRl0usCcDF1cMTTwp8jwTtfwWiEI5ZPoCZS2' -- hash cualquiera, no se usa para loguear
       END,
       g.nombre, g.apellidos, g.mote, FALSE, TRUE
FROM gente g
ON CONFLICT DO NOTHING;

-- 3) Membresía activa de cada uno en la peña demo.
WITH gente(telefono, rol) AS (
  VALUES
    ('699999901','ADMIN'), ('699999902','ADMIN'), ('699999903','MIEMBRO'), ('699999904','MIEMBRO'),
    ('699999905','MIEMBRO'), ('699999906','MIEMBRO'), ('699999907','MIEMBRO'), ('699999908','MIEMBRO'),
    ('699999909','MIEMBRO'), ('699999910','MIEMBRO'), ('699999911','MIEMBRO'), ('699999912','MIEMBRO'),
    ('699999913','MIEMBRO'), ('699999914','MIEMBRO'), ('699999915','MIEMBRO'), ('699999916','MIEMBRO'),
    ('699999917','MIEMBRO'), ('699999918','MIEMBRO'), ('699999919','MIEMBRO'), ('699999920','MIEMBRO'),
    ('699999921','MIEMBRO'), ('699999922','MIEMBRO'), ('666666666','MIEMBRO')
)
INSERT INTO membresia (usuario_id, pena_id, rol, activa)
SELECT u.id, p.id, g.rol, TRUE
FROM gente g
JOIN usuario u ON u.telefono = g.telefono
JOIN pena p ON p.slug = 'demo'
ON CONFLICT (usuario_id, pena_id) DO NOTHING;

-- 4) Perfil "forma de carta" (avatar del catálogo, ya completado) de cada uno.
WITH gente(telefono, avatar, sobre_mi) AS (
  VALUES
    ('699999901','01_chica','Lleva las cuentas de la peña demo.'),
    ('699999902','01_chico',NULL),
    ('699999903','02_chica',NULL),
    ('699999904','02_chico',NULL),
    ('699999905','03_chica',NULL),
    ('699999906','03_chico',NULL),
    ('699999907','04_chica',NULL),
    ('699999908','04_chico',NULL),
    ('699999909','05_chica','Siempre trae la música.'),
    ('699999910','05_chico',NULL),
    ('699999911','06_chica',NULL),
    ('699999912','06_chico',NULL),
    ('699999913','07_chica',NULL),
    ('699999914','07_chico',NULL),
    ('699999915','08_chica',NULL),
    ('699999916','08_chico',NULL),
    ('699999917','09_chica',NULL),
    ('699999918','09_chico',NULL),
    ('699999919','10_chica',NULL),
    ('699999920','10_chico',NULL),
    ('699999921','11_chica',NULL),
    ('699999922','11_chico',NULL),
    ('666666666','12_chico','Cuenta pública de demostración: puedes mirarlo todo, pero no se guarda nada.')
)
INSERT INTO perfil (usuario_id, sobre_mi, imagen_tipo, imagen_ref, completado)
SELECT u.id, g.sobre_mi, 'AVATAR', g.avatar, TRUE
FROM gente g
JOIN usuario u ON u.telefono = g.telefono
ON CONFLICT (usuario_id) DO NOTHING;

-- 5) Las tres cuentas y los tres eventos anuales de la peña, con los MISMOS
--    nombres que tiene la peña real (no son datos personales, son el nombre
--    de fiestas del pueblo) pero en la demo. De momento solo "San Miguel" está
--    desarrollado con datos (asistentes, movimientos) — los otros dos existen
--    vacíos, igual que en la peña real.
INSERT INTO cuenta (pena_id, nombre, descripcion, lleva_ficha_bebida)
SELECT p.id, v.nombre, v.descripcion, v.lleva_ficha_bebida
FROM pena p
CROSS JOIN (VALUES
    ('San Miguel',      'Cuenta de las fiestas de San Miguel.', TRUE),
    ('Chuletas Santas', 'Cuenta de la comida de Chuletas Santas.', FALSE),
    ('Migas Santas',    'Cuenta de la comida de Migas Santas.', FALSE)
) AS v(nombre, descripcion, lleva_ficha_bebida)
WHERE p.slug = 'demo'
ON CONFLICT (pena_id, nombre) DO NOTHING;

INSERT INTO evento (
  pena_id, cuenta_id, nombre, descripcion, lugar, fecha,
  cuota_cubatas, cuota_cervezas, cuota_cubatas_1dia, cuota_cervezas_1dia, cuota_embarazada
)
SELECT p.id, c.id, 'Fiestas de San Miguel 2026', 'Fiestas patronales de San Miguel.', 'El Teleclub', DATE '2026-09-25',
       26.00, 16.00, 14.00, 9.00, 5.00
FROM pena p JOIN cuenta c ON c.pena_id = p.id AND c.nombre = 'San Miguel'
WHERE p.slug = 'demo'
  AND NOT EXISTS (SELECT 1 FROM evento e WHERE e.pena_id = p.id AND e.nombre = 'Fiestas de San Miguel 2026');

INSERT INTO evento (pena_id, cuenta_id, nombre, descripcion, lugar, fecha)
SELECT p.id, c.id, 'Chuletas Santas 2027', NULL, NULL, DATE '2027-03-26'
FROM pena p JOIN cuenta c ON c.pena_id = p.id AND c.nombre = 'Chuletas Santas'
WHERE p.slug = 'demo'
  AND NOT EXISTS (SELECT 1 FROM evento e WHERE e.pena_id = p.id AND e.nombre = 'Chuletas Santas 2027');

INSERT INTO evento (pena_id, cuenta_id, nombre, descripcion, lugar, fecha)
SELECT p.id, c.id, 'Migas Santas 2027', NULL, NULL, DATE '2027-03-27'
FROM pena p JOIN cuenta c ON c.pena_id = p.id AND c.nombre = 'Migas Santas'
WHERE p.slug = 'demo'
  AND NOT EXISTS (SELECT 1 FROM evento e WHERE e.pena_id = p.id AND e.nombre = 'Migas Santas 2027');

-- 6) Asistentes de ejemplo, solo al evento desarrollado (San Miguel): la
--    mayoría apuntada, para que se vea como un evento abierto y con vida.
WITH asistentes(telefono, estado) AS (
  VALUES
    ('699999901','APUNTADO'), ('699999902','APUNTADO'), ('699999903','APUNTADO'),
    ('699999904','NO_VOY'),   ('699999905','EN_DUDA'),  ('699999906','APUNTADO'),
    ('699999907','APUNTADO'), ('699999908','APUNTADO'), ('699999909','APUNTADO'),
    ('699999910','APUNTADO'), ('699999911','APUNTADO'), ('699999912','APUNTADO'),
    ('699999913','EN_DUDA'),  ('699999914','APUNTADO'), ('699999915','NO_VOY'),
    ('699999916','APUNTADO'), ('699999917','APUNTADO'), ('699999918','APUNTADO'),
    ('699999919','APUNTADO'), ('699999920','EN_DUDA'),  ('699999921','APUNTADO'),
    ('699999922','APUNTADO'), ('666666666','APUNTADO')
)
INSERT INTO asistencia_evento (evento_id, usuario_id, estado)
SELECT e.id, u.id, a.estado
FROM asistentes a
JOIN usuario u ON u.telefono = a.telefono
JOIN evento e ON e.nombre = 'Fiestas de San Miguel 2026'
JOIN pena p ON p.id = e.pena_id AND p.slug = 'demo'
ON CONFLICT (evento_id, usuario_id) WHERE usuario_id IS NOT NULL DO NOTHING;

-- 6b) Ficha de bebida de cada asistente (qué bebe, cuota, si ha pagado): sin
--     esto el listado de asistentes da 409 EVENTO_SIN_FICHA. Bebidas por
--     nombre contra el catálogo global de `bebida` (no es dato de la peña).
-- Cuotas de San Miguel = 26/16/14/9/5 (ver evento arriba); coherentes con
-- CalculadoraCuota: COMPLETA (bebe alcohol) = 26, SOLO_CERVEZA (no bebe
-- alcohol) = 16, UN_DIA = 14 (con alcohol) o 9 (sin alcohol), EMBARAZADA = 5.
WITH fichas(telefono, alcohol, refresco, alternativa, modalidad, dia1, dia2, cuota, estado_pago) AS (
  VALUES
    ('699999901','Tanqueray','Coca-Cola','NADA','COMPLETA',TRUE,TRUE,26.00,'CONFIRMADO_EN_CUENTA'),
    ('699999902','Beefeater','Coca-Cola Zero','NADA','COMPLETA',TRUE,TRUE,26.00,'CONFIRMADO_EN_CUENTA'),
    ('699999903',NULL,'Coca-Cola','CERVEZA','SOLO_CERVEZA',TRUE,TRUE,16.00,'CONFIRMADO_EN_CUENTA'),
    ('699999905','Brugal','Tónica','NADA','COMPLETA',TRUE,TRUE,26.00,'PENDIENTE_PAGO'),
    ('699999906','Puerto de Indias','Coca-Cola','NADA','COMPLETA',TRUE,TRUE,26.00,'DECLARADO'),
    ('699999907',NULL,'Coca-Cola','TINTO_VERANO','SOLO_CERVEZA',TRUE,TRUE,16.00,'PENDIENTE_PAGO'),
    ('699999908','Four Roses','Fanta Naranja','NADA','COMPLETA',TRUE,TRUE,26.00,'CONFIRMADO_PENDIENTE_ENVIO'),
    ('699999909',NULL,'Sprite','CERVEZA','SOLO_CERVEZA',TRUE,TRUE,16.00,'PENDIENTE_PAGO'),
    ('699999910','Absolut','Coca-Cola Zero','NADA','COMPLETA',TRUE,TRUE,26.00,'PENDIENTE_PAGO'),
    ('699999911',NULL,'Coca-Cola','CERVEZA','UN_DIA',TRUE,FALSE,9.00,'PENDIENTE_PAGO'),
    ('699999912','Tanqueray','Tónica','NADA','COMPLETA',TRUE,TRUE,26.00,'DECLARADO'),
    ('699999913','Seagram''s','Coca-Cola','NADA','COMPLETA',TRUE,TRUE,26.00,'PENDIENTE_PAGO'),
    ('699999914',NULL,'Nestea','TINTO_VERANO','SOLO_CERVEZA',TRUE,TRUE,16.00,'PENDIENTE_PAGO'),
    ('699999916','Ballantines','Coca-Cola','NADA','COMPLETA',TRUE,TRUE,26.00,'PENDIENTE_PAGO'),
    ('699999917',NULL,'Coca-Cola','CERVEZA','SOLO_CERVEZA',TRUE,TRUE,16.00,'PENDIENTE_PAGO'),
    ('699999918','Larios','Fanta Limón','NADA','COMPLETA',TRUE,TRUE,26.00,'PENDIENTE_PAGO'),
    ('699999919',NULL,'Aquarius','CERVEZA','SOLO_CERVEZA',TRUE,TRUE,16.00,'PENDIENTE_PAGO'),
    ('699999920','Malibú','Coca-Cola','NADA','COMPLETA',TRUE,TRUE,26.00,'PENDIENTE_PAGO'),
    ('699999921',NULL,'Coca-Cola','TINTO_VERANO','SOLO_CERVEZA',TRUE,TRUE,16.00,'PENDIENTE_PAGO'),
    ('699999922','JB','Schweppes Limón','NADA','COMPLETA',TRUE,TRUE,26.00,'PENDIENTE_PAGO'),
    ('666666666',NULL,'Coca-Cola','CERVEZA','SOLO_CERVEZA',TRUE,TRUE,16.00,'PENDIENTE_PAGO')
)
INSERT INTO ficha_bebida (asistencia_id, alcohol_bebida_id, refresco_bebida_id, alternativa, modalidad, asiste_dia_1, asiste_dia_2, cuota, estado_pago)
SELECT ae.id,
       (SELECT id FROM bebida WHERE tipo = 'ALCOHOL' AND nombre = f.alcohol),
       (SELECT id FROM bebida WHERE tipo = 'REFRESCO' AND nombre = f.refresco),
       f.alternativa, f.modalidad, f.dia1, f.dia2, f.cuota, f.estado_pago
FROM fichas f
JOIN usuario u ON u.telefono = f.telefono
JOIN evento e ON e.nombre = 'Fiestas de San Miguel 2026'
JOIN pena p ON p.id = e.pena_id AND p.slug = 'demo'
JOIN asistencia_evento ae ON ae.evento_id = e.id AND ae.usuario_id = u.id
ON CONFLICT (asistencia_id) DO NOTHING;

-- 7) Movimientos de ejemplo de la cuenta de San Miguel (saldo inicial, cuotas
--    confirmadas, gastos de bebida/comida con recibo nulo y un ingreso),
--    estilo hoja del tesorero.
INSERT INTO movimiento_cuenta (cuenta_id, concepto, importe, fecha, origen)
SELECT c.id, 'Saldo del año anterior', 500.00, DATE '2026-01-01', 'SALDO_INICIAL'
FROM cuenta c JOIN pena p ON p.id = c.pena_id
WHERE p.slug = 'demo' AND c.nombre = 'San Miguel'
  AND NOT EXISTS (SELECT 1 FROM movimiento_cuenta m WHERE m.cuenta_id = c.id AND m.origen = 'SALDO_INICIAL');

INSERT INTO movimiento_cuenta (cuenta_id, concepto, importe, fecha, origen)
SELECT c.id, v.concepto, v.importe, CURRENT_DATE - 5, 'CUOTA'
FROM cuenta c
JOIN pena p ON p.id = c.pena_id
CROSS JOIN (VALUES
    ('Cuota Laura Gómez - San Miguel', 20.00),
    ('Cuota Iván Fernández - San Miguel', 20.00),
    ('Cuota Sara Molina - San Miguel', 15.00)
) AS v(concepto, importe)
WHERE p.slug = 'demo' AND c.nombre = 'San Miguel'
  AND NOT EXISTS (SELECT 1 FROM movimiento_cuenta m WHERE m.cuenta_id = c.id AND m.concepto = v.concepto);

INSERT INTO movimiento_cuenta (cuenta_id, concepto, importe, fecha, origen, categoria, recibo_archivo, adelantado_por)
SELECT c.id, v.concepto, v.importe, CURRENT_DATE - 4, v.origen, v.categoria, NULL, u.id
FROM cuenta c
JOIN pena p ON p.id = c.pena_id
CROSS JOIN (VALUES
    ('Bebida alcohólica para las fiestas', -280.00, 'GASTO', 'ALCOHOL',         '699999901'),
    ('Cerveza y tinto de verano',          -150.00, 'GASTO', 'CERVEZA_Y_TINTO', '699999902'),
    ('Refrescos y agua',                   -70.00,  'GASTO', 'REFRESCOS',       '699999903'),
    ('Jamón y picoteo',                    -95.00,  'GASTO', 'COMIDA',          '699999904'),
    ('Donativo del ayuntamiento',           200.00, 'INGRESO','OTROS',          '699999901')
) AS v(concepto, importe, origen, categoria, telefono)
JOIN usuario u ON u.telefono = v.telefono
WHERE p.slug = 'demo' AND c.nombre = 'San Miguel'
  AND NOT EXISTS (SELECT 1 FROM movimiento_cuenta m WHERE m.cuenta_id = c.id AND m.concepto = v.concepto);

-- 8) Inventario: el mismo catálogo de productos que la peña real, con
--    cantidades ficticias (la real las lleva a 0 porque no lo usa como
--    "stock" fijo; aquí sí conviene que se vea con existencias de ejemplo).
INSERT INTO articulo_inventario (pena_id, categoria, nombre, tamano, cantidad, orden)
SELECT p.id, v.categoria, v.nombre, v.tamano, v.cantidad, v.orden
FROM pena p
CROSS JOIN (VALUES
    ('ALCOHOL',   'Tanqueray',        '70 cl',   3,  1),
    ('ALCOHOL',   'Beefeater',        '1 L',     2,  2),
    ('ALCOHOL',   'Puerto de Indias', '1 L',     2,  3),
    ('ALCOHOL',   'Brugal',           '1 L',     4,  4),
    ('ALCOHOL',   'Four Roses',       '70 cl',   1,  5),
    ('CERVEZA',   'Mahou Clásica',    'lata',    150,1),
    ('CERVEZA',   'Mahou 0,0 Tostada','lata',    40, 2),
    ('CERVEZA',   'Coronita',         'lata',    60, 3),
    ('REFRESCOS', 'Coca-Cola normal', 'botella', 30, 1),
    ('REFRESCOS', 'Coca-Cola Zero',   'botella', 20, 2),
    ('REFRESCOS', 'Tónica',           'botella', 15, 3),
    ('REFRESCOS', 'Agua',             'garrafa', 10, 4),
    ('LIMPIEZA',  'Vasos de sidra',   'unidad',  300,1),
    ('LIMPIEZA',  'Vasos de chupito', 'unidad',  200,2),
    ('LIMPIEZA',  'Papel de cocina',  'rollo',   12, 3),
    ('LIMPIEZA',  'Bayetas',          'unidad',  8,  4)
) AS v(categoria, nombre, tamano, cantidad, orden)
WHERE p.slug = 'demo'
  AND NOT EXISTS (
      SELECT 1 FROM articulo_inventario a
      WHERE a.pena_id = p.id AND a.categoria = v.categoria
        AND a.nombre = v.nombre AND a.tamano = v.tamano
  );
