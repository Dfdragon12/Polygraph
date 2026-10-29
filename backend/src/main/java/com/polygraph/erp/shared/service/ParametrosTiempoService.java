package com.polygraph.erp.shared.service;

import com.polygraph.erp.shared.entity.ParametroTiempo;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.repository.ParametroTiempoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** Lee (con caché) los parámetros de la tabla parametros_tiempo; lo que falte o esté mal escrito cae al valor por defecto. */
@Service
public class ParametrosTiempoService {

    public static final String ZONA = "zona_horaria";
    public static final String HORA_CORTE = "hora_corte";
    public static final String UMBRAL = "umbral_por_vencer_dias";
    public static final String REPROGRAMACION = "reprogramacion_polygraph_mide";

    private final ParametroTiempoRepository repository;
    private final ParametrosTiempo fijos;
    private volatile ParametrosTiempo cache;

    @Autowired
    public ParametrosTiempoService(ParametroTiempoRepository repository) {
        this.repository = repository;
        this.fijos = null;
    }

    /** Para pruebas: parámetros fijos, sin base de datos. */
    public ParametrosTiempoService(ParametrosTiempo fijos) {
        this.repository = null;
        this.fijos = fijos;
    }

    public ParametrosTiempo actuales() {
        if (fijos != null) return fijos;
        ParametrosTiempo actual = cache;
        if (actual == null) {
            actual = leer();
            cache = actual;
        }
        return actual;
    }

    /** Un parámetro para mostrarlo y editarlo en la pantalla del administrador. */
    public record ParametroDto(String clave, String valor, String descripcion) {}

    @Transactional(readOnly = true)
    public java.util.List<ParametroDto> listar() {
        if (repository == null) return java.util.List.of();
        return repository.findAll().stream().sorted(java.util.Comparator.comparing(ParametroTiempo::getClave))
                .map(p -> new ParametroDto(p.getClave(), p.getValor(), p.getDescripcion())).toList();
    }

    public void recargar() {
        cache = null;
    }

    private ParametrosTiempo leer() {
        ParametrosTiempo d = ParametrosTiempo.POR_DEFECTO;
        ZoneId zona = d.zona();
        LocalTime hora = d.horaCorte();
        int umbral = d.umbralPorVencerDias();
        boolean contraOriginal = d.polygraphMideContraOriginal();
        for (ParametroTiempo p : repository.findAll()) {
            try {
                switch (p.getClave()) {
                    case ZONA -> zona = ZoneId.of(p.getValor().trim());
                    case HORA_CORTE -> hora = LocalTime.parse(p.getValor().trim());
                    case UMBRAL -> umbral = Math.max(0, Integer.parseInt(p.getValor().trim()));
                    case REPROGRAMACION -> contraOriginal = !"VIGENTE".equalsIgnoreCase(p.getValor().trim());
                    default -> { }
                }
            } catch (DateTimeException | NumberFormatException ignorado) {
                // valor mal escrito: queda el de por defecto
            }
        }
        return new ParametrosTiempo(zona, hora, umbral, contraOriginal);
    }

    /** Cambia un parámetro (con validación). Lo usará la pantalla de configuración del administrador. */
    @Transactional
    public void actualizar(String clave, String valor, String usuario) {
        String limpio = valor == null ? "" : valor.trim();
        try {
            switch (clave) {
                case ZONA -> ZoneId.of(limpio);
                case HORA_CORTE -> LocalTime.parse(limpio);
                case UMBRAL -> { if (Integer.parseInt(limpio) < 0) throw new NumberFormatException(); }
                case REPROGRAMACION -> { if (!limpio.equalsIgnoreCase("ORIGINAL") && !limpio.equalsIgnoreCase("VIGENTE")) throw new NumberFormatException(); }
                default -> throw new ApiException("Parámetro desconocido: " + clave, HttpStatus.NOT_FOUND);
            }
        } catch (DateTimeException | NumberFormatException e) {
            throw new ApiException("Valor no válido para " + clave + ".", HttpStatus.BAD_REQUEST);
        }
        ParametroTiempo p = repository.findById(clave).orElseGet(() -> ParametroTiempo.builder().clave(clave).build());
        p.setValor(limpio);
        p.setModificadoPor(usuario);
        p.setFechaModificacion(LocalDateTime.now());
        repository.save(p);
        recargar();
    }
}
