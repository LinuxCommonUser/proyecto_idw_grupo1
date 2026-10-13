package com.proyecto.utp.proyecto_dwi.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.proyecto.utp.proyecto_dwi.domain.Categoria;
import com.proyecto.utp.proyecto_dwi.domain.Producto;
import com.proyecto.utp.proyecto_dwi.domain.Unidad;
import com.proyecto.utp.proyecto_dwi.dto.ProductoRequest;
import com.proyecto.utp.proyecto_dwi.dto.ProductoResponse;
import com.proyecto.utp.proyecto_dwi.repository.CategoriaRepository;
import com.proyecto.utp.proyecto_dwi.repository.ProductoRepository;
import com.proyecto.utp.proyecto_dwi.repository.UnidadRepository;

@Service
@Transactional(readOnly = true)
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UnidadRepository unidadRepository;
    private final AccesoService acceso;

    public ProductoService(
            ProductoRepository productoRepository,
            CategoriaRepository categoriaRepository,
            UnidadRepository unidadRepository,
            AccesoService acceso
    ) {
        this.productoRepository = productoRepository;
        this.categoriaRepository = categoriaRepository;
        this.unidadRepository = unidadRepository;
        this.acceso = acceso;
    }

    public List<ProductoResponse> listar() {
        return productoRepository.findAllByOrderByNombreAsc()
                .stream()
                .map(this::convertir)
                .toList();
    }

    public ProductoResponse obtener(Long id) {
        return convertir(buscarProducto(id));
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest request) {

        acceso.bloquear();
        acceso.admin();
        String codigo = normalizarCodigo(request.codigo());

        if (productoRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe un producto con ese código"
            );
        }

        Categoria categoria = buscarCategoria(request.categoriaId());
        Unidad unidad = buscarUnidad(request.unidadId());

        Producto producto = new Producto(
                codigo,
                request.nombre().strip(),
                categoria,
                unidad
        );

        Producto guardado = productoRepository.saveAndFlush(producto);
        acceso.audit("GUARDAR_PRODUCTO", "producto", guardado.getId(), guardado.getCodigo());
        return convertir(guardado);
    }

    @Transactional
    public ProductoResponse actualizar(
            Long id,
            ProductoRequest request
    ) {
        Producto producto = buscarProducto(id);

        if (!producto.isActivo()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede editar un producto desactivado"
            );
        }

        acceso.bloquear();
        acceso.admin();
        String codigo = normalizarCodigo(request.codigo());

        if (productoRepository.existsByCodigoIgnoreCaseAndIdNot(
                codigo,
                id
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Otro producto ya utiliza ese código"
            );
        }

        if (!producto.getUnidad().getId().equals(request.unidadId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La unidad base no se puede cambiar"
            );
        }

        Categoria categoria = buscarCategoria(request.categoriaId());

        producto.actualizar(
                codigo,
                request.nombre().strip(),
                categoria
        );

        Producto guardado = productoRepository.saveAndFlush(producto);
        acceso.audit("GUARDAR_PRODUCTO", "producto", guardado.getId(), guardado.getCodigo());
        return convertir(guardado);
    }

    @Transactional
    public void desactivar(Long id) {
        acceso.bloquear(); acceso.admin();
        Producto producto = buscarProducto(id);
        acceso.productoDesactivable(id);
        producto.desactivar();
        acceso.audit("DESACTIVAR_PRODUCTO", "producto", id, producto.getCodigo());
    }

    @Transactional
    public void activar(Long id) { acceso.bloquear(); acceso.admin(); buscarProducto(id).activar(); acceso.audit("ACTIVAR_PRODUCTO", "producto", id, "Reactivación"); }

    private Producto buscarProducto(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Producto no encontrado"
                ));
    }

    private Categoria buscarCategoria(Integer id) {

        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La categoría seleccionada no existe"
                ));

        if (!categoria.isActivo()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La categoría está desactivada"
            );
        }

        return categoria;
    }

    private Unidad buscarUnidad(Integer id) {
        return unidadRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La unidad seleccionada no existe"
                ));
    }

    private String normalizarCodigo(String codigo) {
        return codigo.strip().toUpperCase(Locale.ROOT);
    }

    private ProductoResponse convertir(Producto producto) {
        return new ProductoResponse(
                producto.getId(),
                producto.getCodigo(),
                producto.getNombre(),
                producto.getCategoria().getId(),
                producto.getCategoria().getNombre(),
                producto.getUnidad().getId(),
                producto.getUnidad().getCodigo(),
                producto.isActivo()
        );
    }
}