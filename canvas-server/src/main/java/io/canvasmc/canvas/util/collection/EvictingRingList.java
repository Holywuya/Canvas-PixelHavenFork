package io.canvasmc.canvas.util.collection;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public final class EvictingRingList<E> extends AbstractList<E> implements RandomAccess {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    private Object[] elements;
    private final int maxCapacity;
    private int head;
    private int size;
    private int tail;
    private int mask;

    public EvictingRingList(int requestedMaxCapacity) {
        if (requestedMaxCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.maxCapacity = tableSizeFor(requestedMaxCapacity);
        int initialCapacity = Math.min(DEFAULT_INITIAL_CAPACITY, this.maxCapacity);
        this.elements = new Object[initialCapacity];
        this.mask = initialCapacity - 1;
    }

    public EvictingRingList(Collection<? extends E> collection) {
        this(Math.max(1, collection.size()));
        this.addAll(collection);
    }

    public EvictingRingList(int requestedMaxCapacity, Collection<? extends E> collection) {
        this(requestedMaxCapacity);
        this.addAll(collection);
    }

    private static int tableSizeFor(int capacity) {
        int value = -1 >>> Integer.numberOfLeadingZeros(capacity - 1);
        if (value < 0) {
            return 1;
        }
        return value >= MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : value + 1;
    }

    private void grow() {
        int oldCapacity = this.elements.length;
        int newCapacity = oldCapacity << 1;
        Object[] newElements = new Object[newCapacity];
        int firstPart = oldCapacity - this.head;
        System.arraycopy(this.elements, this.head, newElements, 0, firstPart);
        System.arraycopy(this.elements, 0, newElements, firstPart, this.head);
        this.elements = newElements;
        this.mask = newCapacity - 1;
        this.head = 0;
        this.tail = oldCapacity;
    }

    @Override
    public boolean add(E element) {
        this.modCount++;
        if (this.size < this.elements.length) {
            this.size++;
        } else if (this.elements.length < this.maxCapacity) {
            this.grow();
            this.size++;
        } else {
            this.head = (this.head + 1) & this.mask;
        }
        this.elements[this.tail] = element;
        this.tail = (this.tail + 1) & this.mask;
        return true;
    }

    @Override
    public E get(int index) {
        Objects.checkIndex(index, this.size);
        return (E) this.elements[(this.head + index) & this.mask];
    }

    @Override
    public E set(int index, E element) {
        Objects.checkIndex(index, this.size);
        int realIndex = (this.head + index) & this.mask;
        E oldValue = (E) this.elements[realIndex];
        this.elements[realIndex] = element;
        return oldValue;
    }

    @Override
    public E remove(int index) {
        Objects.checkIndex(index, this.size);
        this.modCount++;
        E oldValue = this.get(index);
        for (int i = index; i < this.size - 1; i++) {
            int current = (this.head + i) & this.mask;
            int next = (this.head + i + 1) & this.mask;
            this.elements[current] = this.elements[next];
        }
        int lastIndex = (this.head + this.size - 1) & this.mask;
        this.elements[lastIndex] = null;
        this.tail = (this.tail - 1) & this.mask;
        this.size--;
        return oldValue;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public void clear() {
        this.modCount++;
        if (this.size == 0) {
            return;
        }
        if (this.head < this.tail) {
            Arrays.fill(this.elements, this.head, this.tail, null);
        } else {
            Arrays.fill(this.elements, this.head, this.elements.length, null);
            if (this.tail > 0) {
                Arrays.fill(this.elements, 0, this.tail, null);
            }
        }
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        int expectedModCount = this.modCount;
        int cursor = this.head;
        for (int count = 0; count < this.size; count++) {
            action.accept((E) this.elements[cursor]);
            cursor = (cursor + 1) & this.mask;
        }
        if (this.modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public Object @NotNull [] toArray() {
        Object[] result = new Object[this.size];
        this.copyInto(result);
        return result;
    }

    @Override
    public <T> T @NotNull [] toArray(T[] array) {
        T[] result = array;
        if (result.length < this.size) {
            result = (T[]) Array.newInstance(array.getClass().getComponentType(), this.size);
        }
        this.copyInto(result);
        if (result.length > this.size) {
            result[this.size] = null;
        }
        return result;
    }

    private void copyInto(Object[] target) {
        if (this.size == 0) {
            return;
        }
        if (this.head < this.tail) {
            System.arraycopy(this.elements, this.head, target, 0, this.size);
            return;
        }
        int firstPart = this.elements.length - this.head;
        System.arraycopy(this.elements, this.head, target, 0, firstPart);
        System.arraycopy(this.elements, 0, target, firstPart, this.tail);
    }

    @Override
    public @NotNull Iterator<E> iterator() {
        return new RingIterator();
    }

    private final class RingIterator implements Iterator<E> {
        private int cursor = EvictingRingList.this.head;
        private int remaining = EvictingRingList.this.size;
        private int lastReturned = -1;
        private int expectedModCount = EvictingRingList.this.modCount;

        @Override
        public boolean hasNext() {
            return this.remaining > 0;
        }

        @Override
        public E next() {
            this.checkForComodification();
            if (this.remaining <= 0) {
                throw new NoSuchElementException();
            }
            this.lastReturned = this.cursor;
            E element = (E) EvictingRingList.this.elements[this.cursor];
            this.cursor = (this.cursor + 1) & EvictingRingList.this.mask;
            this.remaining--;
            return element;
        }

        @Override
        public void remove() {
            if (this.lastReturned < 0) {
                throw new IllegalStateException();
            }
            this.checkForComodification();
            int logicalIndex = (this.lastReturned - EvictingRingList.this.head) & EvictingRingList.this.mask;
            EvictingRingList.this.remove(logicalIndex);
            this.cursor = this.lastReturned;
            this.lastReturned = -1;
            this.expectedModCount = EvictingRingList.this.modCount;
        }

        private void checkForComodification() {
            if (EvictingRingList.this.modCount != this.expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }
    }
}
