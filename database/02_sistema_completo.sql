
USE el_meson_inventario;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF OBJECT_ID('dbo.control_operacion','U') IS NULL
CREATE TABLE dbo.control_operacion(id INT NOT NULL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0);
IF NOT EXISTS(SELECT 1 FROM dbo.control_operacion WHERE id=1)
INSERT INTO dbo.control_operacion(id,version) VALUES(1,0);

IF OBJECT_ID('dbo.sede','U') IS NULL
CREATE TABLE dbo.sede(
 id INT IDENTITY PRIMARY KEY, codigo NVARCHAR(20) NOT NULL UNIQUE,
 nombre NVARCHAR(100) NOT NULL, direccion NVARCHAR(250), activo BIT NOT NULL DEFAULT 1
);
IF OBJECT_ID('dbo.usuario','U') IS NULL
CREATE TABLE dbo.usuario(
 id BIGINT IDENTITY PRIMARY KEY, usuario NVARCHAR(50) NOT NULL UNIQUE,
 nombre NVARCHAR(150) NOT NULL, password_hash NVARCHAR(255) NOT NULL,
 rol NVARCHAR(20) NOT NULL CHECK(rol IN ('ADMIN','ENCARGADO','CONSULTOR')),
 activo BIT NOT NULL DEFAULT 1
);
IF OBJECT_ID('dbo.usuario_sede','U') IS NULL
CREATE TABLE dbo.usuario_sede(
 usuario_id BIGINT NOT NULL REFERENCES dbo.usuario(id),
 sede_id INT NOT NULL REFERENCES dbo.sede(id),
 PRIMARY KEY(usuario_id,sede_id)
);
IF OBJECT_ID('dbo.existencia','U') IS NULL
CREATE TABLE dbo.existencia(
 sede_id INT NOT NULL REFERENCES dbo.sede(id),
 producto_id BIGINT NOT NULL REFERENCES dbo.producto(id),
 cantidad DECIMAL(18,3) NOT NULL DEFAULT 0 CHECK(cantidad>=0),
 stock_minimo DECIMAL(18,3) NOT NULL DEFAULT 0 CHECK(stock_minimo>=0),
 PRIMARY KEY(sede_id,producto_id)
);
IF OBJECT_ID('dbo.transferencia','U') IS NULL
CREATE TABLE dbo.transferencia(
 id BIGINT IDENTITY PRIMARY KEY,
 origen_id INT NOT NULL REFERENCES dbo.sede(id), destino_id INT NOT NULL REFERENCES dbo.sede(id),
 creador_id BIGINT NOT NULL REFERENCES dbo.usuario(id),
 despachador_id BIGINT REFERENCES dbo.usuario(id), receptor_id BIGINT REFERENCES dbo.usuario(id),
 estado NVARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
 creada_en DATETIME2 NOT NULL DEFAULT SYSDATETIME(), despachada_en DATETIME2, recibida_en DATETIME2,
 incidencia NVARCHAR(300), idempotencia NVARCHAR(36) NOT NULL UNIQUE,
 CHECK(origen_id<>destino_id),
 CHECK(estado IN ('BORRADOR','DESPACHADA','RECIBIDA','CANCELADA','DEVUELTA'))
);
IF OBJECT_ID('dbo.detalle_transferencia','U') IS NULL
CREATE TABLE dbo.detalle_transferencia(
 id BIGINT IDENTITY PRIMARY KEY, transferencia_id BIGINT NOT NULL REFERENCES dbo.transferencia(id),
 producto_id BIGINT NOT NULL REFERENCES dbo.producto(id), cantidad DECIMAL(18,3) NOT NULL CHECK(cantidad>0),
 UNIQUE(transferencia_id,producto_id)
);
IF OBJECT_ID('dbo.movimiento','U') IS NULL
CREATE TABLE dbo.movimiento(
 id BIGINT IDENTITY PRIMARY KEY, sede_id INT NOT NULL REFERENCES dbo.sede(id),
 usuario_id BIGINT NOT NULL REFERENCES dbo.usuario(id),
 tipo NVARCHAR(30) NOT NULL, fecha_registro DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
 motivo NVARCHAR(300) NOT NULL, referencia NVARCHAR(100), proveedor NVARCHAR(150),
 reverso_de_id BIGINT REFERENCES dbo.movimiento(id),
 transferencia_id BIGINT REFERENCES dbo.transferencia(id), idempotencia NVARCHAR(36) NOT NULL UNIQUE,
 CHECK(tipo IN ('INICIAL','ENTRADA','SALIDA','MERMA','AJUSTE_ENTRADA','AJUSTE_SALIDA',
 'REVERSO_ENTRADA','REVERSO_SALIDA','TRANSFERENCIA_ENTRADA','TRANSFERENCIA_SALIDA'))
);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.movimiento') AND name='UQ_movimiento_reverso')
CREATE UNIQUE INDEX UQ_movimiento_reverso ON dbo.movimiento(reverso_de_id) WHERE reverso_de_id IS NOT NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.movimiento') AND name='IX_movimiento_sede_fecha')
CREATE INDEX IX_movimiento_sede_fecha ON dbo.movimiento(sede_id,fecha_registro);
IF OBJECT_ID('dbo.detalle_movimiento','U') IS NULL
CREATE TABLE dbo.detalle_movimiento(
 id BIGINT IDENTITY PRIMARY KEY, movimiento_id BIGINT NOT NULL REFERENCES dbo.movimiento(id),
 producto_id BIGINT NOT NULL REFERENCES dbo.producto(id), cantidad DECIMAL(18,3) NOT NULL CHECK(cantidad>0),
 costo_unitario DECIMAL(18,4) CHECK(costo_unitario>=0),
 UNIQUE(movimiento_id,producto_id)
);
IF OBJECT_ID('dbo.auditoria','U') IS NULL
CREATE TABLE dbo.auditoria(
 id BIGINT IDENTITY PRIMARY KEY, usuario_id BIGINT REFERENCES dbo.usuario(id),
 fecha DATETIME2 NOT NULL DEFAULT SYSDATETIME(), accion NVARCHAR(50) NOT NULL,
 entidad NVARCHAR(50) NOT NULL, entidad_id BIGINT, resultado NVARCHAR(20) NOT NULL,
 descripcion NVARCHAR(300)
);
COMMIT;
GO
SELECT name FROM sys.tables ORDER BY name;
GO