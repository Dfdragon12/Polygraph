package com.polygraph.erp.shared.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un festivo (o día no laborable) del calendario hábil. AUTO: lo generó la plataforma con las reglas de la ley (se puede desactivar).
 *  MANUAL: lo agregó el administrador (por ejemplo un cierre de la empresa o un día cívico). */
@Entity
@Table(name = "festivo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Festivo {

    public static final String AUTO = "AUTO";
    public static final String MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "fecha", nullable = false, unique = true)
    private LocalDate fecha;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    @Column(name = "origen", nullable = false, length = 6)
    private String origen;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "creado_por", length = 150)
    private String creadoPor;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "modificado_por", length = 150)
    private String modificadoPor;

    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    @PrePersist
    void alCrear() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }
}
