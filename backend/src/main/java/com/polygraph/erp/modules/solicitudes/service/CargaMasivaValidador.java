package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.clientes.repository.ClientePospagoRepository;
import com.polygraph.erp.modules.clientes.repository.ClienteRepository;
import com.polygraph.erp.modules.pagos.service.BolsaPrepagoService;
import com.polygraph.erp.modules.pagos.service.MoraService;
import com.polygraph.erp.modules.pagos.service.RecargoPendienteService;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.repository.ServicioRepository;
import com.polygraph.erp.modules.solicitudes.dto.BulkUploadResponse.ErrorFila;
import com.polygraph.erp.modules.solicitudes.dto.SolicitudRequest;
import com.polygraph.erp.modules.solicitudes.validacion.DatosEvaluadoValidador;
import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.exceptions.ApiException;
import com.polygraph.erp.shared.service.DiasHabilesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * NS-12: revisa TODO el archivo de carga masiva ANTES de crear nada (todo o nada). Las mismas reglas que Nueva solicitud — datos del evaluado y
 * criticidad (NS-07), dirección y ciudad confirmada (NS-08), duplicados, información adicional de la poligrafía — y las que dependen del conjunto:
 * saldo prepago por servicio sumando todas las filas, o cupo pospago total, y la mora (NS-05). No escribe nada; devuelve un motivo por fila.
 */
@Service
@RequiredArgsConstructor
public class CargaMasivaValidador {

    /** Una fila del Excel tal cual (texto). numero = fila en el Excel (encabezado = 1). */
    public record FilaCarga(int numero, String cedula, String nombres, String apellidos, String celular, String email, String ciudad,
                            String direccionVisita, String cargo, String criticidadCargo, String servicio) {}

    public record Resultado(List<SolicitudRequest> solicitudes, List<ErrorFila> errores) {
        public boolean valido() { return errores.isEmpty(); }
    }

    private static final Pattern SEPARADOR_CIUDAD = Pattern.compile("\\s*[,\\-–—]\\s*");
    private static final Pattern MARCAS = Pattern.compile("\\p{M}");

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final CiudadRepository ciudadRepository;
    private final ServicioRepository servicioRepository;
    private final BolsaPrepagoService bolsa;
    private final ClientePospagoRepository clientePospagoRepository;
    private final RecargoPendienteService recargoPendienteService;
    private final MoraService moraService;
    private final SolicitudExtraService solicitudExtraService;
    private final DiasHabilesService diasHabilesService;

