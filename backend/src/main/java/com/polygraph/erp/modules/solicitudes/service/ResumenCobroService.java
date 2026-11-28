package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.pagos.service.BolsaPrepagoService;
import com.polygraph.erp.modules.pagos.service.RecargoPendienteService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.solicitudes.dto.ResumenCobroResponse;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** NS-11: qué se usa y qué queda, para el resumen del paso 3. No cobra nada. */
@Service
@RequiredArgsConstructor
public class ResumenCobroService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final ProcesoRepository procesoRepository;
    private final CiudadRepository ciudadRepository;
    private final BolsaPrepagoService bolsa;
    private final ClientePospagoRepository clientePospagoRepository;
    private final RecargoPendienteService recargoPendienteService;
    private final com.polygraph.erp.modules.tienda.service.IvaService ivaService;
    private final com.polygraph.erp.modules.pagos.service.SaldoServicioClienteService saldoService;
    private final com.polygraph.erp.modules.tienda.service.ParametrosTiendaService parametros;
    private final com.polygraph.erp.modules.tienda.service.PrecioPospagoService precioPospagoService;

    @Transactional(readOnly = true)
    public ResumenCobroResponse resumir(String emailUsuario, Integer idProceso, Integer idCiudad) {
        var usuario = usuarioRepository.findByEmail(emailUsuario).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Cliente cliente = clienteRepository.findById(usuario.getIdCliente()).orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        Proceso proceso = procesoRepository.findById(idProceso).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        String categoria = proceso.getClasificacion() != null ? proceso.getClasificacion().getNombre() : null;

        Ciudad ciudad = idCiudad != null ? ciudadRepository.findById(idCiudad).orElse(null) : null;
        // TP-08: la unidad prepago sale de la bolsa nacional (sin recargo) o de la de sede, según la ciudad y lo que haya
        boolean pospago = cliente.getTipoCliente() == TipoCliente.POSPAGO;
        int disponiblesPrepago = pospago ? 0
                : bolsa.disponible(cliente.getIdCliente(), idProceso) + saldoService.disponibleNacional(cliente.getIdCliente(), idProceso);
        // TP-12: un cliente pospago con unidades en su bolsa las gasta primero (solo paga el recargo con cupo)
        int unidadesBolsa = pospago && precioPospagoService.activo()
                ? bolsa.disponible(cliente.getIdCliente(), idProceso) + saldoService.disponibleNacional(cliente.getIdCliente(), idProceso) : 0;
        boolean bolsaEnPospago = unidadesBolsa > 0;
        String cobertura = pospago && !bolsaEnPospago ? com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.LOCAL : coberturaQueSeUsara(cliente, proceso, ciudad);
        BigDecimal recargo = recargoPendienteService.calcular(proceso, ciudad, cobertura);
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            BigDecimal valor = bolsaEnPospago ? BigDecimal.ZERO : precioPospagoService.precioVigente(proceso).orElse(BigDecimal.ZERO);
            BigDecimal cupo = clientePospagoRepository.findByIdCliente(cliente.getIdCliente())
                    .map(p -> p.getCreditoDisponible() != null ? p.getCreditoDisponible() : BigDecimal.ZERO).orElse(BigDecimal.ZERO);
            BigDecimal pct = ivaService.porcentaje();
            boolean aplica = !Boolean.FALSE.equals(proceso.getAplicaIva());
            BigDecimal iva = com.polygraph.erp.modules.tienda.service.IvaCalculadora.iva(valor, pct, aplica)
                    .add(com.polygraph.erp.modules.tienda.service.IvaCalculadora.iva(recargo, pct, aplica));
            if (bolsaEnPospago) return pospagoConBolsa(proceso.getNombreProceso(), categoria, unidadesBolsa, recargo, iva, cupo);
            return pospago(proceso.getNombreProceso(), categoria, valor, recargo, iva, cupo);
        }
        return prepago(proceso.getNombreProceso(), categoria, disponiblesPrepago, recargo);
    }

    /** Bolsa de la que saldría la unidad: la preferida según la ciudad (nacional si tiene recargo, sede si es sede) y, si no hay de esa, la otra. */
    private String coberturaQueSeUsara(Cliente cliente, Proceso proceso, Ciudad ciudad) {
        if (!parametros.activo(com.polygraph.erp.modules.tienda.service.ParametrosTiendaService.COBERTURA_POR_SEDE)) return com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.LOCAL;
        String preferida = com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.bolsaPreferida(recargoPendienteService.calcular(proceso, ciudad, com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.LOCAL).signum() > 0);
        int local = bolsa.disponible(cliente.getIdCliente(), proceso.getIdProceso());
        int nacional = saldoService.disponibleNacional(cliente.getIdCliente(), proceso.getIdProceso());
        boolean hayPreferida = com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.NACIONAL.equals(preferida) ? nacional > 0 : local > 0;
        if (hayPreferida) return preferida;
        return com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.NACIONAL.equals(preferida) ? com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.LOCAL : com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.NACIONAL;
    }

    /**
     * TP-08, paso 2: «Esta prueba tiene un recargo de $15.000 + IVA por realizarse en {ciudad}». Sin aviso si el interruptor está apagado, no hay recargo
     * o la unidad saldría del paquete nacional. En pospago el recargo con IVA se descuenta del cupo; en prepago se cobra en línea (decisión pendiente).
     */
    @Transactional(readOnly = true)
    public com.polygraph.erp.modules.solicitudes.dto.RecargoCiudadResponse avisoRecargo(String emailUsuario, Integer idProceso, Integer idCiudad) {
        var usuario = usuarioRepository.findByEmail(emailUsuario).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Cliente cliente = clienteRepository.findById(usuario.getIdCliente()).orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        Proceso proceso = procesoRepository.findById(idProceso).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        Ciudad ciudad = idCiudad != null ? ciudadRepository.findById(idCiudad).orElse(null) : null;
        boolean pospagoCliente = cliente.getTipoCliente() == TipoCliente.POSPAGO;
        boolean activo = parametros.activo(com.polygraph.erp.modules.tienda.service.ParametrosTiendaService.COBERTURA_POR_SEDE)
                || (pospagoCliente && precioPospagoService.activo());   // TP-12: el pospago avisa el recargo aunque la cobertura por sede esté apagada
        if (ciudad == null || !activo) {
            return com.polygraph.erp.modules.solicitudes.dto.RecargoCiudadResponse.sinRecargo(com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.LOCAL, null);
        }
        boolean bolsaPospago = pospagoCliente && precioPospagoService.activo()
                && bolsa.disponible(cliente.getIdCliente(), idProceso) + saldoService.disponibleNacional(cliente.getIdCliente(), idProceso) > 0;
        String cobertura = pospagoCliente && !bolsaPospago ? com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.LOCAL : coberturaQueSeUsara(cliente, proceso, ciudad);
        BigDecimal recargo = recargoPendienteService.calcular(proceso, ciudad, cobertura);
        if (recargo.signum() <= 0) {
            String m = com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.NACIONAL.equals(cobertura) && recargoPendienteService.calcular(proceso, ciudad, com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.LOCAL).signum() > 0
                    ? "Se usará una unidad de tu paquete nacional: sin recargo por realizarse en " + ciudad.getNombreCiudad() + "." : null;
            return com.polygraph.erp.modules.solicitudes.dto.RecargoCiudadResponse.sinRecargo(cobertura, m);
        }
        BigDecimal iva = com.polygraph.erp.modules.tienda.service.IvaCalculadora.iva(recargo, ivaService.porcentaje(), !Boolean.FALSE.equals(proceso.getAplicaIva()));
        String mas = iva.signum() > 0 ? " + IVA" : "";
        String mensaje = pospagoCliente
                ? "Esta prueba en " + ciudad.getNombreCiudad() + " tiene un recargo de " + pesos(recargo) + mas
                : "Esta prueba tiene un recargo de " + pesos(recargo) + mas + " por realizarse en " + ciudad.getNombreCiudad() + ".";
        return new com.polygraph.erp.modules.solicitudes.dto.RecargoCiudadResponse(true, cobertura, recargo, iva, recargo.add(iva), ciudad.getNombreCiudad(), mensaje);
    }

    // ───────────────────────── cálculo puro ─────────────────────────

    /** recargo: el de la ciudad, que en prepago NO se cobra ahora (queda pendiente para la próxima compra); solo se informa. */
    static ResumenCobroResponse prepago(String servicio, String categoria, int disponibles, BigDecimal recargo) {
        boolean alcanza = disponibles >= 1;
        String mensaje = alcanza
                ? "Se usará 1 " + servicio + " de tu bolsa · Disponibles ahora: " + disponibles + " · Quedarán: " + (disponibles - 1)
                : "No tienes «" + servicio + "» disponible en tu bolsa prepago.";
        return new ResumenCobroResponse("PREPAGO", servicio, categoria, alcanza, mensaje, disponibles, alcanza ? disponibles - 1 : disponibles,
                null, recargo, null, null, null, null);
    }

    /** TP-12: la unidad sale de la bolsa prepago; del cupo solo baja el recargo con su IVA (si aplica). */
    static ResumenCobroResponse pospagoConBolsa(String servicio, String categoria, int unidades, BigDecimal recargo, BigDecimal iva, BigDecimal cupo) {
        BigDecimal total = recargo.add(iva);
        BigDecimal quedara = cupo.subtract(total);
        boolean alcanza = quedara.signum() >= 0;
        StringBuilder m = new StringBuilder("Se usará 1 ").append(servicio).append(" de tu bolsa · Disponibles ahora: ").append(unidades)
                .append(" · Quedarán: ").append(unidades - 1).append(" · No se descuenta cupo por el servicio");
        if (recargo.signum() > 0) m.append(" · Recargo por ciudad: ").append(pesos(recargo)).append(" + IVA ").append(pesos(iva)).append(" = ").append(pesos(total));
        m.append(" · Cupo disponible: ").append(pesos(cupo));
        if (alcanza) m.append(" · Quedará: ").append(pesos(quedara)).append(" · Se incluirá en tu factura del próximo corte");
        else m.append(" · No alcanza para el recargo: contacta a tu gestor para ampliar tu cupo");
        return new ResumenCobroResponse("POSPAGO", servicio, categoria, alcanza, m.toString(), unidades, unidades - 1, BigDecimal.ZERO, recargo, cupo, quedara, iva, total);
    }

    /** El cargo es el valor del servicio MÁS el recargo por ciudad (lo mismo que descuenta {@code ClientePospagoService.descontarCredito}). */
    static ResumenCobroResponse pospago(String servicio, String categoria, BigDecimal valor, BigDecimal recargo, BigDecimal cupo) {
        return pospago(servicio, categoria, valor, recargo, BigDecimal.ZERO, cupo);
    }

    /** iva = IVA del servicio + IVA del recargo (cada uno redondeado a pesos); el cupo baja valor + recargo + iva. */
    static ResumenCobroResponse pospago(String servicio, String categoria, BigDecimal valor, BigDecimal recargo, BigDecimal iva, BigDecimal cupo) {
        BigDecimal total = valor.add(recargo).add(iva);
        BigDecimal quedara = cupo.subtract(total);
        boolean alcanza = quedara.signum() >= 0;
        StringBuilder m = new StringBuilder("Valor del servicio: ").append(pesos(valor));
        if (recargo.signum() > 0) m.append(" · Recargo por ciudad: ").append(pesos(recargo));
        if (iva.signum() > 0) m.append(" · IVA: ").append(pesos(iva)).append(" · Total: ").append(pesos(total));
        m.append(" · Cupo disponible: ").append(pesos(cupo));
        if (alcanza) m.append(" · Quedará: ").append(pesos(quedara)).append(" · Se incluirá en tu factura del próximo corte");
        else m.append(" · No alcanza: contacta a tu gestor para ampliar tu cupo");
        return new ResumenCobroResponse("POSPAGO", servicio, categoria, alcanza, m.toString(), null, null, valor, recargo, cupo, quedara, iva, total);
    }

    static String pesos(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        f.setMaximumFractionDigits(0);
        f.setMinimumFractionDigits(0);
        return "$" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")).format(v.setScale(0, java.math.RoundingMode.HALF_UP));
    }
}
