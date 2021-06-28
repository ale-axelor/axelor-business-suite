package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.exception.AxelorException;
import java.math.BigDecimal;

public interface StockMoveLineUpdateReservedQtyService {

  /**
   * Update requested quantity in stock move line. If the requested quantity become lower than the
   * allocated quantity, we also change the allocated quantity to match the requested quantity.
   *
   * @param stockMoveLine
   * @param newReservedQty
   */
  void updateRequestedReservedQty(StockMoveLine stockMoveLine, BigDecimal newReservedQty)
      throws AxelorException;

  /**
   * StockMoveLine cannot be null and quantity cannot be negative. Throws {@link AxelorException} if
   * these conditions are false.
   */
  void checkBeforeUpdatingQties(StockMoveLine stockMoveLine, BigDecimal qty) throws AxelorException;

  /**
   * If the stock move is planned and with an availability request, we cannot lower its quantity.
   *
   * @param stockMoveLine a stock move line.
   * @param qty the quantity that can be requested or reserved.
   * @param isRequested whether the quantity is requested or reserved.
   * @throws AxelorException if we try to change the quantity of a stock move with availability
   *     request equals to true.
   */
  void checkAvailabilityRequest(StockMoveLine stockMoveLine, BigDecimal qty, boolean isRequested)
      throws AxelorException;
}
