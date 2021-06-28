package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.exception.AxelorException;

public interface StockLocationLineUpdateReservedQtyService {

  /**
   * Update requested reserved qty for stock location line from already updated stock moves.
   *
   * @param stockLocationLine
   * @throws AxelorException
   */
  void updateRequestedReservedQty(StockLocationLine stockLocationLine) throws AxelorException;

  /**
   * Update reserved qty for stock location line from already updated stock moves.
   *
   * @param stockLocationLine
   * @throws AxelorException
   */
  void updateReservedQty(StockLocationLine stockLocationLine) throws AxelorException;
}
