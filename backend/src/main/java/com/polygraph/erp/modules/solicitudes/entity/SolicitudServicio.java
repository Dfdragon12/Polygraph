package com.polygraph.erp.modules.solicitudes.entity;

import com.polygraph.erp.modules.servicios.entity.CatalogoServicio;
import com.polygraph.erp.shared.enums.EstadoServicio;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "solicitud_servicios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "solicitud")
public class SolicitudServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud", nullable = false)
    private Solicitud solicitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_catalogo", nullable = false)
    private CatalogoServicio catalogoServicio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 40)
    private EstadoServicio estado;
}
