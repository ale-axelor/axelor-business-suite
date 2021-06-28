package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMoveLine;
import java.util.List;

/** Handles database interaction with services implementing stock reservation feature */
public interface ReservedQtyFetchService {

  List<StockMoveLine> fetchStockMoveLineListToAllocate(
      StockLocation stockLocation, Product product);

  StockMoveLine fetchPlannedStockMoveLine(SaleOrderLine saleOrderLine);

  List<StockMoveLine> fetchRelatedPlannedStockMoveLineList(SaleOrderLine saleOrderLine);

  List<StockMoveLine> fetchRelatedPlannedStockMoveLineList(StockLocationLine stockLocationLine);

  List<StockMoveLine> fetchRelatedPlannedStockMoveLineListNoAvailabilityRequest(
      StockLocationLine stockLocationLine);
}
