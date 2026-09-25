package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.formularios.dto.GuardarPlantillaRequest;
import com.polygraph.erp.modules.formularios.entity.*;
import com.polygraph.erp.modules.formularios.repository.*;
import com.polygraph.erp.modules.servicios.entity.Servicio;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import com.polygraph.erp.modules.servicios.repository.ServicioSubprocesoRepository;
import com.polygraph.erp.shared.enums.EstadoAsignacion;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Genera el documento Word de CUALQUIER formulario a partir de su plantilla y de los datos ya
 *  guardados — no hay nada hardcodeado por módulo, la estructura (secciones, columnas, filas) la
 *  define admin/catálogo → Plantilla. El documento en sí lo arma FormularioPlantillaWordService a
 *  partir del .docx con marcadores que el administrador subió — este servicio solo reúne los
 *  datos guardados (formulario, secciones, filas, valores) y se los pasa. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormularioDocumentoService {

    private final FormularioRepository formularioRepository;
    private final FormularioSeccionRepository seccionRepository;
    private final FormularioFilaRepository filaRepository;
    private final FormularioDatoRepository datoRepository;
    private final ServicioSubprocesoRepository servicioSubprocesoRepository;
    private final FormularioPlantillaConfigRepository configRepository;
    private final FormularioPlantillaWordService plantillaWordService;
    private final DocumentoPdfConverter pdfConverter;
    private final com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService calificacionRiesgoService;

    private static final java.util.Set<Rol> ROLES_EJECUTORES = java.util.Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

    /** Misma noción de "cerrado" que FormularioService.estaCongelado — COMPLETADO y PUBLICADO se
     *  documentan igual, congelados en la versión de plantilla que tenían al completarse. */
    private static boolean estaCongelado(EstadoAsignacion estado) {
        return estado == EstadoAsignacion.COMPLETADO || estado == EstadoAsignacion.PUBLICADO;
    }

    /** Mismo documento que generar() (mismo .docx, mismos marcadores resueltos) — solo se le
     *  cambia el formato de salida a PDF al final, para que "Descargar PDF" muestre el mismo
     *  diseño que arma el administrador en Word, no un reporte genérico aparte. */
    public byte[] generarPdf(Long idServicioSubproceso, Usuario actor) {
        return pdfConverter.convertir(generar(idServicioSubproceso, actor));
    }

    public byte[] generar(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso subproceso = servicioSubprocesoRepository.findById(idServicioSubproceso)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado", HttpStatus.NOT_FOUND));
        com.polygraph.erp.shared.utils.AccesoGestor.validar(subproceso, actor);
        if (ROLES_EJECUTORES.contains(actor.getRol())) {
            Usuario asignado = subproceso.getUsuarioAsignado();
            if (asignado == null || !actor.getIdUsuario().equals(asignado.getIdUsuario())) {
                throw new ApiException("Este subproceso no está asignado a ti.", HttpStatus.FORBIDDEN);
            }
        }
        Formulario formulario = formularioRepository.findByServicioSubproceso_Id(idServicioSubproceso).orElse(null);
        // Si el subproceso ya está completado, no bloqueamos la descarga aunque nunca se haya
        // guardado un Formulario (ej. un tipo de progreso cuyas columnas son todas automáticas,
        // resueltas del candidato/cliente, sin que el ejecutor tuviera que guardar nada a mano) —
        // FormularioService.guardar ya no deja guardar una vez COMPLETADO, así que exigir un
        // Formulario guardado en ese estado sería un callejón sin salida: no se puede guardar
        // porque está completado, y no se puede descargar porque nunca se guardó. Mientras sigue
        // en progreso, sí se exige (nudge para que guarde antes de descargar).
        if (formulario == null && !estaCongelado(subproceso.getEstado())) {
            throw new ApiException("Aún no se ha guardado el formulario de este subproceso.", HttpStatus.NOT_FOUND);
        }

        Servicio servicio = subproceso.getServicio();
        Candidato candidato = servicio != null ? servicio.getCandidato() : null;
        Cliente cliente = servicio != null ? servicio.getCliente() : null;

        // Mismo criterio que FormularioService.obtener(): el congelamiento por versión solo
        // aplica una vez COMPLETADO — mientras sigue en progreso, el documento sigue la
        // plantilla vigente igual que la pantalla.
        List<FormularioSeccion> secciones = seccionRepository
                .findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(subproceso.getTipoProgreso().getIdTipoProgreso())
                .stream()
                .filter(s -> !estaCongelado(subproceso.getEstado()) || s.getVersionCreacion() <= subproceso.getVersionPlantilla())
                .toList();
        List<FormularioFila> filas = formulario != null
                ? filaRepository.findByFormulario_IdFormularioOrderByOrdenAsc(formulario.getIdFormulario())
                : List.of();
        List<FormularioDato> datos = formulario != null
                ? datoRepository.findByFormulario_IdFormulario(formulario.getIdFormulario())
                : List.of();

        Map<Integer, List<FormularioFila>> filasPorSeccion = new HashMap<>();
        for (FormularioFila f : filas) filasPorSeccion.computeIfAbsent(f.getSeccion().getIdSeccion(), k -> new java.util.ArrayList<>()).add(f);
        Map<Integer, String> valoresCampos = new HashMap<>();
        Map<Integer, Map<Integer, String>> valoresPorFila = new HashMap<>();
        for (FormularioDato d : datos) {
            if (d.getFila() == null) valoresCampos.put(d.getColumna().getIdColumna(), d.getValor());
            else valoresPorFila.computeIfAbsent(d.getFila().getIdFila(), k -> new HashMap<>()).put(d.getColumna().getIdColumna(), d.getValor());
        }

        // Un subproceso ya cerrado (COMPLETADO/PUBLICADO) siempre se regenera con el MISMO Word
        // que estaba activo cuando se completó (ver AsignacionService.completar(), que estampa
        // rutaWordCongelada) — así no se mezcla con un .docx más nuevo que ya no tiene marcadores
        // para campos viejos, o que trae marcadores nuevos que este subproceso nunca diligenció.
        // rutaWordCongelada es null en subprocesos completados antes de que existiera esta
        // columna, o si nunca hubo un Word subido al completarse — en esos casos se sigue usando
        // el config vigente, igual que siempre (mejor esfuerzo, no hay forma de reconstruir cuál
        // era el Word de ese momento).
        FormularioPlantillaConfig config = (estaCongelado(subproceso.getEstado()) && subproceso.getRutaWordCongelada() != null)
                ? FormularioPlantillaConfig.builder().plantillaWordRuta(subproceso.getRutaWordCongelada()).build()
                : configRepository.findById(subproceso.getTipoProgreso().getIdTipoProgreso()).orElse(null);
        boolean actorPuedeEditarPlantilla = !ROLES_EJECUTORES.contains(actor.getRol());
        var marcadores = servicio != null
                ? calificacionRiesgoService.marcadoresWord(servicio.getIdServicio(), idServicioSubproceso)
                : null;
        return plantillaWordService.generar(config, candidato, cliente, secciones, valoresCampos, filasPorSeccion, valoresPorFila,
                actorPuedeEditarPlantilla, marcadores != null ? marcadores.riesgo() : null,
                marcadores != null ? marcadores.calificacion() : null);
    }

    /** Vista previa de cómo quedaría el documento, generada directo desde la estructura que se
     *  está editando en admin/catálogo → Plantilla (aunque todavía no se haya guardado) — con
     *  datos de ejemplo, no reales, porque todavía no existe ningún subproceso lleno con esta
     *  plantilla. Solo para que el administrador vea el resultado mientras la arma. */
    public byte[] generarVistaPrevia(Integer idTipoProgreso, GuardarPlantillaRequest req) {
        FormularioPlantillaConfig config = configRepository.findById(idTipoProgreso).orElse(null);
        return plantillaWordService.generarVistaPrevia(config, req);
    }
}