    @Transactional(readOnly = true)
    public Resultado validar(List<FilaCarga> filas, String emailUsuario, Map<String, Proceso> procesosPorNombre) {
        var usuario = usuarioRepository.findByEmail(emailUsuario).orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        Cliente cliente = clienteRepository.findById(usuario.getIdCliente()).orElseThrow(() -> new ApiException("Cliente no encontrado", HttpStatus.NOT_FOUND));
        List<ErrorFila> errores = new ArrayList<>();

        // Mora: una sola vez y para todo el archivo (fila 0 = el archivo completo).
        var mora = moraService.calcular(cliente.getIdCliente());
        if (mora.enMora()) errores.add(new ErrorFila(0, "", mora.mensaje()));

        List<Ciudad> confirmadas = ciudadRepository.findAllByOrderByNombreCiudadAsc().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getPendienteConfirmacion())).toList();
        LocalDate limite = diasHabilesService.hoy().minusMonths(3);

        List<Candidata> validas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (FilaCarga f : filas) {
            List<String> motivos = new ArrayList<>();
            Proceso proceso = procesosPorNombre.get(normalizar(f.servicio()));
            if (proceso == null) motivos.add("Tipo de servicio no reconocido: " + (f.servicio().isBlank() ? "(vacío)" : f.servicio()));
            else if (!Boolean.TRUE.equals(proceso.getActivo())) motivos.add("El servicio '" + proceso.getNombreProceso() + "' no está disponible");

            Ciudad ciudad = null;
            if (f.ciudad().isBlank()) {
                motivos.add("La ciudad es obligatoria");
            } else {
                var r = resolverCiudad(f.ciudad(), confirmadas);
                if (r.error() != null) motivos.add(r.error()); else ciudad = r.ciudad();
            }

            SolicitudRequest req = new SolicitudRequest(f.cedula(), f.nombres(), f.apellidos(), f.celular(), f.email(), ciudad != null ? ciudad.getIdCiudad() : null,
                    f.cargo(), f.criticidadCargo(), f.direccionVisita(), proceso != null ? List.of(proceso.getIdProceso()) : List.of(), null, null, null);
            boolean pideDireccion = proceso != null && Boolean.TRUE.equals(proceso.getRequiereDireccion());
            var porCampo = DatosEvaluadoValidador.errores(req, pideDireccion);
            porCampo.remove("idCiudad"); // la ciudad ya se explicó arriba con su propio texto
            motivos.addAll(porCampo.values());

            if (proceso != null && !f.cedula().isBlank()) {
                if (!vistas.add(f.cedula().trim() + "|" + proceso.getIdProceso())) {
                    motivos.add("La cédula " + f.cedula() + " está repetida en el archivo para '" + proceso.getNombreProceso() + "'");
                } else if (servicioRepository.existeDuplicado(f.cedula().trim(), proceso.getIdProceso(), limite)) {
                    motivos.add("El evaluado con cédula " + f.cedula() + " ya tiene un servicio activo para '" + proceso.getNombreProceso() + "' en los últimos 3 meses");
                }
                try {
                    solicitudExtraService.validar(List.of(proceso), null, null);
                } catch (ApiException e) {
                    motivos.add("'" + proceso.getNombreProceso() + "' pide información adicional que la carga masiva no trae: usa Nueva solicitud");
                }
            }

            if (!motivos.isEmpty()) {
                errores.add(new ErrorFila(f.numero(), f.cedula(), String.join("; ", motivos)));
            } else {
                BigDecimal recargo = recargoPendienteService.calcular(proceso, ciudad);
                validas.add(new Candidata(f.numero(), f.cedula(), proceso, recargo, req));
            }
        }

        // Lo que depende del conjunto de filas válidas: saldo o cupo.
        if (cliente.getTipoCliente() == TipoCliente.POSPAGO) {
            BigDecimal cupo = clientePospagoRepository.findByIdCliente(cliente.getIdCliente())
                    .map(p -> p.getCreditoDisponible() != null ? p.getCreditoDisponible() : BigDecimal.ZERO).orElse(BigDecimal.ZERO);
            List<Costo> costos = validas.stream()
                    .map(v -> new Costo(v.numero(), v.cedula(), (v.proceso().getValor() != null ? v.proceso().getValor() : BigDecimal.ZERO).add(v.recargo())))
                    .toList();
            errores.addAll(filasSinCupo(costos, cupo));
        } else {
            Map<Integer, Integer> disponibles = new HashMap<>();
            for (Candidata v : validas) disponibles.computeIfAbsent(v.proceso().getIdProceso(), id -> bolsa.disponible(cliente.getIdCliente(), id));
            errores.addAll(filasSinSaldo(validas.stream()
                    .map(v -> new Pedido(v.numero(), v.cedula(), v.proceso().getIdProceso(), v.proceso().getNombreProceso())).toList(), disponibles));
        }

        errores.sort(Comparator.comparingInt(ErrorFila::fila));
        return new Resultado(errores.isEmpty() ? validas.stream().map(Candidata::solicitud).toList() : List.of(), errores);
    }

    private record Candidata(int numero, String cedula, Proceso proceso, BigDecimal recargo, SolicitudRequest solicitud) {}

    // ───────────────────────── reglas puras ─────────────────────────

    public record Pedido(int fila, String cedula, Integer idProceso, String nombreProceso) {}
    public record Costo(int fila, String cedula, BigDecimal total) {}

    /** Prepago: por servicio se cuentan TODAS las filas; las que pasan de lo disponible se rechazan con el detalle (cuántas pide el archivo y cuántas hay). */
    public static List<ErrorFila> filasSinSaldo(List<Pedido> pedidos, Map<Integer, Integer> disponibles) {
        Map<Integer, Long> pedidas = new HashMap<>();
        for (Pedido p : pedidos) pedidas.merge(p.idProceso(), 1L, Long::sum);
        Map<Integer, Integer> contadas = new HashMap<>();
        List<ErrorFila> errores = new ArrayList<>();
        for (Pedido p : pedidos) {
            int n = contadas.merge(p.idProceso(), 1, Integer::sum);
            int hay = disponibles.getOrDefault(p.idProceso(), 0);
            if (n > hay) {
                errores.add(new ErrorFila(p.fila(), p.cedula(), "Sin saldo de '" + p.nombreProceso() + "': el archivo pide " + pedidas.get(p.idProceso())
                        + " y tienes " + hay + " disponible" + (hay == 1 ? "" : "s") + " (esta es la solicitud n.º " + n + " de ese servicio)"));
            }
        }
        return errores;
    }

    /** Pospago: el cupo se gasta en el orden del archivo; la fila que ya no cabe (y las siguientes) se rechazan con lo que falta. */
    public static List<ErrorFila> filasSinCupo(List<Costo> costos, BigDecimal cupo) {
        BigDecimal acumulado = BigDecimal.ZERO;
        List<ErrorFila> errores = new ArrayList<>();
        for (Costo c : costos) {
            acumulado = acumulado.add(c.total());
            if (acumulado.compareTo(cupo) > 0) {
                errores.add(new ErrorFila(c.fila(), c.cedula(), "No alcanza el cupo de crédito: con esta fila el archivo suma " + pesos(acumulado)
                        + " y tu cupo disponible es " + pesos(cupo) + " (faltan " + pesos(acumulado.subtract(cupo)) + ")"));
            }
        }
        return errores;
    }

    public record CiudadResuelta(Ciudad ciudad, String error) {}

    /** "Bogotá" o "Bogotá, Cundinamarca" / "Bogotá - Cundinamarca" contra las ciudades confirmadas, sin importar tildes ni mayúsculas. */
    public static CiudadResuelta resolverCiudad(String texto, List<Ciudad> confirmadas) {
        String[] partes = SEPARADOR_CIUDAD.split(texto.trim(), 2);
        String nombre = normalizar(partes[0]);
        String depto = partes.length > 1 ? normalizar(partes[1]) : null;
        List<Ciudad> coincidencias = confirmadas.stream()
                .filter(c -> normalizar(c.getNombreCiudad()).equals(nombre) && (depto == null || normalizar(c.getDepartamento()).equals(depto))).toList();
        if (coincidencias.isEmpty()) return new CiudadResuelta(null, "La ciudad '" + texto + "' no está en la lista de ciudades confirmadas");
        if (coincidencias.size() > 1) return new CiudadResuelta(null, "La ciudad '" + texto + "' existe en varios departamentos: escribe «Ciudad, Departamento»");
        return new CiudadResuelta(coincidencias.get(0), null);
    }

    public static String normalizar(String s) {
        if (s == null) return "";
        return MARCAS.matcher(Normalizer.normalize(s.trim(), Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    static String pesos(BigDecimal v) {
        return "$" + java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CO")).format(v.setScale(0, java.math.RoundingMode.HALF_UP));
    }
}
