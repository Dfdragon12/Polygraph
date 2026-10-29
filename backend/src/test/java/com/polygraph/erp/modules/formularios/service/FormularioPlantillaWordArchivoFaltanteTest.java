package com.polygraph.erp.modules.formularios.service;

import com.polygraph.erp.modules.evaluados.service.AlmacenamientoDocumentosService;
import com.polygraph.erp.modules.formularios.entity.FormularioPlantillaConfig;
import com.polygraph.erp.shared.exceptions.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

/** Si el .docx de la plantilla ya no está en disco, el error debe ser 4xx (la cola de trabajos no
 *  lo reintenta: reintentar no hace aparecer el archivo) y decir qué pasó — antes el cliente veía
 *  "contacta a tu gestor" tras varios minutos de reintentos. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FormularioPlantillaWordService — archivo de plantilla faltante")
class FormularioPlantillaWordArchivoFaltanteTest {

    private static final String RUTA = "plantillas/1/perdida_Antecedentes.docx";

    @Mock private CandidatoInfoService candidatoInfoService;
    @Mock private AlmacenamientoDocumentosService almacenamientoService;
    @InjectMocks private FormularioPlantillaWordService service;

    private ApiException generarCon(boolean puedeEditarPlantilla) {
        when(almacenamientoService.cargarComoResource(RUTA))
                .thenThrow(new ApiException("Archivo no encontrado", HttpStatus.NOT_FOUND));
        FormularioPlantillaConfig config = FormularioPlantillaConfig.builder().plantillaWordRuta(RUTA).build();
        return catchThrowableOfType(() -> service.generar(config, null, null, List.of(), Map.of(), Map.of(), Map.of(),
                puedeEditarPlantilla, null, null), ApiException.class);
    }

    @Test
    @DisplayName("Cliente: 409 con un mensaje claro y sin rutas internas")
    void cliente_mensajeClaroSinRuta() {
        ApiException e = generarCon(false);
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getStatus().is4xxClientError()).isTrue();
        assertThat(e.getMessage()).contains("falta el documento Word").doesNotContain(RUTA);
        // La ruta sí queda en la causa, que el admin ve en Trabajos en segundo plano.
        assertThat(e.getCause()).hasMessageContaining(RUTA);
    }

    @Test
    @DisplayName("Admin/gestor: el mensaje dice cuál archivo falta y dónde volver a subirlo")
    void editor_mensajeConRuta() {
        ApiException e = generarCon(true);
        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getMessage()).contains(RUTA).contains("Plantilla");
    }
}
