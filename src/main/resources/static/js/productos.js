"use strict";

const elementos = {
  tabla: document.getElementById("tablaProductos"),
  contador: document.getElementById("contador"),
  mensaje: document.getElementById("mensaje"),

  buscar: document.getElementById("buscar"),
  filtroCategoria: document.getElementById("filtroCategoria"),
  filtroEstado: document.getElementById("filtroEstado"),

  formulario: document.getElementById("formProducto"),
  campos: document.getElementById("camposFormulario"),
  titulo: document.getElementById("tituloFormulario"),

  codigo: document.getElementById("codigo"),
  nombre: document.getElementById("nombre"),
  categoria: document.getElementById("categoriaId"),
  unidad: document.getElementById("unidadId"),

  guardar: document.getElementById("btnGuardar"),
  nuevo: document.getElementById("btnNuevo"),
  cancelar: document.getElementById("btnCancelar"),
  exportar: document.getElementById("btnExportar"),
};

let productos = [];
let productoEnEdicion = null;
let ocupado = false;
let preparado = false;

function mostrarMensaje(texto, tipo = "exito") {
  elementos.mensaje.textContent = texto;
  elementos.mensaje.className = `mensaje ${tipo}`;
  elementos.mensaje.hidden = false;
}

function ocultarMensaje() {
  elementos.mensaje.hidden = true;
  elementos.mensaje.textContent = "";
}

async function solicitar(url, opciones = {}) {
  const respuesta = await fetch(url, opciones);

  if (respuesta.status === 204) {
    return null;
  }

  const texto = await respuesta.text();
  let datos = null;

  if (texto) {
    try {
      datos = JSON.parse(texto);
    } catch {
      throw new Error("El servidor devolvió una respuesta inesperada.");
    }
  }

  if (!respuesta.ok) {
    const detalle =
      datos?.detail ||
      `No se pudo completar la operación. HTTP ${respuesta.status}.`;

    const errores = datos?.errores
      ? Object.values(datos.errores).join("\n")
      : "";

    throw new Error(errores ? `${detalle}\n${errores}` : detalle);
  }

  return datos;
}

function agregarOpcion(select, valor, texto) {
  const opcion = document.createElement("option");
  opcion.value = valor;
  opcion.textContent = texto;
  select.appendChild(opcion);
}

function productosFiltrados() {
  const busqueda = elementos.buscar.value.trim().toLocaleLowerCase("es");
  const categoria = elementos.filtroCategoria.value;
  const estado = elementos.filtroEstado.value;

  return productos.filter((producto) => {
    const coincideBusqueda =
      producto.codigo.toLocaleLowerCase("es").includes(busqueda) ||
      producto.nombre.toLocaleLowerCase("es").includes(busqueda);

    const coincideCategoria =
      categoria === "" || String(producto.categoriaId) === categoria;

    const coincideEstado = estado === "" || String(producto.activo) === estado;

    return coincideBusqueda && coincideCategoria && coincideEstado;
  });
}

function agregarCelda(fila, texto) {
  const celda = document.createElement("td");
  celda.textContent = texto;
  fila.appendChild(celda);
  return celda;
}

function crearBotonFila(texto, accion, peligro = false) {
  const boton = document.createElement("button");
  boton.type = "button";
  boton.textContent = texto;
  boton.className = peligro ? "boton-fila peligro" : "boton-fila";

  boton.disabled = ocupado;
  boton.addEventListener("click", accion);

  return boton;
}

