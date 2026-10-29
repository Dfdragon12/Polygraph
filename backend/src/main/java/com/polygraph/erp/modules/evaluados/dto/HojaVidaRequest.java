package com.polygraph.erp.modules.evaluados.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record HojaVidaRequest(
        @NotNull(message = "Debe aceptar la autorización de datos")
        Boolean autorizacionDatos,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        LocalDate fechaNacimiento,

        String lugarNacimiento,

        @NotNull(message = "La ciudad de nacimiento es obligatoria")
        Integer idCiudadNacimiento,

        @NotNull(message = "La ciudad de residencia es obligatoria")
        Integer idCiudadResidencia,

        @NotBlank(message = "El estado civil es obligatorio")
        String estadoCivil,

        String nivelEducativo,

        @NotBlank(message = "La dirección de residencia es obligatoria")
        String direccion,

        @NotBlank(message = "El barrio de residencia es obligatorio")
        String barrio,

        @NotBlank(message = "El estrato es obligatorio")
        String estrato,

        @NotBlank(message = "El email es obligatorio")
        String email,

        @NotBlank(message = "El celular es obligatorio")
        @Pattern(regexp = "3[0-9]{9}", message = "El celular debe tener 10 dígitos y empezar por 3 (ej. 3001234567)")
        String celular,

        @NotBlank(message = "El RH es obligatorio")
        String rh,

        @NotBlank(message = "La libreta militar es obligatoria")
        String libretaMilitar,
        // Número de la visa / del pasaporte — solo si tieneVisa / tienePasaporte es true
        String visa,
        String pasaporte,
        @NotBlank(message = "El fondo de pensiones es obligatorio")
        String fondoPensiones,
        @NotBlank(message = "La EPS es obligatoria")
        String eps,
        // Teléfono fijo y/o número de WhatsApp (con código de país: +57 300 123 4567)
        @Pattern(regexp = "^$|^[+]?[0-9 ]{7,17}$", message = "El teléfono fijo o WhatsApp solo admite números (ej. +57 300 123 4567)")
        String telefonoFijo,

        // Vacía solo si sinEstudios = true (ver isEducacionRegistrada)
        @Valid
        List<EducacionRequest> educacion,

        @Valid @NotEmpty(message = "Debe registrar al menos una experiencia laboral")
        List<ExperienciaLaboralRequest> experienciaLaboral,

        @Valid @NotNull(message = "Se requieren 4 referencias personales")
        @Size(min = 4, message = "Se requieren 4 referencias personales")
        List<ReferenciaPersonalRequest> referencias,

        // Clave "fechaInicio_fechaFin" (yyyy-MM-dd) -> texto de justificación del tiempo muerto laboral
        Map<String, String> justificacionesInactividad,

        Integer pasoActual,

        // Los tres siguientes van al final a propósito (no intercalados con el resto) para no
        // romper otros lugares que construyen este record por posición al agregarlos.
        // Opcionales — solo aplican si el candidato nació o vive fuera de Colombia; si vienen
        // null, la ciudad de nacimiento/residencia ya alcanza a ubicarlo (caso normal, Colombia).
        Integer idPaisNacimiento,
        Integer idPaisResidencia,

        // Opcional — referencia al catálogo de barrios (ver BarrioService); si viene, reemplaza
        // el texto libre de `barrio` como dato "vivo" hacia adelante, sin dejar de guardar `barrio`.
        Integer idBarrio,

        // Agregados 2026-10-13 (también al final, por la misma razón).
        // Fecha y ciudad de expedición del documento de identidad (se guardan en el candidato).
        LocalDate fechaExpedicion,
        Integer idCiudadExpedicion,

        // Complemento de la dirección: número de casa, apartamento, torre...
        String direccionAdicional,

        @NotNull(message = "Indique si tiene visa")
        Boolean tieneVisa,
        String visaPais,

        @NotNull(message = "Indique si tiene pasaporte")
        Boolean tienePasaporte,

        // "No tengo estudios para registrar": la lista de educación va vacía
        Boolean sinEstudios,

        // Declaraciones: una respuesta por pregunta activa (las exige EvaluadoService al enviar)
        List<RespuestaHojaVidaRequest> respuestas,

        // Por cada periodo sin empleo (misma clave que justificacionesInactividad, que lleva el
        // detalle): qué actividad realizó y quién o qué documento puede confirmarlo.
        Map<String, DetalleInactividad> detallesInactividad
) {
    /** Actividad y confirmación de un periodo sin empleo de más de 30 días. */
    public record DetalleInactividad(String actividad, String quienConfirma) {}

    /** Constructor sin el detalle de inactividad (quien no lo manda). */
    public HojaVidaRequest(Boolean autorizacionDatos, LocalDate fechaNacimiento, String lugarNacimiento,
                           Integer idCiudadNacimiento, Integer idCiudadResidencia, String estadoCivil,
                           String nivelEducativo, String direccion, String barrio, String estrato, String email,
                           String celular, String rh, String libretaMilitar, String visa, String pasaporte,
                           String fondoPensiones, String eps, String telefonoFijo,
                           List<EducacionRequest> educacion, List<ExperienciaLaboralRequest> experienciaLaboral,
                           List<ReferenciaPersonalRequest> referencias, Map<String, String> justificacionesInactividad,
                           Integer pasoActual, Integer idPaisNacimiento, Integer idPaisResidencia, Integer idBarrio,
                           LocalDate fechaExpedicion, Integer idCiudadExpedicion, String direccionAdicional,
                           Boolean tieneVisa, String visaPais, Boolean tienePasaporte, Boolean sinEstudios,
                           List<RespuestaHojaVidaRequest> respuestas) {
        this(autorizacionDatos, fechaNacimiento, lugarNacimiento, idCiudadNacimiento, idCiudadResidencia,
                estadoCivil, nivelEducativo, direccion, barrio, estrato, email, celular, rh, libretaMilitar,
                visa, pasaporte, fondoPensiones, eps, telefonoFijo, educacion, experienciaLaboral, referencias,
                justificacionesInactividad, pasoActual, idPaisNacimiento, idPaisResidencia, idBarrio,
                fechaExpedicion, idCiudadExpedicion, direccionAdicional, tieneVisa, visaPais, tienePasaporte,
                sinEstudios, respuestas, null);
    }

    @JsonIgnore
    @AssertTrue(message = "Registre al menos un estudio o marque que no tiene estudios para registrar")
    public boolean isEducacionRegistrada() {
        return Boolean.TRUE.equals(sinEstudios) || (educacion != null && !educacion.isEmpty());
    }

    @JsonIgnore
    @AssertTrue(message = "Indique el país y el número de la visa")
    public boolean isVisaCompleta() {
        return !Boolean.TRUE.equals(tieneVisa) || (tieneTexto(visa) && tieneTexto(visaPais));
    }

    @JsonIgnore
    @AssertTrue(message = "Indique el número del pasaporte")
    public boolean isPasaporteCompleto() {
        return !Boolean.TRUE.equals(tienePasaporte) || tieneTexto(pasaporte);
    }

    private static boolean tieneTexto(String v) {
        return v != null && !v.isBlank();
    }
}
