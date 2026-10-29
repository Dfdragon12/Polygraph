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

    @Transactional(readOnly = true)
    public ResumenCobroResponse resumir(String emailUsuario, Integer idProceso, Integer idCiudad) {
        var usuario = usuarioRepository.findByEmail(emailUsuario).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Cliente cliente = clienteRepository.findById(usuario.getIdCliente()).orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        Proceso proceso = procesoRepository.findById(idProceso).orElseThrow(() -> new ApiException("Servicio no encontrado", HttpStatus.NOT_FOUND));
        String categoria = proceso.getClasificacion() != null ? proceso.getClasificacion().getNombre() : null;

        Ciudad ciudad = idCiudad != null ? ciudadRepository.findById(idCiudad).orElse(null) : null;
        BigDecimal recargo = recargoPendienteService.calcular(proceso, ciudad);
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            BigDecimal valor = proceso.getValor() != null ? proceso.getValor() : BigDecimal.ZERO;
            BigDecimal cupo = clientePospagoRepository.findByIdCliente(cliente.getIdCliente())
                    .map(p -> p.getCreditoDisponible() != null ? p.getCreditoDisponible() : BigDecimal.ZERO).orElse(BigDecimal.ZERO);
            return pospago(proceso.getNombreProceso(), categoria, valor, recargo, cupo);
        }
        return prepago(proceso.getNombreProceso(), categoria, bolsa.disponible(cliente.getIdCliente(), idProceso), recargo);
    }

    // ───────────────────────── cálculo puro ─────────────────────────

    /** recargo: el de la ciudad, que en prepago NO se cobra ahora (queda pendiente para la próxima compra); solo se informa. */
    static ResumenCobroResponse prepago(String servicio, String categoria, int disponibles, BigDecimal recargo) {
        boolean alcanza = disponibles >= 1;
        String mensaje = alcanza
                ? "Se usará 1 " + servicio + " de tu bolsa · Disponibles ahora: " + disponibles + " · Quedarán: " + (disponibles - 1)
                : "No tienes «" + servicio + "» disponible en tu bolsa prepago.";
        return new ResumenCobroResponse("PREPAGO", servicio, categoria, alcanza, mensaje, disponibles, alcanza ? disponibles - 1 : disponibles,
                null, recargo, null, null);
    }

    /** El cargo es el valor del servicio MÁS el recargo por ciudad (lo mismo que descuenta {@code ClientePospagoService.descontarCredito}). */
    static ResumenCobroResponse pospago(String servicio, String categoria, BigDecimal valor, BigDecimal recargo, BigDecimal cupo) {
        BigDecimal total = valor.add(recargo);
        BigDecimal quedara = cupo.subtract(total);
        boolean alcanza = quedara.signum() >= 0;
        StringBuilder m = new StringBuilder("Valor del servicio: ").append(pesos(valor));
        if (recargo.signum() > 0) m.append(" · Recargo por ciudad: ").append(pesos(recargo));
        m.append(" · Cupo disponible: ").append(pesos(cupo));
        if (alcanza) m.append(" · Quedará: ").append(pesos(quedara)).append(" · Queda sin facturar hasta tu próximo corte");
        else m.append(" · No alcanza: contacta a tu gestor para ampliar tu cupo");
        return new ResumenCobroResponse("POSPAGO", servicio, categoria, alcanza, m.toString(), null, null, valor, recargo, cupo, quedara);
    }

    static String pesos(BigDecimal v) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"));
        f.setMaximumFractionDigits(0);
        f.setMinimumFractionDigits(0);
        return "$" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")).format(v.setScale(0, java.math.RoundingMode.HALF_UP));
    }
}
