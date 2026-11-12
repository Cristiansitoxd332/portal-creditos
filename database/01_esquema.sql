-- =====================================================================
--  PORTAL DE CONSULTA DE CREDITOS VEHICULARES - PROTOTIPO
--  Script 01: Esquema de la base de datos (estructura)
--  Motor: MySQL 8.4 LTS  |  Base de datos: portal_creditos
--
--  AVISO: este esquema es propio del prototipo y funciona con DATOS
--  FICTICIOS. No replica ni se conecta a ningun sistema de la empresa.
--
--  Uso (desde CMD, en C:\proyectos\portal-creditos):
--    mysql -u portal_user -p portal_creditos < database\01_esquema.sql
--
--  El script se puede ejecutar varias veces: borra y recrea las tablas.
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- Limpieza (permite volver a ejecutar el script desde cero)
-- ---------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS mensaje_cliente;
DROP TABLE IF EXISTS auditoria;
DROP TABLE IF EXISTS importacion_error;
DROP TABLE IF EXISTS importacion;
DROP TABLE IF EXISTS canal_contacto;
DROP TABLE IF EXISTS solicitud_levantamiento;
DROP TABLE IF EXISTS requisito_levantamiento;
DROP TABLE IF EXISTS paz_y_salvo;
DROP TABLE IF EXISTS aplicacion_pago;
DROP TABLE IF EXISTS pago;
DROP TABLE IF EXISTS cuota;
DROP TABLE IF EXISTS credito;
DROP TABLE IF EXISTS vehiculo;
DROP TABLE IF EXISTS usuario;
DROP TABLE IF EXISTS cliente;
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- 1. CLIENTE: personas titulares de los creditos
-- =====================================================================
CREATE TABLE cliente (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    tipo_documento       VARCHAR(5)   NOT NULL,
    numero_documento     VARCHAR(20)  NOT NULL,
    nombres              VARCHAR(100) NOT NULL,
    apellidos            VARCHAR(100) NOT NULL,
    correo               VARCHAR(150) NULL,
    telefono             VARCHAR(20)  NULL,
    direccion            VARCHAR(200) NULL,
    ciudad               VARCHAR(80)  NULL,
    fecha_creacion       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_cliente PRIMARY KEY (id),
    CONSTRAINT uk_cliente_documento UNIQUE (numero_documento),
    CONSTRAINT ck_cliente_tipo_documento CHECK (tipo_documento IN ('CC', 'CE', 'NIT', 'PAS'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 2. USUARIO: cuentas de acceso (clientes y administradores)
--    - Un CLIENTE tiene cliente_id; un ADMIN no.
--    - La contrasena se guarda cifrada con BCrypt, nunca en texto plano.
-- =====================================================================
CREATE TABLE usuario (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    username               VARCHAR(50)  NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    rol                    VARCHAR(20)  NOT NULL,
    cliente_id             BIGINT       NULL,
    activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
    debe_cambiar_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    intentos_fallidos      INT          NOT NULL DEFAULT 0,
    ultimo_acceso          DATETIME     NULL,
    fecha_creacion         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uk_usuario_username UNIQUE (username),
    CONSTRAINT uk_usuario_cliente UNIQUE (cliente_id),
    CONSTRAINT fk_usuario_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('CLIENTE', 'ADMIN')),
    CONSTRAINT ck_usuario_intentos CHECK (intentos_fallidos >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 3. VEHICULO: bien financiado y dado en prenda como garantia
-- =====================================================================
CREATE TABLE vehiculo (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    placa                VARCHAR(10)  NOT NULL,
    marca                VARCHAR(50)  NOT NULL,
    linea                VARCHAR(60)  NOT NULL,
    modelo               SMALLINT     NOT NULL,
    color                VARCHAR(30)  NULL,
    clase                VARCHAR(30)  NOT NULL,
    numero_motor         VARCHAR(40)  NULL,
    numero_chasis        VARCHAR(40)  NULL,
    fecha_creacion       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_vehiculo PRIMARY KEY (id),
    CONSTRAINT uk_vehiculo_placa UNIQUE (placa),
    CONSTRAINT ck_vehiculo_modelo CHECK (modelo BETWEEN 1990 AND 2100),
    CONSTRAINT ck_vehiculo_clase CHECK (clase IN ('AUTOMOVIL', 'CAMIONETA', 'CAMPERO', 'MOTOCICLETA', 'CAMION', 'OTRO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 4. CREDITO: obligacion financiera de un cliente sobre un vehiculo
--    - tasa_interes_mensual se expresa en porcentaje (1.5000 = 1.5 %)
--    - saldo_capital y estado se recalculan en el backend con cada pago
-- =====================================================================
CREATE TABLE credito (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    numero_credito        VARCHAR(20)    NOT NULL,
    cliente_id            BIGINT         NOT NULL,
    vehiculo_id           BIGINT         NOT NULL,
    valor_vehiculo        DECIMAL(15, 2) NOT NULL,
    cuota_inicial         DECIMAL(15, 2) NOT NULL DEFAULT 0,
    valor_financiado      DECIMAL(15, 2) NOT NULL,
    tasa_interes_mensual  DECIMAL(6, 4)  NOT NULL,
    plazo_meses           INT            NOT NULL,
    valor_cuota           DECIMAL(15, 2) NOT NULL,
    fecha_desembolso      DATE           NOT NULL,
    fecha_primera_cuota   DATE           NOT NULL,
    saldo_capital         DECIMAL(15, 2) NOT NULL,
    estado                VARCHAR(20)    NOT NULL DEFAULT 'VIGENTE',
    estado_prenda         VARCHAR(20)    NOT NULL DEFAULT 'VIGENTE',
    fecha_cancelacion     DATE           NULL,
    mora_cubierta_hasta   DATE           NULL COMMENT 'Fecha hasta la cual ya se pago la mora diaria',
    fecha_creacion        DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_credito PRIMARY KEY (id),
    CONSTRAINT uk_credito_numero UNIQUE (numero_credito),
    CONSTRAINT uk_credito_vehiculo UNIQUE (vehiculo_id),
    CONSTRAINT fk_credito_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_credito_vehiculo FOREIGN KEY (vehiculo_id) REFERENCES vehiculo (id),
    CONSTRAINT ck_credito_valores CHECK (valor_vehiculo > 0 AND cuota_inicial >= 0 AND valor_financiado > 0),
    CONSTRAINT ck_credito_tasa CHECK (tasa_interes_mensual >= 0 AND tasa_interes_mensual <= 10),
    CONSTRAINT ck_credito_plazo CHECK (plazo_meses BETWEEN 1 AND 120),
    CONSTRAINT ck_credito_saldo CHECK (saldo_capital >= 0),
    CONSTRAINT ck_credito_estado CHECK (estado IN ('VIGENTE', 'EN_MORA', 'CANCELADO')),
    CONSTRAINT ck_credito_estado_prenda CHECK (estado_prenda IN ('VIGENTE', 'EN_TRAMITE', 'LEVANTADA'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 5. CUOTA: plan de pagos (tabla de amortizacion) de cada credito
--    - El estado "VENCIDA" NO se guarda: se calcula en el backend
--      (cuota no pagada cuya fecha_vencimiento ya paso). Asi nunca
--      queda desactualizado.
-- =====================================================================
CREATE TABLE cuota (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    credito_id           BIGINT         NOT NULL,
    numero_cuota         INT            NOT NULL,
    fecha_vencimiento    DATE           NOT NULL,
    valor_capital        DECIMAL(15, 2) NOT NULL,
    valor_interes        DECIMAL(15, 2) NOT NULL,
    valor_cuota          DECIMAL(15, 2) NOT NULL,
    saldo_capital_final  DECIMAL(15, 2) NOT NULL,
    valor_pagado         DECIMAL(15, 2) NOT NULL DEFAULT 0,
    fecha_pago           DATE           NULL,
    estado               VARCHAR(20)    NOT NULL DEFAULT 'PENDIENTE',
    CONSTRAINT pk_cuota PRIMARY KEY (id),
    CONSTRAINT uk_cuota_credito_numero UNIQUE (credito_id, numero_cuota),
    CONSTRAINT fk_cuota_credito FOREIGN KEY (credito_id) REFERENCES credito (id) ON DELETE CASCADE,
    CONSTRAINT ck_cuota_numero CHECK (numero_cuota > 0),
    CONSTRAINT ck_cuota_valores CHECK (valor_capital >= 0 AND valor_interes >= 0 AND valor_cuota > 0 AND saldo_capital_final >= 0),
    CONSTRAINT ck_cuota_pagado CHECK (valor_pagado >= 0 AND valor_pagado <= valor_cuota),
    CONSTRAINT ck_cuota_estado CHECK (estado IN ('PENDIENTE', 'PARCIAL', 'PAGADA'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_cuota_estado_vencimiento ON cuota (estado, fecha_vencimiento);

-- =====================================================================
-- 6. PAGO: cada transaccion registrada (simulada en el prototipo)
-- =====================================================================
CREATE TABLE pago (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    credito_id      BIGINT         NOT NULL,
    referencia      VARCHAR(40)    NOT NULL,
    fecha_pago      DATETIME       NOT NULL,
    valor           DECIMAL(15, 2) NOT NULL,
    valor_mora      DECIMAL(15, 2) NOT NULL DEFAULT 0.00 COMMENT 'Parte del pago aplicada a mora por dias de atraso',
    medio_pago      VARCHAR(30)    NOT NULL,
    canal           VARCHAR(20)    NOT NULL,
    estado          VARCHAR(20)    NOT NULL DEFAULT 'APROBADO',
    observacion     VARCHAR(255)   NULL,
    registrado_por  BIGINT         NULL,
    fecha_creacion  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_pago PRIMARY KEY (id),
    CONSTRAINT uk_pago_referencia UNIQUE (referencia),
    CONSTRAINT fk_pago_credito FOREIGN KEY (credito_id) REFERENCES credito (id),
    CONSTRAINT fk_pago_usuario FOREIGN KEY (registrado_por) REFERENCES usuario (id),
    CONSTRAINT ck_pago_valor CHECK (valor > 0),
    CONSTRAINT ck_pago_medio CHECK (medio_pago IN ('PSE_SIMULADO', 'TARJETA_SIMULADA', 'EFECTIVO', 'TRANSFERENCIA', 'CONSIGNACION')),
    CONSTRAINT ck_pago_canal CHECK (canal IN ('PORTAL', 'ADMIN', 'IMPORTACION')),
    CONSTRAINT ck_pago_estado CHECK (estado IN ('APROBADO', 'RECHAZADO', 'PENDIENTE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_pago_credito_fecha ON pago (credito_id, fecha_pago);

-- =====================================================================
-- 7. APLICACION_PAGO: como se distribuye un pago entre las cuotas
--    Resuelve la relacion muchos-a-muchos: un pago puede cubrir varias
--    cuotas y una cuota puede pagarse con varios abonos.
-- =====================================================================
CREATE TABLE aplicacion_pago (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    pago_id         BIGINT         NOT NULL,
    cuota_id        BIGINT         NOT NULL,
    valor_aplicado  DECIMAL(15, 2) NOT NULL,
    CONSTRAINT pk_aplicacion_pago PRIMARY KEY (id),
    CONSTRAINT uk_aplicacion_pago_cuota UNIQUE (pago_id, cuota_id),
    CONSTRAINT fk_aplicacion_pago FOREIGN KEY (pago_id) REFERENCES pago (id) ON DELETE CASCADE,
    CONSTRAINT fk_aplicacion_cuota FOREIGN KEY (cuota_id) REFERENCES cuota (id),
    CONSTRAINT ck_aplicacion_valor CHECK (valor_aplicado > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 8. PAZ_Y_SALVO: certificados emitidos (demostrativos, sin validez legal)
--    El codigo_verificacion permite comprobar que un certificado existe.
-- =====================================================================
CREATE TABLE paz_y_salvo (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    credito_id           BIGINT       NOT NULL,
    codigo_verificacion  VARCHAR(40)  NOT NULL,
    fecha_emision        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_paz_y_salvo PRIMARY KEY (id),
    CONSTRAINT uk_paz_y_salvo_credito UNIQUE (credito_id),
    CONSTRAINT uk_paz_y_salvo_codigo UNIQUE (codigo_verificacion),
    CONSTRAINT fk_paz_y_salvo_credito FOREIGN KEY (credito_id) REFERENCES credito (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 9. REQUISITO_LEVANTAMIENTO: requisitos informativos (configurables)
-- =====================================================================
CREATE TABLE requisito_levantamiento (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    titulo       VARCHAR(150)  NOT NULL,
    descripcion  VARCHAR(500)  NOT NULL,
    orden        INT           NOT NULL DEFAULT 0,
    activo       BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_requisito_levantamiento PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 10. SOLICITUD_LEVANTAMIENTO: solicitudes simuladas de levantamiento
--     de prenda y su seguimiento
-- =====================================================================
CREATE TABLE solicitud_levantamiento (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    numero_radicado      VARCHAR(30)   NOT NULL,
    credito_id           BIGINT        NOT NULL,
    fecha_solicitud      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado               VARCHAR(20)   NOT NULL DEFAULT 'RADICADA',
    observacion_cliente  VARCHAR(500)  NULL,
    respuesta_admin      VARCHAR(500)  NULL,
    fecha_respuesta      DATETIME      NULL,
    atendida_por         BIGINT        NULL,
    valor_tramite        DECIMAL(15, 2) NULL COMMENT 'Costo del tramite pagado (simulado)',
    medio_pago_tramite   VARCHAR(20)   NULL COMMENT 'PSE, EFECTY o CONSIGNACION',
    referencia_pago_tramite VARCHAR(30) NULL,
    fecha_pago_tramite   DATETIME      NULL,
    cuenta_recaudo       VARCHAR(20)   NULL COMMENT 'Cuenta exclusiva de recaudo de tramites (no se mezcla con cuotas)',
    vigente_hasta        DATE          NULL COMMENT 'Vigencia del documento de levantamiento (30 dias desde la aprobacion)',
    CONSTRAINT pk_solicitud_levantamiento PRIMARY KEY (id),
    CONSTRAINT uk_solicitud_radicado UNIQUE (numero_radicado),
    CONSTRAINT fk_solicitud_credito FOREIGN KEY (credito_id) REFERENCES credito (id),
    CONSTRAINT fk_solicitud_usuario FOREIGN KEY (atendida_por) REFERENCES usuario (id),
    CONSTRAINT ck_solicitud_estado CHECK (estado IN ('RADICADA', 'EN_REVISION', 'APROBADA', 'RECHAZADA'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 11. CANAL_CONTACTO: canales de atencion (configurables desde el admin)
-- =====================================================================
CREATE TABLE canal_contacto (
    id       BIGINT        NOT NULL AUTO_INCREMENT,
    tipo     VARCHAR(20)   NOT NULL,
    nombre   VARCHAR(100)  NOT NULL,
    valor    VARCHAR(200)  NOT NULL,
    enlace   VARCHAR(300)  NULL,
    horario  VARCHAR(150)  NULL,
    orden    INT           NOT NULL DEFAULT 0,
    activo   BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_canal_contacto PRIMARY KEY (id),
    CONSTRAINT ck_canal_tipo CHECK (tipo IN ('WHATSAPP', 'TELEFONO', 'CORREO', 'SITIO_WEB', 'OFICINA', 'REDES_SOCIALES'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 12. IMPORTACION: historial de cargas de archivos Excel/CSV
-- =====================================================================
CREATE TABLE importacion (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    tipo_carga       VARCHAR(20)   NOT NULL,
    nombre_archivo   VARCHAR(255)  NOT NULL,
    fecha_carga      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_id       BIGINT        NOT NULL,
    total_filas      INT           NOT NULL DEFAULT 0,
    filas_exitosas   INT           NOT NULL DEFAULT 0,
    filas_con_error  INT           NOT NULL DEFAULT 0,
    estado           VARCHAR(30)   NOT NULL DEFAULT 'PROCESANDO',
    CONSTRAINT pk_importacion PRIMARY KEY (id),
    CONSTRAINT fk_importacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT ck_importacion_tipo CHECK (tipo_carga IN ('CLIENTES', 'VEHICULOS', 'CREDITOS', 'PAGOS')),
    CONSTRAINT ck_importacion_filas CHECK (total_filas >= 0 AND filas_exitosas >= 0 AND filas_con_error >= 0),
    CONSTRAINT ck_importacion_estado CHECK (estado IN ('PROCESANDO', 'COMPLETADA', 'COMPLETADA_CON_ERRORES', 'FALLIDA'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 13. IMPORTACION_ERROR: detalle de cada fila rechazada en una carga
-- =====================================================================
CREATE TABLE importacion_error (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    importacion_id  BIGINT        NOT NULL,
    numero_fila     INT           NOT NULL,
    campo           VARCHAR(60)   NULL,
    mensaje         VARCHAR(500)  NOT NULL,
    CONSTRAINT pk_importacion_error PRIMARY KEY (id),
    CONSTRAINT fk_importacion_error FOREIGN KEY (importacion_id) REFERENCES importacion (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =====================================================================
-- 14. AUDITORIA: bitacora de acciones relevantes
--     Guarda el username como texto (sin llave foranea) para que el
--     registro se conserve aunque el usuario se elimine.
-- =====================================================================
CREATE TABLE auditoria (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)    NULL,
    accion        VARCHAR(50)    NOT NULL,
    entidad       VARCHAR(50)    NULL,
    entidad_id    VARCHAR(50)    NULL,
    detalle       VARCHAR(1000)  NULL,
    direccion_ip  VARCHAR(45)    NULL,
    fecha         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_auditoria PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_auditoria_fecha ON auditoria (fecha);

-- =====================================================================
-- 15. MENSAJE_CLIENTE: recordatorios de pago y avisos de mora que la
--     entidad envia al cliente desde el panel administrativo. El cliente
--     los ve en su portal (seccion "Mensajes").
-- =====================================================================
CREATE TABLE mensaje_cliente (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    cliente_id     BIGINT        NOT NULL,
    credito_id     BIGINT        NULL,
    tipo           VARCHAR(20)   NOT NULL,
    titulo         VARCHAR(120)  NOT NULL,
    texto          VARCHAR(700)  NOT NULL,
    fecha_envio    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enviado_por    VARCHAR(50)   NOT NULL,
    leido          BOOLEAN       NOT NULL DEFAULT FALSE,
    fecha_lectura  DATETIME      NULL,
    CONSTRAINT pk_mensaje_cliente PRIMARY KEY (id),
    CONSTRAINT fk_mensaje_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id),
    CONSTRAINT fk_mensaje_credito FOREIGN KEY (credito_id) REFERENCES credito (id),
    CONSTRAINT ck_mensaje_tipo CHECK (tipo IN ('VENCE_HOY', 'RECORDATORIO', 'MORA', 'GENERAL'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_mensaje_cliente ON mensaje_cliente (cliente_id, fecha_envio);
CREATE INDEX ix_mensaje_credito_tipo ON mensaje_cliente (credito_id, tipo, fecha_envio);

-- Fin del script 01
