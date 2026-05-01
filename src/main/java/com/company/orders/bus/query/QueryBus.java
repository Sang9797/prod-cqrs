package com.company.orders.bus.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** QUERY BUS — dispatches read operations. Completely independent of CommandBus. */
@Component
public class QueryBus {

  private static final Logger LOG = LoggerFactory.getLogger(QueryBus.class);
  private final Map<Class<?>, QueryHandler<?, ?>> registry = new HashMap<>();

  public QueryBus(List<QueryHandler<?, ?>> handlers) {
    handlers.forEach(
        h -> {
          registry.put(h.queryType(), h);
          LOG.info(
              "[QueryBus] registered {} → {}",
              h.queryType().getSimpleName(),
              h.getClass().getSimpleName());
        });
    LOG.info("[QueryBus] ready — {} handler(s)", registry.size());
  }

  @SuppressWarnings("unchecked")
  public <Q extends Query<R>, R> R dispatch(Q query) {
    var handler = (QueryHandler<Q, R>) registry.get(query.getClass());
    if (handler == null) {
      throw new IllegalStateException(
          "No handler registered for: " + query.getClass().getSimpleName());
    }
    LOG.debug("[QueryBus] dispatching {}", query.getClass().getSimpleName());
    return handler.handle(query);
  }
}
