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

        List<EducacionItem> educacion,
        List<ExperienciaItem> experienciaLaboral,
        List<InactividadItem> inactividades,
        List<ReferenciaItem> referencias,
        List<DocumentoItem> documentos
) {
    public record EducacionItem(
            String nivel, String institucion, String titulo,
            LocalDate fechaInicio, LocalDate fechaFin, Boolean enCurso, String ciudad
    ) {}

    public record ExperienciaItem(
            String empresa, String cargo, LocalDate fechaInicio, LocalDate fechaFin,
            Boolean laboraActualmente, String ciudad, String telefonoEmpresa,
            String motivoRetiro, String nombreJefe, String cargoJefe
    ) {}

    public record InactividadItem(
            LocalDate fechaInicio, LocalDate fechaFin, Integer diasInactivo,
            String justificacion, Boolean requiereCuestionario
    ) {}

    public record ReferenciaItem(
            String nombre, String parentesco, String telefono, String tiempoConocimiento
    ) {}

    public record DocumentoItem(
            Long id, String tipoDocumento, String nombreArchivo, LocalDateTime fechaCarga
    ) {}
}
