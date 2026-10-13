"use strict";
const $ = (id) => document.getElementById(id);
let me,
  productos = [],
  categorias = [],
  unidades = [],
  destinos = [],
  vista = "productos",
  csrf = null,
  ocupado = false,
  versionVista = 0,
  contadorCampos = 0;
const admin = () => me?.rol === "ADMIN",
  escritor = () => me?.rol !== "CONSULTOR";
const sede = () => Number($("sedeActiva").value) || null;
function el(tag, text, clase) {
  const x = document.createElement(tag);
  if (text !== undefined && text !== null) x.textContent = String(text);
  if (clase) x.className = clase;
  return x;
}
function mostrar(text, error = false) {
  $("mensaje").hidden = false;
  $("mensaje").className = "mensaje " + (error ? "error" : "exito");
  $("mensaje").textContent = text;
}
async function api(url, method = "GET", data) {
  let headers = {};
  if (method !== "GET") {
    if (!csrf) {
      const r = await fetch("/api/csrf");
      if (!r.ok) throw new Error("Recarga la sesión");
      csrf = await r.json();
    }
    headers[csrf.headerName] = csrf.token;
    if (data !== undefined) headers["Content-Type"] = "application/json";
  }
  const r = await fetch(url, {
    method,
    headers,
    body: data === undefined ? undefined : JSON.stringify(data),
  });
  if (r.status === 401) {
    location.assign("/login.html");
    throw new Error("La sesión terminó");
  }
  if (r.status === 204) return null;
  const txt = await r.text();
  let result;
  try {
    result = txt ? JSON.parse(txt) : null;
  } catch {
    throw new Error("Respuesta inesperada del servidor");
  }
  if (!r.ok)
    throw new Error(
      [
        result?.detail || "No se completó la operación",
        ...Object.values(result?.errores || {}),
      ].join("\n"),
    );
  return result;
}
function boton(text, action, primary = false) {
  const b = el(
    "button",
    text,
    "boton " + (primary ? "primario" : "secundario"),
  );
  b.type = "button";
  b.addEventListener("click", () =>
    Promise.resolve()
      .then(action)
      .catch((e) => mostrar(e.message, true)),
  );
  return b;
}
function input(name, label, type = "text", value = "", required = true) {
  const c = el("div", null, "campo"),
    l = el("label", label),
    i = el("input");
  i.name = name;
  i.id = "campo-" + name + "-" + ++contadorCampos;
  i.type = type;
  i.value = value ?? "";
  i.required = required;
  l.htmlFor = i.id;
  c.append(l, i);
  return c;
}
function select(
  name,
  label,
  options,
  value = "",
  required = true,
  multiple = false,
) {
  const c = el("div", null, "campo"),
    l = el("label", label),
    s = el("select");
  s.name = name;
  s.id = "campo-" + name + "-" + ++contadorCampos;
  s.required = required;
  s.multiple = multiple;
  l.htmlFor = s.id;
  if (!multiple) {
    const o = el("option", "Selecciona");
    o.value = "";
    s.append(o);
  }
  for (const [id, text] of options) {
    const o = el("option", text);
    o.value = id;
    o.selected = multiple
      ? (value || []).map(String).includes(String(id))
      : String(value) === String(id);
    s.append(o);
  }
  c.append(l, s);
  return c;
}
function valor(form, name) {
  return form.elements.namedItem(name).value.trim();
}
function num(form, name) {
  return Number(valor(form, name));
}
function badge(value) {
  return el(
    "span",
    value,
    "estado " +
      (["Activo", "RECIBIDA"].includes(value)
        ? "activo"
        : value === "DESPACHADA"
          ? "info"
          : "inactivo"),
  );
}
function tabla(rows, cols, actions) {
  const wrap = el("div", null, "panel-tabla"),
    scroll = el("div", null, "tabla-contenedor"),
    t = el("table"),
    head = el("thead"),
    hr = el("tr"),
    body = el("tbody");
  cols.forEach((c) => hr.append(el("th", c[0])));
  if (actions) hr.append(el("th", "Acciones"));
  head.append(hr);
  for (const row of rows) {
    const tr = el("tr");
    for (const [, key, format] of cols) {
      const td = el("td"),
        value = row[key];
      const v = format ? format(value, row) : (value ?? "—");
      if (v instanceof Node) td.append(v);
      else td.textContent = String(v);
      tr.append(td);
    }
    if (actions) {
      const td = el("td"),
        group = el("div", null, "acciones-fila");
      for (const [label, handler] of actions(row) || [])
        group.append(boton(label, handler));
      td.append(group);
      tr.append(td);
    }
    body.append(tr);
  }
  if (!rows.length) {
    const tr = el("tr"),
      td = el("td", "No hay registros para estos filtros.", "fila-vacia");
    td.colSpan = cols.length + (actions ? 1 : 0);
    tr.append(td);
    body.append(tr);
  }
  t.append(head, body);
  scroll.append(t);
  wrap.append(scroll, el("p", rows.length + " registros", "contador"));
  return wrap;
}
function formPanel(title, handler) {
  const section = el("section", null, "panel-formulario"),
    f = el("form");
  section.append(el("h2", title), f);
  f.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (ocupado) return;
    ocupado = true;
    document.querySelectorAll("button").forEach((b) => (b.disabled = true));
    try {
      await handler(f);
      mostrar("Operación registrada correctamente.");
      try {
        await refrescar();
        await cambiar(vista);
      } catch (e) {
        mostrar(
          "La operación se guardó. No se pudo actualizar la pantalla: " +
            e.message,
          true,
        );
      }
    } catch (e) {
      mostrar(e.message, true);
    } finally {
      ocupado = false;
      document.querySelectorAll("button").forEach((b) => (b.disabled = false));
    }
  });
  return [section, f];
}
function guardar(f, label = "Guardar") {
  const b = el("button", label, "boton primario");
  b.type = "submit";
  f.append(b);
}
function idempotencia(type, payload) {
  const hash = JSON.stringify(payload),
    name = "meson-pendiente-" + type;
  let old;
  try {
    old = JSON.parse(sessionStorage.getItem(name));
  } catch {}
  if (old?.hash === hash) return old.key;
  const key = crypto.randomUUID();
  sessionStorage.setItem(name, JSON.stringify({ hash, key }));
  return key;
}
async function comando(type, url, payload) {
  payload.idempotencia = idempotencia(type, payload);
  const result = await api(url, "POST", payload);
  sessionStorage.removeItem("meson-pendiente-" + type);
  return result;
}
function fecha(v) {
  return v ? String(v).replace("T", " ").slice(0, 19) : "—";
}
function csv(rows, name) {
  if (!rows.length) {
    mostrar("No hay registros para exportar.", true);
    return;
  }
  const keys = Object.keys(rows[0]).filter((k) => !Array.isArray(rows[0][k]));
  const cell = (v) => {
    let s = String(v ?? "");
    if (/^\s*[=+\-@]/.test(s)) s = "'" + s;
    return '"' + s.replaceAll('"', '""') + '"';
  };
  const text = [keys, ...rows.map((r) => keys.map((k) => r[k]))]
    .map((r) => r.map(cell).join(";"))
    .join("\r\n");
  const u = URL.createObjectURL(
      new Blob(["\uFEFF" + text], { type: "text/csv;charset=utf-8" }),
    ),
    a = el("a");
  a.href = u;
  a.download = name + ".csv";
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(u), 1000);
}
async function refrescar() {
  [me, productos, categorias, unidades, destinos] = await Promise.all([
    api("/api/me"),
    api("/api/productos"),
    api("/api/categorias"),
    api("/api/unidades"),
    api("/api/destinos"),
  ]);
  actualizarSedes();
}
function actualizarSedes() {
  const old = $("sedeActiva").value;
  $("sedeActiva").replaceChildren();
  for (const s of me.sedes.filter((s) => s.activo)) {
    const o = el("option", s.nombre);
    o.value = s.id;
    $("sedeActiva").append(o);
  }
  if ([...$("sedeActiva").options].some((o) => o.value === old))
    $("sedeActiva").value = old;
  $("nombreUsuario").textContent = me.nombre;
  $("rolUsuario").textContent = me.rol;
}
function requiereSede() {
  if (!sede()) {
    mostrar("Crea o asigna una sede activa para usar este módulo.", true);
    return false;
  }
  return true;
}
const opcionesProductos = () =>
  productos
    .filter((p) => p.activo)
    .map((p) => [p.id, p.nombre + " (" + p.unidad + ")"]);
