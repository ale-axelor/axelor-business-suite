package com.axelor.apps.supplychain.service.stockreservation;

import com.axelor.JpaTestModule;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.stock.db.StockLocationLine;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.stock.service.StockLocationLineService;
import com.axelor.db.JpaFixture;
import com.axelor.db.JpaSupport;
import com.axelor.exception.AxelorException;
import com.axelor.test.GuiceModules;
import com.axelor.test.GuiceRunner;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

@RunWith(GuiceRunner.class)
@GuiceModules({JpaTestModule.class})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestWithSaleOrderReservedQtyStockWorflowService extends JpaSupport {

  @Inject private ReservedQtyStockWorkflowServiceImpl reservedQtyService;
  @Inject private StockMoveRepository stockMoveRepository;
  @Inject private StockLocationLineService stockLocationLineService;
  @Inject private JpaFixture fixture;

  private StockMove stockMove;
  private SaleOrderLine saleOrderLine;
  private StockLocationLine stockLocationLine;

  @Before
  @Transactional
  public void setUp() {
    fixture.load("stock-reservation/stock-move-with-sale-order.yml");
    stockMove = null;
    saleOrderLine = null;
    stockLocationLine = null;
  }

  /*
   * Tests
   */

  /**
   * Test a simple allocation: We request reservation for 1 unit with 10 unit available, and the
   * stock move has a linked sale order.
   *
   * @throws AxelorException
   */
  @Test
  public void testSaleOrderUpdateReservedQuantityWhenRequested() throws AxelorException {
    givenStockMoveAndSaleOrder();
    whenPlanningStockMove();
    thenReservedQty(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
  }

  private void givenStockMoveAndSaleOrder() {
    stockMove = stockMoveRepository.findByName("stockMove");
    StockMoveLine stockMoveLine = stockMove.getStockMoveLineList().get(0);
    saleOrderLine = stockMoveLine.getSaleOrderLine();
    stockLocationLine =
        stockLocationLineService.getStockLocationLine(
            stockMove.getFromStockLocation(), stockMoveLine.getProduct());
  }

  private void whenPlanningStockMove() throws AxelorException {
    reservedQtyService.updateReservedQuantity(stockMove, StockMoveRepository.STATUS_PLANNED);
  }

  private void thenReservedQty(
      BigDecimal stockMoveReservedQty,
      BigDecimal saleOrderLineReservedQty,
      BigDecimal stockLocationLineReservedQty) {
    StockMoveLine stockMoveLine = stockMove.getStockMoveLineList().get(0);
    Assert.assertEquals(stockMoveReservedQty, stockMoveLine.getReservedQty());
    if (saleOrderLineReservedQty != null) {
      Assert.assertEquals(saleOrderLineReservedQty, saleOrderLine.getReservedQty());
    }
    Assert.assertEquals(stockLocationLineReservedQty, stockLocationLine.getReservedQty());
  }
}
