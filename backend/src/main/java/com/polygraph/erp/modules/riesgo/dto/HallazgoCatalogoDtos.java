package com.polygraph.erp.modules.riesgo.dto;

import com.polygraph.erp.modules.riesgo.entity.CodigoCalificacion;
import jakarta.validation.constraints.*;

/** Catálogo de hallazgos (Admin → Calificación de riesgo → Catálogo de hallazgos, V205). tipo:
 *  PUNTOS (suma sus puntos), CRITICO (nivel ALTO directo) o ALERTA (informativa, 0). acumula: suma
 *  por cada fila (proceso) en vez de una vez. soloCondicion: solo suma si el campo CONDICION del
 *  formulario dice Sí (cargos de conducción). A quién le sale lo define la plantilla (V210). */
public final class HallazgoCatalogoDtos {

    private HallazgoCatalogoDtos() {}

    public record HallazgoRequest(
            @NotBlank(message = "Escribe el código.")
            @Pattern(regexp = "[A-Za-z]{2,5}-\\d{1,3}", message = "El código va como LEG-01: letras, guion y número.") String codigo,
            @NotBlank(message = "Escribe el hallazgo.") @Size(max = 500) String descripcion,
            @NotBlank @Pattern(regexp = "PUNTOS|CRITICO|ALERTA", message = "Tipo no válido.") String tipo,
            @Min(value = 0, message = "Los puntos van de 0 a 100.") @Max(value = 100, message = "Los puntos van de 0 a 100.") Integer puntos,
            boolean acumula,
            boolean soloCondicion,
            Boolean activo) {}

    /** enUso: algún formulario lo ofrece o lo genera — no se puede borrar, solo desactivar. */
    public record HallazgoResponse(Integer id, String codigo, String descripcion, String tipo, Integer puntos, boolean acumula,
                                   boolean soloCondicion, boolean activo, Integer orden, boolean enUso) {

        public static HallazgoResponse de(CodigoCalificacion c, boolean enUso) {
            String tipo = Boolean.TRUE.equals(c.getCritico()) ? "CRITICO" : Boolean.TRUE.equals(c.getEsAlerta()) ? "ALERTA" : "PUNTOS";
            return new HallazgoResponse(c.getId(), c.getCodigo(), c.getDescripcion(), tipo, c.getPuntos(),
                    Boolean.TRUE.equals(c.getPorProceso()), Boolean.TRUE.equals(c.getSoloConduccion()),
                    Boolean.TRUE.equals(c.getActivo()), c.getOrden(), enUso);
        }
    }
}
