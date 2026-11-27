package com.polygraph.erp.modules.evaluados.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.polygraph.erp.modules.evaluados.dto.HojaVidaRequest;
import com.polygraph.erp.shared.exceptions.ApiException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PC-09 (PB-04 A): lo que manda el portal en un enlace CORTO (datos personales, las 3 declaraciones judiciales y la autorización; educación, experiencia y referencias VACÍAS)
 * tiene que poder enviarse. La prueba de punta a punta lo encontró roto: el DTO exigía experiencia y 4 referencias y respondía 400, así que ningún candidato con enlace corto
 * podía terminar. Esas dos reglas viven ahora en el servicio, solo para el enlace completo.
 */
@DisplayName("PC-09 — enviar la hoja de vida del enlace corto")
class PC09EnlaceCortoTest {

    private static final String ENLACE_CORTO = """
            {"autorizacionDatos":true,"fechaNacimiento":"1995-03-12","lugarNacimiento":"Bogotá","idCiudadNacimiento":1,"idCiudadResidencia":1,"estadoCivil":"Soltero(a)",
             "nivelEducativo":"Tecnólogo","direccion":"Calle 10 # 20-30","barrio":"Chapinero","estrato":"3","email":"laura.ficticia@ejemplo.co","celular":"3101234567","rh":"O+",
             "libretaMilitar":"No aplica","fondoPensiones":"Porvenir","eps":"Sanitas","telefonoFijo":"6011234567","fechaExpedicion":"2013-03-20","idCiudadExpedicion":1,
             "tieneVisa":false,"tienePasaporte":false,"sinEstudios":true,
             "respuestas":[{"idPregunta":35,"respuesta":false},{"idPregunta":36,"respuesta":false},{"idPregunta":37,"respuesta":false}],
             "educacion":[],"experienciaLaboral":[],"referencias":[],"justificacionesInactividad":{}}
            """;

    private HojaVidaRequest corto() throws Exception {
        return new ObjectMapper().registerModule(new JavaTimeModule()).readValue(ENLACE_CORTO, HojaVidaRequest.class);
    }

    @Test
    @DisplayName("El envío del enlace corto (sin experiencia ni referencias) pasa la validación del DTO")
    void elDtoNoRechazaElEnlaceCorto() throws Exception {
        Validator v = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(v.validate(corto())).as("violaciones: %s", v.validate(corto())).isEmpty();
    }

    @Test
    @DisplayName("El enlace COMPLETO sí exige al menos una experiencia y 4 referencias (ahora lo dice el servicio)")
    void elCompletoSigueExigiendoExperienciaYReferencias() throws Exception {
        assertThatThrownBy(() -> EvaluadoService.exigirExperienciaYReferencias(corto()))
                .isInstanceOf(ApiException.class).hasMessageContaining("experiencia laboral");

        HojaVidaRequest conExperiencia = new ObjectMapper().registerModule(new JavaTimeModule()).readValue(
                ENLACE_CORTO.replace("\"experienciaLaboral\":[]", "\"experienciaLaboral\":[{\"empresa\":\"Bodegas Imaginarias\",\"cargo\":\"Auxiliar\",\"contratadoPorTemporal\":false}]"), HojaVidaRequest.class);
        assertThatThrownBy(() -> EvaluadoService.exigirExperienciaYReferencias(conExperiencia))
                .isInstanceOf(ApiException.class).hasMessageContaining("4 referencias");

        String cuatro = "[" + "{\"nombre\":\"R\",\"telefono\":\"3108880010\"},".repeat(3) + "{\"nombre\":\"R\",\"telefono\":\"3108880040\"}]";
        HojaVidaRequest completo = new ObjectMapper().registerModule(new JavaTimeModule()).readValue(
                ENLACE_CORTO.replace("\"experienciaLaboral\":[]", "\"experienciaLaboral\":[{\"empresa\":\"Bodegas Imaginarias\",\"cargo\":\"Auxiliar\",\"contratadoPorTemporal\":false}]")
                        .replace("\"referencias\":[]", "\"referencias\":" + cuatro), HojaVidaRequest.class);
        EvaluadoService.exigirExperienciaYReferencias(completo);   // no lanza
    }

    @Test
    @DisplayName("Un enlace inválido o vencido responde 404/410, no 500: servicioConAcceso no marca la transacción como «solo rollback» al lanzar ApiException")
    void elEnlaceInvalidoNoEs500() throws Exception {
        var t = EvaluadoService.class.getMethod("servicioConAcceso", String.class).getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(t.noRollbackFor()).contains(ApiException.class);
    }
}
