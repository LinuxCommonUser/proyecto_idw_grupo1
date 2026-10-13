package com.proyecto.utp.proyecto_dwi.service;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.utp.proyecto_dwi.dto.SistemaRequest;
import static com.proyecto.utp.proyecto_dwi.service.AccesoService.error;

@Service
public class InventarioService {
    private final JdbcTemplate db;
    private final AccesoService acceso;

    public InventarioService(JdbcTemplate db, AccesoService acceso) {
        this.db = db;
        this.acceso = acceso;
    }

    static final Set<String> ENTRADAS = Set.of("INICIAL", "ENTRADA", "AJUSTE_ENTRADA", "REVERSO_ENTRADA",
            "TRANSFERENCIA_ENTRADA");

    static int signo(String tipo) {
        return ENTRADAS.contains(tipo) ? 1 : -1;
    }

    private Map<String, Object> fila(String tabla, long id) {
        var r = db.queryForList("SELECT * FROM dbo." + tabla + " WHERE id=?", id);
        if (r.isEmpty())
            throw error(HttpStatus.NOT_FOUND, "Registro no encontrado");
        return r.get(0);
    }

    private void producto(long id, boolean activo) {
        int n = db.queryForObject("SELECT COUNT(*) FROM dbo.producto WHERE id=?" + (activo ? " AND activo=1" : ""),
                Integer.class, id);
        if (n == 0)
            throw error(HttpStatus.CONFLICT, "El producto no existe o está desactivado");
    }

    private void existencia(int sede, long producto) {
        db.update(
                "INSERT INTO dbo.existencia(sede_id,producto_id,cantidad,stock_minimo) SELECT ?,?,0,0 WHERE NOT EXISTS(SELECT 1 FROM dbo.existencia WHERE sede_id=? AND producto_id=?)",
                sede, producto, sede, producto);
    }

