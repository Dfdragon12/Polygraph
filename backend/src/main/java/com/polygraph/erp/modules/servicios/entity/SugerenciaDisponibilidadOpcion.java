package com.polygraph.erp.modules.servicios.entity;

import com.polygraph.erp.shared.enums.FranjaHoraria;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Un día + franja dentro de una SugerenciaDisponibilidad. */
@Entity
@Table(name = "sugerencia_disponibilidad_opciones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "sugerencia")
public class SugerenciaDisponibilidadOpcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sugerencia", nullable = false)
    private SugerenciaDisponibilidad sugerencia;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "franja", nullable = false, length = 10)
    private FranjaHoraria franja;
}
