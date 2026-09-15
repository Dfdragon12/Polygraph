package com.polygraph.erp.modules.formularios.entity;

import com.polygraph.erp.modules.servicios.entity.ServicioSubproceso;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
}
