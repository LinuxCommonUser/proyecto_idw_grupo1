USE el_meson_inventario;
GO

IF OBJECT_ID(N'dbo.categoria', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.categoria (
        id INT IDENTITY(1,1) NOT NULL,
        nombre NVARCHAR(80) NOT NULL,
        activo BIT NOT NULL
            CONSTRAINT DF_categoria_activo DEFAULT 1,

        CONSTRAINT PK_categoria PRIMARY KEY (id),
        CONSTRAINT UQ_categoria_nombre UNIQUE (nombre)
    );
END;
GO

IF OBJECT_ID(N'dbo.unidad', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.unidad (
        id INT IDENTITY(1,1) NOT NULL,
        codigo NVARCHAR(10) NOT NULL,
        nombre NVARCHAR(40) NOT NULL,

        CONSTRAINT PK_unidad PRIMARY KEY (id),
        CONSTRAINT UQ_unidad_codigo UNIQUE (codigo)
    );
END;
GO

IF OBJECT_ID(N'dbo.producto', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.producto (
        id BIGINT IDENTITY(1,1) NOT NULL,
        codigo NVARCHAR(30) NOT NULL,
        nombre NVARCHAR(120) NOT NULL,
        categoria_id INT NOT NULL,
        unidad_id INT NOT NULL,
        activo BIT NOT NULL
            CONSTRAINT DF_producto_activo DEFAULT 1,

        CONSTRAINT PK_producto PRIMARY KEY (id),
        CONSTRAINT UQ_producto_codigo UNIQUE (codigo),

        CONSTRAINT FK_producto_categoria
            FOREIGN KEY (categoria_id)
            REFERENCES dbo.categoria(id),

        CONSTRAINT FK_producto_unidad
            FOREIGN KEY (unidad_id)
            REFERENCES dbo.unidad(id)
    );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM dbo.categoria WHERE nombre = N'Carnes'
)
    INSERT INTO dbo.categoria(nombre) VALUES (N'Carnes');

IF NOT EXISTS (
    SELECT 1 FROM dbo.categoria WHERE nombre = N'Verduras'
)
    INSERT INTO dbo.categoria(nombre) VALUES (N'Verduras');

IF NOT EXISTS (
    SELECT 1 FROM dbo.categoria WHERE nombre = N'Abarrotes'
)
    INSERT INTO dbo.categoria(nombre) VALUES (N'Abarrotes');

IF NOT EXISTS (
    SELECT 1 FROM dbo.categoria WHERE nombre = N'Bebidas'
)
    INSERT INTO dbo.categoria(nombre) VALUES (N'Bebidas');

IF NOT EXISTS (
    SELECT 1 FROM dbo.categoria WHERE nombre = N'Insumos'
)
    INSERT INTO dbo.categoria(nombre) VALUES (N'Insumos');
GO

IF NOT EXISTS (
    SELECT 1 FROM dbo.unidad WHERE codigo = N'kg'
)
    INSERT INTO dbo.unidad(codigo, nombre)
    VALUES (N'kg', N'Kilogramo');

IF NOT EXISTS (
    SELECT 1 FROM dbo.unidad WHERE codigo = N'L'
)
    INSERT INTO dbo.unidad(codigo, nombre)
    VALUES (N'L', N'Litro');

IF NOT EXISTS (
    SELECT 1 FROM dbo.unidad WHERE codigo = N'und'
)
    INSERT INTO dbo.unidad(codigo, nombre)
    VALUES (N'und', N'Unidad');
GO