"use strict";
const form = document.getElementById("loginForm"),
  error = document.getElementById("loginError"),
  button = document.getElementById("ingresar");
let setup = false;
async function inicializar() {
  try {
    const r = await fetch("/api/setup/status");
    if (!r.ok)
      throw new Error(
        "No se pudo consultar la configuración. Verifica que se ejecutó 02_sistema_completo.sql.",
      );
    setup = (await r.json()).necesario;
    if (setup) {
      document.getElementById("loginTitulo").textContent =
        "Crear administrador inicial";
      document.getElementById("loginAyuda").textContent =
        "Configura la primera cuenta. Usa una contraseña de al menos 12 caracteres.";
      document.getElementById("campoNombre").hidden = false;
      document.getElementById("nombre").required = true;
      document.getElementById("password").minLength = 12;
      document.getElementById("password").autocomplete = "new-password";
      button.textContent = "Crear cuenta";
    }
  } catch (e) {
    error.hidden = false;
    error.textContent = e.message;
    button.disabled = true;
  }
}
form.addEventListener("submit", async (e) => {
  e.preventDefault();
  error.hidden = true;
  button.disabled = true;
  try {
    const c = await fetch("/api/csrf");
    if (!c.ok)
      throw new Error("No se pudo iniciar la sesión. Recarga la página.");
    const csrf = await c.json();
    const headers = { [csrf.headerName]: csrf.token };
    let body;
    if (setup) {
      headers["Content-Type"] = "application/json";
      body = JSON.stringify({
        usuario: form.elements.namedItem("usuario").value,
        nombre: document.getElementById("nombre").value,
        password: form.elements.namedItem("password").value,
      });
    } else {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
      body = new URLSearchParams({
        usuario: form.elements.namedItem("usuario").value,
        password: form.elements.namedItem("password").value,
      });
    }
    const r = await fetch(setup ? "/api/setup" : "/api/login", {
      method: "POST",
      headers,
      body,
    });
    if (!r.ok) {
      const p = await r.json();
      throw new Error(p.detail || "No se pudo iniciar sesión");
    }
    if (setup) {
      location.reload();
    } else {
      location.assign("/");
    }
  } catch (e) {
    error.hidden = false;
    error.textContent = e.message;
  } finally {
    button.disabled = false;
  }
});
inicializar();