    private void validarLineas(List<SistemaRequest.Linea> lineas, boolean activos) {
        Set<Long> ids = new HashSet<>();
        for (var l : lineas) {
            if (!ids.add(l.productoId()))
                throw error(HttpStatus.BAD_REQUEST, "No repitas un producto en las líneas");
            if (l.cantidad() == null || l.cantidad().signum() <= 0 || l.cantidad().scale() > 3
                    || l.cantidad().precision() - l.cantidad().scale() > 15)
                throw error(HttpStatus.BAD_REQUEST, "Cantidad positiva con hasta tres decimales requerida");
            producto(l.productoId(), activos);
        }
        if (lineas.isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Agrega al menos una línea");
    }

    private Long existente(String tabla, String key) {
        var rows = db.queryForList("SELECT id FROM dbo." + tabla + " WHERE idempotencia=?", key);
        return rows.isEmpty() ? null : ((Number) rows.get(0).get("id")).longValue();
    }

    private boolean mismasLineas(List<SistemaRequest.Linea> solicitadas, String tabla, String campo, long id) {
        var guardadas = db.queryForList(
                "SELECT producto_id,cantidad,costo_unitario FROM dbo." + tabla + " WHERE " + campo + "=?", id);
        if (solicitadas.size() != guardadas.size())
            return false;
        for (var l : solicitadas) {
            var encontrada = guardadas.stream()
                    .filter(x -> ((Number) x.get("producto_id")).longValue() == l.productoId()).findFirst();
            if (encontrada.isEmpty())
                return false;
            var x = encontrada.get();
            if (((BigDecimal) x.get("cantidad")).compareTo(l.cantidad()) != 0)
                return false;
            BigDecimal costo = (BigDecimal) x.get("costo_unitario");
            if ((costo == null) != (l.costoUnitario() == null)
                    || costo != null && costo.compareTo(l.costoUnitario()) != 0)
                return false;
        }
        return true;
    }

    private long registrar(int sede, String tipo, String motivo, String referencia, String proveedor, String key,
            List<SistemaRequest.Linea> lineas, Long reverso, Long transfer) {
        validarLineas(lineas, reverso == null);
        if ("INICIAL".equals(tipo)
                && db.queryForObject("SELECT COUNT(*) FROM dbo.movimiento WHERE sede_id=?", Integer.class, sede) > 0)
            throw error(HttpStatus.CONFLICT,
                    "El stock inicial debe ser el primer movimiento de la sede; registra una entrada o ajuste");
        long id = acceso.insertar(
                "INSERT INTO dbo.movimiento(sede_id,usuario_id,tipo,motivo,referencia,proveedor,idempotencia,reverso_de_id,transferencia_id) VALUES(?,?,?,?,?,?,?,?,?)",
                sede, acceso.actor(), tipo, motivo, referencia, proveedor, key, reverso, transfer);
        for (var l : lineas.stream().sorted(Comparator.comparing(SistemaRequest.Linea::productoId)).toList()) {
            existencia(sede, l.productoId());
            BigDecimal delta = l.cantidad().multiply(BigDecimal.valueOf(signo(tipo)));
            int updated = db.update(
                    "UPDATE dbo.existencia SET cantidad=cantidad+? WHERE sede_id=? AND producto_id=? AND cantidad+?>=0",
                    delta, sede, l.productoId(), delta);
            if (updated != 1)
                throw error(HttpStatus.CONFLICT, "Stock insuficiente para el producto " + l.productoId());
            db.update(
                    "INSERT INTO dbo.detalle_movimiento(movimiento_id,producto_id,cantidad,costo_unitario) VALUES(?,?,?,?)",
                    id, l.productoId(), l.cantidad(), l.costoUnitario());
        }
        acceso.audit("REGISTRAR_" + tipo, "movimiento", id, motivo);
        return id;
    }

    @Transactional
    public Map<String, Object> movimiento(SistemaRequest.Movimiento r) {
        acceso.bloquear();
        acceso.escritura();
        acceso.sedeActiva(r.sedeId());
        if (!Set.of("ENTRADA", "SALIDA", "MERMA", "INICIAL").contains(r.tipo()))
            throw error(HttpStatus.BAD_REQUEST, "Tipo de movimiento no permitido");
        if ("INICIAL".equals(r.tipo()))
            acceso.admin();
        Long previo = existente("movimiento", r.idempotencia());
        if (previo != null) {
            var old = fila("movimiento", previo);
            if (((Number) old.get("sede_id")).intValue() != r.sedeId() || !old.get("tipo").equals(r.tipo())
                    || ((Number) old.get("usuario_id")).longValue() != acceso.actor()
                    || !mismasLineas(r.lineas(), "detalle_movimiento", "movimiento_id", previo))
                throw error(HttpStatus.CONFLICT, "La clave ya pertenece a otra operación");
            return detalleMovimiento(previo);
        }
        return detalleMovimiento(registrar(r.sedeId(), r.tipo(), r.motivo(), r.referencia(), r.proveedor(),
                r.idempotencia(), r.lineas(), null, null));
    }

    public Map<String, Object> detalleMovimiento(long id) {
        var m = fila("movimiento", id);
        acceso.sede(((Number) m.get("sede_id")).intValue());
        m.put("lineas", db.queryForList(
                "SELECT d.*,p.nombre,p.codigo,u.codigo AS unidad FROM dbo.detalle_movimiento d JOIN dbo.producto p ON p.id=d.producto_id JOIN dbo.unidad u ON u.id=p.unidad_id WHERE movimiento_id=?",
                id));
        return m;
    }

    @Transactional
    public Map<String, Object> ajuste(SistemaRequest.Ajuste r) {
        acceso.bloquear();
        acceso.admin();
        acceso.sedeActiva(r.sedeId());
        producto(r.productoId(), true);
        Long previo = existente("movimiento", r.idempotencia());
        if (previo != null) {
            var m = detalleMovimiento(previo);
            if (((Number) m.get("sede_id")).intValue() != r.sedeId() || !m.get("tipo").toString().startsWith("AJUSTE_"))
                throw error(HttpStatus.CONFLICT, "La clave ya está utilizada");
            return m;
        }
        existencia(r.sedeId(), r.productoId());
        BigDecimal saldo = db.queryForObject("SELECT cantidad FROM dbo.existencia WHERE sede_id=? AND producto_id=?",
                BigDecimal.class, r.sedeId(), r.productoId());
        BigDecimal diferencia = r.conteo().subtract(saldo);
        if (diferencia.signum() == 0)
            throw error(HttpStatus.CONFLICT, "El conteo coincide con el saldo; no se necesita ajuste");
        long id = registrar(r.sedeId(), diferencia.signum() > 0 ? "AJUSTE_ENTRADA" : "AJUSTE_SALIDA", r.motivo(),
                "Conteo: " + r.conteo(), null, r.idempotencia(),
                List.of(new SistemaRequest.Linea(r.productoId(), diferencia.abs(), null)), null, null);
        return detalleMovimiento(id);
    }

    @Transactional
    public Map<String, Object> reverso(long id, SistemaRequest.Reverso r) {
        acceso.bloquear();
        acceso.admin();
        var original = fila("movimiento", id);
        int sede = ((Number) original.get("sede_id")).intValue();
        acceso.sedeActiva(sede);
        if (original.get("reverso_de_id") != null || original.get("transferencia_id") != null)
            throw error(HttpStatus.CONFLICT,
                    "No se revierten reversos ni movimientos de transferencia desde este módulo");
        Long previo = existente("movimiento", r.idempotencia());
        if (previo != null) {
            var m = detalleMovimiento(previo);
            if (!(m.get("reverso_de_id") instanceof Number ref) || ref.longValue() != id)
                throw error(HttpStatus.CONFLICT, "La clave pertenece a otro reverso");
            return m;
        }
        if (db.queryForObject("SELECT COUNT(*) FROM dbo.movimiento WHERE reverso_de_id=?", Integer.class, id) > 0)
            throw error(HttpStatus.CONFLICT, "El movimiento ya tiene un reverso");
        var lineas = db.query(
                "SELECT producto_id,cantidad,costo_unitario FROM dbo.detalle_movimiento WHERE movimiento_id=?",
                (rs, n) -> new SistemaRequest.Linea(rs.getLong(1), rs.getBigDecimal(2), rs.getBigDecimal(3)), id);
        return detalleMovimiento(
                registrar(sede, signo(original.get("tipo").toString()) > 0 ? "REVERSO_SALIDA" : "REVERSO_ENTRADA",
                        r.motivo(), "Reverso #" + id, null, r.idempotencia(), lineas, id, null));
    }

    @Transactional
    public void minimo(SistemaRequest.Minimo r) {
        acceso.bloquear();
        acceso.admin();
        acceso.sedeActiva(r.sedeId());
        producto(r.productoId(), true);
        existencia(r.sedeId(), r.productoId());
        db.update("UPDATE dbo.existencia SET stock_minimo=? WHERE sede_id=? AND producto_id=?", r.minimo(), r.sedeId(),
                r.productoId());
        acceso.audit("STOCK_MINIMO", "producto", r.productoId(), "Sede " + r.sedeId());
    }

    public List<Map<String, Object>> stock(Integer sede) {
        if (sede != null)
            acceso.sede(sede);
        String sql = "SELECT s.id AS sede_id,s.nombre AS sede,p.id AS producto_id,p.codigo,p.nombre AS producto,u.codigo AS unidad,e.cantidad,e.stock_minimo,CASE WHEN e.cantidad<=e.stock_minimo THEN 1 ELSE 0 END AS alerta FROM dbo.existencia e JOIN dbo.sede s ON s.id=e.sede_id JOIN dbo.producto p ON p.id=e.producto_id JOIN dbo.unidad u ON u.id=p.unidad_id WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (sede != null) {
            sql += " AND s.id=?";
            args.add(sede);
        } else if (!acceso.esAdmin()) {
            sql += " AND s.id IN(SELECT sede_id FROM dbo.usuario_sede WHERE usuario_id=?)";
            args.add(acceso.actor());
        }
        return db.queryForList(sql + " ORDER BY s.nombre,p.nombre", args.toArray());
    }

