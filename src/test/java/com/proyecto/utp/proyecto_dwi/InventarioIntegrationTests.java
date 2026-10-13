package com.proyecto.utp.proyecto_dwi;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.proyecto.utp.proyecto_dwi.dto.SistemaRequest;
import com.proyecto.utp.proyecto_dwi.service.AccesoService;
import com.proyecto.utp.proyecto_dwi.service.InventarioService;

@SpringBootTest @ActiveProfiles("test")
class InventarioIntegrationTests {
 @Autowired JdbcTemplate db; @Autowired InventarioService inventario; @Autowired AccesoService acceso;
 int a,b; long producto;
 static void login(String username){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username,"",List.of()));}
 @BeforeEach void preparar(){
  for(String table:List.of("auditoria","detalle_movimiento","movimiento","detalle_transferencia","transferencia","existencia","usuario_sede","usuario","sede","producto","categoria","unidad"))db.update("DELETE FROM dbo."+table);
  db.update("INSERT INTO dbo.usuario(usuario,nombre,password_hash,rol,activo) VALUES('admin','Administrador','hash','ADMIN',1)");
  login("admin");
  a=(int)acceso.guardarSede(null,new SistemaRequest.Sede("A","Sede A",null));b=(int)acceso.guardarSede(null,new SistemaRequest.Sede("B","Sede B",null));
  long cat=acceso.insertar("INSERT INTO dbo.categoria(nombre,activo) VALUES('Carnes',1)");long unidad=acceso.insertar("INSERT INTO dbo.unidad(codigo,nombre) VALUES('kg','Kilogramo')");
  producto=acceso.insertar("INSERT INTO dbo.producto(codigo,nombre,categoria_id,unidad_id,activo) VALUES('PR-TEST','Pollo',?,?,1)",cat,unidad);
 }
 @AfterEach void limpiar(){SecurityContextHolder.clearContext();}
 SistemaRequest.Linea linea(String cantidad){return new SistemaRequest.Linea(producto,new BigDecimal(cantidad),null);}
 SistemaRequest.Movimiento mov(String tipo,String cantidad,String key){return new SistemaRequest.Movimiento(a,tipo,"Prueba",null,null,key,List.of(linea(cantidad)));}
 BigDecimal saldo(int sede){var rows=db.queryForList("SELECT cantidad FROM dbo.existencia WHERE sede_id=? AND producto_id=?",sede,producto);return rows.isEmpty()?BigDecimal.ZERO:(BigDecimal)rows.get(0).get("cantidad");}
 @Test void salidaInsuficienteRevierteTodaLaOperacion(){
  inventario.movimiento(mov("ENTRADA","5",UUID.randomUUID().toString()));
  assertThrows(ResponseStatusException.class,()->inventario.movimiento(mov("SALIDA","6",UUID.randomUUID().toString())));
  assertEquals(0,saldo(a).compareTo(new BigDecimal("5")));
  assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.movimiento",Integer.class));
 }
 @Test void reintentoNoDuplicaEntrada(){String key=UUID.randomUUID().toString();var one=inventario.movimiento(mov("ENTRADA","3.5",key));var two=inventario.movimiento(mov("ENTRADA","3.5",key));assertEquals(one.get("id"),two.get("id"));assertEquals(0,saldo(a).compareTo(new BigDecimal("3.5")));}
 @Test void despachoRecepcionYRecepcionRepetida(){
  inventario.movimiento(mov("ENTRADA","12",UUID.randomUUID().toString()));
  var t=inventario.crearTransferencia(new SistemaRequest.Transferencia(a,b,UUID.randomUUID().toString(),List.of(linea("5"))));long id=((Number)t.get("id")).longValue();
  inventario.accionTransferencia(id,"despachar","Prueba");assertEquals(0,saldo(a).compareTo(new BigDecimal("7")));assertEquals(0,saldo(b).compareTo(BigDecimal.ZERO));
  inventario.accionTransferencia(id,"recibir","Recibido");inventario.accionTransferencia(id,"recibir","Reintento");assertEquals(0,saldo(b).compareTo(new BigDecimal("5")));
 }
 @Test void reversoConservaOriginal(){var m=inventario.movimiento(mov("ENTRADA","8",UUID.randomUUID().toString()));inventario.reverso(((Number)m.get("id")).longValue(),new SistemaRequest.Reverso("Error",UUID.randomUUID().toString()));assertEquals(0,saldo(a).compareTo(BigDecimal.ZERO));assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM dbo.movimiento",Integer.class));}
 @Test void consultorNoPuedeRegistrar(){long u=acceso.insertar("INSERT INTO dbo.usuario(usuario,nombre,password_hash,rol,activo) VALUES('consulta','Consulta','hash','CONSULTOR',1)");db.update("INSERT INTO dbo.usuario_sede(usuario_id,sede_id) VALUES(?,?)",u,a);login("consulta");assertThrows(ResponseStatusException.class,()->inventario.movimiento(mov("ENTRADA","5",UUID.randomUUID().toString())));}
 @Test void encargadoNoLeeOtraSede(){long u=acceso.insertar("INSERT INTO dbo.usuario(usuario,nombre,password_hash,rol,activo) VALUES('operador','Operador','hash','ENCARGADO',1)");db.update("INSERT INTO dbo.usuario_sede(usuario_id,sede_id) VALUES(?,?)",u,a);login("operador");assertThrows(ResponseStatusException.class,()->inventario.stock(b));}
 @Test void salidasConcurrentesNoGeneranStockNegativo()throws Exception{
  inventario.movimiento(mov("ENTRADA","10",UUID.randomUUID().toString()));ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
  Callable<Boolean> task=()->{login("admin");start.await();try{inventario.movimiento(mov("SALIDA","7",UUID.randomUUID().toString()));return true;}catch(ResponseStatusException e){return false;}finally{SecurityContextHolder.clearContext();}};
  try{Future<Boolean> f1=pool.submit(task),f2=pool.submit(task);start.countDown();int success=(f1.get(10,TimeUnit.SECONDS)?1:0)+(f2.get(10,TimeUnit.SECONDS)?1:0);assertEquals(1,success);assertEquals(0,saldo(a).compareTo(new BigDecimal("3")));}finally{pool.shutdownNow();}
 }
 @Test void unaLineaFallidaRevierteLasOtras(){long second=acceso.insertar("INSERT INTO dbo.producto(codigo,nombre,categoria_id,unidad_id,activo) SELECT 'OTRO','Otro',categoria_id,unidad_id,1 FROM dbo.producto WHERE id=?",producto);inventario.movimiento(mov("ENTRADA","10",UUID.randomUUID().toString()));var request=new SistemaRequest.Movimiento(a,"SALIDA","Prueba",null,null,UUID.randomUUID().toString(),List.of(linea("2"),new SistemaRequest.Linea(second,new BigDecimal("1"),null)));assertThrows(ResponseStatusException.class,()->inventario.movimiento(request));assertEquals(0,saldo(a).compareTo(new BigDecimal("10")));}
 @Test void incidenciaBloqueaRecepcion(){inventario.movimiento(mov("ENTRADA","10",UUID.randomUUID().toString()));long id=((Number)inventario.crearTransferencia(new SistemaRequest.Transferencia(a,b,UUID.randomUUID().toString(),List.of(linea("2")))).get("id")).longValue();inventario.accionTransferencia(id,"despachar","Prueba");inventario.accionTransferencia(id,"incidencia","Falta confirmar entrega");assertThrows(ResponseStatusException.class,()->inventario.accionTransferencia(id,"recibir","Recibido"));}
 @Test void claveRepetidaConOtraCantidadSeRechaza(){String key=UUID.randomUUID().toString();inventario.movimiento(mov("ENTRADA","3",key));assertThrows(ResponseStatusException.class,()->inventario.movimiento(mov("ENTRADA","4",key)));assertEquals(0,saldo(a).compareTo(new BigDecimal("3")));}
 @Test void productoEnTransitoNoSeDesactiva(){inventario.movimiento(mov("ENTRADA","5",UUID.randomUUID().toString()));inventario.crearTransferencia(new SistemaRequest.Transferencia(a,b,UUID.randomUUID().toString(),List.of(linea("2"))));assertThrows(ResponseStatusException.class,()->acceso.productoDesactivable(producto));}
 @Test void noSeCreaUsuarioConClaveDeEspacios(){assertThrows(ResponseStatusException.class,()->acceso.guardarUsuario(null,new SistemaRequest.Usuario("nuevo","Nuevo","            ","CONSULTOR",List.of(a),true)));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.usuario WHERE usuario='nuevo'",Integer.class));}
}