package com.polygraph.erp.modules.clientes.entity;

import com.polygraph.erp.shared.enums.EstadoMora;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "clientes_pospago")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "cliente")
public class ClientePospago {

    @Id
    @Column(name = "id_cliente")
    private Integer idCliente;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @Column(name = "limite_credito", precision = 15, scale = 2)
    private BigDecimal limiteCredito;

    @Column(name = "credito_disponible", precision = 15, scale = 2)
    private BigDecimal creditoDisponible;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_mora", length = 20)
    private EstadoMora estadoMora;

    @Column(name = "dias_mora")
    private Integer diasMora;

    @Column(name = "fecha_ultima_factura")
    private LocalDate fechaUltimaFactura;

    @Column(name = "ultima_revision_credito")
    private LocalDate ultimaRevisionCredito;

    @Column(name = "requiere_aprobacion")
    private Boolean requiereAprobacion;
}
