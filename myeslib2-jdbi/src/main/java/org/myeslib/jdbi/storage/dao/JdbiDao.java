package org.myeslib.jdbi.storage.dao;

import org.myeslib.data.UnitOfWork;
import org.myeslib.data.UnitOfWorkHistory;
import org.myeslib.jdbi.storage.dao.config.DbMetadata;
import org.myeslib.jdbi.storage.dao.config.UowSerialization;
import org.skife.jdbi.v2.*;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

public class JdbiDao<K> implements UnitOfWorkDao<K> {

    static final Logger logger = LoggerFactory.getLogger(JdbiDao.class);

    private final UowSerialization functions;
    private final DbMetadata dbMetadata;
    private final DBI dbi;

    public JdbiDao(UowSerialization functions, DbMetadata dbMetadata, DBI dbi) {
        checkNotNull(functions);
        this.functions = functions;
        checkNotNull(dbMetadata);
        this.dbMetadata = dbMetadata;
        checkNotNull(dbi);
        this.dbi = dbi;
    }

    /*
     * (non-Javadoc)
     * @see org.myeslib.jdbi.AggregateRootHistoryReader#getSnapshot(java.lang.Object)
     */
    @Override
    public UnitOfWorkHistory getFull(final K id) {
        return getPartial(id, 0L);
    }

    /*
         * (non-Javadoc)
         * @see org.myeslib.jdbi.AggregateRootHistoryReader#getPartial(java.lang.Object)
         */
    @Override
    public UnitOfWorkHistory getPartial(K id, Long biggerThanThisVersion) {

        final UnitOfWorkHistory arh = new UnitOfWorkHistory();

        try {

            logger.debug("will load {} from {}", id.toString(), dbMetadata.aggregateRootTable);

            List<UowRecord> unitsOfWork = dbi
                    .withHandle(new HandleCallback<List<UowRecord>>() {

                                    String sql = String.format("select id, version, uow_data, seq_number " +
                                            "from %s where id = :id " +
                                            " and version > :version " +
                                            "order by version", dbMetadata.unitOfWorkTable);

                                    public List<UowRecord> withHandle(Handle h) {
                                        return h.createQuery(sql)
                                                .bind("id", id.toString())
                                                .bind("version", biggerThanThisVersion)
                                                .map(new UowRecordMapper()).list();
                                    }
                                }
                    );

            if (unitsOfWork != null) {
                logger.debug("found {} units of work for id {} and version > {} on {}", unitsOfWork.size(), id.toString(), biggerThanThisVersion, dbMetadata.unitOfWorkTable);
                for (UowRecord r : unitsOfWork) {
                    logger.debug("converting to uow from {}", r.uowData);
                    Function<String, UnitOfWork> f = functions.fromStringFunction;
                    UnitOfWork uow = f.apply(r.uowData);
                    logger.debug(uow.toString());
                    arh.add(uow);
                }
            } else {
                logger.debug("found none unit of work for id {} and version > {} on {}", id.toString(), biggerThanThisVersion, dbMetadata.unitOfWorkTable);
            }

        } catch (Exception e) {
            logger.error("error when loading {} from table {}", id.toString(), dbMetadata.unitOfWorkTable);
            e.printStackTrace();

        } finally {
        }

        return arh;
    }

    @Override
    public void append(final K id, final UnitOfWork uow) {

        String sql = String.format("insert into %s (id, uow_data, version) values (:id, :uow_data, :version)", dbMetadata.unitOfWorkTable);

        logger.debug(sql);

        logger.debug("appending uow to {} with id {}", dbMetadata.aggregateRootTable, id);

        dbi.inTransaction(TransactionIsolationLevel.READ_COMMITTED, (conn, status) -> conn.createStatement(sql)
                .bind("id", id.toString())
                .bind("uow_data", functions.toStringFunction.apply(uow))
                .bind("version", uow.getVersion())
                .execute());

    }

    @Override
    public void appendBatch(K id, UnitOfWork... uowArray) {

        String sql = String.format("insert into %s (id, uow_data, version) values (:id, :uow_data, :version)", dbMetadata.unitOfWorkTable);

        logger.debug("batch appending {} units of work with id {} on table {}", uowArray.length, id, dbMetadata.aggregateRootTable);

        dbi.inTransaction(TransactionIsolationLevel.READ_COMMITTED, (h, ts) -> {
            final PreparedBatch pb = h.prepareBatch(sql);
            for (UnitOfWork uow : uowArray) {
                logger.debug(sql);
                logger.debug("    --> batch appending uow to {} with id {}", dbMetadata.aggregateRootTable, id);
                String asString = functions.toStringFunction.apply(uow);
                pb.add().bind("id", id.toString()).bind("uow_data", asString).bind("version", uow.getVersion());
            }
            return pb.execute().length;
        });

    }

    public static class UowRecord {
        String id;
        Long version;
        String uowData;
        Long seqNumber;

        public UowRecord(String id, Long version, String uowData, Long seqNumber) {
            this.id = id;
            this.version = version;
            this.uowData = uowData;
            this.seqNumber = seqNumber;
        }

    }

    public static class UowRecordMapper implements ResultSetMapper<UowRecord> {
        @Override
        public UowRecord map(int index, ResultSet r, StatementContext ctx)
                throws SQLException {
            String id = r.getString("id");
            Long version = r.getBigDecimal("version").longValue();
            String uowData = new ClobToStringMapper("uow_data").map(index, r, ctx);
            BigDecimal bdSeqNumber = r.getBigDecimal("seq_number");
            Long seqNumber = bdSeqNumber == null ? null : bdSeqNumber.longValue();
            return new UowRecord(id, version, uowData, seqNumber);
        }
    }

}