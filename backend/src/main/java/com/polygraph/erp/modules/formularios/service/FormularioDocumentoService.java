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
import com.polygraph.erp.shared.enums.EstadoSubproceso;
import com.polygraph.erp.shared.enums.EstadoServicio;
import com.polygraph.erp.shared.enums.Rol;
import com.polygraph.erp.shared.exceptions.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final com.polygraph.erp.modules.riesgo.service.CalificacionRiesgoService calificacionRiesgoService;
    private final com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService calculoSubservicioService;

    private static final java.util.Set<Rol> ROLES_EJECUTORES = java.util.Set.of(Rol.ANALISTA_INTERNO, Rol.POLIGRAFISTA, Rol.VISITADOR);

    /** Misma noción de "cerrado" que FormularioService.estaCongelado — COMPLETADO y PUBLICADO se
     *  documentan igual, congelados en la versión de plantilla que tenían al completarse. */
    private static boolean estaCongelado(EstadoSubproceso estado) {
        return estado == EstadoSubproceso.FINALIZADO || estado == EstadoSubproceso.PUBLICADO;
    }

    // El PDF NO se genera aquí a propósito: esta clase es @Transactional, y convertir con
    // LibreOffice dentro de ella retenía una conexión del pool de BD hasta 60 s por descarga
    // (10 descargas a la vez = pool agotado = todo el sistema esperando). Quien necesite PDF
    // llama generar() (abre y cierra su transacción) y DESPUÉS DocumentoPdfConverter.convertir,
    // ya sin conexión tomada — ver FormularioPdfProcesador e InformePdfProcesador.

    /** Documento que el actor SÍ puede descargar. publicado = servicio y subproceso PUBLICADOS:
     *  solo entonces el documento ya no cambia (el riesgo del estudio, que va en los marcadores
     *  ${riesgo[...]}, se bloquea al publicar el servicio) y el PDF se puede guardar en caché.
     *  Un subproceso solo COMPLETADO todavía puede cambiar de riesgo por los demás subprocesos. */
    public record DocumentoAutorizado(Long idServicioSubproceso, boolean publicado, LocalDateTime fechaPublicado) {}

    /** Las mismas reglas de acceso de generar(), sin generar nada — para responder 403/404 en el
     *  request antes de encolar el PDF. */
    public DocumentoAutorizado validarDescarga(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso subproceso = cargarAutorizado(idServicioSubproceso, actor);
        exigirFormularioGuardado(subproceso, formularioRepository.findByServicioSubproceso_Id(idServicioSubproceso).orElse(null));
        Servicio servicio = subproceso.getServicio();
        boolean publicado = subproceso.getEstado() == EstadoSubproceso.PUBLICADO
                && servicio != null && servicio.getEstado() == EstadoServicio.PUBLICADO;
        return new DocumentoAutorizado(idServicioSubproceso, publicado, subproceso.getFechaPublicado());
    }

    private ServicioSubproceso cargarAutorizado(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso subproceso = servicioSubprocesoRepository.findById(idServicioSubproceso)
                .orElseThrow(() -> new ApiException("Subproceso no encontrado", HttpStatus.NOT_FOUND));
        com.polygraph.erp.shared.utils.AccesoGestor.validar(subproceso, actor);
        if (ROLES_EJECUTORES.contains(actor.getRol())) {
            Usuario asignado = subproceso.getUsuarioAsignado();
            if (asignado == null || !actor.getIdUsuario().equals(asignado.getIdUsuario())) {
                throw new ApiException("Este subproceso no está asignado a ti.", HttpStatus.FORBIDDEN);
            }
        }
        return subproceso;
    }

    // Si el subproceso ya está completado, no bloqueamos la descarga aunque nunca se haya
    // guardado un Formulario (ej. un tipo de progreso cuyas columnas son todas automáticas,
    // resueltas del candidato/cliente, sin que el ejecutor tuviera que guardar nada a mano) —
    // FormularioService.guardar ya no deja guardar una vez COMPLETADO, así que exigir un
    // Formulario guardado en ese estado sería un callejón sin salida: no se puede guardar
    // porque está completado, y no se puede descargar porque nunca se guardó. Mientras sigue
    // en progreso, sí se exige (nudge para que guarde antes de descargar).
    private static void exigirFormularioGuardado(ServicioSubproceso subproceso, Formulario formulario) {
        if (formulario == null && !estaCongelado(subproceso.getEstado())) {
            throw new ApiException("Aún no se ha guardado el formulario de este subproceso.", HttpStatus.NOT_FOUND);
        }
    }

    public byte[] generar(Long idServicioSubproceso, Usuario actor) {
        ServicioSubproceso subproceso = cargarAutorizado(idServicioSubproceso, actor);
        Formulario formulario = formularioRepository.findByServicioSubproceso_Id(idServicioSubproceso).orElse(null);
        exigirFormularioGuardado(subproceso, formulario);
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
        // Solo roles internos de coordinación ven el detalle técnico de un error de plantilla — ni
        // los ejecutores ni el cliente (que descarga su informe publicado) pueden corregirla.
        boolean actorPuedeEditarPlantilla = actor.getRol() == Rol.ADMIN_POLYGRAPH
                || actor.getRol() == Rol.GESTOR || actor.getRol() == Rol.PROGRAMADOR;
        var marcadores = servicio != null
                ? calificacionRiesgoService.marcadoresWord(servicio.getIdServicio(), idServicioSubproceso)
                : null;
        // Se calcula otra vez justo antes de generar, con lo guardado (docs/antecedentes, Prompt 4):
        // el Word nunca queda con un puntaje o una numeración vieja.
        ValoresPorClave.Entrada entrada = ValoresPorClave.armar(secciones, valoresCampos, filasPorSeccion, valoresPorFila);
        var calculo = calculoSubservicioService.calcular(secciones,
                com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService.criticidadDelEstudio(subproceso),
                com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService.datosDelEstudio(subproceso),
                entrada.campos(), entrada.tablas()).orElse(null);
        return plantillaWordService.generar(config, subproceso, candidato, cliente, secciones, valoresCampos, filasPorSeccion, valoresPorFila,
                actorPuedeEditarPlantilla, marcadores != null ? marcadores.riesgo() : null,
                marcadores != null ? marcadores.calificacion() : null, calculo,
                formulario != null ? formulario.getSeccionesOmitidas() : java.util.Set.of());
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
