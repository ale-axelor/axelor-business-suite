package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;

public interface StockMoveLineReservationService {

  /**
   * Request quantity for a stock move line.
   *
   * @param stockMoveLine
   * @throws AxelorException
   */
  void requestQty(StockMoveLine stockMoveLine) throws AxelorException;

  /**
   * Cancel the reservation for a stock move line.
   *
   * @param stockMoveLine
   * @throws AxelorException
   */
  void cancelReservation(StockMoveLine stockMoveLine) throws AxelorException;

  /**
   * Create a reservation and allocate as much quantity as we can.
   *
   * @param stockMoveLine
   */
  void allocate(StockMoveLine stockMoveLine) throws AxelorException;

  /**
   * Update allocated quantity in stock move line with a new quantity, updating location. If the
   * allocated quantity become bigger than the requested quantity, we also change the requested
   * quantity to match the allocated quantity.
   *
   * @param stockMoveLine
   * @param newReservedQty
   * @throws AxelorException
   */
  void updateReservedQty(StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException;

  /**
   * Deallocate reserved quantity, but keep the quantity requested.
   *
   * @param stockMoveLine
   * @throws AxelorException
   */
  void deallocate(StockMoveLine stockMoveLine) throws AxelorException;

  /**
   * Update reserved quantity in stock move lines and sale order lines from stock move lines.
   *
   * @param stockMoveLine
   * @param product
   * @param reservedQtyToAdd
   */
  void updateReservedQuantityFromStockMoveLine(
      StockMoveLine stockMoveLine, Product product, BigDecimal reservedQtyToAdd)
      throws AxelorException;
}
