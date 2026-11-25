package com.polygraph.erp.modules.tienda.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.tienda.entity.HistorialPrecioPospago;
import com.polygraph.erp.modules.tienda.entity.HistorialPrecioPospago.Accion;
import com.polygraph.erp.modules.tienda.repository.HistorialPrecioPospagoRepository;
import com.polygraph.erp.shared.enums.ModoPrecioPospago;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LP-03: el administrador edita la lista de precios pospago (precio manual, volver al plan de 100, ofrecido, restablecer todo).
 * Solo con el interruptor «Lista de precios pospago» encendido; apagado, todo responde 409. El modo, el precio manual y «ofrecido» viven en
 * {@code procesos} (decisión LP-00, opción A); cada cambio deja una fila en el historial (solo inserción). Los precios son pesos enteros, sin IVA.
 * No se pide motivo y no se bloquea un precio por debajo del costo: solo queda marcado con {@code margenBajo}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListaPospagoAdminService {

    static final String APAGADA = "La lista de precios pospago está apagada en Admin › Catálogo";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final PrecioPospagoService precios;
    private final ProcesoRepository procesoRepository;
    private final UsuarioRepository usuarioRepository;
    private final HistorialPrecioPospagoRepository historial;
    private final Clock reloj;

    /** Una fila del historial para el panel lateral (sin motivo). */
    public record ItemHistorial(Long id, String accion, String modoAnterior, String modoNuevo, Long precioAnterior, Long precioNuevo,
                                Boolean ofrecidoAnterior, Boolean ofrecidoNuevo, String usuario, LocalDateTime fecha) {}

    @Transactional
    public PrecioPospagoService.Fila actualizar(Integer idProceso, String modo, BigDecimal precioManual, Boolean ofrecido, String emailAdmin) {
        exigirEncendida();
        Usuario admin = admin(emailAdmin);
        Proceso p = procesoRepository.findById(idProceso).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));

        boolean manual = "MANUAL".equals(modo);
        if (!manual && !"AUTO".equals(modo)) throw new ApiException("El modo debe ser AUTO o MANUAL", HttpStatus.BAD_REQUEST);
        if (manual) {
            if (precioManual == null || precioManual.signum() <= 0) throw new ApiException("El precio debe ser mayor que 0", HttpStatus.BAD_REQUEST);
            if (precioManual.stripTrailingZeros().scale() > 0) throw new ApiException("El precio debe ser en pesos enteros, sin decimales", HttpStatus.BAD_REQUEST);
        }

        ModoPrecioPospago modoAntes = precios.modo(p);
        BigDecimal precioAntes = precios.precioEfectivo(p);
        boolean ofrecidoAntes = !Boolean.FALSE.equals(p.getOfrecerPospago());
        BigDecimal manualAntes = p.getPrecioPospagoPropio();

        ModoPrecioPospago modoDespues = manual ? ModoPrecioPospago.PROPIO : ModoPrecioPospago.PLAN_100;
        boolean ofrecidoDespues = ofrecido == null ? ofrecidoAntes : ofrecido;

        p.setModoPrecioPospago(modoDespues);
        p.setPrecioPospagoPropio(manual ? precioManual.setScale(2) : null);
        p.setOfrecerPospago(ofrecidoDespues);
        BigDecimal precioDespues = precios.precioEfectivo(p);

        boolean cambioPrecio = modoAntes != modoDespues || (manual && (manualAntes == null || manualAntes.compareTo(precioManual) != 0));
        boolean cambioOfrecido = ofrecidoAntes != ofrecidoDespues;
        if (cambioPrecio || cambioOfrecido) {
            LocalDateTime ahora = LocalDateTime.now(reloj.withZone(BOGOTA));
            p.setPrecioPospagoActualizadoPor(admin.getIdUsuario());
            p.setPrecioPospagoActualizadoEn(ahora);
            procesoRepository.save(p);
            if (cambioPrecio) {
                insertar(p, manual ? Accion.PRECIO_MANUAL : Accion.VOLVER_AUTO, modoAntes, modoDespues, precioAntes, precioDespues,
                        ofrecidoAntes, ofrecidoAntes, admin, ahora);
            }
            if (cambioOfrecido) {
                insertar(p, ofrecidoDespues ? Accion.OFRECIDO_SI : Accion.OFRECIDO_NO, modoDespues, modoDespues, precioDespues, precioDespues,
                        ofrecidoAntes, ofrecidoDespues, admin, ahora);
            }
            log.info("Lista pospago: servicio={} modo={} precio={} ofrecido={} por={}", idProceso, modoDespues, precioDespues, ofrecidoDespues, admin.getIdUsuario());
        }
        return precios.filaDe(p);
    }

    /** Pone TODOS los servicios en Automático; no cambia «ofrecido». Devuelve la lista completa ya actualizada. */
    @Transactional
    public List<PrecioPospagoService.Fila> restablecerTodos(String emailAdmin) {
        exigirEncendida();
        Usuario admin = admin(emailAdmin);
        LocalDateTime ahora = LocalDateTime.now(reloj.withZone(BOGOTA));
        for (Proceso p : procesoRepository.findAll()) {
            if (precios.modo(p) != ModoPrecioPospago.PROPIO) continue;
            BigDecimal antes = precios.precioEfectivo(p);
            boolean ofrecido = !Boolean.FALSE.equals(p.getOfrecerPospago());
            p.setModoPrecioPospago(ModoPrecioPospago.PLAN_100);
            p.setPrecioPospagoPropio(null);
            p.setPrecioPospagoActualizadoPor(admin.getIdUsuario());
            p.setPrecioPospagoActualizadoEn(ahora);
            procesoRepository.save(p);
            insertar(p, Accion.RESTABLECER_TODOS, ModoPrecioPospago.PROPIO, ModoPrecioPospago.PLAN_100, antes, precios.precioEfectivo(p), ofrecido, ofrecido, admin, ahora);
        }
        return precios.lista();
    }

    @Transactional(readOnly = true)
    public List<ItemHistorial> historial(Integer idProceso) {
        exigirEncendida();
        List<HistorialPrecioPospago> filas = historial.findTop50ByIdProcesoOrderByFechaDescIdDesc(idProceso);
        List<Long> ids = filas.stream().map(HistorialPrecioPospago::getIdUsuario).distinct().toList();
        Map<Long, String> nombres = ids.isEmpty() ? Map.of() : usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getIdUsuario, u -> (nz(u.getNombre()) + " " + nz(u.getApellido())).trim(), (a, b) -> a));
        return filas.stream().map(h -> new ItemHistorial(h.getId(), h.getAccion().name(), h.getModoAnterior(), h.getModoNuevo(), h.getPrecioAnterior(),
                h.getPrecioNuevo(), h.getOfrecidoAnterior(), h.getOfrecidoNuevo(), nombres.getOrDefault(h.getIdUsuario(), "—"), h.getFecha())).toList();
    }

    // ───────────────────────── internos ─────────────────────────

    private void exigirEncendida() {
        if (!precios.activo()) throw new ApiException(APAGADA, HttpStatus.CONFLICT);
    }

    private Usuario admin(String email) {
        return usuarioRepository.findByEmail(email).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    private void insertar(Proceso p, Accion accion, ModoPrecioPospago modoAntes, ModoPrecioPospago modoDespues, BigDecimal precioAntes, BigDecimal precioDespues,
                          boolean ofrecidoAntes, boolean ofrecidoDespues, Usuario admin, LocalDateTime fecha) {
        historial.save(HistorialPrecioPospago.builder().idProceso(p.getIdProceso()).accion(accion)
                .modoAnterior(etiqueta(modoAntes)).modoNuevo(etiqueta(modoDespues))
                .precioAnterior(precioAntes == null ? null : precioAntes.longValue()).precioNuevo(precioDespues == null ? null : precioDespues.longValue())
                .ofrecidoAnterior(ofrecidoAntes).ofrecidoNuevo(ofrecidoDespues).idUsuario(admin.getIdUsuario()).fecha(fecha).build());
    }

    private static String etiqueta(ModoPrecioPospago m) {
        return m == ModoPrecioPospago.PROPIO ? "MANUAL" : "AUTO";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
