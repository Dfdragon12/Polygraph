package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.formularios.entity.Formulario;
import com.polygraph.erp.modules.formularios.entity.FormularioDato;
import com.polygraph.erp.modules.formularios.entity.FormularioFila;
import com.polygraph.erp.modules.formularios.entity.FormularioSeccion;
import com.polygraph.erp.modules.formularios.repository.FormularioDatoRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioFilaRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioRepository;
import com.polygraph.erp.modules.formularios.repository.FormularioSeccionRepository;
import com.polygraph.erp.modules.riesgo.codigos.CalculoSubservicioService;
import com.polygraph.erp.modules.riesgo.codigos.ModeloCodigos.CalculoFormulario;
import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Cálculo propio de UN subproceso a partir de lo que tiene guardado — para quien no tiene ya los
 *  datos del formulario en la mano (el motor del estudio, CalificacionRiesgoService). Vacío si la
 *  plantilla no califica por hallazgos o si nunca se guardó nada. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalculoSubprocesoService {

    private final FormularioRepository formularioRepository;
    private final FormularioSeccionRepository seccionRepository;
    private final FormularioFilaRepository filaRepository;
    private final FormularioDatoRepository datoRepository;
    private final CalculoSubservicioService calculoSubservicioService;

    public Optional<CalculoFormulario> calcular(ServicioSubproceso subproceso) {
        Formulario formulario = formularioRepository.findByServicioSubproceso_Id(subproceso.getId()).orElse(null);
        if (formulario == null) return Optional.empty();

        List<FormularioSeccion> secciones = seccionRepository
                .findByTipoProgreso_IdTipoProgresoOrderByOrdenAsc(subproceso.getTipoProgreso().getIdTipoProgreso());
        Map<Integer, List<FormularioFila>> filasPorSeccion = new HashMap<>();
        for (FormularioFila f : filaRepository.findByFormulario_IdFormularioOrderByOrdenAsc(formulario.getIdFormulario())) {
            filasPorSeccion.computeIfAbsent(f.getSeccion().getIdSeccion(), k -> new ArrayList<>()).add(f);
        }
        Map<Integer, String> valoresCampos = new HashMap<>();
        Map<Integer, Map<Integer, String>> valoresPorFila = new HashMap<>();
        for (FormularioDato d : datoRepository.findByFormulario_IdFormulario(formulario.getIdFormulario())) {
            if (d.getFila() == null) valoresCampos.put(d.getColumna().getIdColumna(), d.getValor());
            else valoresPorFila.computeIfAbsent(d.getFila().getIdFila(), k -> new HashMap<>()).put(d.getColumna().getIdColumna(), d.getValor());
        }
        ValoresPorClave.Entrada entrada = ValoresPorClave.armar(secciones, valoresCampos, filasPorSeccion, valoresPorFila);
        return calculoSubservicioService.calcular(secciones, CalculoSubservicioService.criticidadDelEstudio(subproceso),
                CalculoSubservicioService.datosDelEstudio(subproceso), entrada.campos(), entrada.tablas());
    }
}
