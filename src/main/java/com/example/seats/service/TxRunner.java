package com.example.seats.service;

import com.example.seats.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Transaction boundary for the service layer.
 *
 * Runs a body in ONE transaction (READ COMMITTED) and retries on deadlock / serialization failure.
 * With the lock ordering described in ReservationService these should never fire, but a retry is
 * cheap insurance against turning a rare DB-level abort into a 5xx. ApiExceptions (declines) are
 * NOT retried: they propagate, which rolls the transaction back.
 */
@Component
public class TxRunner {
    private static final Logger log = LoggerFactory.getLogger(TxRunner.class);
    private static final int MAX_TX_RETRIES = 5;

    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public TxRunner(PlatformTransactionManager tm) {
        this.tx = new TransactionTemplate(tm);
        this.readTx = new TransactionTemplate(tm);
        this.readTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.readTx.setReadOnly(true);
    }

    /** Read-write transaction with deadlock retry. */
    public <T> T inTransaction(Supplier<T> body) {
        for (int attempt = 1; attempt <= MAX_TX_RETRIES; attempt++) {
            try {
                return tx.execute(status -> body.get());
            } catch (PessimisticLockingFailureException e) {
                log.atWarn().setMessage("transaction retry").addKeyValue("attempt", attempt).log();
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(1, 20L * attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new ApiException(503, "busy", "system busy, please retry");
    }

    /** One consistent snapshot (REPEATABLE READ, read-only) so multi-query reads always agree. */
    public <T> T inReadOnlyTransaction(Supplier<T> body) {
        return readTx.execute(status -> body.get());
    }
}
