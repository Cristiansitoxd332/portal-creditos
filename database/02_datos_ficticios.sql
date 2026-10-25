-- =====================================================================
--  PORTAL DE CONSULTA DE CREDITOS DE MOTOS (RENTAR) - PROTOTIPO
--  Script 02: DATOS FICTICIOS para pruebas y demostracion
--
--  AVISO: todas las personas, documentos, placas, telefonos, correos y
--  valores de este archivo son INVENTADOS. No corresponden a clientes
--  reales ni a informacion de ninguna empresa.
--
--  Motos Yamaha: precio de lista + matricula con reserva (prenda) tomados
--  de la lista de precios del prototipo. Tasas y plazos son de ejemplo.
--
--  Fecha de referencia de los escenarios: 17 de octubre de 2026.
--  Las tablas de cuotas se calcularon con el sistema frances (cuota fija),
--  redondeando a pesos; la ultima cuota ajusta la diferencia de redondeo.
--
--  Credenciales de prueba:
--    Administrador:  usuario admin           clave Admin2026*
--    Clientes:       usuario = su documento  clave Cliente2026*
--
--  Requiere haber ejecutado antes 01_esquema.sql.
--  Uso (CMD, en C:\proyectos\portal-creditos):
--    mysql -u portal_user -p portal_creditos < database\02_datos_ficticios.sql
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE auditoria;
TRUNCATE TABLE importacion_error;
TRUNCATE TABLE importacion;
TRUNCATE TABLE canal_contacto;
TRUNCATE TABLE solicitud_levantamiento;
TRUNCATE TABLE requisito_levantamiento;
TRUNCATE TABLE paz_y_salvo;
TRUNCATE TABLE aplicacion_pago;
TRUNCATE TABLE pago;
TRUNCATE TABLE cuota;
TRUNCATE TABLE credito;
TRUNCATE TABLE vehiculo;
TRUNCATE TABLE usuario;
TRUNCATE TABLE cliente;
SET FOREIGN_KEY_CHECKS = 1;

START TRANSACTION;

-- ---------------------------------------------------------------------
-- CLIENTES (5 personas ficticias)
-- ---------------------------------------------------------------------
INSERT INTO cliente (id, tipo_documento, numero_documento, nombres, apellidos, correo, telefono, direccion, ciudad) VALUES
(1, 'CC', '1075200101', 'Laura Marcela', 'Gomez Perdomo', 'laura.gomez@correo-ficticio.com', '3000000101', 'Calle 10 # 5-20', 'Neiva'),
(2, 'CC', '1075200202', 'Andres Felipe', 'Rojas Cuellar', 'andres.rojas@correo-ficticio.com', '3000000202', 'Carrera 7 # 12-45', 'Neiva'),
(3, 'CC', '1075200303', 'Diana Patricia', 'Vargas Silva', 'diana.vargas@correo-ficticio.com', '3000000303', 'Avenida 26 # 3-15', 'Pitalito'),
(4, 'CE', '980045600', 'Jorge Luis', 'Medina Ortiz', 'jorge.medina@correo-ficticio.com', '3000000404', 'Calle 21 Sur # 8-30', 'Garzon'),
(5, 'CC', '1075200505', 'Sofia Alejandra', 'Trujillo Ramos', 'sofia.trujillo@correo-ficticio.com', '3000000505', 'Carrera 2 # 18-60', 'Neiva');

-- ---------------------------------------------------------------------
-- USUARIOS (contrasenas cifradas con BCrypt)
-- ---------------------------------------------------------------------
INSERT INTO usuario (id, username, password_hash, rol, cliente_id, activo, debe_cambiar_password) VALUES
(1, 'admin', '$2a$10$NhaUVIPPN716G9FDh1IYl.LAowVIhIr/3TRqmfG9TlRpbL0lUOile', 'ADMIN', NULL, TRUE, FALSE),
(2, '1075200101', '$2a$10$8gJM3eIF44r0OuIaC0OPoOz5yqLjQ7Gpgir/bxGQ4uyUHnDulVmXO', 'CLIENTE', 1, TRUE, FALSE),
(3, '1075200202', '$2a$10$07A3Qz/4hdQLBlnAtGfu/egxQxVrQWtMZEVgVV/V.d3IP.mmyZGc6', 'CLIENTE', 2, TRUE, FALSE),
(4, '1075200303', '$2a$10$I4DsdTz8aO3AMu7ytpUxc.IRiU9F8tXK2vAobqBHYsHO/gHKU5Xlu', 'CLIENTE', 3, TRUE, FALSE),
(5, '980045600', '$2a$10$9CG2FxQ8ZOWIcnxAI0oZBeeCzWbTGKiKgHOGsfInOmOGtghahSWA6', 'CLIENTE', 4, TRUE, TRUE),
(6, '1075200505', '$2a$10$gsSVb0NGhOM8smalMcJ7iu696BZ50gDbpkYgiGlyBC//0ngqDl5VK', 'CLIENTE', 5, TRUE, FALSE);

-- ---------------------------------------------------------------------
-- VEHICULOS (placas y numeros de motor/chasis inventados)
-- ---------------------------------------------------------------------
INSERT INTO vehiculo (id, placa, marca, linea, modelo, color, clase, numero_motor, numero_chasis) VALUES
(1, 'KXT48F', 'Yamaha', 'NMAX GPD155-A Nueva version', 2025, 'Gris', 'MOTOCICLETA', 'MTR-FICT-0001', 'CHS-FICT-0001'),
(2, 'JHM31G', 'Yamaha', 'XTZ-250', 2025, 'Azul', 'MOTOCICLETA', 'MTR-FICT-0002', 'CHS-FICT-0002'),
(3, 'NPQ90E', 'Yamaha', 'FZ15A FZN150-A Nueva', 2025, 'Negro', 'MOTOCICLETA', 'MTR-FICT-0003', 'CHS-FICT-0003'),
(4, 'LBR22H', 'Yamaha', 'XTZ150 Nueva', 2026, 'Blanco', 'MOTOCICLETA', 'MTR-FICT-0004', 'CHS-FICT-0004'),
(5, 'FTY63G', 'Yamaha', 'Crypton T115 FI', 2024, 'Rojo', 'MOTOCICLETA', 'MTR-FICT-0005', 'CHS-FICT-0005'),
(6, 'GWC74D', 'Yamaha', 'MT155', 2025, 'Negro mate', 'MOTOCICLETA', 'MTR-FICT-0006', 'CHS-FICT-0006');

