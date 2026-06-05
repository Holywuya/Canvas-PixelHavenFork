package io.canvasmc.canvas.util;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class WeakConcurrentCollection<E> implements Collection<E> {
    private final CopyOnWriteArrayList<WeakReference<E>> backed = new CopyOnWriteArrayList<>();
    private final AtomicInteger liveCount = new AtomicInteger(0);

    @Override
    public int size() {
        int count = 0;
        for (E ignored : this) count++;
        return count;
    }

    @Override
    public boolean isEmpty() {
        if (liveCount.get() <= 0) return true;
        for (WeakReference<E> ref : backed) {
            if (ref.get() != null) return false;
        }
        return true;
    }

    @Override
    public boolean contains(final Object o) {
        if (o == null) return false;
        for (E value : this) {
            if (o.equals(value)) return true;
        }
        return false;
    }

    @Override
    public @NonNull Iterator<E> iterator() {
        final List<WeakReference<E>> snapshot = List.copyOf(backed);
        return new Iterator<>() {
            private final Iterator<WeakReference<E>> it = snapshot.iterator();
            private @Nullable E next = null;
            private @Nullable E lastResolved = null;

            @Override
            public boolean hasNext() {
                if (next != null) return true;
                while (it.hasNext()) {
                    final E e = it.next().get();
                    if (e != null) {
                        next = e;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public E next() {
                final E result = this.next;
                if (result == null && !hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                this.lastResolved = this.next;
                this.next = null;
                return this.lastResolved;
            }

            @Override
            public void remove() {
                if (lastResolved == null) {
                    throw new IllegalStateException();
                }
                WeakConcurrentCollection.this.remove(lastResolved);
                lastResolved = null;
            }
        };
    }

    @Override
    public Object[] toArray() {
        return toArray(new Object[0]);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T @NonNull [] a) {
        final List<E> list = new ObjectArrayList<>();
        for (E e : this) list.add(e);
        return list.toArray(a);
    }

    @Override
    public boolean add(final E e) {
        Preconditions.checkNotNull(e, "WeakConcurrentCollection does not support null entries");
        liveCount.incrementAndGet();
        return backed.add(new WeakReference<>(e));
    }

    @Override
    public boolean remove(final Object o) {
        if (o == null) return false;
        for (final WeakReference<E> ref : backed) {
            final E value = ref.get();
            if (o.equals(value)) {
                if (backed.remove(ref)) {
                    liveCount.decrementAndGet();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean containsAll(final @NonNull Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) return false;
        }
        return true;
    }

    @Override
    public boolean addAll(final @NonNull Collection<? extends E> c) {
        boolean modified = false;
        for (E e : c) {
            if (add(e)) modified = true;
        }
        return modified;
    }

    @Override
    public boolean removeAll(final @NonNull Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            if (remove(o)) modified = true;
        }
        return modified;
    }

    @Override
    public boolean retainAll(final @NonNull Collection<?> c) {
        boolean modified = false;
        for (E e : this) {
            if (!c.contains(e)) {
                if (remove(e)) modified = true;
            }
        }
        return modified;
    }

    @Override
    public void clear() {
        backed.clear();
        liveCount.set(0);
    }
}
