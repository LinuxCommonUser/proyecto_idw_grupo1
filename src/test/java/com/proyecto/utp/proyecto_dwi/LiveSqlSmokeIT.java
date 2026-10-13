package com.proyecto.utp.proyecto_dwi;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.proyecto.utp.proyecto_dwi.service.ProductoService;

import org.springframework.jdbc.core.JdbcTemplate;

/** Comprobacion explicita de solo lectura contra SQL Server; no forma parte del test normal. */
@SpringBootTest @ActiveProfiles("local")
class LiveSqlSmokeIT {
 @Autowired JdbcTemplate db;
 @Autowired ProductoService productos;
 @Test void esquemaInstaladoYCatalogoExistenteLegible(){
  assertTrue(db.queryForObject("SELECT COUNT(*) FROM sys.tables",Integer.class)>=13);
  for(String nombre:new String[]{"usuario","usuario_sede","sede","existencia","movimiento","detalle_movimiento","transferencia","detalle_transferencia","auditoria","control_operacion"})
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name=?",Integer.class,nombre));
  assertFalse(productos.listar().isEmpty(),"El catalogo original debe seguir disponible");
 }
}
