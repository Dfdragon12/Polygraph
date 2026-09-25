package com.polygraph.erp.modules.riesgo.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.riesgo.dto.GuardarParametrosRiesgoRequest;
import com.polygraph.erp.modules.riesgo.dto.ParametrosRiesgoResponse;
import com.polygraph.erp.modules.riesgo.entity.ParametrosRiesgo;
import com.polygraph.erp.modules.riesgo.entity.UmbralRiesgo;
import com.polygraph.erp.modules.riesgo.repository.ParametrosRiesgoRepository;
import com.polygraph.erp.modules.riesgo.repository.UmbralRiesgoRepository;
import com.polygraph.erp.shared.enums.CriticidadCargo;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Configuración global del motor de riesgo (docs/modelo-calificacion-riesgo.md §2.4 y §2.5):
 * umbrales de nivel por criticidad y parámetros de las reglas de piso. Umbrales y pisos comparten
 * una sola versión (parametros_riesgo.version), que sube con cada cambio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParametrosRiesgoService {

    private final UmbralRiesgoRepository umbralRepository;
    private final ParametrosRiesgoRepository parametrosRepository;
    private final NotificacionService notificacionService;

    @Transactional(readOnly = true)
    public ParametrosRiesgoResponse obtener() {
        return toResponse(parametros(), umbralRepository.findAll());
    }

    @Transactional
    public ParametrosRiesgoResponse guardar(GuardarParametrosRiesgoRequest req, Usuario actor) {
        Set<CriticidadCargo> recibidas = EnumSet.noneOf(CriticidadCargo.class);
        for (GuardarParametrosRiesgoRequest.Umbral u : req.umbrales()) {
            CriticidadCargo c = criticidad(u.criticidad());
            if (!recibidas.add(c)) throw new ApiException("La criticidad " + c + " está repetida.", HttpStatus.BAD_REQUEST);
            validarUmbral(c, u);
        }
        if (!recibidas.equals(EnumSet.allOf(CriticidadCargo.class))) {
            throw new ApiException("Deben venir los umbrales de las 3 criticidades.", HttpStatus.BAD_REQUEST);
        }

        for (GuardarParametrosRiesgoRequest.Umbral u : req.umbrales()) {
            CriticidadCargo c = criticidad(u.criticidad());
            UmbralRiesgo umbral = umbralRepository.findById(c).orElseGet(() -> UmbralRiesgo.builder().criticidad(c).build());
            umbral.setLimiteBajo(escala(u.limiteBajo()));
            umbral.setLimiteMedio(escala(u.limiteMedio()));
            umbral.setMinimoMedio(escala(u.minimoMedio()));
            umbralRepository.save(umbral);
        }

        ParametrosRiesgo p = parametros();
        p.setUmbralServicioAlto(escala(req.umbralServicioAlto()));
        p.setUmbralServicioModerado(escala(req.umbralServicioModerado()));
        p.setCantidadServiciosModerados(req.cantidadServiciosModerados());
        p.setVersion(p.getVersion() + 1);
        p.setActualizadoPor(actor);
        p.setFechaActualizacion(LocalDateTime.now());

        notificacionService.crearParaAdmins("CATALOGO", "Parámetros de riesgo actualizados",
                "Se actualizaron los umbrales y pisos del motor de riesgo (versión " + p.getVersion() + ").");
        log.info("Parámetros de riesgo actualizados a versión {} por {}", p.getVersion(), actor.getIdUsuario());
        return toResponse(p, umbralRepository.findAll());
    }

    /** BAJO si <= limiteBajo; MEDIO si <= limiteMedio; ALTO si mayor. El piso sube a minimoMedio,
     *  que debe caer dentro de MEDIO. */
    static void validarUmbral(CriticidadCargo c, GuardarParametrosRiesgoRequest.Umbral u) {
        String nombre = c.name().charAt(0) + c.name().substring(1).toLowerCase();
        if (u.limiteBajo().compareTo(u.limiteMedio()) >= 0) {
            throw new ApiException(nombre + ": el límite de BAJO debe ser menor que el de MEDIO.", HttpStatus.BAD_REQUEST);
        }
        if (u.minimoMedio().compareTo(u.limiteBajo()) <= 0 || u.minimoMedio().compareTo(u.limiteMedio()) > 0) {
            throw new ApiException(nombre + ": el mínimo de MEDIO debe ser mayor que el límite de BAJO y no pasar el de MEDIO.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static CriticidadCargo criticidad(String valor) {
        try {
            return CriticidadCargo.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new ApiException("Criticidad no válida: " + valor, HttpStatus.BAD_REQUEST);
        }
    }

    private ParametrosRiesgo parametros() {
        return parametrosRepository.findById(ParametrosRiesgo.ID_UNICO)
                .orElseThrow(() -> new ApiException("Faltan los parámetros de riesgo (migración V190).", HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private static BigDecimal escala(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private ParametrosRiesgoResponse toResponse(ParametrosRiesgo p, List<UmbralRiesgo> umbrales) {
        Usuario u = p.getActualizadoPor();
        return new ParametrosRiesgoResponse(
                umbrales.stream().sorted(Comparator.comparing(UmbralRiesgo::getCriticidad))
                        .map(x -> new ParametrosRiesgoResponse.Umbral(x.getCriticidad().name(), x.getLimiteBajo(), x.getLimiteMedio(), x.getMinimoMedio()))
                        .toList(),
                p.getUmbralServicioAlto(), p.getUmbralServicioModerado(), p.getCantidadServiciosModerados(),
                p.getVersion(),
                u != null ? (u.getNombre() + (u.getApellido() != null ? " " + u.getApellido() : "")).trim() : null,
                p.getFechaActualizacion());
    }
}
