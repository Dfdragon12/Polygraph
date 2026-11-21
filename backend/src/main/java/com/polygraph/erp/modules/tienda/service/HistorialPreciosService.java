package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.tienda.dto.PreciosPlanDtos.HistorialPrecioResponse;
import com.polygraph.erp.modules.tienda.entity.HistorialPrecioProceso;
import com.polygraph.erp.modules.tienda.repository.HistorialPrecioProcesoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Historial de cambios de precio y costo de un servicio, con usuario y fecha en hora de Colombia (regla 10). */
@Service
@RequiredArgsConstructor
public class HistorialPreciosService {

    private final HistorialPrecioProcesoRepository repository;
    private final Clock clock;

    /** Registra solo si el valor realmente cambió. */
    @Transactional
    public void registrar(Integer idProceso, String campo, BigDecimal anterior, BigDecimal nuevo) {
        if (sonIguales(anterior, nuevo)) return;
        repository.save(HistorialPrecioProceso.builder()
                .idProceso(idProceso)
                .campo(campo)
                .valorAnterior(texto(anterior))
                .valorNuevo(texto(nuevo))
                .usuario(usuarioActual())
                .fecha(LocalDateTime.now(clock.withZone(ParametrosTiendaService.ZONA)))
                .build());
    }

    @Transactional(readOnly = true)
    public List<HistorialPrecioResponse> listar(Integer idProceso) {
        return repository.findTop200ByIdProcesoOrderByFechaDescIdHistorialDesc(idProceso).stream()
                .map(h -> new HistorialPrecioResponse(h.getFecha(), h.getUsuario(), h.getCampo(), h.getValorAnterior(), h.getValorNuevo()))
                .toList();
    }

    static boolean sonIguales(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    private static String texto(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private static String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return Objects.requireNonNullElse(auth != null ? auth.getName() : null, "sistema");
    }
}
