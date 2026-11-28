package com.polygraph.erp.modules.solicitudes.formato;

import java.util.List;

/** Información extra que el cliente diligencia al solicitar una poligrafía. Cada formato reproduce el
 *  documento "IPSERVICE – Solicitud …" que antes se mandaba por correo; lo que ya se pide en
 *  "Datos del evaluado" (nombre, cédula, teléfono, cargo, correo) NO se repite aquí.
 *  Los campos viven en el código: el servidor los valida y el cliente los dibuja desde
 *  GET /api/v1/solicitudes/formatos, así hay una sola definición. */
public enum FormatoSolicitud {

    PRE_EMPLEO("Solicitud de prueba de pre-empleo", List.of(
            Campo.texto("empresa_solicitante", "Empresa solicitante", true, null),
            Campo.areaTexto("observaciones", "Observaciones para tener en cuenta", false, null))),

    RUTINA("Solicitud de prueba de poligrafía de rutina", List.of(
            Campo.texto("empresa", "Empresa", true, null),
            Campo.fecha("fecha_prueba", "Fecha de la presentación de la prueba", true, null),
            Campo.fecha("fecha_ingreso", "Fecha de ingreso del candidato", false, null),
            Campo.areaTexto("cargos_ocupados", "Cargos ocupados en la empresa", false, null),
            Campo.areaTexto("funciones", "Funciones", false, null),
            Campo.texto("tipo_contrato", "Tipo de contrato", false, null),
            Campo.texto("jefe_inmediato", "Jefe inmediato", false, null),
            Campo.areaTexto("descripcion_cargo", "Descripción del cargo", false, null),
            Campo.areaTexto("actividades_prohibidas", "Actividades prohibidas por la empresa", false, null),
            Campo.areaTexto("objetivo", "¿Cuál es el objetivo de la poligrafía?", true, null),
            Campo.areaTexto("preguntas_sugeridas", "Preguntas sugeridas para el caso", false, null),
            Campo.texto("solicitante_nombre", "Nombre de quien solicita la prueba", true, null),
            Campo.texto("solicitante_cargo", "Cargo de quien solicita la prueba", true, null))),

    ESPECIFICA("Solicitud de prueba de poligrafía específica", List.of(
            Campo.texto("empresa_cliente", "Empresa cliente", true, null),
            Campo.numero("cantidad_poligrafias", "Cantidad de poligrafías solicitadas", true, null),
            Campo.correo("correo_informes", "Correo autorizado para el envío de informes", true, null),
            Campo.fecha("fecha_hechos", "Fecha de los hechos", true, null),
            Campo.texto("lugar_hechos", "Lugar de los hechos", true, null),
            Campo.areaTexto("objetivo", "¿Cuál es el objetivo de la poligrafía?", true, null),
            Campo.areaTexto("descripcion_hechos", "Descripción de los hechos", true, null),
            Campo.areaTexto("posibles_involucrados", "¿Quiénes son los posibles involucrados?", false, null),
            Campo.areaTexto("elementos_extraviados", "¿Cuáles son los elementos extraviados? ¿En cuánto se cuantifica la pérdida?", false, null),
            Campo.areaTexto("anexos", "Anexos (informes de descargos, audios y videos)", false,
                    "Describe los anexos que tienes; el equipo de Polygraph te indicará cómo hacerlos llegar."),
            Campo.areaTexto("preguntas_sugeridas", "Preguntas sugeridas para el caso", false, null),
            Campo.texto("solicitante_nombre", "Nombre de quien solicita la prueba", true, null),
            Campo.texto("solicitante_cargo", "Cargo de quien solicita la prueba", true, null)));

    public enum Tipo { TEXTO, AREA_TEXTO, FECHA, NUMERO, CORREO }

    public record Campo(String clave, String etiqueta, Tipo tipo, boolean obligatorio, String ayuda) {
        static Campo texto(String clave, String etiqueta, boolean obligatorio, String ayuda) {
            return new Campo(clave, etiqueta, Tipo.TEXTO, obligatorio, ayuda);
        }
        static Campo areaTexto(String clave, String etiqueta, boolean obligatorio, String ayuda) {
            return new Campo(clave, etiqueta, Tipo.AREA_TEXTO, obligatorio, ayuda);
        }
        static Campo fecha(String clave, String etiqueta, boolean obligatorio, String ayuda) {
            return new Campo(clave, etiqueta, Tipo.FECHA, obligatorio, ayuda);
        }
        static Campo numero(String clave, String etiqueta, boolean obligatorio, String ayuda) {
            return new Campo(clave, etiqueta, Tipo.NUMERO, obligatorio, ayuda);
        }
        static Campo correo(String clave, String etiqueta, boolean obligatorio, String ayuda) {
            return new Campo(clave, etiqueta, Tipo.CORREO, obligatorio, ayuda);
        }
    }

    private final String titulo;
    private final List<Campo> campos;

    FormatoSolicitud(String titulo, List<Campo> campos) {
        this.titulo = titulo;
        this.campos = campos;
    }

    public String titulo() { return titulo; }

    public List<Campo> campos() { return campos; }
}
