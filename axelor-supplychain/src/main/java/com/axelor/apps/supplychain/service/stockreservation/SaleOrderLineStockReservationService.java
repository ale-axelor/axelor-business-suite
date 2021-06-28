package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;

public interface SaleOrderLineStockReservationService {

  /**
   * Request quantity for a sale order line.
   *
   * @param saleOrderLine
   * @throws AxelorException
   */
  void requestQty(SaleOrderLine saleOrderLine) throws AxelorException;

  /**
   * Cancel the reservation for a sale order line.
   *
   * @param saleOrderLine
   * @throws AxelorException
   */
  void cancelReservation(SaleOrderLine saleOrderLine) throws AxelorException;

  /**
   * Create a reservation and allocate as much quantity as we can.
   *
   * @param saleOrderLine
   */
  void allocate(SaleOrderLine saleOrderLine) throws AxelorException;

  /**
   * Update allocated quantity in sale order line with a new quantity, updating location and moves.
   * If the allocated quantity become bigger than the requested quantity, we also change the
   * requested quantity to match the allocated quantity.
   *
   * @param saleOrderLine
   * @param newReservedQty
   * @throws AxelorException if there is no stock move generated or if we cannot allocate more
   *     quantity.
   */
  void updateReservedQty(SaleOrderLine saleOrderLine, BigDecimal newReservedQty)
      throws AxelorException;

  /**
   * Deallocate reserved quantity, but keep the quantity requested.
   *
   * @param saleOrderLine
   * @throws AxelorException
   */
  void deallocate(SaleOrderLine saleOrderLine) throws AxelorException;

  /**
   * Update requested quantity in sale order line. If the requested quantity become lower than the
   * allocated quantity, we also change the allocated quantity to match the requested quantity.
   *
   * @param saleOrderLine
   * @param newReservedQty
   * @throws AxelorException
   */
  void updateRequestedReservedQty(SaleOrderLine saleOrderLine, BigDecimal newReservedQty)
      throws AxelorException;
}
