/*
 * @copyright Copyright (c) OX Software GmbH, Germany <info@open-xchange.com>
 * @license AGPL-3.0
 *
 * This code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OX App Suite.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>.
 *
 * Any use of the work other than as authorized under this license or copyright law is prohibited.
 *
 */

package com.openexchange.tools.iterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import com.openexchange.exception.OXException;

/**
 * {@link SearchIterators} - Utility class for {@link SearchIterator}.
 *
 * @author <a href="mailto:thorben.betten@open-xchange.com">Thorben Betten</a>
 */
public final class SearchIterators {

    /** Simple class to delay initialization until needed */
    private static class LoggerHolder {
        static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SearchIterators.class);
    }

    /**
     * Initializes a new {@link SearchIterators}.
     */
    private SearchIterators() {
        super();
    }

    /**
     * (Safely) Closes specified {@link SearchIterator} instance.
     *
     * @param iterator The iterator to close
     */
    public static void close(final SearchIterator<?> iterator) {
        if (null != iterator) {
            try {
                iterator.close();
            } catch (Exception e) {
                // Ignore
                LoggerHolder.LOGGER.error("Closing SearchIterator instance failed", e);
            }
        }
    }

    /**
     * Iterates through the supplied search iterator and puts all elements into a list.
     *
     * @param iterator The search iterator to get the elements from
     * @return The iterator's elements in a list
     */
    public static <T> List<T> asList(SearchIterator<T> iterator) throws OXException {
        if (null == iterator) {
            return null;
        }

        try {
            if (!iterator.hasNext()) {
                return Collections.emptyList();
            }

            List<T> list = new ArrayList<T>();
            do {
                list.add(iterator.next());
            } while (iterator.hasNext());
            return list;
        } finally {
            close(iterator);
        }
    }

    @SuppressWarnings("rawtypes")
    private static final SearchIterator EMPTY_ITERATOR = new EmptySearchIterator<>();

    /**
     * Gets the singleton iterator for specified element
     *
     * @param element The element
     * @return The singleton iterator
     */
    @SuppressWarnings("unchecked")
    public static <T> SearchIterator<T> singletonIterator(T element) {
        if (null == element) {
            return EMPTY_ITERATOR;
        }

        return new SingletonSearchIterator<T>(element);
    }

    /**
     * Gets an empty iterator.
     *
     * @param <T> type of elements, if there were any, in the list
     * @return An empty iterator
     */
    @SuppressWarnings("unchecked")
    public static final <T> SearchIterator<T> emptyIterator() {
        return EMPTY_ITERATOR;
    }

    // ------------------------------------------------------------------------------------------------------------------------------

    private static final class EmptySearchIterator<T> implements SearchIterator<T> {

        EmptySearchIterator() {
            super();
        }

        @Override
        public boolean hasNext() throws OXException {
            return false;
        }

        @Override
        public T next() throws OXException {
            throw new NoSuchElementException("Empty iterator has no elements");
        }

        @Override
        public void close() {
            // Nothing
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean hasWarnings() {
            return false;
        }

        @Override
        public void addWarning(OXException warning) {
            // Nothing
        }

        @Override
        public OXException[] getWarnings() {
            return null;
        }
    }

    private static final class SingletonSearchIterator<T> implements SearchIterator<T> {

        private final List<OXException> warnings;
        private T element;

        SingletonSearchIterator(T element) {
            super();
            this.element = element;
            warnings = new LinkedList<OXException>();
        }

        @Override
        public boolean hasNext() throws OXException {
            return null != element;
        }

        @Override
        public T next() throws OXException {
            T retval = this.element;
            if (null == retval) {
                throw new NoSuchElementException();
            }
            this.element = null;
            return retval;
        }

        @Override
        public void close() {
            // Nothing
        }

        @Override
        public int size() {
            return null == element ? 0 : 1;
        }

        @Override
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        @Override
        public void addWarning(OXException warning) {
            if (null != warning) {
                warnings.add(warning);
            }
        }

        @Override
        public OXException[] getWarnings() {
            int size = warnings.size();
            return size == 0 ? null : warnings.toArray(new OXException[size]);
        }
    }

}
