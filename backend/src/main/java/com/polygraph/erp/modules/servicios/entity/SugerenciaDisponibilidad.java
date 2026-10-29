package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.EstadoSugerencia;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** El candidato le dice al programador cuándo tiene tiempo para una cita (poligrafía/visita):
 *  1 a 3 días con franja + una nota. Solo una PENDIENTE por subproceso. Ver V197. */
@Entity
@jakarta.persistence.EntityListeners(com.polygraph.erp.modules.tiemporeal.CambiosEntityListener.class)
@Table(name = "sugerencias_disponibilidad")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"servicioSubproceso", "atendidaPor", "opciones"})
public class SugerenciaDisponibilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_servicio_subproceso", nullable = false)
    private ServicioSubproceso servicioSubproceso;

    @Column(name = "comentario", length = 500)
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoSugerencia estado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_atendio")
    private Usuario atendidaPor;

    @Column(name = "fecha_atendida")
    private LocalDateTime fechaAtendida;

    @OneToMany(mappedBy = "sugerencia", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha ASC")
    @Builder.Default
    private List<SugerenciaDisponibilidadOpcion> opciones = new ArrayList<>();
}
