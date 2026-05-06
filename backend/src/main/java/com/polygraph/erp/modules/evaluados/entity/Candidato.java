package com.polygraph.erp.modules.evaluados.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "candidatos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candidato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_candidato")
    private Long idCandidato;

    @Column(name = "cedula", unique = true, nullable = false, length = 20)
    private String cedula;

    @Column(name = "tipo_documento", nullable = false, length = 20)
    private String tipoDocumento;

    @Column(name = "nombres", nullable = false, length = 200)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 200)
    private String apellidos;

    @Column(name = "lugar_nacimiento", length = 200)
    private String lugarNacimiento;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(name = "fecha_expedicion")
    private LocalDate fechaExpedicion;

    @Column(name = "id_ciudad_nacimiento")
    private Integer idCiudadNacimiento;

    @Column(name = "id_ciudad_residencia")
    private Integer idCiudadResidencia;

    @Column(name = "direccion_residencia", columnDefinition = "TEXT")
    private String direccionResidencia;

    @Column(name = "barrio_residencia", length = 100)
    private String barrioResidencia;

    @Column(name = "telefono_fijo", length = 20)
    private String telefonoFijo;

    @Column(name = "celular", length = 20)
    private String celular;

    @Column(name = "email_principal", length = 150)
    private String emailPrincipal;

    @Column(name = "sector", length = 50)
    private String sector;

    @Column(name = "estrato", length = 1)
    private String estrato;

    @Column(name = "libreta_militar", length = 20)
    private String libretaMilitar;

    @Column(name = "rut", length = 20)
    private String rut;
}
