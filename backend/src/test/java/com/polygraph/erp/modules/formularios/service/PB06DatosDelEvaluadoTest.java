package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.catalogo.repository.CiudadRepository;
import com.polygraph.erp.modules.evaluados.entity.Candidato;
import com.polygraph.erp.modules.evaluados.entity.HojaVidaEvaluado;
import com.polygraph.erp.modules.evaluados.repository.HojaVidaEvaluadoRepository;
import com.polygraph.erp.modules.formularios.dto.CampoCandidatoValorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PB-06 · El panel «Lo que registró el evaluado» muestra lo que escribió el candidato en su hoja de vida (celular, correo, dirección...) y, si difiere de lo
 * que trae la solicitud del cliente, también lo de la solicitud. Antes mostraba solo lo de la solicitud y la dirección salía «sin dato».
 */
@DisplayName("PB-06 — datos del evaluado: hoja de vida y solicitud")
class PB06DatosDelEvaluadoTest {

    private Map<String, CampoCandidatoValorResponse> datos(Candidato c, HojaVidaEvaluado hoja) {
        HojaVidaEvaluadoRepository hojas = mock(HojaVidaEvaluadoRepository.class);
        when(hojas.findByCandidato_IdCandidato(any())).thenReturn(Optional.ofNullable(hoja));
        return new CandidatoInfoService(mock(CiudadRepository.class), hojas).resolverParaAnalista(c).stream()
                .collect(Collectors.toMap(CampoCandidatoValorResponse::clave, Function.identity()));
    }

    @Test
    @DisplayName("Lo que escribió el evaluado manda; lo de la solicitud va aparte cuando es distinto")
    void hojaDeVidaManda() {
        Candidato c = Candidato.builder().idCandidato(53L).cedula("1").celular("3556369896").emailPrincipal("escolta@ejemplo.com").build();
        HojaVidaEvaluado h = HojaVidaEvaluado.builder().celular("3001234567").email("camilo@correo.com").direccion("Calle 10 # 5-20").direccionAdicional("Apto 301")
                .barrio("Chapinero").build();
        var d = datos(c, h);
        assertEquals("3001234567", d.get("CELULAR").valor());
        assertEquals("3556369896", d.get("CELULAR").valorSolicitud());
        assertEquals("camilo@correo.com", d.get("EMAIL_PRINCIPAL").valor());
        assertEquals("escolta@ejemplo.com", d.get("EMAIL_PRINCIPAL").valorSolicitud());
        assertEquals("Calle 10 # 5-20 Apto 301", d.get("DIRECCION_RESIDENCIA").valor());   // antes: «sin dato en el link»
        assertNull(d.get("DIRECCION_RESIDENCIA").valorSolicitud());
        assertEquals("Chapinero", d.get("BARRIO_RESIDENCIA").valor());
    }

    @Test
    @DisplayName("Si coinciden (aunque cambien espacios o mayúsculas) no se repite; si el evaluado no escribió nada, queda lo de la solicitud")
    void coincidenOVacio() {
        Candidato c = Candidato.builder().idCandidato(53L).cedula("1").celular("300 123 4567").emailPrincipal("Camilo@Correo.com").direccionResidencia("Calle 1").build();
        var igual = datos(c, HojaVidaEvaluado.builder().celular("3001234567").email("camilo@correo.com").build());
        assertEquals("3001234567", igual.get("CELULAR").valor());
        assertNull(igual.get("CELULAR").valorSolicitud());
        assertNull(igual.get("EMAIL_PRINCIPAL").valorSolicitud());
        assertEquals("Calle 1", igual.get("DIRECCION_RESIDENCIA").valor());                 // la hoja no tiene dirección: queda la de la solicitud
        assertNull(igual.get("DIRECCION_RESIDENCIA").valorSolicitud());

        var sinHoja = datos(c, null);
        assertEquals("300 123 4567", sinHoja.get("CELULAR").valor());
        assertNull(sinHoja.get("CELULAR").valorSolicitud());
    }
}
