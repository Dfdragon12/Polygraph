package com.polygraph.erp.modules.clientes.entity;

import com.polygraph.erp.shared.enums.TipoCliente;
import com.polygraph.erp.shared.enums.TipoPersona;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer idCliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cliente", nullable = false, length = 10)
    private TipoCliente tipoCliente;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_persona", nullable = false, length = 10)
    private TipoPersona tipoPersona;

    @Column(name = "nit", unique = true, length = 20)
    private String nit;

    @Column(name = "dv", length = 1)
    private String dv;

    @Column(name = "razon_social", length = 200)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 200)
    private String nombreComercial;

    @Column(name = "representante_legal", length = 150)
    private String representanteLegal;

    @Column(name = "nombre", length = 100)
    private String nombre;

    @Column(name = "apellido", length = 100)
    private String apellido;

    @Column(name = "email_principal", nullable = false, unique = true, length = 150)
    private String emailPrincipal;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "id_ciudad")
    private Integer idCiudad;

    @Column(name = "direccion", columnDefinition = "TEXT")
    private String direccion;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;

    @Column(name = "estado", length = 20)
    private String estado;

    @Column(name = "id_gestor")
    private Long idGestor;
}
