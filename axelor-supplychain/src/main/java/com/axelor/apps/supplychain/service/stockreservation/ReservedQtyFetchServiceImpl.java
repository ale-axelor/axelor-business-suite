package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.stock.db.StockLocation;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.google.inject.Inject;
import java.util.List;

public class ReservedQtyFetchServiceImpl implements ReservedQtyFetchService {

  protected StockMoveLineRepository stockMoveLineRepository;

  @Inject
  public ReservedQtyFetchServiceImpl(StockMoveLineRepository stockMoveLineRepository) {
    this.stockMoveLineRepository = stockMoveLineRepository;
  }

  @Override
  public List<StockMoveLine> fetchStockMoveLineListToAllocate(
      StockLocation stockLocation, Product product) {
    return stockMoveLineRepository
        .all()
        .filter(
            "self.stockMove.fromStockLocation.id = :stockLocationId "
                + "AND self.product.id = :productId "
                + "AND self.stockMove.statusSelect = :planned "
                + "AND self.reservationDateTime IS NOT NULL "
                + "AND self.reservedQty < self.requestedReservedQty")
        .bind("stockLocationId", stockLocation.getId())
        .bind("productId", product.getId())
        .bind("planned", StockMoveRepository.STATUS_PLANNED)
        .order("reservationDateTime")
        .order("stockMove.estimatedDate")
        .fetch();
  }

  @Override
  public StockMoveLine fetchPlannedStockMoveLine(SaleOrderLine saleOrderLine) {
    return stockMoveLineRepository
        .all()
        .filter(
            "self.saleOrderLine = :saleOrderLine " + "AND self.stockMove.statusSelect = :planned")
        .bind("saleOrderLine", saleOrderLine)
        .bind("planned", StockMoveRepository.STATUS_PLANNED)
        .fetchOne();
  }

  @Override
  public List<StockMoveLine> fetchRelatedPlannedStockMoveLineList(SaleOrderLine saleOrderLine) {
    return stockMoveLineRepository
        .all()
        .filter(
            "self.saleOrderLine.id = :saleOrderLineId "
                + "AND self.stockMove.statusSelect = :planned")
        .bind("saleOrderLineId", saleOrderLine.getId())
        .bind("planned", StockMoveRepository.STATUS_PLANNED)
        .order("id")
        .fetch();
  }

  @Override
  public List<StockMoveLine> fetchRelatedPlannedStockMoveLineList(
      StockLocationLine stockLocationLine) {
    return stockMoveLineRepository
        .all()
        .filter(
            "self.product.id = :productId "
                + "AND self.stockMove.fromStockLocation.id = :stockLocationId "
                + "AND self.stockMove.statusSelect = :planned")
        .bind("productId", stockLocationLine.getProduct().getId())
        .bind("stockLocationId", stockLocationLine.getStockLocation().getId())
        .bind("planned", StockMoveRepository.STATUS_PLANNED)
        .fetch();
  }

  @Override
  public List<StockMoveLine> fetchRelatedPlannedStockMoveLineListNoAvailabilityRequest(
      StockLocationLine stockLocationLine) {
    return stockMoveLineRepository
        .all()
        .filter(
            "self.product = :product "
                + "AND self.stockMove.fromStockLocation = :stockLocation "
                + "AND self.stockMove.statusSelect = :planned "
                + "AND (self.stockMove.availabilityRequest IS FALSE "
                + "OR self.stockMove.availabilityRequest IS NULL) "
                + "AND self.reservedQty > 0")
        .bind("product", stockLocationLine.getProduct())
        .bind("stockLocation", stockLocationLine.getStockLocation())
        .bind("planned", StockMoveRepository.STATUS_PLANNED)
        .fetch();
  }
}
