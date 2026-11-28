package com.polygraph.erp.modules.solicitudes.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.auth.repository.UsuarioRepository;
import com.polygraph.erp.modules.formularios.service.DocumentoPdfConverter;
import com.polygraph.erp.modules.formularios.service.FormularioDocumentoService;
import com.polygraph.erp.modules.trabajos.dto.TrabajoResponse;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.modules.trabajos.service.ProcesadorTrabajo;
import com.polygraph.erp.modules.trabajos.service.ResultadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** "Descargar informe" del cliente, por la cola: PDF de un subproceso publicado.
 *
 *  · Caché en disco: un informe publicado no cambia, así que se convierte UNA vez y se guarda en
 *    cache/informes/{subproceso}-{publicación}.pdf. Las siguientes descargas (del mismo usuario o
 *    de otro del mismo cliente) no abren LibreOffice ni esperan cola: el trabajo nace COMPLETADO.
 *    Si la solicitud se despublica y se vuelve a publicar, cambia fechaPublicado y con ella el
 *    nombre del archivo — el PDF viejo simplemente deja de usarse y la limpieza lo borra.
 *  · Lock por archivo (compartido con FormularioPdfProcesador, mismo PDF): si diez personas piden el mismo informe a la vez, uno lo genera y los
 *    otros nueve lo encuentran hecho al entrar.
 *  · Sin conexión de BD durante la conversión: validarInforme y generar() abren y cierran su
 *    transacción; convertir() corre fuera de toda transacción. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InformePdfProcesador implements ProcesadorTrabajo {

    private static final String TIPO_PDF = "application/pdf";

    private final SolicitudService solicitudService;
    private final FormularioDocumentoService documentoService;
    private final DocumentoPdfConverter pdfConverter;
    private final ColaTrabajosService cola;
    private final AlmacenTrabajos almacen;
    private final UsuarioRepository usuarioRepository;

    @Override
    public TipoTrabajo tipo() {
        return TipoTrabajo.INFORME_PDF;
    }

    /** Valida en el request (403/404/409 inmediatos) y encola, o devuelve ya completado si el
     *  PDF está en caché. */
    public TrabajoResponse solicitar(Integer idServicio, Long idServicioSubproceso, UserDetails userDetails) {
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        SolicitudService.InformeAutorizado informe = solicitudService.validarInforme(idServicio, idServicioSubproceso, usuario);
        Map<String, Object> parametros = Map.of("idServicio", idServicio, "idServicioSubproceso", idServicioSubproceso);
        String ruta = rutaCache(informe);
        TrabajoAsincrono trabajo;
        if (almacen.existe(ruta)) {
            almacen.tocar(ruta);
            trabajo = cola.registrarCompletado(tipo(), usuario, parametros, ResultadoTrabajo.archivo(ruta, informe.nombreArchivo(), TIPO_PDF));
            log.info("Informe desde caché: servicio={}, subproceso={}, usuario={}", idServicio, idServicioSubproceso, usuario.getEmail());
        } else {
            trabajo = cola.encolar(tipo(), usuario, parametros);
        }
        return cola.aRespuesta(trabajo);
    }

    @Override
    public ResultadoTrabajo procesar(TrabajoAsincrono trabajo, Map<String, Object> parametros, ContextoTrabajo contexto) throws Exception {
        Usuario usuario = trabajo.getUsuario();
        // Se revalida: entre encolar y procesar la solicitud pudo dejar de estar publicada.
        SolicitudService.InformeAutorizado informe = validar(parametros, usuario);
        String ruta = rutaCache(informe);
        ReentrantLock lock = almacen.bloqueo(ruta);
        lock.lock();
        try {
            if (!almacen.existe(ruta)) {
                contexto.progreso(10);
                byte[] docx = documentoService.generar(informe.idServicioSubproceso(), usuario);
                contexto.progreso(30);
                byte[] pdf = pdfConverter.convertir(docx);
                almacen.escribirAtomico(ruta, pdf);
                log.info("Informe generado: servicio={}, subproceso={}, {} KB", informe.idServicio(),
                        informe.idServicioSubproceso(), pdf.length / 1024);
            } else {
                almacen.tocar(ruta);
            }
        } finally {
            lock.unlock();
        }
        return ResultadoTrabajo.archivo(ruta, informe.nombreArchivo(), TIPO_PDF);
    }

    /** Al descargar, quien descarga debe seguir teniendo acceso y el informe seguir publicado. */
    @Override
    public void validarDescarga(TrabajoAsincrono trabajo, Map<String, Object> parametros, Usuario usuario) {
        validar(parametros, usuario);
    }

    private SolicitudService.InformeAutorizado validar(Map<String, Object> parametros, Usuario usuario) {
        Integer idServicio = ((Number) parametros.get("idServicio")).intValue();
        Long idServicioSubproceso = ((Number) parametros.get("idServicioSubproceso")).longValue();
        return solicitudService.validarInforme(idServicio, idServicioSubproceso, usuario);
    }

    private static String rutaCache(SolicitudService.InformeAutorizado informe) {
        return AlmacenTrabajos.rutaCacheInforme(informe.idServicioSubproceso(), informe.fechaPublicado());
    }
}