    public List<Map<String, Object>> movimientos(Integer sede, LocalDate desde, LocalDate hasta) {
        if (sede != null)
            acceso.sede(sede);
        if (desde != null && hasta != null && desde.isAfter(hasta))
            throw error(HttpStatus.BAD_REQUEST, "Período inválido");
        String sql = "SELECT m.*,s.nombre AS sede,u.usuario FROM dbo.movimiento m JOIN dbo.sede s ON s.id=m.sede_id JOIN dbo.usuario u ON u.id=m.usuario_id WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (sede != null) {
            sql += " AND m.sede_id=?";
            args.add(sede);
        } else if (!acceso.esAdmin()) {
            sql += " AND m.sede_id IN(SELECT sede_id FROM dbo.usuario_sede WHERE usuario_id=?)";
            args.add(acceso.actor());
        }
        if (desde != null) {
            sql += " AND m.fecha_registro>=?";
            args.add(desde.atStartOfDay());
        }
        if (hasta != null) {
            sql += " AND m.fecha_registro<?";
            args.add(hasta.plusDays(1).atStartOfDay());
        }
        return db.queryForList(sql + " ORDER BY m.id DESC", args.toArray());
    }

    public List<Map<String, Object>> kardex(int sede, long producto) {
        acceso.sede(sede);
        producto(producto, false);
        var rows = db.queryForList(
                "SELECT m.id,m.fecha_registro,m.tipo,m.motivo,m.referencia,u.usuario,d.cantidad FROM dbo.movimiento m JOIN dbo.detalle_movimiento d ON d.movimiento_id=m.id JOIN dbo.usuario u ON u.id=m.usuario_id WHERE m.sede_id=? AND d.producto_id=? ORDER BY m.id",
                sede, producto);
        BigDecimal saldo = BigDecimal.ZERO;
        for (var row : rows) {
            BigDecimal q = (BigDecimal) row.get("cantidad");
            int sign = signo((String) row.get("tipo"));
            saldo = saldo.add(q.multiply(BigDecimal.valueOf(sign)));
            row.put("entrada", sign > 0 ? q : BigDecimal.ZERO);
            row.put("salida", sign < 0 ? q : BigDecimal.ZERO);
            row.put("saldo", saldo);
        }
        return rows;
    }

    public List<Map<String, Object>> auditoria() {
        acceso.admin();
        return db.queryForList(
                "SELECT TOP 1000 a.*,u.usuario FROM dbo.auditoria a LEFT JOIN dbo.usuario u ON u.id=a.usuario_id ORDER BY a.id DESC");
    }

    public List<Map<String, Object>> destinos() {
        acceso.actual();
        return db.queryForList("SELECT id,nombre FROM dbo.sede WHERE activo=1 ORDER BY nombre");
    }

    public List<Map<String, Object>> transferencias() {
        var u = acceso.actual();
        return acceso.esAdmin() ? db.queryForList(
                "SELECT t.*,o.nombre AS origen,d.nombre AS destino FROM dbo.transferencia t JOIN dbo.sede o ON o.id=t.origen_id JOIN dbo.sede d ON d.id=t.destino_id ORDER BY t.id DESC")
                : db.queryForList(
                        "SELECT t.*,o.nombre AS origen,d.nombre AS destino FROM dbo.transferencia t JOIN dbo.sede o ON o.id=t.origen_id JOIN dbo.sede d ON d.id=t.destino_id WHERE t.origen_id IN(SELECT sede_id FROM dbo.usuario_sede WHERE usuario_id=?) OR t.destino_id IN(SELECT sede_id FROM dbo.usuario_sede WHERE usuario_id=?) ORDER BY t.id DESC",
                        u.get("id"), u.get("id"));
    }

    public Map<String, Object> detalleTransferencia(long id) {
        var t = fila("transferencia", id);
        boolean allowed = transferencias().stream().anyMatch(x -> ((Number) x.get("id")).longValue() == id);
        if (!allowed)
            throw error(HttpStatus.FORBIDDEN, "Transferencia fuera de tus sedes");
        t.put("lineas", db.queryForList(
                "SELECT dt.*,p.nombre,p.codigo,u.codigo AS unidad FROM dbo.detalle_transferencia dt JOIN dbo.producto p ON p.id=dt.producto_id JOIN dbo.unidad u ON u.id=p.unidad_id WHERE transferencia_id=?",
                id));
        return t;
    }

    @Transactional
    public Map<String, Object> crearTransferencia(SistemaRequest.Transferencia r) {
        acceso.bloquear();
        acceso.escritura();
        acceso.sedeActiva(r.origenId());
        if (r.origenId().equals(r.destinoId()))
            throw error(HttpStatus.BAD_REQUEST, "Origen y destino deben ser diferentes");
        if (db.queryForObject("SELECT COUNT(*) FROM dbo.sede WHERE id=? AND activo=1", Integer.class,
                r.destinoId()) == 0)
            throw error(HttpStatus.BAD_REQUEST, "Destino no disponible");
        Long previo = existente("transferencia", r.idempotencia());
        if (previo != null) {
            var old = fila("transferencia", previo);
            var lines = db.queryForList(
                    "SELECT producto_id,cantidad FROM dbo.detalle_transferencia WHERE transferencia_id=?", previo);
            boolean iguales = lines.size() == r.lineas().size() && r.lineas().stream().allMatch(
                    l -> lines.stream().anyMatch(x -> ((Number) x.get("producto_id")).longValue() == l.productoId()
                            && ((BigDecimal) x.get("cantidad")).compareTo(l.cantidad()) == 0));
            if (((Number) old.get("origen_id")).intValue() != r.origenId()
                    || ((Number) old.get("destino_id")).intValue() != r.destinoId() || !iguales)
                throw error(HttpStatus.CONFLICT, "La clave pertenece a otra transferencia");
            return detalleTransferencia(previo);
        }
        validarLineas(r.lineas(), true);
        long id = acceso.insertar(
                "INSERT INTO dbo.transferencia(origen_id,destino_id,creador_id,idempotencia) VALUES(?,?,?,?)",
                r.origenId(), r.destinoId(), acceso.actor(), r.idempotencia());
        for (var l : r.lineas())
            db.update("INSERT INTO dbo.detalle_transferencia(transferencia_id,producto_id,cantidad) VALUES(?,?,?)", id,
                    l.productoId(), l.cantidad());
        acceso.audit("CREAR_TRANSFERENCIA", "transferencia", id, "Borrador");
        return detalleTransferencia(id);
    }

    @Transactional
    public Map<String, Object> accionTransferencia(long id, String accion, String motivo) {
        acceso.bloquear();
        acceso.escritura();
        var t = fila("transferencia", id);
        int origen = ((Number) t.get("origen_id")).intValue(), destino = ((Number) t.get("destino_id")).intValue();
        String estado = (String) t.get("estado");
        var lineas = db.query("SELECT producto_id,cantidad FROM dbo.detalle_transferencia WHERE transferencia_id=?",
                (rs, n) -> new SistemaRequest.Linea(rs.getLong(1), rs.getBigDecimal(2), null), id);
        switch (accion) {
            case "despachar" -> {
                acceso.sedeActiva(origen);
                if ("DESPACHADA".equals(estado) || "RECIBIDA".equals(estado))
                    return detalleTransferencia(id);
                if (!"BORRADOR".equals(estado))
                    throw error(HttpStatus.CONFLICT, "Solo se despacha un borrador");
                registrar(origen, "TRANSFERENCIA_SALIDA", "Despacho a sede " + destino, "TR-" + id, null,
                        UUID.randomUUID().toString(), lineas, null, id);
                db.update(
                        "UPDATE dbo.transferencia SET estado='DESPACHADA',despachador_id=?,despachada_en=CURRENT_TIMESTAMP WHERE id=?",
                        acceso.actor(), id);
            }
            case "recibir" -> {
                acceso.sedeActiva(destino);
                if ("RECIBIDA".equals(estado))
                    return detalleTransferencia(id);
                if (!"DESPACHADA".equals(estado))
                    throw error(HttpStatus.CONFLICT, "La transferencia no está despachada");
                if (t.get("incidencia") != null && !t.get("incidencia").toString().isBlank())
                    throw error(HttpStatus.CONFLICT, "Resuelve la incidencia antes de recibir");
                registrar(destino, "TRANSFERENCIA_ENTRADA", "Recepción desde sede " + origen, "TR-" + id, null,
                        UUID.randomUUID().toString(), lineas, null, id);
                db.update(
                        "UPDATE dbo.transferencia SET estado='RECIBIDA',receptor_id=?,recibida_en=CURRENT_TIMESTAMP WHERE id=?",
                        acceso.actor(), id);
            }
            case "cancelar" -> {
                acceso.sede(origen);
                if ("CANCELADA".equals(estado))
                    return detalleTransferencia(id);
                if (!"BORRADOR".equals(estado))
                    throw error(HttpStatus.CONFLICT, "Solo se cancela un borrador");
                db.update("UPDATE dbo.transferencia SET estado='CANCELADA' WHERE id=?", id);
            }
            case "incidencia" -> {
                acceso.sede(destino);
                if (!"DESPACHADA".equals(estado))
                    throw error(HttpStatus.CONFLICT, "La incidencia corresponde a una transferencia despachada");
                db.update("UPDATE dbo.transferencia SET incidencia=? WHERE id=?", motivo, id);
            }
            case "resolver" -> {
                acceso.admin();
                if (!"DESPACHADA".equals(estado))
                    throw error(HttpStatus.CONFLICT, "La transferencia no está pendiente");
                db.update("UPDATE dbo.transferencia SET incidencia=NULL WHERE id=?", id);
            }
            case "devolver" -> {
                acceso.admin();
                acceso.sedeActiva(origen);
                if ("DEVUELTA".equals(estado))
                    return detalleTransferencia(id);
                if (!"DESPACHADA".equals(estado))
                    throw error(HttpStatus.CONFLICT, "Solo se devuelve mercadería en tránsito");
                registrar(origen, "TRANSFERENCIA_ENTRADA", "Devolución: " + motivo, "TR-" + id, null,
                        UUID.randomUUID().toString(), lineas, null, id);
                db.update("UPDATE dbo.transferencia SET estado='DEVUELTA' WHERE id=?", id);
            }
            default -> throw error(HttpStatus.BAD_REQUEST, "Acción no válida");
        }
        acceso.audit("TRANSFERENCIA_" + accion.toUpperCase(Locale.ROOT), "transferencia", id, motivo);
        return detalleTransferencia(id);
    }
}