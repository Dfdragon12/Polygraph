package com.polygraph.erp.modules.formularios.entity;

import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/** Encabezado del formulario diligenciado por el analista para UN subproceso — uno por
 *  servicio_subproceso, sin importar de qué tipo sea (Antecedentes, Académica, lo que venga).
 *  Qué secciones/columnas tiene lo define la plantilla (FormularioSeccion) según el tipo de
 *  progreso del subproceso — esta entidad no sabe nada de esos detalles, solo ancla los datos
 *  reales (FormularioDato) a un subproceso concreto. */
@Entity
@Table(name = "formularios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Formulario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_formulario")
    private Integer idFormulario;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio_subproceso", nullable = false, unique = true)
    private ServicioSubproceso servicioSubproceso;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    /** Secciones OPCIONALES (FormularioSeccion.opcional) que quien diligencia quitó de este
     *  formulario (V212): no se llenan, sus obligatorios no bloquean y no salen en el Word. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "formulario_secciones_omitidas", joinColumns = @JoinColumn(name = "id_formulario"))
    @Column(name = "id_seccion")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Integer> seccionesOmitidas = new HashSet<>();
}
