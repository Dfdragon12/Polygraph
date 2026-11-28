package com.polygraph.erp.modules.gestor.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Vista de solo lectura, para el gestor, de la hoja de vida que el evaluado diligenció
// a través de su link público.
public record ResumenEvaluadoResponse(
        String nombres,
        String apellidos,
        String cedula,
        String tipoDocumento,

        // Autorización de tratamiento de datos (Ley 1581): la acepta el candidato en la primera
        // pantalla de su link; el gestor solo la consulta.
        Boolean autorizacionDatos,
        LocalDateTime fechaAutorizacion,

        Boolean completado,
        LocalDateTime fechaCompletado,
        Integer progresoPorcentaje,
        Integer pasoActual,

        LocalDate fechaNacimiento,
        String estadoCivil,
        String rh,
        String nivelEducativo,
        String ciudadNacimiento,
        String ciudadResidencia,
        String direccion,
        String barrio,
        String estrato,
        String email,
        String celular,
        String telefonoFijo,
        String libretaMilitar,
        String visa,
        String pasaporte,
        String fondoPensiones,
        String eps,

        // Expedición del documento, complemento de la dirección y visa/pasaporte Sí-No (V211)
        LocalDate fechaExpedicion,
        String ciudadExpedicion,
        String direccionAdicional,
        Boolean tieneVisa,
        String visaPais,
        Boolean tienePasaporte,
        Boolean sinEstudios,

        List<EducacionItem> educacion,
        List<ExperienciaItem> experienciaLaboral,
        List<InactividadItem> inactividades,
        List<ReferenciaItem> referencias,
        List<DocumentoItem> documentos,

        // Declaraciones del candidato (preguntas que administra el admin)
        List<DeclaracionItem> declaraciones
) {
    public record DeclaracionItem(String pregunta, Boolean respuesta, String detalle) {}

    public record EducacionItem(
            String nivel, String institucion, String titulo,
            LocalDate fechaInicio, LocalDate fechaFin, Boolean enCurso, String ciudad,
            String estado, Boolean enExterior, String paisExterior
    ) {}

    public record ExperienciaItem(
            String empresa, String cargo, LocalDate fechaInicio, LocalDate fechaFin,
            Boolean laboraActualmente, String ciudad, String telefonoEmpresa,
            String motivoRetiro, String nombreJefe, String cargoJefe,
            String tipoContrato, Long salario, String llamadosAtencion,
            Boolean contratadoPorTemporal, String nombreTemporal
    ) {}

    public record InactividadItem(
            LocalDate fechaInicio, LocalDate fechaFin, Integer diasInactivo,
            String justificacion, Boolean requiereCuestionario,
            String actividad, String quienConfirma
    ) {}

    public record ReferenciaItem(
            String nombre, String parentesco, String telefono, String tiempoConocimiento,
            Boolean confirmaNoFamiliar, Boolean habloUltimos30Dias
    ) {}

    public record DocumentoItem(
            Long id, String tipoDocumento, String nombreArchivo, LocalDateTime fechaCarga
    ) {}
}
