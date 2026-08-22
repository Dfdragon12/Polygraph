package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.DescuentoRequest;
import com.polygraph.erp.modules.catalogo.dto.DescuentoResponse;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.Descuento;
import com.polygraph.erp.modules.catalogo.repository.ClasificacionProcesoRepository;
import com.polygraph.erp.modules.catalogo.repository.DescuentoRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.shared.enums.TipoDescuento;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Motor de descuentos: reglas dinámicas (% o monto fijo, con o sin código, con o sin vigencia)
 * aplicables a todo el catálogo, una categoría o un proceso puntual — en vez de precios de
 * promoción hardcodeados. El precio final SIEMPRE se recalcula aquí, nunca se confía en un
 * descuento que venga del frontend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DescuentoService {

    private final DescuentoRepository descuentoRepository;
    private final ClasificacionProcesoRepository clasificacionRepository;
    private final ProcesoRepository procesoRepository;

    /** Aplica el mejor descuento vigente para este proceso (por código si se indicó, si no automático) sobre precioBase. */
    @Transactional(readOnly = true)
    public BigDecimal aplicarMejorDescuento(Proceso proceso, BigDecimal precioBase, String codigoCupon) {
        LocalDateTime ahora = LocalDateTime.now();
        List<Descuento> vigentes = descuentoRepository.findVigentesParaProceso(
                proceso.getIdProceso(), proceso.getClasificacion().getIdClasificacion(), ahora);

        List<Descuento> aplicables = seleccionarAplicables(vigentes, codigoCupon);

        return aplicables.stream()
                .map(d -> calcularPrecioConDescuento(d, precioBase))
                .min(Comparator.naturalOrder())
                .orElse(precioBase);
    }

    /** Si hay cupón, solo ese código (si es válido para este alcance); si no, los automáticos (sin código). */
    private List<Descuento> seleccionarAplicables(List<Descuento> vigentes, String codigoCupon) {
        if (codigoCupon != null && !codigoCupon.isBlank()) {
            return vigentes.stream()
                    .filter(d -> codigoCupon.equalsIgnoreCase(d.getCodigo()))
                    .toList();
        }
        return vigentes.stream().filter(d -> d.getCodigo() == null).toList();
    }

    private BigDecimal calcularPrecioConDescuento(Descuento d, BigDecimal precioBase) {
        BigDecimal reduccion = d.getTipo() == TipoDescuento.PORCENTAJE
                ? precioBase.multiply(d.getValor()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : d.getValor();
        if (d.getMontoMaximoDescuento() != null) {
            reduccion = reduccion.min(d.getMontoMaximoDescuento());
        }
        return precioBase.subtract(reduccion).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<DescuentoResponse> listar() {
        return descuentoRepository.findAllByOrderByFechaCreacionDesc().stream().map(this::mapear).toList();
    }

    @Transactional
    public DescuentoResponse crear(DescuentoRequest request) {
        Descuento descuento = new Descuento();
        aplicarCampos(descuento, request);
        descuentoRepository.save(descuento);
        log.info("Descuento creado: id={}, nombre={}, alcance={}", descuento.getIdDescuento(), descuento.getNombre(), descuento.getAlcance());
        return mapear(descuento);
    }

    @Transactional
    public DescuentoResponse actualizar(Long id, DescuentoRequest request) {
        Descuento descuento = descuentoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Descuento no encontrado", HttpStatus.NOT_FOUND));
        aplicarCampos(descuento, request);
        descuentoRepository.save(descuento);
        return mapear(descuento);
    }

    @Transactional
    public DescuentoResponse cambiarActivo(Long id, boolean activo) {
        Descuento descuento = descuentoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Descuento no encontrado", HttpStatus.NOT_FOUND));
        descuento.setActivo(activo);
        descuentoRepository.save(descuento);
        return mapear(descuento);
    }

    private void aplicarCampos(Descuento descuento, DescuentoRequest request) {
        descuento.setNombre(request.nombre());
        descuento.setCodigo(request.codigo() != null && !request.codigo().isBlank() ? request.codigo().toUpperCase() : null);
        descuento.setTipo(request.tipo());
        descuento.setValor(request.valor());
        descuento.setAlcance(request.alcance());
        descuento.setMontoMaximoDescuento(request.montoMaximoDescuento());
        descuento.setFechaInicio(request.fechaInicio());
        descuento.setFechaFin(request.fechaFin());
        descuento.setActivo(request.activo() != null ? request.activo() : Boolean.TRUE);

        descuento.setClasificacion(null);
        descuento.setProceso(null);

        switch (request.alcance()) {
            case CATEGORIA -> descuento.setClasificacion(resolverClasificacion(request.idClasificacion()));
            case PROCESO -> descuento.setProceso(resolverProceso(request.idProceso()));
            case GLOBAL -> { /* sin referencia */ }
        }
    }

    private ClasificacionProceso resolverClasificacion(Integer id) {
        if (id == null) {
            throw new ApiException("Debe indicar la categoría para un descuento de alcance CATEGORIA", HttpStatus.BAD_REQUEST);
        }
        return clasificacionRepository.findById(id)
                .orElseThrow(() -> new ApiException("Categoría no encontrada: " + id, HttpStatus.BAD_REQUEST));
    }

    private Proceso resolverProceso(Integer id) {
        if (id == null) {
            throw new ApiException("Debe indicar el proceso para un descuento de alcance PROCESO", HttpStatus.BAD_REQUEST);
        }
        return procesoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Proceso no encontrado: " + id, HttpStatus.BAD_REQUEST));
    }

    private DescuentoResponse mapear(Descuento d) {
        return new DescuentoResponse(
                d.getIdDescuento(),
                d.getNombre(),
                d.getCodigo(),
                d.getTipo().name(),
                d.getValor(),
                d.getAlcance().name(),
                d.getClasificacion() != null ? d.getClasificacion().getIdClasificacion() : null,
                d.getClasificacion() != null ? d.getClasificacion().getNombre() : null,
                d.getProceso() != null ? d.getProceso().getIdProceso() : null,
                d.getProceso() != null ? d.getProceso().getNombreProceso() : null,
                d.getMontoMaximoDescuento(),
                d.getFechaInicio(),
                d.getFechaFin(),
                d.getActivo(),
                d.getFechaCreacion());
    }
}
