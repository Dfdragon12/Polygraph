package com.polygraph.erp.modules.pagos.entity;

import com.polygraph.erp.modules.auth.entity.Usuario;
import com.polygraph.erp.modules.clientes.entity.Cliente;
import com.polygraph.erp.shared.enums.EstadoOrdenCompra;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Orden de compra de crédito prepago (bolsa de servicios). Se paga vía Wompi o el simulador interno. */
@Entity
@Table(name = "ordenes_compra")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrdenCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_orden_compra")
    private Long idOrdenCompra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(name = "referencia", nullable = false, unique = true, length = 50)
    private String referencia;

    @Column(name = "monto_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoOrdenCompra estado;

    @Column(name = "modo_simulado", nullable = false)
    private Boolean modoSimulado;

    @Column(name = "id_transaccion_pasarela", length = 100)
    private String idTransaccionPasarela;

    @Column(name = "metodo_pago", length = 50)
    private String metodoPago;

    @Column(name = "url_checkout", columnDefinition = "TEXT")
    private String urlCheckout;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_expiracion")
    private LocalDateTime fechaExpiracion;
}
