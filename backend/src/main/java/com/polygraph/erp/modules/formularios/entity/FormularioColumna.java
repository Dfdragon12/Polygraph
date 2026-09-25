package com.polygraph.erp.modules.formularios.entity;

import com.polygraph.erp.shared.enums.TipoDatoFormulario;
import jakarta.persistence.*;
import lombok.*;

/** Plantilla: un campo dentro de una sección (ej. "Multas", "Hallazgo") — el administrador
 *  define libremente el nombre y el tipo de dato, sin necesidad de tocar código. */
@Entity
@Table(name = "formulario_columnas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioColumna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_columna")
    private Integer idColumna;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_seccion", nullable = false)
    private FormularioSeccion seccion;

    /** Título visible en el documento — opcional: si queda vacío, el valor se muestra solo, sin
     *  etiqueta (útil en secciones tipo párrafo, donde no todo necesita un rótulo). */
    @Column(name = "nombre", length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dato", nullable = false, length = 20)
    private TipoDatoFormulario tipoDato;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    /** Igual que en FormularioSeccion: un campo con respuestas guardadas se archiva en vez de
     *  borrarse cuando el administrador lo quita de la plantilla. */
    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /** Clave de CampoCandidato cuando esta columna muestra un dato del candidato (nombres,
     *  cédula, celular, etc.) en vez de un valor que escribe el analista — se resuelve en vivo
     *  desde la tabla de candidatos, nunca se guarda como FormularioDato. Null = columna manual
     *  normal, el comportamiento de siempre. */
    @Column(name = "campo_candidato", length = 50)
    private String campoCandidato;

    /** Clave de CampoCliente cuando esta columna muestra un dato del cliente que solicitó el
     *  servicio (NIT, nombre de la empresa, teléfono) en vez de un valor que escribe el analista
     *  — se resuelve en vivo desde la tabla de clientes, nunca se guarda como FormularioDato.
     *  Null = columna manual normal, o de candidato (ver campoCandidato). Mutuamente excluyente
     *  con campoCandidato — el admin elige uno de los dos, o ninguno, desde un solo selector. */
    @Column(name = "campo_cliente", length = 50)
    private String campoCliente;

    /** Clave de texto estable para referenciar esta columna desde un marcador de plantilla Word
     *  (ej. "Observación" → observacion → ${campos['observacion']}) — se genera siempre al
     *  crear/editar la columna. */
    @Column(name = "clave", length = 50)
    private String clave;

    /** Solo aplica cuando tipoDato es LISTA — opciones de la lista desplegable separadas por
     *  coma, en el orden en que las escribió el administrador. Null/vacío para cualquier otro
     *  tipo de dato. */
    @Column(name = "opciones", length = 1000)
    private String opciones;

    /** El campo cuenta para la calificación de riesgo (solo Lista o Sí/No) — los puntos de cada
     *  respuesta están en puntajes. Ver docs/modelo-calificacion-riesgo.md §3. */
    @Builder.Default
    @Column(name = "califica_riesgo", nullable = false)
    private Boolean calificaRiesgo = false;

    /** Mutar esta MISMA colección (clear/add), nunca reemplazarla: orphanRemoval=true. */
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "columna", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<FormularioColumnaPuntaje> puntajes = new java.util.ArrayList<>();
}
