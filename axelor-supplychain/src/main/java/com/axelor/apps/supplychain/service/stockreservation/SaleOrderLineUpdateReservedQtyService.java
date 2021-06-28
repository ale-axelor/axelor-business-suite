package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;

public interface SaleOrderLineUpdateReservedQtyService {

  /**
   * Update reserved qty for sale order line from already updated stock move.
   *
   * @param saleOrderLine
   * @throws AxelorException
   */
  void updateReservedQty(SaleOrderLine saleOrderLine) throws AxelorException;

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
