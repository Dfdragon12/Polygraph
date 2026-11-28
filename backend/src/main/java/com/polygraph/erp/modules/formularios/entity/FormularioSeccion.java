package com.polygraph.erp.modules.formularios.entity;

import com.polygraph.erp.modules.catalogo.entity.TipoProgreso;
import com.polygraph.erp.shared.enums.TipoSeccionFormulario;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/** Plantilla: una sección del documento de un tipo de subproceso (ej. "SIMIT", "Fuentes
 *  consultadas"), administrable desde admin/catálogo → Plantilla. Universal: cualquier
 *  tipos_progreso puede tener sus propias secciones — no hay nada específico de Antecedentes
 *  en código, solo lo que el administrador configuró para ese id_tipo_progreso. */
@Entity
@Table(name = "formulario_secciones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioSeccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_seccion")
    private Integer idSeccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_progreso", nullable = false)
    private TipoProgreso tipoProgreso;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoSeccionFormulario tipo;

    @Column(name = "orden", nullable = false)
    private Integer orden;

    /** Si el administrador quita una sección que ya tiene respuestas guardadas, se archiva
     *  (activo = false) en vez de borrarse — deja de aparecer para formularios nuevos, pero los
     *  datos ya guardados se conservan (se siguen viendo donde ya se habían diligenciado, y en
     *  el documento generado). Una sección sin datos sí se borra de verdad. */
    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    /** Clave de texto estable para referenciar esta sección (tipo TABLA) desde un
     *  repeatTableRow en una plantilla Word — ${tablas['clave']}. Cuando origenFijo no es null,
     *  clave siempre es igual a origenFijo (no editable a mano). */
    @Column(name = "clave", length = 50)
    private String clave;

    /** Solo aplica a secciones tipo TABLA. Null = origen manual (el analista llena las filas a
     *  mano, comportamiento de siempre). No null = origen automático — una de las claves de
     *  CandidatoInfoService.CLAVES_TABLAS_FIJAS ("referencias", "educacion",
     *  "experiencia_laboral", "inactividades") — la tabla se llena sola con la hoja de vida del
     *  candidato, sin columnas propias y sin aparecer en el formulario que diligencia el
     *  analista. */
    @Column(name = "origen_fijo", length = 30)
    private String origenFijo;

    /** Solo aplica cuando origenFijo no es null. Claves de columnas fijas separadas por coma que
     *  el admin decidió OCULTAR del formulario que diligencia el analista (ej.
     *  "motivo_retiro,cargo_jefe") — null/vacío = se muestran todas. No afecta los marcadores
     *  disponibles en el .docx: esas columnas siguen resolviéndose igual, esto solo controla qué
     *  se ve en la tabla de solo lectura. */
    @Column(name = "columnas_ocultas", length = 255)
    private String columnasOcultas;

    /** Versión de TipoProgreso.versionPlantilla vigente cuando se creó esta sección (ver
     *  FormularioPlantillaService.guardar()) — un ServicioSubproceso solo muestra secciones cuyo
     *  versionCreacion sea menor o igual a su propio versionPlantilla, así una sección agregada
     *  después de que el subproceso ya existía nunca aparece ahí, aunque después le hayan
     *  guardado datos por accidente. */
    @Builder.Default
    @Column(name = "version_creacion", nullable = false)
    private Integer versionCreacion = 1;

    /** Solo secciones TABLA: las filas las arma el código del subservicio (ej. la tabla de
     *  hallazgos de Antecedentes), no el analista — no puede agregar, quitar ni editar filas. */
    @Builder.Default
    @Column(name = "calculada", nullable = false)
    private Boolean calculada = false;

    /** Quien diligencia puede quitarla de SU formulario (V212): no la llena, sus obligatorios no
     *  bloquean y no sale en el Word si el .docx la encierra en repeatDocPart(secciones['clave']).
     *  false = obligatoria, siempre va. Lo decide el administrador en el constructor. */
    @Builder.Default
    @org.hibernate.annotations.ColumnDefault("false") // los INSERT de las migraciones no la mandan
    @Column(name = "opcional", nullable = false)
    private Boolean opcional = false;

    /** Grupo con el que se clasifica la sección (V217), ej. "Datos del evaluado", "Resultado", "Verificación".
     *  Solo organiza el índice del formulario y del constructor: nada más depende de él. Null = sin grupo. */
    @Column(name = "grupo", length = 60)
    private String grupo;

    @OneToMany(mappedBy = "seccion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<FormularioColumna> columnas = new ArrayList<>();

    @OneToMany(mappedBy = "seccion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<FormularioFilaSugerida> filasSugeridas = new ArrayList<>();
}