function renderizarProductos() {
  elementos.tabla.replaceChildren();

  const lista = productosFiltrados();

  if (lista.length === 0) {
    const fila = document.createElement("tr");
    const celda = agregarCelda(
      fila,
      "No hay productos que coincidan con los filtros.",
    );

    celda.colSpan = 6;
    celda.className = "fila-vacia";
    elementos.tabla.appendChild(fila);
  }

  lista.forEach((producto) => {
    const fila = document.createElement("tr");

    agregarCelda(fila, producto.codigo);
    agregarCelda(fila, producto.nombre);
    agregarCelda(fila, producto.categoria);
    agregarCelda(fila, producto.unidad);

    const celdaEstado = agregarCelda(fila, "");
    const etiqueta = document.createElement("span");

    etiqueta.textContent = producto.activo ? "Activo" : "Inactivo";
    etiqueta.className = producto.activo ? "estado activo" : "estado inactivo";

    celdaEstado.appendChild(etiqueta);

    const celdaAcciones = agregarCelda(fila, "");

    if (producto.activo) {
      const acciones = document.createElement("div");
      acciones.className = "acciones-fila";

      acciones.appendChild(
        crearBotonFila("Editar", () => editarProducto(producto)),
      );

      acciones.appendChild(
        crearBotonFila("Desactivar", () => desactivarProducto(producto), true),
      );

      celdaAcciones.appendChild(acciones);
    } else {
      celdaAcciones.textContent = "—";
    }

    elementos.tabla.appendChild(fila);
  });

  elementos.contador.textContent = `${lista.length} de ${productos.length} productos`;

  elementos.exportar.disabled = !preparado || ocupado || lista.length === 0;
}

function actualizarControles() {
  elementos.campos.disabled = !preparado || ocupado;
  elementos.nuevo.disabled = !preparado || ocupado;

  elementos.unidad.disabled = productoEnEdicion !== null || ocupado;

  elementos.guardar.textContent = ocupado
    ? "Guardando…"
    : productoEnEdicion !== null
      ? "Guardar cambios"
      : "Guardar";

  renderizarProductos();
}

function limpiarFormulario(enfocar = false) {
  productoEnEdicion = null;

  elementos.formulario.reset();
  elementos.titulo.textContent = "Nuevo producto";

  actualizarControles();

  if (enfocar) {
    elementos.codigo.focus();
  }
}

function editarProducto(producto) {
  if (ocupado) {
    return;
  }

  ocultarMensaje();

  productoEnEdicion = producto.id;

  elementos.codigo.value = producto.codigo;
  elementos.nombre.value = producto.nombre;
  elementos.categoria.value = String(producto.categoriaId);
  elementos.unidad.value = String(producto.unidadId);

  elementos.titulo.textContent = "Editar producto";

  actualizarControles();
  elementos.nombre.focus();
}

async function cargarProductos() {
  const datos = await solicitar("/api/productos");

  if (!Array.isArray(datos)) {
    throw new Error("La lista de productos no tiene el formato esperado.");
  }

  productos = datos;
  renderizarProductos();
}

async function guardarProducto(evento) {
  evento.preventDefault();

  if (!preparado || ocupado) {
    return;
  }

  ocultarMensaje();

  const codigo = elementos.codigo.value.trim();
  const nombre = elementos.nombre.value.trim();

  if (!codigo || !nombre) {
    mostrarMensaje("El código y el nombre son obligatorios.", "error");
    return;
  }

  const datos = {
    codigo,
    nombre,
    categoriaId: Number(elementos.categoria.value),
    unidadId: Number(elementos.unidad.value),
  };

  const editando = productoEnEdicion !== null;
  const url = editando
    ? `/api/productos/${productoEnEdicion}`
    : "/api/productos";

  ocupado = true;
  actualizarControles();

  try {
    await solicitar(url, {
      method: editando ? "PUT" : "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(datos),
    });

    limpiarFormulario();

    mostrarMensaje(
      editando
        ? "Producto actualizado correctamente."
        : "Producto registrado correctamente.",
    );

    try {
      await cargarProductos();
    } catch {
      mostrarMensaje(
        "El producto se guardó, pero no se pudo actualizar la lista. " +
          "Recarga la página para consultar los datos.",
        "error",
      );
    }
  } catch (error) {
    mostrarMensaje(error.message, "error");
  } finally {
    ocupado = false;
    actualizarControles();
  }
}

