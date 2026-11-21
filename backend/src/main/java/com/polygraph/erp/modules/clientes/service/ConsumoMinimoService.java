package com.polygraph.erp.modules.clientes.service;

import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.AprobacionPospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.tienda.service.ParametrosTiendaService;
import com.polygraph.erp.shared.enums.TipoCliente;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TP-14 · Consumo mínimo del pospago. Cada mes, si un cliente pospago hizo MENOS de {@code pospagoProcesosMinimosMes} solicitudes durante
 * {@code pospagoMesesConsumoBajo} meses completos seguidos, se avisa a su gestor y al administrador. La plataforma NO cambia nada sola: pasar a prepago
 * o ajustar el precio lo decide una persona. Solo con el interruptor moraAutomatica encendido.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumoMinimoService {

    private final ClienteRepository clienteRepository;
    private final ServicioRepository servicioRepository;
    private final AprobacionPospagoRepository aprobacionRepository;
    private final ParametrosTiendaService parametros;
    private final NotificacionService notificacionService;
    private final Clock clock;

    /** Un cliente con consumo bajo y cuánto hizo cada mes (del más antiguo al más reciente). */
    public record Alerta(Integer idCliente, String nombre, List<Long> solicitudesPorMes, int minimo) {}

    /** Regla pura: consumo bajo = TODOS los meses por debajo del mínimo (con un solo mes que llegue al mínimo, no hay alerta). */
    public static boolean consumoBajo(List<Long> porMes, int minimo, int mesesSeguidos) {
        return mesesSeguidos > 0 && porMes.size() >= mesesSeguidos && porMes.stream().allMatch(n -> n < minimo);
    }

    /** Corre una vez al mes. @return los clientes a los que se les avisó. */
    @Transactional
    public List<Alerta> ejecutar() {
        if (!parametros.activo(ParametrosTiendaService.MORA_AUTOMATICA)) return List.of();
        List<Alerta> alertas = evaluar();
        for (Alerta a : alertas) avisar(a);
        log.info("Consumo mínimo pospago: {} cliente(s) con consumo bajo", alertas.size());
        return alertas;
    }

    /** Evalúa sin avisar ni cambiar nada. */
    @Transactional(readOnly = true)
    public List<Alerta> evaluar() {
        int minimo = parametros.entero("pospagoProcesosMinimosMes");
        int meses = parametros.entero("pospagoMesesConsumoBajo");
        YearMonth actual = YearMonth.from(LocalDate.now(clock.withZone(ParametrosTiendaService.ZONA)));
        LocalDate inicioVentana = actual.minusMonths(meses).atDay(1);
        List<Alerta> r = new ArrayList<>();
        for (Cliente c : clienteRepository.findAll()) {
            if (c.getTipoCliente() != TipoCliente.POSPAGO) continue;
            // un cliente aprobado hace menos de la ventana no tiene todos esos meses como pospago: no se evalúa todavía
            var aprobacion = aprobacionRepository.findFirstByIdClienteOrderByAprobadoEnDesc(c.getIdCliente());
            if (aprobacion.isPresent() && aprobacion.get().getAprobadoEn().toLocalDate().isAfter(inicioVentana)) continue;
            List<Long> porMes = new ArrayList<>();
            for (int i = meses; i >= 1; i--) {
                YearMonth m = actual.minusMonths(i);
                porMes.add(servicioRepository.contarNoCanceladosEntre(c.getIdCliente(), m.atDay(1), m.atEndOfMonth()));
            }
            if (consumoBajo(porMes, minimo, meses)) r.add(new Alerta(c.getIdCliente(), nombre(c), porMes, minimo));
        }
        return r;
    }

    private void avisar(Alerta a) {
        String detalle = a.solicitudesPorMes().stream().map(String::valueOf).collect(Collectors.joining(", "));
        String mensaje = a.nombre() + " hizo " + detalle + " solicitudes en los últimos " + a.solicitudesPorMes().size()
                + " meses (mínimo " + a.minimo() + " al mes). Decide si lo pasas a prepago o ajustas el precio; la plataforma no cambia nada sola.";
        clienteRepository.findById(a.idCliente()).ifPresent(c -> {
            if (c.getIdGestor() != null) notificacionService.crearParaUsuario(c.getIdGestor(), "CONSUMO_MINIMO", "Consumo bajo de un cliente pospago", mensaje);
        });
        notificacionService.crearParaAdmins("CONSUMO_MINIMO", "Consumo bajo de un cliente pospago", mensaje);
    }

    private static String nombre(Cliente c) {
        if (c.getRazonSocial() != null && !c.getRazonSocial().isBlank()) return c.getRazonSocial();
        return ((c.getNombre() != null ? c.getNombre() : "") + (c.getApellido() != null ? " " + c.getApellido() : "")).trim();
    }
}
