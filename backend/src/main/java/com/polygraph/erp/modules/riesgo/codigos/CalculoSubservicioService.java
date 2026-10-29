package com.polygraph.erp.modules.riesgo.codigos;

import com.polygraph.erp.modules.formularios.entity.FormularioColumna;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CodigoCatalogo;
import com.polygraph.erp.modules.riesgo.entity.CodigoCalificacion;
import com.polygraph.erp.modules.riesgo.financiero.MotorFinanciero;
import com.polygraph.erp.modules.riesgo.service.CentralRiesgoService;
import com.polygraph.erp.modules.riesgo.hallazgos.MotorHallazgos;
import com.polygraph.erp.modules.riesgo.motor.ModeloRiesgo.Umbrales;
import com.polygraph.erp.modules.riesgo.repository.CodigoCalificacionRepository;
import com.polygraph.erp.modules.riesgo.repository.UmbralRiesgoRepository;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.shared.enums.CriticidadCargo;
import com.polygraph.erp.shared.enums.TipoSeccionFormulario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Supplier;

/** Calificación por hallazgos de un subproceso: arma la plantilla (qué campo aplica hallazgos) y el
 *  catálogo de hallazgos y llama a MotorHallazgos. El formulario, el Word y el motor del estudio
 *  piden el cálculo aquí. Plantilla sin campos de hallazgo: no hay nada calculado. */
@Service
@Transactional(readOnly = true)
public class CalculoSubservicioService {

    /** Valor de tipos_progreso.grupo_calificacion para un servicio cuya plantilla califica por
     *  hallazgos (lo pone FormularioPlantillaService.guardar). */
    public static final String GRUPO_HALLAZGOS = "HALLAZGOS";

    private final Supplier<Map<String, CodigoCatalogo>> catalogo;
    private final Supplier<Map<CriticidadCargo, Umbrales>> umbrales;
    private final Supplier<List<MotorFinanciero.Banda>> bandas;

    @Autowired
    public CalculoSubservicioService(CodigoCalificacionRepository codigos, UmbralRiesgoRepository umbralRepository, CentralRiesgoService centrales) {
        this(() -> catalogoDe(codigos.findAll()), () -> {
            Map<CriticidadCargo, Umbrales> m = new EnumMap<>(CriticidadCargo.class);
            umbralRepository.findAll().forEach(u -> m.put(u.getCriticidad(), new Umbrales(u.getLimiteBajo(), u.getLimiteMedio(), u.getMinimoMedio())));
            return m;
        }, centrales::bandasVigentes);
    }

    /** Para pruebas: catálogo y umbrales vienen de afuera (bandas del score: las de la central actual). */
    public CalculoSubservicioService(Supplier<Map<String, CodigoCatalogo>> catalogo, Supplier<Map<CriticidadCargo, Umbrales>> umbrales) {
        this(catalogo, umbrales, () -> MotorFinanciero.BANDAS_POR_DEFECTO);
    }

    public CalculoSubservicioService(Supplier<Map<String, CodigoCatalogo>> catalogo, Supplier<Map<CriticidadCargo, Umbrales>> umbrales,
                                     Supplier<List<MotorFinanciero.Banda>> bandas) {
        this.catalogo = catalogo;
        this.umbrales = umbrales;
        this.bandas = bandas;
    }

    public Optional<CalculoFormulario> calcular(List<FormularioSeccion> secciones, CriticidadCargo criticidadEstudio,
                                                Map<String, String> campos, Map<String, List<Map<String, String>>> tablas) {
        return calcular(secciones, criticidadEstudio, Map.of(), campos, tablas);
    }

    /** estudio: datos del estudio que el cálculo necesita y el analista no diligencia (ver datosDelEstudio). */
    public Optional<CalculoFormulario> calcular(List<FormularioSeccion> secciones, CriticidadCargo criticidadEstudio, Map<String, String> estudio,
                                                Map<String, String> campos, Map<String, List<Map<String, String>>> tablas) {
        MotorHallazgos.Plantilla p = plantilla(secciones);
        // NS-07: la criticidad que el cliente eligió al solicitar manda sobre el campo del formulario (solo el gestor la cambia, y eso actualiza la solicitud).
        String claveCriticidad = MotorHallazgos.claveCriticidad(p);
        if (claveCriticidad != null && estudio.get(CRITICIDAD_SOLICITUD) != null) {
            campos = new LinkedHashMap<>(campos);
            campos.put(claveCriticidad, estudio.get(CRITICIDAD_SOLICITUD));
        }
        // Antecedentes financieros tiene su propio motor: depende del tipo de estudio (OEA), de rangos y de la banda del score.
        if (MotorFinanciero.aplica(p)) {
            return Optional.of(MotorFinanciero.calcular(catalogo.get(), umbrales.get(), bandas.get(), "Sí".equals(estudio.get("es_oea")), campos, tablas));
        }
        if (!MotorHallazgos.califica(p)) return Optional.empty();
        return Optional.of(MotorHallazgos.calcular(p, catalogo.get(), umbrales.get(), criticidadEstudio, campos, tablas));
    }

