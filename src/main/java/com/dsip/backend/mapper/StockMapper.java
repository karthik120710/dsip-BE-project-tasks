package com.dsip.backend.mapper;

import com.dsip.backend.entity.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * MyBatis mapper interface for Stock entity.
 * Handles database operations for stock price caching.
 */
@Mapper
public interface StockMapper {

    /**
     * Finds a stock by its symbol.
     * This is the PRIMARY lookup method for cache checking.
     *
     * @param stockSymbol the stock symbol to search for
     * @return Optional containing the stock if found, empty otherwise
     */
    Optional<Stock> findByStockSymbol(@Param("stockSymbol") String stockSymbol);

    /**
     * Checks if a stock exists by its symbol.
     *
     * @param stockSymbol the stock symbol to check
     * @return true if stock exists, false otherwise
     */
    boolean existsByStockSymbol(@Param("stockSymbol") String stockSymbol);

    /**
     * Inserts a new stock record into the database.
     * stockSymbol must be unique.
     *
     * @param stock the stock to insert
     * @return number of rows affected (should be 1)
     */
    int insert(Stock stock);

    /**
     * Updates an existing stock's closing price and last updated date.
     *
     * @param stock the stock with updated information
     * @return number of rows affected (should be 1)
     */
    int update(Stock stock);

    /**
     * Deletes a stock by its symbol.
     *
     * @param stockSymbol the stock symbol to delete
     * @return number of rows affected (should be 1)
     */
    int deleteByStockSymbol(@Param("stockSymbol") String stockSymbol);
}
