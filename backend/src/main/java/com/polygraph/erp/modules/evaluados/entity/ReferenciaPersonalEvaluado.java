package com.polygraph.erp.modules.evaluados.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "referencia_personal_evaluado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "hojaVidaEvaluado")
public class ReferenciaPersonalEvaluado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_hoja_vida", nullable = false)
    private HojaVidaEvaluado hojaVidaEvaluado;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "parentesco", length = 50)
    private String parentesco;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "tiempo_conocimiento", length = 100)
    private String tiempoConocimiento;
}