    /** La criticidad del estudio (la que fijó el gestor o la del proceso) — solo se usa si la
     *  plantilla no tiene campo de criticidad. */
    public static CriticidadCargo criticidadDelEstudio(ServicioSubproceso sp) {
        if (sp == null || sp.getServicio() == null) return null;
        if (sp.getServicio().getCriticidadCargo() != null) return sp.getServicio().getCriticidadCargo();
        var proceso = sp.getServicio().getProceso();
        return proceso != null ? proceso.getCriticidadDefecto() : null;
    }

    /** ¿El estudio es OEA? Lo dice el nombre de su proceso (OEA, "Estudio OEA"...). */
    public static boolean esOea(com.polygraph.erp.modules.servicios.entity.Servicio servicio) {
        var proceso = servicio == null ? null : servicio.getProceso();
        return proceso != null && proceso.getNombreProceso() != null && proceso.getNombreProceso().toUpperCase().matches(".*\\bOEA\\b.*");
    }

    /** Clave en `estudio` con la criticidad que el cliente fijó en la solicitud (NS-07); ausente si la solicitud no la tiene (las anteriores a NS-07). */
    public static final String CRITICIDAD_SOLICITUD = "criticidad_solicitud";

    /** Lo del estudio que el cálculo necesita y el analista no diligencia (si es OEA y la criticidad de la solicitud). */
    public static Map<String, String> datosDelEstudio(ServicioSubproceso sp) {
        Map<String, String> datos = new HashMap<>();
        datos.put("es_oea", sp != null && esOea(sp.getServicio()) ? "Sí" : "No");
        if (sp != null && sp.getServicio() != null && sp.getServicio().getCriticidadCargo() != null) {
            datos.put(CRITICIDAD_SOLICITUD, sp.getServicio().getCriticidadCargo().name());
        }
        return datos;
    }

    /** Catálogo que suma: activo y sin alertas. */
    public static Map<String, CodigoCatalogo> catalogoDe(Collection<CodigoCalificacion> codigos) {
        Map<String, CodigoCatalogo> m = new LinkedHashMap<>();
        codigos.stream().filter(c -> Boolean.TRUE.equals(c.getActivo()) && !Boolean.TRUE.equals(c.getEsAlerta()))
                .sorted(Comparator.comparing(CodigoCalificacion::getOrden))
                .forEach(c -> m.put(c.getCodigo(), new CodigoCatalogo(c.getCodigo(), c.getDescripcion(), c.getPuntos(),
                        Boolean.TRUE.equals(c.getCritico()), Boolean.TRUE.equals(c.getPorProceso()), Boolean.TRUE.equals(c.getSoloConduccion()))));
        return m;
    }

    /** Secciones y columnas activas → lo que necesita el motor. Las secciones de origen automático
     *  (hoja de vida del candidato) no califican. */
    public static MotorHallazgos.Plantilla plantilla(List<FormularioSeccion> secciones) {
        List<MotorHallazgos.Seccion> r = new ArrayList<>();
        secciones.stream().filter(s -> Boolean.TRUE.equals(s.getActivo()) && s.getOrigenFijo() == null)
                .sorted(Comparator.comparing(FormularioSeccion::getOrden))
                .forEach(s -> r.add(new MotorHallazgos.Seccion(s.getClave(), s.getNombre(), s.getTipo() == TipoSeccionFormulario.TABLA,
                        Boolean.TRUE.equals(s.getCalculada()),
                        s.getColumnas().stream().filter(c -> Boolean.TRUE.equals(c.getActivo()) && c.getClave() != null)
                                .sorted(Comparator.comparing(FormularioColumna::getOrden))
                                .map(CalculoSubservicioService::columna).toList())));
        return new MotorHallazgos.Plantilla(r);
    }

    private static MotorHallazgos.Columna columna(FormularioColumna c) {
        Map<String, String> porOpcion = new LinkedHashMap<>();
        c.getHallazgos().forEach(h -> porOpcion.put(h.getOpcion(), h.getCodigo()));
        return new MotorHallazgos.Columna(c.getClave(), c.getNombre() != null ? c.getNombre() : c.getClave(),
                Boolean.TRUE.equals(c.getCalculado()), c.getUsoCalificacion(), c.getCalculo(), c.getReferencia(), c.getTextoNota(),
                c.getFuenteHallazgo(), c.getDetalleHallazgo(), porOpcion);
    }
}
