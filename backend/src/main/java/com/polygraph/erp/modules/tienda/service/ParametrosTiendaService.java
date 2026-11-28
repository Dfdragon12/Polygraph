package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.tienda.entity.ParametroTienda;
import com.polygraph.erp.modules.tienda.entity.ParametroTiendaAuditoria;
import com.polygraph.erp.modules.tienda.repository.ParametroTiendaAuditoriaRepository;
import com.polygraph.erp.modules.tienda.repository.ParametroTiendaRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/** Lectura y cambio (con auditoría) de los parámetros globales e interruptores de la tienda y el pospago. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParametrosTiendaService {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public static final String COBRO_CON_IVA = "cobroConIva";
    public static final String TIENDA_POR_PLANES = "tiendaPorPlanes";
    public static final String VIGENCIA_POR_PLAN = "vigenciaPorPlan";
    public static final String COBERTURA_POR_SEDE = "coberturaPorSede";
    public static final String POSPAGO_PLAN_100 = "pospagoPlan100";
    public static final String ALTA_POSPAGO_CONTROLADA = "altaPospagoControlada";
    public static final String MORA_AUTOMATICA = "moraAutomatica";

    private final ParametroTiendaRepository repository;
    private final ParametroTiendaAuditoriaRepository auditoriaRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<String> texto(String clave) {
        return repository.findById(clave).map(ParametroTienda::getValor);
    }

    @Transactional(readOnly = true)
    public java.util.List<ParametroTienda> listar() {
        return repository.findAll().stream().sorted(java.util.Comparator.comparing(ParametroTienda::getClave)).toList();
    }

    /** Interruptor: un parámetro que no existe o no vale "true" está APAGADO. */
    @Transactional(readOnly = true)
    public boolean activo(String clave) {
        return texto(clave).map(v -> "true".equalsIgnoreCase(v.trim())).orElse(false);
    }

    @Transactional(readOnly = true)
    public int entero(String clave) {
        return Integer.parseInt(requerido(clave).trim());
    }

    @Transactional(readOnly = true)
    public BigDecimal decimal(String clave) {
        return new BigDecimal(requerido(clave).trim());
    }

    /** Cambia un valor y deja el registro con usuario, fecha, valor anterior y nuevo (regla 10). */
    @Transactional
    public void cambiar(String clave, String valorNuevo, String usuario) {
        ParametroTienda p = repository.findById(clave)
                .orElseThrow(() -> new ApiException("Parámetro no encontrado: " + clave, HttpStatus.NOT_FOUND));
        validarTipo(p.getTipo(), valorNuevo);
        String anterior = p.getValor();
        if (anterior.equals(valorNuevo)) return;
        LocalDateTime ahora = LocalDateTime.now(clock.withZone(ZONA));
        p.setValor(valorNuevo);
        p.setActualizadoPor(usuario);
        p.setFechaActualizacion(ahora);
        repository.save(p);
        auditoriaRepository.save(ParametroTiendaAuditoria.builder()
                .clave(clave).valorAnterior(anterior).valorNuevo(valorNuevo).usuario(usuario).fecha(ahora).build());
        log.info("Parámetro de tienda cambiado: {} '{}' -> '{}' por {}", clave, anterior, valorNuevo, usuario);
    }

    private String requerido(String clave) {
        return texto(clave).orElseThrow(() -> new ApiException("Falta el parámetro " + clave, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private void validarTipo(String tipo, String valor) {
        try {
            switch (tipo) {
                case "ENTERO" -> Integer.parseInt(valor.trim());
                case "DECIMAL" -> new BigDecimal(valor.trim());
                case "BOOLEANO" -> {
                    if (!"true".equalsIgnoreCase(valor.trim()) && !"false".equalsIgnoreCase(valor.trim())) {
                        throw new NumberFormatException();
                    }
                }
                default -> { }
            }
        } catch (NumberFormatException e) {
            throw new ApiException("El valor '" + valor + "' no es válido para un parámetro " + tipo, HttpStatus.BAD_REQUEST);
        }
    }
}
