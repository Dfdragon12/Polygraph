package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.trabajos.dto.TrabajoResponse;
import com.polygraph.erp.modules.trabajos.entity.TrabajoAsincrono;
import com.polygraph.erp.modules.trabajos.service.AlmacenTrabajos;
import com.polygraph.erp.modules.trabajos.service.ColaTrabajosService;
import com.polygraph.erp.modules.trabajos.service.ProcesadorTrabajo;
import com.polygraph.erp.modules.trabajos.service.ResultadoTrabajo;
import com.polygraph.erp.shared.enums.TipoTrabajo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/** "Descargar PDF" del formulario (roles internos: ejecutor, gestor, programador, admin), por la
 *  cola — igual que el informe del cliente (InformePdfProcesador), antes era síncrono dentro del
 *  request.
 *
 *  · Caché SOLO con servicio y subproceso PUBLICADOS: es el mismo archivo que descarga el cliente
 *    (AlmacenTrabajos.rutaCacheInforme), así se genera una sola vez para todos. Mientras no está
 *    publicado el documento todavía cambia — el formulario se sigue llenando o, ya completado, el
 *    riesgo del estudio (${riesgo[...]}) se mueve con los otros subprocesos —, así que se genera
 *    fresco cada vez en resultados/{uuid}.pdf y se borra con la limpieza de trabajos.
 *  · Las reglas de acceso son las de FormularioDocumentoService.generar(): se validan en el
 *    request (403/404 inmediatos), otra vez al procesar y otra al descargar. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormularioPdfProcesador implements ProcesadorTrabajo {

    private static final String TIPO_PDF = "application/pdf";

    private final FormularioDocumentoService documentoService;
    private final DocumentoPdfConverter pdfConverter;
    private final ColaTrabajosService cola;
    private final AlmacenTrabajos almacen;

    @Override
    public TipoTrabajo tipo() {
        return TipoTrabajo.FORMULARIO_PDF;
    }

    public TrabajoResponse solicitar(Long idServicioSubproceso, Usuario usuario) {
        FormularioDocumentoService.DocumentoAutorizado doc = documentoService.validarDescarga(idServicioSubproceso, usuario);
        Map<String, Object> parametros = Map.of("idServicioSubproceso", idServicioSubproceso);
        TrabajoAsincrono trabajo;
        String cache = doc.publicado() ? AlmacenTrabajos.rutaCacheInforme(idServicioSubproceso, doc.fechaPublicado()) : null;
        if (cache != null && almacen.existe(cache)) {
            almacen.tocar(cache);
            trabajo = cola.registrarCompletado(tipo(), usuario, parametros,
                    ResultadoTrabajo.archivo(cache, nombreArchivo(idServicioSubproceso), TIPO_PDF));
            log.info("PDF de formulario desde caché: subproceso={}, usuario={}", idServicioSubproceso, usuario.getEmail());
        } else {
            trabajo = cola.encolar(tipo(), usuario, parametros);
        }
        return cola.aRespuesta(trabajo);
    }

    @Override
    public ResultadoTrabajo procesar(TrabajoAsincrono trabajo, Map<String, Object> parametros, ContextoTrabajo contexto) throws Exception {
        Usuario usuario = trabajo.getUsuario();
        Long idServicioSubproceso = idDe(parametros);
        // Se revalida: entre encolar y procesar pudieron desasignarlo o cambiar de estado.
        FormularioDocumentoService.DocumentoAutorizado doc = documentoService.validarDescarga(idServicioSubproceso, usuario);

        if (!doc.publicado()) {
            String ruta = AlmacenTrabajos.rutaResultado(trabajo.getUuid(), ".pdf");
            almacen.escribirAtomico(ruta, convertir(idServicioSubproceso, usuario, contexto));
            return ResultadoTrabajo.archivo(ruta, nombreArchivo(idServicioSubproceso), TIPO_PDF);
        }

        String ruta = AlmacenTrabajos.rutaCacheInforme(idServicioSubproceso, doc.fechaPublicado());
        ReentrantLock lock = almacen.bloqueo(ruta);
        lock.lock();
        try {
            if (almacen.existe(ruta)) {
                almacen.tocar(ruta);
            } else {
                almacen.escribirAtomico(ruta, convertir(idServicioSubproceso, usuario, contexto));
            }
        } finally {
            lock.unlock();
        }
        return ResultadoTrabajo.archivo(ruta, nombreArchivo(idServicioSubproceso), TIPO_PDF);
    }

    @Override
    public void validarDescarga(TrabajoAsincrono trabajo, Map<String, Object> parametros, Usuario usuario) {
        documentoService.validarDescarga(idDe(parametros), usuario);
    }

    /** .docx con su transacción, y la conversión ya fuera de ella (sin conexión de BD tomada). */
    private byte[] convertir(Long idServicioSubproceso, Usuario usuario, ContextoTrabajo contexto) {
        contexto.progreso(10);
        byte[] docx = documentoService.generar(idServicioSubproceso, usuario);
        contexto.progreso(30);
        byte[] pdf = pdfConverter.convertir(docx);
        log.info("PDF de formulario generado: subproceso={}, {} KB", idServicioSubproceso, pdf.length / 1024);
        return pdf;
    }

    private static Long idDe(Map<String, Object> parametros) {
        return ((Number) parametros.get("idServicioSubproceso")).longValue();
    }

    private static String nombreArchivo(Long idServicioSubproceso) {
        return "formulario-" + idServicioSubproceso + ".pdf";
    }
}
