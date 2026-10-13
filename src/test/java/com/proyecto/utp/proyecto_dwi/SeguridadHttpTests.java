package com.proyecto.utp.proyecto_dwi;

import java.net.*;
import java.net.http.*;
import java.util.regex.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:h2:mem:mesonhttp;MODE=MSSQLServer;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class SeguridadHttpTests {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    HttpClient client;
    String csrf;
    @BeforeEach void preparar(){
        db.update("DELETE FROM dbo.usuario_sede");
        db.update("DELETE FROM dbo.auditoria");
        db.update("DELETE FROM dbo.usuario");
        db.update("INSERT INTO dbo.usuario(usuario,nombre,password_hash,rol,activo) VALUES('admin','Admin',?,'ADMIN',1)",encoder.encode("ClavePruebas123!"));
        db.update("INSERT INTO dbo.usuario(usuario,nombre,password_hash,rol,activo) VALUES('consulta','Consulta',?,'CONSULTOR',1)",encoder.encode("ClavePruebas123!"));
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    }
    HttpResponse<String> enviar(String path,String method,String body,boolean token)throws Exception{
        var r=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        if(token)r.header("X-CSRF-TOKEN",csrf);
        if(body!=null)r.header("Content-Type",path.equals("/api/login")?"application/x-www-form-urlencoded":"application/json");
        return client.send(r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    void token()throws Exception{var r=enviar("/api/csrf","GET",null,false);assertEquals(200,r.statusCode());var m=Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(r.body());assertTrue(m.find());csrf=m.group(1);}
    void login(String username)throws Exception{token();assertEquals(204,enviar("/api/login","POST","usuario="+username+"&password=ClavePruebas123%21",true).statusCode());token();}
    @Test void apiExigeSesion()throws Exception{assertEquals(401,enviar("/api/productos","GET",null,false).statusCode());}
    @Test void escrituraExigeCsrf()throws Exception{login("admin");assertEquals(403,enviar("/api/sedes","POST","{\"codigo\":\"X\",\"nombre\":\"Prueba\"}",false).statusCode());}
    @Test void consultorNoCreaSedesNiProductos()throws Exception{login("consulta");assertEquals(403,enviar("/api/sedes","POST","{\"codigo\":\"X\",\"nombre\":\"Prueba\"}",true).statusCode());assertEquals(403,enviar("/api/productos","POST","{}",true).statusCode());}
    @Test void administradorConsultaYCuentaDesactivadaPierdeSesion()throws Exception{login("admin");assertEquals(200,enviar("/api/me","GET",null,false).statusCode());db.update("UPDATE dbo.usuario SET activo=0 WHERE usuario='admin'");assertEquals(401,enviar("/api/me","GET",null,false).statusCode());}
    @Test void setupSeCierraDespuesDePrimeraCuenta()throws Exception{token();assertEquals(409,enviar("/api/setup","POST","{\"usuario\":\"nuevo\",\"nombre\":\"Nuevo\",\"password\":\"ClavePruebas123!\"}",true).statusCode());}
}