async function productosVista(root) {
  const filtros = el("div", null, "filtros"),
    buscar = input("buscar", "Buscar", "search", "", false),
    categoria = select(
      "categoriaFiltro",
      "Categoría",
      [["todos", "Todas"], ...categorias.map((c) => [c.id, c.nombre])],
      "todos",
    ),
    estado = select(
      "estadoFiltro",
      "Estado",
      [
        ["todos", "Todos"],
        ["true", "Activos"],
        ["false", "Inactivos"],
      ],
      "todos",
    );
  filtros.append(buscar, categoria, estado);
  root.append(filtros);
  const panels = el("div", null, "paneles"),
    lista = el("div"),
    panel = el("div");
  panels.append(lista, panel);
  root.append(panels);
  let filtered = [];
  function render() {
    const q = buscar.querySelector("input").value.toLocaleLowerCase("es"),
      c = categoria.querySelector("select").value,
      e = estado.querySelector("select").value;
    filtered = productos.filter(
      (p) =>
        (p.nombre.toLocaleLowerCase("es").includes(q) ||
          p.codigo.toLocaleLowerCase("es").includes(q)) &&
        (c === "todos" || String(p.categoriaId) === c) &&
        (e === "todos" || String(p.activo) === e),
    );
    lista.replaceChildren(
      tabla(
        filtered,
        [
          ["Código", "codigo"],
          ["Producto", "nombre"],
          ["Categoría", "categoria"],
          ["Unidad", "unidad"],
          ["Estado", "activo", (v) => badge(v ? "Activo" : "Inactivo")],
        ],
        admin()
          ? (p) =>
              p.activo
                ? [
                    ["Editar", () => editor(p)],
                    [
                      "Desactivar",
                      async () => {
                        if (!confirm("¿Desactivar " + p.nombre + "?")) return;
                        await api(
                          "/api/productos/" + p.id + "/desactivar",
                          "PATCH",
                        );
                        await refrescar();
                        render();
                        mostrar("Producto desactivado.");
                      },
                    ],
                  ]
                : [
                    [
                      "Reactivar",
                      async () => {
                        await api(
                          "/api/productos/" + p.id + "/activar",
                          "PATCH",
                        );
                        await refrescar();
                        render();
                        mostrar("Producto reactivado.");
                      },
                    ],
                  ]
          : null,
      ),
    );
  }
  function editor(p) {
    if (!admin()) return;
    const [box, f] = formPanel(
      p ? "Editar producto" : "Nuevo producto",
      async (f) => {
        await api(
          "/api/productos" + (p ? "/" + p.id : ""),
          p ? "PUT" : "POST",
          {
            codigo: valor(f, "codigo"),
            nombre: valor(f, "nombre"),
            categoriaId: num(f, "categoriaId"),
            unidadId: p ? p.unidadId : num(f, "unidadId"),
          },
        );
      },
    );
    panel.replaceChildren(box);
    f.append(
      input("codigo", "Código", "text", p?.codigo),
      input("nombre", "Nombre", "text", p?.nombre),
      select(
        "categoriaId",
        "Categoría",
        categorias.map((c) => [c.id, c.nombre]),
        p?.categoriaId,
      ),
      select(
        "unidadId",
        "Unidad base",
        unidades.map((u) => [u.id, u.nombre + " (" + u.codigo + ")"]),
        p?.unidadId,
      ),
    );
    f.elements.namedItem("codigo").maxLength = 30;
    f.elements.namedItem("nombre").maxLength = 120;
    if (p) f.elements.namedItem("unidadId").disabled = true;
    f.append(
      el(
        "p",
        "La unidad base se conserva. Los productos con historial se desactivan.",
        "ayuda",
      ),
    );
    guardar(f);
    f.append(boton("Cancelar", () => editor(null)));
  }
  filtros.addEventListener("input", render);
  $("accionesPagina").append(
    boton("Exportar CSV", () => csv(filtered, "productos")),
    ...(admin() ? [boton("Nuevo producto", () => editor(null), true)] : []),
  );
  render();
  editor(null);
}
async function usuariosVista(root) {
  if (!admin()) {
    mostrar("Solo el administrador gestiona usuarios y sedes.", true);
    return;
  }
  const users = await api("/api/usuarios");
  const panels = el("div", null, "paneles"),
    listas = el("div"),
    editor = el("div");
  panels.append(listas, editor);
  root.append(panels);
  listas.append(
    el("h2", "Usuarios"),
    tabla(
      users,
      [
        ["Usuario", "usuario"],
        ["Nombre", "nombre"],
        ["Rol", "rol"],
        [
          "Sedes",
          "sedes",
          (v) =>
            v
              .map((id) => me.sedes.find((s) => s.id === id)?.nombre || id)
              .join(", "),
        ],
        ["Estado", "activo", (v) => badge(v ? "Activo" : "Inactivo")],
      ],
      (u) => [["Editar", () => editUser(u)]],
    ),
    el("h2", "Sedes"),
    tabla(
      me.sedes,
      [
        ["Código", "codigo"],
        ["Sede", "nombre"],
        ["Dirección", "direccion"],
        ["Estado", "activo", (v) => badge(v ? "Activo" : "Inactivo")],
      ],
      (s) => [
        ["Editar", () => editSede(s)],
        [
          s.activo ? "Desactivar" : "Activar",
          async () => {
            if (!confirm("¿Cambiar el estado de " + s.nombre + "?")) return;
            await api(
              "/api/sedes/" + s.id + "/estado?activo=" + !s.activo,
              "PATCH",
            );
            await refrescar();
            await cambiar(vista);
          },
        ],
      ],
    ),
  );
  function editSede(s) {
    const [b, f] = formPanel(s ? "Editar sede" : "Nueva sede", async (f) =>
      api("/api/sedes" + (s ? "/" + s.id : ""), s ? "PUT" : "POST", {
        codigo: valor(f, "codigo"),
        nombre: valor(f, "nombre"),
        direccion: valor(f, "direccion"),
      }),
    );
    editor.replaceChildren(b);
    f.append(
      input("codigo", "Código", "text", s?.codigo),
      input("nombre", "Nombre", "text", s?.nombre),
      input("direccion", "Dirección", "text", s?.direccion, false),
    );
    f.elements.namedItem("codigo").maxLength = 20;
    f.elements.namedItem("nombre").maxLength = 100;
    f.elements.namedItem("direccion").maxLength = 250;
    guardar(f);
  }
  function editUser(u) {
    const [b, f] = formPanel(
      u ? "Editar usuario" : "Nuevo usuario",
      async (f) =>
        api("/api/usuarios" + (u ? "/" + u.id : ""), u ? "PUT" : "POST", {
          usuario: valor(f, "usuario"),
          nombre: valor(f, "nombre"),
          password: f.elements.namedItem("password").value,
          rol: valor(f, "rol"),
          activo: valor(f, "activo") === "true",
          sedes: [...f.elements.namedItem("sedes").selectedOptions].map((o) =>
            Number(o.value),
          ),
        }),
    );
    editor.replaceChildren(b);
    f.append(
      input("usuario", "Usuario", "text", u?.usuario),
      input("nombre", "Nombre completo", "text", u?.nombre),
      input(
        "password",
        u
          ? "Nueva contraseña (vacío conserva actual)"
          : "Contraseña de al menos 12 caracteres",
        "password",
        "",
        !u,
      ),
      select(
        "rol",
        "Rol",
        [
          ["ADMIN", "Administrador"],
          ["ENCARGADO", "Encargado de sede"],
          ["CONSULTOR", "Consultor"],
        ],
        u?.rol || "ENCARGADO",
      ),
      select(
        "activo",
        "Estado",
        [
          ["true", "Activo"],
          ["false", "Inactivo"],
        ],
        String(u?.activo ?? true),
      ),
      select(
        "sedes",
        "Sedes asignadas (Ctrl para varias)",
        me.sedes.filter((s) => s.activo).map((s) => [s.id, s.nombre]),
        u?.sedes || [],
        false,
        true,
      ),
    );
    f.elements.namedItem("password").minLength = 12;
    f.elements.namedItem("password").maxLength = 128;
    f.elements.namedItem("usuario").maxLength = 50;
    f.elements.namedItem("nombre").maxLength = 150;
    guardar(f);
  }
  $("accionesPagina").append(
    boton("Nueva sede", () => editSede(null)),
    boton("Nuevo usuario", () => editUser(null), true),
  );
  editSede(null);
}
function lineasFormulario(f) {
  const box = el("div", null, "lineas");
  f.append(el("h2", "Líneas de la operación"), box);
  function add() {
    const row = el("div", null, "linea");
    const product = select("lineaProducto", "Producto", opcionesProductos()),
      cantidad = input("lineaCantidad", "Cantidad", "number");
    cantidad.querySelector("input").min = "0.001";
    cantidad.querySelector("input").step = "0.001";
    row.append(
      product,
      cantidad,
      boton("Quitar", () => row.remove()),
    );
    box.append(row);
  }
  f.append(boton("+ Agregar línea", add));
  add();
  return () =>
    [...box.children].map((row) => ({
      productoId: Number(row.querySelector("select").value),
      cantidad: Number(row.querySelector("input").value),
    }));
}
async function movimientosVista(root) {
  if (!requiereSede()) return;
  const lista = await api("/api/movimientos?sedeId=" + sede());
  const panels = el("div", null, "paneles"),
    history = el("div");
  panels.append(history);
  root.append(panels);
  history.append(
    tabla(
      lista,
      [
        ["ID", "id"],
        ["Fecha", "fecha_registro", fecha],
        ["Tipo", "tipo"],
        ["Motivo", "motivo"],
        ["Usuario", "usuario"],
      ],
      (m) => [
        [
          "Detalle",
          async () =>
            mostrar(
              JSON.stringify(await api("/api/movimientos/" + m.id), null, 2),
            ),
        ],
      ],
    ),
  );
  if (!escritor()) return;
  const [b, f] = formPanel("Registrar movimiento", async (f) =>
    comando("movimiento", "/api/movimientos", {
      sedeId: sede(),
      tipo: valor(f, "tipo"),
      motivo: valor(f, "motivo"),
      referencia: valor(f, "referencia"),
      proveedor: valor(f, "proveedor"),
      lineas: leer(),
    }),
  );
  panels.append(b);
  f.append(
    select(
      "tipo",
      "Tipo",
      [
        ["ENTRADA", "Entrada"],
        ["SALIDA", "Salida"],
        ["MERMA", "Merma"],
        ...(admin() ? [["INICIAL", "Stock inicial"]] : []),
      ],
      "ENTRADA",
    ),
    input("motivo", "Motivo"),
    input("referencia", "Comprobante o referencia", "text", "", false),
    input("proveedor", "Proveedor (entradas)", "text", "", false),
  );
  f.elements.namedItem("motivo").maxLength = 300;
  f.elements.namedItem("referencia").maxLength = 100;
  f.elements.namedItem("proveedor").maxLength = 150;
  const leer = lineasFormulario(f);
  guardar(f, "Confirmar movimiento");
}
async function ajustesVista(root) {
  if (!admin()) {
    mostrar("Solo el administrador autoriza ajustes y reversos.", true);
    return;
  }
  if (!requiereSede()) return;
  const panels = el("div", null, "paneles iguales");
  root.append(panels);
  const [b, f] = formPanel("Ajuste por conteo físico", async (f) =>
    comando("ajuste", "/api/ajustes", {
      sedeId: sede(),
      productoId: num(f, "productoId"),
      conteo: num(f, "conteo"),
      motivo: valor(f, "motivo"),
    }),
  );
  f.append(
    select("productoId", "Producto", opcionesProductos()),
    input("conteo", "Cantidad contada", "number"),
    input("motivo", "Motivo"),
  );
  f.elements.namedItem("conteo").min = 0;
  f.elements.namedItem("conteo").step = "0.001";
  f.elements.namedItem("motivo").maxLength = 250;
  guardar(f, "Autorizar ajuste");
  panels.append(b);
  const movimientos = await api("/api/movimientos?sedeId=" + sede()),
    [box, g] = formPanel("Reverso de movimiento", async (g) =>
      comando(
        "reverso",
        "/api/movimientos/" + num(g, "movimientoId") + "/reverso",
        { motivo: valor(g, "motivo") },
      ),
    );
  g.append(
    select(
      "movimientoId",
      "Movimiento original",
      movimientos
        .filter((m) => !m.reverso_de_id && !m.transferencia_id)
        .map((m) => [
          m.id,
          "#" + m.id + " " + m.tipo + " " + fecha(m.fecha_registro),
        ]),
    ),
    input("motivo", "Motivo del reverso"),
  );
  g.elements.namedItem("motivo").maxLength = 300;
  g.append(
    el(
      "p",
      "Se crea una operación compensatoria. No se borra el original.",
      "ayuda",
    ),
  );
  guardar(g, "Autorizar reverso");
  panels.append(box);
}
async function transferenciasVista(root) {
  const list = await api("/api/transferencias");
  const panels = el("div", null, "paneles"),
    detail = el("div");
  root.append(panels);
  const permitida = (id) => admin() || me.sedes.some((s) => s.id === id);
  async function accion(t, name) {
    const reason = prompt("Motivo o confirmación física de " + name + ":");
    if (!reason?.trim()) return;
    await api("/api/transferencias/" + t.id + "/" + name, "POST", {
      motivo: reason.trim(),
    });
    await refrescar();
    await cambiar(vista);
    mostrar("Transferencia actualizada.");
  }
  panels.append(
    tabla(
      list,
      [
        ["ID", "id"],
        ["Origen", "origen"],
        ["Destino", "destino"],
        ["Estado", "estado", badge],
        ["Incidencia", "incidencia"],
      ],
      (t) => {
        const a = [
          [
            "Detalle",
            async () => {
              const d = await api("/api/transferencias/" + t.id);
              detail.replaceChildren(
                el("h2", "Transferencia #" + t.id),
                el("p", "Estado: " + d.estado),
                tabla(d.lineas, [
                  ["Producto", "nombre"],
                  ["Cantidad", "cantidad"],
                  ["Unidad", "unidad"],
                ]),
              );
            },
          ],
        ];
        if (!escritor()) return a;
        if (t.estado === "BORRADOR" && permitida(t.origen_id))
          a.push(
            ["Despachar", () => accion(t, "despachar")],
            ["Cancelar", () => accion(t, "cancelar")],
          );
        if (t.estado === "DESPACHADA") {
          if (permitida(t.destino_id))
            a.push(
              ["Recibir", () => accion(t, "recibir")],
              ["Incidencia", () => accion(t, "incidencia")],
            );
          if (admin())
            a.push(
              ["Resolver incidencia", () => accion(t, "resolver")],
              ["Devolver a origen", () => accion(t, "devolver")],
            );
        }
        return a;
      },
    ),
  );
  const right = el("div");
  panels.append(right);
  if (escritor()) {
    const [b, f] = formPanel("Nueva transferencia", async (f) =>
      comando("transferencia", "/api/transferencias", {
        origenId: num(f, "origenId"),
        destinoId: num(f, "destinoId"),
        lineas: leer(),
      }),
    );
    right.append(b);
    f.append(
      select(
        "origenId",
        "Origen",
        me.sedes.filter((s) => s.activo).map((s) => [s.id, s.nombre]),
        sede(),
      ),
      select(
        "destinoId",
        "Destino",
        destinos.map((s) => [s.id, s.nombre]),
      ),
    );
    const leer = lineasFormulario(f);
    guardar(f, "Guardar borrador");
  }
  right.append(detail);
}
async function existenciasVista(root) {
  if (!requiereSede()) return;
  const rows = await api("/api/existencias?sedeId=" + sede());
  root.append(
    tabla(
      rows,
      [
        ["Código", "codigo"],
        ["Producto", "producto"],
        ["Unidad", "unidad"],
        ["Saldo", "cantidad"],
        ["Mínimo", "stock_minimo"],
        [
          "Alerta",
          "alerta",
          (v) => badge(Number(v) ? "Bajo mínimo" : "Normal"),
        ],
      ],
      (r) => [
        [
          "Kardex",
          async () => {
            const k = await api(
              "/api/kardex?sedeId=" + sede() + "&productoId=" + r.producto_id,
            );
            hist.replaceChildren(
              el("h2", "Kardex de " + r.producto),
              tabla(k, [
                ["Fecha", "fecha_registro", fecha],
                ["Tipo", "tipo"],
                ["Entrada", "entrada"],
                ["Salida", "salida"],
                ["Saldo", "saldo"],
                ["Usuario", "usuario"],
              ]),
            );
          },
        ],
      ],
    ),
  );
  $("accionesPagina").append(
    boton("Exportar CSV", () => csv(rows, "existencias")),
  );
  const hist = el("div", null, "seccion");
  root.append(hist);
  if (admin()) {
    const [b, f] = formPanel("Configurar stock mínimo", async (f) =>
      api("/api/existencias/minimo", "POST", {
        sedeId: sede(),
        productoId: num(f, "productoId"),
        minimo: num(f, "minimo"),
      }),
    );
    f.append(
      select("productoId", "Producto", opcionesProductos()),
      input("minimo", "Mínimo", "number", "0"),
    );
    f.elements.namedItem("minimo").min = 0;
    f.elements.namedItem("minimo").step = "0.001";
    guardar(f);
    root.append(b);
  }
}
async function reportesVista(root) {
  const filtros = el("form", null, "filtros"),
    result = el("div"),
    today = new Date().toLocaleDateString("en-CA");
  filtros.append(
    select(
      "tipo",
      "Reporte",
      [
        ["stock", "Existencias"],
        ["movimientos", "Movimientos"],
      ],
      "stock",
    ),
    select(
      "sedeId",
      "Sede",
      [
        ["todas", "Consolidado autorizado"],
        ...me.sedes.map((s) => [s.id, s.nombre]),
      ],
      sede() || "todas",
    ),
    input("desde", "Desde", "date", "", false),
    input("hasta", "Hasta", "date", "", false),
  );
  const b = el("button", "Consultar", "boton primario");
  b.type = "submit";
  filtros.append(b);
  root.append(filtros, result);
  let rows = [];
  async function cargar() {
    const s = valor(filtros, "sedeId"),
      params = new URLSearchParams();
    if (s !== "todas") params.set("sedeId", s);
    if (valor(filtros, "tipo") === "movimientos") {
      if (valor(filtros, "desde")) params.set("desde", valor(filtros, "desde"));
      if (valor(filtros, "hasta")) params.set("hasta", valor(filtros, "hasta"));
    }
    rows = await api(
      "/api/" +
        (valor(filtros, "tipo") === "stock" ? "existencias" : "movimientos") +
        "?" +
        params,
    );
    result.replaceChildren(
      tabla(
        rows,
        valor(filtros, "tipo") === "stock"
          ? [
              ["Sede", "sede"],
              ["Producto", "producto"],
              ["Saldo", "cantidad"],
              ["Unidad", "unidad"],
              ["Mínimo", "stock_minimo"],
            ]
          : [
              ["ID", "id"],
              ["Sede", "sede"],
              ["Fecha", "fecha_registro", fecha],
              ["Tipo", "tipo"],
              ["Motivo", "motivo"],
              ["Usuario", "usuario"],
            ],
      ),
    );
  }
  filtros.addEventListener("submit", (e) => {
    e.preventDefault();
    cargar().catch((e) => mostrar(e.message, true));
  });
  $("accionesPagina").append(
    boton("Exportar CSV", () => csv(rows, "reporte-inventario")),
  );
  await cargar();
  if (admin()) {
    const a = await api("/api/auditoria");
    root.append(
      el("h2", "Bitácora"),
      tabla(a, [
        ["Fecha", "fecha", fecha],
        ["Usuario", "usuario"],
        ["Acción", "accion"],
        ["Entidad", "entidad"],
        ["ID", "entidad_id"],
        ["Resultado", "resultado"],
      ]),
    );
  }
}
const modulos = {
  productos: [
    "Productos",
    "Catálogo de productos, categorías y unidades base.",
    productosVista,
  ],
  usuarios: [
    "Usuarios y sedes",
    "Cuentas, roles y sedes asignadas.",
    usuariosVista,
  ],
  movimientos: [
    "Entradas, salidas y mermas",
    "Registra movimientos de la sede activa.",
    movimientosVista,
  ],
  ajustes: [
    "Ajustes y reversos",
    "Correcciones autorizadas con historial.",
    ajustesVista,
  ],
  transferencias: [
    "Transferencias entre sedes",
    "Borrador → Despachada → Recibida.",
    transferenciasVista,
  ],
  existencias: [
    "Existencias y kardex",
    "Stock por sede, mínimos y movimientos.",
    existenciasVista,
  ],
  reportes: [
    "Reportes y bitácora",
    "Consultas y exportación con permisos por sede.",
    reportesVista,
  ],
};
async function cambiar(name) {
  vista = name;
  const v = ++versionVista;
  const [title, sub, render] = modulos[name];
  $("titulo").textContent = title;
  $("subtitulo").textContent = sub;
  $("accionesPagina").replaceChildren();
  $("mensaje").hidden = true;
  document
    .querySelectorAll("#menu button")
    .forEach((b) =>
      b.classList.toggle("menu-activo", b.dataset.vista === name),
    );
  const root = el("div");
  $("vista").replaceChildren(root);
  root.append(el("p", "Cargando…", "ayuda"));
  try {
    root.replaceChildren();
    await render(root);
  } catch (e) {
    if (v === versionVista) mostrar(e.message, true);
  }
}
async function iniciar() {
  await refrescar();
  for (const [name, [title]] of Object.entries(modulos)) {
    if (!admin() && ["usuarios", "ajustes"].includes(name)) continue;
    const b = boton(title, () => cambiar(name));
    b.dataset.vista = name;
    $("menu").append(b);
  }
  await cambiar("productos");
}
$("sedeActiva").addEventListener("change", () => cambiar(vista));
$("cerrarSesion").addEventListener("click", async () => {
  try {
    await api("/api/logout", "POST");
    location.assign("/login.html");
  } catch (e) {
    mostrar(e.message, true);
  }
});
iniciar().catch((e) => mostrar(e.message, true));