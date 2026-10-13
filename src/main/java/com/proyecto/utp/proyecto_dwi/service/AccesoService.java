package com.proyecto.utp.proyecto_dwi.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.proyecto.utp.proyecto_dwi.dto.SistemaRequest;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AccesoService {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;

    public AccesoService(JdbcTemplate db, PasswordEncoder encoder) {
        this.db = db;
        this.encoder = encoder;
    }

    public static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    public void bloquear() {
        if (db.update("UPDATE dbo.control_operacion SET version=version+1 WHERE id=1") != 1)
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Falta la fila de control; ejecuta la migración del sistema");
    }

    public long insertar(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        db.update(c -> {
            var p = c.prepareStatement(sql, new String[] { "id" });
            for (int i = 0; i < args.length; i++)
                p.setObject(i + 1, args[i]);
            return p;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    public Map<String, Object> cuenta(String username) {
        var list = db.queryForList("SELECT id,usuario,nombre,rol,activo FROM dbo.usuario WHERE usuario=?", username);
        if (list.isEmpty() || !Boolean.TRUE.equals(list.get(0).get("activo")))
            throw error(HttpStatus.UNAUTHORIZED, "La cuenta no está activa");
        return list.get(0);
    }

    public Map<String, Object> actual() {
        return cuenta(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    public long actor() {
        return ((Number) actual().get("id")).longValue();
    }

    public boolean esAdmin() {
        return "ADMIN".equals(actual().get("rol"));
    }

    public void admin() {
        if (!esAdmin())
            throw error(HttpStatus.FORBIDDEN, "Solo el administrador puede realizar esta operación");
    }

    public void escritura() {
        if ("CONSULTOR".equals(actual().get("rol")))
            throw error(HttpStatus.FORBIDDEN, "Tu perfil es de consulta");
    }

    public void sede(int sede) {
        var u = actual();
        if (!"ADMIN".equals(u.get("rol"))
                && db.queryForObject("SELECT COUNT(*) FROM dbo.usuario_sede WHERE usuario_id=? AND sede_id=?",
                        Integer.class, u.get("id"), sede) == 0)
            throw error(HttpStatus.FORBIDDEN, "No tienes acceso a esta sede");
        if (db.queryForObject("SELECT COUNT(*) FROM dbo.sede WHERE id=?", Integer.class, sede) == 0)
            throw error(HttpStatus.NOT_FOUND, "Sede no encontrada");
    }

    public void sedeActiva(int sede) {
        sede(sede);
        if (db.queryForObject("SELECT COUNT(*) FROM dbo.sede WHERE id=? AND activo=1", Integer.class, sede) == 0)
            throw error(HttpStatus.CONFLICT, "La sede está desactivada");
    }

    public List<Map<String, Object>> sedes() {
        var u = actual();
        return "ADMIN".equals(u.get("rol")) ? db.queryForList("SELECT * FROM dbo.sede ORDER BY nombre")
                : db.queryForList(
                        "SELECT s.* FROM dbo.sede s JOIN dbo.usuario_sede us ON us.sede_id=s.id WHERE us.usuario_id=? ORDER BY s.nombre",
                        u.get("id"));
    }

    public Map<String, Object> perfil() {
        var u = new LinkedHashMap<>(actual());
        u.put("sedes", sedes());
        return u;
    }

    public void productoDesactivable(long id) {
        if (db.queryForObject(
                "SELECT COUNT(*) FROM dbo.detalle_transferencia dt JOIN dbo.transferencia t ON t.id=dt.transferencia_id WHERE dt.producto_id=? AND t.estado IN ('BORRADOR','DESPACHADA')",
                Integer.class, id) > 0)
            throw error(HttpStatus.CONFLICT, "El producto tiene transferencias pendientes");
    }

    public void audit(String accion, String entidad, Long id, String descripcion) {
        db.update(
                "INSERT INTO dbo.auditoria(usuario_id,accion,entidad,entidad_id,resultado,descripcion) VALUES(?,?,?,?,?,?)",
                actor(), accion, entidad, id, "EXITOSO", descripcion);
    }

    private void validarPassword(String password) {
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw error(HttpStatus.BAD_REQUEST, "La contraseña debe ocupar como máximo 72 bytes UTF-8");
    }

    public boolean necesitaSetup() {
        return db.queryForObject("SELECT COUNT(*) FROM dbo.usuario", Integer.class) == 0;
    }

    @Transactional
    public void setup(SistemaRequest.Setup r) {
        bloquear();
        validarPassword(r.password());
        if (!necesitaSetup())
            throw error(HttpStatus.CONFLICT, "El administrador inicial ya fue creado");
        db.update("INSERT INTO dbo.usuario(usuario,nombre,password_hash,rol,activo) VALUES(?,?,?,'ADMIN',1)",
                r.usuario().toLowerCase(Locale.ROOT), r.nombre().strip(), encoder.encode(r.password()));
    }

    @Transactional
    public long guardarSede(Integer id, SistemaRequest.Sede r) {
        bloquear();
        admin();
        long target;
        if (id == null)
            target = insertar("INSERT INTO dbo.sede(codigo,nombre,direccion,activo) VALUES(?,?,?,1)",
                    r.codigo().strip().toUpperCase(Locale.ROOT), r.nombre().strip(), r.direccion());
        else {
            sede(id);
            db.update("UPDATE dbo.sede SET codigo=?,nombre=?,direccion=? WHERE id=?",
                    r.codigo().strip().toUpperCase(Locale.ROOT), r.nombre().strip(), r.direccion(), id);
            target = id;
        }
        audit("GUARDAR_SEDE", "sede", target, r.nombre());
        return target;
    }

    @Transactional
    public void estadoSede(int id, boolean activo) {
        bloquear();
        admin();
        sede(id);
        if (!activo && db.queryForObject(
                "SELECT COUNT(*) FROM dbo.transferencia WHERE (origen_id=? OR destino_id=?) AND estado IN ('BORRADOR','DESPACHADA')",
                Integer.class, id, id) > 0)
            throw error(HttpStatus.CONFLICT, "La sede tiene transferencias pendientes");
        if (!activo && db.queryForObject("SELECT COUNT(*) FROM dbo.existencia WHERE sede_id=? AND cantidad>0",
                Integer.class, id) > 0)
            throw error(HttpStatus.CONFLICT, "La sede conserva existencias; concílialas antes de desactivar");
        db.update("UPDATE dbo.sede SET activo=? WHERE id=?", activo, id);
        audit("ESTADO_SEDE", "sede", (long) id, Boolean.toString(activo));
    }

    public List<Map<String, Object>> usuarios() {
        admin();
        var list = db.queryForList("SELECT id,usuario,nombre,rol,activo FROM dbo.usuario ORDER BY nombre");
        for (var u : list)
            u.put("sedes", db.queryForList("SELECT sede_id FROM dbo.usuario_sede WHERE usuario_id=?", Integer.class,
                    u.get("id")));
        return list;
    }

    @Transactional
    public long guardarUsuario(Long id, SistemaRequest.Usuario r) {
        bloquear();
        admin();
        if (id != null && db.queryForObject("SELECT COUNT(*) FROM dbo.usuario WHERE id=?", Integer.class, id) == 0)
            throw error(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        if (id != null && id == actor() && (!r.activo() || !"ADMIN".equals(r.rol())))
            throw error(HttpStatus.CONFLICT, "No puedes desactivar tu cuenta ni retirar tu propio rol administrador");
        if (!"ADMIN".equals(r.rol()) && r.sedes().isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Asigna al menos una sede al usuario");
        for (Integer s : new HashSet<>(r.sedes()))
            sedeActiva(s);
        boolean nuevaClave = r.password() != null && !r.password().isBlank();
        if ((id == null || nuevaClave)
                && (r.password() == null || r.password().isBlank() || r.password().length() < 12))
            throw error(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 12 caracteres");
        if (nuevaClave)
            validarPassword(r.password());
        long target;
        if (id == null)
            target = insertar("INSERT INTO dbo.usuario(usuario,nombre,password_hash,rol,activo) VALUES(?,?,?,?,?)",
                    r.usuario().toLowerCase(Locale.ROOT), r.nombre().strip(), encoder.encode(r.password()), r.rol(),
                    r.activo());
        else {
            target = id;
            db.update("UPDATE dbo.usuario SET usuario=?,nombre=?,rol=?,activo=? WHERE id=?",
                    r.usuario().toLowerCase(Locale.ROOT), r.nombre().strip(), r.rol(), r.activo(), id);
            if (nuevaClave)
                db.update("UPDATE dbo.usuario SET password_hash=? WHERE id=?", encoder.encode(r.password()), id);
        }
        db.update("DELETE FROM dbo.usuario_sede WHERE usuario_id=?", target);
        for (Integer s : new HashSet<>(r.sedes()))
            db.update("INSERT INTO dbo.usuario_sede(usuario_id,sede_id) VALUES(?,?)", target, s);
        audit("GUARDAR_USUARIO", "usuario", target, r.usuario());
        return target;
    }
}