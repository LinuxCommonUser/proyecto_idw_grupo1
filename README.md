# Proyecto de Desarrollo Web Integrado - Sistema de Inventario El Meson

Aplicación académica con Java 21 o superior, Spring Boot 4.1.1 y SQL Server. El catálogo existente se conserva. El frontend se sirve desde Spring Boot y sigue la distribución y colores del prototipo Figma.

## Iniciar

Desde PowerShell, en la raíz del proyecto:

```powershell
cd D:\el-meson-inventario
.\mvnw.cmd spring-boot:run
```

Abre http://localhost:8080/. Si ya existe otro proceso en ese puerto, detén la ejecución anterior con Ctrl+C antes de arrancar. No abras index.html con Live Server.

En la primera visita se solicita crear el administrador inicial. No hay una contraseña de aplicación preestablecida. Esa contraseña es independiente de la cuenta SQL `meson_app`. Una vez creada la cuenta inicial, el formulario de configuración queda cerrado.

La configuración de conexión original se conserva en `src/main/resources/application-local.properties`, excluido de Git. El perfil local se carga por defecto. Para otra computadora, define DB_PASSWORD y configura su conexión en application.properties. No compartas application-local.properties ni .codex-backups.

## Instalación de las tablas

1. Ejecuta `database/01_catalogo.sql` si es una instalación nueva.
2. Ejecuta `database/02_sistema_completo.sql` en SSMS, con una cuenta administradora y sobre la misma instancia de la aplicación. Ambos scripts conservan las tablas existentes.
3. La cuenta meson_app necesita lectura y escritura; no necesita permisos de creación de tablas para operar.

## Uso inicial

1. Crea el administrador desde la pantalla de acceso e inicia sesión.
2. En Usuarios y sedes, crea los establecimientos reales.
3. Crea usuarios con rol ENCARGADO o CONSULTOR y asigna sus sedes.
4. Administra productos y verifica categorías y unidades existentes.
5. Selecciona una sede y registra entradas o stock inicial. El stock inicial se acepta como primer movimiento de la sede; los saldos posteriores se corrigen mediante ajustes con motivo.
6. Registra consumo como SALIDA y desperdicio como MERMA. No se descuentan recetas automáticamente.
7. Configura mínimos en Existencias y kardex.
8. Para un traslado, crea el borrador, despacha desde el origen y confirma la recepción total desde el destino. Entre despacho y recepción, la mercadería queda en tránsito.
9. Si existe una diferencia, registra una incidencia. Un administrador puede conciliarla y resolverla con motivo, o registrar la devolución física al origen.
10. Consulta y exporta reportes a CSV. La bitácora está disponible para administradores.

## Permisos

| Rol | Acceso |
|---|---|
| ADMIN | Usuarios, sedes, catálogo, ajustes, reversos, inventario y bitácora |
| ENCARGADO | Lectura y movimientos en sedes asignadas; despacho en origen autorizado y recepción en destino autorizado |
| CONSULTOR | Lectura, kardex y reportes de sus sedes; no registra operaciones |

Las autorizaciones se comprueban en el servidor. El catálogo de productos es compartido; los saldos están separados por sede. La lista pública de destinos dentro de una sesión solo contiene identificadores y nombres, sin existencias de sedes ajenas.

## Integridad

- Cada operación se confirma en una transacción. Si falla una línea, no se confirma ninguna.
- Una fila de control serializa las confirmaciones para evitar saldos negativos y duplicados en esta versión académica. No es una arquitectura de alta concurrencia.
- Las cantidades tienen hasta tres decimales. Los tipos de entrada suman y los de salida restan.
- Los movimientos no se editan ni borran. Los reversos están vinculados a la operación original.
- Los traslados no se revierten desde el módulo general: se gestionan mediante estados de transferencia.
- Los reintentos usan claves de idempotencia y las recepciones repetidas no suman existencias de nuevo.
- Inicio de sesión con sesión HTTP, contraseñas con BCrypt y protección CSRF.

## Estructura

- `domain`, `repository`, `dto`, `service/ProductoService`: catálogo existente con JPA.
- `dto/SistemaRequest`: contratos y validaciones de los módulos nuevos.
- `service/AccesoService`: roles, cuentas, asignaciones y sedes.
- `service/InventarioService`: consultas y transacciones con JdbcTemplate para inventario y transferencias.
- `config/SecurityConfig`: autenticación, autorización y actualización de permisos de sesiones.
- `controller/SistemaController`: API de los módulos nuevos.
- `static/login.html` y `static/js/login.js`: acceso y configuración inicial.
- `static/index.html`, `static/js/app.js` y `static/css/estilos.css`: interfaz integrada.
- `static/js/productos.js`: referencia del frontend original; la pantalla integrada utiliza app.js.
- `src/test`: pruebas con H2 en memoria, separadas de SQL Server y de los datos reales.

## Pruebas

```powershell
.\mvnw.cmd test
```

Se verifican autorización por sede y rol, sesión y CSRF, rollback de operaciones, salidas concurrentes, reintentos, reversos y despacho/recepción. Las pruebas no reemplazan la validación y recuperación de respaldos en el servidor productivo.

## Alcance de esta versión

Incluye el flujo principal de los ocho prototipos. La exportación es CSV, compatible con Excel; no genera archivos XLSX. Los catálogos de categorías y unidades se inicializan mediante SQL y se consultan desde la aplicación. No incluye contabilidad, facturación, recetas, lotes, vencimientos, recepción parcial, automatización de copias de seguridad ni indicadores económicos. El servidor escucha en localhost; el acceso real entre sedes exige configurar alojamiento, HTTPS, red y copias externas.
