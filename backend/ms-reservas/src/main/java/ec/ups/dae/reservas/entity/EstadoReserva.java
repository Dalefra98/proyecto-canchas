package ec.ups.dae.reservas.entity;

/**
 * Valores identicos al CHECK ck_reserva_estado de la tabla reserva y al contrato congelado
 * (RN-08).
 *
 * El servicio solo escribe CONFIRMADA y CANCELADA: FINALIZADA se calcula al leer y nunca se
 * persiste (design D-02). Se declara igual para que el enum cubra el dominio completo de la
 * columna y una fila con ese valor no reviente el mapeo (design D-04).
 */
public enum EstadoReserva {
    CONFIRMADA,
    CANCELADA,
    FINALIZADA
}
