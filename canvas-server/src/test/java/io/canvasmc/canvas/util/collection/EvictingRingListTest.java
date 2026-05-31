package io.canvasmc.canvas.util.collection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

class EvictingRingListTest {

    @Test
    void evictsOldestEntriesWhenCapacityIsReached() {
        EvictingRingList<Integer> list = new EvictingRingList<>(3);

        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        assertEquals(3, list.size());
        assertArrayEquals(new Object[] {2, 3, 4}, list.toArray());
    }

    @Test
    void supportsGetSetAndClear() {
        EvictingRingList<String> list = new EvictingRingList<>(2);

        list.add("first");
        list.add("second");
        String oldValue = list.set(1, "updated");

        assertEquals("second", oldValue);
        assertEquals("first", list.get(0));
        assertEquals("updated", list.get(1));

        list.clear();

        assertEquals(0, list.size());
        assertArrayEquals(new Object[0], list.toArray());
    }

    @Test
    void iteratorIsFailFast() {
        EvictingRingList<Integer> list = new EvictingRingList<>(2);
        list.add(1);
        list.add(2);
        Iterator<Integer> iterator = list.iterator();

        list.add(3);

        assertThrows(ConcurrentModificationException.class, iterator::next);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new EvictingRingList<>(0));
    }
}
