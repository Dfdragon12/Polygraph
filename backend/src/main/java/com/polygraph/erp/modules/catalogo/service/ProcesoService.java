package com.polygraph.erp.modules.catalogo.service;

import com.polygraph.erp.modules.catalogo.dto.*;
import com.polygraph.erp.modules.catalogo.entity.ClasificacionProceso;
import com.polygraph.erp.modules.catalogo.entity.DocumentoRequeridoProceso;
import com.polygraph.erp.modules.catalogo.entity.ProcesoTipoProgreso;
import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.modules.catalogo.repository.ClasificacionProcesoRepository;
import com.polygraph.erp.modules.catalogo.repository.DocumentoRequeridoProcesoRepository;
import com.polygraph.erp.modules.catalogo.repository.ProcesoTipoProgresoRepository;
import com.polygraph.erp.modules.catalogo.repository.TipoProgresoRepository;
import com.polygraph.erp.modules.notificaciones.service.NotificacionService;
import com.polygraph.erp.modules.servicios.entity.PrecioCiudadProceso;
import com.polygraph.erp.modules.servicios.entity.Proceso;
import com.polygraph.erp.modules.servicios.entity.TramoPrecioProceso;
import com.polygraph.erp.modules.servicios.repository.PrecioCiudadProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.ProcesoRepository;
import com.polygraph.erp.modules.servicios.repository.TramoPrecioProcesoRepository;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProcesoService {

    private final ProcesoRepository procesoRepository;
    private final ClasificacionProcesoRepository clasificacionRepository;
    private final ProcesoTipoProgresoRepository pasoRepository;
    private final TipoProgresoRepository tipoProgresoRepository;
    private final TramoPrecioProcesoRepository tramoRepository;
    private final PrecioCiudadProcesoRepository precioCiudadRepository;
    private final DocumentoRequeridoProcesoRepository documentoRequeridoRepository;
    private final NotificacionService notificacionService;
    private final CalificacionProcesoService calificacionProcesoService;
    private final com.polygraph.erp.modules.solicitudes.service.SolicitudExtraService solicitudExtraService;
    private final com.polygraph.erp.modules.tienda.service.PlanesTiendaService planesTiendaService;
    private final com.polygraph.erp.modules.tienda.service.HistorialPreciosService historialPrecios;
    private final com.polygraph.erp.modules.tienda.service.ParametrosTiendaService parametrosTienda;
    private final com.polygraph.erp.modules.pagos.service.RecargoPendienteService recargoPendienteService;
    private final com.polygraph.erp.modules.tienda.service.PrecioPospagoService precioPospagoService;

    @Transactional(readOnly = true)
    public List<ProcesoResponse> listarTodos() {
        return procesoRepository.findAll().stream()
                .map(p -> toResponse(p, pasoRepository.countByProceso_IdProceso(p.getIdProceso())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProcesoPublicoResponse> listarActivos() {
        return procesoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .map(p -> new ProcesoPublicoResponse(
                        p.getIdProceso(),
                        p.getNombreProceso(),
                        p.getDescripcion(),
                        p.getClasificacion() != null ? p.getClasificacion().getNombre() : null,
                        p.getClasificacion() != null ? p.getClasificacion().getCodigo() : null,
                        p.getValor(),
                        p.getDiasHabilesEntrega(),
                        listarTramos(p.getIdProceso()),
                        separarPuntosClave(p.getPuntosClave()),
                        p.getAplicaPrecioCiudad(),
                        listarPreciosCiudad(p.getIdProceso()),
                        p.getFormatoSolicitud() != null ? p.getFormatoSolicitud().name() : null,
                        solicitudExtraService.admitePreguntas(p),
                        Boolean.TRUE.equals(p.getRequiereDireccion()),
                        Boolean.TRUE.equals(p.getCiudadEsDePrueba()),
                        textoONull(p.getTituloComercial()),
                        textoONull(p.getFrasePrincipal()),
                        textoONull(p.getDescripcionCorta()),
                        separarPuntosClave(p.getIncluye()),
                        textoONull(p.getIdealPara()),
                        textoONull(p.getUnidadSingular()),
                        textoONull(p.getUnidadPlural()),
                        !Boolean.FALSE.equals(p.getVisibleEnTienda()),
                        !Boolean.FALSE.equals(p.getAplicaIva()),
                        planesConCobertura(p),
                        planesTiendaService.descuentoMaximo(planesTiendaService.planesDe(p))))
                .toList();
    }

    /**
     * TP-11: ofrecer en pospago, modo y precio propio (null = no cambia). Con el interruptor pospagoPlan100 encendido se valida ANTES de guardar:
     * ofrecido → debe haber precio; PROPIO → mayor que 0; PLAN_100 → el plan de 100 debe tener precio. Si falla, no se guarda nada.
     */
    private void aplicarPrecioPospago(Proceso proceso, ProcesoRequest req) {
        if (req.ofrecerPospago() == null && req.modoPrecioPospago() == null && req.precioPospagoPropio() == null) return;
        precioPospagoService.validarAlGuardar(proceso, req.ofrecerPospago(), req.modoPrecioPospago(), req.precioPospagoPropio());
        if (req.precioPospagoPropio() != null && (proceso.getPrecioPospagoPropio() == null
                || req.precioPospagoPropio().compareTo(proceso.getPrecioPospagoPropio()) != 0)) {
            historialPrecios.registrar(proceso.getIdProceso(), "Precio pospago propio", proceso.getPrecioPospagoPropio(), req.precioPospagoPropio());
            proceso.setPrecioPospagoPropio(req.precioPospagoPropio().signum() == 0 ? null : req.precioPospagoPropio());
        }
        if (req.modoPrecioPospago() != null) proceso.setModoPrecioPospago(req.modoPrecioPospago());
        if (req.ofrecerPospago() != null) proceso.setOfrecerPospago(req.ofrecerPospago());
    }

    /**
     * Planes del servicio y, con coberturaPorSede encendido y un servicio con recargo de ciudad intermedia, el precio del paquete nacional
     * (TP-08). Antecedentes y los demás servicios sin recargo no traen precio nacional.
     */
    private List<com.polygraph.erp.modules.tienda.dto.PlanPublicoResponse> planesConCobertura(Proceso p) {
        var planes = planesTiendaService.planesDe(p);
        if (planes.isEmpty() || !parametrosTienda.activo(com.polygraph.erp.modules.tienda.service.ParametrosTiendaService.COBERTURA_POR_SEDE)) return planes;
        BigDecimal intermedio = recargoPendienteService.recargoIntermedio(p);
        if (!com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.admiteNacional(Boolean.TRUE.equals(p.getAplicaPrecioCiudad()), intermedio)) return planes;
        BigDecimal factor = parametrosTienda.decimal("factorPrecioNacional");
        return planes.stream()
                .map(pl -> pl.conNacional(com.polygraph.erp.modules.tienda.service.CalculadoraCobertura.precioNacional(pl.precioUnitario(), intermedio, factor)))
                .toList();
    }

    public ProcesoResponse crear(ProcesoRequest req) {
        if (procesoRepository.existsByNombreProceso(req.nombreProceso())) {
            throw new ApiException("Ya existe un proceso con ese nombre", HttpStatus.CONFLICT);
        }
        ClasificacionProceso clasificacion = findClasificacion(req.idClasificacion());
        Proceso proceso = Proceso.builder()
                .nombreProceso(req.nombreProceso())
                .descripcion(req.descripcion())
                .clasificacion(clasificacion)
                .valor(req.valor())
                .diasHabilesEntrega(req.diasHabilesEntrega() != null ? req.diasHabilesEntrega() : 5)
                .aplicaPrecioCiudad(Boolean.TRUE.equals(req.aplicaPrecioCiudad()))
                .requiereHojaVida(req.requiereHojaVida() == null || req.requiereHojaVida())
                .requiereDireccion(Boolean.TRUE.equals(req.requiereDireccion()))
                .ciudadEsDePrueba(Boolean.TRUE.equals(req.ciudadEsDePrueba()))
                .puntosClave(unirPuntosClave(req.puntosClave()))
                .activo(true)
                .build();
        aplicarTextosComerciales(proceso, req);
        procesoRepository.save(proceso);
        historialPrecios.registrar(proceso.getIdProceso(), "Precio plan 1", null, proceso.getValor());
        historialPrecios.registrar(proceso.getIdProceso(), "Costo directo", null, proceso.getCostoDirecto());
        log.info("Proceso creado: {}", proceso.getNombreProceso());
        notificacionService.crearParaAdmins("CATALOGO", "Proceso creado",
                "Se creó el proceso \"" + proceso.getNombreProceso() + "\"");
        return toResponse(proceso, 0L);
    }

    public ProcesoResponse actualizar(Integer id, ProcesoRequest req) {
        Proceso proceso = findProceso(id);
        if (procesoRepository.existsByNombreProcesoAndIdProcesoNot(req.nombreProceso(), id)) {
            throw new ApiException("Ya existe un proceso con ese nombre", HttpStatus.CONFLICT);
        }
        ClasificacionProceso clasificacion = findClasificacion(req.idClasificacion());
        proceso.setNombreProceso(req.nombreProceso());
        proceso.setDescripcion(req.descripcion());
        proceso.setClasificacion(clasificacion);
        // null = no cambia: el precio se edita con los precios por plan (TP-04).
        if (req.valor() != null && (proceso.getValor() == null || req.valor().compareTo(proceso.getValor()) != 0)) {
            planesTiendaService.validarEscalera(req.valor(), tramosGuardados(id, null, null, null));
            historialPrecios.registrar(id, "Precio plan 1", proceso.getValor(), req.valor());
            proceso.setValor(req.valor());
        }
        BigDecimal costoAnterior = proceso.getCostoDirecto();
        if (req.diasHabilesEntrega() != null) proceso.setDiasHabilesEntrega(req.diasHabilesEntrega());
        // Los recargos se exigen al guardarlos (guardarPreciosCiudad): el formulario manda casilla y recargos juntos, primero la casilla.
        boolean aplicaCiudad = Boolean.TRUE.equals(req.aplicaPrecioCiudad());
        proceso.setAplicaPrecioCiudad(aplicaCiudad);
        if (req.requiereHojaVida() != null) proceso.setRequiereHojaVida(req.requiereHojaVida());
        if (req.requiereDireccion() != null) proceso.setRequiereDireccion(req.requiereDireccion());
        if (req.ciudadEsDePrueba() != null) proceso.setCiudadEsDePrueba(req.ciudadEsDePrueba());
        proceso.setPuntosClave(unirPuntosClave(req.puntosClave()));
        aplicarTextosComerciales(proceso, req);
        historialPrecios.registrar(id, "Costo directo", costoAnterior, proceso.getCostoDirecto());
        aplicarPrecioPospago(proceso, req);
        log.info("Proceso actualizado: {}", proceso.getNombreProceso());
        notificacionService.crearParaAdmins("CATALOGO", "Proceso actualizado",
                "Se actualizó el proceso \"" + proceso.getNombreProceso() + "\"");
        return toResponse(proceso, pasoRepository.countByProceso_IdProceso(id));
    }

    public ProcesoResponse cambiarEstado(Integer id, boolean activo) {
        Proceso proceso = findProceso(id);
        proceso.setActivo(activo);
        notificacionService.crearParaAdmins("CATALOGO",
                activo ? "Proceso activado" : "Proceso desactivado",
                "El proceso \"" + proceso.getNombreProceso() + "\" fue " + (activo ? "activado" : "desactivado"));
        return toResponse(proceso, pasoRepository.countByProceso_IdProceso(id));
    }

    @Transactional(readOnly = true)
    public List<PasoProcesoResponse> obtenerPasos(Integer idProceso) {
        findProceso(idProceso);
        return pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(idProceso).stream()
                .map(this::toPasoResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PasoProcesoPublicoResponse> obtenerPasosPublicos(Integer idProceso) {
        findProceso(idProceso);
        return pasoRepository.findByProceso_IdProcesoOrderByTipoProgreso_OrdenAscTipoProgreso_NombreProgresoAsc(idProceso).stream()
                .filter(p -> Boolean.TRUE.equals(p.getHabilitado()) && Boolean.TRUE.equals(p.getTipoProgreso().getActivo()))
                .map(p -> new PasoProcesoPublicoResponse(
                        p.getTipoProgreso().getNombreProgreso(),
                        p.getTipoProgreso().getDescripcion(),
                        p.getTipoProgreso().getOrden(),
                        p.getObligatorio()))
                .toList();
    }

    public PasoProcesoResponse asignarPaso(Integer idProceso, PasoProcesoRequest req) {
        Proceso proceso = findProceso(idProceso);
        TipoProgreso tipo = tipoProgresoRepository.findById(req.idTipoProgreso())
                .orElseThrow(() -> new ApiException("Tipo de progreso no encontrado", HttpStatus.NOT_FOUND));

        if (pasoRepository.existsByProceso_IdProcesoAndTipoProgreso_IdTipoProgreso(idProceso, req.idTipoProgreso())) {
            throw new ApiException("Ese tipo de progreso ya está asignado a este proceso", HttpStatus.CONFLICT);
        }

        ProcesoTipoProgreso paso = ProcesoTipoProgreso.builder()
                .proceso(proceso)
                .tipoProgreso(tipo)
                .ordenEnProceso(req.ordenEnProceso())
                .habilitado(req.habilitado() != null ? req.habilitado() : true)
                .obligatorio(req.obligatorio() != null ? req.obligatorio() : true)
                .build();
        pasoRepository.save(paso);
        calificacionProcesoService.trasCambioDeComposicion(proceso);
        log.info("Paso '{}' asignado al proceso '{}'", tipo.getNombreProgreso(), proceso.getNombreProceso());
        notificacionService.crearParaAdmins("CATALOGO", "Progreso asignado",
                "\"" + tipo.getNombreProgreso() + "\" fue asignado al proceso \"" + proceso.getNombreProceso() + "\"");
        return toPasoResponse(paso);
    }

    public PasoProcesoResponse actualizarPaso(Integer pasoId, PasoProcesoRequest req) {
        ProcesoTipoProgreso paso = findPaso(pasoId);
        String nombreProgreso = paso.getTipoProgreso().getNombreProgreso();
        String nombreProceso  = paso.getProceso().getNombreProceso();
        boolean cambiaCalificacion = (req.habilitado() != null && !req.habilitado().equals(paso.getHabilitado()))
                || (req.obligatorio() != null && !req.obligatorio().equals(paso.getObligatorio()));
        paso.setOrdenEnProceso(req.ordenEnProceso());
        if (req.habilitado() != null) paso.setHabilitado(req.habilitado());
        if (req.obligatorio() != null) paso.setObligatorio(req.obligatorio());
        if (cambiaCalificacion) calificacionProcesoService.trasCambioDeComposicion(paso.getProceso());
        notificacionService.crearParaAdmins("CATALOGO", "Configuración de progreso actualizada",
                "Se actualizó \"" + nombreProgreso + "\" en el proceso \"" + nombreProceso + "\"");
        return toPasoResponse(paso);
    }

    public void eliminarPaso(Integer pasoId) {
        ProcesoTipoProgreso paso = findPaso(pasoId);
        String nombreProgreso = paso.getTipoProgreso().getNombreProgreso();
        String nombreProceso  = paso.getProceso().getNombreProceso();
        Proceso proceso = paso.getProceso();
        pasoRepository.delete(paso);
        pasoRepository.flush();
        calificacionProcesoService.trasCambioDeComposicion(proceso);
        log.info("Paso {} eliminado del proceso '{}'", nombreProgreso, nombreProceso);
        notificacionService.crearParaAdmins("CATALOGO", "Progreso removido",
                "\"" + nombreProgreso + "\" fue removido del proceso \"" + nombreProceso + "\"");
    }

    // ---------- Documentos requeridos (qué debe subir el candidato para este proceso) ----------

    @Transactional(readOnly = true)
    public List<DocumentoRequeridoResponse> obtenerDocumentosRequeridos(Integer idProceso) {
        findProceso(idProceso);
        return documentoRequeridoRepository.findByProceso_IdProcesoOrderByOrdenAscNombreAsc(idProceso).stream()
                .map(DocumentoRequeridoResponse::from)
                .toList();
    }

    public DocumentoRequeridoResponse agregarDocumentoRequerido(Integer idProceso, DocumentoRequeridoRequest req) {
        Proceso proceso = findProceso(idProceso);
        String clave = claveDeRequest(req);
        if (documentoRequeridoRepository.existsByProceso_IdProcesoAndClaveIgnoreCase(idProceso, clave)) {
            throw new ApiException("Ya hay un documento con esa clave en este proceso", HttpStatus.CONFLICT);
        }
        DocumentoRequeridoProceso doc = DocumentoRequeridoProceso.builder()
                .proceso(proceso)
                .clave(clave)
                .nombre(req.nombre().trim())
                .obligatorio(req.obligatorio() == null || req.obligatorio())
                .orden(req.orden() != null ? req.orden() : 0)
                .build();
        documentoRequeridoRepository.save(doc);
        log.info("Documento requerido agregado: proceso={}, clave={}", proceso.getNombreProceso(), clave);
        return DocumentoRequeridoResponse.from(doc);
    }

    public DocumentoRequeridoResponse actualizarDocumentoRequerido(Integer id, DocumentoRequeridoRequest req) {
        DocumentoRequeridoProceso doc = findDocumentoRequerido(id);
        Integer idProceso = doc.getProceso().getIdProceso();
        String clave = claveDeRequest(req);
        if (documentoRequeridoRepository.existsByProceso_IdProcesoAndClaveIgnoreCaseAndIdNot(idProceso, clave, id)) {
            throw new ApiException("Ya hay un documento con esa clave en este proceso", HttpStatus.CONFLICT);
        }
        doc.setClave(clave);
        doc.setNombre(req.nombre().trim());
        if (req.obligatorio() != null) doc.setObligatorio(req.obligatorio());
        if (req.orden() != null) doc.setOrden(req.orden());
        log.info("Documento requerido actualizado: id={}, proceso={}", id, doc.getProceso().getNombreProceso());
        return DocumentoRequeridoResponse.from(doc);
    }

    public void eliminarDocumentoRequerido(Integer id) {
        DocumentoRequeridoProceso doc = findDocumentoRequerido(id);
        log.info("Documento requerido eliminado: id={}, proceso={}", id, doc.getProceso().getNombreProceso());
        documentoRequeridoRepository.delete(doc);
    }

    private DocumentoRequeridoProceso findDocumentoRequerido(Integer id) {
        return documentoRequeridoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Documento requerido no encontrado", HttpStatus.NOT_FOUND));
    }

    // Si no viene una clave explícita, se deriva del nombre (minúsculas, sin tildes, espacios ->
    // guion bajo) — el admin normalmente solo escribe el nombre visible ("Cédula por ambas caras")
    // y no necesita pensar en un identificador técnico aparte.
    private String claveDeRequest(DocumentoRequeridoRequest req) {
        if (req.clave() != null && !req.clave().isBlank()) return req.clave().trim().toLowerCase();
        String sinTildes = java.text.Normalizer.normalize(req.nombre(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String clave = sinTildes.toLowerCase().trim()
                .replaceAll("[^a-z0-9\\s_]", "")
                .replaceAll("\\s+", "_");
        if (clave.isBlank()) {
            throw new ApiException("No se pudo derivar una clave válida del nombre — indícala manualmente.", HttpStatus.BAD_REQUEST);
        }
        return clave;
    }

    private ProcesoResponse toResponse(Proceso p, long totalPasos) {
        ClasificacionProcesoResponse clsf = null;
        if (p.getClasificacion() != null) {
            ClasificacionProceso c = p.getClasificacion();
            clsf = new ClasificacionProcesoResponse(
                    c.getIdClasificacion(), c.getCodigo(), c.getNombre(), c.getDescripcion(), c.getActivo()
            );
        }
        BigDecimal valorCalculado = pasoRepository.sumValorHabilitadosByProcesoId(p.getIdProceso());
        CalificacionProcesoService.ResumenPesos pesos = calificacionProcesoService.resumen(p.getIdProceso());
        return new ProcesoResponse(
                p.getIdProceso(), p.getNombreProceso(), p.getDescripcion(),
                p.getActivo(), totalPasos, clsf, p.getValor(), valorCalculado,
                p.getDiasHabilesEntrega(), listarTramos(p.getIdProceso()),
                p.getAplicaPrecioCiudad(), !Boolean.FALSE.equals(p.getRequiereHojaVida()),
                Boolean.TRUE.equals(p.getRequiereDireccion()), Boolean.TRUE.equals(p.getCiudadEsDePrueba()), listarPreciosCiudad(p.getIdProceso()),
                separarPuntosClave(p.getPuntosClave()),
                pesos.suma(), pesos.califica(), pesos.motivo(),
                p.getCriticidadDefecto().name(), p.getCoberturaMinima(),
                textoONull(p.getTituloComercial()), textoONull(p.getFrasePrincipal()), textoONull(p.getDescripcionCorta()),
                separarPuntosClave(p.getIncluye()), textoONull(p.getIdealPara()),
                textoONull(p.getUnidadSingular()), textoONull(p.getUnidadPlural()),
                p.getCostoDirecto(), !Boolean.FALSE.equals(p.getVisibleEnTienda()), !Boolean.FALSE.equals(p.getAplicaIva()),
                !Boolean.FALSE.equals(p.getOfrecerPospago()), precioPospagoService.modo(p).name(), p.getPrecioPospagoPropio()
        );
    }

    /** TP-01: solo cambia lo que viene (null = no cambia); un texto vacío lo deja en blanco. */
    private void aplicarTextosComerciales(Proceso p, ProcesoRequest req) {
        if (req.tituloComercial() != null) p.setTituloComercial(textoONull(req.tituloComercial()));
        if (req.frasePrincipal() != null) p.setFrasePrincipal(textoONull(req.frasePrincipal()));
        if (req.descripcionCorta() != null) p.setDescripcionCorta(textoONull(req.descripcionCorta()));
        if (req.incluye() != null) p.setIncluye(unirPuntosClave(req.incluye()));
        if (req.idealPara() != null) p.setIdealPara(textoONull(req.idealPara()));
        if (req.unidadSingular() != null) p.setUnidadSingular(textoONull(req.unidadSingular()));
        if (req.unidadPlural() != null) p.setUnidadPlural(textoONull(req.unidadPlural()));
        if (req.costoDirecto() != null) p.setCostoDirecto(req.costoDirecto());
        if (req.visibleEnTienda() != null) p.setVisibleEnTienda(req.visibleEnTienda());
        if (req.aplicaIva() != null) p.setAplicaIva(req.aplicaIva());
    }

    private static String textoONull(String t) {
        return (t == null || t.isBlank()) ? null : t.trim();
    }

    // ---------- Puntos clave (bullets cortos para la tarjeta de la tienda) ----------

    private String unirPuntosClave(List<String> puntos) {
        if (puntos == null || puntos.isEmpty()) return null;
        String unido = puntos.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse(null);
        return (unido == null || unido.isBlank()) ? null : unido;
    }

    private List<String> separarPuntosClave(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        return Arrays.stream(texto.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private Proceso findProceso(Integer id) {
        return procesoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Proceso no encontrado", HttpStatus.NOT_FOUND));
    }

    private ClasificacionProceso findClasificacion(Integer idClasificacion) {
        return clasificacionRepository.findById(idClasificacion)
                .orElseThrow(() -> new ApiException("Clasificación no encontrada", HttpStatus.NOT_FOUND));
    }

    // ---------- Tramos de precio por volumen ----------

    @Transactional(readOnly = true)
    public List<TramoPrecioResponse> listarTramos(Integer idProceso) {
        return tramoRepository.findByProceso_IdProcesoOrderByCantidadMinimaAsc(idProceso).stream()
                .map(this::toTramoResponse)
                .toList();
    }

    public TramoPrecioResponse agregarTramo(Integer idProceso, TramoPrecioRequest req) {
        Proceso proceso = findProceso(idProceso);
        if (tramoRepository.existsByProceso_IdProcesoAndCantidadMinima(idProceso, req.cantidadMinima())) {
            throw new ApiException("Ya existe un tramo para esa cantidad mínima", HttpStatus.CONFLICT);
        }
        validarValorTramo(proceso, req.valorUnitario());
        planesTiendaService.validarEscalera(proceso.getValor(), tramosGuardados(idProceso, null, req.cantidadMinima(), req.valorUnitario()));
        TramoPrecioProceso tramo = TramoPrecioProceso.builder()
                .proceso(proceso)
                .cantidadMinima(req.cantidadMinima())
                .valorUnitario(req.valorUnitario())
                .build();
        tramoRepository.save(tramo);
        log.info("Tramo de precio agregado: proceso={}, cantidadMinima={}, valorUnitario={}",
                proceso.getNombreProceso(), req.cantidadMinima(), req.valorUnitario());
        return toTramoResponse(tramo);
    }

    public TramoPrecioResponse actualizarTramo(Integer idTramo, TramoPrecioRequest req) {
        TramoPrecioProceso tramo = findTramo(idTramo);
        Integer idProceso = tramo.getProceso().getIdProceso();
        if (tramoRepository.existsByProceso_IdProcesoAndCantidadMinimaAndIdTramoNot(idProceso, req.cantidadMinima(), idTramo)) {
            throw new ApiException("Ya existe un tramo para esa cantidad mínima", HttpStatus.CONFLICT);
        }
        validarValorTramo(tramo.getProceso(), req.valorUnitario());
        planesTiendaService.validarEscalera(tramo.getProceso().getValor(), tramosGuardados(idProceso, idTramo, req.cantidadMinima(), req.valorUnitario()));
        tramo.setCantidadMinima(req.cantidadMinima());
        tramo.setValorUnitario(req.valorUnitario());
        log.info("Tramo de precio actualizado: id={}, proceso={}", idTramo, tramo.getProceso().getNombreProceso());
        return toTramoResponse(tramo);
    }

    public void eliminarTramo(Integer idTramo) {
        TramoPrecioProceso tramo = findTramo(idTramo);
        log.info("Tramo de precio eliminado: id={}, proceso={}", idTramo, tramo.getProceso().getNombreProceso());
        tramoRepository.delete(tramo);
    }

    /** Tramos del proceso por cantidad mínima tal como quedarían: sin `excluirId` y con (cantidad, valor) si viene. */
    private java.util.Map<Integer, BigDecimal> tramosGuardados(Integer idProceso, Integer excluirId, Integer cantidad, BigDecimal valor) {
        java.util.Map<Integer, BigDecimal> m = new java.util.HashMap<>();
        for (TramoPrecioProceso t : tramoRepository.findByProceso_IdProcesoOrderByCantidadMinimaAsc(idProceso)) {
            if (!t.getIdTramo().equals(excluirId)) m.put(t.getCantidadMinima(), t.getValorUnitario());
        }
        if (cantidad != null) m.put(cantidad, valor);
        return m;
    }

    private void validarValorTramo(Proceso proceso, BigDecimal valorUnitario) {
        if (proceso.getValor() != null && valorUnitario.compareTo(proceso.getValor()) >= 0) {
            throw new ApiException(
                    "El valor del tramo debe ser menor al valor base del proceso (" + proceso.getValor() + ")",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private TramoPrecioProceso findTramo(Integer id) {
        return tramoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Tramo de precio no encontrado", HttpStatus.NOT_FOUND));
    }

    private TramoPrecioResponse toTramoResponse(TramoPrecioProceso t) {
        return new TramoPrecioResponse(t.getIdTramo(), t.getCantidadMinima(), t.getValorUnitario());
    }

    // ---------- Precios por nivel de ciudad ----------

    @Transactional(readOnly = true)
    public List<PrecioCiudadResponse> listarPreciosCiudad(Integer idProceso) {
        return precioCiudadRepository.findByProceso_IdProcesoOrderByNivelCiudadAsc(idProceso).stream()
                .map(pc -> new PrecioCiudadResponse(pc.getIdPrecioCiudad(), pc.getNivelCiudad(), pc.getValor()))
                .toList();
    }

    // Reemplaza el set completo de precios por nivel de un proceso — pensado para guardarse junto
    // (hasta 3 filas fijas: PRINCIPAL / INTERMEDIA / MUNICIPIO_SECUNDARIO), no como CRUD incremental.
    public List<PrecioCiudadResponse> guardarPreciosCiudad(Integer idProceso, List<PrecioCiudadRequest> items) {
        Proceso proceso = findProceso(idProceso);
        if (!Boolean.TRUE.equals(proceso.getAplicaPrecioCiudad())) {
            throw new ApiException("Este proceso no tiene habilitado \"aplica precio por ciudad\"", HttpStatus.BAD_REQUEST);
        }
        // TP-08: con la cobertura por sede encendida, un servicio con precio por ciudad necesita el recargo de ciudad intermedia y de municipio
        if (parametrosTienda.activo(com.polygraph.erp.modules.tienda.service.ParametrosTiendaService.COBERTURA_POR_SEDE)) {
            exigirRecargos(proceso.getNombreProceso(), items.stream().filter(it -> it.valor() != null && it.valor().signum() > 0)
                    .map(PrecioCiudadRequest::nivelCiudad).collect(java.util.stream.Collectors.toSet()));
        }
        precioCiudadRepository.deleteByProceso_IdProceso(idProceso);
        List<PrecioCiudadProceso> nuevos = items.stream()
                .map(it -> PrecioCiudadProceso.builder()
                        .proceso(proceso)
                        .nivelCiudad(it.nivelCiudad())
                        .valor(it.valor())
                        .build())
                .toList();
        precioCiudadRepository.saveAll(nuevos);
        log.info("Precios por ciudad actualizados: proceso={}, niveles={}", proceso.getNombreProceso(), items.size());
        return listarPreciosCiudad(idProceso);
    }

    private void exigirRecargos(String servicio, java.util.Set<com.polygraph.erp.shared.enums.NivelCiudad> cargados) {
        java.util.List<String> faltan = new java.util.ArrayList<>();
        if (!cargados.contains(com.polygraph.erp.shared.enums.NivelCiudad.INTERMEDIA)) faltan.add("ciudad intermedia");
        if (!cargados.contains(com.polygraph.erp.shared.enums.NivelCiudad.MUNICIPIO_SECUNDARIO)) faltan.add("municipio");
        if (!faltan.isEmpty()) {
            throw new ApiException("«" + servicio + "» tiene el precio por ciudad encendido y le falta el recargo de: " + String.join(" y ", faltan)
                    + ". Cárgalo o apaga «precio por ciudad».", HttpStatus.BAD_REQUEST);
        }
    }

    private ProcesoTipoProgreso findPaso(Integer id) {
        return pasoRepository.findById(id)
                .orElseThrow(() -> new ApiException("Paso no encontrado", HttpStatus.NOT_FOUND));
    }

    private PasoProcesoResponse toPasoResponse(ProcesoTipoProgreso p) {
        TipoProgreso tipo = p.getTipoProgreso();
        return new PasoProcesoResponse(
                p.getId(),
                tipo.getIdTipoProgreso(),
                tipo.getNombreProgreso(),
                tipo.getDescripcion(),
                tipo.getOrden(),
                p.getHabilitado(),
                p.getObligatorio(),
                p.getPeso(),
                Boolean.TRUE.equals(tipo.getActivo())
        );
    }
}