-- ---------------------------------------------------------------------
-- CREDITOS - escenarios de prueba
--   CV-2025-0001: AL DIA: paga puntual, sin cuotas vencidas
--   CV-2025-0002: EN MORA: 3 cuotas vencidas (ago, sep y oct de 2026)
--   CV-2025-0003: CANCELADO: paz y salvo emitido, levantamiento de prenda en revision
--   CV-2026-0004: NUEVO: primera cuota aun no vence; primer ingreso exige cambio de clave
--   CV-2024-0005: CANCELADO: prenda ya levantada (solicitud aprobada)
--   CV-2025-0006: AL DIA con pago adelantado: tiene la cuota de noviembre ya pagada
-- ---------------------------------------------------------------------
INSERT INTO credito (id, numero_credito, cliente_id, vehiculo_id, valor_vehiculo, cuota_inicial, valor_financiado, tasa_interes_mensual, plazo_meses, valor_cuota, fecha_desembolso, fecha_primera_cuota, saldo_capital, estado, estado_prenda, fecha_cancelacion) VALUES
(1, 'CV-2025-0001', 1, 1, 17625000.00, 2625000.00, 15000000.00, 1.8500, 36, 574410.00, '2025-09-25', '2025-10-25', 11051239.00, 'VIGENTE', 'VIGENTE', NULL),
(2, 'CV-2025-0002', 2, 2, 27201000.00, 5201000.00, 22000000.00, 1.9000, 36, 849322.00, '2025-05-10', '2025-06-10', 15155983.00, 'EN_MORA', 'VIGENTE', NULL),
(3, 'CV-2025-0003', 3, 3, 12594000.00, 2594000.00, 10000000.00, 1.8000, 12, 934020.00, '2025-08-05', '2025-09-05', 0.00, 'CANCELADO', 'EN_TRAMITE', '2026-08-04'),
(4, 'CV-2026-0004', 4, 4, 15311000.00, 3311000.00, 12000000.00, 1.8500, 24, 623724.00, '2026-09-18', '2026-10-18', 12000000.00, 'VIGENTE', 'VIGENTE', NULL),
(5, 'CV-2024-0005', 5, 5, 9792000.00, 1792000.00, 8000000.00, 1.9000, 18, 528937.00, '2024-11-15', '2024-12-15', 0.00, 'CANCELADO', 'LEVANTADA', '2026-05-12'),
(6, 'CV-2025-0006', 5, 6, 16317000.00, 3317000.00, 13000000.00, 1.8500, 36, 497822.00, '2025-03-12', '2025-04-12', 6840292.00, 'VIGENTE', 'VIGENTE', NULL);

