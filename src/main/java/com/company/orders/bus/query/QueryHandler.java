package com.company.orders.bus.query;

/** One handler per query type. */
public interface QueryHandler<Q extends Query<R>, R> {
  R handle(Q query);

  Class<Q> queryType();
}
