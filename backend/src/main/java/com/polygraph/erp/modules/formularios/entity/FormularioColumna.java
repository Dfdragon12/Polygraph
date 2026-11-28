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

    /** Solo aplica cuando tipoDato es LISTA — opciones de la lista desplegable, una por línea, en
     *  el orden en que las escribió el administrador (leer/escribir siempre con OpcionesLista).
     *  Null/vacío para cualquier otro tipo de dato. */
    @Column(name = "opciones", columnDefinition = "TEXT")
    private String opciones;

    /** Clave de CampoServicio: dato del estudio (N° de estudio, cliente, cargo...) que se resuelve
     *  en vivo, igual que campoCandidato/campoCliente. Los tres son mutuamente excluyentes. */
    @Column(name = "campo_servicio", length = 50)
    private String campoServicio;

    /** El valor lo calcula el código del subservicio (fórmula, puntaje, semáforo, notas); quien
     *  diligencia solo lo ve. Nunca se guarda como FormularioDato. */
    @Builder.Default
    @Column(name = "calculado", nullable = false)
    private Boolean calculado = false;

    /** Lo que vale el campo mientras nadie lo cambie (ej. "—" en las columnas Ref.). */
    @Column(name = "valor_defecto", length = 200)
    private String valorDefecto;

    /** No se puede completar el subproceso con este campo vacío. */
    @Builder.Default
    @Column(name = "obligatorio", nullable = false)
    private Boolean obligatorio = false;

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

    /** Para qué sirve el campo en la calificación por hallazgos (V205): null = campo normal;
     *  HALLAZGO = quien diligencia elige un hallazgo del catálogo (las opciones son los códigos que
     *  escogió el admin); CRITICIDAD = criticidad del cargo; CONDICION = "Sí" habilita los
     *  hallazgos "solo con condición" (conducción). Ver riesgo/hallazgos/MotorHallazgos. */
    @Column(name = "uso_calificacion", length = 20)
    private String usoCalificacion;

    /** Solo columnas calculadas: qué resultado muestran (PUNTAJE, FORMULA, NIVEL, SEMAFORO_*,
     *  NUMERO_FILA, NOTA y, en la tabla de hallazgos, REF, DESCRIPCION, FUENTE, DETALLE, PUNTOS). */
    @Column(name = "calculo", length = 20)
    private String calculo;

    /** NOTA: clave del campo o tabla que se mira; el texto sale si no tiene valor. */
    @Column(name = "referencia", length = 50)
    private String referencia;

    @Column(name = "texto_nota", length = 500)
    private String textoNota;

    /** Cómo se escribe el hallazgo en la tabla de hallazgos ({clave} = valor de ese campo). Vacío:
     *  fuente = nombre de la sección, detalle = lo diligenciado en la fila o el campo. */
    @Column(name = "fuente_hallazgo", length = 300)
    private String fuenteHallazgo;

    @Column(name = "detalle_hallazgo", length = 300)
    private String detalleHallazgo;

    /** Hallazgo que genera cada opción de una lista normal (ej. "No encontrada" → LEG-01).
     *  Mutar esta MISMA colección (clear/add), nunca reemplazarla: orphanRemoval=true. */
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "columna", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<FormularioColumnaHallazgo> hallazgos = new java.util.ArrayList<>();

    /** Máximo de caracteres de un campo de texto (V209); null = LONGITUD_TEXTO_DEFECTO. */
    @Column(name = "longitud_maxima")
    private Integer longitudMaxima;

    public static final int LONGITUD_TEXTO_DEFECTO = 2000;

    /** El máximo que se aplica: el configurado o, en texto, el de por defecto. null = sin límite
     *  (fechas, números, listas…). */
    public Integer longitudMaximaEfectiva() {
        if (tipoDato != com.polygraph.erp.shared.enums.TipoDatoFormulario.TEXTO) return null;
        return longitudMaxima != null ? longitudMaxima : LONGITUD_TEXTO_DEFECTO;
    }

    /** Nadie la diligencia: dato del candidato, del cliente, del estudio, o calculado por el
     *  código. Su valor se resuelve al mostrarla y nunca se guarda en formulario_datos. */
    public boolean esAutomatica() {
        return campoCandidato != null || campoCliente != null || campoServicio != null || Boolean.TRUE.equals(calculado);
    }
}
