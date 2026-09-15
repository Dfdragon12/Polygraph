package com.polygraph.erp.modules.catalogo.entity;

import com.polygraph.erp.shared.enums.Rol;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tipos_progreso")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipoProgreso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tipo_progreso")
    private Integer idTipoProgreso;

    @Column(name = "nombre_progreso", nullable = false, length = 100)
    private String nombreProgreso;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "orden")
    private Integer orden;

    @Column(name = "activo")
    private Boolean activo;

    @Column(name = "valor", precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "minutos_estimados")
    private Integer minutosEstimados;

    /** Qué rol interno ejecuta este subproceso — ANALISTA_INTERNO, POLIGRAFISTA o VISITADOR. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rol_responsable", nullable = false, length = 30)
    private Rol rolResponsable;

    /** Si este subproceso necesita un documento/verificación estructurada (como Antecedentes) —
     *  controla si admin/catálogo → Subprocesos le muestra el botón de plantilla. No implica que
     *  el constructor de documento ya exista: hoy solo "Antecedentes" lo tiene. */
    @Column(name = "requiere_documento", nullable = false)
    private Boolean requiereDocumento;

    /** Contador simple: sube en 1 cada vez que admin/catálogo → Plantilla guarda cambios de
     *  secciones/columnas (ver FormularioPlantillaService.guardar()) — cada ServicioSubproceso
     *  guarda en su propio versionPlantilla el valor vigente al momento de crearse, para poder
     *  comparar "con qué versión se creó" contra "cuál es la versión actual". */
    @Column(name = "version_plantilla", nullable = false)
    @Builder.Default
    private Integer versionPlantilla = 1;
}