-- ---------------------------------------------------------------------
-- CUOTAS (plan de pagos completo de cada credito)
-- ---------------------------------------------------------------------
INSERT INTO cuota (id, credito_id, numero_cuota, fecha_vencimiento, valor_capital, valor_interes, valor_cuota, saldo_capital_final, valor_pagado, fecha_pago, estado) VALUES
(1, 1, 1, '2025-10-25', 296910.00, 277500.00, 574410.00, 14703090.00, 574410.00, '2025-10-23', 'PAGADA'),
(2, 1, 2, '2025-11-25', 302403.00, 272007.00, 574410.00, 14400687.00, 574410.00, '2025-11-22', 'PAGADA'),
(3, 1, 3, '2025-12-25', 307997.00, 266413.00, 574410.00, 14092690.00, 574410.00, '2025-12-21', 'PAGADA'),
(4, 1, 4, '2026-01-25', 313695.00, 260715.00, 574410.00, 13778995.00, 574410.00, '2026-01-24', 'PAGADA'),
(5, 1, 5, '2026-02-25', 319499.00, 254911.00, 574410.00, 13459496.00, 574410.00, '2026-02-23', 'PAGADA'),
(6, 1, 6, '2026-03-25', 325409.00, 249001.00, 574410.00, 13134087.00, 574410.00, '2026-03-22', 'PAGADA'),
(7, 1, 7, '2026-04-25', 331429.00, 242981.00, 574410.00, 12802658.00, 574410.00, '2026-04-21', 'PAGADA'),
(8, 1, 8, '2026-05-25', 337561.00, 236849.00, 574410.00, 12465097.00, 574410.00, '2026-05-24', 'PAGADA'),
(9, 1, 9, '2026-06-25', 343806.00, 230604.00, 574410.00, 12121291.00, 574410.00, '2026-06-23', 'PAGADA'),
(10, 1, 10, '2026-07-25', 350166.00, 224244.00, 574410.00, 11771125.00, 574410.00, '2026-07-22', 'PAGADA'),
(11, 1, 11, '2026-08-25', 356644.00, 217766.00, 574410.00, 11414481.00, 574410.00, '2026-08-21', 'PAGADA'),
(12, 1, 12, '2026-09-25', 363242.00, 211168.00, 574410.00, 11051239.00, 574410.00, '2026-09-24', 'PAGADA'),
(13, 1, 13, '2026-10-25', 369962.00, 204448.00, 574410.00, 10681277.00, 0.00, NULL, 'PENDIENTE'),
(14, 1, 14, '2026-11-25', 376806.00, 197604.00, 574410.00, 10304471.00, 0.00, NULL, 'PENDIENTE'),
(15, 1, 15, '2026-12-25', 383777.00, 190633.00, 574410.00, 9920694.00, 0.00, NULL, 'PENDIENTE'),
(16, 1, 16, '2027-01-25', 390877.00, 183533.00, 574410.00, 9529817.00, 0.00, NULL, 'PENDIENTE'),
(17, 1, 17, '2027-02-25', 398108.00, 176302.00, 574410.00, 9131709.00, 0.00, NULL, 'PENDIENTE'),
(18, 1, 18, '2027-03-25', 405473.00, 168937.00, 574410.00, 8726236.00, 0.00, NULL, 'PENDIENTE'),
(19, 1, 19, '2027-04-25', 412975.00, 161435.00, 574410.00, 8313261.00, 0.00, NULL, 'PENDIENTE'),
(20, 1, 20, '2027-05-25', 420615.00, 153795.00, 574410.00, 7892646.00, 0.00, NULL, 'PENDIENTE'),
(21, 1, 21, '2027-06-25', 428396.00, 146014.00, 574410.00, 7464250.00, 0.00, NULL, 'PENDIENTE'),
(22, 1, 22, '2027-07-25', 436321.00, 138089.00, 574410.00, 7027929.00, 0.00, NULL, 'PENDIENTE'),
(23, 1, 23, '2027-08-25', 444393.00, 130017.00, 574410.00, 6583536.00, 0.00, NULL, 'PENDIENTE'),
(24, 1, 24, '2027-09-25', 452615.00, 121795.00, 574410.00, 6130921.00, 0.00, NULL, 'PENDIENTE'),
(25, 1, 25, '2027-10-25', 460988.00, 113422.00, 574410.00, 5669933.00, 0.00, NULL, 'PENDIENTE'),
(26, 1, 26, '2027-11-25', 469516.00, 104894.00, 574410.00, 5200417.00, 0.00, NULL, 'PENDIENTE'),
(27, 1, 27, '2027-12-25', 478202.00, 96208.00, 574410.00, 4722215.00, 0.00, NULL, 'PENDIENTE'),
(28, 1, 28, '2028-01-25', 487049.00, 87361.00, 574410.00, 4235166.00, 0.00, NULL, 'PENDIENTE'),
(29, 1, 29, '2028-02-25', 496059.00, 78351.00, 574410.00, 3739107.00, 0.00, NULL, 'PENDIENTE'),
(30, 1, 30, '2028-03-25', 505237.00, 69173.00, 574410.00, 3233870.00, 0.00, NULL, 'PENDIENTE'),
(31, 1, 31, '2028-04-25', 514583.00, 59827.00, 574410.00, 2719287.00, 0.00, NULL, 'PENDIENTE'),
(32, 1, 32, '2028-05-25', 524103.00, 50307.00, 574410.00, 2195184.00, 0.00, NULL, 'PENDIENTE'),
(33, 1, 33, '2028-06-25', 533799.00, 40611.00, 574410.00, 1661385.00, 0.00, NULL, 'PENDIENTE'),
(34, 1, 34, '2028-07-25', 543674.00, 30736.00, 574410.00, 1117711.00, 0.00, NULL, 'PENDIENTE'),
(35, 1, 35, '2028-08-25', 553732.00, 20678.00, 574410.00, 563979.00, 0.00, NULL, 'PENDIENTE'),
(36, 1, 36, '2028-09-25', 563979.00, 10434.00, 574413.00, 0.00, 0.00, NULL, 'PENDIENTE'),
(37, 2, 1, '2025-06-10', 431322.00, 418000.00, 849322.00, 21568678.00, 849322.00, '2025-06-08', 'PAGADA'),
(38, 2, 2, '2025-07-10', 439517.00, 409805.00, 849322.00, 21129161.00, 849322.00, '2025-07-07', 'PAGADA'),
(39, 2, 3, '2025-08-10', 447868.00, 401454.00, 849322.00, 20681293.00, 849322.00, '2025-08-06', 'PAGADA'),
(40, 2, 4, '2025-09-10', 456377.00, 392945.00, 849322.00, 20224916.00, 849322.00, '2025-09-09', 'PAGADA'),
(41, 2, 5, '2025-10-10', 465049.00, 384273.00, 849322.00, 19759867.00, 849322.00, '2025-10-14', 'PAGADA'),
(42, 2, 6, '2025-11-10', 473885.00, 375437.00, 849322.00, 19285982.00, 849322.00, '2025-11-07', 'PAGADA'),
(43, 2, 7, '2025-12-10', 482888.00, 366434.00, 849322.00, 18803094.00, 849322.00, '2025-12-06', 'PAGADA'),
(44, 2, 8, '2026-01-10', 492063.00, 357259.00, 849322.00, 18311031.00, 849322.00, '2026-01-09', 'PAGADA'),
(45, 2, 9, '2026-02-10', 501412.00, 347910.00, 849322.00, 17809619.00, 849322.00, '2026-02-08', 'PAGADA'),
(46, 2, 10, '2026-03-10', 510939.00, 338383.00, 849322.00, 17298680.00, 849322.00, '2026-03-14', 'PAGADA'),
(47, 2, 11, '2026-04-10', 520647.00, 328675.00, 849322.00, 16778033.00, 849322.00, '2026-04-06', 'PAGADA'),
(48, 2, 12, '2026-05-10', 530539.00, 318783.00, 849322.00, 16247494.00, 849322.00, '2026-05-09', 'PAGADA'),
(49, 2, 13, '2026-06-10', 540620.00, 308702.00, 849322.00, 15706874.00, 849322.00, '2026-06-08', 'PAGADA'),
(50, 2, 14, '2026-07-10', 550891.00, 298431.00, 849322.00, 15155983.00, 849322.00, '2026-07-07', 'PAGADA'),
(51, 2, 15, '2026-08-10', 561358.00, 287964.00, 849322.00, 14594625.00, 0.00, NULL, 'PENDIENTE'),
(52, 2, 16, '2026-09-10', 572024.00, 277298.00, 849322.00, 14022601.00, 0.00, NULL, 'PENDIENTE'),
(53, 2, 17, '2026-10-10', 582893.00, 266429.00, 849322.00, 13439708.00, 0.00, NULL, 'PENDIENTE'),
(54, 2, 18, '2026-11-10', 593968.00, 255354.00, 849322.00, 12845740.00, 0.00, NULL, 'PENDIENTE'),
(55, 2, 19, '2026-12-10', 605253.00, 244069.00, 849322.00, 12240487.00, 0.00, NULL, 'PENDIENTE'),
(56, 2, 20, '2027-01-10', 616753.00, 232569.00, 849322.00, 11623734.00, 0.00, NULL, 'PENDIENTE'),
(57, 2, 21, '2027-02-10', 628471.00, 220851.00, 849322.00, 10995263.00, 0.00, NULL, 'PENDIENTE'),
(58, 2, 22, '2027-03-10', 640412.00, 208910.00, 849322.00, 10354851.00, 0.00, NULL, 'PENDIENTE'),
(59, 2, 23, '2027-04-10', 652580.00, 196742.00, 849322.00, 9702271.00, 0.00, NULL, 'PENDIENTE'),
(60, 2, 24, '2027-05-10', 664979.00, 184343.00, 849322.00, 9037292.00, 0.00, NULL, 'PENDIENTE'),
(61, 2, 25, '2027-06-10', 677613.00, 171709.00, 849322.00, 8359679.00, 0.00, NULL, 'PENDIENTE'),
(62, 2, 26, '2027-07-10', 690488.00, 158834.00, 849322.00, 7669191.00, 0.00, NULL, 'PENDIENTE'),
(63, 2, 27, '2027-08-10', 703607.00, 145715.00, 849322.00, 6965584.00, 0.00, NULL, 'PENDIENTE'),
(64, 2, 28, '2027-09-10', 716976.00, 132346.00, 849322.00, 6248608.00, 0.00, NULL, 'PENDIENTE'),
(65, 2, 29, '2027-10-10', 730598.00, 118724.00, 849322.00, 5518010.00, 0.00, NULL, 'PENDIENTE'),
(66, 2, 30, '2027-11-10', 744480.00, 104842.00, 849322.00, 4773530.00, 0.00, NULL, 'PENDIENTE'),
(67, 2, 31, '2027-12-10', 758625.00, 90697.00, 849322.00, 4014905.00, 0.00, NULL, 'PENDIENTE'),
(68, 2, 32, '2028-01-10', 773039.00, 76283.00, 849322.00, 3241866.00, 0.00, NULL, 'PENDIENTE'),
(69, 2, 33, '2028-02-10', 787727.00, 61595.00, 849322.00, 2454139.00, 0.00, NULL, 'PENDIENTE'),
(70, 2, 34, '2028-03-10', 802693.00, 46629.00, 849322.00, 1651446.00, 0.00, NULL, 'PENDIENTE'),
(71, 2, 35, '2028-04-10', 817945.00, 31377.00, 849322.00, 833501.00, 0.00, NULL, 'PENDIENTE'),
(72, 2, 36, '2028-05-10', 833501.00, 15837.00, 849338.00, 0.00, 0.00, NULL, 'PENDIENTE'),
(73, 3, 1, '2025-09-05', 754020.00, 180000.00, 934020.00, 9245980.00, 934020.00, '2025-09-03', 'PAGADA'),
(74, 3, 2, '2025-10-05', 767592.00, 166428.00, 934020.00, 8478388.00, 934020.00, '2025-10-02', 'PAGADA'),
(75, 3, 3, '2025-11-05', 781409.00, 152611.00, 934020.00, 7696979.00, 934020.00, '2025-11-01', 'PAGADA'),
(76, 3, 4, '2025-12-05', 795474.00, 138546.00, 934020.00, 6901505.00, 934020.00, '2025-12-04', 'PAGADA'),
(77, 3, 5, '2026-01-05', 809793.00, 124227.00, 934020.00, 6091712.00, 934020.00, '2026-01-03', 'PAGADA'),
(78, 3, 6, '2026-02-05', 824369.00, 109651.00, 934020.00, 5267343.00, 934020.00, '2026-02-02', 'PAGADA'),
(79, 3, 7, '2026-03-05', 839208.00, 94812.00, 934020.00, 4428135.00, 934020.00, '2026-03-01', 'PAGADA'),
(80, 3, 8, '2026-04-05', 854314.00, 79706.00, 934020.00, 3573821.00, 934020.00, '2026-04-04', 'PAGADA'),
(81, 3, 9, '2026-05-05', 869691.00, 64329.00, 934020.00, 2704130.00, 934020.00, '2026-05-03', 'PAGADA'),
(82, 3, 10, '2026-06-05', 885346.00, 48674.00, 934020.00, 1818784.00, 934020.00, '2026-06-02', 'PAGADA'),
(83, 3, 11, '2026-07-05', 901282.00, 32738.00, 934020.00, 917502.00, 934020.00, '2026-07-01', 'PAGADA'),
(84, 3, 12, '2026-08-05', 917502.00, 16515.00, 934017.00, 0.00, 934017.00, '2026-08-04', 'PAGADA'),
(85, 4, 1, '2026-10-18', 401724.00, 222000.00, 623724.00, 11598276.00, 0.00, NULL, 'PENDIENTE'),
(86, 4, 2, '2026-11-18', 409156.00, 214568.00, 623724.00, 11189120.00, 0.00, NULL, 'PENDIENTE'),
(87, 4, 3, '2026-12-18', 416725.00, 206999.00, 623724.00, 10772395.00, 0.00, NULL, 'PENDIENTE'),
(88, 4, 4, '2027-01-18', 424435.00, 199289.00, 623724.00, 10347960.00, 0.00, NULL, 'PENDIENTE'),
(89, 4, 5, '2027-02-18', 432287.00, 191437.00, 623724.00, 9915673.00, 0.00, NULL, 'PENDIENTE'),
(90, 4, 6, '2027-03-18', 440284.00, 183440.00, 623724.00, 9475389.00, 0.00, NULL, 'PENDIENTE'),
(91, 4, 7, '2027-04-18', 448429.00, 175295.00, 623724.00, 9026960.00, 0.00, NULL, 'PENDIENTE'),
(92, 4, 8, '2027-05-18', 456725.00, 166999.00, 623724.00, 8570235.00, 0.00, NULL, 'PENDIENTE'),
(93, 4, 9, '2027-06-18', 465175.00, 158549.00, 623724.00, 8105060.00, 0.00, NULL, 'PENDIENTE'),
(94, 4, 10, '2027-07-18', 473780.00, 149944.00, 623724.00, 7631280.00, 0.00, NULL, 'PENDIENTE'),
(95, 4, 11, '2027-08-18', 482545.00, 141179.00, 623724.00, 7148735.00, 0.00, NULL, 'PENDIENTE'),
(96, 4, 12, '2027-09-18', 491472.00, 132252.00, 623724.00, 6657263.00, 0.00, NULL, 'PENDIENTE'),
(97, 4, 13, '2027-10-18', 500565.00, 123159.00, 623724.00, 6156698.00, 0.00, NULL, 'PENDIENTE'),
(98, 4, 14, '2027-11-18', 509825.00, 113899.00, 623724.00, 5646873.00, 0.00, NULL, 'PENDIENTE'),
(99, 4, 15, '2027-12-18', 519257.00, 104467.00, 623724.00, 5127616.00, 0.00, NULL, 'PENDIENTE'),
(100, 4, 16, '2028-01-18', 528863.00, 94861.00, 623724.00, 4598753.00, 0.00, NULL, 'PENDIENTE'),
(101, 4, 17, '2028-02-18', 538647.00, 85077.00, 623724.00, 4060106.00, 0.00, NULL, 'PENDIENTE'),
(102, 4, 18, '2028-03-18', 548612.00, 75112.00, 623724.00, 3511494.00, 0.00, NULL, 'PENDIENTE'),
(103, 4, 19, '2028-04-18', 558761.00, 64963.00, 623724.00, 2952733.00, 0.00, NULL, 'PENDIENTE'),
(104, 4, 20, '2028-05-18', 569098.00, 54626.00, 623724.00, 2383635.00, 0.00, NULL, 'PENDIENTE'),
(105, 4, 21, '2028-06-18', 579627.00, 44097.00, 623724.00, 1804008.00, 0.00, NULL, 'PENDIENTE'),
(106, 4, 22, '2028-07-18', 590350.00, 33374.00, 623724.00, 1213658.00, 0.00, NULL, 'PENDIENTE'),
(107, 4, 23, '2028-08-18', 601271.00, 22453.00, 623724.00, 612387.00, 0.00, NULL, 'PENDIENTE'),
(108, 4, 24, '2028-09-18', 612387.00, 11329.00, 623716.00, 0.00, 0.00, NULL, 'PENDIENTE'),
(109, 5, 1, '2024-12-15', 376937.00, 152000.00, 528937.00, 7623063.00, 528937.00, '2024-12-13', 'PAGADA'),
(110, 5, 2, '2025-01-15', 384099.00, 144838.00, 528937.00, 7238964.00, 528937.00, '2025-01-12', 'PAGADA'),
(111, 5, 3, '2025-02-15', 391397.00, 137540.00, 528937.00, 6847567.00, 528937.00, '2025-02-11', 'PAGADA'),
(112, 5, 4, '2025-03-15', 398833.00, 130104.00, 528937.00, 6448734.00, 528937.00, '2025-03-14', 'PAGADA'),
(113, 5, 5, '2025-04-15', 406411.00, 122526.00, 528937.00, 6042323.00, 528937.00, '2025-04-13', 'PAGADA'),
(114, 5, 6, '2025-05-15', 414133.00, 114804.00, 528937.00, 5628190.00, 528937.00, '2025-05-12', 'PAGADA'),
(115, 5, 7, '2025-06-15', 422001.00, 106936.00, 528937.00, 5206189.00, 528937.00, '2025-06-11', 'PAGADA'),
(116, 5, 8, '2025-07-15', 430019.00, 98918.00, 528937.00, 4776170.00, 528937.00, '2025-07-14', 'PAGADA'),
(117, 5, 9, '2025-08-15', 438190.00, 90747.00, 528937.00, 4337980.00, 528937.00, '2025-08-13', 'PAGADA'),
(118, 5, 10, '2025-09-15', 446515.00, 82422.00, 528937.00, 3891465.00, 528937.00, '2025-09-12', 'PAGADA'),
(119, 5, 11, '2025-10-15', 454999.00, 73938.00, 528937.00, 3436466.00, 528937.00, '2025-10-11', 'PAGADA'),
(120, 5, 12, '2025-11-15', 463644.00, 65293.00, 528937.00, 2972822.00, 528937.00, '2025-11-14', 'PAGADA'),
(121, 5, 13, '2025-12-15', 472453.00, 56484.00, 528937.00, 2500369.00, 528937.00, '2025-12-13', 'PAGADA'),
(122, 5, 14, '2026-01-15', 481430.00, 47507.00, 528937.00, 2018939.00, 528937.00, '2026-01-12', 'PAGADA'),
(123, 5, 15, '2026-02-15', 490577.00, 38360.00, 528937.00, 1528362.00, 528937.00, '2026-02-11', 'PAGADA'),
(124, 5, 16, '2026-03-15', 499898.00, 29039.00, 528937.00, 1028464.00, 528937.00, '2026-03-14', 'PAGADA'),
(125, 5, 17, '2026-04-15', 509396.00, 19541.00, 528937.00, 519068.00, 528937.00, '2026-04-13', 'PAGADA'),
(126, 5, 18, '2026-05-15', 519068.00, 9862.00, 528930.00, 0.00, 528930.00, '2026-05-12', 'PAGADA'),
(127, 6, 1, '2025-04-12', 257322.00, 240500.00, 497822.00, 12742678.00, 497822.00, '2025-04-10', 'PAGADA'),
(128, 6, 2, '2025-05-12', 262082.00, 235740.00, 497822.00, 12480596.00, 497822.00, '2025-05-09', 'PAGADA'),
(129, 6, 3, '2025-06-12', 266931.00, 230891.00, 497822.00, 12213665.00, 497822.00, '2025-06-08', 'PAGADA'),
(130, 6, 4, '2025-07-12', 271869.00, 225953.00, 497822.00, 11941796.00, 497822.00, '2025-07-11', 'PAGADA'),
(131, 6, 5, '2025-08-12', 276899.00, 220923.00, 497822.00, 11664897.00, 497822.00, '2025-08-10', 'PAGADA'),
(132, 6, 6, '2025-09-12', 282021.00, 215801.00, 497822.00, 11382876.00, 497822.00, '2025-09-09', 'PAGADA'),
(133, 6, 7, '2025-10-12', 287239.00, 210583.00, 497822.00, 11095637.00, 497822.00, '2025-10-08', 'PAGADA'),
(134, 6, 8, '2025-11-12', 292553.00, 205269.00, 497822.00, 10803084.00, 497822.00, '2025-11-11', 'PAGADA'),
(135, 6, 9, '2025-12-12', 297965.00, 199857.00, 497822.00, 10505119.00, 497822.00, '2025-12-10', 'PAGADA'),
(136, 6, 10, '2026-01-12', 303477.00, 194345.00, 497822.00, 10201642.00, 497822.00, '2026-01-09', 'PAGADA'),
(137, 6, 11, '2026-02-12', 309092.00, 188730.00, 497822.00, 9892550.00, 497822.00, '2026-02-08', 'PAGADA'),
(138, 6, 12, '2026-03-12', 314810.00, 183012.00, 497822.00, 9577740.00, 497822.00, '2026-03-11', 'PAGADA'),
(139, 6, 13, '2026-04-12', 320634.00, 177188.00, 497822.00, 9257106.00, 497822.00, '2026-04-10', 'PAGADA'),
(140, 6, 14, '2026-05-12', 326566.00, 171256.00, 497822.00, 8930540.00, 497822.00, '2026-05-09', 'PAGADA'),
(141, 6, 15, '2026-06-12', 332607.00, 165215.00, 497822.00, 8597933.00, 497822.00, '2026-06-08', 'PAGADA'),
(142, 6, 16, '2026-07-12', 338760.00, 159062.00, 497822.00, 8259173.00, 497822.00, '2026-07-11', 'PAGADA'),
(143, 6, 17, '2026-08-12', 345027.00, 152795.00, 497822.00, 7914146.00, 497822.00, '2026-08-10', 'PAGADA'),
(144, 6, 18, '2026-09-12', 351410.00, 146412.00, 497822.00, 7562736.00, 497822.00, '2026-09-09', 'PAGADA'),
(145, 6, 19, '2026-10-12', 357911.00, 139911.00, 497822.00, 7204825.00, 497822.00, '2026-10-08', 'PAGADA'),
(146, 6, 20, '2026-11-12', 364533.00, 133289.00, 497822.00, 6840292.00, 497822.00, '2026-10-14', 'PAGADA'),
(147, 6, 21, '2026-12-12', 371277.00, 126545.00, 497822.00, 6469015.00, 0.00, NULL, 'PENDIENTE'),
(148, 6, 22, '2027-01-12', 378145.00, 119677.00, 497822.00, 6090870.00, 0.00, NULL, 'PENDIENTE'),
(149, 6, 23, '2027-02-12', 385141.00, 112681.00, 497822.00, 5705729.00, 0.00, NULL, 'PENDIENTE'),
(150, 6, 24, '2027-03-12', 392266.00, 105556.00, 497822.00, 5313463.00, 0.00, NULL, 'PENDIENTE'),
(151, 6, 25, '2027-04-12', 399523.00, 98299.00, 497822.00, 4913940.00, 0.00, NULL, 'PENDIENTE'),
(152, 6, 26, '2027-05-12', 406914.00, 90908.00, 497822.00, 4507026.00, 0.00, NULL, 'PENDIENTE'),
(153, 6, 27, '2027-06-12', 414442.00, 83380.00, 497822.00, 4092584.00, 0.00, NULL, 'PENDIENTE'),
(154, 6, 28, '2027-07-12', 422109.00, 75713.00, 497822.00, 3670475.00, 0.00, NULL, 'PENDIENTE'),
(155, 6, 29, '2027-08-12', 429918.00, 67904.00, 497822.00, 3240557.00, 0.00, NULL, 'PENDIENTE'),
(156, 6, 30, '2027-09-12', 437872.00, 59950.00, 497822.00, 2802685.00, 0.00, NULL, 'PENDIENTE'),
(157, 6, 31, '2027-10-12', 445972.00, 51850.00, 497822.00, 2356713.00, 0.00, NULL, 'PENDIENTE'),
(158, 6, 32, '2027-11-12', 454223.00, 43599.00, 497822.00, 1902490.00, 0.00, NULL, 'PENDIENTE'),
(159, 6, 33, '2027-12-12', 462626.00, 35196.00, 497822.00, 1439864.00, 0.00, NULL, 'PENDIENTE'),
(160, 6, 34, '2028-01-12', 471185.00, 26637.00, 497822.00, 968679.00, 0.00, NULL, 'PENDIENTE'),
(161, 6, 35, '2028-02-12', 479901.00, 17921.00, 497822.00, 488778.00, 0.00, NULL, 'PENDIENTE'),
(162, 6, 36, '2028-03-12', 488778.00, 9042.00, 497820.00, 0.00, 0.00, NULL, 'PENDIENTE');