async function desactivarProducto(producto) {
  if (ocupado) {
    return;
  }

  const confirmado = window.confirm(
    `¿Deseas desactivar "${producto.nombre}"? ` + "El registro se conservará.",
  );

  if (!confirmado) {
    return;
  }

  ocultarMensaje();
  ocupado = true;
  actualizarControles();

  try {
    await solicitar(`/api/productos/${producto.id}/desactivar`, {
      method: "PATCH",
    });

    if (productoEnEdicion === producto.id) {
      limpiarFormulario();
    }

    mostrarMensaje("Producto desactivado correctamente.");

    try {
      await cargarProductos();
    } catch {
      mostrarMensaje(
        "El producto se desactivó, pero no se pudo actualizar la lista. " +
          "Recarga la página.",
        "error",
      );
    }
  } catch (error) {
    mostrarMensaje(error.message, "error");
  } finally {
    ocupado = false;
    actualizarControles();
  }
}

function celdaCsv(valor) {
  let texto = String(valor ?? "");

  // Evita interpretar valores del catálogo como fórmulas en Excel.
  if (/^\s*[=+\-@]/.test(texto)) {
    texto = "'" + texto;
  }

  return '"' + texto.replaceAll('"', '""') + '"';
}

function exportarCsv() {
  const lista = productosFiltrados();

  if (lista.length === 0) {
    return;
  }

  const filas = [
    ["Código", "Producto", "Categoría", "Unidad", "Estado"],
    ...lista.map((producto) => [
      producto.codigo,
      producto.nombre,
      producto.categoria,
      producto.unidad,
      producto.activo ? "Activo" : "Inactivo",
    ]),
  ];

  const contenido = filas
    .map((fila) => fila.map(celdaCsv).join(";"))
    .join("\r\n");

  const archivo = new Blob(["\uFEFF" + contenido], {
    type: "text/csv;charset=utf-8;",
  });

  const url = URL.createObjectURL(archivo);
  const enlace = document.createElement("a");

  enlace.href = url;
  enlace.download = "productos-el-meson.csv";

  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();

  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function iniciar() {
  elementos.nuevo.disabled = true;
  elementos.exportar.disabled = true;

  try {
    const [categorias, unidades, listaProductos] = await Promise.all([
      solicitar("/api/categorias"),
      solicitar("/api/unidades"),
      solicitar("/api/productos"),
    ]);

    if (
      !Array.isArray(categorias) ||
      !Array.isArray(unidades) ||
      !Array.isArray(listaProductos)
    ) {
      throw new Error("El servidor devolvió un catálogo no válido.");
    }

    categorias.forEach((categoria) => {
      agregarOpcion(elementos.categoria, categoria.id, categoria.nombre);

      agregarOpcion(elementos.filtroCategoria, categoria.id, categoria.nombre);
    });

    unidades.forEach((unidad) => {
      agregarOpcion(
        elementos.unidad,
        unidad.id,
        `${unidad.nombre} (${unidad.codigo})`,
      );
    });

    productos = listaProductos;

    if (categorias.length === 0 || unidades.length === 0) {
      throw new Error(
        "Debes registrar categorías y unidades antes de crear productos.",
      );
    }

    preparado = true;
    actualizarControles();
  } catch (error) {
    mostrarMensaje("No se pudo cargar el catálogo. " + error.message, "error");

    elementos.tabla.replaceChildren();

    const fila = document.createElement("tr");
    const celda = agregarCelda(
      fila,
      "No se pudieron cargar los datos. Recarga la página para reintentar.",
    );

    celda.colSpan = 6;
    celda.className = "fila-vacia";

    elementos.tabla.appendChild(fila);
  }
}

elementos.formulario.addEventListener("submit", guardarProducto);

elementos.nuevo.addEventListener("click", () => {
  ocultarMensaje();
  limpiarFormulario(true);
});

elementos.cancelar.addEventListener("click", () => {
  ocultarMensaje();
  limpiarFormulario();
});

elementos.buscar.addEventListener("input", renderizarProductos);
elementos.filtroCategoria.addEventListener("change", renderizarProductos);
elementos.filtroEstado.addEventListener("change", renderizarProductos);

elementos.exportar.addEventListener("click", exportarCsv);

iniciar();