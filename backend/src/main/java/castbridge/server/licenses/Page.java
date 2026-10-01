package castbridge.server.licenses;

import java.util.List;

/** A page of results: {"items":[…],"page":0,"size":50,"total":123}. */
public record Page<T>(List<T> items, int page, int size, long total) {
    public boolean hasNext() { return (long) (page + 1) * size < total; }

    public boolean hasPrevious() { return page > 0; }

    public int pages() { return (int) Math.max(1, (total + size - 1) / size); }
}
