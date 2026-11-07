package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.entity.Ciudad;
import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.formularios.dto.CampoCandidatoValorResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** AC-08 · El analista ve expedición y nacimiento del evaluado: lo que está en el candidato, completado con la hoja de vida. */
class CandidatoInfoAnalistaTest {

    @Test
    void expedicionYNacimientoLlegan() {
        CiudadRepository ciudades = mock(CiudadRepository.class);
        HojaVidaEvaluadoRepository hojas = mock(HojaVidaEvaluadoRepository.class);
        when(ciudades.findById(1)).thenReturn(Optional.of(Ciudad.builder().nombreCiudad("Bogotá").build()));
        // El nacimiento lo guardó la hoja de vida (sin lugar escrito); el lugar sale de la ciudad de nacimiento del candidato.
        when(hojas.findByCandidato_IdCandidato(any())).thenReturn(Optional.of(HojaVidaEvaluado.builder().fechaNacimiento(LocalDate.of(1988, 4, 12)).build()));
        Candidato c = Candidato.builder().idCandidato(47L).nombres("Laura").apellidos("Ejemplo").cedula("1056235689").tipoDocumento("CC")
                .fechaExpedicion(LocalDate.of(2006, 5, 20)).idCiudadExpedicion(1).idCiudadNacimiento(1).build();

        Map<String, String> datos = new CandidatoInfoService(ciudades, hojas).resolverParaAnalista(c).stream()
                .collect(Collectors.toMap(CampoCandidatoValorResponse::clave, CampoCandidatoValorResponse::valor));

        assertEquals("20/05/2006", datos.get("FECHA_EXPEDICION"));
        assertEquals("Bogotá", datos.get("CIUDAD_EXPEDICION"));
        assertEquals("12/04/1988", datos.get("FECHA_NACIMIENTO"));
        assertEquals("Bogotá", datos.get("LUGAR_NACIMIENTO"));
        assertEquals("CC", datos.get("TIPO_DOCUMENTO"));
    }
}