-- ---------------------------------------------------------------------
-- PAGOS (simulados) y su aplicacion a cuotas
-- ---------------------------------------------------------------------
INSERT INTO pago (id, credito_id, referencia, fecha_pago, valor, medio_pago, canal, estado, observacion, registrado_por) VALUES
(1, 1, 'PAG-2025-000001', '2025-10-23 09:07:00', 574410.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(2, 1, 'PAG-2025-000002', '2025-11-22 10:14:00', 574410.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(3, 1, 'PAG-2025-000003', '2025-12-21 11:21:00', 574410.00, 'EFECTIVO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(4, 1, 'PAG-2026-000004', '2026-01-24 12:28:00', 574410.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(5, 1, 'PAG-2026-000005', '2026-02-23 13:35:00', 574410.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(6, 1, 'PAG-2026-000006', '2026-03-22 14:42:00', 574410.00, 'TRANSFERENCIA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(7, 1, 'PAG-2026-000007', '2026-04-21 15:49:00', 574410.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(8, 1, 'PAG-2026-000008', '2026-05-24 16:56:00', 574410.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(9, 1, 'PAG-2026-000009', '2026-06-23 17:03:00', 574410.00, 'TARJETA_SIMULADA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(10, 1, 'PAG-2026-000010', '2026-07-22 08:10:00', 574410.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(11, 1, 'PAG-2026-000011', '2026-08-21 09:17:00', 574410.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(12, 1, 'PAG-2026-000012', '2026-09-24 10:24:00', 574410.00, 'CONSIGNACION', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(13, 2, 'PAG-2025-000013', '2025-06-08 11:31:00', 849322.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(14, 2, 'PAG-2025-000014', '2025-07-07 12:38:00', 849322.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(15, 2, 'PAG-2025-000015', '2025-08-06 13:45:00', 849322.00, 'PSE_SIMULADO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(16, 2, 'PAG-2025-000016', '2025-09-09 14:52:00', 849322.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(17, 2, 'PAG-2025-000017', '2025-10-14 15:59:00', 849322.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(18, 2, 'PAG-2025-000018', '2025-11-07 16:06:00', 849322.00, 'EFECTIVO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(19, 2, 'PAG-2025-000019', '2025-12-06 17:13:00', 849322.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(20, 2, 'PAG-2026-000020', '2026-01-09 08:20:00', 849322.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(21, 2, 'PAG-2026-000021', '2026-02-08 09:27:00', 849322.00, 'TRANSFERENCIA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(22, 2, 'PAG-2026-000022', '2026-03-14 10:34:00', 849322.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(23, 2, 'PAG-2026-000023', '2026-04-06 11:41:00', 849322.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(24, 2, 'PAG-2026-000024', '2026-05-09 12:48:00', 849322.00, 'TARJETA_SIMULADA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(25, 2, 'PAG-2026-000025', '2026-06-08 13:55:00', 849322.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(26, 2, 'PAG-2026-000026', '2026-07-07 14:02:00', 849322.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(27, 3, 'PAG-2025-000027', '2025-09-03 15:09:00', 934020.00, 'CONSIGNACION', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(28, 3, 'PAG-2025-000028', '2025-10-02 16:16:00', 934020.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(29, 3, 'PAG-2025-000029', '2025-11-01 17:23:00', 934020.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(30, 3, 'PAG-2025-000030', '2025-12-04 08:30:00', 934020.00, 'PSE_SIMULADO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(31, 3, 'PAG-2026-000031', '2026-01-03 09:37:00', 934020.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(32, 3, 'PAG-2026-000032', '2026-02-02 10:44:00', 934020.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(33, 3, 'PAG-2026-000033', '2026-03-01 11:51:00', 934020.00, 'EFECTIVO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(34, 3, 'PAG-2026-000034', '2026-04-04 12:58:00', 934020.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(35, 3, 'PAG-2026-000035', '2026-05-03 13:05:00', 934020.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(36, 3, 'PAG-2026-000036', '2026-06-02 14:12:00', 934020.00, 'TRANSFERENCIA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(37, 3, 'PAG-2026-000037', '2026-07-01 15:19:00', 934020.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(38, 3, 'PAG-2026-000038', '2026-08-04 16:26:00', 934017.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(39, 5, 'PAG-2024-000039', '2024-12-13 17:33:00', 528937.00, 'TARJETA_SIMULADA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(40, 5, 'PAG-2025-000040', '2025-01-12 08:40:00', 528937.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(41, 5, 'PAG-2025-000041', '2025-02-11 09:47:00', 528937.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(42, 5, 'PAG-2025-000042', '2025-03-14 10:54:00', 528937.00, 'CONSIGNACION', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(43, 5, 'PAG-2025-000043', '2025-04-13 11:01:00', 528937.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(44, 5, 'PAG-2025-000044', '2025-05-12 12:08:00', 528937.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(45, 5, 'PAG-2025-000045', '2025-06-11 13:15:00', 528937.00, 'PSE_SIMULADO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(46, 5, 'PAG-2025-000046', '2025-07-14 14:22:00', 528937.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(47, 5, 'PAG-2025-000047', '2025-08-13 15:29:00', 528937.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(48, 5, 'PAG-2025-000048', '2025-09-12 16:36:00', 528937.00, 'EFECTIVO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(49, 5, 'PAG-2025-000049', '2025-10-11 17:43:00', 528937.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(50, 5, 'PAG-2025-000050', '2025-11-14 08:50:00', 528937.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(51, 5, 'PAG-2025-000051', '2025-12-13 09:57:00', 528937.00, 'TRANSFERENCIA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(52, 5, 'PAG-2026-000052', '2026-01-12 10:04:00', 528937.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(53, 5, 'PAG-2026-000053', '2026-02-11 11:11:00', 528937.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(54, 5, 'PAG-2026-000054', '2026-03-14 12:18:00', 528937.00, 'TARJETA_SIMULADA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(55, 5, 'PAG-2026-000055', '2026-04-13 13:25:00', 528937.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(56, 5, 'PAG-2026-000056', '2026-05-12 14:32:00', 528930.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(57, 6, 'PAG-2025-000057', '2025-04-10 15:39:00', 497822.00, 'CONSIGNACION', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(58, 6, 'PAG-2025-000058', '2025-05-09 16:46:00', 497822.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(59, 6, 'PAG-2025-000059', '2025-06-08 17:53:00', 497822.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(60, 6, 'PAG-2025-000060', '2025-07-11 08:00:00', 497822.00, 'PSE_SIMULADO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(61, 6, 'PAG-2025-000061', '2025-08-10 09:07:00', 497822.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(62, 6, 'PAG-2025-000062', '2025-09-09 10:14:00', 497822.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(63, 6, 'PAG-2025-000063', '2025-10-08 11:21:00', 497822.00, 'EFECTIVO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(64, 6, 'PAG-2025-000064', '2025-11-11 12:28:00', 497822.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(65, 6, 'PAG-2025-000065', '2025-12-10 13:35:00', 497822.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(66, 6, 'PAG-2026-000066', '2026-01-09 14:42:00', 497822.00, 'TRANSFERENCIA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(67, 6, 'PAG-2026-000067', '2026-02-08 15:49:00', 497822.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(68, 6, 'PAG-2026-000068', '2026-03-11 16:56:00', 497822.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(69, 6, 'PAG-2026-000069', '2026-04-10 17:03:00', 497822.00, 'TARJETA_SIMULADA', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(70, 6, 'PAG-2026-000070', '2026-05-09 08:10:00', 497822.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(71, 6, 'PAG-2026-000071', '2026-06-08 09:17:00', 497822.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(72, 6, 'PAG-2026-000072', '2026-07-11 10:24:00', 497822.00, 'CONSIGNACION', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(73, 6, 'PAG-2026-000073', '2026-08-10 11:31:00', 497822.00, 'PSE_SIMULADO', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(74, 6, 'PAG-2026-000074', '2026-09-09 12:38:00', 497822.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL),
(75, 6, 'PAG-2026-000075', '2026-10-08 13:45:00', 497822.00, 'PSE_SIMULADO', 'ADMIN', 'APROBADO', 'Pago simulado - dato ficticio', 1),
(76, 6, 'PAG-2026-000076', '2026-10-14 14:52:00', 497822.00, 'TARJETA_SIMULADA', 'PORTAL', 'APROBADO', 'Pago simulado - dato ficticio', NULL);

INSERT INTO aplicacion_pago (pago_id, cuota_id, valor_aplicado) VALUES
(1, 1, 574410.00),
(2, 2, 574410.00),
(3, 3, 574410.00),
(4, 4, 574410.00),
(5, 5, 574410.00),
(6, 6, 574410.00),
(7, 7, 574410.00),
(8, 8, 574410.00),
(9, 9, 574410.00),
(10, 10, 574410.00),
(11, 11, 574410.00),
(12, 12, 574410.00),
(13, 37, 849322.00),
(14, 38, 849322.00),
(15, 39, 849322.00),
(16, 40, 849322.00),
(17, 41, 849322.00),
(18, 42, 849322.00),
(19, 43, 849322.00),
(20, 44, 849322.00),
(21, 45, 849322.00),
(22, 46, 849322.00),
(23, 47, 849322.00),
(24, 48, 849322.00),
(25, 49, 849322.00),
(26, 50, 849322.00),
(27, 73, 934020.00),
(28, 74, 934020.00),
(29, 75, 934020.00),
(30, 76, 934020.00),
(31, 77, 934020.00),
(32, 78, 934020.00),
(33, 79, 934020.00),
(34, 80, 934020.00),
(35, 81, 934020.00),
(36, 82, 934020.00),
(37, 83, 934020.00),
(38, 84, 934017.00),
(39, 109, 528937.00),
(40, 110, 528937.00),
(41, 111, 528937.00),
(42, 112, 528937.00),
(43, 113, 528937.00),
(44, 114, 528937.00),
(45, 115, 528937.00),
(46, 116, 528937.00),
(47, 117, 528937.00),
(48, 118, 528937.00),
(49, 119, 528937.00),
(50, 120, 528937.00),
(51, 121, 528937.00),
(52, 122, 528937.00),
(53, 123, 528937.00),
(54, 124, 528937.00),
(55, 125, 528937.00),
(56, 126, 528930.00),
(57, 127, 497822.00),
(58, 128, 497822.00),
(59, 129, 497822.00),
(60, 130, 497822.00),
(61, 131, 497822.00),
(62, 132, 497822.00),
(63, 133, 497822.00),
(64, 134, 497822.00),
(65, 135, 497822.00),
(66, 136, 497822.00),
(67, 137, 497822.00),
(68, 138, 497822.00),
(69, 139, 497822.00),
(70, 140, 497822.00),
(71, 141, 497822.00),
(72, 142, 497822.00),
(73, 143, 497822.00),
(74, 144, 497822.00),
(75, 145, 497822.00),
(76, 146, 497822.00);

-- ---------------------------------------------------------------------
-- PAZ Y SALVO (demostrativos, sin validez legal)
-- ---------------------------------------------------------------------
INSERT INTO paz_y_salvo (credito_id, codigo_verificacion, fecha_emision) VALUES
(3, 'PYS-2026-7F3A9C21', '2026-08-12 10:15:00'),
(5, 'PYS-2026-B41E06D8', '2026-05-20 15:40:00');

-- ---------------------------------------------------------------------
-- REQUISITOS DE LEVANTAMIENTO DE PRENDA (texto de EJEMPLO, editable desde el panel admin;
-- los requisitos reales los define la empresa)
-- ---------------------------------------------------------------------
INSERT INTO requisito_levantamiento (titulo, descripcion, orden, activo) VALUES
('Credito cancelado en su totalidad', 'El credito debe tener saldo cero y contar con el paz y salvo emitido por la entidad.', 1, TRUE),
('Solicitud de levantamiento', 'Radicar la solicitud de levantamiento de prenda a traves del portal o de los canales oficiales de atencion.', 2, TRUE),
('Documento de identidad del titular', 'Copia legible del documento de identidad del titular del credito.', 3, TRUE),
('Licencia de transito del vehiculo', 'Copia de la licencia de transito (tarjeta de propiedad) del vehiculo financiado.', 4, TRUE),
('Tramite ante el organismo de transito', 'Con el documento de levantamiento entregado por la entidad, el titular realiza la inscripcion del levantamiento ante el organismo de transito donde esta matriculado el vehiculo.', 5, TRUE);

-- ---------------------------------------------------------------------
-- SOLICITUDES DE LEVANTAMIENTO (simuladas)
-- ---------------------------------------------------------------------
INSERT INTO solicitud_levantamiento (numero_radicado, credito_id, fecha_solicitud, estado, observacion_cliente, respuesta_admin, fecha_respuesta, atendida_por) VALUES
('RAD-2026-000001', 5, '2026-05-21 09:30:00', 'APROBADA', 'Solicito el levantamiento de la prenda de mi motocicleta.', 'Documentos verificados. Levantamiento aprobado (dato ficticio).', '2026-05-28 14:00:00', 1),
('RAD-2026-000002', 3, '2026-08-13 11:05:00', 'EN_REVISION', 'Ya cancele el credito, solicito el levantamiento de la prenda.', NULL, NULL, NULL);

-- ---------------------------------------------------------------------
-- CANALES DE CONTACTO (valores de ejemplo, NO son los de la empresa)
-- ---------------------------------------------------------------------
INSERT INTO canal_contacto (tipo, nombre, valor, enlace, horario, orden, activo) VALUES
('WHATSAPP', 'WhatsApp de atencion', '+57 300 000 0000', 'https://wa.me/573000000000', 'Lunes a viernes 8:00 a. m. - 6:00 p. m.', 1, TRUE),
('TELEFONO', 'Linea de atencion', '(608) 000 0000', 'tel:+576080000000', 'Lunes a viernes 8:00 a. m. - 5:00 p. m.', 2, TRUE),
('CORREO', 'Correo de servicio al cliente', 'atencion@empresa-ejemplo.com', 'mailto:atencion@empresa-ejemplo.com', 'Respuesta en maximo 2 dias habiles', 3, TRUE),
('SITIO_WEB', 'Sitio web', 'www.empresa-ejemplo.com', 'https://www.example.com', NULL, 4, TRUE),
('OFICINA', 'Oficina principal', 'Calle 00 # 00-00, Neiva (direccion ficticia)', NULL, 'Lunes a viernes 8:00 a. m. - 12:00 m. y 2:00 - 5:00 p. m.', 5, TRUE);

-- ---------------------------------------------------------------------
-- AUDITORIA (algunos registros de ejemplo)
-- ---------------------------------------------------------------------
INSERT INTO auditoria (username, accion, entidad, entidad_id, detalle, direccion_ip, fecha) VALUES
('admin', 'CARGA_INICIAL', 'SISTEMA', NULL, 'Carga de datos ficticios para el prototipo', '127.0.0.1', '2026-10-17 08:00:00'),
('admin', 'RESPONDER_SOLICITUD', 'solicitud_levantamiento', '1', 'Solicitud RAD-2026-000001 aprobada', '127.0.0.1', '2026-05-28 14:00:00'),
('1075200303', 'CREAR_SOLICITUD', 'solicitud_levantamiento', '2', 'Solicitud RAD-2026-000002 radicada', '127.0.0.1', '2026-08-13 11:05:00');

COMMIT;

-- ---------------------------------------------------------------------
-- VERIFICACION: resumen de cada credito (cuotas pagadas y vencidas a hoy)
-- ---------------------------------------------------------------------
SELECT c.numero_credito,
       CONCAT(cl.nombres, ' ', cl.apellidos) AS cliente,
       c.estado,
       c.estado_prenda,
       c.saldo_capital,
       SUM(cu.estado = 'PAGADA') AS cuotas_pagadas,
       SUM(cu.estado <> 'PAGADA' AND cu.fecha_vencimiento < '2026-10-17') AS cuotas_vencidas,
       c.plazo_meses
FROM credito c
JOIN cliente cl ON cl.id = c.cliente_id
JOIN cuota cu ON cu.credito_id = c.id
GROUP BY c.id
ORDER BY c.id;
