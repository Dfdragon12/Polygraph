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

    private static final java.util.Set<Rol> ROLES_EJECUTORES = java.util.Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

    /** Mismo documento que generar() (mismo .docx, mismos marcadores resueltos) — solo se le
     *  cambia el formato de salida a PDF al final, para que "Descargar PDF" muestre el mismo
     *  diseño que arma el administrador en Word, no un reporte genérico aparte. */
    public byte[] generarPdf(Long idServicioSubproceso, Usuario actor) {
        return pdfConverter.convertir(generar(idServicioSubproceso, actor));
    }

    public byte[] generar(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso subproceso = servicioSubprocesoRepository.findById(idServicioSubproceso)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado", HttpStatus.NOT_FOUND));
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
        if (formulario == null && subproceso.getEstado() != EstadoAsignacion.COMPLETADO) {
            throw new ApiException("Aún no se ha guardado el formulario de este subproceso.", HttpStatus.NOT_FOUND);
        }

        Servicio servicio = subproceso.getServicio();
        Candidato candidato = servicio != null ? servicio.getCandidato() : null;
        Cliente cliente = servicio != null ? servicio.getCliente() : null;

        // Mismo criterio que FormularioService.obtener(): una sección agregada a la plantilla
        // DESPUÉS de que este subproceso ya existía no debe aparecer en el documento generado,
        // así como no aparece en pantalla.
        List<FormularioSeccion> secciones = seccionRepository
                .findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(subproceso.getTipoProgreso().getIdTipoProgreso())
                .stream().filter(s -> s.getVersionCreacion() <= subproceso.getVersionPlantilla()).toList();
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

        FormularioPlantillaConfig config = configRepository.findById(subproceso.getTipoProgreso().getIdTipoProgreso()).orElse(null);
        boolean actorPuedeEditarPlantilla = !ROLES_EJECUTORES.contains(actor.getRol());
        return plantillaWordService.generar(config, candidato, cliente, secciones, valoresCampos, filasPorSeccion, valoresPorFila,
                actorPuedeEditarPlantilla);
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
