

package top.niunaijun.blackbox.core.system.pm;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;


public final class BzFastImmutableArraySet<T> extends AbstractSet<T> {
    private final Object[] snapshot;
    private transient SnapshotIterator cachedIterator;

    public BzFastImmutableArraySet(T[] contents) {
        this.snapshot = contents == null ? new Object[0] : contents.clone();
    }

    @Override
    public Iterator<T> iterator() {
        SnapshotIterator it = cachedIterator;
        if (it == null) {
            it = new SnapshotIterator(snapshot);
            cachedIterator = it;
        }
        it.rewind();
        @SuppressWarnings("unchecked")
        Iterator<T> out = (Iterator<T>) it;
        return out;
    }

    @Override
    public int size() {
        return snapshot.length;
    }

    @Override
    public boolean contains(Object o) {
        for (Object element : snapshot) {
            if (Objects.equals(element, o)) {
                return true;
            }
        }
        return false;
    }

    private static final class SnapshotIterator implements Iterator<Object> {
        private final Object[] elements;
        private int cursor;

        SnapshotIterator(Object[] elements) {
            this.elements = elements;
        }

        void rewind() {
            cursor = 0;
        }

        @Override
        public boolean hasNext() {
            return cursor >= 0 && cursor < elements.length;
        }

        @Override
        public Object next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return elements[cursor++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
