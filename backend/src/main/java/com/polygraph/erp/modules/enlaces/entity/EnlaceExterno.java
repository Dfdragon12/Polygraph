package com.polygraph.erp.modules.enlaces.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.shared.enums.CategoriaEnlace;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "enlaces_externos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnlaceExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_enlace")
    private Integer idEnlace;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 50)
    private CategoriaEnlace categoria;

    @Column(name = "nombre_entidad", nullable = false, length = 200)
    private String nombreEntidad;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "correo_contacto", length = 150)
    private String correoContacto;

    @Column(name = "telefono_contacto", length = 30)
    private String telefonoContacto;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "requiere_login")
    private Boolean requiereLogin;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "activo", nullable = false)
    private Boolean activo;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id")
    private Usuario creadoPor;
}
